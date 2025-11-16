package com.luizeduardobrandao.obra.ui.calculo

import android.content.ClipData
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentCalcRevestimentoBinding
import com.luizeduardobrandao.obra.ui.calculo.domain.rules.CalcRevestimentoRules
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.ImpermeabilizacaoSpecifications
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.RevestimentoSpecifications
import com.luizeduardobrandao.obra.ui.calculo.ui.*
import com.luizeduardobrandao.obra.ui.calculo.utils.NumberFormatter
import com.luizeduardobrandao.obra.ui.calculo.utils.PdfGenerator
import com.luizeduardobrandao.obra.ui.calculo.utils.UnitConverter
import com.luizeduardobrandao.obra.ui.calculo.validation.FieldValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Fragment para cálculo de materiais de revestimento
 * Wizard de 10 etapas: 0. Tela inicial | 1. Tipo | 2. Ambiente | 3. Tráfego (Intertravado)
 * 4. Medidas | 5. Peça | 6. Rodapé | 7. Impermeabilização | 8. Revisão | 9. Resultado
 */
@AndroidEntryPoint
class CalcRevestimentoFragment : Fragment() {

    private var _binding: FragmentCalcRevestimentoBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CalcRevestimentoViewModel by viewModels()

    // Gerenciadores de UI
    private lateinit var toolbarManager: ToolbarManager
    private lateinit var validator: FieldValidator
    private lateinit var iconManager: RequiredIconManager
    private lateinit var tableBuilder: MaterialTableBuilder
    private lateinit var pdfGenerator: PdfGenerator
    private lateinit var radioSynchronizer: RadioGroupSynchronizer
    private lateinit var fieldSynchronizer: FieldSynchronizer
    private lateinit var visibilityManager: VisibilityManager
    private lateinit var navigationHandler: StepNavigationHandler

    // Controle de UX pós-compartilhamento
    private var pendingShareConfirm = false
    private var leftAppForShare = false
    private var skipFocusHijackOnce = false
    private var isSyncing = false
    private var espessuraUserEdited = false
    private lateinit var deferredValidator: DebouncedValidationManager

    /* ═══════════════════════════════════════════════════════════════════════════
     * CICLO DE VIDA
     * ═══════════════════════════════════════════════════════════════════════════ */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingShareConfirm = savedInstanceState?.getBoolean("pendShare", false) == true
        skipFocusHijackOnce = savedInstanceState != null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalcRevestimentoBinding.inflate(inflater, container, false)

        initializeHelpers()
        setupToolbar()
        setupUi()
        setupObservers()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (pendingShareConfirm && leftAppForShare) {
            pendingShareConfirm = false
            leftAppForShare = false
            toast(getString(R.string.resumo_export_shared_ok))
        }
    }

    override fun onStop() {
        super.onStop()
        if (pendingShareConfirm) leftAppForShare = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("pendShare", pendingShareConfirm)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancela qualquer validação adiada pendente para não rodar depois que a View morrer
        if (::deferredValidator.isInitialized) {
            deferredValidator.cancelAll()
        }
        _binding = null
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * INICIALIZAÇÃO
     * ═══════════════════════════════════════════════════════════════════════════ */
    /** Inicializa todos os gerenciadores de UI */
    private fun initializeHelpers() {
        toolbarManager = ToolbarManager(
            binding = binding,
            navController = findNavController(),
            onPrevStep = { viewModel.prevStep() },
            onExport = { share -> export(share) },
            getString = { resId -> getString(resId) }
        )
        validator = FieldValidator(viewModel) { resId -> getString(resId) }
        iconManager = RequiredIconManager(requireContext())
        tableBuilder = MaterialTableBuilder(requireContext(), layoutInflater)
        pdfGenerator = PdfGenerator(requireContext()) { tableBuilder.buildComprarCell(it) }
        radioSynchronizer = RadioGroupSynchronizer()
        fieldSynchronizer = FieldSynchronizer()
        visibilityManager = VisibilityManager()
        navigationHandler = StepNavigationHandler()
        deferredValidator = DebouncedValidationManager() // delay das validações visuais
    }

    /** Configura a toolbar usando o arquivo ToolbarManager */
    private fun setupToolbar() = toolbarManager.setup()

    /* ═══════════════════════════════════════════════════════════════════════════
     * SETUP DA UI
     * ═══════════════════════════════════════════════════════════════════════════ */
    /** Configura todos os componentes da UI */
    private fun setupUi() {
        enableErrorSlotsForAllFields()
        setupNavigationButtons()
        setupStep1Listeners()
        setupStep2Listeners()
        setupStep3TrafegoListeners()
        setupStep3Listeners()
        setupStep4Listeners()
        setupStep5Listeners()
        setupStep6Listeners()
        setupStep7Listeners()
    }

    /** Habilita slots de erro para todos os campos */
    private fun enableErrorSlotsForAllFields() = with(binding) {
        listOf(
            tilComp, tilLarg, tilAltura, tilAreaInformada, tilParedeQtd, tilAbertura,
            tilPecaComp, tilPecaLarg, tilPecaEsp, tilJunta, tilPecasPorCaixa, tilDesnivel, tilSobra,
            tilRodapeAltura, tilRodapeAbertura, tilRodapeCompComercial
        ).forEach { it.isErrorEnabled = true }
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * NAVEGAÇÃO
     * ═══════════════════════════════════════════════════════════════════════════ */
    /** Configura botões de navegação (Iniciar/Voltar/Avançar/Cancelar) */
    private fun setupNavigationButtons() = with(binding) {
        btnNext.setOnClickListener { handleNextButtonClick() }
        btnBack.setOnClickListener { rootCalc.post { viewModel.prevStep() } }
        btnStart.setOnClickListener { rootCalc.post { viewModel.nextStep() } }
        btnCancel.setOnClickListener { findNavController().navigateUp() }
    }

    /** Trata clique no botão "Avançar" - Valida campos antes de prosseguir */
    private fun handleNextButtonClick() {
        with(binding) {
            val step = viewModel.step.value

            // Recalcula estado do botão com todas as regras por etapa
            refreshNextEnabled()
            if (!btnNext.isEnabled) {
                toast(getString(R.string.calc_validate_step))
                return
            }
            // Regra especial: Mármore/Granito precisa definir aplicação (Piso/Parede)
            if (step == 1 && needsAplicacaoDialog()) {
                showAplicacaoDialogForMG()
                return
            }
            rootCalc.post { viewModel.nextStep() }
        }
    }

    /** Verifica se precisa mostrar diálogo de aplicação (Mármore/Granito) */
    private fun needsAplicacaoDialog(): Boolean {
        val i = viewModel.inputs.value
        return (i.revest == CalcRevestimentoViewModel.RevestimentoType.MARMORE ||
                i.revest == CalcRevestimentoViewModel.RevestimentoType.GRANITO) && i.aplicacao == null
    }

    /** Mostra diálogo para escolher aplicação (Piso ou Parede) para Mármore/Granito */
    private fun showAplicacaoDialogForMG() {
        val options = arrayOf(
            getString(R.string.calc_aplicacao_option_piso),
            getString(R.string.calc_aplicacao_option_parede)
        )

        var selected = -1
        lateinit var dialog: AlertDialog

        dialog =
            MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_ObrasApp_FuncDialog)
                .setTitle(getString(R.string.calc_aplicacao_dialog_title_mg))
                .setSingleChoiceItems(options, -1) { _, which ->
                    selected = which
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                }
                .setPositiveButton(getString(R.string.generic_ok_upper_case), null)
                .setNegativeButton(getString(R.string.generic_cancel)) { d, _ -> d.dismiss() }
                .create()

        dialog.setOnShowListener {
            val okBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            okBtn.isEnabled = (selected != -1)

            okBtn.setOnClickListener {
                if (selected == -1) return@setOnClickListener

                val aplicacao = if (selected == 0)
                    CalcRevestimentoViewModel.AplicacaoType.PISO
                else
                    CalcRevestimentoViewModel.AplicacaoType.PAREDE

                viewModel.setAplicacao(aplicacao)
                dialog.dismiss()
                binding.rootCalc.post { viewModel.nextStep() }
            }
        }
        dialog.show()
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * LISTENERS DE ETAPAS
     * ═══════════════════════════════════════════════════════════════════════════ */
    /** Etapa 1: Tipo de revestimento */
    private fun setupStep1Listeners() = with(binding) {
        rgRevest.setOnCheckedChangeListener { _, id ->
            if (isSyncing) return@setOnCheckedChangeListener
            val type = mapRadioIdToRevestimento(id)
            type?.let { viewModel.setRevestimento(it) }

            // Novo tipo de revestimento → reset no controle de auto-preenchimento
            espessuraUserEdited = false

            groupPlacaTipo.isVisible = (type == CalcRevestimentoViewModel.RevestimentoType.PISO)
        }

        rgPlacaTipo.setOnCheckedChangeListener { _, id ->
            if (isSyncing) return@setOnCheckedChangeListener
            val placa = mapRadioIdToPlacaTipo(id)
            viewModel.setPlacaTipo(placa)
        }
    }

    /** Etapa 2: Tipo de ambiente */
    private fun setupStep2Listeners() = with(binding) {
        rgAmbiente.setOnCheckedChangeListener { _, id ->
            if (isSyncing) return@setOnCheckedChangeListener
            val amb = mapRadioIdToAmbiente(id)
            amb?.let { viewModel.setAmbiente(it) }
        }
    }

    /** Etapa 3: Tipo de tráfego (apenas Piso Intertravado) */
    private fun setupStep3TrafegoListeners() = with(binding) {
        rgTrafego.setOnCheckedChangeListener { _, id ->
            if (isSyncing) return@setOnCheckedChangeListener
            val trafego = mapRadioIdToTrafego(id)
            viewModel.setTrafego(trafego)
            refreshNextEnabled()
        }
    }

    /** Etapa 4: Medidas do ambiente */
    private fun setupStep3Listeners() = with(binding) {
        setupMedidaField(etComp, tilComp)
        setupMedidaField(etLarg, tilLarg)
        setupMedidaField(etAlt, tilAltura)
        setupParedeQtdField()
        setupAberturaField()
        setupAreaInformadaField()
    }

    /** Configura campo de medida (Comp/Larg/Alt) */
    private fun setupMedidaField(
        et: TextInputEditText,
        til: TextInputLayout
    ) {
        et.doAfterTextChanged {
            viewModel.setMedidas(
                getD(binding.etComp),
                getD(binding.etLarg),
                getD(binding.etAlt),
                getD(binding.etAreaInformada)
            )

            if (til.isVisible) {
                val isAreaValid = validator.isAreaTotalValidNow(binding.etAreaInformada)
                if (et === binding.etAlt) {
                    validator.validateAlturaLive(et, til, isAreaValid)
                } else {
                    validator.validateCompOrLargLive(et, til, isAreaValid)
                }
            } else {
                validator.setInlineError(et, til, null)
            }

            updateRequiredIconsStep3()
            refreshNextEnabled()
        }

        if (et === binding.etAlt) {
            validator.validateRangeOnBlur(
                et,
                til,
                { getD(et) },
                CalcRevestimentoRules.Medidas.ALTURA_RANGE_M,
                getString(R.string.calc_err_medida_alt_m)
            )
        } else {
            validator.validateRangeOnBlur(
                et,
                til,
                { getD(et) },
                CalcRevestimentoRules.Medidas.COMP_LARG_RANGE_M,
                getString(R.string.calc_err_medida_comp_larg_m)
            )
        }
    }

    /** Configura campo de quantidade de paredes */
    private fun setupParedeQtdField() = with(binding) {
        etParedeQtd.doAfterTextChanged {
            val qtd = etParedeQtd.text?.toString()?.toIntOrNull()
            viewModel.setParedeQtd(qtd)

            validator.validateParedeQtdLive(etParedeQtd, tilParedeQtd)
            updateRequiredIconsStep3()
            refreshNextEnabled()
        }

        validator.validateParedeQtdOnBlur(etParedeQtd, tilParedeQtd)
    }

    /** Configura campo de abertura (portas/janelas) */
    private fun setupAberturaField() = with(binding) {
        etAbertura.doAfterTextChanged {
            val abertura = getD(etAbertura)
            viewModel.setAberturaM2(abertura)

            validator.validateAberturaLive(etAbertura, tilAbertura)
            updateRequiredIconsStep3()
            refreshNextEnabled()
        }

        validator.validateAberturaOnBlur(etAbertura, tilAbertura)
    }

    /** Configura campo de área total informada */
    private fun setupAreaInformadaField() = with(binding) {
        etAreaInformada.doAfterTextChanged {
            viewModel.setMedidas(getD(etComp), getD(etLarg), getD(etAlt), getD(etAreaInformada))

            validator.validateAreaInformadaLive(etAreaInformada, tilAreaInformada)

            // Limpa ou revalida C/L/A conforme área total
            val isValidArea = validator.isAreaTotalValidNow(etAreaInformada)
            if (isValidArea) {
                validator.setInlineError(etComp, tilComp, null)
                validator.setInlineError(etLarg, tilLarg, null)
                if (tilAltura.isVisible) validator.setInlineError(etAlt, tilAltura, null)
            } else {
                validateAllDimensions()
            }

            tvAreaTotalAviso.isVisible = !etAreaInformada.text.isNullOrBlank()
            updateRequiredIconsStep3()
            refreshNextEnabled()
        }

        validator.validateAreaInformadaOnBlur(etAreaInformada, tilAreaInformada)
    }

    /** Etapa 5: Parâmetros da peça */
    private fun setupStep4Listeners() = with(binding) {
        setupPecaField(etPecaComp, tilPecaComp)
        setupPecaField(etPecaLarg, tilPecaLarg)
        setupEspessuraField()
        setupJuntaField()
        setupPecasPorCaixaField()
        setupDesnivelField()
        setupSobraField()

        // Helper inicial da junta baseado no revestimento atual
        validator.updateJuntaHelperText(viewModel.inputs.value, tilJunta)

        rgPastilhaTamanho.setOnCheckedChangeListener { _, id ->
            if (isSyncing) return@setOnCheckedChangeListener
            val formato = mapRadioIdToPastilhaFormato(id)
            viewModel.setPastilhaFormato(formato)
            updateRequiredIconsStep4()
            refreshNextEnabled()
        }
    }

    /** Configura campo de peça (comprimento ou largura) */
    private fun setupPecaField(et: TextInputEditText, til: TextInputLayout) {
        et.doAfterTextChanged {
            val text = et.text?.toString().orEmpty()

            updatePecaParametros() // Atualiza ViewModel normalmente (sem delay)
            if (viewModel.step.value in 1..7) { // Botão "Avançar" continua reagindo em tempo real
                refreshNextEnabled()
            }
            when {
                // Campo vazio → volta para o comportamento normal (sem delay)
                text.isEmpty() -> {
                    deferredValidator.cancel(et)
                    validator.validatePecaLive(et, til)
                    updateRequiredIconsStep4()
                }
                // Primeiro dígito → não mostra erro agora, só depois de 1s parado
                text.length == 1 -> {
                    // Cancela qualquer agendamento anterior
                    deferredValidator.cancel(et)
                    // Some com o erro imediatamente (para não ficar vermelho enquanto digita o 1º número)
                    validator.setInlineError(et, til, null)
                    updateRequiredIconsStep4()
                    // Agenda validação visual para 1s depois
                    deferredValidator.schedule(et) {
                        validator.validatePecaLive(et, til)
                        updateRequiredIconsStep4()
                        if (viewModel.step.value in 1..7) {
                            refreshNextEnabled()
                        }
                    }
                }
                // 2+ dígitos → validação imediata, igual era antes
                else -> {
                    deferredValidator.cancel(et)

                    validator.validatePecaLive(et, til)
                    updateRequiredIconsStep4()

                    if (viewModel.step.value in 1..7) {
                        refreshNextEnabled()
                    }
                }
            }
        }
        validator.validatePecaOnBlur(et, til)
    }

    /** Configura campo de espessura */
    private fun setupEspessuraField() = with(binding) {
        // Validação em tempo real
        etPecaEsp.doAfterTextChanged {
            if (isSyncing) return@doAfterTextChanged

            // Qualquer alteração (inclusive apagar) conta como edição do usuário
            espessuraUserEdited = true

            updatePecaParametros()

            validator.validateEspessuraLive(
                et = etPecaEsp,
                til = tilPecaEsp,
                isPastilha = isPastilha(),
                espValueMm = when {
                    isPastilha() -> null
                    isIntertravado() -> getD(etPecaEsp)?.times(10)
                    else -> mToMmIfLooksLikeMeters(getD(etPecaEsp))
                }
            )

            updateRequiredIconsStep4()
            refreshNextEnabled()
        }

        // Validação ao perder o foco
        etPecaEsp.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener

            validator.validateEspessuraLive(
                et = etPecaEsp,
                til = tilPecaEsp,
                isPastilha = isPastilha(),
                espValueMm = when {
                    isPastilha() -> null
                    isIntertravado() -> getD(etPecaEsp)?.times(10)
                    else -> mToMmIfLooksLikeMeters(getD(etPecaEsp))
                }
            )

            refreshNextEnabled()
        }
    }

    /** Configura campo de junta */
    private fun setupJuntaField() = with(binding) {
        etJunta.doAfterTextChanged {
            if (isSyncing) return@doAfterTextChanged

            updatePecaParametros()

            validator.validateJuntaLive(
                etJunta,
                tilJunta,
                juntaValueMm()
            )

            updateRequiredIconsStep4()
            refreshNextEnabled()
        }
    }

    /** Configura campo de peças por caixa */
    private fun setupPecasPorCaixaField() = with(binding) {
        etPecasPorCaixa.doAfterTextChanged {
            updatePecaParametros()
            updateRequiredIconsStep4()

            validator.validatePecasPorCaixaLive(etPecasPorCaixa, tilPecasPorCaixa)
            refreshNextEnabled()
        }
    }

    /** Configura campo de desnível */
    private fun setupDesnivelField() = with(binding) {
        etDesnivel.doAfterTextChanged {
            updateDesnivelViewModel()

            val v = getD(etDesnivel)
            validator.validateDesnivelLive(
                etDesnivel,
                tilDesnivel,
                tilDesnivel.isVisible,
                v
            )

            refreshNextEnabled()
        }

        etDesnivel.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            val v = getD(etDesnivel)
            validator.validateDesnivelOnBlur(
                etDesnivel,
                tilDesnivel,
                tilDesnivel.isVisible,
                v
            )
        }
    }

    /** Configura campo de sobra técnica */
    private fun setupSobraField() = with(binding) {
        etSobra.doAfterTextChanged {
            updatePecaParametros()

            validator.validateSobraLive(etSobra, tilSobra)

            if (viewModel.step.value in 1..7) refreshNextEnabled()
            updateRequiredIconsStep4()
        }

        etSobra.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener

            validator.validateSobraOnBlur(etSobra, tilSobra)

            if (viewModel.step.value in 1..7) refreshNextEnabled()
        }
    }

    /** Etapa 6: Rodapé */
    private fun setupStep5Listeners() = with(binding) {
        switchRodape.setOnCheckedChangeListener { _, isChecked ->
            groupRodapeFields.isVisible = isChecked
            if (!isChecked) {
                etRodapeAbertura.text?.clear()
                tilRodapeAbertura.error = null
            }
            updateRodapeViewModel()
            updateRequiredIconsStep5()
            if (viewModel.step.value in 1..7) refreshNextEnabled()
        }

        rgRodapeMat.setOnCheckedChangeListener { _, checkedId ->
            if (isSyncing) return@setOnCheckedChangeListener
            val isPecaPronta = (checkedId == R.id.rbRodapePeca)
            tilRodapeCompComercial.isVisible = isPecaPronta

            if (!isPecaPronta) {
                etRodapeCompComercial.text?.clear()
                tilRodapeCompComercial.error = null
            }

            updateRodapeViewModel()
            updateRequiredIconsStep5()
            if (viewModel.step.value in 1..7) refreshNextEnabled()
        }

        etRodapeAltura.doAfterTextChanged {
            val text = etRodapeAltura.text?.toString().orEmpty()
            updateRodapeViewModel()
            updateRequiredIconsStep5()
            if (viewModel.step.value in 1..7) {
                refreshNextEnabled()
            }
            when {
                // Campo vazio → valida normal (sem delay, e sem erro se estiver vazio)
                text.isEmpty() -> {
                    deferredValidator.cancel(etRodapeAltura)
                    validator.validateRodapeAlturaLive(etRodapeAltura, tilRodapeAltura)
                }
                // Primeiro dígito → limpa erro na hora e só valida depois de 1s
                text.length == 1 -> {
                    deferredValidator.cancel(etRodapeAltura)
                    // Some com o erro enquanto usuário está só iniciando o valor
                    validator.setInlineError(etRodapeAltura, tilRodapeAltura, null)

                    deferredValidator.schedule(etRodapeAltura) {
                        validator.validateRodapeAlturaLive(etRodapeAltura, tilRodapeAltura)

                        if (viewModel.step.value in 1..7) {
                            refreshNextEnabled()
                        }
                    }
                }
                // 2+ dígitos → validação imediata
                else -> {
                    deferredValidator.cancel(etRodapeAltura)

                    validator.validateRodapeAlturaLive(etRodapeAltura, tilRodapeAltura)

                    if (viewModel.step.value in 1..7) {
                        refreshNextEnabled()
                    }
                }
            }
        }
        validator.validateRodapeAlturaOnBlur(etRodapeAltura, tilRodapeAltura)

        etRodapeAbertura.doAfterTextChanged {
            updateRodapeViewModel()
            updateRequiredIconsStep5()

            validator.validateRodapeAberturaLive(etRodapeAbertura, tilRodapeAbertura)
            if (viewModel.step.value in 1..7) refreshNextEnabled()
        }
        validator.validateRodapeAberturaOnBlur(etRodapeAbertura, tilRodapeAbertura)

        etRodapeCompComercial.doAfterTextChanged {
            val text = etRodapeCompComercial.text?.toString().orEmpty()
            updateRodapeViewModel()
            updateRequiredIconsStep5()
            if (viewModel.step.value in 1..7) {
                refreshNextEnabled()
            }
            // Sempre precisamos saber se o campo está realmente valendo (só quando peça pronta)
            fun isPecaProntaSelecionada(): Boolean =
                switchRodape.isChecked &&
                        rgRodapeMat.checkedRadioButtonId == R.id.rbRodapePeca &&
                        tilRodapeCompComercial.isVisible
            when {
                // Vazio → volta para comportamento normal (sem delay)
                text.isEmpty() -> {
                    deferredValidator.cancel(etRodapeCompComercial)

                    validator.validateRodapeCompComercialLive(
                        etRodapeCompComercial,
                        tilRodapeCompComercial,
                        isPecaProntaSelecionada()
                    )
                }
                // Primeiro dígito → esconde erro agora, valida em 1s se continuar inválido
                text.length == 1 -> {
                    deferredValidator.cancel(etRodapeCompComercial)
                    validator.setInlineError(etRodapeCompComercial, tilRodapeCompComercial, null)
                    deferredValidator.schedule(etRodapeCompComercial) {
                        validator.validateRodapeCompComercialLive(
                            etRodapeCompComercial,
                            tilRodapeCompComercial,
                            isPecaProntaSelecionada()
                        )
                        if (viewModel.step.value in 1..7) {
                            refreshNextEnabled()
                        }
                    }
                }
                // 2+ dígitos → validação imediata, igual antes
                else -> {
                    deferredValidator.cancel(etRodapeCompComercial)
                    validator.validateRodapeCompComercialLive(
                        etRodapeCompComercial,
                        tilRodapeCompComercial,
                        isPecaProntaSelecionada()
                    )
                    if (viewModel.step.value in 1..7) {
                        refreshNextEnabled()
                    }
                }
            }
        }
        validator.validateRodapeCompComercialOnBlur(etRodapeCompComercial, tilRodapeCompComercial)
    }

    /** Etapa 7: Impermeabilização */
    private fun setupStep6Listeners() = with(binding) {
        switchImp.setOnCheckedChangeListener { _, on ->
            viewModel.setImpermeabilizacao(on)
            refreshNextEnabled()
        }

        rgIntertravadoImp.setOnCheckedChangeListener { _, id ->
            if (isSyncing) return@setOnCheckedChangeListener
            val tipo = mapRadioIdToImpTipo(id)
            tipo?.let { viewModel.setIntertravadoImpTipo(it) }
            refreshNextEnabled()
        }
    }

    /** Etapa 8: Revisão e cálculo */
    private fun setupStep7Listeners() = with(binding) {
        btnCalcular.setOnClickListener { viewModel.calcular() }
        btnVoltarResultado.setOnClickListener { viewModel.goTo(8) }
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * OBSERVERS
     * ═══════════════════════════════════════════════════════════════════════════ */
    /** Configura observers para StateFlow do ViewModel */
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeStep() }
                launch { observeInputs() }
                launch { observeResultado() }
            }
        }
    }

    /** Observa mudanças na etapa atual */
    private suspend fun observeStep() {
        viewModel.step.collect { step ->
            binding.viewFlipper.displayedChild = step

            syncRadioGroups()
            handleStepLayout(step)
            handleStepIcons(step)
            handleStepButtons(step)
            toolbarManager.updateForStep(step)
            handleStep7Resume(step)
            ensureTopNoFlicker(step)
            refreshNextEnabled()
        }
    }

    /** Observa mudanças nos inputs */
    private suspend fun observeInputs() {
        viewModel.inputs.collect { i ->
            syncRadioGroups()
            syncFieldValues()
            updateUIVisibility()

            // Espessura padrão automática para Mármore/Granito
            validator.ensureDefaultMgEspessura(espessuraUserEdited)

            // Helpers dinâmicos (espessura, peça, junta, desnível)
            validator.updateStep4HelperTexts(
                i,
                binding.tilPecaEsp,
                binding.tilPecaComp,
                binding.tilPecaLarg,
                binding.tilJunta,
                binding.tilDesnivel
            )

            if (viewModel.step.value in 1..7) refreshNextEnabled()
        }
    }

    /** Observa resultado do cálculo */
    private suspend fun observeResultado() {
        viewModel.resultado.collect { ui ->
            when (ui) {
                is UiState.Success -> {
                    displayResultado(ui.data.resultado)
                    binding.btnVoltarResultado.isVisible = true
                }

                else -> {
                    binding.btnVoltarResultado.isVisible = false
                }
            }
        }
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * SINCRONIZAÇÃO DE UI
     * ═══════════════════════════════════════════════════════════════════════════ */
    /** Sincroniza RadioGroups com o estado do ViewModel */
    private fun syncRadioGroups() = with(binding) {
        isSyncing = true
        radioSynchronizer.syncAllRadioGroups(
            viewModel.inputs.value,
            rgRevest, rgPlacaTipo, rgAmbiente, rgRodapeMat,
            rgTrafego, rgIntertravadoImp, rgPastilhaTamanho
        )
        isSyncing = false
    }

    /** Sincroniza campos de texto com o estado do ViewModel */
    private fun syncFieldValues() = with(binding) {
        isSyncing = true
        fieldSynchronizer.syncAllFields(
            viewModel.inputs.value,
            etComp, etLarg, etAlt, etParedeQtd, etAbertura, etAreaInformada,
            etPecaComp, etPecaLarg, etPecaEsp, etJunta, etSobra, etPecasPorCaixa,
            etDesnivel, etRodapeAltura, etRodapeAbertura, etRodapeCompComercial,
            tilComp, tilLarg, tilAltura, tilParedeQtd, tilAbertura, tilAreaInformada,
            tilPecaComp, tilPecaLarg, tilPecaEsp, tilJunta, tilPecasPorCaixa,
            tilDesnivel, tilSobra, tilRodapeAltura, tilRodapeAbertura, tilRodapeCompComercial,
            rgPastilhaTamanho,
            isMG()
        )

        normalizePredefinedDefaults()
        isSyncing = false
    }

    /** Normaliza exibição dos valores padrão auto-preenchidos */
    private fun normalizePredefinedDefaults() = with(binding) {
        val i = viewModel.inputs.value
        // Piso Intertravado, a espessura é armazenada em mm e exibida em cm (÷10)
        val espDisplay = when (i.revest) {
            CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO ->
                i.pecaEspMm?.div(10.0) // mm → cm
            else ->
                i.pecaEspMm           // demais revestimentos continuam em mm
        }
        normalizeAutoField(etPecaEsp, espDisplay)
        normalizeAutoField(etJunta, i.juntaMm)
        normalizeAutoField(etSobra, i.sobraPct)
        normalizeAutoField(etDesnivel, i.desnivelCm)
    }

    private fun normalizeAutoField(editText: TextInputEditText, value: Double?) {
        if (editText.hasFocus()) return
        val adjusted = NumberFormatter.adjustDefaultFieldText(editText.text?.toString(), value)
        if (adjusted != null && adjusted != editText.text?.toString()) {
            editText.setText(adjusted)
        }
    }

    /** Atualiza visibilidade de componentes conforme inputs */
    private fun updateUIVisibility() = with(binding) {
        visibilityManager.updateAllVisibilities(
            viewModel.inputs.value, tvAreaTotalAviso, groupPlacaTipo, groupPecaTamanho,
            groupPastilhaTamanho, groupRodapeFields, groupIntertravadoImpOptions, tilComp,
            tilLarg, tilAltura, tilParedeQtd, tilAbertura, tilAreaInformada,
            tilPecaComp, tilPecaLarg, tilPecaEsp, tilJunta, tilPecasPorCaixa, tilDesnivel,
            tilSobra, tilRodapeAltura, tilRodapeAbertura, tilRodapeCompComercial,
            etLarg, etAlt, etParedeQtd, etAbertura,
            etPecaEsp, etJunta, etPecasPorCaixa, etRodapeAbertura,
            rgPlacaTipo, rgIntertravadoImp,
            switchImp, switchRodape
        )
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * HANDLERS DE ETAPAS
     * ═══════════════════════════════════════════════════════════════════════════ */
    /** Ajusta layout conforme etapa */
    private fun handleStepLayout(step: Int) = with(binding) {
        val tela0 = viewFlipper.getChildAt(0) as LinearLayout

        if (step == 0) {
            bottomBar.isVisible = false
            tela0.gravity = Gravity.CENTER
            scrollContent.post {
                val viewport = scrollContent.height
                val paddings = contentColumn.paddingTop + contentColumn.paddingBottom
                tela0.minimumHeight = (viewport - paddings).coerceAtLeast(0)
            }
        } else {
            bottomBar.isVisible = true
            tela0.gravity = Gravity.CENTER_HORIZONTAL
            tela0.minimumHeight = 0
        }
    }

    /** Inicializa ícones obrigatórios conforme etapa */
    private fun handleStepIcons(step: Int) = with(binding) {
        when (step) {
            4 -> {
                iconManager.setRequiredIconVisible(etComp, true)
                iconManager.setRequiredIconVisible(etLarg, true)
                iconManager.setRequiredIconVisible(etAreaInformada, true)
                if (tilAltura.isVisible) iconManager.setRequiredIconVisible(etAlt, true)
                updateRequiredIconsStep3()
            }

            5 -> updateRequiredIconsStep4()
            6 -> updateRequiredIconsStep5()
        }
    }

    /** Configura botões conforme etapa */
    private fun handleStepButtons(step: Int) = with(binding) {
        navigationHandler.handleStepNavigation(
            step = step, btnBack = btnBack, btnNext = btnNext, btnCalcular = btnCalcular,
            bottomBar = bottomBar, viewFlipper = viewFlipper,
            onSetupNovoCalculo = { setupNovoCalculoButton() },
            onRestoreDefaultBack = { restoreDefaultBackButton() }
        )
    }

    /** Preenche resumo na etapa 8 */
    private fun handleStep7Resume(step: Int) {
        if (step == 8) {
            binding.tvResumoRevisao.text = viewModel.getResumoRevisao()
        }
    }

    /** Configura botão "Novo Cálculo" */
    private fun setupNovoCalculoButton() = with(binding) {
        btnNext.isVisible = false
        btnBack.text = getString(R.string.calc_novo_calculo)

        try {
            btnBack.setIconResource(R.drawable.ic_refresh)
            btnBack.setIconSize(resources.getDimensionPixelSize(R.dimen.calc_btn_icon_large))
        } catch (_: Throwable) {
        }

        val largeH = resources.getDimensionPixelSize(R.dimen.calc_btn_large_height)
        btnBack.layoutParams = btnBack.layoutParams.apply { height = largeH }

        val largeTextPx = resources.getDimension(R.dimen.calc_btn_large_text)
        btnBack.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, largeTextPx)
        btnBack.setTypeface(btnBack.typeface, Typeface.BOLD)

        btnBack.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.calc_new_calc_title))
                .setMessage(getString(R.string.calc_new_calc_message))
                .setPositiveButton(getString(R.string.generic_yes_lower_case)) { _, _ ->
                    navigateBackToCalcMaterial()
                }
                .setNegativeButton(getString(R.string.generic_cancel), null)
                .show()
        }
    }

    /** Restaura botão "Voltar" padrão */
    private fun restoreDefaultBackButton() = with(binding) {
        btnBack.text = getString(R.string.generic_back)
        try {
            btnBack.setIconResource(0)
        } catch (_: Throwable) {
        }

        btnBack.layoutParams = btnBack.layoutParams.apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        val normalTextPx = resources.getDimension(R.dimen.calc_btn_normal_text)
        btnBack.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, normalTextPx)
        btnBack.setTypeface(btnBack.typeface, Typeface.NORMAL)

        btnBack.setOnClickListener { viewModel.prevStep() }
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * ATUALIZAÇÃO DE VIEWMODEL
     * ═══════════════════════════════════════════════════════════════════════════ */
    /** Atualiza parâmetros da peça no ViewModel */
    private fun updatePecaParametros() = with(binding) {
        val pc = parsePecaToCm(getD(etPecaComp))
        val pl = parsePecaToCm(getD(etPecaLarg))
        val esp = when {
            isPastilha() -> null
            isIntertravado() -> getD(etPecaEsp)?.times(10)
            else -> mToMmIfLooksLikeMeters(getD(etPecaEsp))
        }
        val junta = mToMmIfLooksLikeMeters(getD(etJunta))
        val sobra = getD(etSobra)
        val ppc = etPecasPorCaixa.text?.toString()?.toIntOrNull()
        viewModel.setPecaParametros(pc, pl, esp, junta, sobra, ppc)
    }

    /** Atualiza desnível no ViewModel */
    private fun updateDesnivelViewModel() {
        viewModel.setDesnivelCm(getD(binding.etDesnivel))
    }

    /** Atualiza rodapé no ViewModel */
    private fun updateRodapeViewModel() = with(binding) {
        val material = if (rgRodapeMat.checkedRadioButtonId == R.id.rbRodapeMesma)
            CalcRevestimentoViewModel.RodapeMaterial.MESMA_PECA
        else
            CalcRevestimentoViewModel.RodapeMaterial.PECA_PRONTA

        val compProntaM = if (material == CalcRevestimentoViewModel.RodapeMaterial.PECA_PRONTA)
            getD(etRodapeCompComercial)?.div(100.0)
        else
            null

        val aberturaM = getD(etRodapeAbertura)?.coerceAtLeast(0.0) ?: 0.0

        viewModel.setRodape(
            enable = switchRodape.isChecked,
            alturaCm = mToCmIfLooksLikeMeters(getD(etRodapeAltura)),
            perimetroManualM = null,
            descontarVaoM = aberturaM,
            perimetroAuto = true,
            material = material,
            orientacaoMaior = true,
            compComercialM = compProntaM
        )
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * ÍCONES OBRIGATÓRIOS
     * ═══════════════════════════════════════════════════════════════════════════ */
    /** Atualiza ícones obrigatórios da Etapa 4 (Medidas) */
    private fun updateRequiredIconsStep3() = with(binding) {
        iconManager.updateStep3Icons(
            etComp, etLarg, etAlt, etParedeQtd, etAbertura, etAreaInformada,
            tilAltura.isVisible, tilParedeQtd.isVisible
        )
    }

    /** Atualiza ícones obrigatórios da Etapa 5 (Peça) */
    private fun updateRequiredIconsStep4() = with(binding) {
        iconManager.updateStep4Icons(
            etPecaComp, etPecaLarg, etJunta, etPecaEsp, etPecasPorCaixa, etSobra,
            etDesnivel, viewModel.inputs.value.revest, groupPecaTamanho.isVisible
        )
    }

    /** Atualiza ícones obrigatórios da Etapa 6 (Rodapé) */
    private fun updateRequiredIconsStep5() = with(binding) {
        iconManager.updateStep5Icons(
            etRodapeAltura, etRodapeCompComercial, switchRodape.isChecked,
            rgRodapeMat.checkedRadioButtonId == R.id.rbRodapePeca,
            tilRodapeCompComercial.isVisible
        )
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * VALIDAÇÃO E HABILITAÇÃO
     * ═══════════════════════════════════════════════════════════════════════════ */
    /** Revalida todas as dimensões (Comp/Larg/Alt) */
    private fun validateAllDimensions() = with(binding) {
        validator.validateDimLive(
            etComp,
            tilComp,
            CalcRevestimentoRules.Medidas.COMP_LARG_RANGE_M,
            getString(R.string.calc_err_medida_comp_larg_m),
            false
        )
        validator.validateDimLive(
            etLarg,
            tilLarg,
            CalcRevestimentoRules.Medidas.COMP_LARG_RANGE_M,
            getString(R.string.calc_err_medida_comp_larg_m),
            false
        )
        if (tilAltura.isVisible) {
            validator.validateDimLive(
                etAlt,
                tilAltura,
                CalcRevestimentoRules.Medidas.ALTURA_RANGE_M,
                getString(R.string.calc_err_medida_alt_m),
                false
            )
        }
    }

    /** Recalcula habilitação do botão "Avançar" */
    private fun refreshNextEnabled() = with(binding) {
        val step = viewModel.step.value
        val validation = viewModel.validateStep(step)
        val inputs = viewModel.inputs.value

        var enabled = validation.isValid

        // ----- Etapa 4 (Medidas / Área total) -----
        if (enabled && step >= 4) {
            enabled =
                !validator.hasAreaTotalErrorNow(etAreaInformada) &&
                        tilParedeQtd.error.isNullOrEmpty() &&
                        tilAbertura.error.isNullOrEmpty()
        }

        // ----- Etapa 5 (Peça: junta, espessura, ppc, desnível, sobra) -----
        if (enabled && step >= 5) {
            val isPedra = inputs.revest == CalcRevestimentoViewModel.RevestimentoType.PEDRA

            enabled = when {
                // PEDRA PORTUGUESA: considerar apenas desnível + sobra técnica
                isPedra -> {
                    !validator.hasDesnivelErrorNow(
                        etDesnivel,
                        tilDesnivel.isVisible,
                        getD(etDesnivel)
                    ) && tilSobra.error.isNullOrEmpty()
                }

                else -> {
                    !validator.hasJuntaErrorNow(etJunta, juntaValueMm()) &&
                            !validator.hasEspessuraErrorNow(
                                etPecaEsp,
                                isPastilha(),
                                when {
                                    isPastilha() -> null
                                    isIntertravado() -> getD(etPecaEsp)?.times(10)
                                    else -> mToMmIfLooksLikeMeters(getD(etPecaEsp))
                                }
                            ) &&
                            !validator.hasPecasPorCaixaErrorNow(etPecasPorCaixa) &&
                            !validator.hasDesnivelErrorNow(
                                etDesnivel,
                                tilDesnivel.isVisible,
                                getD(etDesnivel)
                            ) &&
                            tilSobra.error.isNullOrEmpty()
                }
            }
        }

        // ----- Etapa 6 (Rodapé) -----
        if (enabled && step >= 6) {
            var rodapeOk = tilRodapeAbertura.error.isNullOrEmpty()

            if (inputs.rodapeEnable &&
                inputs.rodapeMaterial == CalcRevestimentoViewModel.RodapeMaterial.PECA_PRONTA
            ) {
                val alturaCm = mToCmIfLooksLikeMeters(getD(etRodapeAltura))
                val compCm = getD(etRodapeCompComercial)

                val alturaOk =
                    alturaCm != null &&
                            alturaCm in CalcRevestimentoRules.Rodape.ALTURA_RANGE_CM &&
                            tilRodapeAltura.error.isNullOrEmpty()

                val compOk =
                    compCm != null &&
                            compCm in CalcRevestimentoRules.Rodape.COMP_COMERCIAL_RANGE_CM &&
                            tilRodapeCompComercial.error.isNullOrEmpty()

                rodapeOk = rodapeOk && alturaOk && compOk
            }

            enabled = rodapeOk
        }

        btnNext.isEnabled = enabled

        // ----- Ajuste extra para dimensões de Mármore/Granito (Etapa 5+) -----
        if ((inputs.revest == CalcRevestimentoViewModel.RevestimentoType.MARMORE ||
                    inputs.revest == CalcRevestimentoViewModel.RevestimentoType.GRANITO) &&
            step >= 5
        ) {
            val compValid = parsePecaToCm(getD(etPecaComp))
                ?.let { it in CalcRevestimentoRules.Peca.MG_RANGE_CM } == true
            val largValid = parsePecaToCm(getD(etPecaLarg))
                ?.let { it in CalcRevestimentoRules.Peca.MG_RANGE_CM } == true

            btnNext.isEnabled = btnNext.isEnabled && compValid && largValid
        }
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * RESULTADO
     * ═══════════════════════════════════════════════════════════════════════════ */
    /** Exibe resultado do cálculo */
    private fun displayResultado(r: CalcRevestimentoViewModel.Resultado) = with(binding) {
        tableContainer.removeAllViews()
        tableContainer.addView(tableBuilder.makeHeaderRow())
        r.itens.forEach { tableContainer.addView(tableBuilder.makeDataRow(it)) }
        // Aplicar responsividade após renderização
        tableContainer.post {
            applyTableResponsiveness()
        }
    }

    /** Aplica responsividade aos elementos da tabela */
    private fun applyTableResponsiveness() = with(binding) {
        val responsiveHelper = TableResponsiveHelper(requireContext())

        // Título "Lista de Materiais"
        val titleViews =
            (viewFlipper.getChildAt(9) as? LinearLayout)?.children?.filterIsInstance<TextView>()
        titleViews?.firstOrNull { it.text == getString(R.string.calc_table_title) }
            ?.let { tvTitulo ->
                responsiveHelper.setTextSizeSp(tvTitulo, responsiveHelper.titleTextSize)
                (tvTitulo.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                    topMargin = responsiveHelper.titleMarginTop
                    bottomMargin = responsiveHelper.titleMarginBottom
                    tvTitulo.layoutParams = this
                }
            }

        // Card da tabela
        val cardTabela = tableContainer.parent as? com.google.android.material.card.MaterialCardView
        cardTabela?.apply {
            radius = responsiveHelper.cardCornerRadius
            cardElevation = responsiveHelper.cardElevation
        }

        // Card de informação
        cardInformation.apply {
            radius = responsiveHelper.infoCardCornerRadius
            setPadding(
                responsiveHelper.infoCardPadding,
                responsiveHelper.infoCardPadding,
                responsiveHelper.infoCardPadding,
                responsiveHelper.infoCardPadding
            )
            (layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                topMargin = responsiveHelper.infoCardMarginTop
                cardInformation.layoutParams = this
            }
        }
        // Texto do card de informação
        val tvInfoText = cardInformation.findViewById(android.R.id.text1)
            ?: cardInformation.findViewTreeDescendants<TextView>()
                .firstOrNull { it.text == getString(R.string.calc_table_hint) }
        tvInfoText?.let {
            responsiveHelper.setTextSizeSp(it, responsiveHelper.infoCardTextSize)
        }
        // Divider
        (dividerTabelaInfo.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
            topMargin = responsiveHelper.infoCardMarginTop
            dividerTabelaInfo.layoutParams = this
        }
        // Margens extras apenas no primeiro e último item de dados (ignorando o cabeçalho)
        responsiveHelper.applyEdgeItemMargins(tableContainer, skipHeader = true)
    }

    // Helper para encontrar views descendentes
    private inline fun <reified T : View> View.findViewTreeDescendants(): List<T> {
        val result = mutableListOf<T>()
        val stack = ArrayDeque<View>()
        stack.add(this)

        while (stack.isNotEmpty()) {
            val view = stack.removeFirst()

            if (view is T) {
                result.add(view)
            }

            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    stack.add(view.getChildAt(i))
                }
            }
        }

        return result
    }

    private val ViewGroup.children: Sequence<View>
        get() = (0 until childCount).asSequence().map { getChildAt(it) }

    /* ═══════════════════════════════════════════════════════════════════════════
     * EXPORTAÇÃO DE PDF
     * ═══════════════════════════════════════════════════════════════════════════ */
    /** Exporta resultado em PDF (compartilhar ou salvar) */
    private fun export(share: Boolean) {
        val ui = (viewModel.resultado.value as? UiState.Success)?.data ?: run {
            toast(getString(R.string.calc_export_no_data))
            return
        }

        lifecycleScope.launch {
            binding.loadingOverlay.isVisible = true

            try {
                val (bytes, fileName) = withContext(Dispatchers.IO) {
                    val pdfBytes = pdfGenerator.generate(
                        ui.resultado,
                        viewModel.inputs.value.pecaEspMm,
                        viewModel.inputs.value.pecasPorCaixa,
                        viewModel.inputs.value.desnivelCm
                    )
                    val name = "Calculo_Revestimento_${System.currentTimeMillis()}.pdf"
                    Pair(pdfBytes, name)
                }

                if (share) {
                    sharePdf(bytes, fileName)
                } else {
                    savePdf(bytes, fileName)
                }
            } catch (e: Exception) {
                toast("Erro ao gerar PDF: ${e.message}")
            } finally {
                binding.loadingOverlay.isVisible = false
            }
        }
    }

    /** Compartilha PDF via Intent */
    private fun sharePdf(bytes: ByteArray, fileName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val cacheFile = File(requireContext().cacheDir, fileName)
            FileOutputStream(cacheFile).use { it.write(bytes) }

            withContext(Dispatchers.Main) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    cacheFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newUri(requireContext().contentResolver, "PDF", uri)
                }

                val chooser = Intent.createChooser(
                    shareIntent,
                    getString(R.string.export_share_chooser_calc)
                ).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                pendingShareConfirm = true
                startActivity(chooser)
            }
        }
    }

    /** Salva PDF em Downloads */
    private suspend fun savePdf(bytes: ByteArray, fileName: String) {
        val saved = withContext(Dispatchers.IO) {
            try {
                val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                if (!downloads.exists()) downloads.mkdirs()
                val file = File(downloads, fileName)
                FileOutputStream(file).use { it.write(bytes) }
                Uri.fromFile(file)
            } catch (_: Exception) {
                null
            }
        }

        if (saved == null) toast(getString(R.string.resumo_export_error_save))
        else toast(getString(R.string.resumo_export_saved_ok))
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * MAPPERS (RadioId → Enum)
     * ═══════════════════════════════════════════════════════════════════════════ */
    private fun mapRadioIdToRevestimento(id: Int) = when (id) {
        R.id.rbPiso -> CalcRevestimentoViewModel.RevestimentoType.PISO
        R.id.rbAzulejo -> CalcRevestimentoViewModel.RevestimentoType.AZULEJO
        R.id.rbPastilha -> CalcRevestimentoViewModel.RevestimentoType.PASTILHA
        R.id.rbPedra -> CalcRevestimentoViewModel.RevestimentoType.PEDRA
        R.id.rbPisoIntertravado -> CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO
        R.id.rbMarmore -> CalcRevestimentoViewModel.RevestimentoType.MARMORE
        R.id.rbGranito -> CalcRevestimentoViewModel.RevestimentoType.GRANITO
        else -> null
    }

    private fun mapRadioIdToPlacaTipo(id: Int) = when (id) {
        R.id.rbCeramica -> CalcRevestimentoViewModel.PlacaTipo.CERAMICA
        R.id.rbPorcelanato -> CalcRevestimentoViewModel.PlacaTipo.PORCELANATO
        else -> null
    }

    private fun mapRadioIdToAmbiente(id: Int) = when (id) {
        R.id.rbSeco -> CalcRevestimentoViewModel.AmbienteType.SECO
        R.id.rbSemi -> CalcRevestimentoViewModel.AmbienteType.SEMI
        R.id.rbMolhado -> CalcRevestimentoViewModel.AmbienteType.MOLHADO
        R.id.rbSempre -> CalcRevestimentoViewModel.AmbienteType.SEMPRE
        else -> null
    }

    private fun mapRadioIdToTrafego(id: Int) = when (id) {
        R.id.rbTrafegoLeve -> CalcRevestimentoViewModel.TrafegoType.LEVE
        R.id.rbTrafegoMedio -> CalcRevestimentoViewModel.TrafegoType.MEDIO
        R.id.rbTrafegoPesado -> CalcRevestimentoViewModel.TrafegoType.PESADO
        else -> null
    }

    private fun mapRadioIdToImpTipo(id: Int) = when (id) {
        R.id.rbImpMantaGeotextil -> ImpermeabilizacaoSpecifications.ImpIntertravadoTipo.MANTA_GEOTEXTIL
        R.id.rbImpAditivoSika1 -> ImpermeabilizacaoSpecifications.ImpIntertravadoTipo.ADITIVO_SIKA1
        else -> null
    }

    private fun mapRadioIdToPastilhaFormato(id: Int) = when (id) {
        R.id.rbPastilha5 -> RevestimentoSpecifications.PastilhaFormato.P5
        R.id.rbPastilha7_5 -> RevestimentoSpecifications.PastilhaFormato.P7_5
        R.id.rbPastilha10 -> RevestimentoSpecifications.PastilhaFormato.P10
        else -> null
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * HELPERS E UTILITÁRIOS
     * ═══════════════════════════════════════════════════════════════════════════ */
    private fun isMG() = viewModel.inputs.value.revest in setOf(
        CalcRevestimentoViewModel.RevestimentoType.MARMORE,
        CalcRevestimentoViewModel.RevestimentoType.GRANITO
    )

    private fun isPastilha() =
        viewModel.inputs.value.revest == CalcRevestimentoViewModel.RevestimentoType.PASTILHA

    private fun isIntertravado() =
        viewModel.inputs.value.revest == CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO

    private fun parsePecaToCm(v: Double?) = UnitConverter.parsePecaToCm(v, isMG())
    private fun mToCmIfLooksLikeMeters(v: Double?) = UnitConverter.mToCmIfLooksLikeMeters(v)
    private fun mToMmIfLooksLikeMeters(v: Double?) = UnitConverter.mToMmIfLooksLikeMeters(v)

    private fun getD(et: TextInputEditText) =
        et.text?.toString()?.replace(",", ".")?.toDoubleOrNull()

    private fun juntaValueMm() = mToMmIfLooksLikeMeters(getD(binding.etJunta))

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    private fun navigateBackToCalcMaterial() {
        val nav = findNavController()
        val popped = nav.popBackStack(R.id.calcMaterialFragment, false)
        if (!popped) {
            try {
                nav.navigate(R.id.calcMaterialFragment)
            } catch (_: IllegalArgumentException) {
                nav.navigateUp()
            }
        }
    }

    /** Garante que o topo da tela está visível sem flicker */
    private fun ensureTopNoFlicker(step: Int) {
        val sv = binding.scrollContent
        val root = binding.rootCalc
        val prevFocusable = root.isFocusableInTouchMode

        if (!skipFocusHijackOnce && step != 0) {
            root.isFocusableInTouchMode = true
            root.requestFocus()
        }
        skipFocusHijackOnce = false
        sv.scrollTo(0, 0)
        sv.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val vto = sv.viewTreeObserver
                if (vto.isAlive) vto.removeOnPreDrawListener(this)

                if (viewModel.step.value == step) {
                    sv.scrollTo(0, 0)
                }

                root.isFocusableInTouchMode = prevFocusable
                return true
            }
        })
    }
}