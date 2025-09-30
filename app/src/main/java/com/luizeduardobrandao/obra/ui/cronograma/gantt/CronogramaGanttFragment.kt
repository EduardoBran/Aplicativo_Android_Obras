package com.luizeduardobrandao.obra.ui.cronograma.gantt

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Etapa
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentCronogramaGanttBinding
import com.luizeduardobrandao.obra.ui.cronograma.CronogramaViewModel
import com.luizeduardobrandao.obra.ui.cronograma.gantt.adapter.GanttRowAdapter
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.funcionario.FuncionarioViewModel
import com.luizeduardobrandao.obra.ui.resumo.ResumoViewModel
import com.luizeduardobrandao.obra.utils.GanttUtils
import com.luizeduardobrandao.obra.utils.savePdfToDownloads
import com.luizeduardobrandao.obra.ui.snackbar.SnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.syncEndInsetSymmetric
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import java.time.LocalDate
import androidx.core.graphics.drawable.toDrawable

@AndroidEntryPoint
class CronogramaGanttFragment : Fragment() {

    private var _binding: FragmentCronogramaGanttBinding? = null
    private val binding get() = _binding!!

    private val args: CronogramaGanttFragmentArgs by navArgs()
    private val viewModel: CronogramaViewModel by viewModels()

    private lateinit var adapter: GanttRowAdapter

    private val viewModelFuncionario: FuncionarioViewModel by viewModels()
    private var funcionariosCache: List<Funcionario> = emptyList()

    // Cabeçalho/timeline global (mínimo comum entre todas as etapas carregadas)
    private var headerDays: List<LocalDate> = emptyList()

    private var currentEtapas: List<Etapa> = emptyList()

    // VM só para obter saldo/aportes (reaproveita a lógica do Resumo)
    private val resumoViewModel: ResumoViewModel by viewModels()

    // Cache p/ resumo do rodapé
    private var saldoInicialObra: Double = 0.0
    private var totalAportesObra: Double = 0.0
    private var hasAportes: Boolean = false

    // Controle de padding dinâmico do Recycler sob o card fixo
    private var baseRvPaddingBottom: Int = 0
    private var footerGlobalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    // Estado Popup Informação e Aba Expansível
    private var popupIntegralVisible = false
    private var popupSemDuplaVisible = false
    private var popupSaldoVisible = false
    private var resumoExpanded = false

    // Manter referência dos popups (para fechar ao colapsar a aba)
    private var popupIntegral: PopupWindow? = null
    private var popupSemDupla: PopupWindow? = null
    private var popupSaldo: PopupWindow? = null

    // Evitar que a inicialização padrão da aba sobrescreva o estado restaurado
    private var stateRestored = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCronogramaGanttBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        // toolbar
        toolbarGantt.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        toolbarGantt.title = getString(R.string.gantt_title)

        // aplica a simetria de padding depois de o menu estar pronto
        toolbarGantt.syncEndInsetSymmetric()

        // Botão de download (actionView) -> popup com PDF
        val menuItem = toolbarGantt.menu.findItem(R.id.action_gantt_export)
        val anchor = menuItem.actionView?.findViewById<View>(R.id.btnDownloadMenu)

        anchor?.setOnClickListener {
            val themed = ContextThemeWrapper(requireContext(), R.style.PopupMenu_WhiteBg_BlackText)
            val popup = PopupMenu(themed, anchor, Gravity.END).apply {
                menuInflater.inflate(R.menu.menu_gantt_export_popup, menu)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {

                        R.id.action_export_pdf -> {
                            askSavePdf()
                            true
                        }

                        else -> false
                    }
                }
            }
            popup.show()
        }

        // ----- Resumo Cronogramas (aba fixa) -----
        // Colapsada por padrão; alterna visibilidade com animação de transição leve
        setupExpandableFooter(
            header = binding.headerResumoGantt,
            content = binding.contentResumoGantt,
            arrow = binding.ivArrowResumoGantt,
            startCollapsed = true
        )

        // ▼▼ Listeners dos ícones de informação (mini-card ancorado) ▼▼
        ivInfoIntegral.setOnClickListener { a ->
            if (!popupIntegralVisible) {
                popupIntegral = showInfoPopup(
                    anchor = a,
                    text = getString(R.string.crg_footer_valor_total_integral_exp)
                ) {
                    popupIntegralVisible = false
                    popupIntegral = null
                }
                popupIntegralVisible = true
            }
        }

        ivInfoSemDupla.setOnClickListener { a ->
            if (!popupSemDuplaVisible) {
                popupSemDupla = showInfoPopup(
                    anchor = a,
                    text = getString(R.string.crg_footer_valor_total_sem_dupla_exp)
                ) {
                    popupSemDuplaVisible = false
                    popupSemDupla = null
                }
                popupSemDuplaVisible = true
            }
        }

        ivInfoSaldo.setOnClickListener { a ->
            if (!popupSaldoVisible) {
                popupSaldo = showInfoPopup(
                    anchor = a,
                    text = getString(R.string.crg_footer_saldo_restante_exp)
                ) {
                    popupSaldoVisible = false
                    popupSaldo = null
                }
                popupSaldoVisible = true
            }
        }

        // recycler
        rvGantt.layoutManager = LinearLayoutManager(requireContext())
        adapter = GanttRowAdapter(
            onToggleDay = { etapa, newSetUtc ->
                // Persistir alteração via ViewModel (recalcula % + status)
                viewModel.commitDias(etapa, newSetUtc)
            },
            requestHeaderDays = { headerDays },
            getFuncionarios = { funcionariosCache } // << NOVO
        )

        // aqui você conecta o callback ANTES de setar o adapter no RecyclerView
        adapter.onFirstLeftWidth = { leftWidth ->
            val row = binding.headerRow
            val inset = resources.getDimensionPixelSize(R.dimen.gantt_header_start_inset)
            val startGap = resources.getDimensionPixelSize(R.dimen.gantt_first_cell_margin_start)
            row.setPadding(
                leftWidth + inset + startGap,
                row.paddingTop,
                row.paddingRight,
                row.paddingBottom
            )
        }

        rvGantt.adapter = adapter

        // Guarda o padding original do Recycler (para somar com a altura do footer)
        baseRvPaddingBottom = rvGantt.paddingBottom

        // Observa mudanças de tamanho do card (expande/colapsa) para atualizar o padding do Recycler
        footerGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            updateRecyclerBottomInsetForFooter()
        }
        binding.cardResumoGantt.viewTreeObserver.addOnGlobalLayoutListener(
            footerGlobalLayoutListener
        )

        // Carrega e observa funcionários para cálculo de valores
        viewModelFuncionario.loadFuncionarios()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModelFuncionario.state.collect { ui ->
                    when (ui) {
                        is UiState.Success -> {
                            funcionariosCache = ui.data
                            // Rebind para recalcular os valores quando a lista mudar
                            adapter.notifyDataSetChanged()
                        }

                        else -> Unit
                    }
                }
            }
        }

        // carrega as etapas
        collectState()

        // Observa saldo inicial + aportes para preencher o rodapé
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                resumoViewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Success -> {
                            val data = ui.data
                            saldoInicialObra = data.saldoInicial
                            totalAportesObra = data.totalAportes
                            hasAportes = data.aportes.isNotEmpty()
                            updateResumoFooter() // recalcula textos quando chegarem os dados financeiros
                        }

                        else -> Unit
                    }
                }
            }
        }

        // dispara primeira carga (se ainda não)
        viewModel.loadEtapas()

        // Garante nested scrolling ativado sem risco de NPE na inflação
        binding.contentResumoGantt.isNestedScrollingEnabled = true
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun collectState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Combine obra + etapas para controlarmos um único "loading"
                kotlinx.coroutines.flow.combine(
                    viewModel.obraState,
                    viewModel.state
                ) { obra, etapasUi -> obra to etapasUi }
                    .collect { (obra, etapasUi) ->

                        // 1) Monte/valide o cabeçalho da obra
                        val ini = GanttUtils.brToLocalDateOrNull(obra?.dataInicio)
                        val fim = GanttUtils.brToLocalDateOrNull(obra?.dataFim)
                        val hasHeader = (ini != null && fim != null && !fim.isBefore(ini))
                        headerDays =
                            if (hasHeader) GanttUtils.daysBetween(ini!!, fim!!) else emptyList()

                        // 2) Estado da lista de etapas
                        when (etapasUi) {
                            is UiState.Loading -> {
                                // Enquanto qualquer lado não estiver pronto → loading
                                renderLoading(true)
                                return@collect
                            }

                            is UiState.ErrorRes -> {
                                // Erro: esconde conteúdo e mostra mensagem
                                renderLoading(false)
                                binding.headerContainer.isVisible = false
                                binding.rvGantt.isVisible = false
                                binding.textEmpty.isVisible = true
                                binding.textEmpty.setText(etapasUi.resId)
                                return@collect
                            }

                            is UiState.Success -> {
                                // Ordene por data de início (mais recente primeiro). Datas inválidas vão para o fim.
                                val lista = etapasUi.data
                                    .sortedBy {
                                        GanttUtils.brToLocalDateOrNull(it.dataInicio)?.toEpochDay()
                                            ?: Long.MAX_VALUE
                                    }

                                currentEtapas = lista // << guardar para exportações

                                // Se o header NÃO estiver pronto ainda, aguarde (loading)
                                if (!hasHeader) {
                                    renderLoading(true)
                                    return@collect
                                }

                                // 3) Já temos header + etapas → renderiza TUDO de uma vez
                                buildHeaderViews() // monta as datas do topo (sem mexer em visibilidade)
                                adapter.submitList(lista) {
                                    // Mostrar tudo só depois que a lista aplicar o diff
                                    renderLoading(false)
                                    binding.textEmpty.isVisible = lista.isEmpty()
                                    if (lista.isEmpty()) binding.textEmpty.setText(R.string.gantt_empty)

                                    binding.headerContainer.isVisible = lista.isNotEmpty()
                                    binding.rvGantt.isVisible = lista.isNotEmpty()

                                    binding.headerContainer.isVisible = lista.isNotEmpty()
                                    binding.rvGantt.isVisible = lista.isNotEmpty()

                                    // >>> Recalcula o resumo do rodapé sempre que as etapas mudarem
                                    updateResumoFooter()
                                }
                            }

                            else -> Unit
                        }
                    }
            }
        }
    }

    private fun renderLoading(show: Boolean) = with(binding) {
        progressGantt.isVisible = show
        // Conteúdo some enquanto carrega
        rvGantt.isVisible = !show
        headerContainer.isVisible = !show
        textEmpty.isVisible = false
    }

    private fun buildHeaderViews() = with(binding) {
        headerRow.removeAllViews()
        if (headerDays.isEmpty()) return@with

        val inflater = LayoutInflater.from(root.context)
        headerDays.forEach { d ->
            val tv = inflater.inflate(R.layout.item_gantt_header_day, headerRow, false) as ViewGroup
            val label = tv.findViewById<TextView>(R.id.tvDayLabel)
            label.text = if (GanttUtils.isSunday(d))
                getString(R.string.gantt_sunday_short)      // "D"
            else
                GanttUtils.formatDayForHeader(d)
            if (GanttUtils.isSunday(d)) {
                val big = resources.getDimension(R.dimen.gantt_day_text_size_sunday)
                label.setTextSize(TypedValue.COMPLEX_UNIT_PX, big)
            } else {
                val normal = resources.getDimension(R.dimen.gantt_day_text_size)
                label.setTextSize(TypedValue.COMPLEX_UNIT_PX, normal)
            }
            headerRow.addView(tv)
        }
        adapter.attachHeaderScroll(binding.headerScroll)
    }

    /** Mesmo cálculo já usado no Adapter: NÃO conta domingos. */
    private fun computeValorTotal(etapa: Etapa): Double? {
        if (etapa.responsavelTipo == "EMPRESA") return etapa.empresaValor

        val nomes = parseCsvNomes(etapa.funcionarios)
        if (etapa.responsavelTipo != "FUNCIONARIOS" || nomes.isEmpty()) return null

        val ini = GanttUtils.brToLocalDateOrNull(etapa.dataInicio)
        val fim = GanttUtils.brToLocalDateOrNull(etapa.dataFim)
        if (ini == null || fim == null || fim.isBefore(ini)) return null

        val diasUteis = GanttUtils.daysBetween(ini, fim).count { !GanttUtils.isSunday(it) }
        if (diasUteis <= 0) return 0.0
        if (funcionariosCache.isEmpty()) return null

        var total = 0.0
        nomes.forEach { nomeSel ->
            val f =
                funcionariosCache.firstOrNull { it.nome.trim().equals(nomeSel, ignoreCase = true) }
                    ?: return@forEach
            val salario = f.salario.coerceAtLeast(0.0)
            val tipo = f.formaPagamento.trim().lowercase(Locale.getDefault())
            total += when {
                tipo.contains("diária") ||
                        tipo.contains("diaria") ||
                        tipo.contains("Diária") ||
                        tipo.contains("Diaria") -> diasUteis * salario

                tipo.contains("semanal") ||
                        tipo.contains("Semanal") ->
                    kotlin.math.ceil(diasUteis / 7.0).toInt() * salario

                tipo.contains("mensal") ||
                        tipo.contains("Mensal") ->
                    kotlin.math.ceil(diasUteis / 30.0).toInt() * salario

                tipo.contains("tarefeiro") ||
                        tipo.contains("tarefa") ||
                        tipo.contains("Tarefeiro ") -> salario

                else -> 0.0
            }
        }
        return total
    }

    /**
     * Cenário A: Valor Total Integral
     * - Soma todos os valores das etapas como estão.
     * - Empresas: soma direta de empresaValor.
     * - Funcionários: soma todos os salários por etapa,
     *   sem deduplicar funcionários que trabalhem em mais de uma etapa no mesmo dia.
     */
    private fun computeValorTotalIntegral(
        etapas: List<Etapa>
    ): Double {
        var total = 0.0

        etapas.forEach { etapa ->
            when (etapa.responsavelTipo) {
                "EMPRESA" -> {
                    total += etapa.empresaValor ?: 0.0
                }

                "FUNCIONARIOS" -> {
                    val valorEtapa = computeValorTotal(etapa) ?: 0.0
                    total += valorEtapa
                }
            }
        }
        return total
    }

    /**
     * Cenário B: Valor Total sem dupla contagem
     * - Empresas: soma direta de empresaValor.
     * - Funcionários: considera apenas 1 salário por funcionário no mesmo dia,
     *   mesmo que ele esteja em mais de uma etapa no mesmo dia.
     */
    private fun computeValorTotalSemOverlapInclusiveEmpresas(
        etapas: List<Etapa>,
        funcionarios: List<Funcionario>
    ): Double {
        val totalEmpresas: Double = etapas
            .asSequence()
            .filter { it.responsavelTipo == "EMPRESA" }
            .mapNotNull { it.empresaValor }
            .sum()

        val etapasFunc = etapas.filter { it.responsavelTipo == "FUNCIONARIOS" }
        if (etapasFunc.isEmpty() || funcionarios.isEmpty()) return totalEmpresas

        // 1) Dias únicos por funcionário (sem domingo) E número de etapas por funcionário
        val diasPorFuncionario = mutableMapOf<String, MutableSet<LocalDate>>()
        val etapasPorFuncionario = mutableMapOf<String, Int>()

        etapasFunc.forEach { e ->
            val ini = GanttUtils.brToLocalDateOrNull(e.dataInicio)
            val fim = GanttUtils.brToLocalDateOrNull(e.dataFim)
            if (ini == null || fim == null || fim.isBefore(ini)) return@forEach

            val diasValidos = GanttUtils.daysBetween(ini, fim).filter { !GanttUtils.isSunday(it) }
            if (diasValidos.isEmpty()) return@forEach

            val nomes = parseCsvNomes(e.funcionarios)
            nomes.forEach { nome ->
                val key = nome.trim().lowercase(Locale.getDefault())
                val set = diasPorFuncionario.getOrPut(key) { mutableSetOf() }
                set.addAll(diasValidos)
                // conta 1 por etapa em que o funcionário aparece
                etapasPorFuncionario[key] = (etapasPorFuncionario[key] ?: 0) + 1
            }
        }

        // 2) Converte para quantidade a pagar conforme forma de pagamento
        var totalFuncs = 0.0
        val weekFields = java.time.temporal.WeekFields.ISO

        diasPorFuncionario.forEach { (nomeLower, diasUnicos) ->
            if (diasUnicos.isEmpty()) return@forEach

            val f = funcionarios.firstOrNull { it.nome.trim().equals(nomeLower, ignoreCase = true) }
                ?: funcionarios.firstOrNull {
                    it.nome.trim().lowercase(Locale.getDefault()) == nomeLower
                }
                ?: return@forEach

            val salario = f.salario.coerceAtLeast(0.0)
            val tipo = f.formaPagamento.trim().lowercase(Locale.getDefault())

            val qtd = when {
                // diária → 1 por DIA único
                tipo.contains("diária") ||
                        tipo.contains("diaria") ||
                        tipo.contains("Diária") ||
                        tipo.contains("Diaria") -> diasUnicos.size

                // semanal → 1 por semana ISO única (ano/semana)
                tipo.contains("semanal") || tipo.contains("Semanal") -> diasUnicos
                    .map { it.get(weekFields.weekBasedYear()) to it.get(weekFields.weekOfWeekBasedYear()) }
                    .toSet().size

                // mensal → 1 por (ano,mês) único
                tipo.contains("mensal") || tipo.contains("Mensal") -> diasUnicos
                    .map { it.year to it.monthValue }
                    .toSet().size

                // tarefeiro → sem sobrepor no mesmo dia, mas NÃO vira “por dia”
                // conta no máx. o nº de etapas em que ele aparece
                tipo.contains("tarefeiro") || tipo.contains("tarefa") ||
                        tipo.contains("Tarefeiro") -> {
                    val etapasCount = etapasPorFuncionario[nomeLower] ?: 0
                    kotlin.math.min(diasUnicos.size, etapasCount)
                }

                else -> 0
            }

            if (qtd > 0) totalFuncs += qtd * salario
        }

        return totalEmpresas + totalFuncs
    }

    /** Aba Expansível */
    @Suppress("SameParameterValue")
    private fun setupExpandableFooter(
        header: View,
        content: View,
        arrow: ImageView,
        startCollapsed: Boolean
    ) {
        fun apply(expanded: Boolean, animate: Boolean) {
            // estado global (salva/recupera em rotação)
            resumoExpanded = expanded

            if (animate) {
                val t = androidx.transition.AutoTransition().apply { duration = 180 }
                androidx.transition.TransitionManager.beginDelayedTransition(
                    binding.root as ViewGroup, t
                )
            }

            content.isVisible = expanded

            // se colapsar, feche quaisquer popups abertos
            if (!expanded) {
                popupIntegral?.dismiss()
                popupSemDupla?.dismiss()
                popupSaldo?.dismiss()
            }

            arrow.animate()
                .rotation(if (expanded) 180f else 0f)
                .setDuration(180)
                .start()

            // Ajusta o inset do Recycler após a mudança de altura
            content.post { updateRecyclerBottomInsetForFooter() }
        }

        // Estado inicial:
        // - se já restauramos de rotação, respeita resumoExpanded;
        // - caso contrário, usa o startCollapsed (expandido = !startCollapsed).
        if (stateRestored) {
            apply(expanded = resumoExpanded, animate = false)
        } else {
            apply(expanded = !startCollapsed, animate = false)
        }

        // Toggle ao tocar no cabeçalho
        header.setOnClickListener {
            val willExpand = !content.isVisible
            apply(willExpand, animate = true)
        }
    }

    // ——— Recalcula e preenche os textos do rodapé ———
    private fun updateResumoFooter() = with(binding) {
        // 1) Andamento da obra
        val avgPct: Int = if (currentEtapas.isEmpty()) {
            0
        } else {
            val sum = currentEtapas.sumOf { e ->
                GanttUtils.calcularProgresso(
                    e.diasConcluidos?.toSet() ?: emptySet(),
                    e.dataInicio,
                    e.dataFim
                )
            }
            kotlin.math.round(sum.toDouble() / currentEtapas.size).toInt()
        }
        tvAndamentoObraGantt.text = getString(R.string.crg_footer_status_value, avgPct)

        // 2) Financeiro: Saldo Inicial / Aportes
        tvSaldoInicialGantt.text = HtmlCompat.fromHtml(
            getString(R.string.crg_footer_saldo_inicial_mask, formatMoneyBR(saldoInicialObra)),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        val saldoComAportes: Double? = if (hasAportes) saldoInicialObra + totalAportesObra else null
        tvSaldoComAportesGantt.text = HtmlCompat.fromHtml(
            getString(
                R.string.crg_footer_saldo_aporte_mask,
                saldoComAportes?.let { formatMoneyBR(it) } ?: "-"
            ),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        // 3) Valor Total (Integral)  – soma como no Recycler (sem deduplicar)
        val valorIntegral = computeValorTotalIntegral(currentEtapas)
        tvValorTotalIntegralGantt.text = HtmlCompat.fromHtml(
            getString(R.string.crg_footer_valor_total_integral_mask, formatMoneyBR(valorIntegral)),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        // 4) Valor Total (Sem Duplicadas) – não duplica funcionário no mesmo dia
        val valorSemDupla =
            computeValorTotalSemOverlapInclusiveEmpresas(currentEtapas, funcionariosCache)
        tvValorTotalSemDuplaGantt.text = HtmlCompat.fromHtml(
            getString(R.string.crg_footer_valor_total_sem_dupla_mask, formatMoneyBR(valorSemDupla)),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        // 5) Saldo Restante = (Saldo Inicial + Aportes se houver) − Valor Total (Sem Duplicadas)
        val base = saldoComAportes ?: saldoInicialObra
        val saldoRestante = base - valorSemDupla
        tvSaldoRestanteGantt.text = getString(
            R.string.crg_footer_saldo_restante_mask,
            formatMoneyBR(saldoRestante)
        )

        val normalColor =
            ContextCompat.getColor(requireContext(), R.color.md_theme_light_onSurfaceVariant)
        val errorColor = ContextCompat.getColor(requireContext(), R.color.md_theme_light_error)
        tvSaldoRestanteGantt.setTextColor(if (saldoRestante < 0) errorColor else normalColor)

        binding.tvSaldoRestanteExpGantt.text = HtmlCompat.fromHtml(
            getString(R.string.crg_footer_saldo_restante_exp),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }

    /** Aplica o paddingBottom no Recycler igual à altura atual do card fixo do rodapé */
    private fun updateRecyclerBottomInsetForFooter() {
        // Se a view já foi destruída, simplesmente saia
        val b = _binding ?: return

        val lp = b.cardResumoGantt.layoutParams as ViewGroup.MarginLayoutParams
        val visibleFooterHeight =
            if (b.cardResumoGantt.isShown) b.cardResumoGantt.height + lp.bottomMargin else 0

        b.rvGantt.setPadding(
            b.rvGantt.paddingLeft,
            b.rvGantt.paddingTop,
            b.rvGantt.paddingRight,
            baseRvPaddingBottom + visibleFooterHeight
        )
    }

    /** Helper para abrir o popup */
    @SuppressLint("InflateParams")
    private fun showInfoPopup(anchor: View, text: String, onDismiss: () -> Unit): PopupWindow {
        val inflater = LayoutInflater.from(requireContext())
        val popupView = inflater.inflate(R.layout.layout_info_popup, null)

        val textView = popupView.findViewById<TextView>(R.id.tvInfoText)
        val closeBtn = popupView.findViewById<ImageView>(R.id.btnClose)

        // Renderiza <b> e <br/>
        textView.text = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY)

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 12f
            setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
            isOutsideTouchable = true
            isClippingEnabled = true // não deixa desenhar fora da tela
            setOnDismissListener { onDismiss() }
        }

        // botão X
        closeBtn.setOnClickListener { popupWindow.dismiss() }

        // --- MEDIR tamanho do popup para reposicionamento inteligente ---
        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popW = popupView.measuredWidth
        val popH = popupView.measuredHeight

        // Área visível da janela (exclui status bar / gesture area)
        val screen = android.graphics.Rect()
        requireActivity().window.decorView.getWindowVisibleDisplayFrame(screen)

        // Posição do anchor na tela
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val anchorLeft = loc[0]
        val anchorTop = loc[1]
        val anchorBottom = anchorTop + anchor.height

        // Posição "padrão": à direita e levemente acima
        var xOff = 16
        var yOff = -anchor.height

        // Coordenadas absolutas resultantes do showAsDropDown (rel. ao anchor)
        var desiredLeft = anchorLeft + xOff
        var desiredTop = anchorBottom + yOff

        // Se ultrapassar a borda direita, empurre para a esquerda
        val overflowRight = (desiredLeft + popW) - screen.right
        if (overflowRight > 0) {
            xOff -= (overflowRight + 16)
            desiredLeft -= (overflowRight + 16)
        }

        // Se ultrapassar a borda esquerda, puxe para dentro
        val overflowLeft = screen.left - desiredLeft
        if (overflowLeft > 0) {
            xOff += (overflowLeft + 16)
            desiredLeft += (overflowLeft + 16)
        }

        // Se ultrapassar a borda inferior, suba o popup
        val overflowBottom = (desiredTop + popH) - screen.bottom
        if (overflowBottom > 0) {
            yOff -= (overflowBottom + 16)
            desiredTop -= (overflowBottom + 16)
        }

        // Se ainda ficar acima do topo, prenda logo abaixo do header
        val overflowTop = screen.top - desiredTop
        if (overflowTop > 0) {
            // tenta exibir acima do anchor (tipo tooltip invertido)
            val tryAbove = popH + anchor.height + 16
            yOff += (overflowTop + tryAbove)
        }

        // Exibe ancorado, já com offsets corrigidos para caber na tela
        popupWindow.showAsDropDown(anchor, xOff, yOff, Gravity.START)
        return popupWindow
    }

    /** --- Helper de texto para PDF --- */
    private val nfBR: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    private fun formatMoneyBR(v: Double?): String =
        if (v == null) "-" else nfBR.format(v)

    private fun parseCsvNomes(csv: String?): List<String> =
        csv?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    /** Elipsa o texto para caber na largura especificada (em px). */
    private fun ellipsizeToWidth(text: String, paint: Paint, maxWidthPx: Float): String {
        if (text.isEmpty()) return ""
        if (paint.measureText(text) <= maxWidthPx) return text

        val ellipsis = "…"
        val ellW = paint.measureText(ellipsis)
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) > maxWidthPx - ellW) {
            end--
        }
        return if (end <= 0) ellipsis else text.substring(0, end) + ellipsis
    }

    /** Desenha texto elipsado para caber entre esta coluna e a próxima. */
    @Suppress("SameParameterValue")
    private fun drawCell(
        canvas: android.graphics.Canvas,
        paint: Paint,
        raw: String,
        colStartX: Int,
        nextColStartX: Int,
        paddingEndPx: Int = 8,
        baselineY: Float
    ) {
        val maxW = (nextColStartX - colStartX - paddingEndPx).coerceAtLeast(0)
        val safe = ellipsizeToWidth(raw, paint, maxW.toFloat())
        canvas.drawText(safe, colStartX.toFloat(), baselineY, paint)
    }

    /** Desenha texto até a borda direita (sem “próxima coluna”). */
    @Suppress("SameParameterValue")
    private fun drawCellToRightEdge(
        canvas: android.graphics.Canvas,
        paint: Paint,
        raw: String,
        colStartX: Int,
        rightEdgeX: Int,
        paddingEndPx: Int = 0,
        baselineY: Float
    ) {
        val maxW = (rightEdgeX - colStartX - paddingEndPx).coerceAtLeast(0)
        val safe = ellipsizeToWidth(raw, paint, maxW.toFloat())
        canvas.drawText(safe, colStartX.toFloat(), baselineY, paint)
    }

    /** Snackbar perguntando se deseja salvar */
    private fun askSavePdf() {
        showSnackbarFragment(
            type = Constants.SnackType.WARNING.name,
            title = getString(R.string.export_summary_snack_title),
            msg = getString(R.string.export_summary_snack_msg),
            btnText = getString(R.string.export_summary_snack_yes),
            onAction = { exportToPdf() },
            btnNegativeText = getString(R.string.export_summary_snack_no),
            onNegative = { /* fica na página */ }
        )
    }

    /** Desenha um parágrafo com quebra automática de linha até a borda direita.
     *  Retorna o novo Y (baseline) após a última linha desenhada.
     */
    @Suppress("SameParameterValue")
    private fun drawWrappedText(
        canvas: android.graphics.Canvas,
        paint: Paint,
        text: String,
        leftX: Int,
        rightX: Int,
        startY: Float,
        lineSpacingPx: Float = 4f
    ): Float {
        if (text.isBlank()) return startY
        val words = text.trim().split(Regex("\\s+"))
        val maxWidth = (rightX - leftX).toFloat()
        val fm = paint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent)

        var y = startY
        var line = StringBuilder()

        fun flushLine() {
            if (line.isNotEmpty()) {
                canvas.drawText(line.toString(), leftX.toFloat(), y, paint)
                y += lineHeight + lineSpacingPx
                line = StringBuilder()
            }
        }

        for (w in words) {
            val test = if (line.isEmpty()) w else "$line $w"
            if (paint.measureText(test) <= maxWidth) {
                line.clear(); line.append(test)
            } else {
                flushLine()
                // Se a palavra sozinha excede a largura, corta com reticências
                if (paint.measureText(w) > maxWidth) {
                    var cut = w
                    while (cut.isNotEmpty() && paint.measureText("$cut…") > maxWidth) {
                        cut = cut.dropLast(1)
                    }
                    if (cut.isNotEmpty()) {
                        canvas.drawText("$cut…", leftX.toFloat(), y, paint)
                        y += lineHeight + lineSpacingPx
                    }
                } else {
                    line.append(w)
                }
            }
        }
        flushLine()
        return y
    }

    /** Exportar PDF */
    private fun exportToPdf() {
        try {
            val pageWidth = 595  // ~A4 em pontos (72 dpi)
            val pageHeight = 842
            val left = 40
            val top = 40
            val right = pageWidth - 40
            val bottom = pageHeight - 40

            val titlePaint = Paint().apply {
                isAntiAlias = true
                textSize = 18f
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
            }
            val headPaint = Paint().apply {
                isAntiAlias = true
                textSize = 12f
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
            }
            val textPaint = Paint().apply {
                isAntiAlias = true
                textSize = 11f
            }
            val linePaint = Paint().apply { strokeWidth = 1.2f }

            val doc = PdfDocument()
            var pageNumber = 0
            fun newPage(): PdfDocument.Page {
                pageNumber += 1
                val pageInfo =
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                return doc.startPage(pageInfo)
            }

            var page = newPage()
            var canvas = page.canvas
            var y = top.toFloat()

            // Título
            canvas.drawText(getString(R.string.gantt_title), left.toFloat(), y, titlePaint)
            y += 18f + 12f

            // Colunas (um pouco mais enxutas para sobrar espaço ao "Progresso")
            val colTitulo = left
            val colPeriodo = colTitulo + 170
            val colResp = colPeriodo + 130
            val colValor = colResp + 100
            val colProg = colValor + 70   // agora sobra ~45px até 'right'

            fun drawHeaderRow() {
                canvas.drawText(
                    getString(R.string.etapa_reg_name_hint),
                    colTitulo.toFloat(),
                    y,
                    headPaint
                )
                canvas.drawText(
                    getString(R.string.cronograma_date_range, "Ini", "Fim"),
                    colPeriodo.toFloat(), y, headPaint
                )
                canvas.drawText(
                    getString(R.string.cron_reg_responsavel_title),
                    colResp.toFloat(),
                    y,
                    headPaint
                )
                canvas.drawText(
                    getString(R.string.gantt_valor_col),
                    colValor.toFloat(),
                    y,
                    headPaint
                )
                canvas.drawText(
                    getString(R.string.gantt_progress_col),
                    colProg.toFloat(),
                    y,
                    headPaint
                )
                y += 10f
                canvas.drawLine(left.toFloat(), y, right.toFloat(), y, linePaint)
                y += 12f
            }

            drawHeaderRow()

            // Linhas
            currentEtapas.forEach { e ->
                val ini = GanttUtils.brToLocalDateOrNull(e.dataInicio)
                val fim = GanttUtils.brToLocalDateOrNull(e.dataFim)
                val fmtIni =
                    ini?.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy")) ?: "—"
                val fmtFim =
                    fim?.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy")) ?: "—"
                val periodoRaw = "$fmtIni — $fmtFim"

                val respRaw = when (e.responsavelTipo) {
                    "EMPRESA" -> (e.empresaNome?.ifBlank { "—" } ?: "—")
                    "FUNCIONARIOS" -> getString(R.string.cronograma_funcionarios_title)
                    else -> "—"
                }
                val valorRaw = formatMoneyBR(computeValorTotal(e))
                val progressoRaw = GanttUtils
                    .calcularProgresso(
                        e.diasConcluidos?.toSet() ?: emptySet(),
                        e.dataInicio,
                        e.dataFim
                    )
                    .toString() + "%"

                // Quebra de página ANTES de desenhar a próxima linha
                if (y > (bottom - 40)) {
                    doc.finishPage(page)
                    page = newPage()
                    canvas = page.canvas
                    y = top.toFloat()

                    // título da nova página
                    canvas.drawText(getString(R.string.gantt_title), left.toFloat(), y, titlePaint)
                    y += 18f + 12f
                    // cabeçalho da tabela
                    drawHeaderRow()
                }

                // Desenha cada célula elipsando para caber até a próxima coluna
                drawCell(canvas, textPaint, e.titulo, colTitulo, colPeriodo, 8, y)
                drawCell(canvas, textPaint, periodoRaw, colPeriodo, colResp, 8, y)
                drawCell(canvas, textPaint, respRaw, colResp, colValor, 8, y)
                drawCell(canvas, textPaint, valorRaw, colValor, colProg, 8, y)
                drawCellToRightEdge(canvas, textPaint, progressoRaw, colProg, right, 0, y)

                y += 16f
            }

            // --------------------- RESUMO FINANCEIRO (PDF) ---------------------
            run {
                // 1) Calcula os mesmos números que a tela exibe
                val valorTotalSemDuplicatas =
                    computeValorTotalSemOverlapInclusiveEmpresas(currentEtapas, funcionariosCache)

                val saldoComAportes: Double? =
                    if (hasAportes) saldoInicialObra + totalAportesObra else null

                val baseSaldo = saldoComAportes ?: saldoInicialObra
                val saldoRestante = baseSaldo - valorTotalSemDuplicatas

                // 2) Espaço antes do bloco
                val fmTitle = headPaint.fontMetrics
                val titleHeight = (fmTitle.descent - fmTitle.ascent)
                val blockTopExtra = 18f

                // Quebra de página se faltar espaço
                if (y > (bottom - (blockTopExtra + titleHeight + 80f))) {
                    doc.finishPage(page)
                    page = newPage()
                    canvas = page.canvas
                    y = top.toFloat()
                }

                y += blockTopExtra

                // 3) Cabeçalho do bloco
                canvas.drawText(
                    getString(R.string.crg_pdf_finance_header),
                    left.toFloat(),
                    y,
                    headPaint
                )
                y += 12f

                // 4) Linhas (usar o mesmo formatMoneyBR da tela)
                val linhaSaldoInicial =
                    getString(R.string.crg_pdf_saldo_inicial_mask, formatMoneyBR(saldoInicialObra))
                val linhaSaldoComAportes = getString(
                    R.string.crg_pdf_saldo_aporte_mask,
                    saldoComAportes?.let { formatMoneyBR(it) } ?: "-"
                )
                val linhaValorTotalSemDup = getString(
                    R.string.crg_pdf_valor_total_sem_dup_mask,
                    formatMoneyBR(valorTotalSemDuplicatas)
                )
                val linhaSaldoRestante =
                    getString(R.string.crg_pdf_saldo_restante_mask, formatMoneyBR(saldoRestante))

                val textGap = 4f
                canvas.drawText(linhaSaldoInicial, left.toFloat(), y, textPaint)
                y += 11f + textGap
                canvas.drawText(linhaSaldoComAportes, left.toFloat(), y, textPaint)
                y += 11f + textGap
                canvas.drawText(linhaValorTotalSemDup, left.toFloat(), y, textPaint)
                y += 11f + textGap

                // Saldo Restante em vermelho quando negativo (mesma lógica da tela)
                val normalColor = ContextCompat.getColor(
                    requireContext(),
                    R.color.md_theme_light_onSurfaceVariant
                )
                val errorColor =
                    ContextCompat.getColor(requireContext(), R.color.md_theme_light_error)

                val oldTextColor = textPaint.color
                textPaint.color = if (saldoRestante < 0) errorColor else normalColor
                canvas.drawText(linhaSaldoRestante, left.toFloat(), y, textPaint)
                textPaint.color = oldTextColor
                y += 14f

                // 5) Parágrafo explicativo (wrap automático)
                val exp = getString(R.string.crg_pdf_saldo_restante_exp)

                // Quebra de página se faltar espaço para o parágrafo
                if (y > (bottom - 60f)) {
                    doc.finishPage(page)
                    page = newPage()
                    canvas = page.canvas
                    y = top.toFloat()
                }

                y = drawWrappedText(
                    canvas = canvas,
                    paint = textPaint,
                    text = exp,
                    leftX = left,
                    rightX = right,
                    startY = y,
                    lineSpacingPx = 2f
                )
            }

            doc.finishPage(page)

            val name = "gantt_${args.obraId}_${System.currentTimeMillis()}.pdf"
            val baos = java.io.ByteArrayOutputStream()
            doc.writeTo(baos)
            doc.close()

            val uri = savePdfToDownloads(requireContext(), baos.toByteArray(), name)
            if (uri != null) {
                SnackbarFragment.newInstance(
                    type = com.luizeduardobrandao.obra.utils.Constants.SnackType.SUCCESS.name,
                    title = getString(R.string.snack_success),
                    msg = getString(R.string.gantt_export_pdf_ok),
                    btnText = getString(R.string.snack_button_ok)
                ).show(childFragmentManager, SnackbarFragment.TAG)
            } else {
                SnackbarFragment.newInstance(
                    type = com.luizeduardobrandao.obra.utils.Constants.SnackType.ERROR.name,
                    title = getString(R.string.snack_error),
                    msg = getString(R.string.gantt_export_error),
                    btnText = getString(R.string.snack_button_ok)
                ).show(childFragmentManager, SnackbarFragment.TAG)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            SnackbarFragment.newInstance(
                type = com.luizeduardobrandao.obra.utils.Constants.SnackType.ERROR.name,
                title = getString(R.string.snack_error),
                msg = getString(R.string.gantt_export_error),
                btnText = getString(R.string.snack_button_ok)
            ).show(childFragmentManager, SnackbarFragment.TAG)
        }
    }

    // Salvar/Restaurar estado de rotação
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("popup_integral", popupIntegralVisible)
        outState.putBoolean("popup_sem_dupla", popupSemDuplaVisible)
        outState.putBoolean("popup_saldo", popupSaldoVisible)
        outState.putBoolean("resumo_expanded", resumoExpanded)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.let {
            // 1) Restaura a aba antes de qualquer popup
            resumoExpanded = it.getBoolean("resumo_expanded", false)
            binding.contentResumoGantt.isVisible = resumoExpanded
            binding.ivArrowResumoGantt.rotation = if (resumoExpanded) 180f else 0f
            binding.contentResumoGantt.post { updateRecyclerBottomInsetForFooter() }

            // marca que já restauramos estado para não sobrescrever na inicialização
            stateRestored = true

            // 2) Restaura flags dos popups
            popupIntegralVisible = it.getBoolean("popup_integral", false)
            popupSemDuplaVisible = it.getBoolean("popup_sem_dupla", false)
            popupSaldoVisible = it.getBoolean("popup_saldo", false)

            // 3) Reabre popups (se a aba está visível)
            binding.root.post {
                if (resumoExpanded && popupIntegralVisible) {
                    popupIntegral = showInfoPopup(
                        binding.ivInfoIntegral,
                        getString(R.string.crg_footer_valor_total_integral_exp)
                    ) { popupIntegralVisible = false; popupIntegral = null }
                }
                if (resumoExpanded && popupSemDuplaVisible) {
                    popupSemDupla = showInfoPopup(
                        binding.ivInfoSemDupla,
                        getString(R.string.crg_footer_valor_total_sem_dupla_exp)
                    ) { popupSemDuplaVisible = false; popupSemDupla = null }
                }
                if (resumoExpanded && popupSaldoVisible) {
                    popupSaldo = showInfoPopup(
                        binding.ivInfoSaldo,
                        getString(R.string.crg_footer_saldo_restante_exp)
                    ) { popupSaldoVisible = false; popupSaldo = null }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter.detachHeaderScroll()

        // Remova o listener com segurança (o VTO pode não estar "alive")
        footerGlobalLayoutListener?.let { l ->
            _binding?.cardResumoGantt?.viewTreeObserver?.let { vto ->
                if (vto.isAlive) vto.removeOnGlobalLayoutListener(l)
            }
            footerGlobalLayoutListener = null
        }

        _binding = null
    }
}