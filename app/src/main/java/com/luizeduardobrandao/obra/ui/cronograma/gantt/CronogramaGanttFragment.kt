package com.luizeduardobrandao.obra.ui.cronograma.gantt

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.SystemClock
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
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.NavOptions
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Etapa
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentCronogramaGanttBinding
import com.luizeduardobrandao.obra.ui.cronograma.CronogramaViewModel
import com.luizeduardobrandao.obra.ui.cronograma.gantt.adapter.GanttRowAdapter
import com.luizeduardobrandao.obra.ui.cronograma.gantt.anim.GanttHeaderAnimator
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.funcionario.FuncionarioViewModel
import com.luizeduardobrandao.obra.ui.resumo.ResumoViewModel
import com.luizeduardobrandao.obra.utils.calcularProgressoGeralPorDias
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
    private var lastBuiltHeaderDays: List<LocalDate>? = null

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

    // --- NOVO: última largura conhecida da coluna fixa (esquerda)
    private var lastKnownLeftWidth: Int = 0

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

    // Reposicionar Recycler ao retornar de CronogramaRegister
    private var savedScrollX: Int = 0

    // Loading com duração mínima
    private val minLoadingTime = 1_000L
    private var loadingStartedAt: Long = 0L
    private var pendingHideRunnable: Runnable? = null

    // Estado Animação
    private var headerAnimatedOnce = false
    private var headerIntroDxPx: Int = 0

    // ——— Barra de progresso (resumo) ———
    private var lastAvgPct: Int = 0
    private var animateBarOnNextUpdate = false

    // Evitar dupla navegação acidental ao reiniciar o Gantt
    private var selfRestarted = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCronogramaGanttBinding.inflate(inflater, container, false)
        // >>> força rebuild do header quando a View é recriada
        lastBuiltHeaderDays = null
        headerAnimatedOnce = false
        headerIntroDxPx = 0
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
        // Cria o adapter com o Context e os callbacks
        adapter = GanttRowAdapter(
            onToggleDay = { etapa, newSetUtc ->
                // Persistir alteração (já existia)
                viewModel.commitDias(etapa, newSetUtc)
            },
            requestHeaderDays = { headerDays }, // Ainda usado no bind, mas pode ser refatorado
            getFuncionarios = { funcionariosCache },
            onEditEtapa = { etapa ->
                // Navegar para edição da etapa a partir do Gantt
                val dir = CronogramaGanttFragmentDirections
                    .actionGanttToRegister(args.obraId, etapa.id)
                findNavController().navigate(dir)
            }
        )

        // Injetar o X salvo
        adapter.setInitialScrollX(savedScrollX)

        // Conecta o callback ANTES de setar o adapter no RecyclerView
        adapter.onFirstLeftWidth = { leftWidth ->
            lastKnownLeftWidth = leftWidth
            applyHeaderLayoutWithLeftWidth(leftWidth)

            val row = binding.headerRow

            // Paddings/margens reais que existem ANTES do HSV de cada linha
            val listStartPad = binding.rvGantt.paddingLeft
            val firstGap = resources.getDimensionPixelSize(R.dimen.gantt_first_cell_margin_start)
            val endPadMin = resources.getDimensionPixelSize(R.dimen.gantt_header_end_pad)
            val dayWidth = resources.getDimensionPixelSize(R.dimen.gantt_day_width)
            val dayGap = resources.getDimensionPixelSize(R.dimen.gantt_day_gap)

            val cardMarginStart = resources.getDimensionPixelSize(R.dimen.gantt_card_margin_h)
            val rowInnerPadStart = resources.getDimensionPixelSize(R.dimen.gantt_row_content_pad)

            // largura total dos dias (quadrados + gaps) + firstGap
            val daysWidth = if (headerDays.isNotEmpty()) {
                (dayWidth * headerDays.size + dayGap * (headerDays.size - 1)) + firstGap
            } else 0

            // offset EXATO até o início do 1º quadrado da 1ª linha
            val startOffset =
                cardMarginStart + listStartPad + rowInnerPadStart + leftWidth + firstGap

            // paddingEnd que garante que o ÚLTIMO quadrado apareça quando rolar até o fim
            val computedEndPad = maxOf(
                endPadMin,
                binding.root.width - (startOffset - firstGap + daysWidth)
            )

            // Header usa o mesmo endPad das linhas
            row.setPadding(
                startOffset,                 // paddingStart alinha com o 1º quadrado
                row.paddingTop,
                computedEndPad,              // paddingEnd idêntico ao das timelines
                row.paddingBottom
            )

            // Reposiciona o header já com o novo padding aplicado E só então recalcula o fade
            postIfAlive { b ->
                val headerW = b.headerRow.measuredWidth
                val vpW = b.headerScroll.width
                val maxScrollX = (headerW - vpW).coerceAtLeast(0)

                val targetX = savedScrollX.coerceIn(0, maxScrollX)
                b.headerScroll.scrollTo(targetX, 0)
                syncVisibleRowsTo(targetX)
                GanttHeaderAnimator.requestFadeRecalc(b.headerScroll, b.headerRow)
            }

            // Linhas: manda o mesmo endPad para o adapter (ele aplica no GanttTimelineView)
            adapter.setTimelineEndPad(computedEndPad)
            adapter.freezeLeftWidth(leftWidth)
        }

        rvGantt.adapter = adapter

        // Interromper animação após clique rápido
        binding.rvGantt.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    val vh = binding.rvGantt.findContainingViewHolder(view) as? GanttRowAdapter.VH
                        ?: return
                    val rowScroll = vh.b.rowScroll
                    // detector por linha
                    GanttHeaderAnimator.installEarlyFinishOnRowScroll(
                        rowScroll = rowScroll,
                        headerScroll = binding.headerScroll,
                        container = binding.headerRow
                    )
                }

                override fun onChildViewDetachedFromWindow(view: View) {
                    val vh = binding.rvGantt.findContainingViewHolder(view) as? GanttRowAdapter.VH
                        ?: return
                    GanttHeaderAnimator.uninstallEarlyFinishOnRowScroll(vh.b.rowScroll)
                }
            }
        )

        // elimina cross-fade/merge que causava blink
        (rvGantt.itemAnimator as? SimpleItemAnimator)?.apply {
            supportsChangeAnimations = false
            changeDuration = 0
        }

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

        // Se a aba já vier expandida (ex.: após rotação/restauração), pinte imediatamente sem animar
        if (binding.contentResumoGantt.isVisible) {
            // usa o último valor conhecido antes da coleta finalizar
            binding.progressStatusBar.setProgress(lastAvgPct, animate = false)
        }
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
                        adapter.updateHeaderDays(headerDays) // Sincroniza o adapter com headerDays

                        // 2) Estado da lista de etapas
                        when (etapasUi) {
                            is UiState.Loading -> {
                                // Enquanto qualquer lado não estiver pronto → loading
                                startLoading()
                                return@collect
                            }

                            is UiState.ErrorRes -> {
                                // Erro: esconde conteúdo e mostra mensagem
                                finishLoading {
                                    binding.headerContainer.isVisible = false
                                    binding.rvGantt.isVisible = false
                                    binding.textEmpty.isVisible = true
                                    binding.textEmpty.setText(etapasUi.resId)
                                    binding.headerScroll.isVisible = false
                                    binding.cardResumoGantt.isVisible = false
                                }
                                return@collect
                            }

                            is UiState.Success -> {
                                // Ordene por data de início (mais recente primeiro). Datas inválidas vão para o fim.
                                val cmp = compareBy<Etapa>(
                                    {
                                        GanttUtils.brToLocalDateOrNull(it.dataInicio)?.toEpochDay()
                                            ?: Long.MAX_VALUE
                                    },
                                    {
                                        GanttUtils.brToLocalDateOrNull(it.dataFim)?.toEpochDay()
                                            ?: Long.MAX_VALUE
                                    },
                                    { it.titulo.trim().lowercase(Locale.getDefault()) }
                                )

                                val lista = etapasUi.data.sortedWith(cmp)

                                currentEtapas = lista // << guardar para exportações

                                // Se o header NÃO estiver pronto ainda, aguarde (loading)
                                if (!hasHeader) {
                                    startLoading()
                                    return@collect
                                }

                                // 3) Já temos header + etapas → renderiza TUDO de uma vez
                                buildHeaderViews() // monta as datas do topo (sem mexer em visibilidade)
                                adapter.submitList(lista) {
                                    // Mostrar tudo só depois que a lista aplicar o diff
                                    finishLoading {
                                        binding.textEmpty.isVisible = lista.isEmpty()
                                        if (lista.isEmpty()) binding.textEmpty.setText(R.string.gantt_empty)

                                        binding.headerContainer.isVisible = lista.isNotEmpty()
                                        binding.rvGantt.isVisible = lista.isNotEmpty()
                                        binding.headerScroll.isVisible = lista.isNotEmpty()
                                        binding.cardResumoGantt.isVisible = true

                                        // Recalcula o resumo do rodapé sempre que as etapas mudarem
                                        updateResumoFooter()

                                        // Re-emite um "scroll changed" após as linhas estarem bindadas
                                        postIfAlive { b ->
                                            val x = b.headerScroll.scrollX
                                            b.headerScroll.scrollTo(x + 1, 0)
                                            b.headerScroll.scrollTo(x, 0)

                                            syncVisibleRowsTo()
                                            GanttHeaderAnimator.requestFadeRecalc(
                                                b.headerScroll,
                                                b.headerRow
                                            )
                                        }
                                    }
                                }
                            }

                            else -> Unit
                        }
                    }
            }
        }
    }

    // Loading com 1s no mínimo
    private fun startLoading() = with(binding) {
        // cancela qualquer hide pendente
        pendingHideRunnable?.let {
            progressGantt.removeCallbacks(it)
            pendingHideRunnable = null
        }
        if (!progressGantt.isVisible) {
            loadingStartedAt = SystemClock.elapsedRealtime()
            progressGantt.isIndeterminate = true // redundante, mas seguro
            progressGantt.isVisible = true
        }
        // nunca conteúdo junto com loading
        rvGantt.isVisible = false
        headerContainer.isVisible = false
        textEmpty.isVisible = false
        headerScroll.isVisible = false
        cardResumoGantt.isVisible = false
    }

    private fun finishLoading(after: () -> Unit) = with(binding) {
        // cancela atrasos anteriores
        pendingHideRunnable?.let {
            progressGantt.removeCallbacks(it)
            pendingHideRunnable = null
        }
        val elapsed = SystemClock.elapsedRealtime() - loadingStartedAt
        val remain = (minLoadingTime - elapsed).coerceAtLeast(0L)

        val hideAndThen = Runnable {
            progressGantt.isVisible = false
            pendingHideRunnable = null

            // 1) Deixe quem chamou mostrar o conteúdo
            after()

            // 2) Anime o header uma única vez
            if (!headerAnimatedOnce && headerRow.isNotEmpty()) {
                postIfAlive { b ->
                    val offsetPx = -(resources.displayMetrics.density * 2f)
                    GanttHeaderAnimator.animateInDates(
                        container = b.headerRow,
                        durationMs = 580L,
                        staggerMs = 90L,
                        offsetPx = offsetPx
                    )
                    headerAnimatedOnce = true
                }
            }
        }

        if (remain > 0L) {
            pendingHideRunnable = hideAndThen
            progressGantt.postDelayed(hideAndThen, remain)
        } else {
            hideAndThen.run()
        }
    }

    // Exibição do cabeçalho com as datas
    private fun buildHeaderViews() = with(binding) {
        if (headerDays.isEmpty()) return@with

        // se os dias não mudaram desde a última vez → não refaz
        if (lastBuiltHeaderDays == headerDays) return@with

        headerRow.removeAllViews()

        val inflater = LayoutInflater.from(root.context)
        headerDays.forEach { d ->
            val tv = inflater.inflate(R.layout.item_gantt_header_day, headerRow, false) as ViewGroup
            val label = tv.findViewById<TextView>(R.id.tvDayLabel)

            // Texto
            label.text = if (GanttUtils.isSunday(d)) {
                getString(R.string.gantt_sunday_short)
            } else {
                GanttUtils.formatDayForHeader(d)
            }

            // Tamanho diferenciado para domingo
            if (GanttUtils.isSunday(d)) {
                val big = resources.getDimension(R.dimen.gantt_day_text_size_sunday)
                label.setTextSize(TypedValue.COMPLEX_UNIT_PX, big)
            } else {
                val normal = resources.getDimension(R.dimen.gantt_day_text_size)
                label.setTextSize(TypedValue.COMPLEX_UNIT_PX, normal)
            }

            headerRow.addView(tv)
        }

        // sincroniza adapter ↔ header
        adapter.attachHeaderScroll(headerScroll)

        // Ativa o fade proporcional de datas conforme visibilidade da célula
        GanttHeaderAnimator.enableScrollFade(headerScroll, headerRow)

        // Gesto real do usuário encerra a intro e ativa o fade imediatamente
        GanttHeaderAnimator.installEarlyFinishGestures(
            headerScroll = headerScroll,
            container = headerRow,
            recycler = rvGantt
        )

        if (headerAnimatedOnce) {
            // pulamos a animação inicial após rotação → libera o fade
            GanttHeaderAnimator.markIntroDone(headerRow)
        }

        // PRIME anti-flash: só se ainda não animou
        if (!headerAnimatedOnce) {
            val startOffset = -(resources.displayMetrics.density * 40f) // esquerda → direita
            for (i in 0 until headerRow.childCount) {
                val v = headerRow.getChildAt(i)
                v.alpha = 0f
                v.translationX = startOffset
            }
        }

        // --- NOVO: se já conhecemos a largura da coluna fixa, aplicamos o layout do header agora,
        // mesmo que o primeiro item ainda não esteja visível.
        if (lastKnownLeftWidth > 0) {
            applyHeaderLayoutWithLeftWidth(lastKnownLeftWidth)
        } else {
            tryApplyHeaderLayoutFromFirstVisibleRow()
        }

        // cache atualizado
        lastBuiltHeaderDays = headerDays
    }

    // --- NOVO: tenta estimar a largura a partir da primeira linha visível
    private fun tryApplyHeaderLayoutFromFirstVisibleRow() {
        val lm = binding.rvGantt.layoutManager as? LinearLayoutManager ?: return
        val pos = lm.findFirstVisibleItemPosition()
        if (pos == RecyclerView.NO_POSITION) return
        val vh =
            binding.rvGantt.findViewHolderForAdapterPosition(pos) as? GanttRowAdapter.VH ?: return
        val leftWidthGuess = vh.b.leftColumn.width
        if (leftWidthGuess > 0) {
            lastKnownLeftWidth = leftWidthGuess
            applyHeaderLayoutWithLeftWidth(leftWidthGuess)
        }
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
        fun apply(expanded: Boolean, animateLayout: Boolean) {
            resumoExpanded = expanded

            if (animateLayout) {
                val t = androidx.transition.AutoTransition().apply { duration = 180 }
                androidx.transition.TransitionManager.beginDelayedTransition(
                    binding.root as ViewGroup, t
                )
            }

            // aplica visibilidade
            content.isVisible = expanded

            if (!expanded) {
                // ao colapsar só fecha popups
                popupIntegral?.dismiss()
                popupSemDupla?.dismiss()
                popupSaldo?.dismiss()
            } else {
                // ao expandir, quem decide animar é o updateResumoFooter via flag
                // (abaixo chamamos updateResumoFooter() para já pintar)
                updateResumoFooter()
            }

            arrow.animate()
                .rotation(if (expanded) 180f else 0f)
                .setDuration(180)
                .start()

            postIfAlive { updateRecyclerBottomInsetForFooter() }
        }

        // Estado inicial do fragment
        if (stateRestored) {
            apply(expanded = resumoExpanded, animateLayout = false)
        } else {
            apply(expanded = !startCollapsed, animateLayout = false)
        }

        // Toggle do usuário
        header.setOnClickListener {
            val willExpand = !content.isVisible
            if (willExpand) {
                // NÃO zere mais aqui. Só arme o flag.
                animateBarOnNextUpdate = true
            }
            apply(expanded = willExpand, animateLayout = true)
        }
    }


    // ——— Recalcula e preenche os textos do rodapé ———
    private fun updateResumoFooter() = with(binding) {
        // 1) Andamento da obra
        val avgPct: Int = calcularProgressoGeralPorDias(currentEtapas)

        // Guarda para uso ao expandir
        lastAvgPct = avgPct

        // Pinta a barra
        if (contentResumoGantt.isVisible) {
            if (animateBarOnNextUpdate) {
                contentResumoGantt.post {
                    // garante estado inicial sem animação
                    binding.progressStatusBar.setProgress(0, animate = false)
                    // agora anima até o valor calculado
                    binding.progressStatusBar.setProgress(avgPct, animate = true)
                    animateBarOnNextUpdate = false
                }
            } else {
                binding.progressStatusBar.setProgress(avgPct, animate = false)
            }
        }

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

    /** Exportar PDF — robusto e à prova de NPE/estado do Fragment */
    private fun exportToPdf() {
        // Garante que o Fragment está anexado e a View existe
        if (!isAdded || _binding == null) return
        val ctx = context ?: return

        var doc: PdfDocument? = null
        var baos: java.io.ByteArrayOutputStream? = null

        try {
            // Dimensões A4 em ~72dpi
            val pageWidth = 595
            val pageHeight = 842
            val left = 40
            val top = 40
            val right = pageWidth - 40
            val bottom = pageHeight - 40

            // Pincéis
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

            // Documento
            doc = PdfDocument()
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
            canvas.drawText(ctx.getString(R.string.gantt_title), left.toFloat(), y, titlePaint)
            y += 18f + 12f

            // Colunas
            val colTitulo = left
            val colPeriodo = colTitulo + 170
            val colResp = colPeriodo + 130
            val colValor = colResp + 100
            val colProg = colValor + 70  // sobra margem à direita

            fun drawHeaderRow() {
                canvas.drawText(
                    ctx.getString(R.string.etapa_reg_name_hint),
                    colTitulo.toFloat(), y, headPaint
                )
                canvas.drawText(
                    ctx.getString(R.string.cronograma_date_range, "Ini", "Fim"),
                    colPeriodo.toFloat(), y, headPaint
                )
                canvas.drawText(
                    ctx.getString(R.string.cron_reg_responsavel_title),
                    colResp.toFloat(), y, headPaint
                )
                canvas.drawText(
                    ctx.getString(R.string.gantt_valor_col),
                    colValor.toFloat(), y, headPaint
                )
                canvas.drawText(
                    ctx.getString(R.string.gantt_progress_col),
                    colProg.toFloat(), y, headPaint
                )
                y += 10f
                canvas.drawLine(left.toFloat(), y, right.toFloat(), y, linePaint)
                y += 12f
            }

            drawHeaderRow()

            // Linhas (usa estado atual — rápido o bastante para síncrono)
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
                    "FUNCIONARIOS" -> ctx.getString(R.string.cronograma_funcionarios_title)
                    else -> "—"
                }
                val valorRaw = formatMoneyBR(computeValorTotal(e))
                val progressoRaw = (GanttUtils.calcularProgresso(
                    e.diasConcluidos?.toSet() ?: emptySet(),
                    e.dataInicio,
                    e.dataFim
                )).toString() + "%"

                // quebra de página se necessário (antes de desenhar)
                if (y > (bottom - 40)) {
                    doc.finishPage(page)
                    page = newPage()
                    canvas = page.canvas
                    y = top.toFloat()

                    canvas.drawText(
                        ctx.getString(R.string.gantt_title),
                        left.toFloat(),
                        y,
                        titlePaint
                    )
                    y += 18f + 12f
                    drawHeaderRow()
                }

                // Células
                drawCell(canvas, textPaint, e.titulo, colTitulo, colPeriodo, 8, y)
                drawCell(canvas, textPaint, periodoRaw, colPeriodo, colResp, 8, y)
                drawCell(canvas, textPaint, respRaw, colResp, colValor, 8, y)
                drawCell(canvas, textPaint, valorRaw, colValor, colProg, 8, y)
                drawCellToRightEdge(canvas, textPaint, progressoRaw, colProg, right, 0, y)

                y += 16f
            }

            // --------------------- RESUMO FINANCEIRO (PDF) ---------------------
            run {
                val valorTotalIntegral = computeValorTotalIntegral(currentEtapas)
                val valorTotalSemDuplicatas =
                    computeValorTotalSemOverlapInclusiveEmpresas(currentEtapas, funcionariosCache)

                val saldoComAportes: Double? =
                    if (hasAportes) saldoInicialObra + totalAportesObra else null

                val baseSaldo = saldoComAportes ?: saldoInicialObra
                val saldoRestante = baseSaldo - valorTotalSemDuplicatas

                val blockTopExtra = 18f
                // espaço mínimo para o bloco
                if (y > (bottom - (blockTopExtra + 12f + 80f))) {
                    doc.finishPage(page)
                    page = newPage()
                    canvas = page.canvas
                    y = top.toFloat()
                }

                y += blockTopExtra

                // Cabeçalho bloco
                canvas.drawText(
                    ctx.getString(R.string.crg_pdf_finance_header),
                    left.toFloat(), y, headPaint
                )
                y += 12f

                // Linhas
                val linhaSaldoInicial = ctx.getString(
                    R.string.crg_pdf_saldo_inicial_mask, formatMoneyBR(saldoInicialObra)
                )
                val linhaSaldoComAportes = ctx.getString(
                    R.string.crg_pdf_saldo_aporte_mask,
                    saldoComAportes?.let { formatMoneyBR(it) } ?: "-"
                )
                val linhaValorTotalIntegral = ctx.getString(
                    R.string.crg_pdf_valor_total_integral_mask, formatMoneyBR(valorTotalIntegral)
                )
                val linhaValorTotalSemDup = ctx.getString(
                    R.string.crg_pdf_valor_total_sem_dup_mask,
                    formatMoneyBR(valorTotalSemDuplicatas)
                )
                val linhaSaldoRestante = ctx.getString(
                    R.string.crg_pdf_saldo_restante_mask, formatMoneyBR(saldoRestante)
                )

                val textGap = 4f
                canvas.drawText(linhaSaldoInicial, left.toFloat(), y, textPaint); y += 11f + textGap
                canvas.drawText(
                    linhaSaldoComAportes,
                    left.toFloat(),
                    y,
                    textPaint
                ); y += 11f + textGap
                canvas.drawText(
                    linhaValorTotalIntegral,
                    left.toFloat(),
                    y,
                    textPaint
                ); y += 11f + textGap
                canvas.drawText(
                    linhaValorTotalSemDup,
                    left.toFloat(),
                    y,
                    textPaint
                ); y += 11f + textGap

                // Cor especial p/ saldo negativo
                val normalColor =
                    ContextCompat.getColor(ctx, R.color.md_theme_light_onSurfaceVariant)
                val errorColor = ContextCompat.getColor(ctx, R.color.md_theme_light_error)
                val oldTextColor = textPaint.color
                textPaint.color = if (saldoRestante < 0) errorColor else normalColor
                canvas.drawText(linhaSaldoRestante, left.toFloat(), y, textPaint)
                textPaint.color = oldTextColor
                y += 14f

                // Parágrafo explicativo (wrap)
                val exp = ctx.getString(R.string.crg_pdf_saldo_restante_exp)
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

            // Finaliza e grava
            doc.finishPage(page)

            baos = java.io.ByteArrayOutputStream()
            doc.writeTo(baos)
            val bytes = baos.toByteArray()

            val name = "Obra_${args.obraId}_${System.currentTimeMillis()}.pdf"
            val uri = savePdfToDownloads(ctx, bytes, name)

            // UI (snackbar) — sempre via postIfAlive
            postIfAlive { _ ->
                if (uri != null) {
                    SnackbarFragment.newInstance(
                        type = com.luizeduardobrandao.obra.utils.Constants.SnackType.SUCCESS.name,
                        title = ctx.getString(R.string.generic_success),
                        msg = ctx.getString(R.string.gantt_export_pdf_ok),
                        btnText = ctx.getString(R.string.generic_ok_upper_case)
                    ).show(childFragmentManager, SnackbarFragment.TAG)
                } else {
                    SnackbarFragment.newInstance(
                        type = com.luizeduardobrandao.obra.utils.Constants.SnackType.ERROR.name,
                        title = ctx.getString(R.string.generic_error),
                        msg = ctx.getString(R.string.gantt_export_error),
                        btnText = ctx.getString(R.string.generic_ok_upper_case)
                    ).show(childFragmentManager, SnackbarFragment.TAG)
                }
            }
        } catch (t: Throwable) {
            // Erro genérico (ex.: I/O). Loga e avisa o usuário se a View ainda existir.
            postIfAlive { _ ->
                t.printStackTrace()
                SnackbarFragment.newInstance(
                    type = com.luizeduardobrandao.obra.utils.Constants.SnackType.ERROR.name,
                    title = ctx.getString(R.string.generic_error),
                    msg = ctx.getString(R.string.gantt_export_error),
                    btnText = ctx.getString(R.string.generic_ok_upper_case)
                ).show(childFragmentManager, SnackbarFragment.TAG)
            }
        } finally {
            // Fecha recursos mesmo em caso de exceção
            runCatching { doc?.close() }
            runCatching { baos?.close() }
        }
    }

    /** Snackbar perguntando se deseja salvar */
    private fun askSavePdf() {
        postIfAlive {
            showSnackbarFragment(
                type = Constants.SnackType.WARNING.name,
                title = getString(R.string.export_summary_snack_title),
                msg = getString(R.string.export_summary_snack_msg),
                btnText = getString(R.string.generic_yes_upper_case),
                onAction = { exportToPdf() },
                btnNegativeText = getString(R.string.generic_no_upper_case),
                onNegative = { /* fica na página */ }
            )
        }
    }

    // Helper ante NPE/race: só executa se a view ainda estiver anexada
    private inline fun postIfAlive(
        crossinline block: (FragmentCronogramaGanttBinding) -> Unit
    ) {
        val v = _binding?.root ?: return
        v.post {
            val b = _binding ?: return@post
            if (!b.root.isAttachedToWindow) return@post
            block(b)
        }
    }

    /** Alinha todas as linhas visíveis ao X do header (ou a um X específico). */
    private fun syncVisibleRowsTo(targetX: Int? = null) = postIfAlive { b ->
        val lm = b.rvGantt.layoutManager as? LinearLayoutManager ?: return@postIfAlive

        // Clampa o X ao conteúdo do header (evita “pulo” após rotação)
        val headerContentW = b.headerRow.measuredWidth
        val viewportW = b.headerScroll.width
        val maxHeaderX = (headerContentW - viewportW).coerceAtLeast(0)
        val x = targetX ?: b.headerScroll.scrollX
        val clampedHeaderX = x.coerceIn(0, maxHeaderX)

        // Garante o X do header
        b.headerScroll.scrollTo(clampedHeaderX, 0)

        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION ||
            last == RecyclerView.NO_POSITION
        ) return@postIfAlive

        // Aplica o mesmo X, respeitando o limite individual de cada linha
        for (pos in first..last) {
            val holder =
                b.rvGantt.findViewHolderForAdapterPosition(pos) as? GanttRowAdapter.VH ?: continue
            val row = holder.b.rowScroll
            val childW = row.getChildAt(0)?.width ?: 0
            val maxRowX = (childW - row.width).coerceAtLeast(0)
            val clampedRowX = clampedHeaderX.coerceAtMost(maxRowX)
            if (row.scrollX != clampedRowX) row.scrollTo(clampedRowX, 0)
        }
    }

    // --- NOVO: aplica padding do header e sincroniza tudo usando uma largura conhecida da coluna fixa
    private fun applyHeaderLayoutWithLeftWidth(leftWidth: Int) = with(binding) {
        // Paddings/margens reais antes do HSV de cada linha
        val listStartPad = rvGantt.paddingLeft
        val firstGap = resources.getDimensionPixelSize(R.dimen.gantt_first_cell_margin_start)
        val endPadMin = resources.getDimensionPixelSize(R.dimen.gantt_header_end_pad)
        val dayWidth = resources.getDimensionPixelSize(R.dimen.gantt_day_width)
        val dayGap = resources.getDimensionPixelSize(R.dimen.gantt_day_gap)

        val cardMarginStart = resources.getDimensionPixelSize(R.dimen.gantt_card_margin_h)
        val rowInnerPadStart = resources.getDimensionPixelSize(R.dimen.gantt_row_content_pad)

        val daysWidth = if (headerDays.isNotEmpty()) {
            (dayWidth * headerDays.size + dayGap * (headerDays.size - 1)) + firstGap
        } else 0

        // offset até o 1º quadrado
        val startOffset = cardMarginStart + listStartPad + rowInnerPadStart + leftWidth + firstGap

        // paddingEnd que garante visualizar o último quadrado
        val computedEndPad = maxOf(
            endPadMin,
            root.width - (startOffset - firstGap + daysWidth)
        )

        // Header usa o mesmo endPad das linhas
        headerRow.setPadding(
            startOffset,
            headerRow.paddingTop,
            computedEndPad,
            headerRow.paddingBottom
        )

        // Reposiciona header e recalc do fade
        postIfAlive { b ->
            val headerW = b.headerRow.measuredWidth
            val vpW = b.headerScroll.width
            val maxScrollX = (headerW - vpW).coerceAtLeast(0)
            val targetX = savedScrollX.coerceIn(0, maxScrollX)
            b.headerScroll.scrollTo(targetX, 0)
            syncVisibleRowsTo(targetX)
            GanttHeaderAnimator.requestFadeRecalc(b.headerScroll, b.headerRow)
        }

        // Linhas: mesmo endPad + congela largura no adapter
        adapter.setTimelineEndPad(computedEndPad)
        adapter.freezeLeftWidth(leftWidth)
    }


    // Salvar/Restaurar estado de rotação
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Estado da aba + popups
        outState.putBoolean("popup_integral", popupIntegralVisible)
        outState.putBoolean("popup_sem_dupla", popupSemDuplaVisible)
        outState.putBoolean("popup_saldo", popupSaldoVisible)
        outState.putBoolean("resumo_expanded", resumoExpanded)

        // Scroll horizontal atual do Gantt (se adapter já existe) senão usa último salvo
        val ganttX = if (::adapter.isInitialized) adapter.getLastScrollX() else savedScrollX
        outState.putInt("gantt_scroll_x", ganttX)

        outState.putBoolean("header_anim_once", headerAnimatedOnce)

        // Barra Progresso
        outState.putInt("bar_pct_last", lastAvgPct)

        outState.putInt("left_width_last", lastKnownLeftWidth)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.let { bundle ->
            // 0) Recupera o X horizontal ANTES de criar/ligar o adapter.
            savedScrollX = bundle.getInt("gantt_scroll_x", 0)
            headerAnimatedOnce = bundle.getBoolean("header_anim_once", true)
            lastKnownLeftWidth = bundle.getInt("left_width_last", 0)

            // Restaura último percentual conhecido para a barra
            lastAvgPct = bundle.getInt("bar_pct_last", lastAvgPct)

            // 1) Restaura a aba (faz isso cedo para o layout já nascer no estado correto)
            resumoExpanded = bundle.getBoolean("resumo_expanded", false)

            // Se a aba estava expandida durante a rotação, pinte imediatamente sem animar
            if (resumoExpanded) {
                animateBarOnNextUpdate = false
                postIfAlive {
                    binding.progressStatusBar.setProgress(lastAvgPct, animate = false)
                }
            }
            binding.contentResumoGantt.isVisible = resumoExpanded
            binding.ivArrowResumoGantt.rotation = if (resumoExpanded) 180f else 0f
            postIfAlive { updateRecyclerBottomInsetForFooter() }

            postIfAlive { syncVisibleRowsTo(savedScrollX) } // <— ajuda em alguns aparelhos

            // Evita que a inicialização padrão sobrescreva este estado
            stateRestored = true

            // 2) Restaura flags dos popups
            popupIntegralVisible = bundle.getBoolean("popup_integral", false)
            popupSemDuplaVisible = bundle.getBoolean("popup_sem_dupla", false)
            popupSaldoVisible = bundle.getBoolean("popup_saldo", false)

            // 3) Reabre os popups (somente se a aba estiver visível)
            postIfAlive { b ->
                if (resumoExpanded && popupIntegralVisible) {
                    popupIntegral = showInfoPopup(
                        b.ivInfoIntegral,
                        getString(R.string.crg_footer_valor_total_integral_exp)
                    ) { popupIntegralVisible = false; popupIntegral = null }
                }
                if (resumoExpanded && popupSemDuplaVisible) {
                    popupSemDupla = showInfoPopup(
                        b.ivInfoSemDupla,
                        getString(R.string.crg_footer_valor_total_sem_dupla_exp)
                    ) { popupSemDuplaVisible = false; popupSemDupla = null }
                }
                if (resumoExpanded && popupSaldoVisible) {
                    popupSaldo = showInfoPopup(
                        b.ivInfoSaldo,
                        getString(R.string.crg_footer_saldo_restante_exp)
                    ) { popupSaldoVisible = false; popupSaldo = null }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (requireActivity().isChangingConfigurations) return
        popupIntegral?.dismiss(); popupIntegral = null
        popupSemDupla?.dismiss(); popupSemDupla = null
        popupSaldo?.dismiss(); popupSaldo = null
    }

    override fun onResume() {
        super.onResume()
        val sHandle = findNavController().currentBackStackEntry?.savedStateHandle
        val mustRestart = sHandle?.get<Boolean>("RESTART_GANTT") == true
        if (mustRestart && !selfRestarted) {
            selfRestarted = true
            sHandle?.remove<Boolean>("RESTART_GANTT")
            val nav = findNavController()
            if (nav.currentDestination?.id == R.id.cronogramaGanttFragment) {
                nav.navigate(
                    R.id.cronogramaGanttFragment,
                    bundleOf("obraId" to args.obraId),
                    NavOptions.Builder()
                        .setPopUpTo(R.id.cronogramaGanttFragment, true)
                        .build()
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter.detachHeaderScroll()

        // Remove listener aditivo do header (fade) com binding nul-safe
        _binding?.let { GanttHeaderAnimator.disableScrollFade(it.headerRow) }
        _binding?.let {
            GanttHeaderAnimator.uninstallEarlyFinishGestures(
                it.headerScroll,
                it.rvGantt
            )
        }

        // Remova o listener com segurança (o VTO pode não estar "alive")
        footerGlobalLayoutListener?.let { l ->
            _binding?.cardResumoGantt?.viewTreeObserver?.let { vto ->
                if (vto.isAlive) vto.removeOnGlobalLayoutListener(l)
            }
            footerGlobalLayoutListener = null
        }

        pendingHideRunnable?.let { _binding?.progressGantt?.removeCallbacks(it) }
        pendingHideRunnable = null
        _binding?.progressGantt?.isVisible = false

        // limpa callbacks em handlers
        runCatching { _binding?.root?.handler?.removeCallbacksAndMessages(null) }
        runCatching { _binding?.headerScroll?.handler?.removeCallbacksAndMessages(null) }
        runCatching { _binding?.rvGantt?.handler?.removeCallbacksAndMessages(null) }

        // evita vazamento do adapter
        _binding?.rvGantt?.adapter = null

        _binding = null
    }
}