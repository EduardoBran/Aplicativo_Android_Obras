package com.luizeduardobrandao.obra.ui.fotos

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.fotos.adapter.ImagemAdapter
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.FileUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.luizeduardobrandao.obra.utils.showMaterialDatePickerBrToday

@AndroidEntryPoint
class FotosFragment : Fragment() {

    private var _binding: FragmentFotosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FotosViewModel by viewModels()
    private val args: FotosFragmentArgs by navArgs()

    private lateinit var imagesAdapter: ImagemAdapter

    private var suppressNextErrorAfterDelete = false

    // Foto selecionada/capturada (pendente de salvar)
    private var tempCameraUri: Uri? = null
    private var localBytes: ByteArray? = null
    private var localMime: String? = null

    // Controle do salvamento do formulário
    private var saving = false

    private var userEditedName = false

    // Result APIs
    private val openImagePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        // ADICIONE estas duas linhas ↓↓↓
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
                getString(R.string.snack_error),
                getString(R.string.nota_photo_error_permission_camera),
                getString(R.string.snack_button_ok)
            )
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { _ ->
        val uri = tempCameraUri ?: return@registerForActivityResult
        try {
            // Se quiser, pode remover esta linha se tirar finalizePendingImage do projeto
            FileUtils.finalizePendingImage(requireContext(), uri)

            val bytes = FileUtils.readBytes(requireContext(), uri)
            val mime = FileUtils.detectMime(requireContext(), uri)

            if (bytes.isNotEmpty()) {
                onPhotoChosen(bytes, mime)   // abre o card normalmente
            } else {
                showSnackbarFragment(
                    Constants.SnackType.ERROR.name,
                    getString(R.string.snack_error),
                    getString(R.string.nota_photo_error_camera),
                    getString(R.string.snack_button_ok)
                )
            }
        } catch (_: Exception) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.snack_error),
                getString(R.string.nota_photo_error_camera),
                getString(R.string.snack_button_ok)
            )
        } finally {
            tempCameraUri = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use a back stack entry ESTÁVEL do próprio FotosFragment
        val navController = findNavController()
        val fotosEntry = navController.getBackStackEntry(R.id.fotosFragment)

        // 1) LEITURA SINCRÔNICA: se já existe a chave, arma o flag ANTES de coletar o state
        fotosEntry.savedStateHandle.get<Boolean>("imagemDeletada")?.let { deleted ->
            if (deleted) {
                suppressNextErrorAfterDelete = true
                fotosEntry.savedStateHandle.remove<Boolean>("imagemDeletada")
            }
        }

        // 2) Observa futuros eventos também (em recriações, etc.)
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
        setupForm()
        bindState()

        // back físico: fecha card se aberto
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (binding.cardNovaImagem.isVisible) {
                toggleCard(false)
            } else findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /* -------------------- Toolbar & filtro -------------------- */

    private fun setupToolbar() = with(binding.toolbarFotos) {
        setNavigationOnClickListener { findNavController().navigateUp() }

        // botão customizado do menu (actionLayout)
        val menuItem = menu.findItem(R.id.action_fotos_menu)
        menuItem.actionView?.findViewById<View>(R.id.btnFilterMenu)?.setOnClickListener {
            showFilterPopup(it)
        }

        // deixa o ícone branco do actionView (se precisar)
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

    /* -------------------- Recycler com 3 ou 4 colunas -------------------- */
    private fun setupRecycler() = with(binding.rvImagens) {
        // 1) Span dinâmico por orientação
        val span = resources.getInteger(R.integer.fotos_grid_span)

        layoutManager = GridLayoutManager(requireContext(), span)

        if (itemDecorationCount == 0) {
            addItemDecoration(
                GridSpacingItemDecoration(
                    spanCount = span,   // 2) use o mesmo span aqui
                    spacingDp = 8,
                    includeEdge = true
                )
            )
        }

        this@FotosFragment.imagesAdapter = ImagemAdapter(
            onOpen = { openDetail(it) },
            onExpand = { openDetail(it) }
        )
        this.adapter = this@FotosFragment.imagesAdapter
    }

    private fun openDetail(img: Imagem) {
        findNavController().navigate(
            FotosFragmentDirections.actionFotosToDetail(
                obraId = args.obraId,
                imagemId = img.id
            )
        )
    }

    /* -------------------- Formulário (card) -------------------- */
    private fun setupForm() = with(binding) {
        tvFiltroTitulo.text =
            getString(R.string.imagens_filter_title_fmt, getString(R.string.imagens_filter_all))

        // Botões topo
        btnEnviar.setOnClickListener { openPicker() }
        btnTirarFoto.setOnClickListener { ensureCameraAndStart() }

        // Data
        etDataImagem.setOnClickListener {
            showMaterialDatePickerBrToday { chosen ->
                binding.etDataImagem.setText(chosen)
                validateForm()
            }
        }

        // Nome revalida
        etNomeImagem.doAfterTextChanged {
            userEditedName = true
            validateForm()
        }

        // Tipo: qualquer RadioButton dentro do grupo
        rgTipos.setOnCheckedChangeListener { _, _ -> validateForm() }

        // Salvar
        btnSalvarImagem.setOnClickListener {
            if (!validateForm()) return@setOnClickListener
            saveImagem()
        }

        // Cancelar
        btnCancelarImagem.setOnClickListener {
            toggleCard(false)
            clearForm()
        }
    }

    private fun onPhotoChosen(bytes: ByteArray, mime: String) {
        localBytes = bytes
        localMime = mime

        prepareFormForNewPhoto()  // <-- limpa e exige nova digitação
        toggleCard(true)

        // Pequeno atraso para evitar clique fantasma (ver item 2)
        binding.btnSalvarImagem.isClickable = false
        binding.btnSalvarImagem.postDelayed(
            { binding.btnSalvarImagem.isClickable = true }, 350
        )

        validateForm()
        binding.fotosScroll.postDelayed({
            binding.fotosScroll.smoothScrollTo(0, binding.cardNovaImagem.top)
        }, 100)
    }

    private fun openPicker() {
        openImagePicker.launch(arrayOf("image/*"))
    }

    private fun ensureCameraAndStart() {
        if (!FileUtils.hasCameraApp(requireContext())) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.snack_attention),
                getString(R.string.nota_photo_error_camera),
                getString(R.string.snack_button_ok)
            )
            return
        }
        requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun startCameraCapture() {
        tempCameraUri = FileUtils.createTempImageUri2(requireContext())
        takePictureLauncher.launch(tempCameraUri)
    }

    private fun toggleCard(show: Boolean) = with(binding) {
        cardNovaImagem.isVisible = show
        if (show) {
            // Garante "Pintura" selecionado se nada estiver marcado
            if (rgTipos.checkedRadioButtonId == View.NO_ID) {
                rgTipos.check(R.id.rbPintura)
            }

            // Preenche a data de hoje se estiver vazia
            if (etDataImagem.text.isNullOrBlank()) {
                etDataImagem.setText(todayPtBr())
            }

            etNomeImagem.requestFocus()
            validateForm() // revalida e habilita o botão salvar se possível
        } else {
            root.hideKeyboard()
        }
    }

    private fun todayPtBr(): String {
        val sdf = java.text.SimpleDateFormat(
            "dd/MM/yyyy", java.util.Locale("pt", "BR")
        )
        return sdf.format(java.util.Date())
    }

    private fun clearForm() = with(binding) {
        tilNomeImagem.error = null
        etNomeImagem.setText("")
        etDescricaoImagem.setText("")
        etDataImagem.setText("")
        rgTipos.check(R.id.rbPintura)
        progressSalvarImagem.isVisible = false
        btnSalvarImagem.isEnabled = false
        saving = false
        userEditedName = false
        localBytes = null
        localMime = null
    }

    private fun validateForm(): Boolean = with(binding) {
        val hasPhoto = localBytes != null && localMime != null
        val nome = etNomeImagem.text?.toString()?.trim().orEmpty()
        val nomeOk = nome.isNotEmpty()
        tilNomeImagem.error = if (!nomeOk) getString(R.string.nota_reg_error_nome) else null

        val dataOk = etDataImagem.text?.toString()
            ?.trim().orEmpty()
            .matches(Regex("""\d{2}/\d{2}/\d{4}"""))

        val tipoOk = rgTipos.checkedRadioButtonId != View.NO_ID

        // >>> só habilita se o usuário editou o nome nesta sessão
        val ok = hasPhoto && nomeOk && dataOk && tipoOk && userEditedName
        if (!saving) btnSalvarImagem.isEnabled = ok
        ok
    }

    private fun currentTipo(): String {
        val rbId = binding.rgTipos.checkedRadioButtonId
        val rb = binding.rgTipos.findViewById<RadioButton>(rbId)
        return rb?.text?.toString() ?: getString(R.string.imagens_filter_pintura)
    }

    private fun saveImagem() = with(binding) {
        if (!validateForm()) return@with

        showSnackbarFragment(
            Constants.SnackType.WARNING.name,
            getString(R.string.nota_photo_confirm_title),
            getString(R.string.imagem_save_confirm),
            getString(R.string.snack_button_yes),
            onAction = {
                val bytes = localBytes
                val mime = localMime
                if (bytes == null || mime == null) {
                    // Garantia extra: se por algum motivo perdeu, avisa e não tenta salvar
                    showSnackbarFragment(
                        Constants.SnackType.ERROR.name,
                        getString(R.string.snack_error),
                        getString(R.string.imagens_save_error),
                        getString(R.string.snack_button_ok)
                    )
                    return@showSnackbarFragment
                }

                if (saving) return@showSnackbarFragment
                saving = true
                btnSalvarImagem.isEnabled = false
                progressSalvarImagem.isVisible = true
                root.hideKeyboard()

                val imagem = Imagem(
                    id = "",
                    nome = etNomeImagem.text!!.trim().toString(),
                    descricao = etDescricaoImagem.text?.trim()?.toString(),
                    tipo = currentTipo(),
                    data = etDataImagem.text!!.toString(),
                    fotoUrl = null,
                    fotoPath = null
                )

                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.addImagem(imagem, bytes, mime).collectLatest { st ->
                        when (st) {
                            is UiState.Loading -> {
                                binding.progressSalvarImagem.isVisible = true
                                binding.btnSalvarImagem.isEnabled = false
                            }

                            is UiState.Success -> {
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.imagens_save_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                                toggleCard(false)
                                clearForm()
                            }

                            is UiState.ErrorRes -> {
                                binding.progressSalvarImagem.isVisible = false
                                binding.btnSalvarImagem.isEnabled = true
                                saving = false
                                showSnackbarFragment(
                                    Constants.SnackType.ERROR.name,
                                    getString(R.string.snack_error),
                                    getString(st.resId),
                                    getString(R.string.snack_button_ok)
                                )
                            }

                            else -> Unit
                        }
                    }
                }
            },
            btnNegativeText = getString(R.string.snack_button_no),
            onNegative = { /* mantém o card aberto */ }
        )
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

                                // <- só aqui resetamos: após um sucesso
                                suppressNextErrorAfterDelete = false
                            }

                            is UiState.ErrorRes -> {
                                showLoading(false)

                                // <- se viemos de deleção, ignore TODOS os erros até o primeiro Success
                                if (suppressNextErrorAfterDelete) {
                                    // não zere aqui; apenas ignore
                                    return@collect
                                } else {
                                    showSnackbarFragment(
                                        Constants.SnackType.ERROR.name,
                                        getString(R.string.snack_error),
                                        getString(ui.resId),
                                        getString(R.string.snack_button_ok)
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
        fotosScroll.isGone = show
    }

    /* preparar o card para uma nova foto */
    private fun prepareFormForNewPhoto() = with(binding) {
        saving = false
        userEditedName = false
        tilNomeImagem.error = null

        // Limpa campos – não herdamos nada da foto anterior
        etNomeImagem.setText("")           // nome obrigatoriamente vazio
        etDescricaoImagem.setText("")
        etDataImagem.setText(todayPtBr())  // data de hoje
        rgTipos.check(R.id.rbPintura)      // tipo padrão

        // Botão Salvar começa desabilitado
        btnSalvarImagem.isEnabled = false
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
        val position = parent.getChildAdapterPosition(view) // item position
        val column = position % spanCount // item column

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