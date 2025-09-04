package com.luizeduardobrandao.obra.ui.ia

import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentIaBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.FileUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import io.noties.markwon.Markwon

@AndroidEntryPoint
class IaFragment : Fragment() {

    private var _binding: FragmentIaBinding? = null
    private val binding get() = _binding!!

    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private val args: IaFragmentArgs by navArgs()
    private val viewModel: IaViewModel by viewModels()

    private var currentImageBytes: ByteArray? = null
    private var currentImageMime: String? = null

    private lateinit var markwon: Markwon

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val bytes = FileUtils.readBytes(requireContext(), uri)
        val mime = FileUtils.detectMime(requireContext(), uri)
        currentImageBytes = bytes
        currentImageMime = mime
        viewModel.setImage(bytes, mime)
        binding.tvPhotoLoaded.visibility = View.VISIBLE
        Toast.makeText(
            requireContext(),
            getString(R.string.nota_photo_pending_generic_toast),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        markwon = Markwon.create(requireContext())

        // Importante: não restaurar automaticamente o texto do EditText após rotação
        etProblem.isSaveEnabled = false

        // Links clicáveis no Markdown
        tvAnswer.movementMethod = android.text.method.LinkMovementMethod.getInstance()

        // Toolbar
        toolbarIa.setNavigationOnClickListener { findNavController().navigateUp() }
        toolbarIa.menu.findItem(R.id.action_open_history)?.icon?.setTint(
            ContextCompat.getColor(requireContext(), android.R.color.white)
        )
        toolbarIa.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_open_history) {
                findNavController().navigate(
                    IaFragmentDirections.actionIaToHistory(args.obraId)
                )
                true
            } else false
        }

        btnPickImageIa.setOnClickListener {
            pickImageLauncher.launch(arrayOf("image/*"))
        }

        // Habilita botão somente com 8..100 caracteres e mostra aviso no EditText
        etProblem.doAfterTextChanged {
            validateProblem()
        }

        // Estado inicial: botão desabilitado
        btnSendIa.isEnabled = false

        btnSendIa.setOnClickListener {
            root.hideKeyboard()
            val text = etProblem.text?.toString()?.trim().orElseEmpty()
            viewModel.sendQuestion(text)
        }

        btnSaveHistory.setOnClickListener {
            val text = etProblem.text?.toString()?.trim().orEmpty()
            val answer = tvAnswer.text?.toString()?.trim().orEmpty()
            showSnackbarFragment(
                type = Constants.SnackType.WARNING.name,
                title = getString(R.string.ia_snack_save_title),
                msg = getString(R.string.ia_snack_save_msg),
                btnText = getString(R.string.snack_button_yes),
                onAction = {
                    viewModel.saveToHistory(
                        title = text.take(100),
                        content = answer
                    )
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.ia_toast_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                btnNegativeText = getString(R.string.snack_button_no),
                onNegative = { /* nada */ }
            )
        }

        btnNewQuestion.setOnClickListener {
            showSnackbarFragment(
                type = Constants.SnackType.WARNING.name,
                title = getString(R.string.ia_snack_new_title),
                msg = getString(R.string.ia_snack_new_msg),
                btnText = getString(R.string.snack_button_yes),
                onAction = { resetAll() },
                btnNegativeText = getString(R.string.snack_button_no),
                onNegative = { /* nada */ }
            )
        }

        // Comportamento do FAB
        setupFabBehavior()

        collectSendState()
    }

    private fun collectSendState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sendState.collect { ui ->
                    when (ui) {
                        is UiState.Idle -> {
                            showLoading(false)
                            binding.cardAnswer.visibility = View.GONE
                            binding.rowActionsIa.visibility = View.GONE
                        }

                        is UiState.Loading -> {
                            showLoading(true)
                            binding.cardAnswer.visibility = View.GONE
                            binding.rowActionsIa.visibility = View.GONE
                        }

                        is UiState.Success -> {
                            showLoading(false)
                            binding.cardAnswer.visibility = View.VISIBLE
                            binding.tvAnswer.text = ui.data
                            markwon.setMarkdown(binding.tvAnswer, ui.data)

                            binding.rowActionsIa.visibility = View.VISIBLE

                            binding.scrollAnswer.post {
                                binding.scrollAnswer.scrollTo(0, 0)
                            }
                            binding.iaScroll.post {
                                val y = binding.cardAnswer.top -
                                        (16 * resources.displayMetrics.density).toInt()
                                binding.iaScroll.smoothScrollTo(0, y.coerceAtLeast(0))
                                binding.cardAnswer.sendAccessibilityEvent(
                                    android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED
                                )
                            }
                            // Reavalia a visibilidade do FAB quando o layout estiver pronto
                            binding.iaScroll.post { updateFabVisibility() }
                        }

                        is UiState.ErrorRes -> {
                            showLoading(false)
                            binding.cardAnswer.visibility = View.GONE
                            binding.rowActionsIa.visibility = View.GONE
                            showError(getString(ui.resId))
                        }

                        is UiState.Error -> {
                            showLoading(false)
                            binding.cardAnswer.visibility = View.GONE
                            binding.rowActionsIa.visibility = View.GONE
                            showError(ui.message)
                        }
                    }
                }
            }
        }
    }

    /** Valida o campo de problema: habilita o botão somente se 8..100 chars.
     *  Em caso de inválido, mostra um "bubble" de erro no EditText.
     */
    private fun validateProblem(): Boolean = with(binding) {
        val len = etProblem.text?.toString()?.trim()?.length ?: 0
        val isValid = len in 8..100

        // Mesmo padrão do WorkFragment: usar a propriedade 'error' do TextInputEditText
        etProblem.error = if (!isValid) {
            getString(R.string.ia_problem_length_error)
        } else null

        btnSendIa.isEnabled = isValid
        return isValid
    }

    private fun showLoading(show: Boolean) = with(binding) {
        aiOverlay.visibility = if (show) View.VISIBLE else View.GONE
        if (show) lottieIa.playAnimation() else lottieIa.cancelAnimation()
        // Desabilita inputs enquanto carrega
        btnPickImageIa.isEnabled = !show
        etProblem.isEnabled = !show

        // Quando sair do loading, revalida o campo para decidir o estado do botão
        btnSendIa.isEnabled = if (show) false else validateProblem()
    }

    private fun showError(msg: String) {
        binding.root.context?.let {
            showSnackbarFragment(
                type = Constants.SnackType.ERROR.name,
                title = getString(R.string.snack_error),
                msg = msg,
                btnText = getString(R.string.snack_button_ok)
            )
        }
    }

    private fun resetAll() = with(binding) {
        // Limpa UI
        etProblem.setText("")
        tvAnswer.text = ""
        cardAnswer.visibility = View.GONE
        rowActionsIa.visibility = View.GONE
        tvPhotoLoaded.visibility = View.GONE

        // Limpa dados auxiliares
        currentImageBytes = null
        currentImageMime = null
        viewModel.clearImage()

        // Zera estado do ViewModel para sobreviver à rotação sem restaurar o card
        viewModel.resetSendState()

        // Botão volta desabilitado após "Nova dúvida"
        btnSendIa.isEnabled = false

        iaScroll.scrollTo(0, 0)
        fabScrollDownIa.isGone = true
        fabScrollUpIa.isGone = true
    }

    private fun String?.orElseEmpty() = this ?: ""

    /** Mostra FAB de descer se há rolagem e não está no fim;
     *  mostra FAB de subir se há rolagem e está no fim, com transição elegante. */
    private fun updateFabVisibility() {
        val b = _binding ?: return  // view já destruída -> não faz nada
        with(b) {
            val child = iaScroll.getChildAt(0)
            val hasScrollable = child != null && child.height > iaScroll.height
            val atBottom = !iaScroll.canScrollVertically(1)

            val showDown = hasScrollable && !atBottom
            val showUp = hasScrollable && atBottom

            fabScrollDownIa.toggleFabAnimated(showDown)
            fabScrollUpIa.toggleFabAnimated(showUp)
        }
    }

    /** Configura listeners para aparecer/desaparecer conforme rolagem e clique. */
    private fun setupFabBehavior() = with(binding) {
        // ambos começam invisíveis
        fabScrollDownIa.isGone = true
        fabScrollUpIa.isGone = true

        // Reage a qualquer rolagem do usuário
        iaScroll.setOnScrollChangeListener { _, _, _, _, _ ->
            updateFabVisibility()
        }

        // Ao clicar para descer: rola até o fim e esconde o FAB de descer;
        // ao chegar no fim, updateFabVisibility mostrará o FAB de subir.
        fabScrollDownIa.setOnClickListener {
            iaScroll.post {
                iaScroll.smoothScrollTo(0, iaScroll.getChildAt(0).bottom)
                fabScrollDownIa.isGone = true
                iaScroll.post { updateFabVisibility() }
            }
        }

        // Ao clicar para subir: rola ao topo e esconde o FAB de subir;
        // updateFabVisibility decide se volta a mostrar o de descer.
        fabScrollUpIa.setOnClickListener {
            iaScroll.post {
                iaScroll.smoothScrollTo(0, 0)
                fabScrollUpIa.isGone = true
                iaScroll.post { updateFabVisibility() }
            }
        }

        // Quando o layout “assentar”, revalida (ex.: depois que o card aparece)
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            updateFabVisibility() // já é safe
        }
        iaScroll.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    // Helper de animação para mostrar/ocultar FAB com fade + scale
    private fun View.toggleFabAnimated(show: Boolean) {
        val interp = FastOutSlowInInterpolator()
        if (show && isGone) {
            // aparecer
            isGone = false
            alpha = 0f
            scaleX = 0.85f
            scaleY = 0.85f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(160L)
                .setInterpolator(interp)
                .start()
        } else if (!show && !isGone) {
            // desaparecer
            animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(140L)
                .setInterpolator(interp)
                .withEndAction {
                    isGone = true
                    // reset (evita ficar “achatado” quando voltar)
                    alpha = 1f
                    scaleX = 1f
                    scaleY = 1f
                }
                .start()
        }
    }

    override fun onDestroyView() {
        // Remover listeners de rolagem e de layout para evitar callbacks após destruir a view
        _binding?.let { b ->
            b.iaScroll.setOnScrollChangeListener(null as View.OnScrollChangeListener?)
            globalLayoutListener?.let { gl ->
                b.iaScroll.viewTreeObserver.removeOnGlobalLayoutListener(gl)
            }
        }
        globalLayoutListener = null

        _binding = null
        super.onDestroyView()
    }
}