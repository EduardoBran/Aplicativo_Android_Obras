package com.luizeduardobrandao.obra.ui.cronograma

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.checkbox.MaterialCheckBox
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Etapa
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.ui.funcionario.FuncionarioViewModel
import com.luizeduardobrandao.obra.databinding.FragmentCronogramaRegisterBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.applyFullWidthButtonSizingGrowShrink
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.showMaterialDatePickerBrInRange
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class CronogramaRegisterFragment : Fragment() {

    private var _binding: FragmentCronogramaRegisterBinding? = null
    private val binding get() = _binding!!

    private val args: CronogramaRegisterFragmentArgs by navArgs()
    private val viewModel: CronogramaViewModel by viewModels()

    // Inclusão quando etapaId == null
    private val isEdit get() = args.etapaId != null
    private var etapaOriginal: Etapa? = null
    private var dataLoaded = false
    private var watchersSet = false

    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
        isLenient = false
    }

    // Controle de loading/navegação (mesmo padrão de Nota/Funcionário)
    private var isSaving = false
    private var shouldCloseAfterSave = false

    // Responsável
    private enum class RespTipo { FUNCIONARIOS, EMPRESA }

    private var responsavelSelecionado: RespTipo? = null
    private var lastRespErrorVisible: Boolean? = null
    private var lastEmpresaErrorVisible: Boolean? = null

    // Datas da Obra (BR dd/MM/uuuu) para limitar o DatePicker
    private var obraDataInicioBr: String? = null
    private var obraDataFimBr: String? = null

    // ViewModel de Funcionários (pega obraId via Safe-Args / SavedStateHandle)
    private val viewModelFuncionario: FuncionarioViewModel by viewModels()

    // Lista (ordenada) de funcionários ATIVOS por nome
    private val funcionariosAtivos: MutableList<String> = mutableListOf()

    // Manter funcionários completos para o novo diálogo de edição
    private val funcionariosAll: MutableList<Funcionario> = mutableListOf()

    // Seleção atual (exibida e salva)
    private val selecionadosAtual: LinkedHashSet<String> = linkedSetOf()

    // controla restauração do diálogo após rotação
    private var isFuncPickerOpen: Boolean = false
    private var shouldReopenPicker: Boolean = false
    private var restoredSelectionFromState: Boolean = false

    private var lastTituloErrorVisible: Boolean? = null
    private var lastDataInicioErrorVisible: Boolean? = null
    private var lastDataFimErrorVisible: Boolean? = null

    // Snapshot local das etapas para validação de título duplicado
    private var etapasSnapshot: List<Etapa> = emptyList()
    private var etapasLoaded: Boolean = false

    // Bloqueio temporário após tentar salvar com título duplicado
    private var blockSaveDueToDuplicate: Boolean = false
    private var lastDuplicateTitleKey: String? = null

    private fun titleKey(raw: String?): String =
        raw?.trim()
            ?.lowercase(Locale.getDefault())
            ?.replace(Regex("\\s+"), " ")  // colapsa espaços
            .orEmpty()

    private companion object {
        private const val STATE_FUNC_PICKER_OPEN = "state_func_picker_open"
        private const val STATE_FUNC_SELECTION = "state_func_selection"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCronogramaRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // se o picker estava aberto antes da rotação, marcamos para reabrir
        shouldReopenPicker = savedInstanceState?.getBoolean(STATE_FUNC_PICKER_OPEN) == true

        // restaura a seleção feita no diálogo antes da rotação (se houver)
        savedInstanceState?.getStringArrayList(STATE_FUNC_SELECTION)?.let { restored ->
            selecionadosAtual.clear()
            selecionadosAtual.addAll(restored)
            restoredSelectionFromState = true
            renderFuncionariosSelecionados() // já exibe abaixo do campo
        }

        with(binding) {
            // Toolbar back & title
            toolbarEtapaReg.menu.findItem(R.id.action_open_gantt)?.icon?.setTint(
                ContextCompat.getColor(requireContext(), android.R.color.white)
            )
            toolbarEtapaReg.setNavigationOnClickListener { handleBackPress() }
            toolbarEtapaReg.title = if (isEdit)
                getString(R.string.etapa_reg_title_edit)
            else
                getString(R.string.etapa_reg_title)

            // Responsividade do botão salvar (full-width)
            btnSaveEtapa.doOnPreDraw {
                btnSaveEtapa.applyFullWidthButtonSizingGrowShrink()
            }

            // Negrito em "Resumo" e "Notas" via HTML
            tvEmpresaValorInfo.text = androidx.core.text.HtmlCompat.fromHtml(
                getString(R.string.etapa_reg_empresa_valor_info),
                androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
            )

            // Date pickers
            etDataInicioEtapa.setOnClickListener {
                // initial = valor atual do campo (se houver), limitado ao intervalo da Obra
                val initial = etDataInicioEtapa.text?.toString()
                showMaterialDatePickerBrInRange(
                    initialBr = initial,
                    startBr = obraDataInicioBr,
                    endBr = obraDataFimBr
                ) { chosen ->
                    etDataInicioEtapa.setText(chosen)
                    validateForm()
                }
            }
            etDataFimEtapa.setOnClickListener {
                val initial = etDataFimEtapa.text?.toString()
                showMaterialDatePickerBrInRange(
                    initialBr = initial,
                    startBr = obraDataInicioBr,
                    endBr = obraDataFimBr
                ) { chosen ->
                    etDataFimEtapa.setText(chosen)
                    validateForm()
                }
            }

            // Responsável - listeners
            rgResponsavel.setOnCheckedChangeListener { _, checkedId ->
                // scene root correto é o LinearLayout que contém os views que mudam
                val sceneRoot = formContainer
                TransitionManager.beginDelayedTransition(
                    sceneRoot,
                    AutoTransition().apply { duration = 150 }
                )

                when (checkedId) {
                    R.id.rbRespFuncs -> {
                        responsavelSelecionado = RespTipo.FUNCIONARIOS

                        // Funcionários visíveis
                        tilFuncionarios.visibility = View.VISIBLE
                        tvFuncionariosSelecionados.visibility =
                            if (selecionadosAtual.isEmpty()) View.GONE else View.VISIBLE

                        // Empresa invisível
                        tilEmpresa.visibility = View.GONE
                        tvEmpresaError.visibility = View.GONE
                        etEmpresa.setText("")

                        // Valor Empresa invisível
                        tilEmpresaValor.visibility = View.GONE
                        tvEmpresaValorInfo.visibility = View.GONE
                        etEmpresaValor.setText("")
                    }

                    R.id.rbRespEmpresa -> {
                        responsavelSelecionado = RespTipo.EMPRESA

                        // Funcionários invisível
                        tilFuncionarios.visibility = View.GONE
                        tvFuncionariosSelecionados.visibility = View.GONE
                        tvFuncionariosSelecionados.text = ""
                        dropdownFuncionarios.setText("", false)

                        // Empresa visível
                        tilEmpresa.visibility = View.VISIBLE
                        // Valor Empresa visível
                        tilEmpresaValor.visibility = View.VISIBLE
                        tvEmpresaValorInfo.visibility = View.VISIBLE
                    }

                    else -> {
                        responsavelSelecionado = null

                        tilFuncionarios.visibility = View.GONE
                        tvFuncionariosSelecionados.visibility = View.GONE
                        tvFuncionariosSelecionados.text = ""

                        tilEmpresa.visibility = View.GONE
                        tvEmpresaError.visibility = View.GONE
                        etEmpresa.setText("")

                        tilEmpresaValor.visibility = View.GONE
                        tvEmpresaValorInfo.visibility = View.GONE
                        etEmpresaValor.setText("")
                    }
                }

                validateForm()
            }

            // Botão Salvar/Atualizar
            btnSaveEtapa.text = getString(
                if (isEdit) R.string.generic_update else R.string.generic_add
            )
            btnSaveEtapa.isEnabled = false
            btnSaveEtapa.setOnClickListener { onSaveClicked() }

            // ⚠️ NÃO instale watchers nem valide ainda em modo edição
            //    (anti-"flash"). Só depois que os dados chegarem.
            viewModel.loadEtapas()
            if (isEdit) {
                viewModel.loadEtapas()
                observeEtapa() // em observeEtapa(), após preencher, marcamos dataLoaded=true e instalamos os watchers
            } else {
                // Modo criação: podemos liberar imediatamente
                dataLoaded = true
                setupValidationWatchersOnce()
                validateForm()

                // Ajustes iniciais baseados nos TextViews de erro (sem animação)
                root.post {
                    adjustSpacingAfterView(
                        tvTituloError,
                        tilDescricaoCronograma,
                        visibleTopDp = 8,
                        goneTopDp = 12,
                        animate = false
                    )
                    adjustSpacingAfterView(
                        tvDataInicioError,
                        tilDataFimEtapa,
                        visibleTopDp = 22,
                        goneTopDp = 12,
                        animate = false
                    )
                }
            }

            // Coleta estado de operação (loading/sucesso/erro)
            collectOperationState()

            // Limites de datas da Obra para o DatePicker
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.obraState.collect { obra ->
                        obraDataInicioBr = obra?.dataInicio
                        obraDataFimBr = obra?.dataFim
                    }
                }
            }

            // ▼ Funcionários (dropdown multi-seleção)
            setupFuncionariosDropdown()                       // configura listeners/adapter
            viewModelFuncionario.loadFuncionarios()           // inicia listener dos funcionários
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModelFuncionario.state.collect { ui ->
                        when (ui) {
                            is UiState.Success -> {
                                // mantém um cache completo (ativos + inativos)
                                funcionariosAll.clear()
                                funcionariosAll.addAll(ui.data)

                                // cache de nomes ATIVOS para o autocompletar antigo e compatibilidade
                                val ativos = ui.data
                                    .filter { it.status.equals("Ativo", ignoreCase = true) }
                                    .map { it.nome.trim() }
                                    .filter { it.isNotEmpty() }
                                    .sortedBy { it.lowercase(Locale.getDefault()) }

                                funcionariosAtivos.clear()
                                funcionariosAtivos.addAll(ativos)

                                // reabrir o diálogo se ele estava aberto antes da rotação
                                if (shouldReopenPicker) {
                                    shouldReopenPicker = false
                                    binding.root.post { openFuncionariosPicker() }
                                }
                            }

                            else -> Unit
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    if (ui is UiState.Success) {
                        etapasSnapshot = ui.data
                        etapasLoaded = true
                        // Revalida para (des)habilitar o botão quando os dados chegarem
                        if (dataLoaded) validateForm()
                    }
                }
            }
        }

        // Back físico/gesto
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            handleBackPress()
        }
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_FUNC_PICKER_OPEN, isFuncPickerOpen)
        outState.putStringArrayList(
            STATE_FUNC_SELECTION,
            ArrayList(selecionadosAtual) // salva as marcações correntes
        )
    }

    /*──────────── Observa a lista para obter a etapa em modo edição ───────────*/
    private fun observeEtapa() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    if (ui is UiState.Success) {
                        val et = ui.data.firstOrNull { it.id == args.etapaId } ?: return@collect
                        etapaOriginal = et
                        fillFields(et)

                        // ✅ evita "flash" de erros
                        dataLoaded = true
                        setupValidationWatchersOnce()
                        validateForm()

                        binding.root.post {
                            // Inicial: alinhar tudo com base nos TextViews de erro (sem animação)
                            adjustSpacingAfterView(
                                binding.tvTituloError,
                                binding.tilDescricaoCronograma,
                                visibleTopDp = 8,
                                goneTopDp = 12,
                                animate = false
                            )
                            adjustSpacingAfterView(
                                binding.tvDataInicioError,
                                binding.tilDataFimEtapa,
                                visibleTopDp = 22,
                                goneTopDp = 12,
                                animate = false
                            )
                        }
                    }
                }
            }
        }
    }

    /*──────────── Coleta opState para mostrar progress/navegar ───────────*/
    private fun collectOperationState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.opState.collect { ui ->
                    when (ui) {
                        is UiState.Loading -> {
                            if (isSaving) progress(true)
                        }

                        is UiState.Success -> {
                            isSaving = false
                            if (shouldCloseAfterSave) {
                                val msgRes = if (isEdit)
                                    R.string.etapa_update_success
                                else
                                    R.string.etapa_add_success
                                Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show()

                                binding.root.post {
                                    navigateUpReturningToCallerAndRestartGanttIfNeeded()
                                    shouldCloseAfterSave = false
                                }
                            } else {
                                progress(false)
                                binding.btnSaveEtapa.isEnabled = true
                            }
                        }

                        is UiState.ErrorRes -> {
                            progress(false)
                            isSaving = false
                            shouldCloseAfterSave = false
                            binding.btnSaveEtapa.isEnabled = true
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

    /*──────────── Preenche os campos em modo edição ───────────*/
    private fun fillFields(e: Etapa) = with(binding) {
        etTituloEtapa.setText(e.titulo)
        etDescEtapa.setText(e.descricao)
        etDataInicioEtapa.setText(e.dataInicio)
        etDataFimEtapa.setText(e.dataFim)

        // Parse da seleção salva (CSV) – apenas prepara a estrutura
        if (!restoredSelectionFromState) {
            val nomes = parseCsvNomes(e.funcionarios)
            selecionadosAtual.clear()
            selecionadosAtual.addAll(nomes)
        }

        // 1) Evita disparar transições/listeners enquanto ajustamos a UI programaticamente
        rgResponsavel.setOnCheckedChangeListener(null)

        // 2) Ajusta UI de acordo com o tipo salvo
        when (e.responsavelTipo) {
            "FUNCIONARIOS" -> {
                rbRespFuncs.isChecked = true
                responsavelSelecionado = RespTipo.FUNCIONARIOS

                tilFuncionarios.visibility = View.VISIBLE
                tilEmpresa.visibility = View.GONE
                tvEmpresaError.visibility = View.GONE
                etEmpresa.setText("")
                tilEmpresaValor.visibility = View.GONE
                tvEmpresaValorInfo.visibility = View.GONE
                etEmpresaValor.setText("")
            }

            "EMPRESA" -> {
                rbRespEmpresa.isChecked = true
                responsavelSelecionado = RespTipo.EMPRESA

                tilFuncionarios.visibility = View.GONE
                tvFuncionariosSelecionados.visibility = View.GONE
                tvFuncionariosSelecionados.text = ""

                tilEmpresa.visibility = View.VISIBLE
                etEmpresa.setText(e.empresaNome.orEmpty())
                // Mostrar campo de valor e info
                tilEmpresaValor.visibility = View.VISIBLE
                tvEmpresaValorInfo.visibility = View.VISIBLE
                // Preenche valor salvo (se houver) — exibe como "5.050,20" (sem "R$")
                if (e.empresaValor != null) {
                    val nf = java.text.NumberFormat.getNumberInstance(
                        Locale("pt", "BR")
                    ).apply {
                        minimumFractionDigits = 2
                        maximumFractionDigits = 2
                        isGroupingUsed = true
                    }
                    etEmpresaValor.setText(nf.format(e.empresaValor))
                } else {
                    etEmpresaValor.setText("")
                }
            }

            else -> {
                // legado: nenhum selecionado
                rgResponsavel.clearCheck()
                responsavelSelecionado = null

                tilFuncionarios.visibility = View.GONE
                tvFuncionariosSelecionados.visibility = View.GONE
                tvFuncionariosSelecionados.text = ""

                tilEmpresa.visibility = View.GONE
                tvEmpresaError.visibility = View.GONE
                etEmpresa.setText("")
                tilEmpresaValor.visibility = View.GONE
                tvEmpresaValorInfo.visibility = View.GONE
                etEmpresaValor.setText("")
            }
        }

        // 3) Só agora renderize Funcionários, SE esse for o tipo escolhido
        if (responsavelSelecionado == RespTipo.FUNCIONARIOS) {
            renderFuncionariosSelecionados()
        }

        // 4) Reata o listener após o setup programático
        rgResponsavel.setOnCheckedChangeListener { _, checkedId ->
            val sceneRoot = formContainer
            TransitionManager.beginDelayedTransition(
                sceneRoot, AutoTransition().apply { duration = 150 }
            )
            when (checkedId) {
                R.id.rbRespFuncs -> {
                    responsavelSelecionado = RespTipo.FUNCIONARIOS

                    // Funcionários visíveis
                    tilFuncionarios.visibility = View.VISIBLE
                    tvFuncionariosSelecionados.visibility =
                        if (selecionadosAtual.isEmpty()) View.GONE else View.VISIBLE

                    // Empresa invisível
                    tilEmpresa.visibility = View.GONE
                    tvEmpresaError.visibility = View.GONE
                    etEmpresa.setText("")

                    // Valor da empresa invisível + limpar
                    tilEmpresaValor.visibility = View.GONE
                    tvEmpresaValorInfo.visibility = View.GONE
                    etEmpresaValor.setText("")
                }

                R.id.rbRespEmpresa -> {
                    responsavelSelecionado = RespTipo.EMPRESA

                    // Funcionários invisível
                    tilFuncionarios.visibility = View.GONE
                    tvFuncionariosSelecionados.visibility = View.GONE
                    tvFuncionariosSelecionados.text = ""
                    dropdownFuncionarios.setText("", false)

                    // Empresa visível
                    tilEmpresa.visibility = View.VISIBLE

                    // Valor da empresa visível
                    tilEmpresaValor.visibility = View.VISIBLE
                    tvEmpresaValorInfo.visibility = View.VISIBLE
                }

                else -> {
                    responsavelSelecionado = null

                    // Esconde tudo e limpa
                    tilFuncionarios.visibility = View.GONE
                    tvFuncionariosSelecionados.visibility = View.GONE
                    tvFuncionariosSelecionados.text = ""

                    tilEmpresa.visibility = View.GONE
                    tvEmpresaError.visibility = View.GONE
                    etEmpresa.setText("")

                    tilEmpresaValor.visibility = View.GONE
                    tvEmpresaValorInfo.visibility = View.GONE
                    etEmpresaValor.setText("")
                }
            }
            validateForm()
        }
    }


    /*──────────── Salvar / Validar ───────────*/
    private fun onSaveClicked() {
        val titulo = binding.etTituloEtapa.text.toString().trim()
        val dataIni = binding.etDataInicioEtapa.text.toString().trim()
        val dataFim = binding.etDataFimEtapa.text.toString().trim()

        // Checagem de duplicado (SNACK só no clique)
        val tituloNorm = titleKey(titulo)
        val existeMesmoTitulo = etapasSnapshot.any { etapa ->
            titleKey(etapa.titulo) == tituloNorm && (!isEdit || etapa.id != args.etapaId)
        }
        if (existeMesmoTitulo) {
            // Bloqueia até o usuário alterar o título
            blockSaveDueToDuplicate = true
            lastDuplicateTitleKey = tituloNorm
            binding.btnSaveEtapa.isEnabled = false

            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.snack_error),
                getString(R.string.etapa_error_duplicate_title),
                getString(R.string.snack_button_ok)
            )
            return
        }

        if (titulo.length < Constants.Validation.MIN_NAME) {
            showError(R.string.etapa_error_title); return
        }
        if (dataIni.isBlank() || dataFim.isBlank()) {
            showError(R.string.etapa_error_dates); return
        }
        if (!isDateOrderValid(dataIni, dataFim)) {
            binding.tilDataFimEtapa.error = null // garantimos que não cria caption interno
            binding.tvDataFimError.isVisible = true
            binding.tvDataFimError.text = getString(R.string.etapa_error_date_order)
            return
        }
        val empresaValorParsed: Double? = if (responsavelSelecionado == RespTipo.EMPRESA) {
            parseEmpresaValorOrNull(binding.etEmpresaValor.text?.toString()) {
                binding.etEmpresaValor.setText("")
            }
        } else null

        val etapa =
            if (isEdit) {
                // ✅ Preserva status, progresso e diasConcluidos (e quaisquer demais campos)
                val base = etapaOriginal ?: return
                base.copy(
                    titulo = titulo,
                    descricao = binding.etDescEtapa.text.toString().trim(),
                    funcionarios = selecionadosAtual
                        .toList()
                        .sortedBy { it.lowercase(Locale.getDefault()) }
                        .joinToString(", ")
                        .ifBlank { null },
                    dataInicio = dataIni,
                    dataFim = dataFim,
                    responsavelTipo = when (responsavelSelecionado) {
                        RespTipo.FUNCIONARIOS -> "FUNCIONARIOS"
                        RespTipo.EMPRESA -> "EMPRESA"
                        else -> null
                    },
                    empresaNome = when (responsavelSelecionado) {
                        RespTipo.EMPRESA -> binding.etEmpresa.text?.toString()?.trim().orEmpty()
                        else -> null
                    },
                    empresaValor = when (responsavelSelecionado) {
                        RespTipo.EMPRESA -> empresaValorParsed
                        else -> null
                    }
                )
            } else {
                // Inclusão nova mantém PENDENTE (default do modelo) e não tem diasConcluidos
                Etapa(
                    id = "",
                    titulo = titulo,
                    descricao = binding.etDescEtapa.text.toString().trim(),
                    funcionarios = selecionadosAtual
                        .toList()
                        .sortedBy { it.lowercase(Locale.getDefault()) }
                        .joinToString(", ")
                        .ifBlank { null },
                    dataInicio = dataIni,
                    dataFim = dataFim,
                    // status: usa default PENDENTE do data class
                    responsavelTipo = when (responsavelSelecionado) {
                        RespTipo.FUNCIONARIOS -> "FUNCIONARIOS"
                        RespTipo.EMPRESA -> "EMPRESA"
                        else -> null
                    },
                    empresaNome = when (responsavelSelecionado) {
                        RespTipo.EMPRESA -> binding.etEmpresa.text?.toString()?.trim().orEmpty()
                        else -> null
                    },
                    empresaValor = when (responsavelSelecionado) {
                        RespTipo.EMPRESA -> empresaValorParsed
                        else -> null
                    }
                )
            }

        shouldCloseAfterSave = true
        isSaving = true
        progress(true)

        if (isEdit) viewModel.updateEtapa(etapa) else viewModel.addEtapa(etapa)
    }

    private fun showError(@androidx.annotation.StringRes res: Int) {
        showSnackbarFragment(
            Constants.SnackType.ERROR.name,
            getString(R.string.snack_error),
            getString(res),
            getString(R.string.snack_button_ok)
        )
    }

    private fun setupValidationWatchersOnce() = with(binding) {
        if (watchersSet) return
        watchersSet = true

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (dataLoaded) validateForm()
            }

            override fun afterTextChanged(s: Editable?) {}
        }

        etTituloEtapa.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!dataLoaded) return

                // Se estava bloqueado por duplicado, desbloqueia assim que o título mudar
                if (blockSaveDueToDuplicate) {
                    val currentKey = titleKey(s?.toString())
                    if (currentKey != lastDuplicateTitleKey) {
                        blockSaveDueToDuplicate = false
                        lastDuplicateTitleKey = null
                    }
                }
                validateForm()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        etEmpresa.addTextChangedListener(watcher) // validação dinâmica do nome da empresa
        // Valor Empresa (opcional, mas sem permitir negativo)
        etEmpresaValor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!dataLoaded) return
                // se negativo, limpar silenciosamente
                parseEmpresaValorOrNull(s?.toString()) {
                    etEmpresaValor.setText("")
                }
                validateForm()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        etDataInicioEtapa.addTextChangedListener(watcher)
        etDataFimEtapa.addTextChangedListener(watcher)
    }

    private fun validateForm() = with(binding) {
        // Guard anti-"flash": não valide antes de os dados estarem carregados
        if (!dataLoaded) {
            btnSaveEtapa.isEnabled = false
            clearErrors()
            return@with
        }

        val titulo = etTituloEtapa.text?.toString()?.trim().orEmpty()
        val iniStr = etDataInicioEtapa.text?.toString().orEmpty()
        val fimStr = etDataFimEtapa.text?.toString().orEmpty()

        val tituloOk = titulo.isNotEmpty()
        val iniOk = iniStr.isNotBlank()
        val fimOk = fimStr.isNotBlank()
        val ordemOk = if (iniOk && fimOk) isDateOrderValid(iniStr, fimStr) else false

        // ---- Título: usar TextView externo ----
        tvTituloError.isVisible = !tituloOk
        if (!tituloOk) tvTituloError.text = getString(R.string.etapa_error_title_required)
        // anima só quando mudar de estado
        val tituloChanged = lastTituloErrorVisible != tvTituloError.isVisible
        lastTituloErrorVisible = tvTituloError.isVisible
        // ajusta a margem da Descrição conforme erro visível/oculto
        adjustSpacingAfterView(
            tvTituloError,
            tilDescricaoCronograma,
            visibleTopDp = 8,
            goneTopDp = 12,
            animate = tituloChanged
        )
        // evita “caption” interno
        tilTituloEtapa.error = null

        // ---- Data Início: usar TextView externo ----
        tvDataInicioError.isVisible = !iniOk
        if (!iniOk) tvDataInicioError.text = getString(R.string.etapa_error_date_start_required)
        val iniChanged = lastDataInicioErrorVisible != tvDataInicioError.isVisible
        lastDataInicioErrorVisible = tvDataInicioError.isVisible
        // ajusta margem de Data Fim conforme erro de Data Início
        adjustSpacingAfterView(
            tvDataInicioError,
            tilDataFimEtapa,
            visibleTopDp = 22,
            goneTopDp = 12,
            animate = iniChanged
        )
        // evita “caption” interno
        tilDataInicioEtapa.error = null

        // ---- Data Fim: usar TextView externo ----
        val showFimError = when {
            !fimOk -> true
            iniOk && fimOk && !ordemOk -> true
            else -> false
        }
        tvDataFimError.isVisible = showFimError
        if (!fimOk) {
            tvDataFimError.text = getString(R.string.etapa_error_date_end_required)
        } else if (iniOk && !ordemOk) {
            tvDataFimError.text = getString(R.string.etapa_error_date_order)
        }
        val fimChanged = lastDataFimErrorVisible != tvDataFimError.isVisible
        lastDataFimErrorVisible = tvDataFimError.isVisible
        // ajusta margem do título "Status" conforme erro de Data Fim
        adjustSpacingAfterView(
            tvDataFimError,
            tvStatusTitle,
            visibleTopDp = 22,
            goneTopDp = 10,
            animate = fimChanged
        )
        // evita “caption” interno
        tilDataFimEtapa.error = null

        // Responsável (obrigatório)
        val respOk = responsavelSelecionado != null
        tvResponsavelError.isVisible = !respOk
        if (!respOk) tvResponsavelError.text = getString(R.string.etapa_error_responsavel_required)
        val respChanged = lastRespErrorVisible != tvResponsavelError.isVisible
        lastRespErrorVisible = tvResponsavelError.isVisible
        // ajustar espaçamento do próximo bloco (tilFuncionarios ou tilEmpresa) conforme erro
        adjustSpacingAfterView(
            tvResponsavelError,
            if (responsavelSelecionado == RespTipo.EMPRESA) tilEmpresa else tilFuncionarios,
            visibleTopDp = 8,
            goneTopDp = 12,
            animate = respChanged
        )

        // Se Empresa → campo obrigatório 2..20
        var empresaOk = true
        if (responsavelSelecionado == RespTipo.EMPRESA) {
            val nome = etEmpresa.text?.toString()?.trim().orEmpty()
            empresaOk = nome.isNotEmpty()
            tvEmpresaError.isVisible = !empresaOk
            if (!empresaOk) tvEmpresaError.text = getString(R.string.etapa_error_empresa_required)

            val sizeOk = nome.length in 2..20
            if (empresaOk && !sizeOk) {
                tvEmpresaError.isVisible = true
                tvEmpresaError.text = getString(R.string.etapa_error_empresa_size)
            }
            empresaOk = empresaOk && sizeOk

            val empChanged = lastEmpresaErrorVisible != tvEmpresaError.isVisible
            lastEmpresaErrorVisible = tvEmpresaError.isVisible
            // Ajusta margem antes do bloco Data Início
            adjustSpacingAfterView(
                tvEmpresaError,
                tilDataInicioEtapa,
                visibleTopDp = 8,
                goneTopDp = 24,
                animate = empChanged
            )
            // evita caption interno
            tilEmpresa.error = null
        } else {
            // quando não é empresa, apagar erro/estado visual
            tvEmpresaError.isVisible = false
            tilEmpresa.error = null
        }

        // habilitação do botão
        val baseOk = tituloOk && iniOk && fimOk && ordemOk && respOk &&
                (responsavelSelecionado != RespTipo.EMPRESA || empresaOk)

        if (!isSaving && !shouldCloseAfterSave) {
            // Se o usuário tentou salvar com duplicado, fica bloqueado
            // até alterar o título (o watcher do título libera o bloqueio)
            val enabledFinal = baseOk && !blockSaveDueToDuplicate
            btnSaveEtapa.isEnabled = enabledFinal
        }
    }

    private fun parseDateOrNull(s: String?): Date? = try {
        if (s.isNullOrBlank()) null else sdf.parse(s)
    } catch (_: ParseException) {
        null
    }

    private fun isDateOrderValid(dataInicio: String?, dataFim: String?): Boolean {
        val ini = parseDateOrNull(dataInicio) ?: return false
        val fim = parseDateOrNull(dataFim) ?: return false
        return !fim.before(ini)
    }

    // ---------------- Verificação de Edição -----------------

    private fun handleBackPress() {
        if (hasUnsavedChanges()) {
            showSnackbarFragment(
                type = Constants.SnackType.WARNING.name,
                title = getString(R.string.snack_attention),
                msg = getString(R.string.unsaved_confirm_msg),
                btnText = getString(R.string.snack_button_yes),
                onAction = { navigateUpReturningToCallerAndRestartGanttIfNeeded() },
                btnNegativeText = getString(R.string.snack_button_no),
                onNegative = { /* permanece nesta tela */ }
            )
        } else {
            navigateUpReturningToCallerAndRestartGanttIfNeeded()
        }
    }

    @Suppress("KotlinConstantConditions")
    private fun hasUnsavedChanges(): Boolean = with(binding) {
        val titulo = etTituloEtapa.text?.toString()?.trim().orEmpty()
        val desc = etDescEtapa.text?.toString()?.trim().orEmpty()
        val ini = etDataInicioEtapa.text?.toString()?.trim().orEmpty()
        val fim = etDataFimEtapa.text?.toString()?.trim().orEmpty()

        val respAtual: String? = when {
            rbRespFuncs.isChecked -> "FUNCIONARIOS"
            rbRespEmpresa.isChecked -> "EMPRESA"
            else -> null
        }
        val empresaAtual = etEmpresa.text?.toString()?.trim().orEmpty()

        // ✅ use o mesmo parser do onSaveClicked
        val empresaValorAtual: Double? =
            parseEmpresaValorOrNull(etEmpresaValor.text?.toString())

        if (!isEdit) {
            return@with titulo.isNotEmpty()
                    || desc.isNotEmpty()
                    || ini.isNotEmpty()
                    || fim.isNotEmpty()
                    || selecionadosAtual.isNotEmpty()
                    || respAtual != null
                    || (respAtual == "EMPRESA" && (
                    empresaAtual.isNotEmpty() || empresaValorAtual != null
                    ))
        }

        val orig = etapaOriginal ?: return@with false
        val nomesOrig = parseCsvNomes(orig.funcionarios)
        val respOrig = orig.responsavelTipo
        val empresaOrig = orig.empresaNome.orEmpty()
        val empresaValorOrig = orig.empresaValor   // Double?

        var alterou = false
        alterou = alterou || (titulo != orig.titulo)
        alterou = alterou || (desc != (orig.descricao ?: ""))
        alterou = alterou || (ini != orig.dataInicio)
        alterou = alterou || (fim != orig.dataFim)
        alterou = alterou || (selecionadosAtual != nomesOrig)

        alterou = alterou || (respAtual != respOrig)
        if (respAtual == "EMPRESA") {
            alterou = alterou || (empresaAtual != empresaOrig)
            val changedValor = when {
                empresaValorOrig == null && empresaValorAtual == null -> false
                empresaValorOrig == null && empresaValorAtual != null -> true
                empresaValorOrig != null && empresaValorAtual == null -> true
                else -> kotlin.math.abs(empresaValorOrig!! - empresaValorAtual!!) > 1e-6
            }
            alterou = alterou || changedValor
        }

        return@with alterou
    }

    /*──────────── Progress control (spinner + UX) ───────────*/
    private fun progress(show: Boolean) = with(binding) {
        val saving = show && isSaving
        cronRegScroll.isEnabled = !saving
        btnSaveEtapa.isEnabled = if (saving) false else !shouldCloseAfterSave

        progressSaveEtapa.isVisible = saving

        if (saving) {
            requireActivity().currentFocus?.clearFocus()
            root.clearFocus()
            cronRegScroll.isFocusableInTouchMode = true
            cronRegScroll.requestFocus()
            root.hideKeyboard()
            progressSaveEtapa.post {
                cronRegScroll.smoothScrollTo(0, progressSaveEtapa.bottom)
            }
        }
    }

    /** Configura o MaterialAlertDialogBuilder */
    private fun setupFuncionariosDropdown() = with(binding) {
        val dd = dropdownFuncionarios

        // Campo “inerte”: não mostra/usa dropdown nativo, só serve para abrir o picker
        dd.setText("", false)
        dd.inputType = android.text.InputType.TYPE_NULL
        dd.isCursorVisible = false
        dd.keyListener = null
        dd.setOnClickListener { openFuncionariosPicker() }
        tilFuncionarios.setEndIconOnClickListener { openFuncionariosPicker() }
    }

    private fun openFuncionariosPicker() {
        // 1) Ativos
        val ativos = funcionariosAll.filter { it.status.equals("Ativo", ignoreCase = true) }

        // 2) Inativos que JÁ estavam selecionados na etapa (para poder desmarcar)
        val inativosSelecionados = funcionariosAll.filter { func ->
            !func.status.equals("Ativo", ignoreCase = true) &&
                    selecionadosAtual.contains(func.nome)
        }

        // 3) Conjunto final “visível” no diálogo
        val visiveis = (ativos + inativosSelecionados)
            .distinctBy { it.id } // segurança
            .sortedWith(
                compareBy(
                    { it.funcao.lowercase(Locale.getDefault()) },
                    { it.nome.lowercase(Locale.getDefault()) }
                ))

        // Se não há NINGUÉM para mostrar, manter seu comportamento:
        if (visiveis.isEmpty()) {
            // aqui você já mostra o snackbar "Sem funcionários cadastrados" e retorna
            showSnackbarFragment(
                type = Constants.SnackType.WARNING.name,
                title = getString(R.string.snack_attention),
                msg = getString(R.string.cron_func_no_employees),
                btnText = getString(R.string.snack_button_ok)
            )
            return
        }

        // Versão AGRUPADA do diálogo (função nova)
        showFuncionariosDialogGrouped(visiveis)
    }

    private fun showFuncionariosDialogGrouped(lista: List<Funcionario>) {
        // Agrupa por função (apenas com quem está “visível”: ativos + inativos selecionados)
        val grupos = lista
            .groupBy { it.funcao.trim() }
            .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

        // Constrói um layout custom no Dialog (ScrollView -> LinearLayout vertical)
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 16)
        }
        val scroll = ScrollView(requireContext()).apply {
            isFillViewport = true
            addView(container)
        }

        // Cabeçalho customizado (mantém seu título existente)
        val titleView = TextView(requireContext()).apply {
            text = getString(R.string.cron_func_material_title) // "Selecione os Funcionários:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(48, 32, 48, 8)
        }

        // Para cada função (ordenadas), adiciona header + checkboxes dessa função (ordenados por nome)
        val roleKeys = grupos.keys.toList()
        roleKeys.forEachIndexed { indexRole, role ->
            // Header sublinhado da função
            val header = TextView(requireContext()).apply {
                // texto via string resource (evita concatenação manual)
                text = getString(R.string.cron_func_role_header_underline, role)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                paint.isUnderlineText = true
                setPadding(0, if (indexRole == 0) 4 else 16, 0, 6)
            }
            container.addView(header)

            // Pessoas dessa função, ordenadas por nome (case-insensitive)
            val pessoas = grupos[role]!!.sortedBy { it.nome.lowercase(Locale.getDefault()) }

            pessoas.forEach { f ->
                val check =
                    MaterialCheckBox(requireContext()).apply {
                        val salarioFmt = formatMoneyBr(f.salario)
                        val pagamento = f.formaPagamento.ifBlank { "-" }

                        // NÃO concatenar: usa string com placeholders
                        text = getString(
                            R.string.cron_func_item_line,  // "%1$s (%2$s / %3$s)"
                            f.nome,
                            salarioFmt,
                            pagamento.lowercase(Locale.getDefault())
                        )

                        isChecked = selecionadosAtual.contains(f.nome)
                        setPadding(0, 4, 0, 4)
                        setOnCheckedChangeListener { _, marcado ->
                            if (marcado) selecionadosAtual.add(f.nome) else selecionadosAtual.remove(
                                f.nome
                            )
                        }
                    }
                container.addView(check)
            }

            // Divider de largura total entre grupos (exceto no último)
            if (indexRole < roleKeys.lastIndex) {
                val divider = View(requireContext()).apply {
                    setBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(), R.color.md_theme_light_outline
                        )
                    )
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (resources.displayMetrics.density).toInt() // 1dp
                    )
                }
                container.addView(divider)
            }
        }

        // Constrói e mostra o diálogo
        val dlg = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_ObrasApp_FuncDialog
        )
            .setCustomTitle(titleView)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                renderFuncionariosSelecionados() // mantém seu resumo abaixo do campo
            }
            .setNegativeButton(R.string.generic_cancel, null)
            .create()

        isFuncPickerOpen = true
        dlg.setOnDismissListener { isFuncPickerOpen = false }
        dlg.show()
    }

    // Helper de formatação monetária (BR) — coloque no mesmo arquivo
    private fun formatMoneyBr(valor: Double): String {
        val nf = java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        return nf.format(valor)
    }


    /** Atualiza o TextView abaixo do MaterialAlertDialogBuilder com os nomes selecionados (singular/plural). */
    private fun renderFuncionariosSelecionados() = with(binding) {
        val tv = tvFuncionariosSelecionados
        val qtd = selecionadosAtual.size
        if (qtd == 0) {
            tv.visibility = View.GONE
            tv.text = ""
            return@with
        }

        val nomesOrdenados = selecionadosAtual
            .toList()
            .sortedBy { it.lowercase(Locale.getDefault()) }

        // Monta lista com "e" antes do último nome
        val lista = when (nomesOrdenados.size) {
            1 -> nomesOrdenados[0]
            2 -> nomesOrdenados.joinToString(" e ")
            else ->
                nomesOrdenados.dropLast(1).joinToString(", ") +
                        " e " + nomesOrdenados.last()
        }

        // Monta string completa a partir do plural
        val textoCompleto = resources.getQuantityString(
            R.plurals.cron_func_selected_label,
            qtd,
            lista
        )

        val spannable = SpannableString(textoCompleto)

        // Deixa prefixo em negrito
        val prefixoFim = textoCompleto.indexOf(lista).takeIf { it > 0 } ?: textoCompleto.length
        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            prefixoFim,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Cor para os nomes
        val corNomes = ContextCompat.getColor(root.context, R.color.btn_text_success)
        spannable.setSpan(
            ForegroundColorSpan(corNomes),
            prefixoFim,
            textoCompleto.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        tv.visibility = View.VISIBLE
        tv.text = spannable
    }

    /** Converte CSV "João, Maria" -> Set("João","Maria"), tolerante a null/strings vazias. */
    private fun parseCsvNomes(csv: String?): LinkedHashSet<String> {
        if (csv.isNullOrBlank()) return linkedSetOf()
        return csv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toCollection(linkedSetOf())
    }

    /** Converte "1.234,56", "1,234.56", "1234,56", "1234.56" e até "50.050.50" em Double (>= 0).
     *  Se negativo for digitado (ex.: colar), limpa o campo silenciosamente e retorna null. */
    private fun parseEmpresaValorOrNull(raw: String?, onNegative: (() -> Unit)? = null): Double? {
        val s0 = raw?.trim().orEmpty()
        if (s0.isBlank()) return null

        // remove espaços visíveis/invisíveis
        val s = s0.replace(Regex("\\s"), "")

        val hasComma = s.indexOf(',') >= 0
        val hasDot = s.indexOf('.') >= 0

        val normalized = when {
            // Tem vírgula E ponto -> BR clássico "1.234,56"
            hasComma && hasDot -> s.replace(".", "").replace(',', '.')

            // Só vírgulas (pode ter várias): último é decimal, demais são milhar
            hasComma && !hasDot -> {
                val last = s.lastIndexOf(',')
                buildString(s.length) {
                    s.forEachIndexed { i, ch ->
                        when (ch) {
                            ',' -> if (i == last) append('.') // decimal
                            else { /* descarta vírgula de milhar */
                            }

                            else -> append(ch)
                        }
                    }
                }
            }

            // Só pontos (pode ter vários): último é decimal, demais são milhar
            !hasComma && hasDot -> {
                val last = s.lastIndexOf('.')
                buildString(s.length) {
                    s.forEachIndexed { i, ch ->
                        when (ch) {
                            '.' -> if (i == last) append('.') // decimal
                            else { /* descarta ponto de milhar */
                            }

                            else -> append(ch)
                        }
                    }
                }
            }

            // Só dígitos (sem separador)
            else -> s
        }

        val v = normalized.toDoubleOrNull() ?: return null
        if (v < 0.0) {
            onNegative?.invoke() // regra: não permitir negativo
            return null
        }
        return v
    }

    // ------------- Helpers de espaçamento -------------

    // dp helper
    private fun Int.dp(): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun adjustSpacingAfterView(
        precedingView: View,
        nextView: View,
        visibleTopDp: Int,
        goneTopDp: Int,
        animate: Boolean = true
    ) {
        val parent = nextView.parent as? ViewGroup ?: return

        // encerra transições pendentes para evitar “pulos”
        TransitionManager.endTransitions(parent)

        if (animate) {
            TransitionManager.beginDelayedTransition(
                parent,
                AutoTransition().apply { duration = 150 }
            )
        }

        (nextView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            val newTop = if (precedingView.isVisible) visibleTopDp.dp() else goneTopDp.dp()
            if (lp.topMargin != newTop) {
                lp.topMargin = newTop
                nextView.layoutParams = lp
                parent.requestLayout()
                parent.invalidate()
                nextView.requestLayout()
                nextView.invalidate()
            }
        }
    }

    // Limpa erros (para evitar "flash" antes de dataLoaded=true)
    private fun clearErrors() = with(binding) {
        // zera erros em TILs (não usamos, mas garante “limpo”)
        tilTituloEtapa.error = null
        tilDataInicioEtapa.error = null
        tilDataFimEtapa.error = null

        // esconde TextViews de erro
        tvTituloError.isVisible = false
        tvDataInicioError.isVisible = false
        tvDataFimError.isVisible = false
    }

    // Navegação segura de volta para o CronogramaGanttFragment
    private fun requestGanttRestartIfPresent() {
        val nav = findNavController()
        runCatching {
            nav.getBackStackEntry(R.id.cronogramaGanttFragment)
                .savedStateHandle["RESTART_GANTT"] = true
        }.onFailure {
            // Não faz nada (volta normal para o CronogramaFragment).
        }
    }

    // use SEMPRE para sair do Register
    private fun navigateUpReturningToCallerAndRestartGanttIfNeeded() {
        requestGanttRestartIfPresent()          // sinaliza restart do Gantt somente se ele existir na pilha
        findNavController().navigateUp()        // volta para quem chamou (Gantt ou Cronograma)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}