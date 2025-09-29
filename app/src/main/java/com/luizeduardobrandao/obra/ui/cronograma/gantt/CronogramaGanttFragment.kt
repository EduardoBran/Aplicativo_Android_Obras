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
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
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
            val label = tv.findViewById<android.widget.TextView>(R.id.tvDayLabel)
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

    // -------------- Helpers comuns (PDF) --------------
    private val nfBR: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    private fun formatMoneyBR(v: Double?): String =
        if (v == null) "-" else nfBR.format(v)

    private fun parseCsvNomes(csv: String?): List<String> =
        csv?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

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

    /** Aba Expansível */
    // ——— Acordeão simples (mesma ideia do ResumoFragment) ———
    @Suppress("SameParameterValue")
    private fun setupExpandableFooter(
        header: View,
        content: View,
        arrow: ImageView,
        startCollapsed: Boolean
    ) {
        fun apply(expanded: Boolean, animate: Boolean) {
            if (animate) {
                val t = androidx.transition.AutoTransition().apply { duration = 180 }
                androidx.transition.TransitionManager.beginDelayedTransition(
                    binding.root as ViewGroup, t
                )
            }
            content.isVisible = expanded
            arrow.animate().rotation(if (expanded) 180f else 0f).setDuration(180).start()

            // Recalcula o padding do Recycler para nada ficar escondido
            // (post para aguardar o novo tamanho do card)
            content.post { updateRecyclerBottomInsetForFooter() }
        }

        // estado inicial (colapsado)
        content.post { apply(expanded = !startCollapsed, animate = false) }
        header.setOnClickListener {
            val willExpand = !content.isVisible
            apply(willExpand, animate = true)
        }
    }

    // ——— Recalcula e preenche os textos do rodapé ———
    private fun updateResumoFooter() = with(binding) {
        // 1) Andamento da obra: média dos percentuais de todas as etapas
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

        // 2) Financeiro: Saldo Inicial / Saldo com Aportes (mesmos valores do Resumo)
        tvSaldoInicialGantt.text = getString(
            R.string.crg_footer_saldo_inicial_mask,
            formatMoneyBR(saldoInicialObra)
        )

        val saldoComAportes: Double? =
            if (hasAportes) saldoInicialObra + totalAportesObra else null

        tvSaldoComAportesGantt.text = getString(
            R.string.crg_footer_saldo_aporte_mask,
            saldoComAportes?.let { formatMoneyBR(it) } ?: "-"
        )

        // 3) Valor Total: somatório dos valores cadastrados em cada etapa (ignora etapas sem valor)
        val valorTotalCronogramas: Double = currentEtapas
            .mapNotNull { computeValorTotal(it) }
            .sum()
        tvValorTotalGantt.text = getString(
            R.string.crg_footer_valor_total_mask,
            formatMoneyBR(valorTotalCronogramas)
        )

        // 4) Saldo Restante (regra DESTA tela):
        //    (Saldo Inicial + Aportes(se houver)) − (somatório dos valores das etapas)
        val base = saldoComAportes ?: saldoInicialObra
        val saldoRestante = base - valorTotalCronogramas
        tvSaldoRestanteGantt.text = getString(
            R.string.crg_footer_saldo_restante_mask,
            formatMoneyBR(saldoRestante)
        )

        // Vermelho quando negativo; cor padrão caso contrário
        val normalColor = ContextCompat.getColor(
            requireContext(), R.color.md_theme_light_onSurfaceVariant
        )
        val errorColor = ContextCompat.getColor(
            requireContext(), R.color.md_theme_light_error
        )
        tvSaldoRestanteGantt.setTextColor(if (saldoRestante < 0) errorColor else normalColor)
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


    /** --- Helper de texto para PDF --- */

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

/** Sanitiza campo para CSV: remove quebras de linha e escapa aspas. */
//private fun sanitizeCsvField(s: String?): String =
//    s.orEmpty()
//        .replace("\r", " ")
//        .replace("\n", " ")
//        .trim()
//        .replace("\"", "\"\"")

/** Cabeçalho global da timeline (dias) já está em headerDays. */
//private fun workingHeaderDays(): List<LocalDate> =
//    headerDays.filter { !GanttUtils.isSunday(it) }

/** Exportar CSV */
//private fun exportToCsv() {
//    try {
//        val dias = workingHeaderDays()
//        val sb = StringBuilder()
//
//        // Cabeçalho
//        sb.append(
//            listOf(
//                "id", "titulo", "data_inicio", "data_fim",
//                "responsavel_tipo", "funcionarios", "empresa_nome", "empresa_valor",
//                "valor_calculado", "progresso_percent"
//            ).plus(dias.map { it.toString() }).joinToString(",")
//        ).append("\n")
//
//        currentEtapas.forEach { e ->
//            val ini = GanttUtils.brToLocalDateOrNull(e.dataInicio)
//            val fim = GanttUtils.brToLocalDateOrNull(e.dataFim)
//            val progresso = GanttUtils.calcularProgresso(
//                e.diasConcluidos?.toSet() ?: emptySet(),
//                e.dataInicio,
//                e.dataFim
//            )
//            val valor = computeValorTotal(e)
//
//            val fixas = listOf(
//                sanitizeCsvField(e.id),
//                sanitizeCsvField(e.titulo),
//                sanitizeCsvField(e.dataInicio),
//                sanitizeCsvField(e.dataFim),
//                sanitizeCsvField(e.responsavelTipo),
//                sanitizeCsvField(e.funcionarios),
//                sanitizeCsvField(e.empresaNome),
//                sanitizeCsvField(e.empresaValor?.toString()),
//                sanitizeCsvField(valor?.toString()),
//                sanitizeCsvField(progresso.toString())
//            ).joinToString(",") { "\"$it\"" }
//
//            val rangeOk = (ini != null && fim != null && !fim.isBefore(ini))
//            val done = e.diasConcluidos?.toSet() ?: emptySet()
//            val porDia = dias.joinToString(",") { d ->
//                val inRange = rangeOk && !d.isBefore(ini) && !d.isAfter(fim)
//                val mark =
//                    if (inRange && done.contains(GanttUtils.localDateToUtcString(d))) 1 else 0
//                mark.toString()
//            }
//
//            sb.append(fixas).append(",").append(porDia).append("\n")
//        }
//
//        val name = "gantt_${args.obraId}_${System.currentTimeMillis()}.csv"
//        val bytes = sb.toString().toByteArray(Charsets.UTF_8)
//        val uri = saveCsvToDownloads(requireContext(), bytes, name)
//
//        if (uri != null) {
//            SnackbarFragment.newInstance(
//                type = com.luizeduardobrandao.obra.utils.Constants.SnackType.SUCCESS.name,
//                title = getString(R.string.snack_success),
//                msg = getString(R.string.gantt_export_csv_ok),
//                btnText = getString(R.string.snack_button_ok)
//            ).show(childFragmentManager, SnackbarFragment.TAG)
//        } else {
//            SnackbarFragment.newInstance(
//                type = com.luizeduardobrandao.obra.utils.Constants.SnackType.ERROR.name,
//                title = getString(R.string.snack_error),
//                msg = getString(R.string.gantt_export_error),
//                btnText = getString(R.string.snack_button_ok)
//            ).show(childFragmentManager, SnackbarFragment.TAG)
//        }
//    } catch (t: Throwable) {
//        t.printStackTrace()
//        SnackbarFragment.newInstance(
//            type = com.luizeduardobrandao.obra.utils.Constants.SnackType.ERROR.name,
//            title = getString(R.string.snack_error),
//            msg = getString(R.string.gantt_export_error),
//            btnText = getString(R.string.snack_button_ok)
//        ).show(childFragmentManager, SnackbarFragment.TAG)
//    }
//}