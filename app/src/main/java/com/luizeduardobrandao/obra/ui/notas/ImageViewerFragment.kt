package com.luizeduardobrandao.obra.ui.notas

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.databinding.FragmentImageViewerBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.FileUtils
import com.luizeduardobrandao.obra.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Tela de visualização de imagem (fullscreen) para a foto da Nota.
 *
 * • Recebe a URL pública (fotoUrl) via Safe-Args (recomendado).
 * • Carrega com Coil (com progress e tratamento de erro).
 * • Toolbar com voltar e ação de "Salvar".
 * • Ao salvar, confirma com SnackbarFragment e grava no MediaStore via FileUtils.
 */

class ImageViewerFragment : Fragment() {

    private var _binding: FragmentImageViewerBinding? = null
    private val binding get() = _binding!!

    private val args: ImageViewerFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentImageViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            // Toolbar: back + menu "Salvar" inflado via XML.
            toolbarImageViewer.setNavigationOnClickListener { findNavController().navigateUp() }
            toolbarImageViewer.menu.clear()
            toolbarImageViewer.inflateMenu(R.menu.menu_image_viewer)
            toolbarImageViewer.menu.findItem(R.id.action_image_save)
                .icon?.mutate()?.setTint(
                    androidx.core.content.ContextCompat.getColor(
                        requireContext(),
                        android.R.color.white
                    )
                )
            toolbarImageViewer.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_image_save) {
                    askToSaveImage()
                    true
                } else {
                    false
                }
            }

            // Carregar imagem
            val url = args.fotoUrl
            if (url.isBlank()) {
                tvError.isVisible = true
                progressImage.isVisible = false
                showSnackbarFragment(
                    type = Constants.SnackType.ERROR.name,
                    title = getString(R.string.generic_attention),
                    msg = getString(R.string.image_viewer_error_loading),
                    btnText = getString(R.string.generic_ok_upper_case)
                ) { findNavController().navigateUp() }
            } else {
                loadImage(url)
            }
        }
    }

    private fun loadImage(url: String) {
        with(binding) {
            progressImage.isVisible = true
            tvError.isVisible = false

            val request = ImageRequest.Builder(requireContext())
                .data(url)
                .target(
                    onStart = {
                        progressImage.isVisible = true
                        tvError.isVisible = false
                    },
                    onSuccess = { drawable ->
                        imgViewer.setImageDrawable(drawable)
                        progressImage.isVisible = false
                    },
                    onError = {
                        progressImage.isVisible = false
                        tvError.isVisible = true
                        showSnackbarFragment(
                            type = Constants.SnackType.ERROR.name,
                            title = getString(R.string.generic_attention),
                            msg = getString(R.string.image_viewer_error_loading),
                            btnText = getString(R.string.generic_ok_upper_case)
                        )
                    }
                )
                .build()

            // enqueue() retorna um Disposable, mas ignoramos — o método não precisa retornar nada.
            ImageLoader(requireContext()).enqueue(request)
        }
    }

    /** Exibe o BottomSheet para confirmar o salvamento no dispositivo. */
    private fun askToSaveImage() {
        showSnackbarFragment(
            type = Constants.SnackType.WARNING.name,
            title = getString(R.string.image_save_confirm_title),
            msg = getString(R.string.image_save_confirm_msg),
            btnText = getString(R.string.generic_yes_upper_case),
            onAction = { saveCurrentImage() },
            btnNegativeText = getString(R.string.generic_no_upper_case),
            onNegative = { /* permanece na visualização */ }
        )
    }

    /** Converte o drawable atual em bytes e salva no MediaStore via FileUtils. */
    private fun saveCurrentImage() {
        val drawable = binding.imgViewer.drawable
        val bmp = (drawable as? BitmapDrawable)?.bitmap

        if (bmp == null) {
            // Tentar obter o bitmap via Coil (se o drawable não for BitmapDrawable).
            val url = args.fotoUrl
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val loader = ImageLoader(requireContext())
                val request = ImageRequest.Builder(requireContext())
                    .data(url)
                    .allowHardware(false) // precisamos do bitmap em memória
                    .build()
                val result = loader.execute(request)
                val bitmap = (result as? SuccessResult)?.drawable
                    ?.let { it as? BitmapDrawable }
                    ?.bitmap

                if (bitmap == null) {
                    launch(Dispatchers.Main) { showSaveResult(false) }
                    return@launch
                }

                val success = persistBitmap(bitmap, guessMimeFromUrl(url))
                launch(Dispatchers.Main) { showSaveResult(success) }
            }
            return
        }

        // Já temos Bitmap do ImageView:
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val url = args.fotoUrl
            val success = persistBitmap(bmp, guessMimeFromUrl(url))
            launch(Dispatchers.Main) { showSaveResult(success) }
        }
    }

    private fun persistBitmap(bitmap: Bitmap, mime: String): Boolean {
        val baos = ByteArrayOutputStream()
        val format = when (mime.lowercase(Locale.ROOT)) {
            "image/png" -> Bitmap.CompressFormat.PNG
            "image/webp" -> {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }

            else -> Bitmap.CompressFormat.JPEG
        }
        val ok = bitmap.compress(format, 95, baos)
        if (!ok) return false

        val displayName = buildFileName(mime)
        return FileUtils.saveImageToMediaStore(
            context = requireContext(),
            bytes = baos.toByteArray(),
            mime = mime,
            displayName = displayName
        )
    }

    private fun showSaveResult(success: Boolean) {
        val msg = if (success) R.string.image_save_success_toast
        else R.string.image_save_error_toast

        android.widget.Toast.makeText(
            requireContext(),
            getString(msg),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    /** Deduz o MIME pelo sufixo da URL (fallback para JPEG). */
    private fun guessMimeFromUrl(url: String): String {
        val lower = url.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".heic") || lower.endsWith(".heif") -> "image/heic"
            else -> "image/jpeg"
        }
    }

    /** Monta um nome amigável pra galeria, ex.: nota_2025-08-20_153012.jpg */
    private fun buildFileName(mime: String): String {
        val ext = when (mime.lowercase(Locale.ROOT)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            else -> "jpg"
        }
        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(java.util.Date())
        return "nota_$ts.$ext"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}