package com.luizeduardobrandao.obra.ui.fotos

import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.ImageLoader
import coil.load
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Imagem
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentFotoDetailBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.FileUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale

@AndroidEntryPoint
class FotoDetailFragment : Fragment() {

    private var _binding: FragmentFotoDetailBinding? = null
    private val binding get() = _binding!!

    private val args: FotoDetailFragmentArgs by navArgs()
    private val viewModel: FotosViewModel by viewModels()

    private var currentImagem: Imagem? = null

    private var isDeleting = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFotoDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)
        setupToolbar()
        observeImagem()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /* -------------------- Toolbar -------------------- */

    private fun setupToolbar() = with(binding.toolbarFotoDetail) {
        setNavigationOnClickListener { findNavController().navigateUp() }

        // Como o menu é inflado via XML (app:menu), os itens já existem aqui.
        // Deixamos os ícones brancos programaticamente:
        menu.findItem(R.id.action_image_save)?.icon?.mutate()?.setTint(
            ContextCompat.getColor(requireContext(), android.R.color.white)
        )
        menu.findItem(R.id.action_image_delete)?.icon?.mutate()?.setTint(
            ContextCompat.getColor(requireContext(), android.R.color.white)
        )

        setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_image_save -> {
                    askSaveToGallery(); true
                }

                R.id.action_image_delete -> {
                    askDelete(); true
                }

                else -> false
            }
        }
    }

    /* -------------------- Dados e imagem -------------------- */

    private fun observeImagem() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeImagem(args.imagemId).collect { img ->
                    if (img == null) {
                        // SE estamos deletando, NÃO mostre o erro nem navegue — o fluxo de delete cuidará disso
                        if (isDeleting) return@collect
                        showErrorAndBack(); return@collect
                    }
                    currentImagem = img
                    bindData(img)
                }
            }
        }
    }

    private fun bindData(img: Imagem) = with(binding) {
        toolbarFotoDetail.title = img.nome

        progressDetail.isVisible = true
        detailScroll.isVisible = true

        tvData.text = getString(R.string.imagens_detail_date_fmt, img.data)
        tvTipo.text = getString(R.string.imagens_detail_type_fmt, img.tipo)
        tvDescricao.text = img.descricao?.takeIf { it.isNotBlank() } ?: "-"

        val url = img.fotoUrl
        if (url.isNullOrBlank()) {
            progressDetail.isVisible = false
            detailScroll.isVisible = true
            imgFull.setImageResource(R.drawable.ic_broken_image)
            return@with
        }

        imgFull.load(url) {
            crossfade(true)
            listener(
                onSuccess = { _, _ ->
                    progressDetail.isVisible = false
                    detailScroll.isInvisible = false
                },
                onError = { _, _ ->
                    progressDetail.isVisible = false
                    detailScroll.isInvisible = false
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.nota_view_image_error_open),
                        Toast.LENGTH_SHORT
                    ).show()
                    imgFull.setImageResource(R.drawable.ic_broken_image)
                }
            )
        }

    }

    private fun showErrorAndBack() {
        showSnackbarFragment(
            Constants.SnackType.ERROR.name,
            getString(R.string.generic_error),
            getString(R.string.image_viewer_error_loading),
            getString(R.string.generic_ok_upper_case)
        ) { findNavController().navigateUp() }
    }

    /* -------------------- Salvar na galeria -------------------- */

    private fun askSaveToGallery() {
        showSnackbarFragment(
            Constants.SnackType.WARNING.name,
            getString(R.string.image_save_confirm_title),
            getString(R.string.image_save_confirm_msg),
            getString(R.string.generic_yes_upper_case),
            onAction = { saveCurrent() },
            btnNegativeText = getString(R.string.generic_no_upper_case),
            onNegative = { /* stay */ }
        )
    }

    private fun saveCurrent() {
        val img = currentImagem ?: return
        val url = img.fotoUrl ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val loader = ImageLoader(requireContext())
            val request = ImageRequest.Builder(requireContext())
                .data(url)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            val bmp = (result as? SuccessResult)?.drawable
                ?.let { it as? BitmapDrawable }
                ?.bitmap

            if (bmp == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.image_save_error_toast),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val baos = ByteArrayOutputStream()
            val mime = guessMimeFromUrl(url)
            val format = when (mime.lowercase(Locale.ROOT)) {
                "image/png" -> android.graphics.Bitmap.CompressFormat.PNG
                "image/webp" -> @Suppress("DEPRECATION") android.graphics.Bitmap.CompressFormat.WEBP
                else -> android.graphics.Bitmap.CompressFormat.JPEG
            }
            if (!bmp.compress(format, 95, baos)) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.image_save_error_toast),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val displayName = buildFileName(mime)
            val success = FileUtils.saveImageToMediaStore(
                requireContext(), baos.toByteArray(), mime, displayName
            )
            val resId =
                if (success) R.string.image_save_success_toast else R.string.image_save_error_toast
            Toast.makeText(requireContext(), getString(resId), Toast.LENGTH_SHORT).show()
        }
    }

    private fun guessMimeFromUrl(url: String): String {
        val lower = url.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".heic") || lower.endsWith(".heif") -> "image/heic"
            else -> "image/jpeg"
        }
    }

    private fun buildFileName(mime: String): String {
        val ext = when (mime.lowercase(Locale.ROOT)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            else -> "jpg"
        }
        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(java.util.Date())
        return "obra_$ts.$ext"
    }

    /* -------------------- Excluir -------------------- */

    private fun askDelete() {
        showSnackbarFragment(
            Constants.SnackType.WARNING.name,
            getString(R.string.generic_warning),
            getString(R.string.imagens_delete_confirm_msg),
            getString(R.string.generic_yes_upper_case),
            onAction = { deleteNow() },
            btnNegativeText = getString(R.string.generic_no_upper_case),
            onNegative = { /* stay */ }
        )
    }

    private fun deleteNow() {
        val img = currentImagem ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            isDeleting = true   // <<< MARCA que a deleção começou
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deleteImagem(img).collect { st ->
                    when (st) {
                        is UiState.Loading -> {
                            binding.progressDetail.isVisible = true
                            binding.detailScroll.isVisible = false
                        }

                        is UiState.Success -> {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.nota_photo_removed_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                            // avisa a lista
                            val entry = findNavController().getBackStackEntry(R.id.fotosFragment)
                            entry.savedStateHandle["imagemDeletada"] = true
                            isDeleting = false
                            findNavController().navigateUp()
                        }

                        is UiState.ErrorRes -> {
                            isDeleting = false  // <<< libera o erro normal
                            binding.progressDetail.isVisible = false
                            binding.detailScroll.isVisible = true
                            showSnackbarFragment(
                                Constants.SnackType.ERROR.name,
                                getString(R.string.generic_error),
                                getString(st.resId),
                                getString(R.string.generic_ok_upper_case)
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}
