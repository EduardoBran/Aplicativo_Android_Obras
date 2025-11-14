package com.luizeduardobrandao.obra.ui.fotos

import android.content.res.Configuration
import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.PopupMenu
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnPreDraw
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Imagem
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentFotosBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.extensions.bindScrollToBottomFabForRecycler
import com.luizeduardobrandao.obra.ui.extensions.RecyclerFabBindings
import com.luizeduardobrandao.obra.ui.extensions.updateFabVisibilityAnimated
import com.luizeduardobrandao.obra.ui.fotos.adapter.ImagemAdapter
import com.luizeduardobrandao.obra.utils.applyResponsiveButtonSizingGrowShrink
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.FileUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class FotosFragment : Fragment() {

    private var _binding: FragmentFotosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FotosViewModel by viewModels()
    private val args: FotosFragmentArgs by navArgs()

    private lateinit var imagesAdapter: ImagemAdapter

    private var suppressNextErrorAfterDelete = false

    // Comportamento do FAB
    private var rvFabBindings: RecyclerFabBindings? = null

    // Foto temporária capturada via câmera
    private var tempCameraUri: Uri? = null

    // Result APIs
    private val openImagePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        // manter permissão de leitura
        requireContext().contentResolver.takePersistableUriPermission(
            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val bytes = FileUtils.readBytes(requireContext(), uri)
        val mime = FileUtils.detectMime(requireContext(), uri)
        onPhotoChosen(bytes, mime)
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCameraCapture() else {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.generic_error),
                getString(R.string.nota_photo_error_permission_camera),
                getString(R.string.generic_ok_upper_case)
            )
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { _ ->
        val uri = tempCameraUri ?: return@registerForActivityResult
        try {
            FileUtils.finalizePendingImage(requireContext(), uri)

            val bytes = FileUtils.readBytes(requireContext(), uri)
            val mime = FileUtils.detectMime(requireContext(), uri)

            if (bytes.isNotEmpty()) {
                onPhotoChosen(bytes, mime)   // → abre BottomSheet com formulário
            } else {
                showSnackbarFragment(
                    Constants.SnackType.ERROR.name,
                    getString(R.string.generic_error),
                    getString(R.string.nota_photo_error_camera),
                    getString(R.string.generic_ok_upper_case)
                )
            }
        } catch (_: Exception) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.generic_error),
                getString(R.string.nota_photo_error_camera),
                getString(R.string.generic_ok_upper_case)
            )
        } finally {
            tempCameraUri = null
        }
    }

    // util simples
    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // BackStackEntry estável para ignorar erro após delete
        val navController = findNavController()
        val fotosEntry = navController.getBackStackEntry(R.id.fotosFragment)

        fotosEntry.savedStateHandle.get<Boolean>("imagemDeletada")?.let { deleted ->
            if (deleted) {
                suppressNextErrorAfterDelete = true
                fotosEntry.savedStateHandle.remove<Boolean>("imagemDeletada")
            }
        }

        fotosEntry.savedStateHandle
            .getLiveData<Boolean>("imagemDeletada")
            .observe(this) { deleted ->
                if (deleted == true) {
                    suppressNextErrorAfterDelete = true
                    fotosEntry.savedStateHandle.remove<Boolean>("imagemDeletada")
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFotosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)

        setupToolbar()
        setupRecycler()

        rvFabBindings = bindScrollToBottomFabForRecycler(
            fab = binding.fabScrollDown,
            recyclerView = binding.rvImagens
        )

        setupButtons()

        // ── Responsividade dos botões do header (lado a lado)
        binding.btnEnviar.doOnPreDraw {
            binding.btnEnviar.applyResponsiveButtonSizingGrowShrink()
        }
        binding.btnTirarFoto.doOnPreDraw {
            binding.btnTirarFoto.applyResponsiveButtonSizingGrowShrink()
        }

        applyLandscapeCompactUiIfNeeded()
        bindState()

        // back físico: comportamento padrão (apenas navigateUp)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            findNavController().navigateUp()
        }
    }

    /* -------------------- Toolbar & filtro -------------------- */

    private fun setupToolbar() = with(binding.toolbarFotos) {
        setNavigationOnClickListener { findNavController().navigateUp() }

        // botão customizado do menu (actionLayout) para filtros
        val menuItem = menu.findItem(R.id.action_fotos_menu)
        menuItem.actionView?.findViewById<View>(R.id.btnFilterMenu)?.setOnClickListener {
            showFilterPopup(it)
        }
        menuItem.actionView?.alpha = 1f
    }

    private fun showFilterPopup(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_fotos_popup, popup.menu)
        popup.setOnMenuItemClickListener {
            val (filter, title) = when (it.itemId) {
                R.id.filter_all -> ImagemFilter.TODAS to getString(R.string.imagens_filter_all)
                R.id.filter_name -> ImagemFilter.NOME to getString(R.string.imagens_filter_name)
                R.id.filter_pintura -> ImagemFilter.PINTURA to getString(R.string.imagens_filter_pintura)
                R.id.filter_pedreiro -> ImagemFilter.PEDREIRO to getString(R.string.imagens_filter_pedreiro)
                R.id.filter_ladrilheiro -> ImagemFilter.LADRILHEIRO to getString(R.string.imagens_filter_ladrilheiro)
                R.id.filter_hidraulica -> ImagemFilter.HIDRAULICA to getString(R.string.imagens_filter_hidraulica)
                R.id.filter_eletrica -> ImagemFilter.ELETRICA to getString(R.string.imagens_filter_eletrica)
                R.id.filter_outro -> ImagemFilter.OUTRO to getString(R.string.imagens_filter_outro)
                else -> return@setOnMenuItemClickListener false
            }
            viewModel.setFilter(filter)
            binding.tvFiltroTitulo.text = getString(R.string.imagens_filter_title_fmt, title)
            true
        }
        popup.show()
    }

    /* -------------------- Recycler com grid -------------------- */
    private fun setupRecycler() = with(binding.rvImagens) {
        val span = resources.getInteger(R.integer.fotos_grid_span)
        layoutManager = GridLayoutManager(requireContext(), span).apply {
            // mantém célula de header OUT (header agora fica fora do Recycler)
        }

        if (itemDecorationCount == 0) {
            addItemDecoration(
                GridSpacingItemDecoration(
                    spanCount = span,
                    spacingDp = 10,
                    includeEdge = true
                )
            )
        }

        this@FotosFragment.imagesAdapter = ImagemAdapter(
            onOpen = { openDetail(it) },
            onExpand = { openDetail(it) }
        )
        this.adapter = this@FotosFragment.imagesAdapter
        setHasFixedSize(true)
    }

    private fun openDetail(img: Imagem) {
        findNavController().navigate(
            FotosFragmentDirections.actionFotosToDetail(
                obraId = args.obraId,
                imagemId = img.id
            )
        )
    }

    private fun applyLandscapeCompactUiIfNeeded() = with(binding) {
        val isLandscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (!isLandscape) return@with

        // ↓ margens menores do card
        headerCard.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = 12.dp()
            rightMargin = 12.dp()
            topMargin = 8.dp()
        }

        // ↓ padding interno menor (o LinearLayout é o filho 0 do card)
        (headerCard.getChildAt(0) as? ViewGroup)?.setPadding(
            12.dp(), // start
            10.dp(), // top
            12.dp(), // end
            10.dp()  // bottom
        )

        // ↓ título menor
        tvFiltroTitulo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)

        // ↓ divisor com respiro menor
        dividerHeader.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = 4.dp()
        }

        // ↓ menos padding no topo da lista para “grudar” no cabeçalho
        rvImagens.setPadding(
            rvImagens.paddingLeft,
            4.dp(),
            rvImagens.paddingRight,
            rvImagens.paddingBottom
        )
    }

    /* -------------------- Ações topo (Enviar / Tirar Foto) -------------------- */

    private fun setupButtons() = with(binding) {
        tvFiltroTitulo.text =
            getString(R.string.imagens_filter_title_fmt, getString(R.string.imagens_filter_all))

        btnEnviar.setOnClickListener { openPicker() }
        btnTirarFoto.setOnClickListener { ensureCameraAndStart() }
    }

    private fun openPicker() {
        openImagePicker.launch(arrayOf("image/*"))
    }

    private fun ensureCameraAndStart() {
        if (!FileUtils.hasCameraApp(requireContext())) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.generic_attention),
                getString(R.string.nota_photo_error_camera),
                getString(R.string.generic_ok_upper_case)
            )
            return
        }
        requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun startCameraCapture() {
        tempCameraUri = FileUtils.createTempImageUri2(requireContext())
        takePictureLauncher.launch(tempCameraUri)
    }

    private fun onPhotoChosen(bytes: ByteArray, mime: String) {
        // passa os dados para o ViewModel (consumidos pelo BottomSheet)
        viewModel.setPendingPhoto(bytes, mime)
        ImagemFormBottomSheet.show(childFragmentManager)
    }

    /* -------------------- State (lista) -------------------- */
    private fun bindState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Lista filtrada
                launch {
                    viewModel.state.collect { ui ->
                        when (ui) {
                            is UiState.Loading -> showLoading(true)

                            is UiState.Success -> {
                                showLoading(false)
                                val list = ui.data
                                imagesAdapter.submitList(list)
                                binding.tvEmptyImagens.isVisible = list.isEmpty()

                                suppressNextErrorAfterDelete = false
                            }

                            is UiState.ErrorRes -> {
                                showLoading(false)

                                if (suppressNextErrorAfterDelete) {
                                    return@collect
                                } else {
                                    showSnackbarFragment(
                                        Constants.SnackType.ERROR.name,
                                        getString(R.string.generic_error),
                                        getString(ui.resId),
                                        getString(R.string.generic_ok_upper_case)
                                    )
                                }
                            }

                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun showLoading(show: Boolean) = with(binding) {
        progressFotos.isVisible = show
        rvImagens.isGone = show
        binding.fabScrollDown.updateFabVisibilityAnimated(!show) // esconde o FAB
    }

    override fun onDestroyView() {
        // Remover listeners do FAB/Recycler para evitar leaks
        rvFabBindings?.let { (gl, sl, obs) ->
            binding.rvImagens.removeOnScrollListener(sl)
            binding.rvImagens.viewTreeObserver.removeOnGlobalLayoutListener(gl)
            obs?.let { binding.rvImagens.adapter?.unregisterAdapterDataObserver(it) }
        }
        rvFabBindings = null
        binding.fabScrollDown.setOnClickListener(null)

        super.onDestroyView()
        _binding = null
    }
}

/** ItemDecoration para espaçamento uniforme entre células do Grid */
class GridSpacingItemDecoration(
    private val spanCount: Int,
    private val spacingDp: Int,
    private val includeEdge: Boolean
) : RecyclerView.ItemDecoration() {

    private fun dpToPx(view: View, dp: Int): Int {
        val metrics = view.resources.displayMetrics
        return (dp * metrics.density).roundToInt()
    }

    override fun getItemOffsets(
        outRect: android.graphics.Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val spacingPx = dpToPx(view, spacingDp)
        val position = parent.getChildAdapterPosition(view)
        val column = position % spanCount

        if (includeEdge) {
            outRect.left = spacingPx - column * spacingPx / spanCount
            outRect.right = (column + 1) * spacingPx / spanCount
            if (position < spanCount) {
                outRect.top = spacingPx
            }
            outRect.bottom = spacingPx
        } else {
            outRect.left = column * spacingPx / spanCount
            outRect.right = spacingPx - (column + 1) * spacingPx / spanCount
            if (position >= spanCount) {
                outRect.top = spacingPx
            }
        }
    }
}
