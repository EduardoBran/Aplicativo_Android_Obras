package com.luizeduardobrandao.obra.ui.ia

import android.annotation.SuppressLint
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentIaBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.ia.dialogs.QuestionTypeDialog
import com.luizeduardobrandao.obra.ui.ia.dialogs.QuestionTypeDialog.Category
import com.luizeduardobrandao.obra.ui.ia.dialogs.QuestionTypeDialog.CalcSub
import com.luizeduardobrandao.obra.ui.ia.dialogs.QuestionTypeDialog.Selection
import com.luizeduardobrandao.obra.utils.applyResponsiveButtonSizingGrowShrink
import com.luizeduardobrandao.obra.utils.applyFullWidthButtonSizingGrowShrink
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.FileUtils
import com.luizeduardobrandao.obra.utils.syncTextSizesGroup
import com.luizeduardobrandao.obra.utils.VoiceInputHelper
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import androidx.core.net.toUri


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

    // Voz
    private lateinit var voiceHelper: VoiceInputHelper

    // Controle de visibilidade: só mostra campo/botão após escolher tipo
    private var hasChosenType = false
    private var isLoadingIa = false

    // Seleção corrente (para enviar com o ViewModel)
    private var currentSelection: Selection = Selection(Category.GERAL, null)

    // Permissão de Localização
    private lateinit var fusedClient: FusedLocationProviderClient
    private var pendingSelectionForLocation: Selection? = null

    // Origem do pedido de permissão
    private enum class LocationRequestOrigin { ON_TYPE_SELECTION, ON_SEND_CLICK }

    private var pendingLocationOrigin: LocationRequestOrigin? = null


    private val requestLocationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        val sel = pendingSelectionForLocation
        val origin = pendingLocationOrigin
        pendingSelectionForLocation = null
        pendingLocationOrigin = null

        if (granted) {
            // ✅ Permissão concedida
            if (origin == LocationRequestOrigin.ON_SEND_CLICK && sel != null) {
                // Somente aqui disparamos o envio (o usuário clicou "Enviar")
                sendWithLocation(sel)
            }
            // Se veio da seleção do tipo, não fazemos nada: o usuário vai digitar e depois clicar "Enviar".
        } else {
            // ❌ Permissão negada: mostrar bottom sheet e reiniciar a página ao confirmar
            showSnackbarFragment(
                type = Constants.SnackType.ERROR.name,
                title = getString(R.string.snack_error),
                msg = getString(R.string.ia_location_permission_denied),
                btnText = getString(R.string.snack_button_ok),
                onAction = {
                    // "Reiniciar a página"
                    // Opção leve: resetar tudo
                    resetAll()
                    // Se quiser recriar a Fragment: requireActivity().recreate()
                    // (use apenas se realmente precisar recarregar tudo)
                }
            )
        }
    }

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

        // Ditado por voz: conecta o ícone do TextInputLayout
        voiceHelper = VoiceInputHelper(
            fragment = this@IaFragment,   // <-- use o Fragment, não o binding
            etTarget = etProblem,
            tilContainer = tilProblem,
            canStartVoiceInput = { hasChosenType },  // <-- passe a lambda nomeada
            // appendRecognizedText = true            // (opcional, já é default)
        )
        voiceHelper.attach()

        // Localização
        fusedClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Importante: não restaurar automaticamente o texto do EditText após rotação
        etProblem.isSaveEnabled = false

        // Restaura a UI a partir do ViewModel (sobrevive à rotação)
        hasChosenType = viewModel.hasChosenType.value
        if (hasChosenType) {
            // mantenha currentSelection sincronizado
            currentSelection = viewModel.selection.value
            applySelectionUi(currentSelection)
            showProblemSection(true)
            updateSendButtonVisibility()
            updateChosenTypeLabel(currentSelection) // <-- ADICIONE ESTA LINHA
        } else {
            showProblemSection(false)
            updateChosenTypeLabel(null) // <-- garante escondido ao iniciar
        }

        // Links clicáveis no Markdown
        tvAnswer.movementMethod = android.text.method.LinkMovementMethod.getInstance()

        // Copiar texto do card
        btnCopyAnswer.setOnClickListener {
            val text = tvAnswer.text?.toString().orEmpty()
            val clipboard = requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("IA Answer", text))
            Toast.makeText(
                requireContext(),
                getString(R.string.ia_copy_success),
                Toast.LENGTH_SHORT
            ).show()
        }

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

        btnTipoDuvida.setOnClickListener {
            QuestionTypeDialog.pick(
                context = requireContext(),
                preselected = currentSelection.category
            ) { selection ->
                // Guarda a seleção
                currentSelection = selection
                viewModel.setSelection(selection)
                viewModel.setHasChosenType(true)

                // Ajusta UI (hint/instruções quando for cálculo de material)
                applySelectionUi(selection)

                // Revela seção (campo + botão Enviar)
                hasChosenType = true
                showProblemSection(true)
                updateSendButtonVisibility()

                // Atualiza o rótulo abaixo do "Nova Dúvida"
                updateChosenTypeLabel(selection)

                // Pedir permissão
                if (selection.category == Category.PESQUISA_DE_LOJA) {
                    // Apenas solicita a permissão já na escolha do tipo
                    // (origem = seleção; NÃO enviamos nada aqui)
                    val fine = ActivityCompat.checkSelfPermission(
                        requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val coarse = ActivityCompat.checkSelfPermission(
                        requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!fine && !coarse) {
                        pendingSelectionForLocation = selection
                        pendingLocationOrigin = LocationRequestOrigin.ON_TYPE_SELECTION
                        requestLocationPermissions.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                    // Se já tem permissão, beleza: só deixa o usuário DIGITAR e depois clicar "Enviar".
                }
            }
        }

        // Habilita botão somente com 8..100 caracteres e mostra aviso no EditText
        etProblem.doAfterTextChanged {
            validateProblem()
            updateSendButtonVisibility()
        }

        // Estado inicial: botão desabilitado
        btnSendIa.isEnabled = false

        btnSendIa.setOnClickListener {
            root.hideKeyboard()
            val text = etProblem.text?.toString()?.trim().orElseEmpty()
            if (!validateProblem()) return@setOnClickListener

            if (currentSelection.category == Category.PESQUISA_DE_LOJA) {
                // ✅ Abre o Maps com a consulta digitada
                openMapsWithQuery(text)
            } else {
                viewModel.sendQuestion(text, currentSelection, extraContext = null)
            }
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

        // ── Responsividade inicial dos botões
        binding.root.doOnPreDraw {
            // "Enviar" costuma ocupar a largura toda -> usa preset full-width
            binding.btnSendIa.applyFullWidthButtonSizingGrowShrink()
            binding.btnTipoDuvida.applyFullWidthButtonSizingGrowShrink()

            // "Escolher imagem" é um botão solo
            binding.btnPickImageIa.applyResponsiveButtonSizingGrowShrink()

            // Os de ação (Salvar / Nova dúvida) são lado a lado, mas só aparecem depois;
            // ainda assim, se já estiverem visíveis aqui, nivelamos.
            if (binding.rowActionsIa.isVisible) {
                binding.btnSaveHistory.applyResponsiveButtonSizingGrowShrink()
                binding.btnNewQuestion.applyResponsiveButtonSizingGrowShrink()
                binding.rowActionsIa.syncTextSizesGroup(
                    binding.btnSaveHistory,
                    binding.btnNewQuestion
                )
            }
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

                            // Responsividade e nivelamento de "Salvar" / "Nova dúvida"
                            binding.rowActionsIa.doOnPreDraw {
                                binding.btnSaveHistory.applyResponsiveButtonSizingGrowShrink()
                                binding.btnNewQuestion.applyResponsiveButtonSizingGrowShrink()
                                binding.rowActionsIa.syncTextSizesGroup(
                                    binding.btnSaveHistory,
                                    binding.btnNewQuestion
                                )
                            }

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
        // Se ainda não escolheu o tipo, não valida nem exibe erro
        if (!hasChosenType) {
            tilProblem.error = null
            return false
        }

        val len = etProblem.text?.toString()?.trim()?.length ?: 0
        val isValid = len in 8..100

        tilProblem.error = if (!isValid) {
            getString(R.string.ia_problem_length_error)
        } else null

        return isValid
    }

    private fun showLoading(show: Boolean) = with(binding) {
        aiOverlay.visibility = if (show) View.VISIBLE else View.GONE
        if (show) lottieIa.playAnimation() else lottieIa.cancelAnimation()

        // Desabilita inputs enquanto carrega
        btnPickImageIa.isEnabled = !show
        etProblem.isEnabled = !show
        btnTipoDuvida.isEnabled = !show

        // Atualiza flag de loading e recalcula visibilidade/habilitação do Enviar
        isLoadingIa = show
        updateSendButtonVisibility()
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
        tvChosenType.visibility = View.GONE
        tvChosenType.text = ""
        binding.tvChosenType.visibility = View.GONE
        binding.tvChosenType.text = ""

        // Limpa dados auxiliares
        currentImageBytes = null
        currentImageMime = null
        viewModel.clearImage()

        // Zera estado do ViewModel para sobreviver à rotação sem restaurar o card
        viewModel.resetSendState()
        viewModel.setHasChosenType(false)

        // Botão volta desabilitado após "Nova dúvida"
        btnSendIa.isEnabled = false

        // Volta ao estado inicial (campo e botão escondidos até escolher o tipo)
        hasChosenType = false
        showProblemSection(false)
        binding.btnSendIa.isVisible = false
        binding.btnSendIa.isEnabled = false

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

    /** Helper para checar permissões **/
    private fun hasLocationPermission(): Boolean {
        val ctx = requireContext()
        val fine = ActivityCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /** Obtém a última localização conhecida e envia a pergunta com contexto de localização. */
    private fun sendWithLocation(sel: Selection) {
        val text = binding.etProblem.text?.toString()?.trim().orEmpty()

        if (!hasLocationPermission()) {
            // Sem permissão -> segue sem localização
            val extra = getString(R.string.ia_store_location_unavailable)
            viewModel.sendQuestion(text, sel, extraContext = extra)
            return
        }

        try {
            // Chama o mét0do interno que realmente usa a localização
            sendWithLocationInternal(sel, text)
        } catch (se: SecurityException) {
            val extra = getString(R.string.ia_store_location_unavailable)
            viewModel.sendQuestion(text, sel, extraContext = extra)
        }
    }

    /** Helper interno que de fato acessa a API de localização, anotado com a permissão **/
    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    @SuppressLint("MissingPermission") // justificativa: checado no wrapper + try/catch
    private fun sendWithLocationInternal(sel: Selection, text: String) {
        fusedClient.lastLocation
            .addOnSuccessListener { loc ->
                val extra = if (loc != null) {
                    getString(R.string.ia_store_location_format, loc.latitude, loc.longitude)
                } else {
                    getString(R.string.ia_store_location_unavailable)
                }
                viewModel.sendQuestion(text, sel, extraContext = extra)
            }
            .addOnFailureListener {
                val extra = getString(R.string.ia_store_location_unavailable)
                viewModel.sendQuestion(text, sel, extraContext = extra)
            }
    }

    /** Mostra/oculta o bloco de descrição + instruções + botão Enviar */
    private fun showProblemSection(show: Boolean) = with(binding) {
        tilProblem.visibility = if (show) View.VISIBLE else View.GONE
        // As instruções aparecem só para Cálculo de Material (applySelectionUi cuidará disso)
        if (!show) {
            tvCalcHelper.visibility = View.GONE
            tvCalcHelper.text = ""
        }
        updateSendButtonVisibility()
    }

    /** Habilita/mostra o botão Enviar:
     *  - Invisível até escolher o Tipo de Dúvida
     *  - Depois: visível; habilita com 8..100 chars e sem loading */
    private fun updateSendButtonVisibility() {
        val valid = validateProblem()
        binding.btnSendIa.isVisible = hasChosenType && !isLoadingIa
        binding.btnSendIa.isEnabled = hasChosenType && valid && !isLoadingIa
    }

    /** Ajusta hint e instruções para Cálculo de Material; demais categorias usam hint padrão. */
    private fun applySelectionUi(sel: Selection) = with(binding) {
        if (sel.category == Category.CALCULO_MATERIAL) {
            // se você criou a string específica, use-a; se não, mantenha a genérica
            tilProblem.hint = getString(R.string.ia_hint_problem_calc)
            tvCalcHelper.visibility = View.VISIBLE
            val sub = sel.sub ?: CalcSub.ALVENARIA_E_ESTRUTURA
            tvCalcHelper.text = when (sub) {
                CalcSub.ALVENARIA_E_ESTRUTURA -> getString(R.string.ia_calc_hint_alvenaria)
                CalcSub.ELETRICA -> getString(R.string.ia_calc_hint_eletrica)
                CalcSub.HIDRAULICA -> getString(R.string.ia_calc_hint_hidraulica)
                CalcSub.PINTURA -> getString(R.string.ia_calc_hint_pintura)
                CalcSub.PISO -> getString(R.string.ia_calc_hint_piso)
            }
        } else {
            tilProblem.hint = getString(R.string.ia_hint_problem)
            tvCalcHelper.visibility = View.GONE
            tvCalcHelper.text = ""
        }
    }

    /** Função para atualizar o texto/visibilidade do rótulo **/
    private fun updateChosenTypeLabel(sel: Selection?) = with(binding) {
        if (!hasChosenType || sel == null) {
            tvChosenType.visibility = View.GONE
            tvChosenType.text = ""
            return@with
        }
        val text = if (sel.category == Category.CALCULO_MATERIAL) {
            val sub = sel.sub ?: CalcSub.ALVENARIA_E_ESTRUTURA
            getString(R.string.ia_chosen_type_calc, subToString(sub))
        } else {
            getString(R.string.ia_chosen_type_generic, categoryToString(sel.category))
        }
        tvChosenType.text = text
        tvChosenType.visibility = View.VISIBLE
    }

    /** Helpers para converter enum → string legível **/
    private fun categoryToString(cat: Category): String = when (cat) {
        Category.GERAL -> getString(R.string.ia_cat_duvida_geral)
        Category.CALCULO_MATERIAL -> getString(R.string.ia_cat_calculo_material)
        Category.ALVENARIA_E_ESTRUTURA -> getString(R.string.ia_cat_alvenaria_estrutura)
        Category.INSTALACOES_ELETRICAS -> getString(R.string.ia_cat_instalacoes_eletricas)
        Category.INSTALACOES_HIDRAULICAS -> getString(R.string.ia_cat_instalacoes_hidraulicas)
        Category.PINTURA_E_ACABAMENTOS -> getString(R.string.ia_cat_pintura_acabamentos)
        Category.PLANEJAMENTO_E_CONSTRUCAO -> getString(R.string.ia_cat_planejamento_construcao)
        Category.LIMPEZA_POS_OBRA -> getString(R.string.ia_cat_limpeza_pos_obra)
        Category.PESQUISA_DE_LOJA -> getString(R.string.ia_cat_pesquisa_loja)
    }

    private fun subToString(sub: CalcSub): String = when (sub) {
        CalcSub.ALVENARIA_E_ESTRUTURA -> getString(R.string.ia_sub_alvenaria_estrutura)
        CalcSub.ELETRICA -> getString(R.string.ia_sub_eletrica)
        CalcSub.HIDRAULICA -> getString(R.string.ia_sub_hidraulica)
        CalcSub.PINTURA -> getString(R.string.ia_sub_pintura)
        CalcSub.PISO -> getString(R.string.ia_sub_piso)
    }

    // Helpers para abir Maps

    /** Abre o Google Maps com a busca "perto de mim" usando lat/lon se disponíveis. */
    private fun openMapsWithQuery(queryRaw: String) {
        val query = queryRaw.trim().ifEmpty { return }
        // Tenta usar localização (se tiver permissão); se não, cai no "0,0"
        if (hasLocationPermission()) {
            try {
                fusedClient.lastLocation
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            openMapsWithQueryNear(query, loc.latitude, loc.longitude)
                        } else {
                            openMapsWithQueryFallback(query)
                        }
                    }
                    .addOnFailureListener {
                        openMapsWithQueryFallback(query)
                    }
            } catch (_: SecurityException) {
                openMapsWithQueryFallback(query)
            }
        } else {
            openMapsWithQueryFallback(query)
        }
    }

    /** Abre Maps ancorando no ponto informado. */
    private fun openMapsWithQueryNear(queryRaw: String, lat: Double, lon: Double) {
        val queryEnc = Uri.encode(queryRaw)
        // Formato: geo:lat,lon?q=consulta
        val uri = "geo:$lat,$lon?q=$queryEnc".toUri()
        launchMapsIntent(uri, queryRaw)
    }

    /** Abre Maps sem coordenadas (o app do Maps usará a localização do dispositivo se puder). */
    private fun openMapsWithQueryFallback(queryRaw: String) {
        val queryEnc = Uri.encode(queryRaw)
        // Formato: geo:0,0?q=consulta   (Maps tenta usar a localização atual do usuário)
        val uri = "geo:0,0?q=$queryEnc".toUri()
        launchMapsIntent(uri, queryRaw)
    }

    /** Dispara o Intent para o Google Maps; se não houver, cai no browser. */
    private fun launchMapsIntent(geoUri: Uri, originalQuery: String) {
        val mapsIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, geoUri)
            .setPackage("com.google.android.apps.maps")

        try {
            startActivity(mapsIntent)
        } catch (_: android.content.ActivityNotFoundException) {
            // Fallback web
            val webUri =
                "https://www.google.com/maps/search/?api=1&query=${Uri.encode(originalQuery)}".toUri()
            val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, webUri)
            startActivity(webIntent)
        }
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