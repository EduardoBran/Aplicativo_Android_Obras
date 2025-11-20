package com.luizeduardobrandao.obra.ui.calculo

import android.content.ClipData
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
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
 * Wizard de etapas: 1. Tela Inicial | 2. Revestimento | 3. Ambiente | 3. Tráfego (Intertravado)
 * 4. Medidas da Área | 5. Medidas da Peça | 6. Revisão de Parâmetros | 7. Tabela Resultado
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
    private lateinit var deferredValidator: DelayValidationMsgField

    /** ======================= CICLO DE VIDA ======================= */
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
        initializeAllHelpers()
        setupToolbar()
        setupAllUiComponents()
        setupStateObservers()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (pendingShareConfirm && leftAppForShare) {
            pendingShareConfirm = false
            leftAppForShare = false
            showToast(getString(R.string.resumo_export_shared_ok))
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
        if (::deferredValidator.isInitialized) { // Cancela qualquer validação adiada pendente
            deferredValidator.cancelAll()
        }
        _binding = null
    }

    /** ======================= INICIALIZAÇÃO ======================= */
    // Inicializa todos os gerenciadores de UI
    private fun initializeAllHelpers() {
        toolbarManager = ToolbarManager(
            binding = binding, navController = findNavController(),
            onPrevStep = { viewModel.prevStep() }, onExport = { share -> exportResultAsPdf(share) },
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
        deferredValidator = DelayValidationMsgField() // delay das validações visuais
    }

    private fun setupToolbar() = toolbarManager.setup() // Configuração da toolbar

    /** ======================= SETUP DA UI ======================= */
    // Configura todos os componentes da UI
    private fun setupAllUiComponents() {
        enableErrorSlotsForAllFields()
        setupNavigationButtons()
        setupStep1RevTypeListeners()
        setupStep2AmbTypeListeners()
        setupStep3TrafTypeListeners()
        setupStep4AreaListeners()
        setupStep4Switches()
        setupStep5PecaParametersListeners()
        setupRodapeListeners()
        setupStep6ReviewParametersListeners()
    }

    // Habilita slots de erro para todos os campos
    private fun enableErrorSlotsForAllFields() = with(binding) {
        listOf(
            tilComp, tilLarg, tilAltura, tilAreaInformada, tilParedeQtd, tilAbertura,
            tilPecaComp, tilPecaLarg, tilPecaEsp, tilJunta, tilPecasPorCaixa, tilDesnivel, tilSobra,
            tilRodapeAltura, tilRodapeAbertura, tilRodapeCompComercial
        ).forEach { it.isErrorEnabled = true }
    }

    /** ======================= NAVEGAÇÃO ======================= */
    // Configura botões de navegação (Iniciar/Voltar/Avançar/Cancelar)
    private fun setupNavigationButtons() = with(binding) {
        btnNext.setOnClickListener { handleNextButtonClick() }
        btnBack.setOnClickListener { rootCalc.post { viewModel.prevStep() } }
        btnStart.setOnClickListener { rootCalc.post { viewModel.nextStep() } }
        btnCancel.setOnClickListener { findNavController().navigateUp() }
    }

    // Trata clique no botão "Avançar" - Valida campos antes de prosseguir
    private fun handleNextButtonClick() {
        with(binding) {
            refreshNextButtonEnabled() // Recalcula estado do botão com todas as regras por etapa
            if (!btnNext.isEnabled) {
                showToast(getString(R.string.calc_validate_step))
                return
            }
            rootCalc.post { viewModel.nextStep() }
        }
    }

    /** ======================= LISTENERS DE ETAPAS ======================= */
    // Etapa 1: Tipo de revestimento
    private fun setupStep1RevTypeListeners() = with(binding) {
        rgRevest.setOnCheckedChangeListener { _, id ->
            if (isSyncing) return@setOnCheckedChangeListener
            val newType = mapRadioIdToRevestimento(id) ?: return@setOnCheckedChangeListener
            // Sempre limpar tipo de placa ao trocar de revestimento
            viewModel.setPlacaTipo(null)
            // Se trocou para Mármore/Granito, zera aplicação (Piso/Parede) do grupo novo
            if (newType == CalcRevestimentoViewModel.RevestimentoType.MARMORE ||
                newType == CalcRevestimentoViewModel.RevestimentoType.GRANITO
            ) {
                viewModel.setAplicacao(null)
            }
            viewModel.setRevestimento(newType)
            espessuraUserEdited = false // Reset no controle de auto-preenchimento
            refreshNextButtonEnabled()
        }
        // Aplicação (Cerâmica / Porcelanato) específica de Piso/Azulejo/Pastilha
        rgPlacaTipo.setOnCheckedChangeListener { _, id ->
            if (isSyncing) return@setOnCheckedChangeListener
            val placa = mapRadioIdToPlacaTipo(id)
            viewModel.setPlacaTipo(placa)
            refreshNextButtonEnabled()
        }
        // Aplicação (Piso / Parede) específica de Mármore/Granito
        rgMgAplicacao.setOnCheckedChangeListener { _, checkedId ->
            if (isSyncing) return@setOnCheckedChangeListener
            val aplicacao = when (checkedId) {
                rbMgPiso.id -> CalcRevestimentoViewModel.AplicacaoType.PISO
                rbMgParede.id -> CalcRevestimentoViewModel.AplicacaoType.PAREDE
                else -> null
            }
            viewModel.setAplicacao(aplicacao)
            refreshNextButtonEnabled()
        }
    }

    // Etapa 2: Tipo de ambiente
    private fun setupStep2AmbTypeListeners() = with(binding) {
        rgAmbiente.setOnCheckedChangeListener { _, id ->
            if (isSyncing) return@setOnCheckedChangeListener
            val amb = mapRadioIdToAmbiente(id)
            amb?.let { viewModel.setAmbiente(it) }
        }
    }

    // Etapa 3: Tipo de tráfego (apenas Piso Intertravado)
    private fun setupStep3TrafTypeListeners() = with(binding) {
        rgTrafego.setOnCheckedChangeListener { _, id ->
            if (isSyncing) return@setOnCheckedChangeListener
            val trafego = mapRadioIdToTrafego(id)
            viewModel.setTrafego(trafego)
            refreshNextButtonEnabled()
        }
    }

    // Etapa 4: Medidas do ambiente
    private fun setupStep4AreaListeners() = with(binding) {
        setupAreaDimensionFields(etComp, tilComp)
        setupAreaDimensionFields(etLarg, tilLarg)
        setupAreaDimensionFields(etAlt, tilAltura)
        setupParedeQtdField()
        setupAberturaAreaField()
        setupTotalAreaField()
    }

    // Configura campos de medidas da área (Comp/Larg/Alt)
    private fun setupAreaDimensionFields(
        et: TextInputEditText,
        til: TextInputLayout
    ) {
        et.doAfterTextChanged {
            viewModel.setMedidas(
                getDoubleValue(binding.etComp), getDoubleValue(binding.etLarg),
                getDoubleValue(binding.etAlt), getDoubleValue(binding.etAreaInformada)
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
            updateRequiredIconsForStep3Area()

            if (binding.tilAbertura.isVisible &&
                !binding.etAbertura.text.isNullOrBlank() &&
                binding.etAreaInformada.text.isNullOrBlank()
            ) { // Revalida Abertura com a área bruta nova (comp/alt/parede atualizados no ViewModel)
                validator.validateAberturaLive(binding.etAbertura, binding.tilAbertura)
            }
            refreshNextButtonEnabled()
        }

        if (et === binding.etAlt) {
            validator.validateRangeOnBlur(
                et, til, { getDoubleValue(et) }, CalcRevestimentoRules.Medidas.ALTURA_RANGE_M,
                getString(R.string.calc_err_medida_alt_m)
            )
        } else {
            validator.validateRangeOnBlur(
                et, til, { getDoubleValue(et) }, CalcRevestimentoRules.Medidas.COMP_LARG_RANGE_M,
                getString(R.string.calc_err_medida_comp_larg_m)
            )
        }
    }

    // Configura campo de quantidade de paredes
    private fun setupParedeQtdField() = with(binding) {
        etParedeQtd.doAfterTextChanged {
            val qtd = etParedeQtd.text?.toString()?.toIntOrNull()
            viewModel.setParedeQtd(qtd)

            validator.validateParedeQtdLive(etParedeQtd, tilParedeQtd)
            updateRequiredIconsForStep3Area()

            if (tilAbertura.isVisible &&
                !etAbertura.text.isNullOrBlank() &&
                etAreaInformada.text.isNullOrBlank()
            ) {
                validator.validateAberturaLive(etAbertura, tilAbertura)
            }
            refreshNextButtonEnabled()
        }
        validator.validateParedeQtdOnBlur(etParedeQtd, tilParedeQtd)
    }

    // Configura campo de abertura (portas/janelas)
    private fun setupAberturaAreaField() = with(binding) {
        etAbertura.doAfterTextChanged {
            val abertura = getDoubleValue(etAbertura)
            viewModel.setAberturaM2(abertura)

            validator.validateAberturaLive(etAbertura, tilAbertura)
            updateRequiredIconsForStep3Area()
            refreshNextButtonEnabled()
        }
        validator.validateAberturaOnBlur(etAbertura, tilAbertura)
    }

    // Configura campo de área total informada
    private fun setupTotalAreaField() = with(binding) {
        etAreaInformada.doAfterTextChanged {
            viewModel.setMedidas(
                getDoubleValue(etComp), getDoubleValue(etLarg),
                getDoubleValue(etAlt), getDoubleValue(etAreaInformada)
            )
            // Valida a própria Área Total
            validator.validateAreaInformadaLive(etAreaInformada, tilAreaInformada)
            // Limpa ou revalida C/L/A + Parede + Abertura conforme Área Total
            val isValidArea = validator.isAreaTotalValidNow(etAreaInformada)
            if (isValidArea) {
                // Área Total válida → DOMINA a etapa 4
                validator.setInlineError(etComp, tilComp, null)
                validator.setInlineError(etLarg, tilLarg, null)
                if (tilAltura.isVisible) {
                    validator.setInlineError(etAlt, tilAltura, null)
                }
                //Limpar também Parede (qtd) e Abertura
                validator.setInlineError(etParedeQtd, tilParedeQtd, null)
                validator.setInlineError(etAbertura, tilAbertura, null)
            } else { // Área Total vazia ou inválida → volta a usar as dimensões
                validateAllAreaDimensions()
                // Revalida Parede/Abertura com o novo contexto
                validator.validateParedeQtdLive(etParedeQtd, tilParedeQtd)
                validator.validateAberturaLive(etAbertura, tilAbertura)
            }
            tvAreaTotalAviso.isVisible = !etAreaInformada.text.isNullOrBlank()
            updateRequiredIconsForStep3Area()
            refreshNextButtonEnabled()
        }
        validator.validateAreaInformadaOnBlur(etAreaInformada, tilAreaInformada)
    }

    // Etapa 5: Parâmetros da peça
    private fun setupStep5PecaParametersListeners() = with(binding) {
        setupPecaDimensionsFields(etPecaComp, tilPecaComp)
        setupPecaDimensionsFields(etPecaLarg, tilPecaLarg)
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
            updateRequiredIconsForStep4PecaParameters()
            refreshNextButtonEnabled()
        }

        rgPastilhaPorcelanatoTamanho.setOnCheckedChangeListener { _, id ->
            if (isSyncing) return@setOnCheckedChangeListener
            val formato = mapRadioIdToPastilhaFormato(id)
            viewModel.setPastilhaFormato(formato)
            updateRequiredIconsForStep4PecaParameters()
            refreshNextButtonEnabled()
        }
    }

    // Etapa 4: Switches extras (Peças por caixa / Desnível MG)
    private fun setupStep4Switches() = with(binding) {
        // Switch "Peças por caixa"
        switchPecasPorCaixa.setOnCheckedChangeListener { _, isChecked ->
            if (isSyncing) return@setOnCheckedChangeListener
            val inputs = viewModel.inputs.value
            groupPecasPorCaixaFields.isVisible = isChecked // Campos visível apenas quando ligado

            if (!isChecked) { // Desabilita switch → limpa valor e ignora no cálculo/validação
                etPecasPorCaixa.text = null
                viewModel.setPecaParametros(
                    inputs.pecaCompCm, inputs.pecaLargCm, inputs.pecaEspMm,
                    inputs.juntaMm, inputs.sobraPct, null
                )
                validator.setInlineError(etPecasPorCaixa, tilPecasPorCaixa, null)
            }
            updateRequiredIconsForStep4PecaParameters() // Recalcula ícones obrigatórios
            smoothScrollToggle(switchPecasPorCaixa.isChecked) // Animação de scroll
            if (viewModel.step.value in 1..6) {
                refreshNextButtonEnabled()
            }
        }
        // Switch "Desnível" (layout só para MG)
        switchDesnivel.setOnCheckedChangeListener { _, isChecked ->
            if (isSyncing) return@setOnCheckedChangeListener

            val inputs = viewModel.inputs.value
            val revest = inputs.revest
            val isMg =
                revest == CalcRevestimentoViewModel.RevestimentoType.MARMORE ||
                        revest == CalcRevestimentoViewModel.RevestimentoType.GRANITO
            val isPedra = revest == CalcRevestimentoViewModel.RevestimentoType.PEDRA

            if (!isMg) { // Pedra: sem switch "lógico", apenas visibilidade antiga
                groupDesnivelFields.isVisible = isPedra && tilDesnivel.isVisible
                return@setOnCheckedChangeListener
            }
            viewModel.setDesnivelEnable(isChecked)
            groupDesnivelFields.isVisible = isChecked

            if (isChecked) {
                val current = getDoubleValue(etDesnivel)
                val hasError = validator.hasDesnivelErrorNow(
                    etDesnivel,
                    true,
                    current
                )
                if (current == null || hasError) {
                    val defaultMg = CalcRevestimentoRules.Desnivel.MG_DEFAULT_CM
                    etDesnivel.setText(defaultMg.toString().replace(".", ","))
                    viewModel.setDesnivelCm(defaultMg)
                    validator.setInlineError(etDesnivel, tilDesnivel, null)
                } else {
                    viewModel.setDesnivelCm(current)
                }
            } else {
                etDesnivel.text = null
                viewModel.setDesnivelCm(null)
                validator.setInlineError(etDesnivel, tilDesnivel, null)
            }
            updateRequiredIconsForStep4PecaParameters()  // Recalcula ícones obrigatórios
            smoothScrollToggle(switchDesnivel.isChecked) // Animação de scroll
            if (viewModel.step.value in 1..6) {
                refreshNextButtonEnabled()
            }
        }
    }

    // Configura campos de peça
    private fun setupPecaDimensionsFields(et: TextInputEditText, til: TextInputLayout) {
        et.doAfterTextChanged {
            val text = et.text?.toString().orEmpty()
            updatePecaParametersFields() // Atualiza ViewModel e botão em tempo real
            if (viewModel.step.value in 1..6) {
                refreshNextButtonEnabled()
            }
            applyDeferredValidationPattern(
                et = et, til = til, text = text,
                onValidateNow = {
                    validator.validatePecaLive(et, til)
                    updateRequiredIconsForStep4PecaParameters()
                },
                onAfterDeferredValidation = {
                    if (viewModel.step.value in 1..6) {
                        refreshNextButtonEnabled()
                    }
                },
                onFirstDigitBeforeSchedule = {
                    updateRequiredIconsForStep4PecaParameters()
                }
            )
        }
        validator.validatePecaOnBlur(et, til)
    }

    // Configura campo de espessura
    private fun setupEspessuraField() = with(binding) {
        etPecaEsp.doAfterTextChanged { // Validação em tempo real
            if (isSyncing) return@doAfterTextChanged
            espessuraUserEdited = true // Qualquer alteração conta como edição do usuário
            updatePecaParametersFields()

            validator.validateEspessuraLive(
                et = etPecaEsp, til = tilPecaEsp, isPastilha = isPastilha(),
                espValueMm = when {
                    isPastilha() -> null
                    isIntertravado() -> getDoubleValue(etPecaEsp)?.times(10)
                    else -> convertMetersToMillimeters(getDoubleValue(etPecaEsp))
                }
            )
            updateRequiredIconsForStep4PecaParameters()
            refreshNextButtonEnabled()
        }

        etPecaEsp.setOnFocusChangeListener { _, hasFocus -> // Validação ao perder o foco
            if (hasFocus) return@setOnFocusChangeListener
            validator.validateEspessuraLive(
                et = etPecaEsp, til = tilPecaEsp, isPastilha = isPastilha(),
                espValueMm = when {
                    isPastilha() -> null
                    isIntertravado() -> getDoubleValue(etPecaEsp)?.times(10)
                    else -> convertMetersToMillimeters(getDoubleValue(etPecaEsp))
                }
            )
            refreshNextButtonEnabled()
        }
    }

    // Configura campo de junta
    private fun setupJuntaField() = with(binding) {
        etJunta.doAfterTextChanged {
            if (isSyncing) return@doAfterTextChanged
            updatePecaParametersFields()
            validator.validateJuntaLive(
                etJunta, tilJunta, getFieldJuntaValueInMillimeters()
            )
            updateRequiredIconsForStep4PecaParameters()
            refreshNextButtonEnabled()
        }
    }

    // Configura campo de peças por caixa
    private fun setupPecasPorCaixaField() = with(binding) {
        etPecasPorCaixa.doAfterTextChanged {
            updatePecaParametersFields()
            updateRequiredIconsForStep4PecaParameters()
            validator.validatePecasPorCaixaLive(etPecasPorCaixa, tilPecasPorCaixa)
            refreshNextButtonEnabled()
        }
    }

    // Configura campo de desnível
    private fun setupDesnivelField() = with(binding) {
        etDesnivel.doAfterTextChanged {
            updateDesnivelField()
            val v = getDoubleValue(etDesnivel)
            validator.validateDesnivelLive(
                etDesnivel, tilDesnivel, tilDesnivel.isVisible, v
            )
            refreshNextButtonEnabled()
        }
        etDesnivel.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            val v = getDoubleValue(etDesnivel)
            validator.validateDesnivelOnBlur(
                etDesnivel, tilDesnivel, tilDesnivel.isVisible, v
            )
        }
    }

    // Configura campo de sobra técnica
    private fun setupSobraField() = with(binding) {
        etSobra.doAfterTextChanged {
            updatePecaParametersFields()
            validator.validateSobraLive(etSobra, tilSobra)
            if (viewModel.step.value in 1..6) refreshNextButtonEnabled()
            updateRequiredIconsForStep4PecaParameters()
        }
        etSobra.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            validator.validateSobraOnBlur(etSobra, tilSobra)
            if (viewModel.step.value in 1..6) refreshNextButtonEnabled()
        }
    }

    // Etapa: Rodapé
    private fun setupRodapeListeners() = with(binding) {
        switchRodape.setOnCheckedChangeListener { _, _ ->
            updateRodapeFields()
            updateRequiredIconsRodapeFields()
            smoothScrollToggle(switchRodape.isChecked) // Scroll suave para topo/fim conforme estado do switch
            if (viewModel.step.value in 1..6) {
                refreshNextButtonEnabled()
            }
        }

        rgRodapeMat.setOnCheckedChangeListener { _, checkedId ->
            if (isSyncing) return@setOnCheckedChangeListener
            val isPecaPronta = (checkedId == R.id.rbRodapePeca)
            tilRodapeCompComercial.isVisible = isPecaPronta

            if (!isPecaPronta) {
                etRodapeCompComercial.text?.clear()
                tilRodapeCompComercial.error = null
            }
            updateRodapeFields()
            updateRequiredIconsRodapeFields()
            if (switchRodape.isChecked && isPecaPronta) {
                smoothScrollToggle(true)
            }
            if (viewModel.step.value in 1..6) refreshNextButtonEnabled()
        }

        etRodapeAltura.doAfterTextChanged {
            val text = etRodapeAltura.text?.toString().orEmpty()
            updateRodapeFields()
            updateRequiredIconsRodapeFields()
            if (viewModel.step.value in 1..6) {
                refreshNextButtonEnabled()
            }
            applyDeferredValidationPattern(
                et = etRodapeAltura, til = tilRodapeAltura, text = text,
                onValidateNow = {
                    validator.validateRodapeAlturaLive(etRodapeAltura, tilRodapeAltura)
                },
                onAfterDeferredValidation = {
                    if (viewModel.step.value in 1..6) {
                        refreshNextButtonEnabled()
                    }
                }
            )
        }
        validator.validateRodapeAlturaOnBlur(etRodapeAltura, tilRodapeAltura)

        etRodapeAbertura.doAfterTextChanged {
            updateRodapeFields()
            updateRequiredIconsRodapeFields()
            validator.validateRodapeAberturaLive(etRodapeAbertura, tilRodapeAbertura)
            if (viewModel.step.value in 1..6) refreshNextButtonEnabled()
        }
        validator.validateRodapeAberturaOnBlur(etRodapeAbertura, tilRodapeAbertura)

        etRodapeCompComercial.doAfterTextChanged {
            val text = etRodapeCompComercial.text?.toString().orEmpty()
            updateRodapeFields()
            updateRequiredIconsRodapeFields()
            if (viewModel.step.value in 1..6) {
                refreshNextButtonEnabled()
            }

            // Verifica se o campo está realmente valendo (só quando peça pronta)
            fun isPecaProntaSelecionada(): Boolean =
                switchRodape.isChecked &&
                        rgRodapeMat.checkedRadioButtonId == R.id.rbRodapePeca &&
                        tilRodapeCompComercial.isVisible

            applyDeferredValidationPattern(
                et = etRodapeCompComercial, til = tilRodapeCompComercial, text = text,
                onValidateNow = {
                    validator.validateRodapeCompComercialLive(
                        etRodapeCompComercial, tilRodapeCompComercial, isPecaProntaSelecionada()
                    )
                },
                onAfterDeferredValidation = {
                    if (viewModel.step.value in 1..6) {
                        refreshNextButtonEnabled()
                    }
                }
            )
        }
        validator.validateRodapeCompComercialOnBlur(etRodapeCompComercial, tilRodapeCompComercial)
    }

    // Etapa 6: Revisão e cálculo
    private fun setupStep6ReviewParametersListeners() = with(binding) {
        btnCalcular.setOnClickListener { viewModel.calcular() }
        btnVoltarResultado.setOnClickListener { viewModel.goTo(6) }
    }

    /** ======================= OBSERVERS ======================= */
    // Configura observers para StateFlow do ViewModel
    private fun setupStateObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeCurrentStep() }
                launch { observeUserInputs() }
                launch { observeResultado() }
            }
        }
    }

    // Observa mudanças na etapa atual
    private suspend fun observeCurrentStep() {
        viewModel.step.collect { step ->
            binding.viewFlipper.displayedChild = step
            syncAllRadioGroupsWithViewModel()
            handleAdjustUiForCurrentStep(step)
            handleInitRequiredIcons(step)
            handleConfNavButtonsForStep(step)
            toolbarManager.updateForStep(step)
            handleDisplayReviewParameters(step)
            ensureScrollAtTopWithoutFlicker(step)
            refreshNextButtonEnabled()
        }
    }

    // Observa mudanças nos inputs
    private suspend fun observeUserInputs() {
        viewModel.inputs.collect { i ->
            syncAllRadioGroupsWithViewModel()
            syncAllFieldsValues()
            updateAllComponentsVisibility()
            // Espessura padrão automática para Mármore/Granito
            validator.ensureDefaultMgEspessura(espessuraUserEdited)
            // Helpers dinâmicos (espessura, peça, junta, desnível)
            validator.updateStep4HelperTexts(
                i,
                binding.tilPecaEsp, binding.tilPecaComp, binding.tilPecaLarg,
                binding.tilJunta, binding.tilDesnivel
            )

            val hasPecasPorCaixa = i.pecasPorCaixa != null // Sincroniza switch com os inputs
            if (hasPecasPorCaixa) { // Se existe valor na ViewModel → switch ON e grupo visível
                if (!binding.switchPecasPorCaixa.isChecked) {
                    isSyncing = true
                    binding.switchPecasPorCaixa.isChecked = true
                    isSyncing = false
                }
                binding.groupPecasPorCaixaFields.isVisible = true
            } else { // Só ajusta a visibilidade do grupo conforme o switch atual
                binding.groupPecasPorCaixaFields.isVisible = binding.switchPecasPorCaixa.isChecked
            }
            // Desnível: regra diferente para Pedra x MG
            val isMg = i.revest == CalcRevestimentoViewModel.RevestimentoType.MARMORE ||
                    i.revest == CalcRevestimentoViewModel.RevestimentoType.GRANITO
            val isPedra = i.revest == CalcRevestimentoViewModel.RevestimentoType.PEDRA
            binding.rowDesnivelSwitchStep4.isVisible = isMg
            binding.switchDesnivel.isVisible = isMg
            binding.switchDesnivel.isChecked = isMg && i.desnivelEnable

            binding.groupDesnivelFields.isVisible = when {
                isPedra -> binding.tilDesnivel.isVisible       // Pedra: sem switch
                isMg -> i.desnivelEnable                       // MG: visível só se switch ligado
                else -> false
            }
            if (viewModel.step.value in 1..6) refreshNextButtonEnabled()
        }
    }

    // Observa resultado do cálculo
    private suspend fun observeResultado() {
        viewModel.resultado.collect { ui ->
            when (ui) {
                is UiState.Success -> {
                    displayCalculationResult(ui.data.resultado)
                    binding.btnVoltarResultado.isVisible = true
                }

                else -> {
                    binding.btnVoltarResultado.isVisible = false
                }
            }
        }
    }

    /** ======================= SINCRONIZAÇÃO DE UI ======================= */
    // Sincroniza RadioGroups com o estado do ViewModel
    private fun syncAllRadioGroupsWithViewModel() = with(binding) {
        isSyncing = true
        radioSynchronizer.syncAllRadioGroups(
            viewModel.inputs.value,
            rgRevest, rgPlacaTipo, rgAmbiente, rgRodapeMat, rgTrafego,
            rgPastilhaTamanho, rgPastilhaPorcelanatoTamanho, rgMgAplicacao
        )
        isSyncing = false
    }

    // Sincroniza campos de texto com o estado do ViewModel
    private fun syncAllFieldsValues() = with(binding) {
        isSyncing = true
        fieldSynchronizer.syncAllFields(
            viewModel.inputs.value,
            etComp, etLarg, etAlt, etParedeQtd, etAbertura, etAreaInformada,
            etPecaComp, etPecaLarg, etPecaEsp, etJunta, etSobra, etPecasPorCaixa,
            etDesnivel, etRodapeAltura, etRodapeAbertura, etRodapeCompComercial,
            tilComp, tilLarg, tilAltura, tilParedeQtd, tilAbertura, tilAreaInformada,
            tilPecaComp, tilPecaLarg, tilPecaEsp, tilJunta, tilPecasPorCaixa,
            tilDesnivel, tilSobra, tilRodapeAltura, tilRodapeAbertura, tilRodapeCompComercial,
            rgPastilhaTamanho, rgPastilhaPorcelanatoTamanho, isMG()
        )
        normalizeAutoPredefinedFieldValues()
        isSyncing = false
    }

    // Normaliza exibição dos valores padrão auto-preenchidos
    private fun normalizeAutoPredefinedFieldValues() = with(binding) {
        val i = viewModel.inputs.value
        val espDisplay = when (i.revest) { // Intertravado, espessura armazenada mm; exibida em cm
            CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO ->
                i.pecaEspMm?.div(10.0) // mm → cm
            else ->
                i.pecaEspMm                 // demais revestimentos continuam em mm
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

    // Atualiza visibilidade de componentes conforme inputs
    private fun updateAllComponentsVisibility() = with(binding) {
        visibilityManager.updateAllVisibilities(
            viewModel.inputs.value,
            tvAreaTotalAviso, groupPlacaTipo, groupPecaTamanho, groupPastilhaTamanho,
            groupPastilhaPorcelanatoTamanho, groupRodapeFields, groupMgAplicacao,
            tilComp, tilLarg, tilAltura, tilParedeQtd, tilAbertura, tilAreaInformada,
            tilPecaComp, tilPecaLarg, tilPecaEsp, tilJunta, tilPecasPorCaixa,
            tilDesnivel, tilSobra, tilRodapeAltura, tilRodapeAbertura, tilRodapeCompComercial,
            etLarg, etAlt, etParedeQtd, etAbertura, etPecaEsp, etJunta, etPecasPorCaixa,
            etRodapeAbertura, rgPlacaTipo, switchRodape, rowPecasPorCaixaSwitch,
            groupPecasPorCaixaFields, rowDesnivelSwitchStep4, groupDesnivelFields
        )
    }

    /** ======================= HANDLERS DE ETAPAS ======================= */
    // Ajusta layout conforme etapa
    private fun handleAdjustUiForCurrentStep(step: Int) = with(binding) {
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

    // Inicializa ícones obrigatórios conforme etapa
    private fun handleInitRequiredIcons(step: Int) = with(binding) {
        when (step) {
            4 -> {
                iconManager.setRequiredIconVisible(etComp, true)
                iconManager.setRequiredIconVisible(etLarg, true)
                iconManager.setRequiredIconVisible(etAreaInformada, true)
                if (tilAltura.isVisible) iconManager.setRequiredIconVisible(etAlt, true)
                updateRequiredIconsForStep3Area()
            }
            // Etapa 5 = Parâmetros da Peça + Rodapé (mesma tela)
            5 -> {
                updateRequiredIconsForStep4PecaParameters() // peça
                updateRequiredIconsRodapeFields()           // rodapé (switch + campos)
            }
        }
    }

    // Configura botões conforme etapa
    private fun handleConfNavButtonsForStep(step: Int) = with(binding) {
        navigationHandler.handleStepNavigation(
            step = step, btnBack = btnBack, btnNext = btnNext, btnCalcular = btnCalcular,
            bottomBar = bottomBar, viewFlipper = viewFlipper,
            onSetupNovoCalculo = { handleConfNewCalculationButton() },
            onRestoreDefaultBack = { handleRestoreDefaultBackButton() }
        )
    }

    // Preenche resumo na etapa 6 (Tela de Revisão de Parâmetros com animação)
    private fun handleDisplayReviewParameters(step: Int) {
        if (step == 6) {
            val inputs = viewModel.inputs.value
            // Monta texto da revisão utilizando strings.xml
            binding.tvResumoRevisao.text =
                ReviewParametersFormatter.buildResumoRevisao(requireContext(), inputs)
            // Anima entrada: card → conteúdo → botão
            Animations.playReviewAnimation(
                card = binding.cardResumoRevisao,
                content = binding.tvResumoRevisao,
                button = binding.btnCalcular
            )
        }
    }

    // Configura botão "Novo Cálculo"
    private fun handleConfNewCalculationButton() = with(binding) {
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
                .setNegativeButton(getString(R.string.generic_cancel), null).show()
        }
    }

    // Restaura botão "Voltar" padrão
    private fun handleRestoreDefaultBackButton() = with(binding) {
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

    /** ======================= ATUALIZAÇÃO DE VIEWMODEL ======================= */
    // Atualiza parâmetros da peça no ViewModel
    private fun updatePecaParametersFields() = with(binding) {
        val pc = convertPecaDimensionToCm(getDoubleValue(etPecaComp))
        val pl = convertPecaDimensionToCm(getDoubleValue(etPecaLarg))
        val esp = when {
            isPastilha() -> null
            isIntertravado() -> getDoubleValue(etPecaEsp)?.times(10)
            else -> convertMetersToMillimeters(getDoubleValue(etPecaEsp))
        }
        val junta = getDoubleValue(etJunta)
        val sobra = getDoubleValue(etSobra)
        val ppc = etPecasPorCaixa.text?.toString()?.toIntOrNull()
        viewModel.setPecaParametros(pc, pl, esp, junta, sobra, ppc)
    }

    // Atualiza desnível no ViewModel
    private fun updateDesnivelField() = viewModel.setDesnivelCm(getDoubleValue(binding.etDesnivel))

    // Atualiza rodapé no ViewModel
    private fun updateRodapeFields() = with(binding) {
        val material = if (rgRodapeMat.checkedRadioButtonId == R.id.rbRodapeMesma)
            CalcRevestimentoViewModel.RodapeMaterial.MESMA_PECA
        else
            CalcRevestimentoViewModel.RodapeMaterial.PECA_PRONTA

        val compProntaM = if (material == CalcRevestimentoViewModel.RodapeMaterial.PECA_PRONTA)
            getDoubleValue(etRodapeCompComercial)?.div(100.0)
        else
            null
        val aberturaM = getDoubleValue(etRodapeAbertura)?.coerceAtLeast(0.0) ?: 0.0
        viewModel.setRodape(
            enable = switchRodape.isChecked,
            alturaCm = convertMetersToCm(getDoubleValue(etRodapeAltura)), perimetroManualM = null,
            descontarVaoM = aberturaM, perimetroAuto = true, material = material,
            orientacaoMaior = true, compComercialM = compProntaM
        )
    }

    /** ======================= ÍCONES OBRIGATÓRIOS ======================= */
    // Atualiza ícones obrigatórios da Etapa 3 (Medidas da Área)
    private fun updateRequiredIconsForStep3Area() = with(binding) {
        iconManager.updateStep4IconsAreaDimensions(
            etComp, etLarg, etAlt, etParedeQtd, etAbertura, etAreaInformada,
            tilAltura.isVisible, tilParedeQtd.isVisible
        )
    }

    // Atualiza ícones obrigatórios da Etapa 4 (Medidas do Revestimento)
    private fun updateRequiredIconsForStep4PecaParameters() = with(binding) {
        iconManager.updateStep5IconsPecaDimensions(
            etPecaComp, etPecaLarg, etJunta, etPecaEsp, etPecasPorCaixa, etSobra,
            etDesnivel, viewModel.inputs.value.revest, groupPecaTamanho.isVisible
        )
    }

    // Atualiza ícones obrigatórios do Rodapé (embutido na tela da Peça)
    private fun updateRequiredIconsRodapeFields() = with(binding) {
        val inputs = viewModel.inputs.value
        // Verifica cenário atual possui rodapé
        val hasRodapeStep = RevestimentoSpecifications.hasRodapeStep(inputs)
        // Switch ligado e cenário com rodapé habilitado
        val rodapeOn = hasRodapeStep && switchRodape.isChecked
        val isPecaPronta = rgRodapeMat.checkedRadioButtonId == R.id.rbRodapePeca
        iconManager.updateStepRodapeIconFields(
            etRodapeAltura, etRodapeCompComercial, hasRodapeStep, rodapeOn,
            isPecaPronta, tilRodapeCompComercial.isVisible
        )
    }

    /** ======================= VALIDAÇÃO E HABILITAÇÃO ======================= */
    // Revalida todas as dimensões (Comp/Larg/Alt/Parede/Abertura/Area Total)
    private fun validateAllAreaDimensions() = with(binding) {
        validator.validateDimLive(
            etComp, tilComp, CalcRevestimentoRules.Medidas.COMP_LARG_RANGE_M,
            getString(R.string.calc_err_medida_comp_larg_m), false
        )
        validator.validateDimLive(
            etLarg, tilLarg, CalcRevestimentoRules.Medidas.COMP_LARG_RANGE_M,
            getString(R.string.calc_err_medida_comp_larg_m), false
        )
        if (tilAltura.isVisible) {
            validator.validateDimLive(
                etAlt, tilAltura, CalcRevestimentoRules.Medidas.ALTURA_RANGE_M,
                getString(R.string.calc_err_medida_alt_m), false
            )
        }
    }

    // Recalcula habilitação do botão "Avançar"
    private fun refreshNextButtonEnabled() = with(binding) {
        val step = viewModel.step.value
        val validation = viewModel.validateStep(step)
        val inputs = viewModel.inputs.value
        var enabled = validation.isValid
        // ----- Etapa 1 (Tipo de Revestimento + Tipo de Placa / Aplicação MG) -----
        if (step == 1 && enabled) {
            val revest = inputs.revest
            val precisaTipoPlaca =
                revest == CalcRevestimentoViewModel.RevestimentoType.PISO ||
                        revest == CalcRevestimentoViewModel.RevestimentoType.AZULEJO ||
                        revest == CalcRevestimentoViewModel.RevestimentoType.PASTILHA
            val isMg =
                revest == CalcRevestimentoViewModel.RevestimentoType.MARMORE ||
                        revest == CalcRevestimentoViewModel.RevestimentoType.GRANITO

            if (precisaTipoPlaca) {
                enabled = inputs.pisoPlacaTipo != null
            } else if (isMg) { // MG precisa escolher Piso/Parede
                enabled = inputs.aplicacao != null
            }
        }
        // ----- Etapa 4 (Medidas da Área) -----
        if (step >= 4) {
            val areaTxt = etAreaInformada.text
            val hasAreaTotalPreenchida = !areaTxt.isNullOrBlank()

            enabled = if (hasAreaTotalPreenchida) { // Área Total preenchida → Valor prevalece
                !validator.hasAreaTotalErrorNow(etAreaInformada)
            } else if (enabled) { // Sem Área Total → complementa o que o ViewModel já validou
                !validator.hasAreaTotalErrorNow(etAreaInformada) &&
                        tilParedeQtd.error.isNullOrEmpty() &&
                        tilAbertura.error.isNullOrEmpty()
            } else {
                false // validateStep já reprovou por outro motivo (ex.: dimensões ruins)
            }
        }
        // ----- Etapa 5 (Medidas do Revestimento) -----
        if (enabled && step >= 5) {
            val isPedra = inputs.revest == CalcRevestimentoViewModel.RevestimentoType.PEDRA
            val isMg = isMG()
            enabled = when {
                isPedra -> { // Regra Pedra Portuguesa: considerar apenas desnível + sobra técnica
                    !validator.hasDesnivelErrorNow(
                        etDesnivel, tilDesnivel.isVisible, getDoubleValue(etDesnivel)
                    ) && tilSobra.error.isNullOrEmpty()
                }

                else -> {
                    val compCm = convertPecaDimensionToCm(getDoubleValue(etPecaComp))
                    val largCm = convertPecaDimensionToCm(getDoubleValue(etPecaLarg))
                    val compInRange = when {
                        isMg -> compCm != null && compCm in CalcRevestimentoRules.Peca.MG_RANGE_CM
                        else -> compCm != null && compCm in CalcRevestimentoRules.Peca.GENERIC_RANGE_CM
                    }
                    val largInRange = when {
                        isMg -> largCm != null && largCm in CalcRevestimentoRules.Peca.MG_RANGE_CM
                        else -> largCm != null && largCm in CalcRevestimentoRules.Peca.GENERIC_RANGE_CM
                    }
                    // Switch "Peças por caixa": só exige valor quando estiver ligado
                    val pecasPorCaixaOk =
                        if (switchPecasPorCaixa.isVisible && switchPecasPorCaixa.isChecked) {
                            !validator.hasPecasPorCaixaErrorNow(etPecasPorCaixa)
                        } else {
                            true
                        }
                    // Desnível (Tratamento diferente para Pedra e MG)
                    val desnivelVisivelAgora = when {
                        inputs.revest == CalcRevestimentoViewModel.RevestimentoType.MARMORE ||
                                inputs.revest == CalcRevestimentoViewModel.RevestimentoType.GRANITO ->
                            switchDesnivel.isVisible && switchDesnivel.isChecked

                        else -> tilDesnivel.isVisible
                    }
                    val desnivelOk = !validator.hasDesnivelErrorNow(
                        etDesnivel, desnivelVisivelAgora, getDoubleValue(etDesnivel)
                    )
                    !validator.hasJuntaErrorNow(etJunta, getFieldJuntaValueInMillimeters()) &&
                            !validator.hasEspessuraErrorNow(
                                etPecaEsp, isPastilha(),
                                when {
                                    isPastilha() -> null
                                    isIntertravado() -> getDoubleValue(etPecaEsp)?.times(10)
                                    else -> convertMetersToMillimeters(getDoubleValue(etPecaEsp))
                                }
                            ) &&
                            pecasPorCaixaOk && desnivelOk && tilSobra.error.isNullOrEmpty() &&
                            compInRange && largInRange && tilPecaComp.error.isNullOrEmpty() &&
                            tilPecaLarg.error.isNullOrEmpty()
                }
            }
        }
        // ----- Etapa Rodapé dentro de Medidas do Revestimento -----
        if (enabled && step >= 5) {
            var rodapeOk = tilRodapeAbertura.error.isNullOrEmpty()
            if (inputs.rodapeEnable) {
                val alturaCm = convertMetersToCm(getDoubleValue(etRodapeAltura))
                val alturaOk =
                    alturaCm != null &&
                            alturaCm in CalcRevestimentoRules.Rodape.ALTURA_RANGE_CM &&
                            tilRodapeAltura.error.isNullOrEmpty()
                rodapeOk = rodapeOk && alturaOk

                if (inputs.rodapeMaterial == CalcRevestimentoViewModel.RodapeMaterial.PECA_PRONTA) {
                    val compCm = getDoubleValue(etRodapeCompComercial)
                    val compOk =
                        compCm != null &&
                                compCm in CalcRevestimentoRules.Rodape.COMP_COMERCIAL_RANGE_CM &&
                                tilRodapeCompComercial.error.isNullOrEmpty()
                    rodapeOk = rodapeOk && compOk
                }
            }
            enabled = rodapeOk
        }
        btnNext.isEnabled = enabled
        // ----- Ajuste extra para dimensões de Mármore/Granito -----
        if ((inputs.revest == CalcRevestimentoViewModel.RevestimentoType.MARMORE ||
                    inputs.revest == CalcRevestimentoViewModel.RevestimentoType.GRANITO) &&
            step >= 5
        ) {
            val compValid = convertPecaDimensionToCm(getDoubleValue(etPecaComp))
                ?.let { it in CalcRevestimentoRules.Peca.MG_RANGE_CM } == true
            val largValid = convertPecaDimensionToCm(getDoubleValue(etPecaLarg))
                ?.let { it in CalcRevestimentoRules.Peca.MG_RANGE_CM } == true
            btnNext.isEnabled = btnNext.isEnabled && compValid && largValid
        }
    }

    /** ======================= RESULTADO ======================= */
    // Exibe resultado do cálculo
    private fun displayCalculationResult(r: CalcRevestimentoViewModel.Resultado) = with(binding) {
        tableContainer.removeAllViews()
        tableContainer.addView(tableBuilder.makeHeaderRow())
        r.itens.forEach { tableContainer.addView(tableBuilder.makeDataRow(it)) }
        tableContainer.post { applyTableResponsiveness() } // Aplicar responsividade após renderização
    }

    // Aplica responsividade aos elementos da tabela
    private fun applyTableResponsiveness() = with(binding) {
        val responsiveHelper = TableResponsiveHelper(requireContext())
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
        cardInformation.apply { // Card de informação
            radius = responsiveHelper.infoCardCornerRadius
            setPadding(
                responsiveHelper.infoCardPadding, responsiveHelper.infoCardPadding,
                responsiveHelper.infoCardPadding, responsiveHelper.infoCardPadding
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

    /** ======================= EXPORTAÇÃO DE PDF ======================= */
    private fun exportResultAsPdf(share: Boolean) {
        val ui = (viewModel.resultado.value as? UiState.Success)?.data ?: run {
            showToast(getString(R.string.calc_export_no_data))
            return
        }
        lifecycleScope.launch {
            binding.loadingOverlay.isVisible = true
            try {
                val (bytes, fileName) = withContext(Dispatchers.IO) {
                    val pdfBytes = pdfGenerator.generate(
                        ui.resultado, viewModel.inputs.value.pecaEspMm,
                        viewModel.inputs.value.pecasPorCaixa, viewModel.inputs.value.desnivelCm
                    )
                    val name = "Calculo_Revestimento_${System.currentTimeMillis()}.pdf"
                    Pair(pdfBytes, name)
                }
                if (share) {
                    sharePdfFile(bytes, fileName)
                } else {
                    savePdfFile(bytes, fileName)
                }
            } catch (e: Exception) {
                showToast("Erro ao gerar PDF: ${e.message}")
            } finally {
                binding.loadingOverlay.isVisible = false
            }
        }
    }

    private fun sharePdfFile(bytes: ByteArray, fileName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val cacheFile = File(requireContext().cacheDir, fileName)
            FileOutputStream(cacheFile).use { it.write(bytes) }

            withContext(Dispatchers.Main) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider", cacheFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newUri(requireContext().contentResolver, "PDF", uri)
                }
                val chooser = Intent.createChooser(
                    shareIntent, getString(R.string.export_share_chooser_calc)
                ).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                pendingShareConfirm = true
                startActivity(chooser)
            }
        }
    }

    private suspend fun savePdfFile(bytes: ByteArray, fileName: String) {
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
        if (saved == null) showToast(getString(R.string.resumo_export_error_save))
        else showToast(getString(R.string.resumo_export_saved_ok))
    }

    /** ======================= MAPPERS (RadioId → Enum) ======================= */
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

    private fun mapRadioIdToPastilhaFormato(id: Int) = when (id) {
        R.id.rbPastilha5 -> RevestimentoSpecifications.PastilhaFormato.P5
        R.id.rbPastilha7_5 -> RevestimentoSpecifications.PastilhaFormato.P7_5
        R.id.rbPastilha10 -> RevestimentoSpecifications.PastilhaFormato.P10
        R.id.rbPastilhaP1_5 -> RevestimentoSpecifications.PastilhaFormato.P1_5
        R.id.rbPastilhaP2 -> RevestimentoSpecifications.PastilhaFormato.P2
        R.id.rbPastilhaP2_2 -> RevestimentoSpecifications.PastilhaFormato.P2_2
        R.id.rbPastilhaP2_5 -> RevestimentoSpecifications.PastilhaFormato.P2_5
        R.id.rbPastilhaP5_5 -> RevestimentoSpecifications.PastilhaFormato.P5_5
        R.id.rbPastilhaP5_10 -> RevestimentoSpecifications.PastilhaFormato.P5_10
        R.id.rbPastilhaP5_15 -> RevestimentoSpecifications.PastilhaFormato.P5_15
        R.id.rbPastilhaP7_5p -> RevestimentoSpecifications.PastilhaFormato.P7_5P
        R.id.rbPastilhaP10p -> RevestimentoSpecifications.PastilhaFormato.P10P

        else -> null
    }

    /** ======================= HELPERS E UTILITÁRIOS ======================= */
    private fun isMG() = viewModel.inputs.value.revest in setOf(
        CalcRevestimentoViewModel.RevestimentoType.MARMORE,
        CalcRevestimentoViewModel.RevestimentoType.GRANITO
    )

    private fun isPastilha() =
        viewModel.inputs.value.revest == CalcRevestimentoViewModel.RevestimentoType.PASTILHA

    private fun isIntertravado() =
        viewModel.inputs.value.revest == CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO

    private fun convertPecaDimensionToCm(v: Double?) = UnitConverter.parsePecaToCm(v, isMG())
    private fun convertMetersToCm(v: Double?) = UnitConverter.mToCmIfLooksLikeMeters(v)
    private fun convertMetersToMillimeters(v: Double?) = UnitConverter.mToMmIfLooksLikeMeters(v)

    private fun getDoubleValue(et: TextInputEditText) =
        et.text?.toString()?.replace(",", ".")?.toDoubleOrNull()

    private fun getFieldJuntaValueInMillimeters() = getDoubleValue(binding.etJunta)

    private fun smoothScrollToggle(isChecked: Boolean) {
        binding.scrollContent.post {
            val targetY =
                if (isChecked) binding.scrollContent.getChildAt(0)?.bottom ?: 0 else 0
            Animations.smoothScrollToY(binding.scrollContent, targetY)
        }
    }

    private fun showToast(msg: String) =
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

    // Validação adiada no 1º dígito
    private fun applyDeferredValidationPattern(
        et: TextInputEditText, til: TextInputLayout, text: String, onValidateNow: () -> Unit,
        onAfterDeferredValidation: (() -> Unit)? = null,
        onFirstDigitBeforeSchedule: (() -> Unit)? = null
    ) {
        when {
            text.isEmpty() -> { // Campo vazio → volta para o comportamento normal (sem delay)
                deferredValidator.cancel(et)
                onValidateNow()
            }

            text.length == 1 -> { // 1º dígito → some com erro e agenda validação visual para depois
                deferredValidator.cancel(et)
                validator.setInlineError(et, til, null)
                onFirstDigitBeforeSchedule?.invoke()
                deferredValidator.schedule(et) {
                    onValidateNow()
                    onAfterDeferredValidation?.invoke()
                }
            }

            else -> { // 2+ dígitos → validação imediata
                deferredValidator.cancel(et)
                onValidateNow()
                onAfterDeferredValidation?.invoke()
            }
        }
    }

    // Garante que o topo da tela está visível sem flicker
    private fun ensureScrollAtTopWithoutFlicker(step: Int) {
        val sv = binding.scrollContent
        val root = binding.rootCalc
        // Calcula se deve ou não "roubar" o foco nessa troca de etapa
        val shouldHijackFocus = !skipFocusHijackOnce && step != 0
        Animations.ensureScrollAtTopWithoutFlicker(
            scrollView = sv, rootView = root, expectedStep = step,
            shouldHijackFocus = shouldHijackFocus, currentStepProvider = { viewModel.step.value }
        )
        skipFocusHijackOnce = false
    }
}