package com.luizeduardobrandao.obra.ui.notas

import android.Manifest
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.AutoFillResult
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.data.model.TipoNota
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentNotaRegisterBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.extensions.bindScrollToBottomFabBehavior
import com.luizeduardobrandao.obra.ui.extensions.isAtBottom
import com.luizeduardobrandao.obra.ui.extensions.updateFabVisibilityAnimated
import com.luizeduardobrandao.obra.ui.notas.adapter.NotaPagerAdapter
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.FileUtils
import com.luizeduardobrandao.obra.utils.showMaterialDatePickerBrToday
import com.luizeduardobrandao.obra.utils.showMaterialDatePickerBrWithInitial
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.core.widget.doAfterTextChanged
import java.util.*
import android.util.TypedValue
import java.text.NumberFormat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder

@AndroidEntryPoint
class NotaRegisterFragment : Fragment() {

    private var _binding: FragmentNotaRegisterBinding? = null
    private val binding get() = _binding!!

    private val args: NotaRegisterFragmentArgs by navArgs()
    private val viewModel: NotasViewModel by viewModels()

    private var isEdit = false
    private lateinit var notaOriginal: Nota   // usado em edição

    // ─────────────── Foto (galeria/câmera) ───────────────
    private var tempCameraUri: Uri? = null

    // controla exclusivamente o loading do botão (salvar/alterar)
    private var isSaving = false

    // Fecha a tela apenas quando o sucesso vier de um salvamento (criar/atualizar)
    private var shouldCloseAfterSave = false

    // Pré-visualização local (antes de salvar a nota)
    private var localPreviewBytes: ByteArray? = null
    private var localPreviewMine: String? = null

    private val hasLocalPreview get() = localPreviewBytes != null
    private val hasRemotePhoto
        get() =
            isEdit && ::notaOriginal.isInitialized && notaOriginal.fotoUrl != null

    // Launcher: abrir seletor de imagens (galeria/arquivos)
    private val openImagePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        // Lê bites + MINE
        val bytes = FileUtils.readBytes(requireContext(), uri)
        val mine = FileUtils.detectMime(requireContext(), uri)

        // Confirma com snackbarfragment
        confirmAddPhoto(bytes, mine)
    }

    // Launcher: câmera (grava na Uri temporária do FileProvider)
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) return@registerForActivityResult

        val uri = tempCameraUri ?: return@registerForActivityResult
        val bytes = FileUtils.readBytes(requireContext(), uri)
        val mine = FileUtils.detectMime(requireContext(), uri)

        confirmAddPhoto(bytes, mine)
    }

    // (Opcional) Permissão de câmera quando necessário (SDK 23+)
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCameraCapture() else {
            showSnackbarFragment(
                type = Constants.SnackType.ERROR.name,
                title = getString(R.string.snack_error),
                msg = getString(R.string.nota_photo_error_permission_camera),
                btnText = getString(R.string.snack_button_ok)
            )
        }
    }

    // Foto: exclusão pendente (somente em edição)
    private var isPhotoDeletePending = false

    // IA: contador de tentativas de autofill (reinicia a cada nova foto confirmada)
    private var autofillAttemptCount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotaRegisterBinding.inflate(inflater, container, false)
        binding.tilDataNota.onCaptionToggle {
            adjustSectionTopSpacing(binding.tilDataNota, binding.tvStatusTitle, collapsedTopDp = 10)
        }
        binding.tilValorNota.onCaptionToggle {
            adjustSectionTopSpacing(binding.tilValorNota, binding.tvPhotoTitle, collapsedTopDp = 34)
        }
        return binding.root
    }

    /* ───────────────────────── lifecycle ───────────────────────── */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            toolbarNotaReg.setNavigationOnClickListener { handleBackPress() }

            etDataNota.setOnClickListener {
                val atual = binding.etDataNota.text?.toString().orEmpty()
                if (atual.isNotBlank()) {
                    // Abre já posicionado na data que está no campo (ex.: preenchida pela IA)
                    showMaterialDatePickerBrWithInitial(atual) { chosen ->
                        applyChosenNotaDate(chosen)
                    }
                } else {
                    // Campo vazio → abre em hoje
                    showMaterialDatePickerBrToday { chosen ->
                        applyChosenNotaDate(chosen)
                    }
                }
            }
            btnSaveNota.setOnClickListener { onSaveClick() }

            // Form validation
            btnSaveNota.isEnabled = false
            etNomeMaterial.doAfterTextChanged { validateForm() }
            etLoja.doAfterTextChanged { validateForm() }
            etValorNota.doAfterTextChanged { validateForm() }
            listOf(
                cbPintura,
                cbPedreiro,
                cbHidraulica,
                cbEletrica,
                cbLimpeza,
                cbOutros
            ).forEach { cb ->
                cb.setOnCheckedChangeListener { _, _ -> validateForm() }
            }

            // Foto: cliques dos botões/visualização/exclusão
            btnPickImage.setOnClickListener { startPickImage() }
            btnTakePhoto.setOnClickListener { ensureCameraAndStart() }
            tvPhotoPreview.setOnClickListener { showPhotoPreviewOverlay() }
            btnDeletePhoto.setOnClickListener { onDeletePhotoClick() }

            // Overlay de pré-visualização
            photoPreviewOverlay.setOnClickListener { hidePhotoPreviewOverlay() }
            btnClosePreview.setOnClickListener { hidePhotoPreviewOverlay() }

            // Edição?
            args.notaId?.let { notaId ->
                isEdit = true
                observeNota(notaId)
            } ?: run {
                // Não é edição → inicia UI de foto no modo "sem foto"
                updatePhotoUiFromState()
            }

            // Floating Bottom Rolagem – mostra só quando: edição && !salvando && !no final
            bindScrollToBottomFabBehavior(
                fab = binding.fabScrollDown,
                scrollView = binding.notaRegScroll,
                isEditProvider = { isEdit },
                isSavingProvider = { isSaving }
            )

            collectOperationState()
            collectAutoFillState() // Observar o estado da IA e reagir
            validateForm()
            binding.root.post {
                adjustSectionTopSpacing(
                    binding.tilDataNota,
                    binding.tvStatusTitle,
                    collapsedTopDp = 10,
                    animate = false
                )
                adjustSectionTopSpacing(
                    binding.tilValorNota,
                    binding.tvPhotoTitle,
                    collapsedTopDp = 34,
                    animate = false
                )
            }
            reevalScrollFab()
        }
        // Intercepta o botão físico/gesto de voltar
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            handleBackPress()
        }
    }

    /* ────────────────────── Observa Nota (edição) ────────────────────── */
    private fun observeNota(notaId: String) {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeNota(args.obraId, notaId).collect { nota ->
                    nota ?: return@collect
                    notaOriginal = nota
                    prefillFields(nota)
                    updatePhotoUiFromState()  // ajusta botões/cartão de foto conforme estado
                    validateForm()            // revalida depois de preencher
                }
            }
        }
    }

    /* ────────────────────── Validação & Salvar ────────────────────── */
    private fun onSaveClick() = with(binding) {
        shouldCloseAfterSave = true   // Só fecha depois de salvar
        isSaving = true               // Liga o loading do botão

        progress(true)
        // Role para o final imediatamente ao salvar para garantir o FAB visível na borda
        binding.notaRegScroll.post {
            val child = binding.notaRegScroll.getChildAt(0)
            if (child != null) {
                binding.notaRegScroll.smoothScrollTo(0, child.bottom)
            }
            reevalScrollFab()
        }

        val nome = etNomeMaterial.text.toString().trim()
        val loja = etLoja.text.toString().trim()
        val data = etDataNota.text.toString()
        val valor =
            etValorNota.text.toString().replace(',', '.').toDoubleOrNull() ?: -1.0
        val status = if (rbStatusPagar.isChecked) NotaPagerAdapter.STATUS_A_PAGAR
        else NotaPagerAdapter.STATUS_PAGO

        val tipos = mutableListOf<String>()
        listOf(cbPintura, cbPedreiro, cbHidraulica, cbEletrica, cbLimpeza, cbOutros)
            .filter { it.isChecked }
            .forEach { tipos.add(it.text.toString()) }

        if (nome.isBlank() || data.isBlank() || tipos.isEmpty() || valor <= 0.0) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.snack_error),
                getString(R.string.nota_reg_error_required),
                getString(R.string.snack_button_ok)
            )
            return
        }

        // monta a nota base
        val baseNota = Nota(
            id = args.notaId.orEmpty(),
            nomeMaterial = nome,
            descricao = etDescricaoMaterial.text.toString().trim(),
            loja = loja,
            tipos = tipos,
            data = data,
            status = status,
            valor = valor
        )

        // ► se for edição e você NÃO selecionou nova foto, preserve a foto antiga
        val finalNota =
            if (isEdit && !hasLocalPreview && hasRemotePhoto)
                baseNota.copy(
                    fotoUrl = notaOriginal.fotoUrl,
                    fotoPath = notaOriginal.fotoPath
                )
            else baseNota

        // chamadas mantendo a foto quando apropriado
        if (isEdit) {
            when {
                // 1) Substituição / inclusão de foto nova
                hasLocalPreview -> {
                    // ViewModel deve cuidar da troca (upload + eventual limpeza da antiga)
                    viewModel.updateNotaWithOptionalPhoto(notaOriginal, finalNota)
                }

                // 2) Exclusão pendente, sem nova foto
                isPhotoDeletePending -> {
                    // Limpa os campos de foto na nota e efetiva a exclusão no storage
                    val semFoto = finalNota.copy(fotoUrl = null, fotoPath = null)
                    // 2.1) apagar do storage + limpar campos
                    viewModel.deleteNotaPhotoAndClearField(notaOriginal)
                    // 2.2) atualizar demais campos da nota
                    viewModel.updateNota(notaOriginal, semFoto)
                }

                // 3) Sem mudanças de foto → apenas atualizar a nota
                else -> {
                    viewModel.updateNota(notaOriginal, finalNota)
                }
            }
        } else {
            if (hasLocalPreview) viewModel.createNotaWithOptionalPhoto(finalNota)
            else viewModel.addNota(finalNota)
        }

        btnSaveNota.isEnabled = false
    }

    /* ────────────────────── State collector ────────────────────── */
    private fun collectOperationState() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Loading -> {
                            // Só mostra o loading do botão quando estiver salvando
                            if (isSaving) progress(true)
                        }

                        is UiState.Success -> {
                            isSaving = false

                            if (shouldCloseAfterSave) {
                                val msgRes =
                                    if (isEdit) R.string.nota_toast_updated
                                    else R.string.nota_toast_added
                                Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show()
                                binding.root.hideKeyboard()

                                // Não desligue o progress aqui.
                                // Poste a navegação para o próximo frame para a UI ter tempo de desenhar o spinner.
                                binding.root.post {
                                    findNavController().navigateUp()
                                    shouldCloseAfterSave = false
                                }
                            } else {
                                // operações que NÃO fecham a tela (ex.: excluir foto)
                                progress(false)
                                binding.btnSaveNota.isEnabled = true
                            }
                        }

                        is UiState.ErrorRes -> {
                            // erro em qualquer operação: some loading do botão e reabilita
                            progress(false)
                            isSaving = false
                            shouldCloseAfterSave = false
                            binding.btnSaveNota.isEnabled = true
                            showSnackbarFragment(
                                Constants.SnackType.ERROR.name,
                                getString(R.string.snack_error),
                                getString(ui.resId),
                                getString(R.string.snack_button_ok)
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    // Observar o estado da IA e reagir
    private fun collectAutoFillState() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.autoFillState.collect { ai ->
                    when (ai) {
                        is NotasViewModel.AutoFillUiState.Idle -> {
                            hideAutoFillOverlay()
                        }

                        is NotasViewModel.AutoFillUiState.Running -> {
                            showAutoFillOverlay()
                        }

                        is NotasViewModel.AutoFillUiState.Success -> {
                            hideAutoFillOverlay()
                            applyAutofillToForm(ai.data)
                            validateForm()
                            // sucesso → zera contador
                            autofillAttemptCount = 0
                            // rola até o final, como você já faz
                            binding.notaRegScroll.post {
                                val child = binding.notaRegScroll.getChildAt(0)
                                if (child != null) binding.notaRegScroll.smoothScrollTo(
                                    0,
                                    child.bottom
                                )
                                reevalScrollFab()
                            }
                        }

                        is NotasViewModel.AutoFillUiState.Error -> {
                            hideAutoFillOverlay()
                            when {
                                // Houve tentativa iniciada via diálogo e ainda temos até 2 re-tentativas
                                autofillAttemptCount in 1..2 -> {
                                    showAutofillRetryDialog()
                                }
                                // Estourou o limite (3) → cai no Snackbar de erro padrão e reseta
                                autofillAttemptCount >= 3 -> {
                                    autofillAttemptCount = 0
                                    viewModel.resetAutofillFlow()

                                    showSnackbarFragment(
                                        type = Constants.SnackType.ERROR.name,
                                        title = getString(R.string.snack_error),
                                        msg = getString(R.string.ia_autofill_error_message),
                                        btnText = getString(R.string.snack_button_ok)
                                    )
                                }
                                // Fallback: erro sem ter vindo do fluxo do diálogo (raro)
                                else -> {
                                    showSnackbarFragment(
                                        type = Constants.SnackType.ERROR.name,
                                        title = getString(R.string.snack_error),
                                        msg = getString(R.string.ia_autofill_error_message),
                                        btnText = getString(R.string.snack_button_ok)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /* ────────────────────── Utilitários de formulário ────────────────────── */

    // Preenchimento dos campos em modo de edição
    private fun prefillFields(n: Nota) = with(binding) {
        toolbarNotaReg.title = getString(R.string.nota_reg_button_edit)
        btnSaveNota.setText(R.string.generic_update)

        etNomeMaterial.setText(n.nomeMaterial)
        etDescricaoMaterial.setText(n.descricao)
        etLoja.setText(n.loja)
        etDataNota.setText(n.data)

        val nf = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            isGroupingUsed = false // evita "1.234,56" no campo de edicao
        }
        etValorNota.setText(nf.format(n.valor))

        listOf(cbPintura, cbPedreiro, cbHidraulica, cbEletrica, cbLimpeza, cbOutros).forEach {
            it.isChecked = n.tipos.contains(it.text.toString())
        }
        if (n.status == NotaPagerAdapter.STATUS_A_PAGAR) rbStatusPagar.isChecked = true
        else rbStatusPago.isChecked = true

        adjustSectionTopSpacing(binding.tilDataNota, binding.tvStatusTitle, collapsedTopDp = 10)
        adjustSectionTopSpacing(binding.tilValorNota, binding.tvPhotoTitle, collapsedTopDp = 34)
    }

    // Aplicar os dados da IA no formulário (com saneamento de acordo com as regras)
    private fun applyAutofillToForm(result: AutoFillResult) = with(binding) {
        // ---------- Nome do material ----------
        // Regra: Nunca pode começar com símbolo ou espaço; deve começar com letra.
        // Removemos qualquer prefixo que não seja letra (inclui espaços e símbolos).
        val rawNome = result.nomeMaterial
        val nomeSanitizado = rawNome
            .replace(
                Regex("^[^\\p{L}]+"),
                ""
            ) // corta tudo até a 1ª letra (qualquer alfabeto/acentos)
            .trimStart()
        etNomeMaterial.setText(nomeSanitizado)

        // ---------- Descrição (opcional) ----------
        etDescricaoMaterial.setText(result.descricao)

        // ---------- Loja ----------
        // Regra: Somente letras, espaços e acentos (sem símbolos).
        // Mantemos apenas letras e espaço, colapsamos espaços múltiplos.
        val rawLoja = result.loja
        val lojaSanitizada = rawLoja
            .replace(Regex("[^\\p{L} ]+"), "") // remove tudo que não seja letra ou espaço
            .replace(Regex("\\s+"), " ")
            .trim()
        etLoja.setText(lojaSanitizada)

        // ---------- Tipos ----------
        val tipos: Set<TipoNota> = result.tipos.toSet()
        cbPintura.isChecked = TipoNota.PINTURA in tipos
        cbPedreiro.isChecked = TipoNota.PEDREIRO in tipos
        cbHidraulica.isChecked = TipoNota.HIDRAULICA in tipos
        cbEletrica.isChecked = TipoNota.ELETRICA in tipos
        cbLimpeza.isChecked = TipoNota.LIMPEZA in tipos
        cbOutros.isChecked = TipoNota.OUTROS in tipos

        // ---------- Data ----------
        // Regra: Nunca pode ter "-", deve estar exatamente em dd/MM/yyyy.
        // Extraímos o primeiro match válido; se não houver, deixamos vazio.
        // O ano sempre será 2025, mesmo que a IA traga outro ano.
        val rawData = result.data
        val match = Regex("\\b\\d{2}/\\d{2}/\\d{4}\\b").find(rawData)?.value
        val dataSanitizada = match?.let {
            val (dia, mes) = it.split("/").take(2)
            "$dia/$mes/2025"
        }.orEmpty()
        etDataNota.setText(dataSanitizada)

        // ---------- Valor ----------
        // Regra: Nunca negativo. Se vier negativo, usar o valor absoluto.
        val valorPositivo = abs(result.valor)
        val nf = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            isGroupingUsed = false
        }
        etValorNota.setText(nf.format(valorPositivo))
    }

    // Validação
    private fun validateForm(): Boolean = with(binding) {
        // Nome do material
        val nome = etNomeMaterial.text?.toString()?.trim().orEmpty()
        val nomeOk = nome.isNotEmpty()
        tilNomeMaterial.error = if (!nomeOk) getString(R.string.nota_reg_error_nome) else null

        // Loja (Não exibe erro para loja vazia)
        tilLoja.error = null

        // Data (formato dd/MM/yyyy)
        val data = etDataNota.text?.toString().orEmpty()
        val dataOk = data.matches(Regex("""\d{2}/\d{2}/\d{4}"""))
        // Obs.: helperText de "data no passado" continua sendo setado no onDateSet()
        tilDataNota.error = if (!dataOk) getString(R.string.nota_reg_error_data) else null

        // Valor (> 0)
        val valorOk = etValorNota.text?.toString()
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?.let { it > 0.0 } == true

        tilValorNota.isErrorEnabled = !valorOk
        tilValorNota.error = if (!valorOk) getString(R.string.nota_reg_error_valor) else null

        // Tipos (ao menos um marcado)
        val algumTipo = listOf(cbPintura, cbPedreiro, cbHidraulica, cbEletrica, cbLimpeza, cbOutros)
            .any { it.isChecked }

        // Mesma lógica de validação, mas com cor dinâmica:
        // - Sem seleção: erro + vermelho
        // - Com seleção: dica + cor de helper (igual ao helperText da data)
        if (!algumTipo) {
            tvTipoError.text =
                getString(R.string.nota_reg_error_tipo) // "Selecione ao menos um tipo."
            val errorColor =
                ContextCompat.getColor(requireContext(), R.color.md_theme_light_error)
            tvTipoError.setTextColor(errorColor)
            tvTipoError.visibility = View.VISIBLE
        } else {
            tvTipoError.text = HtmlCompat.fromHtml(
                getString(R.string.nota_reg_type_hint_multi_bold), // "Selecione mais de um tipo, se desejar."
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            val helperColor =
                ContextCompat.getColor(requireContext(), R.color.md_theme_light_secondary2)
            tvTipoError.setTextColor(helperColor)
            tvTipoError.visibility = View.VISIBLE
        }

        val ok = nomeOk && dataOk && valorOk && algumTipo
        // Nao reabilitar o botao enquanto estiver salvando ou depois do clique que vai fechar a tela.
        if (!isSaving && !shouldCloseAfterSave) {
            btnSaveNota.isEnabled = ok
        }
        // ⬇️ Recalcula espaçamento conforme helper/erro de Data e Valor
        adjustSectionTopSpacing(tilDataNota, tvStatusTitle, collapsedTopDp = 10)   // Data → Status
        adjustSectionTopSpacing(tilValorNota, tvPhotoTitle, collapsedTopDp = 32)   // Valor → Foto

        ok
    }

    // Exibição do DatePicker
    private fun applyChosenNotaDate(chosen: String) {
        // chosen vem em "dd/MM/yyyy"
        binding.etDataNota.setText(chosen)

        // helper de data no passado (informativo)
        val parts = chosen.split("/")
        if (parts.size == 3) {
            val d = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull() // 1..12
            val y = parts[2].toIntOrNull()
            if (d != null && m != null && y != null) {
                val sel = Calendar.getInstance().apply {
                    set(y, m - 1, d, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val hoje = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                binding.tilDataNota.helperText =
                    if (sel.before(hoje)) getString(R.string.nota_past_date_warning) else null
            } else {
                binding.tilDataNota.helperText = null
            }
        } else {
            binding.tilDataNota.helperText = null
        }

        // ⬇️ Ajusta o espaçamento do título "Status" conforme o helper/erro da data
        adjustSectionTopSpacing(binding.tilDataNota, binding.tvStatusTitle)
        validateForm()
    }

    // Exibição do progress
    private fun progress(show: Boolean) = with(binding) {
        // o scroll e o botão só travam quando estamos salvando
        val saving = show && isSaving
        notaRegScroll.isEnabled = !saving
        btnSaveNota.isEnabled = if (saving) false else !shouldCloseAfterSave

        // esconde floating bottom rolagem
        fabScrollDown.updateFabVisibilityAnimated(
            visible = isEdit && !saving && !notaRegScroll.isAtBottom()
        )

        // o indicador abaixo do botão aparece só durante salvamento
        progressSaveNota.isVisible = saving

        if (saving) {
            // 1) Limpa o foco de qualquer campo (ex.: "Loja") para evitar auto-scroll do sistema
            requireActivity().currentFocus?.clearFocus()
            root.clearFocus()

            // 2) Foca o container não-editável para “segurar” o foco
            notaRegScroll.isFocusableInTouchMode = true
            notaRegScroll.requestFocus()

            // 3) Agora sim, fecha o teclado
            root.hideKeyboard()

            reevalScrollFab()
        }
    }

    // Helpers do overlay (mostrar/ocultar)
    private fun showAutoFillOverlay() {
        binding.autoFillOverlay.isVisible = true
        binding.lottieAi.playAnimation()     // <-- inicia a animação
    }

    private fun hideAutoFillOverlay() {
        binding.lottieAi.cancelAnimation()    // <-- para a animação
        binding.autoFillOverlay.isVisible = false
    }

    /** Diálogo (MaterialAlertDialogBuilder) perguntando se deseja o Preenchimento Automático. */
    private fun showAutofillConfirmDialog() {
        val titleView = TextView(requireContext()).apply {
            text = getString(R.string.ia_autofill_title) // "Preenchimento Automático"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(48, 32, 48, 8) // igual ao Cronograma
        }

        MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_ObrasApp_FuncDialog
        )
            .setCustomTitle(titleView)
            .setMessage(getString(R.string.ia_autofill_message))
            .setNegativeButton(R.string.snack_button_no) { _, _ -> // "Não" (esquerda)
                autofillAttemptCount = 0
                viewModel.resetAutofillFlow()
            }
            .setPositiveButton(R.string.snack_button_yes) { _, _ -> // "Sim" (direita)
                // 1ª tentativa
                autofillAttemptCount = 1
                viewModel.requestAutofillFromPendingPhoto()
                showAutoFillOverlay()
            }
            .create()
            .show()
    }

    /** Diálogo (MaterialAlertDialogBuilder) de re-tentativa em caso de erro (máx. 3x). */
    private fun showAutofillRetryDialog() {
        val titleView = TextView(requireContext()).apply {
            text = getString(R.string.snack_error) // "Erro"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(48, 32, 48, 8) // igual ao Cronograma
        }

        MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_ObrasApp_FuncDialog
        )
            .setCustomTitle(titleView)
            .setMessage(getString(R.string.ia_autofill_retry_message))
            .setNegativeButton(R.string.snack_button_no) { _, _ ->  // "Não" (esquerda)
                autofillAttemptCount = 0
                viewModel.resetAutofillFlow()
            }
            .setPositiveButton(R.string.snack_button_yes) { _, _ -> // "Sim" (direita)
                // próxima tentativa
                autofillAttemptCount += 1
                viewModel.requestAutofillFromPendingPhoto()
                showAutoFillOverlay()
            }
            .create()
            .show()
    }

    /* ────────────────────── Foto: ações dos botões ────────────────────── */
    private fun startPickImage() {
        // Limpa estado anterior de erro e contador
        viewModel.resetAutofillFlow()
        autofillAttemptCount = 0
        // Aceita qualquer imagem
        openImagePicker.launch(arrayOf("image/*"))
    }

    private fun ensureCameraAndStart() {
        // Checa se há app de câmera
        if (!FileUtils.hasCameraApp(requireContext())) {
            showSnackbarFragment(
                type = Constants.SnackType.ERROR.name,
                title = getString(R.string.snack_attention),
                msg = getString(R.string.nota_photo_error_camera),
                btnText = getString(R.string.snack_button_ok)
            )
            return
        }

        // Limpa estado anterior de erro e contador
        viewModel.resetAutofillFlow()
        autofillAttemptCount = 0

        // Checa/solicita permissão de CÂMERA (SDK 23+)
        requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun startCameraCapture() {
        tempCameraUri = FileUtils.createTempImageUri(requireContext())
        takePictureLauncher.launch(tempCameraUri)
    }

    /** Pergunta se deseja anexar/substituir a foto na nota. */
    private fun confirmAddPhoto(bytes: ByteArray, mime: String) {
        val isReplacingExisting = isEdit && hasRemotePhoto

        val titleRes = if (isReplacingExisting)
            R.string.nota_photo_confirm_replace_title   // "Deseja substituir esta foto da nota?"
        else
            R.string.nota_photo_confirm_msg             // "Deseja salvar esta foto na nota?"

        showSnackbarFragment(
            type = Constants.SnackType.WARNING.name,
            title = getString(R.string.snack_attention),
            msg = getString(titleRes),
            btnText = getString(R.string.snack_button_yes),
            onAction = {
                // guarda no ViewModel como pendente para subir no salvar
                viewModel.setPendingPhoto(bytes, mime)

                // guarda localmente para pré-visualização e para trocar rótulos
                localPreviewBytes = bytes
                localPreviewMine = mime

                // se havia exclusão pendente, ela deixa de existir (vamos substituir a foto)
                isPhotoDeletePending = false

                // ➜ Toast "Foto selecionada."
                Toast.makeText(
                    requireContext(),
                    getString(R.string.nota_photo_pending_generic_toast),
                    Toast.LENGTH_LONG
                ).show()

                // reset nas tentativas da IA ao confirmar uma nova foto
                autofillAttemptCount = 0
                viewModel.resetAutofillFlow()

                updatePhotoUiFromState()
                reevalScrollFab()

                // ➜ Abre o MaterialAlertDialog
                binding.root.post { showAutofillConfirmDialog() }
            },
            btnNegativeText = getString(R.string.snack_button_no),
            onNegative = { /* nada: mantém estado anterior */ }
        )
    }

    /** Excluir (apenas pendente; efetiva no "Alterar") com confirmação. */
    private fun onDeletePhotoClick() {
        showSnackbarFragment(
            type = Constants.SnackType.WARNING.name,
            title = getString(R.string.nota_photo_delete_confirm_title),
            msg = getString(R.string.nota_photo_delete_confirm_msg),
            btnText = getString(R.string.snack_button_yes),
            onAction = {
                if (hasLocalPreview) {
                    // Cancelar a foto local pendente (volta ao estado anterior)
                    localPreviewBytes = null
                    localPreviewMine = null
                    viewModel.clearPendingPhoto()
                    // Não exclui agora – apenas marca como pendente
                    isPhotoDeletePending = true

                    val actionLabel = binding.btnSaveNota.text.toString()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.nota_photo_pending_delete_generic_toast, actionLabel),
                        Toast.LENGTH_SHORT
                    ).show()

                    hidePhotoPreviewOverlay()
                    updatePhotoUiFromState()
                    reevalScrollFab()
                    return@showSnackbarFragment
                }

                if (isEdit && hasRemotePhoto) {
                    // Não exclui agora – apenas marca como pendente
                    isPhotoDeletePending = true

                    Toast.makeText(
                        requireContext(),
                        getString(R.string.nota_photo_removed_toast),
                        Toast.LENGTH_SHORT
                    ).show()

                    // Some com o overlay e atualiza UI (cartão some, botões voltam a "adicionar")
                    hidePhotoPreviewOverlay()
                    updatePhotoUiFromState()
                    reevalScrollFab()
                }
            },
            btnNegativeText = getString(R.string.snack_button_no),
            onNegative = { /* nada */ }
        )
    }

    /** Atualiza textos dos botões e a visibilidade do cartão/preview. */
    private fun updatePhotoUiFromState() = with(binding) {
        // Se estiver marcada exclusão pendente, comporta-se como "sem foto"
        val hasPhotoNow = !isPhotoDeletePending && (hasLocalPreview || hasRemotePhoto)

        // Botões: rótulos padrão vs. "trocar"
        btnPickImage.setText(
            if (hasPhotoNow) R.string.nota_btn_pick_image_replace else R.string.nota_btn_pick_image
        )
        btnTakePhoto.setText(
            if (hasPhotoNow) R.string.nota_btn_take_photo_replace else R.string.nota_btn_take_photo
        )

        // Card informativo com texto "Pré-visualizar…" + lixeira
        cardPhotoInfo.isVisible = hasPhotoNow

        if (!hasPhotoNow) hidePhotoPreviewOverlay()
    }

    /* ────────────────────── Pré-visualização rápida (overlay) ────────────────────── */
    private fun showPhotoPreviewOverlay() = with(binding) {
        // Abre o overlay
        photoPreviewOverlay.isVisible = true

        if (hasLocalPreview) {
            // Foto local: sem loading de rede
            progressPhotoPreview.isVisible = false
            imgPhotoPreviewLarge.visibility = View.VISIBLE
            val bmp =
                BitmapFactory.decodeByteArray(localPreviewBytes, 0, localPreviewBytes!!.size)
            imgPhotoPreviewLarge.setImageBitmap(bmp)
            return@with
        }

        if (hasRemotePhoto) {
            val url = notaOriginal.fotoUrl!!

            // Mostra spinner e reserva espaço com o ImageView INVISIBLE
            progressPhotoPreview.isVisible = true
            imgPhotoPreviewLarge.visibility = View.INVISIBLE

            imgPhotoPreviewLarge.load(url) {
                crossfade(true)
                listener(
                    onSuccess = { _, _ ->
                        progressPhotoPreview.isVisible = false
                        imgPhotoPreviewLarge.visibility = View.VISIBLE
                    },
                    onError = { _, _ ->
                        progressPhotoPreview.isVisible = false
                        imgPhotoPreviewLarge.setImageResource(R.drawable.ic_broken_image)
                        imgPhotoPreviewLarge.visibility = View.VISIBLE
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.nota_view_image_error_open),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }

    private fun hidePhotoPreviewOverlay() {
        binding.progressPhotoPreview.isVisible = false
        binding.photoPreviewOverlay.isVisible = false
    }

    /* ────────────────────── Verificação de Edição ────────────────────── */

    // Botão Voltar
    private fun handleBackPress() {
        if (hasUnsavedChanges()) {
            showSnackbarFragment(
                type = Constants.SnackType.WARNING.name,
                title = getString(R.string.snack_attention),
                msg = getString(R.string.unsaved_confirm_msg),
                btnText = getString(R.string.snack_button_yes),
                onAction = { findNavController().navigateUp() }, // SIM
                btnNegativeText = getString(R.string.snack_button_no),
                onNegative = { /* NÃO → permanece na tela */ }
            )
        } else {
            findNavController().navigateUp()
        }
    }

    // Detecção de alteração não salva
    private fun hasUnsavedChanges(): Boolean = with(binding) {
        // Excluir a foto (pendente) também conta como alteração
        if (localPreviewBytes != null || isPhotoDeletePending) return@with true

        val nome = etNomeMaterial.text?.toString()?.trim().orEmpty()
        val desc = etDescricaoMaterial.text?.toString()?.trim().orEmpty()
        val loja = etLoja.text?.toString()?.trim().orEmpty()
        val data = etDataNota.text?.toString().orEmpty()
        val valor =
            etValorNota.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        val status = if (rbStatusPagar.isChecked) NotaPagerAdapter.STATUS_A_PAGAR
        else NotaPagerAdapter.STATUS_PAGO

        val tiposAtual = buildList {
            if (cbPintura.isChecked) add(cbPintura.text.toString())
            if (cbPedreiro.isChecked) add(cbPedreiro.text.toString())
            if (cbHidraulica.isChecked) add(cbHidraulica.text.toString())
            if (cbEletrica.isChecked) add(cbEletrica.text.toString())
            if (cbLimpeza.isChecked) add(cbLimpeza.text.toString())
            if (cbOutros.isChecked) add(cbOutros.text.toString())
        }.toSet()

        if (!isEdit) {
            // Modo criação: compara com estado “vazio”
            return@with nome.isNotEmpty() ||
                    desc.isNotEmpty() ||
                    loja.isNotEmpty() ||
                    data.isNotEmpty() ||
                    (valor != null && valor > 0.0) ||
                    tiposAtual.isNotEmpty()
        }

        // Modo edição: compara com a nota original
        val orig = notaOriginal
        val tiposOrig = orig.tipos.toSet()

        return@with nome != orig.nomeMaterial ||
                desc != orig.descricao ||
                loja != orig.loja ||
                data != orig.data ||
                status != orig.status ||
                (valor != null && valor != orig.valor) ||
                tiposAtual != tiposOrig
    }

    // “Recheck” útil para mudanças que alteram a altura/posição do conteúdo
    private fun reevalScrollFab() {
        binding.notaRegScroll.post {
            binding.fabScrollDown.updateFabVisibilityAnimated(
                isEdit && !isSaving && !binding.notaRegScroll.isAtBottom()
            )
        }
    }

    // Margin Top Ajustável de "Status" e "Foto da Nota"
    private fun Int.dp(): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            resources.displayMetrics
        ).toInt()

    /**
     * Ajusta a margem-top do título de seção (ex.: “Status”, “Foto da nota”)
     * conforme o helper/erro do TextInputLayout anterior.
     *
     * @param precedingTil TIL anterior (ex.: tilDataNota ou tilValorNota)
     * @param titleView    Título da seção seguinte (ex.: tvStatusTitle ou tvPhotoTitle)
     * @param expandedTopDp Top quando HÁ helper/erro abaixo do TIL anterior
     * @param collapsedTopDp Top quando NÃO há helper/erro abaixo do TIL anterior
     * @param animate      Se true, anima a transição da margem
     */
    private fun adjustSectionTopSpacing(
        precedingTil: TextInputLayout,
        titleView: TextView,
        expandedTopDp: Int = 22,
        collapsedTopDp: Int = 10,
        animate: Boolean = true
    ) {
        val hasCaption = !precedingTil.helperText.isNullOrEmpty() ||
                !precedingTil.error.isNullOrEmpty()

        val parent = titleView.parent as? ViewGroup ?: return
        if (animate) {
            TransitionManager.beginDelayedTransition(
                parent,
                AutoTransition().apply { duration = 150 }
            )
        }

        (titleView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            val newTop = if (hasCaption) expandedTopDp.dp() else collapsedTopDp.dp()
            if (lp.topMargin != newTop) {
                lp.topMargin = newTop
                titleView.layoutParams = lp
            }
        }
    }

    private fun TextInputLayout.hasCaption(): Boolean =
        !helperText.isNullOrEmpty() || !error.isNullOrEmpty()

    private fun TextInputLayout.onCaptionToggle(onToggle: () -> Unit) {
        var last = hasCaption()
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val now = hasCaption()
            if (now != last) {
                last = now
                onToggle()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        binding.lottieAi.cancelAnimation()
    }

    override fun onDestroyView() {
        // 1) Remova listeners para evitar leaks
        binding.notaRegScroll.setOnScrollChangeListener(
            null as androidx.core.widget.NestedScrollView.OnScrollChangeListener?
        )
        binding.fabScrollDown.setOnClickListener(null)

        // (opcional) garanta que o overlay esteja fechado
        binding.photoPreviewOverlay.visibility = View.GONE

        // 2) Libere o binding
        _binding = null

        // 3) Chame o super no final
        super.onDestroyView()
    }
}