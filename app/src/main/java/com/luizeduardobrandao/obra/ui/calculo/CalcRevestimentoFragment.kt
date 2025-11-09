package com.luizeduardobrandao.obra.ui.calculo

import android.content.ClipData
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.size
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentCalcRevestimentoBinding
import com.luizeduardobrandao.obra.ui.calculo.ui.MaterialTableBuilder
import com.luizeduardobrandao.obra.ui.calculo.ui.RequiredIconManager
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
import androidx.core.view.get

@AndroidEntryPoint
class CalcRevestimentoFragment : Fragment() {

    private var _binding: FragmentCalcRevestimentoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CalcRevestimentoViewModel by viewModels()

    // Helpers
    private lateinit var validator: FieldValidator
    private lateinit var iconManager: RequiredIconManager
    private lateinit var tableBuilder: MaterialTableBuilder
    private lateinit var pdfGenerator: PdfGenerator

    // Controle de UX pós-share
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

        // Inicializar helpers
        validator = FieldValidator(viewModel)
        iconManager = RequiredIconManager(requireContext())
        tableBuilder = MaterialTableBuilder(requireContext(), layoutInflater)
        pdfGenerator = PdfGenerator(requireContext()) { tableBuilder.buildComprarCell(it) }

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
     * SETUP INICIAL
     * ═══════════════════════════════════════════════════════════════════════════ */

    // Configura a toolbar com menu e navegação
    private fun setupToolbar() = with(binding.toolbar) {
        setNavigationOnClickListener { findNavController().navigateUp() }
        inflateMenu(R.menu.menu_calc_revestimento)
        setTitleTextColor(ContextCompat.getColor(context, R.color.white))

        for (i in 0 until menu.size) {
            menu[i].icon?.setTint(ContextCompat.getColor(context, R.color.white))
        }

        setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_export_calc -> {
                    showExportMenu(this); true
                }

                else -> false
            }
        }
    }

    // Mostra menu popup de exportação
    private fun showExportMenu(anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchor, Gravity.END)
        popup.menu.add(0, 1, 0, getString(R.string.export_share))
            .setIcon(R.drawable.ic_export_share)
        popup.menu.add(0, 2, 1, getString(R.string.export_download))
            .setIcon(R.drawable.ic_download2)

        try {
            popup.setForceShowIcon(true)
        } catch (_: Throwable) {
        }

        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> {
                    popup.dismiss(); export(share = true)
                }

                2 -> {
                    popup.dismiss(); export(share = false)
                }
            }
            true
        }
        popup.show()
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * SETUP DA UI
     * ═══════════════════════════════════════════════════════════════════════════ */

    private fun setupUi() = with(binding) {
        // Habilita slots de erro em todos os campos com TIL
        listOf(
            tilComp, tilLarg, tilAltura, tilAreaInformada,
            tilPecaComp, tilPecaLarg, tilPecaEsp, tilJunta,
            tilPecasPorCaixa, tilDesnivel, tilSobra,
            tilRodapeAltura, tilRodapeCompComercial
        ).forEach { it.isErrorEnabled = true }

        setupNavigationButtons()
        setupStep1Listeners()  // Tipo de revestimento
        setupStep2Listeners()  // Ambiente
        setupStep3TrafegoListeners() // Tipo de tráfego (Intertravado)
        setupStep3Listeners()  // Medidas
        setupStep4Listeners()  // Peça
        setupStep5Listeners()  // Rodapé
        setupStep6Listeners()  // Impermeabilização
        setupStep7Listeners()  // Revisão e cálculo
    }

    // Botões de navegação entre etapas
    private fun setupNavigationButtons() = with(binding) {
        btnNext.setOnClickListener {
            val step = viewModel.step.value
            val ok = viewModel.isStepValid(step) &&
                    !validator.hasJuntaErrorNow(etJunta, juntaValueMm(), juntaRange()) &&
                    !validator.hasEspessuraErrorNow(
                        etPecaEsp,
                        isPastilha(),
                        mToMmIfLooksLikeMeters(getD(etPecaEsp))
                    )

            if (ok) {
                rootCalc.post { viewModel.nextStep() }
            } else {
                toast(getString(R.string.calc_validate_step))
            }
        }

        btnBack.setOnClickListener {
            rootCalc.post { viewModel.prevStep() }
        }

        btnStart.setOnClickListener {
            rootCalc.post { viewModel.nextStep() }
        }

        btnCancel.setOnClickListener { findNavController().navigateUp() }
    }

    // Etapa 1: Tipo de revestimento
    private fun setupStep1Listeners() = with(binding) {
        rgRevest.setOnCheckedChangeListener { _, id ->
            if (isSyncing) return@setOnCheckedChangeListener
            val type = when (id) {
                R.id.rbPiso -> CalcRevestimentoViewModel.RevestimentoType.PISO
                R.id.rbAzulejo -> CalcRevestimentoViewModel.RevestimentoType.AZULEJO
                R.id.rbPastilha -> CalcRevestimentoViewModel.RevestimentoType.PASTILHA
                R.id.rbPedra -> CalcRevestimentoViewModel.RevestimentoType.PEDRA
                R.id.rbPisoIntertravado -> CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO
                R.id.rbMarmore -> CalcRevestimentoViewModel.RevestimentoType.MARMORE
                R.id.rbGranito -> CalcRevestimentoViewModel.RevestimentoType.GRANITO
                else -> null
            }
            type?.let { viewModel.setRevestimento(it) }
            groupPlacaTipo.isVisible = (type == CalcRevestimentoViewModel.RevestimentoType.PISO)
        }

        rgPlacaTipo.setOnCheckedChangeListener { _, id ->
            if (isSyncing) return@setOnCheckedChangeListener
            val placa = when (id) {
                R.id.rbCeramica -> CalcRevestimentoViewModel.PlacaTipo.CERAMICA
                R.id.rbPorcelanato -> CalcRevestimentoViewModel.PlacaTipo.PORCELANATO
                else -> null
            }
            viewModel.setPlacaTipo(placa)
        }
    }

    // Etapa 2: Ambiente
    private fun setupStep2Listeners() = with(binding) {
        rgAmbiente.setOnCheckedChangeListener { _, id ->
            if (isSyncing) return@setOnCheckedChangeListener
            val amb = when (id) {
                R.id.rbSeco -> CalcRevestimentoViewModel.AmbienteType.SECO
                R.id.rbSemi -> CalcRevestimentoViewModel.AmbienteType.SEMI
                R.id.rbMolhado -> CalcRevestimentoViewModel.AmbienteType.MOLHADO
                R.id.rbSempre -> CalcRevestimentoViewModel.AmbienteType.SEMPRE
                else -> null
            }
            amb?.let { viewModel.setAmbiente(it) }
        }
    }

    // Etapa 2.1 Tipo de Tráfego (apenas Piso Intertravado)
    private fun setupStep3TrafegoListeners() = with(binding) {
        rgTrafego.setOnCheckedChangeListener { _, id ->
            if (isSyncing) return@setOnCheckedChangeListener
            val trafego = when (id) {
                R.id.rbTrafegoLeve -> CalcRevestimentoViewModel.TrafegoType.LEVE
                R.id.rbTrafegoMedio -> CalcRevestimentoViewModel.TrafegoType.MEDIO
                R.id.rbTrafegoPesado -> CalcRevestimentoViewModel.TrafegoType.PESADO
                else -> null
            }
            viewModel.setTrafego(trafego)
            refreshNextEnabled()
        }
    }

    // Etapa 3: Medidas do ambiente
    private fun setupStep3Listeners() = with(binding) {
        // Comprimento
        setupMedidaField(etComp, tilComp, 0.01..10000.0, R.string.calc_err_medida_comp_larg_m)

        // Largura
        setupMedidaField(etLarg, tilLarg, 0.01..10000.0, R.string.calc_err_medida_comp_larg_m)

        // Altura
        setupMedidaField(etAlt, tilAltura, 0.01..100.0, R.string.calc_err_medida_alt_m)

        // Área total
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

    // Configura campo de medida (C/L/A)
    private fun setupMedidaField(
        et: TextInputEditText,
        til: TextInputLayout,
        range: ClosedRange<Double>,
        errorMsgRes: Int
    ) {
        validator.validateRangeOnBlur(
            et, til,
            { getD(et) },
            range,
            getString(errorMsgRes)
        )

        et.doAfterTextChanged {
            viewModel.setMedidas(
                getD(binding.etComp),
                getD(binding.etLarg),
                getD(binding.etAlt),
                getD(binding.etAreaInformada)
            )

            if (til.isVisible) {
                validator.validateDimLive(
                    et, til, range,
                    getString(errorMsgRes),
                    validator.isAreaTotalValidNow(binding.etAreaInformada)
                )
            } else {
                validator.setInlineError(et, til, null)
            }

            updateRequiredIconsStep3()
        }
    }

    // Etapa 4: Parâmetros da peça
    private fun setupStep4Listeners() = with(binding) {
        // Comprimento da peça
        setupPecaField(etPecaComp, tilPecaComp)
        // Largura da peça
        setupPecaField(etPecaLarg, tilPecaLarg)
        // Espessura
        setupEspessuraField()
        // Junta
        setupJuntaField()
        // Peças por caixa
        setupPecasPorCaixaField()
        // Desnível
        setupDesnivelField()
        // Sobra
        setupSobraField()
    }

    // Configura campo de peça (comp/larg)
    private fun setupPecaField(et: TextInputEditText, til: TextInputLayout) {
        et.doAfterTextChanged {
            updatePecaParametros()

            val v = parsePecaToCm(getD(et))
            val inRange = when {
                isMG() -> (v != null && v in 10.0..2000.0) // 0,1 m .. 20,0 m em cm
                isPastilha() -> (v != null && v in 20.0..40.0)
                else -> (v != null && v in 5.0..200.0)
            }

            val msg = when {
                et.text.isNullOrBlank() -> null
                inRange -> null
                isMG() -> getString(R.string.calc_piece_err_mg)
                isPastilha() -> getString(R.string.calc_piece_err_pastilha_range)
                else -> getString(R.string.calc_piece_err_cm)
            }

            validator.setInlineError(et, til, msg)
            updateRequiredIconsStep4()
        }

        validator.validatePecaOnBlur(
            et, til, isMG(), isPastilha(),
            { parsePecaToCm(it) },
            getString(R.string.calc_piece_err_mg),
            getString(R.string.calc_piece_err_pastilha_range),
            getString(R.string.calc_piece_err_cm)
        )
    }

    // Configura campo de espessura da peça
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

            val (valorMm, minMm, maxMm, errMsg) =
                if (isIntertravado()) {
                    val cm = getD(etPecaEsp)
                    val mm = cm?.times(10) // cm → mm
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

        // validação "on blur"
        if (isIntertravado()) {
            validator.validateRangeOnBlur(
                etPecaEsp, tilPecaEsp,
                { getD(etPecaEsp)?.times(10) }, // cm → mm
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

    // helper local para tupla simples
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    // Configura campo de junta
    private fun setupJuntaField() = with(binding) {
        etJunta.doAfterTextChanged {
            updatePecaParametros()

            val hasErr = validator.hasJuntaErrorNow(etJunta, juntaValueMm(), juntaRange())
            validator.setInlineError(etJunta, tilJunta, if (hasErr) juntaErrorMsg() else null)

            updateRequiredIconsStep4()
            refreshNextEnabled()
        }

        validator.validateRangeOnBlur(
            etJunta, tilJunta,
            { juntaValueMm() },
            juntaRange(),
            juntaErrorMsg()
        )
    }

    // Configura campo de peças por caixa
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

    // Configura o campo de desnível
    private fun setupDesnivelField() = with(binding) {
        etDesnivel.doAfterTextChanged {
            updateDesnivelViewModel()

            // Validação inline dinâmica por tipo de revestimento
            val txtEmpty = etDesnivel.text.isNullOrBlank()
            val v = getD(etDesnivel)
            val (range, errRes) = desnivelRangeAndError()
            val ok = txtEmpty || (v != null && v in range)

            validator.setInlineError(etDesnivel, tilDesnivel, if (ok) null else getString(errRes))
            refreshNextEnabled()
        }

        // Validação "no blur" no mesmo padrão dos demais campos
        etDesnivel.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            val txtEmpty = etDesnivel.text.isNullOrBlank()
            val v = getD(etDesnivel)
            val (range, errRes) = desnivelRangeAndError()
            val ok = txtEmpty || (v != null && v in range)
            validator.setInlineError(etDesnivel, tilDesnivel, if (ok) null else getString(errRes))
        }
    }

    private fun updateDesnivelViewModel() = with(binding) {
        viewModel.setDesnivelCm(getD(etDesnivel)) // valor já em cm
    }

    // Faixas e mensagens dinâmicas por revestimento
    private fun desnivelRangeAndError(): Pair<ClosedRange<Double>, Int> {
        return when (viewModel.inputs.value.revest) {
            CalcRevestimentoViewModel.RevestimentoType.PEDRA ->
                (4.0..8.0) to R.string.calc_err_desnivel_pedra_range

            CalcRevestimentoViewModel.RevestimentoType.MARMORE,
            CalcRevestimentoViewModel.RevestimentoType.GRANITO ->
                (0.0..3.0) to R.string.calc_err_desnivel_mg_range

            else -> (Double.NEGATIVE_INFINITY..Double.POSITIVE_INFINITY) to R.string.calc_err_generic
        }
    }

    // Configura campo de sobra
    private fun setupSobraField() = with(binding) {
        etSobra.doAfterTextChanged {
            updatePecaParametros()

            val s = getD(etSobra)
            val msg = when {
                etSobra.text.isNullOrBlank() -> null
                s == null || s < 0.0 || s > 50.0 ->
                    getString(R.string.calc_err_sobra_range)

                else -> null
            }

            tilSobra.error = msg

            if (viewModel.step.value in 1..7) {
                refreshNextEnabled()
            }
            updateRequiredIconsStep4()
        }

        etSobra.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener

            // Se usuário deixar em branco, aplicar automaticamente 10%
            if (etSobra.text.isNullOrBlank()) {
                etSobra.setText(getString(R.string.valor_sobra_minima))
                updatePecaParametros()
                tilSobra.error = null
                if (viewModel.step.value in 1..7) {
                    refreshNextEnabled()
                }
                return@setOnFocusChangeListener
            }

            val s = getD(etSobra)
            val msg = when {
                s == null || s < 0.0 || s > 50.0 ->
                    getString(R.string.calc_err_sobra_range)

                else -> null
            }

            validator.setInlineError(etSobra, tilSobra, msg)
        }
    }

    // Etapa 5: Rodapé
    private fun setupStep5Listeners() = with(binding) {
        switchRodape.setOnCheckedChangeListener { _, isChecked ->
            groupRodapeFields.isVisible = isChecked
            updateRodapeViewModel()
            updateRequiredIconsStep5()
            if (viewModel.step.value in 1..7) {
                refreshNextEnabled()
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

            updateRodapeViewModel()
            updateRequiredIconsStep5()

            if (viewModel.step.value in 1..7) {
                refreshNextEnabled()
            }
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

        etRodapeCompComercial.doAfterTextChanged {
            updateRodapeViewModel()
            updateRequiredIconsStep5()

            val isPecaProntaSelecionada =
                switchRodape.isChecked &&
                        rgRodapeMat.checkedRadioButtonId == R.id.rbRodapePeca &&
                        tilRodapeCompComercial.isVisible

            val compCm = getD(etRodapeCompComercial)

            val msg = when {
                // Enquanto vazio, não mostra erro visual (só bloqueia o Avançar via refreshNextEnabled)
                !isPecaProntaSelecionada || etRodapeCompComercial.text.isNullOrBlank() -> null
                compCm == null || compCm !in 5.0..300.0 ->
                    getString(R.string.calc_err_rodape_comp_cm_range)

                else -> null
            }

            validator.setInlineError(etRodapeCompComercial, tilRodapeCompComercial, msg)

            if (viewModel.step.value in 1..7) {
                refreshNextEnabled()
            }
        }
        validator.validateRangeOnBlur(
            etRodapeCompComercial,
            tilRodapeCompComercial,
            { getD(etRodapeCompComercial) },
            5.0..300.0,
            getString(R.string.calc_err_rodape_comp_cm_range)
        )
    }

    // Etapa 6: Impermeabilização
    private fun setupStep6Listeners() = with(binding) {
        switchImp.setOnCheckedChangeListener { _, on ->
            viewModel.setImpermeabilizacao(on)
            refreshNextEnabled()
        }
        rgIntertravadoImp.setOnCheckedChangeListener { _, id ->
            if (isSyncing) return@setOnCheckedChangeListener
            val tipo = when (id) {
                R.id.rbImpMantaGeotextil ->
                    CalcRevestimentoViewModel.ImpIntertravadoTipo.MANTA_GEOTEXTIL

                R.id.rbImpAditivoSika1 ->
                    CalcRevestimentoViewModel.ImpIntertravadoTipo.ADITIVO_SIKA1

                else -> null
            }
            tipo?.let { viewModel.setIntertravadoImpTipo(it) }
            refreshNextEnabled()
        }
    }

    // Etapa 7: Revisão e cálculo
    private fun setupStep7Listeners() = with(binding) {
        btnCalcular.setOnClickListener { viewModel.calcular() }
        btnVoltarResultado.setOnClickListener { viewModel.goTo(8) }
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * OBSERVERS
     * ═══════════════════════════════════════════════════════════════════════════ */

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeStep() }
                launch { observeInputs() }
                launch { observeResultado() }
            }
        }
    }

    // Observa mudanças na etapa atual
    private suspend fun observeStep() {
        viewModel.step.collect { step ->
            binding.viewFlipper.displayedChild = step

            // garante que, assim que a tela aparece, radios reflitam inputs atuais
            syncRadioGroups(viewModel.inputs.value)

            handleStep0Layout(step)
            handleStepIcons(step)
            handleStepButtons(step)
            handleStep7Resume(step)

            ensureTopNoFlicker(step)
            refreshNextEnabled()
        }
    }

    // Observa mudanças nos inputs
    private suspend fun observeInputs() {
        viewModel.inputs.collect { i ->
            syncRadioGroups(i)
            syncFieldValues(i)
            updateUIVisibility(i)
            updateHelperTexts(i)
            updateJuntaHelper(i)

            if (viewModel.step.value in 1..7) refreshNextEnabled()
        }
    }

    // Observa resultado do cálculo
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
     * HELPERS DE UI
     * ═══════════════════════════════════════════════════════════════════════════ */

    // Atualiza parâmetros da peça no ViewModel
    private fun updatePecaParametros() = with(binding) {
        val pc = parsePecaToCm(getD(etPecaComp))
        val pl = parsePecaToCm(getD(etPecaLarg))
        val esp = when {
            isPastilha() -> null
            isIntertravado() -> getD(etPecaEsp)?.times(10) // cm → mm
            else -> mToMmIfLooksLikeMeters(getD(etPecaEsp))
        }
        val junta = mToMmIfLooksLikeMeters(getD(etJunta))
        val sobra = getD(etSobra)
        val ppc = etPecasPorCaixa.text?.toString()?.toIntOrNull()

        viewModel.setPecaParametros(pc, pl, esp, junta, sobra, ppc)
    }

    private fun isIntertravado() =
        viewModel.inputs.value.revest == CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO

    // Atualiza rodapé no ViewModel
    private fun updateRodapeViewModel() = with(binding) {
        val material = if (rgRodapeMat.checkedRadioButtonId == R.id.rbRodapeMesma)
            CalcRevestimentoViewModel.RodapeMaterial.MESMA_PECA
        else
            CalcRevestimentoViewModel.RodapeMaterial.PECA_PRONTA

        val compProntaM =
            if (material == CalcRevestimentoViewModel.RodapeMaterial.PECA_PRONTA)
                getD(etRodapeCompComercial)?.div(100.0) // campo agora em cm → ViewModel continua recebendo em m
            else
                null

        viewModel.setRodape(
            enable = switchRodape.isChecked,
            alturaCm = mToCmIfLooksLikeMeters(getD(etRodapeAltura)),
            perimetroManualM = null,
            descontarVaoM = 0.0,
            perimetroAuto = true,
            material = material,
            orientacaoMaior = true,
            compComercialM = compProntaM
        )
    }

    // Atualiza ícones obrigatórios conforme etapa
    private fun updateRequiredIconsStep3() = with(binding) {
        iconManager.updateStep3Icons(etComp, etLarg, etAlt, etAreaInformada, tilAltura.isVisible)
    }

    private fun updateRequiredIconsStep4() = with(binding) {
        iconManager.updateStep4Icons(
            etPecaComp, etPecaLarg, etJunta, etPecaEsp, etPecasPorCaixa, etSobra,
            viewModel.inputs.value.revest,
            groupPecaTamanho.isVisible
        )
    }

    private fun updateRequiredIconsStep5() = with(binding) {
        iconManager.updateStep5Icons(
            etRodapeAltura, etRodapeCompComercial,
            switchRodape.isChecked,
            rgRodapeMat.checkedRadioButtonId == R.id.rbRodapePeca,
            tilRodapeCompComercial.isVisible
        )
    }

    // Revalida todas as dimensões
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

    // Recalcula habilitação do botão "Avançar"
    private fun refreshNextEnabled() = with(binding) {
        val step = viewModel.step.value
        val validation = viewModel.validateStep(step)

        btnNext.isEnabled = validation.isValid &&
                !validator.hasAreaTotalErrorNow(etAreaInformada) &&
                !validator.hasJuntaErrorNow(etJunta, juntaValueMm(), juntaRange()) &&
                !validator.hasEspessuraErrorNow(
                    etPecaEsp,
                    isPastilha(),
                    mToMmIfLooksLikeMeters(getD(etPecaEsp))
                ) &&
                !validator.hasPecasPorCaixaErrorNow(etPecasPorCaixa) &&
                !validator.hasDesnivelErrorNow(etDesnivel, tilDesnivel.isVisible, getD(etDesnivel))

        val inputs = viewModel.inputs.value

        if (inputs.rodapeEnable &&
            inputs.rodapeMaterial == CalcRevestimentoViewModel.RodapeMaterial.PECA_PRONTA
        ) {
            // Altura em cm (já tratada pelo helper existente)
            val alturaCm = mToCmIfLooksLikeMeters(getD(etRodapeAltura))
            val compCm = getD(etRodapeCompComercial)

            val alturaOk = alturaCm != null &&
                    alturaCm in 3.0..30.0 &&
                    tilRodapeAltura.error.isNullOrEmpty()

            // Obrigatório e entre 5 e 300cm
            val compOk = compCm != null &&
                    compCm in 5.0..300.0 &&
                    tilRodapeCompComercial.error.isNullOrEmpty()

            btnNext.isEnabled = btnNext.isEnabled && alturaOk && compOk
        }
    }

    // Exibe resultado do cálculo
    private fun displayResultado(r: CalcRevestimentoViewModel.Resultado) = with(binding) {
        headerResumo.text = buildHeaderText(r)

        tableContainer.removeAllViews()
        tableContainer.addView(tableBuilder.makeHeaderRow())
        r.itens.forEach { tableContainer.addView(tableBuilder.makeDataRow(it)) }
    }

    // Constrói texto do cabeçalho do resultado
    private fun buildHeaderText(r: CalcRevestimentoViewModel.Resultado): String = buildString {
        appendLine("📊 RESUMO DO CÁLCULO\n")

        append("🏗️ Revestimento: ")
        appendLine(
            when (r.header.tipo) {
                "PISO" -> "Piso"
                "AZULEJO" -> "Azulejo"
                "PASTILHA" -> "Pastilha"
                "PEDRA" -> "Pedra portuguesa/irregular"
                "PISO_INTERTRAVADO" -> "Piso intertravado"
                "MARMORE" -> "Mármore"
                "GRANITO" -> "Granito"
                else -> r.header.tipo
            }
        )

        append("🌡️ Ambiente: ")
        appendLine(
            when (r.header.ambiente) {
                "SECO" -> "Seco"
                "SEMI" -> "Semi-molhado"
                "MOLHADO" -> "Molhado"
                "SEMPRE" -> "Sempre molhado"
                else -> r.header.ambiente
            }
        )

        r.classeArgamassa?.let { appendLine("🧱 Argamassa: $it") }

        val areaTotalHeader = r.header.areaM2 + r.header.rodapeAreaM2
        appendLine("📐 Área total: ${NumberFormatter.format(areaTotalHeader)} m²")

        if (r.header.rodapeAreaM2 > 0) {
            appendLine(
                "📏 Rodapé: ${NumberFormatter.format(r.header.rodapeBaseM2)} m² + ${
                    NumberFormatter.format(r.header.rodapeAlturaCm)
                } cm = ${NumberFormatter.format(r.header.rodapeAreaM2)} m²"
            )
        }

        val espMm = viewModel.inputs.value.pecaEspMm
        if (espMm != null) {
            if (viewModel.inputs.value.revest == CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO) {
                val espCm = espMm / 10.0
                appendLine("🧩 Espessura: ${NumberFormatter.format(espCm)} cm")
            } else {
                appendLine("🧩 Espessura: ${NumberFormatter.format(espMm)} mm")
            }
        }

        viewModel.inputs.value.pecasPorCaixa?.let {
            appendLine("📦 Peças por caixa: $it")
        }

        if (viewModel.inputs.value.revest != CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO &&
            r.header.juntaMm > 0.0
        ) {
            appendLine("🔗 Junta: ${NumberFormatter.format(r.header.juntaMm)} mm")
        }

        viewModel.inputs.value.desnivelCm?.let {
            appendLine("📉 Desnível: ${NumberFormatter.format(it)} cm")
        }

        if (r.header.sobraPct > 0) {
            appendLine("➕ Sobra técnica: ${NumberFormatter.format(r.header.sobraPct)}%")
        }
    }.trimEnd()

    /* ═══════════════════════════════════════════════════════════════════════════
     * HANDLERS DE ETAPAS
     * ═══════════════════════════════════════════════════════════════════════════ */

    // Ajusta layout da tela 0
    private fun handleStep0Layout(step: Int) = with(binding) {
        val tela0 = viewFlipper.getChildAt(0) as LinearLayout

        if (step == 0) {
            bottomBar.isGone = true
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

    // Inicializa ícones conforme etapa
    private fun handleStepIcons(step: Int) = with(binding) {
        when (step) {
            4 -> { // Medidas
                iconManager.setRequiredIconVisible(etComp, true)
                iconManager.setRequiredIconVisible(etLarg, true)
                iconManager.setRequiredIconVisible(etAreaInformada, true)
                if (tilAltura.isVisible) iconManager.setRequiredIconVisible(etAlt, true)
                updateRequiredIconsStep3()
            }

            5 -> updateRequiredIconsStep4() // Peça
            6 -> updateRequiredIconsStep5() // Rodapé
        }
    }

    // Configura botões conforme etapa
    private fun handleStepButtons(step: Int) = with(binding) {
        btnBack.isVisible = step > 0
        btnNext.isVisible = step in 1..7
        btnCalcular.isVisible = (step == 8)
        toolbarMenuVisible(step == 9)

        if (step == 9) {
            setupNovoCalculoButton()
        } else {
            restoreDefaultBackButton()
        }
    }

    // Configura botão "Novo Cálculo" na etapa 8
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
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.calc_new_calc_title))
                .setMessage(getString(R.string.calc_new_calc_message))
                .setPositiveButton(getString(R.string.generic_yes)) { _, _ ->
                    navigateBackToCalcMaterial()
                }
                .setNegativeButton(getString(R.string.generic_cancel), null)
                .show()
        }
    }

    // Restaura botão "Voltar" padrão
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

    // Preenche resumo na etapa 7
    private fun handleStep7Resume(step: Int) {
        if (step == 8) {
            binding.tvResumoRevisao.text = viewModel.getResumoRevisao()
        }
    }

    private fun RadioGroup.setCheckedSafely(id: Int?) {
        if (id == null) {
            if (checkedRadioButtonId != View.NO_ID) {
                clearCheck()
            }
        } else if (checkedRadioButtonId != id) {
            check(id)
        }
    }

    // Sincroniza radio groups
    private fun syncRadioGroups(i: CalcRevestimentoViewModel.Inputs) = with(binding) {
        isSyncing = true

        // Revestimento
        when (i.revest) {
            CalcRevestimentoViewModel.RevestimentoType.PISO -> rgRevest.setCheckedSafely(R.id.rbPiso)
            CalcRevestimentoViewModel.RevestimentoType.AZULEJO -> rgRevest.setCheckedSafely(R.id.rbAzulejo)
            CalcRevestimentoViewModel.RevestimentoType.PASTILHA -> rgRevest.setCheckedSafely(R.id.rbPastilha)
            CalcRevestimentoViewModel.RevestimentoType.PEDRA -> rgRevest.setCheckedSafely(R.id.rbPedra)
            CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO -> rgRevest.setCheckedSafely(
                R.id.rbPisoIntertravado
            )

            CalcRevestimentoViewModel.RevestimentoType.MARMORE -> rgRevest.setCheckedSafely(R.id.rbMarmore)
            CalcRevestimentoViewModel.RevestimentoType.GRANITO -> rgRevest.setCheckedSafely(R.id.rbGranito)
            null -> rgRevest.setCheckedSafely(null)
        }

        // Tipo placa piso
        when (i.pisoPlacaTipo) {
            CalcRevestimentoViewModel.PlacaTipo.CERAMICA -> rgPlacaTipo.setCheckedSafely(R.id.rbCeramica)
            CalcRevestimentoViewModel.PlacaTipo.PORCELANATO -> rgPlacaTipo.setCheckedSafely(R.id.rbPorcelanato)
            null -> rgPlacaTipo.setCheckedSafely(null)
        }

        // Ambiente
        when (i.ambiente) {
            CalcRevestimentoViewModel.AmbienteType.SECO -> rgAmbiente.setCheckedSafely(R.id.rbSeco)
            CalcRevestimentoViewModel.AmbienteType.SEMI -> rgAmbiente.setCheckedSafely(R.id.rbSemi)
            CalcRevestimentoViewModel.AmbienteType.MOLHADO -> rgAmbiente.setCheckedSafely(R.id.rbMolhado)
            CalcRevestimentoViewModel.AmbienteType.SEMPRE -> rgAmbiente.setCheckedSafely(R.id.rbSempre)
            null -> rgAmbiente.setCheckedSafely(null)
        }

        // Rodapé
        when (i.rodapeMaterial) {
            CalcRevestimentoViewModel.RodapeMaterial.MESMA_PECA -> rgRodapeMat.setCheckedSafely(R.id.rbRodapeMesma)
            CalcRevestimentoViewModel.RodapeMaterial.PECA_PRONTA -> rgRodapeMat.setCheckedSafely(R.id.rbRodapePeca)
        }

        // Tráfego
        when (i.trafego) {
            CalcRevestimentoViewModel.TrafegoType.LEVE -> rgTrafego.setCheckedSafely(R.id.rbTrafegoLeve)
            CalcRevestimentoViewModel.TrafegoType.MEDIO -> rgTrafego.setCheckedSafely(R.id.rbTrafegoMedio)
            CalcRevestimentoViewModel.TrafegoType.PESADO -> rgTrafego.setCheckedSafely(R.id.rbTrafegoPesado)
            null -> rgTrafego.setCheckedSafely(null)
        }

        // Impermeabilização Intertravado
        when (i.impIntertravadoTipo) {
            CalcRevestimentoViewModel.ImpIntertravadoTipo.MANTA_GEOTEXTIL ->
                rgIntertravadoImp.setCheckedSafely(R.id.rbImpMantaGeotextil)

            CalcRevestimentoViewModel.ImpIntertravadoTipo.ADITIVO_SIKA1 ->
                rgIntertravadoImp.setCheckedSafely(R.id.rbImpAditivoSika1)

            else -> rgIntertravadoImp.setCheckedSafely(null)
        }

        isSyncing = false
    }

    // Sincroniza valores dos campos
    private fun syncFieldValues(i: CalcRevestimentoViewModel.Inputs) = with(binding) {

        // Detecta reset geral (Inputs() padrão)
        if (i == CalcRevestimentoViewModel.Inputs()) {
            // Limpa textos
            etComp.text?.clear()
            etLarg.text?.clear()
            etAlt.text?.clear()
            etAreaInformada.text?.clear()
            etPecaComp.text?.clear()
            etPecaLarg.text?.clear()
            etPecaEsp.text?.clear()
            etJunta.text?.clear()
            etSobra.text?.clear()
            etPecasPorCaixa.text?.clear()
            etDesnivel.text?.clear()
            etRodapeAltura.text?.clear()
            etRodapeCompComercial.text?.clear()

            // Limpa erros básicos
            tilComp.error = null
            tilLarg.error = null
            tilAltura.error = null
            tilAreaInformada.error = null
            tilPecaComp.error = null
            tilPecaLarg.error = null
            tilPecaEsp.error = null
            tilJunta.error = null
            tilPecasPorCaixa.error = null
            tilDesnivel.error = null
            tilSobra.error = null
            tilRodapeAltura.error = null
            tilRodapeCompComercial.error = null

            tvAreaTotalAviso.isVisible = false

            // Switches / grupos dependentes
            switchImp.isChecked = false
            switchRodape.isChecked = false
            groupRodapeFields.isVisible = false

            return@with
        }

        if (i.compM != null || i.largM != null || i.altM != null || i.areaInformadaM2 != null ||
            i.pecaCompCm != null || i.pecaLargCm != null || i.juntaMm != null
        ) {

            syncField(etComp, i.compM)
            syncField(etLarg, i.largM)
            syncField(etAlt, i.altM)
            syncField(etAreaInformada, i.areaInformadaM2)
            syncFieldPeca(etPecaComp, i.pecaCompCm)
            syncFieldPeca(etPecaLarg, i.pecaLargCm)
            if (i.pecaEspMm != null) {
                if (i.revest == CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO) {
                    // armazenado em mm, exibido em cm
                    syncField(etPecaEsp, i.pecaEspMm / 10.0)
                } else {
                    syncField(etPecaEsp, i.pecaEspMm)
                }
            }
            syncField(etJunta, i.juntaMm)
            syncField(etSobra, i.sobraPct)
            syncField(etRodapeAltura, i.rodapeAlturaCm)

            i.pecasPorCaixa?.let {
                val current = etPecasPorCaixa.text?.toString()?.toIntOrNull()
                if (current != it) etPecasPorCaixa.setText(it.toString())
            }
        }

        tvAreaTotalAviso.isVisible = (i.areaInformadaM2 != null)
        switchImp.isEnabled = !i.impermeabilizacaoLocked
        switchImp.isChecked = i.impermeabilizacaoOn
        switchRodape.isChecked = i.rodapeEnable
        groupRodapeFields.isVisible = i.rodapeEnable
    }

    // Sincroniza campo simples
    private fun syncField(et: TextInputEditText, value: Double?) {
        if (et.hasFocus()) return
        if (value != null) {
            val currentNum = et.text?.toString()?.replace(",", ".")?.toDoubleOrNull()
            if (currentNum != value) {
                et.setText(value.toString().replace(".", ","))
            }
        }
    }

    // Sincroniza campo de peça
    private fun syncFieldPeca(et: TextInputEditText, valueCm: Double?) {
        if (et.hasFocus() || valueCm == null) return

        val raw = et.text?.toString()?.replace(",", ".")?.toDoubleOrNull()
        if (!isMG() && raw != null && raw < 1.0) {
            val asCm = raw * 100.0
            if (kotlin.math.abs(asCm - valueCm) < 1e-6) return
        }

        val display = if (isMG()) valueCm / 100.0 else valueCm
        if (raw == null || kotlin.math.abs(raw - display) > 1e-6) {
            et.setText(display.toString().replace(".", ","))
        }
    }

    // Atualiza visibilidades condicionais
    private fun updateUIVisibility(i: CalcRevestimentoViewModel.Inputs) = with(binding) {
        tvObsAc3.isVisible = (i.revest == CalcRevestimentoViewModel.RevestimentoType.PISO &&
                i.pisoPlacaTipo == CalcRevestimentoViewModel.PlacaTipo.PORCELANATO &&
                (i.ambiente == CalcRevestimentoViewModel.AmbienteType.SEMI ||
                        i.ambiente == CalcRevestimentoViewModel.AmbienteType.MOLHADO))

        groupPlacaTipo.isVisible = (i.revest == CalcRevestimentoViewModel.RevestimentoType.PISO)

        if (i.revest == CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO) {
            // Oculta campos não usados na peça
            tilPecasPorCaixa.isVisible = false
            etPecasPorCaixa.text?.clear()
            tilPecasPorCaixa.error = null

            tilJunta.isVisible = false
            etJunta.text?.clear()
            tilJunta.error = null

            tilDesnivel.isVisible = false
            tilDesnivel.error = null
        }

        // Impermeabilização Intertravado: controlar UI conforme regras
        if (i.revest == CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO) {
            val amb = i.ambiente
            val traf = i.trafego

            // Se ainda não tem ambiente ou tráfego, ou se for SECO → sem opções
            if (amb == null || traf == null || amb == CalcRevestimentoViewModel.AmbienteType.SECO) {
                switchImp.isVisible = false
                groupIntertravadoImpOptions.isVisible = false
                rgIntertravadoImp.clearCheck()
            } else {
                // Sempre mostrar switch para intertravado quando houver etapa
                switchImp.isVisible = true
                switchImp.isEnabled = true

                val precisaEscolhaTipo =
                    (amb == CalcRevestimentoViewModel.AmbienteType.MOLHADO ||
                            amb == CalcRevestimentoViewModel.AmbienteType.SEMPRE) &&
                            (traf == CalcRevestimentoViewModel.TrafegoType.LEVE ||
                                    traf == CalcRevestimentoViewModel.TrafegoType.MEDIO)

                val showRadios = precisaEscolhaTipo && i.impermeabilizacaoOn
                groupIntertravadoImpOptions.isVisible = showRadios
                if (!showRadios) {
                    rgIntertravadoImp.clearCheck()
                }
            }
        } else {
            // Mantém comportamento atual para os demais revestimentos
            switchImp.isVisible = true
            switchImp.isEnabled = !i.impermeabilizacaoLocked
            groupIntertravadoImpOptions.isVisible = false
        }

        if (i.revest != CalcRevestimentoViewModel.RevestimentoType.PISO || i.pisoPlacaTipo == null) {
            if (rgPlacaTipo.checkedRadioButtonId != View.NO_ID) {
                rgPlacaTipo.clearCheck()
            }
        }

        tilAltura.isVisible = i.revest in setOf(
            CalcRevestimentoViewModel.RevestimentoType.AZULEJO,
            CalcRevestimentoViewModel.RevestimentoType.PASTILHA,
            CalcRevestimentoViewModel.RevestimentoType.MARMORE,
            CalcRevestimentoViewModel.RevestimentoType.GRANITO
        )

        groupPecaTamanho.isVisible = (i.revest != CalcRevestimentoViewModel.RevestimentoType.PEDRA)

        if (i.revest != CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO &&
            groupPecaTamanho.isVisible
        ) {
            tilJunta.isVisible = true
        }

        val isPastilha = (i.revest == CalcRevestimentoViewModel.RevestimentoType.PASTILHA)
        tilPecaEsp.isVisible = !isPastilha
        if (isPastilha) {
            etPecaEsp.text?.clear()
            tilPecaEsp.error = null
        }
        // Visibilidade do Desnível (Pedra / Mármore / Granito)
        tilDesnivel.isVisible = i.revest in setOf(
            CalcRevestimentoViewModel.RevestimentoType.PEDRA,
            CalcRevestimentoViewModel.RevestimentoType.MARMORE,
            CalcRevestimentoViewModel.RevestimentoType.GRANITO
        )
        if (!tilDesnivel.isVisible) {
            tilDesnivel.error = null // limpa erro quando oculto
        }
        // Ocultar "Peças por caixa" em Mármore, Granito, Pedra e Piso Intertravado
        val isMG = i.revest == CalcRevestimentoViewModel.RevestimentoType.MARMORE ||
                i.revest == CalcRevestimentoViewModel.RevestimentoType.GRANITO

        val hidePecasPorCaixa =
            isMG ||
                    i.revest == CalcRevestimentoViewModel.RevestimentoType.PEDRA ||
                    i.revest == CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO

        tilPecasPorCaixa.isVisible = !hidePecasPorCaixa

        if (hidePecasPorCaixa) {
            etPecasPorCaixa.text?.clear()
            tilPecasPorCaixa.error = null
        }
    }

    // Atualiza helper texts dinâmicos
    private fun updateHelperTexts(i: CalcRevestimentoViewModel.Inputs) = with(binding) {
        val padraoEsp = viewModel.espessuraPadraoAtual()

        if (i.revest == CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO) {
            tilPecaEsp.hint = getString(R.string.calc_step4_peca_esp_intertravado_hint)
            tilPecaEsp.setHelperTextSafely(
                getString(R.string.calc_step4_peca_esp_intertravado_helper)
            )
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

        when {
            pastilha -> {
                tilPecaComp.hint = getString(R.string.calc_step4_pastilha_comp_hint)
                tilPecaLarg.hint = getString(R.string.calc_step4_pastilha_larg_hint)
                tilPecaComp.setHelperTextSafely(getString(R.string.calc_piece_helper_pastilha))
                tilPecaLarg.setHelperTextSafely(getString(R.string.calc_piece_helper_pastilha))
                tilJunta.setHelperTextSafely(
                    getString(
                        R.string.calc_step4_junta_helper_pastilha,
                        NumberFormatter.format(viewModel.juntaPadraoAtual())
                    )
                )
            }

            else -> {
                tilJunta.setHelperTextSafely(getString(R.string.calc_step4_junta_helper_default))
                tilPecaComp.hint =
                    getString(if (mg) R.string.calc_step4_peca_comp_m else R.string.calc_step4_peca_comp)
                tilPecaLarg.hint =
                    getString(if (mg) R.string.calc_step4_peca_larg_m else R.string.calc_step4_peca_larg)
                tilPecaComp.setHelperTextSafely(getString(if (mg) R.string.calc_piece_helper_mg else R.string.calc_piece_helper_cm))
                tilPecaLarg.setHelperTextSafely(getString(if (mg) R.string.calc_piece_helper_mg else R.string.calc_piece_helper_cm))
            }
        }
        when (i.revest) {
            CalcRevestimentoViewModel.RevestimentoType.PEDRA -> {
                tilDesnivel.setHelperTextSafely(getString(R.string.calc_step4_desnivel_helper_pedra))
            }

            CalcRevestimentoViewModel.RevestimentoType.MARMORE,
            CalcRevestimentoViewModel.RevestimentoType.GRANITO -> {
                tilDesnivel.setHelperTextSafely(getString(R.string.calc_step4_desnivel_helper_mg))
            }

            else -> tilDesnivel.setHelperTextSafely(null)
        }
    }

    // Atualiza helper texts dinâmicos para junta
    private fun updateJuntaHelper(i: CalcRevestimentoViewModel.Inputs) = with(binding) {
        tilJunta.helperText = when (i.revest) {
            CalcRevestimentoViewModel.RevestimentoType.PISO -> {
                if (i.pisoPlacaTipo == CalcRevestimentoViewModel.PlacaTipo.PORCELANATO) {
                    getString(R.string.helper_junta_piso_porcelanato)
                } else {
                    getString(R.string.helper_junta_piso_ceramico)
                }
            }

            CalcRevestimentoViewModel.RevestimentoType.PASTILHA ->
                getString(R.string.helper_junta_pastilha)

            CalcRevestimentoViewModel.RevestimentoType.AZULEJO ->
                getString(R.string.helper_junta_azulejo)

            CalcRevestimentoViewModel.RevestimentoType.PEDRA ->
                getString(R.string.helper_junta_pedra_portuguesa)

            CalcRevestimentoViewModel.RevestimentoType.MARMORE,
            CalcRevestimentoViewModel.RevestimentoType.GRANITO ->
                getString(R.string.helper_junta_marmore_granito)

            CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO ->
                getString(R.string.helper_junta_piso_intertravado)

            else -> null
        }
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * EXPORTAÇÃO DE PDF
     * ═══════════════════════════════════════════════════════════════════════════ */

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

    private fun sharePdf(bytes: ByteArray, fileName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val cacheFile = File(requireContext().cacheDir, fileName)
            FileOutputStream(cacheFile).use { it.write(bytes) }

            withContext(Dispatchers.Main) {
                val uri = FileProvider.getUriForFile(
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
     * HELPERS E UTILITÁRIOS
     * ═══════════════════════════════════════════════════════════════════════════ */

    private fun isMG() = viewModel.inputs.value.revest in setOf(
        CalcRevestimentoViewModel.RevestimentoType.MARMORE,
        CalcRevestimentoViewModel.RevestimentoType.GRANITO
    )

    private fun isPastilha() =
        viewModel.inputs.value.revest == CalcRevestimentoViewModel.RevestimentoType.PASTILHA

    private fun parsePecaToCm(v: Double?) = UnitConverter.parsePecaToCm(v, isMG())
    private fun mToCmIfLooksLikeMeters(v: Double?) = UnitConverter.mToCmIfLooksLikeMeters(v)
    private fun mToMmIfLooksLikeMeters(v: Double?) = UnitConverter.mToMmIfLooksLikeMeters(v)

    private fun getD(et: TextInputEditText) =
        et.text?.toString()?.replace(",", ".")?.toDoubleOrNull()

    private fun juntaValueMm() = mToMmIfLooksLikeMeters(getD(binding.etJunta))
    private fun juntaRange() = if (isPastilha()) 1.0..3.0 else 0.5..20.0
    private fun juntaErrorMsg() = getString(
        if (isPastilha()) R.string.calc_err_junta_pastilha_range
        else R.string.calc_err_junta_range
    )

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

    private fun toolbarMenuVisible(visible: Boolean) {
        binding.toolbar.menu.findItem(R.id.action_export_calc)?.isVisible = visible
    }

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