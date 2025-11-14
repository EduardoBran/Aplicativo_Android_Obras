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
        validator = FieldValidator(viewModel)
        iconManager = RequiredIconManager(requireContext())
        tableBuilder = MaterialTableBuilder(requireContext(), layoutInflater)
        pdfGenerator = PdfGenerator(requireContext()) { tableBuilder.buildComprarCell(it) }
        radioSynchronizer = RadioGroupSynchronizer()
        fieldSynchronizer = FieldSynchronizer()
        visibilityManager = VisibilityManager()
        navigationHandler = StepNavigationHandler()
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

            // Valida todos os campos da etapa atual
            val ok = viewModel.isStepValid(step) &&
                    !validator.hasAreaTotalErrorNow(etAreaInformada) &&
                    !validator.hasJuntaErrorNow(etJunta, juntaValueMm(), juntaRange()) &&
                    !validator.hasEspessuraErrorNow(
                        etPecaEsp, isPastilha(), mToMmIfLooksLikeMeters(getD(etPecaEsp))
                    ) &&
                    !validator.hasPecasPorCaixaErrorNow(etPecasPorCaixa) &&
                    !validator.hasDesnivelErrorNow(
                        etDesnivel, tilDesnivel.isVisible, getD(etDesnivel)
                    ) &&
                    tilParedeQtd.error.isNullOrEmpty() && tilAbertura.error.isNullOrEmpty()

            if (!ok) {
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

        dialog = MaterialAlertDialogBuilder(requireContext())
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
        setupMedidaField(etComp, tilComp, 0.01..10000.0, R.string.calc_err_medida_comp_larg_m)
        setupMedidaField(etLarg, tilLarg, 0.01..10000.0, R.string.calc_err_medida_comp_larg_m)
        setupMedidaField(etAlt, tilAltura, 0.01..100.0, R.string.calc_err_medida_alt_m)
        setupParedeQtdField()
        setupAberturaField()
        setupAreaInformadaField()
    }

    /** Configura campo de medida (Comp/Larg/Alt) */
    private fun setupMedidaField(
        et: TextInputEditText,
        til: TextInputLayout,
        range: ClosedRange<Double>,
        errorMsgRes: Int
    ) {
        validator.validateRangeOnBlur(et, til, { getD(et) }, range, getString(errorMsgRes))

        et.doAfterTextChanged {
            viewModel.setMedidas(
                getD(binding.etComp),
                getD(binding.etLarg),
                getD(binding.etAlt),
                getD(binding.etAreaInformada)
            )

            if (til.isVisible) {
                validator.validateDimLive(
                    et,
                    til,
                    range,
                    getString(errorMsgRes),
                    validator.isAreaTotalValidNow(binding.etAreaInformada)
                )
            } else {
                validator.setInlineError(et, til, null)
            }

            updateRequiredIconsStep3()
        }
    }

    /** Configura campo de quantidade de paredes */
    private fun setupParedeQtdField() = with(binding) {
        etParedeQtd.doAfterTextChanged {
            val qtd = etParedeQtd.text?.toString()?.toIntOrNull()
            viewModel.setParedeQtd(qtd)

            val msg = when {
                etParedeQtd.text.isNullOrBlank() -> null
                qtd == null || qtd !in 1..20 -> getString(R.string.calc_err_parede_qtd_range)
                else -> null
            }

            validator.setInlineError(etParedeQtd, tilParedeQtd, msg)
            updateRequiredIconsStep3()
            refreshNextEnabled()
        }

        validator.validateRangeOnBlur(
            etParedeQtd, tilParedeQtd,
            { etParedeQtd.text?.toString()?.toIntOrNull()?.toDouble() },
            1.0..20.0,
            getString(R.string.calc_err_parede_qtd_range)
        )
    }

    /** Configura campo de abertura (portas/janelas) */
    private fun setupAberturaField() = with(binding) {
        etAbertura.doAfterTextChanged {
            val abertura = getD(etAbertura)
            viewModel.setAberturaM2(abertura)

            val i = viewModel.inputs.value
            val c = i.compM
            val h = i.altM
            val paredes = i.paredeQtd
            val areaBruta = if (c != null && h != null && paredes != null && paredes in 1..20)
                c * h * paredes else null

            val msg = when {
                etAbertura.text.isNullOrBlank() -> null
                abertura == null || abertura < 0.0 -> getString(R.string.calc_err_abertura_negative)
                areaBruta != null && abertura > areaBruta -> getString(R.string.calc_err_abertura_maior_area)
                else -> null
            }

            validator.setInlineError(etAbertura, tilAbertura, msg)
            updateRequiredIconsStep3()
            refreshNextEnabled()
        }

        validator.validateRangeOnBlur(
            etAbertura, tilAbertura,
            { getD(etAbertura) },
            0.0..50000.0,
            getString(R.string.calc_err_abertura_negative)
        )
    }

    /** Configura campo de área total informada */
    private fun setupAreaInformadaField() = with(binding) {
        etAreaInformada.doAfterTextChanged {
            viewModel.setMedidas(getD(etComp), getD(etLarg), getD(etAlt), getD(etAreaInformada))

            val v = getD(etAreaInformada)
            val isValidArea =
                !etAreaInformada.text.isNullOrBlank() && v != null && v in 0.01..50000.0

            validator.setInlineError(
                etAreaInformada, tilAreaInformada,
                when {
                    etAreaInformada.text.isNullOrBlank() -> null
                    isValidArea -> null
                    else -> getString(R.string.calc_err_area_total)
                }
            )

            // Limpa ou revalida C/L/A conforme área total
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

        validator.validateRangeOnBlur(
            etAreaInformada, tilAreaInformada,
            { getD(etAreaInformada) },
            0.01..50000.0,
            getString(R.string.calc_err_area_total)
        )
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
            updatePecaParametros()

            val v = parsePecaToCm(getD(et))
            val inRange = when {
                isMG() -> (v != null && v in 10.0..2000.1)
                else -> (v != null && v in 5.0..200.0)
            }

            val msg = when {
                et.text.isNullOrBlank() -> null
                inRange -> null
                isMG() -> getString(R.string.calc_piece_err_mg)
                else -> getString(R.string.calc_piece_err_cm)
            }

            validator.setInlineError(et, til, msg)
            updateRequiredIconsStep4()
        }

        validator.validatePecaOnBlur(
            et = et, til = til, isMGProvider = { isMG() }, parseFunc = { parsePecaToCm(it) },
            errorMsgMG = getString(R.string.calc_piece_err_mg),
            errorMsgDefault = getString(R.string.calc_piece_err_cm)
        )
    }

    /** Configura campo de espessura */
    private fun setupEspessuraField() = with(binding) {
        etPecaEsp.doAfterTextChanged {
            if (isPastilha()) {
                updatePecaParametros()
                validator.setInlineError(etPecaEsp, tilPecaEsp, null)
                updateRequiredIconsStep4()
                refreshNextEnabled()
                return@doAfterTextChanged
            }

            updatePecaParametros()

            val (valorMm, minMm, maxMm, errMsg) = if (isIntertravado()) {
                val cm = getD(etPecaEsp)
                val mm = cm?.times(10)
                Quad(mm, 40.0, 120.0, getString(R.string.calc_err_esp_intertravado_range))
            } else {
                Quad(
                    mToMmIfLooksLikeMeters(getD(etPecaEsp)),
                    3.0,
                    30.0,
                    getString(R.string.calc_err_esp_range)
                )
            }

            val ok = (valorMm != null && valorMm in minMm..maxMm)
            val msg = when {
                etPecaEsp.text.isNullOrBlank() -> null
                ok -> null
                else -> errMsg
            }

            validator.setInlineError(etPecaEsp, tilPecaEsp, msg)
            updateRequiredIconsStep4()
            refreshNextEnabled()
        }

        // Validação on blur
        if (isIntertravado()) {
            validator.validateRangeOnBlur(
                etPecaEsp, tilPecaEsp,
                { getD(etPecaEsp)?.times(10) },
                40.0..120.0,
                getString(R.string.calc_err_esp_intertravado_range)
            )
        } else {
            validator.validateRangeOnBlur(
                etPecaEsp, tilPecaEsp,
                { mToMmIfLooksLikeMeters(getD(etPecaEsp)) },
                3.0..30.0,
                getString(R.string.calc_err_esp_range)
            )
        }
    }

    /** Configura campo de junta */
    private fun setupJuntaField() = with(binding) {
        etJunta.doAfterTextChanged {
            updatePecaParametros()

            val hasErr = validator.hasJuntaErrorNow(etJunta, juntaValueMm(), juntaRange())
            validator.setInlineError(etJunta, tilJunta, if (hasErr) juntaErrorMsg() else null)

            updateRequiredIconsStep4()
            refreshNextEnabled()
        }
    }

    /** Configura campo de peças por caixa */
    private fun setupPecasPorCaixaField() = with(binding) {
        etPecasPorCaixa.doAfterTextChanged {
            updatePecaParametros()
            updateRequiredIconsStep4()

            val n = etPecasPorCaixa.text?.toString()?.toIntOrNull()
            val ok = etPecasPorCaixa.text.isNullOrBlank() || (n != null && n in 1..50)

            validator.setInlineError(
                etPecasPorCaixa, tilPecasPorCaixa,
                if (ok) null else getString(R.string.calc_err_ppc_range)
            )
            refreshNextEnabled()
        }
    }

    /** Configura campo de desnível */
    private fun setupDesnivelField() = with(binding) {
        etDesnivel.doAfterTextChanged {
            updateDesnivelViewModel()

            val txtEmpty = etDesnivel.text.isNullOrBlank()
            val v = getD(etDesnivel)
            val (range, errRes) = desnivelRangeAndError()
            val ok = txtEmpty || (v != null && v in range)

            validator.setInlineError(etDesnivel, tilDesnivel, if (ok) null else getString(errRes))
            refreshNextEnabled()
        }

        etDesnivel.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            val txtEmpty = etDesnivel.text.isNullOrBlank()
            val v = getD(etDesnivel)
            val (range, errRes) = desnivelRangeAndError()
            val ok = txtEmpty || (v != null && v in range)
            validator.setInlineError(etDesnivel, tilDesnivel, if (ok) null else getString(errRes))
        }
    }

    /** Configura campo de sobra técnica */
    private fun setupSobraField() = with(binding) {
        etSobra.doAfterTextChanged {
            updatePecaParametros()

            val raw = etSobra.text?.toString()?.replace(",", ".")
            val s = raw?.toDoubleOrNull()

            val msg = when {
                raw.isNullOrBlank() -> getString(R.string.calc_err_sobra_range)
                s == null || s < 0.0 || s > 50.0 -> getString(R.string.calc_err_sobra_range)
                else -> null
            }

            validator.setInlineError(etSobra, tilSobra, msg)

            if (viewModel.step.value in 1..7) refreshNextEnabled()
            updateRequiredIconsStep4()
        }

        etSobra.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener

            // Se vazio ao sair do campo, restaura automaticamente 10%
            if (etSobra.text.isNullOrBlank()) {
                etSobra.setText(getString(R.string.valor_sobra_minima))
                updatePecaParametros()
                validator.setInlineError(etSobra, tilSobra, null)

                if (viewModel.step.value in 1..7) refreshNextEnabled()
                return@setOnFocusChangeListener
            }

            // Revalida valor existente ao perder o foco
            val s = getD(etSobra)
            val msg = when {
                s == null || s < 0.0 || s > 50.0 -> getString(R.string.calc_err_sobra_range)
                else -> null
            }

            validator.setInlineError(etSobra, tilSobra, msg)

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
            updateRodapeViewModel()
            updateRequiredIconsStep5()

            val v = mToCmIfLooksLikeMeters(getD(etRodapeAltura))
            val msg = when {
                etRodapeAltura.text.isNullOrBlank() -> null
                v == null || v !in 3.0..30.0 -> getString(R.string.calc_err_rodape_altura)
                else -> null
            }
            validator.setInlineError(etRodapeAltura, tilRodapeAltura, msg)
        }

        validator.validateRangeOnBlur(
            etRodapeAltura, tilRodapeAltura,
            { mToCmIfLooksLikeMeters(getD(etRodapeAltura)) },
            3.0..30.0,
            getString(R.string.calc_err_rodape_altura)
        )

        etRodapeAbertura.doAfterTextChanged {
            updateRodapeViewModel()
            updateRequiredIconsStep5()

            val texto = etRodapeAbertura.text?.toString()
            val aberturaM = getD(etRodapeAbertura)

            val msg = if (texto.isNullOrBlank()) {
                null
            } else {
                val maxRodape = viewModel.getRodapePerimetroPossivel()
                if (aberturaM == null || aberturaM < 0.0) {
                    getString(R.string.calc_err_rodape_abertura_maior_perimetro)
                } else if (maxRodape != null && aberturaM > maxRodape) {
                    getString(R.string.calc_err_rodape_abertura_maior_perimetro)
                } else {
                    null
                }
            }

            validator.setInlineError(etRodapeAbertura, tilRodapeAbertura, msg)
            if (viewModel.step.value in 1..7) refreshNextEnabled()
        }

        validator.validateRangeOnBlur(
            etRodapeAbertura, tilRodapeAbertura,
            { getD(etRodapeAbertura) },
            0.0..10000.0,
            getString(R.string.calc_err_rodape_abertura_maior_perimetro)
        )

        etRodapeCompComercial.doAfterTextChanged {
            updateRodapeViewModel()
            updateRequiredIconsStep5()

            val isPecaProntaSelecionada = switchRodape.isChecked &&
                    rgRodapeMat.checkedRadioButtonId == R.id.rbRodapePeca &&
                    tilRodapeCompComercial.isVisible

            val compCm = getD(etRodapeCompComercial)

            val msg = when {
                !isPecaProntaSelecionada || etRodapeCompComercial.text.isNullOrBlank() -> null
                compCm == null || compCm !in 5.0..300.0 -> getString(R.string.calc_err_rodape_comp_cm_range)
                else -> null
            }

            validator.setInlineError(etRodapeCompComercial, tilRodapeCompComercial, msg)
            if (viewModel.step.value in 1..7) refreshNextEnabled()
        }

        validator.validateRangeOnBlur(
            etRodapeCompComercial, tilRodapeCompComercial,
            { getD(etRodapeCompComercial) },
            5.0..300.0,
            getString(R.string.calc_err_rodape_comp_cm_range)
        )
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
            updateHelperTexts(i)
            updateJuntaHelper(i)

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
    }

    /** Normaliza exibição dos valores padrão auto-preenchidos */
    private fun normalizePredefinedDefaults() = with(binding) {
        val i = viewModel.inputs.value
        normalizeAutoField(etPecaEsp, i.pecaEspMm)
        normalizeAutoField(etJunta, i.juntaMm)
        normalizeAutoField(etSobra, i.sobraPct)
    }

    private fun normalizeAutoField(editText: TextInputEditText, value: Double?) {
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
            viewModel.inputs.value.revest,
            groupPecaTamanho.isVisible
        )
    }

    /** Atualiza ícones obrigatórios da Etapa 6 (Rodapé) */
    private fun updateRequiredIconsStep5() = with(binding) {
        iconManager.updateStep5Icons(
            etRodapeAltura, etRodapeCompComercial,
            switchRodape.isChecked,
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
            0.01..10000.0,
            getString(R.string.calc_err_medida_comp_larg_m),
            false
        )
        validator.validateDimLive(
            etLarg,
            tilLarg,
            0.01..10000.0,
            getString(R.string.calc_err_medida_comp_larg_m),
            false
        )
        if (tilAltura.isVisible) {
            validator.validateDimLive(
                etAlt,
                tilAltura,
                0.01..100.0,
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

        btnNext.isEnabled = validation.isValid &&
                !validator.hasAreaTotalErrorNow(etAreaInformada) &&
                !validator.hasJuntaErrorNow(etJunta, juntaValueMm(), juntaRange()) &&
                !validator.hasEspessuraErrorNow(
                    etPecaEsp,
                    isPastilha(),
                    mToMmIfLooksLikeMeters(getD(etPecaEsp))
                ) &&
                !validator.hasPecasPorCaixaErrorNow(etPecasPorCaixa) &&
                !validator.hasDesnivelErrorNow(
                    etDesnivel,
                    tilDesnivel.isVisible,
                    getD(etDesnivel)
                ) &&
                tilParedeQtd.error.isNullOrEmpty() && tilAbertura.error.isNullOrEmpty() &&
                tilRodapeAbertura.error.isNullOrEmpty() && tilSobra.error.isNullOrEmpty()

        // Regra específica para Mármore/Granito
        if ((inputs.revest == CalcRevestimentoViewModel.RevestimentoType.MARMORE ||
                    inputs.revest == CalcRevestimentoViewModel.RevestimentoType.GRANITO) &&
            step >= 5
        ) {
            // ✅ Validação direta pelos valores atuais digitados (m → cm)
            val compValid = parsePecaToCm(getD(etPecaComp))?.let { it in 10.0..2000.1 } == true
            val largValid = parsePecaToCm(getD(etPecaLarg))?.let { it in 10.0..2000.1 } == true

            // Ignora o estado textual de erro (que pode estar “grudado”)
            btnNext.isEnabled = btnNext.isEnabled && compValid && largValid
        }

        // Validação extra para rodapé peça pronta
        if (inputs.rodapeEnable &&
            inputs.rodapeMaterial == CalcRevestimentoViewModel.RodapeMaterial.PECA_PRONTA
        ) {
            val alturaCm = mToCmIfLooksLikeMeters(getD(etRodapeAltura))
            val compCm = getD(etRodapeCompComercial)

            val alturaOk =
                alturaCm != null && alturaCm in 3.0..30.0 && tilRodapeAltura.error.isNullOrEmpty()
            val compOk =
                compCm != null && compCm in 5.0..300.0 && tilRodapeCompComercial.error.isNullOrEmpty()

            btnNext.isEnabled = btnNext.isEnabled && alturaOk && compOk
        }
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * HELPER TEXTS
     * ═══════════════════════════════════════════════════════════════════════════ */

    /** Atualiza helper texts dinâmicos */
    private fun updateHelperTexts(i: CalcRevestimentoViewModel.Inputs) = with(binding) {
        val padraoEsp = viewModel.espessuraPadraoAtual()

        // Espessura
        if (i.revest == CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO) {
            tilPecaEsp.hint = getString(R.string.calc_step4_peca_esp_intertravado_hint)
            tilPecaEsp.setHelperTextSafely(getString(R.string.calc_step4_peca_esp_intertravado_helper))
        } else {
            tilPecaEsp.hint = getString(R.string.calc_step4_peca_esp)
            tilPecaEsp.setHelperTextSafely(
                getString(
                    R.string.calc_step4_peca_esp_helper_with_default,
                    NumberFormatter.format(padraoEsp)
                )
            )
        }

        val mg = i.revest in setOf(
            CalcRevestimentoViewModel.RevestimentoType.MARMORE,
            CalcRevestimentoViewModel.RevestimentoType.GRANITO
        )
        val pastilha = (i.revest == CalcRevestimentoViewModel.RevestimentoType.PASTILHA)

        // Peça
        when {
            pastilha -> {
                tilJunta.setHelperTextSafely(getString(R.string.calc_pastilha_junta_helper))
            }

            else -> {
                tilJunta.setHelperTextSafely(getString(R.string.calc_step4_junta_helper_default))
                tilPecaComp.hint =
                    getString(if (mg) R.string.calc_step4_peca_comp_m else R.string.calc_step4_peca_comp)
                tilPecaLarg.hint =
                    getString(if (mg) R.string.calc_step4_peca_larg_m else R.string.calc_step4_peca_larg)
                tilPecaComp.setHelperTextSafely(
                    getString(if (mg) R.string.calc_piece_helper_mg else R.string.calc_piece_helper_cm)
                )
                tilPecaLarg.setHelperTextSafely(
                    getString(if (mg) R.string.calc_piece_helper_mg else R.string.calc_piece_helper_cm)
                )
            }
        }

        // Desnível
        when (i.revest) {
            CalcRevestimentoViewModel.RevestimentoType.PEDRA ->
                tilDesnivel.setHelperTextSafely(getString(R.string.calc_step4_desnivel_helper_pedra))

            CalcRevestimentoViewModel.RevestimentoType.MARMORE,
            CalcRevestimentoViewModel.RevestimentoType.GRANITO ->
                tilDesnivel.setHelperTextSafely(getString(R.string.calc_step4_desnivel_helper_mg))

            else -> tilDesnivel.setHelperTextSafely(null)
        }
    }

    /** Atualiza helper text de junta */
    private fun updateJuntaHelper(i: CalcRevestimentoViewModel.Inputs) = with(binding) {
        tilJunta.helperText = when (i.revest) {
            CalcRevestimentoViewModel.RevestimentoType.PISO -> {
                if (i.pisoPlacaTipo == CalcRevestimentoViewModel.PlacaTipo.PORCELANATO) {
                    getString(R.string.helper_junta_piso_porcelanato)
                } else {
                    getString(R.string.helper_junta_piso_ceramico)
                }
            }

            CalcRevestimentoViewModel.RevestimentoType.PASTILHA -> getString(R.string.calc_pastilha_junta_helper)
            CalcRevestimentoViewModel.RevestimentoType.AZULEJO -> getString(R.string.helper_junta_azulejo)
            CalcRevestimentoViewModel.RevestimentoType.PEDRA -> getString(R.string.helper_junta_pedra_portuguesa)
            CalcRevestimentoViewModel.RevestimentoType.MARMORE,
            CalcRevestimentoViewModel.RevestimentoType.GRANITO -> getString(R.string.helper_junta_marmore_granito)

            CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO -> getString(R.string.helper_junta_piso_intertravado)
            else -> null
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

        // ✅ Aplicar responsividade após renderização
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
    private fun juntaRange() = if (isPastilha()) 1.0..5.0 else 0.5..20.0
    private fun juntaErrorMsg() = getString(
        if (isPastilha()) R.string.calc_err_junta_pastilha_range
        else R.string.calc_err_junta_range
    )

    /** Retorna range e mensagem de erro para desnível */
    private fun desnivelRangeAndError(): Pair<ClosedRange<Double>, Int> {
        return when (viewModel.inputs.value.revest) {
            CalcRevestimentoViewModel.RevestimentoType.PEDRA -> (4.0..8.0) to R.string.calc_err_desnivel_pedra_range
            CalcRevestimentoViewModel.RevestimentoType.MARMORE,
            CalcRevestimentoViewModel.RevestimentoType.GRANITO -> (0.0..3.0) to R.string.calc_err_desnivel_mg_range

            else -> (Double.NEGATIVE_INFINITY..Double.POSITIVE_INFINITY) to R.string.calc_err_generic
        }
    }

    /** Define helper text de forma segura (preserva erro atual) */
    private fun TextInputLayout.setHelperTextSafely(newText: CharSequence?) {
        if (helperText == newText) return
        val currentError = error
        helperText = newText
        if (!currentError.isNullOrEmpty()) {
            isErrorEnabled = true
            error = currentError
        }
    }

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

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}