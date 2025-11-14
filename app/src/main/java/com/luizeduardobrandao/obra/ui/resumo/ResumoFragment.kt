package com.luizeduardobrandao.obra.ui.resumo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.view.ViewGroup as AndroidViewGroup
import android.view.ViewTreeObserver
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentResumoBinding
import com.luizeduardobrandao.obra.ui.cronograma.CronogramaViewModel
import com.luizeduardobrandao.obra.ui.cronograma.adapter.CronogramaPagerAdapter
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.extensions.bindScrollToBottomFabForResumo
import com.luizeduardobrandao.obra.ui.material.MaterialViewModel
import com.luizeduardobrandao.obra.ui.material.adapter.MaterialPagerAdapter
import com.luizeduardobrandao.obra.ui.notas.NotasViewModel
import com.luizeduardobrandao.obra.ui.resumo.adapter.AporteAdapter
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class ResumoFragment : Fragment() {

    private var _binding: FragmentResumoBinding? = null
    private val binding get() = _binding!!

    private val args: ResumoFragmentArgs by navArgs()

    // ViewModels
    private val viewModel: ResumoViewModel by viewModels()
    private val notasViewModel: NotasViewModel by viewModels()
    private val cronogramaViewModel: CronogramaViewModel by viewModels()
    private val materialViewModel: MaterialViewModel by viewModels()

    // Adapter de aportes
    private lateinit var aporteAdapter: AporteAdapter

    // Controle do toast ao adicionar aporte
    private var lastAporteCount = 0
    private var aportesInitialized = false

    // Estado das abas
    private var isFunExpanded = false
    private var isNotasExpanded = false
    private var isCronExpanded = false
    private var isMatExpanded = false
    private var isSaldoExpanded = false
    private var isAportesExpanded = false

    // Comportamento do FAB
    private var resumoFabGlobalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    // Flag desaperecer imagem modo horizontal
    private var lastHeroVisible: Boolean = false
    private var canShowHeroInThisContext: Boolean = true

    // estado do FAB após rotação
    private var restoreFabVisible: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            isFunExpanded = it.getBoolean(KEY_FUN_EXP, false)
            isNotasExpanded = it.getBoolean(KEY_NOTAS_EXP, false)
            isCronExpanded = it.getBoolean(KEY_CRON_EXP, false)
            isMatExpanded = it.getBoolean(KEY_MAT_EXP, false)
            isSaldoExpanded = it.getBoolean(KEY_SALDO_EXP, false)
            isAportesExpanded = it.getBoolean(KEY_APORTES_EXP, false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResumoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        restoreFabVisible = savedInstanceState?.getBoolean(KEY_FAB_VISIBLE)

        toolbarResumoObra.setNavigationOnClickListener { findNavController().navigateUp() }

        // Anchor do botão custom (actionView)
        val exportItem = toolbarResumoObra.menu.findItem(R.id.action_export_summary)
        val btnExport = exportItem.actionView?.findViewById<View>(R.id.btnExportSummary)
        btnExport?.setOnClickListener {
            // Safe Args: navega passando o obraId atual
            findNavController().navigate(
                ResumoFragmentDirections.actionResumoToExport(args.obraId)
            )
        }

        // (opcional) fallback via listener tradicional
        toolbarResumoObra.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_export_summary -> {
                    findNavController().navigate(
                        ResumoFragmentDirections.actionResumoToExport(args.obraId)
                    )
                    true
                }

                else -> false
            }
        }

        // ❶ Decida se o hero deve aparecer neste contexto (portrait/tablet) ou sumir (landscape phone)
        val showHeroNow = resources.getBoolean(R.bool.show_hero)
        canShowHeroInThisContext = showHeroNow
        imgResumo.visibility = if (showHeroNow) View.VISIBLE else View.GONE
        lastHeroVisible = showHeroNow

        // ❷ Leia o estado anterior para decidir se anima quando voltar de landscape -> portrait
        val prevHeroVisible = savedInstanceState?.getBoolean(KEY_PREV_HERO_VISIBLE)

        // ❸ Só anima se:
        //    - for a primeira vez (savedInstanceState == null), e o hero estiver visível
        //    - OU se antes estava oculto (landscape) e agora ficou visível (portrait)
        val shouldAnimateHero =
            showHeroNow && (savedInstanceState == null || prevHeroVisible == false)
        if (shouldAnimateHero) {
            runHeroEnterAnimation()
        }

        // Abas expansíveis (padrão)
        setupExpandable(
            containerResumo,
            headerAbaFuncionarios,
            contentAbaFuncionarios,
            ivArrowFuncionarios,
            isFunExpanded
        ) {
            isFunExpanded = it
            updateHeroVisibility(animate = true)
        }
        setupExpandable(
            containerResumo,
            headerAbaNotas,
            contentAbaNotas,
            ivArrowNotas,
            isNotasExpanded
        ) {
            isNotasExpanded = it
            updateHeroVisibility(animate = true)
        }
        setupExpandable(
            containerResumo,
            headerAbaCronograma,
            contentAbaCronograma,
            ivArrowCronograma,
            isCronExpanded
        ) {
            isCronExpanded = it
            updateHeroVisibility(animate = true)
        }
        setupExpandable(
            containerResumo,
            headerAbaMateriais,
            contentAbaMateriais,
            ivArrowMateriais,
            isMatExpanded
        ) {
            isMatExpanded = it
            updateHeroVisibility(animate = true)
        }
        setupExpandable(
            containerResumo,
            headerAbaSaldo,
            contentAbaSaldo,
            ivArrowSaldo,
            isSaldoExpanded,
            scrollToEndOnExpand = true   // ⬅️ SOMENTE AQUI
        ) {
            isSaldoExpanded = it
            updateHeroVisibility(animate = true)
        }
        // ✅ Nova sub-aba: Aportes (dentro de Financeiro)
        setupExpandable(
            containerResumo,
            headerAbaAportes,
            contentAbaAportes,
            ivArrowAportes,
            isAportesExpanded
        ) {
            isAportesExpanded = it
            updateHeroVisibility(animate = true)
        }

        // Ajusta a visibilidade inicial da imagem conforme o estado restaurado das abas (sem animação de layout aqui)
        updateHeroVisibility(animate = false)

        // FAB de rolagem do Resumo – visível somente quando houver conteúdo rolável
        resumoFabGlobalLayoutListener = bindScrollToBottomFabForResumo(
            fab = binding.fabScrollDownResumo,
            scrollView = binding.scrollResumo
        )

        updateFabForScrollState()

        // ── Funcionários → ResumoFuncionarioFragment
        btnAbrirFuncionarios.setOnClickListener {
            findNavController().navigate(
                ResumoFragmentDirections.actionResumoToFuncionario(args.obraId)
            )
        }
        // 👉 "Abrir" da faixa VERDE (Ativo) → FuncionarioFragment na aba 0
        btnAbrirFuncAtivos.setOnClickListener {
            findNavController().navigate(
                ResumoFragmentDirections.actionResumoToFuncionarioTabs(
                    args.obraId, /* startTab = */
                    0
                )
            )
        }

        // 👉 "Abrir" da faixa VERMELHA (Inativo) → FuncionarioFragment na aba 1
        btnAbrirFuncInativos.setOnClickListener {
            findNavController().navigate(
                ResumoFragmentDirections.actionResumoToFuncionarioTabs(
                    args.obraId, /* startTab = */
                    1
                )
            )
        }
        // ── Notas → abas corretas (0 = A Receber, 1 = Pago)
        btnAbrirNotasDue.setOnClickListener {
            findNavController().navigate(
                ResumoFragmentDirections.actionResumoToNotas(args.obraId, 0)
            )
        }
        btnAbrirNotasPaid.setOnClickListener {
            findNavController().navigate(
                ResumoFragmentDirections.actionResumoToNotas(args.obraId, 1)
            )
        }
        // ── Notas → ResumoNotasFragment
        btnAbrirNotasGeral.setOnClickListener {
            findNavController().navigate(
                ResumoFragmentDirections.actionResumoToResumoNotas(args.obraId)
            )
        }
        // ── Cronograma → abas corretas (0 = Pendente, 1 = Andamento, 2 = Concluído)
        btnAbrirCronPendentes.setOnClickListener {
            findNavController().navigate(
                ResumoFragmentDirections.actionResumoToCronograma(args.obraId, 0)
            )
        }
        btnAbrirCronAndamento.setOnClickListener {
            findNavController().navigate(
                ResumoFragmentDirections.actionResumoToCronograma(args.obraId, 1)
            )
        }
        btnAbrirCronConcluidos.setOnClickListener {
            findNavController().navigate(
                ResumoFragmentDirections.actionResumoToCronograma(args.obraId, 2)
            )
        }
        // ── Materiais → abas corretas (0 = Ativo, 1 = Inativo)
        btnAbrirMateriaisAtivos.setOnClickListener {
            findNavController().navigate(
                ResumoFragmentDirections.actionResumoToMaterial(args.obraId, 0)
            )
        }
        btnAbrirMateriaisInativos.setOnClickListener {
            findNavController().navigate(
                ResumoFragmentDirections.actionResumoToMaterial(args.obraId, 1)
            )
        }

        // RecyclerView de Aportes
        aporteAdapter = AporteAdapter { aporte ->
            // Confirmação antes de excluir
            showSnackbarFragment(
                type = Constants.SnackType.WARNING.name,
                title = getString(R.string.generic_warning),
                msg = getString(R.string.resumo_aporte_delete_confirm),
                btnText = getString(R.string.generic_yes_upper_case),
                onAction = { viewModel.deleteAporte(aporte.aporteId) },
                btnNegativeText = getString(R.string.generic_no_upper_case),
                onNegative = { /* no-op */ }
            )
        }
        rvAportes.layoutManager = LinearLayoutManager(requireContext())
        rvAportes.adapter = aporteAdapter

        observeResumo()                // funcionários + financeiros (inclui aportes)
        observeNotas()                 // notas por status
        observeCronograma()            // etapas por status
        observeMateriais()             // materiais por status
        observeDeleteAporteState()     // feedback da exclusão de aporte


        // Dispara os carregamentos existentes
        notasViewModel.loadNotas()
        cronogramaViewModel.loadEtapas()
        materialViewModel.loadMateriais()
    }

    /*──────────────  Observers  ──────────────*/
    private fun observeResumo() = with(binding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Loading -> {
                            progressResumo.isVisible = true
                            containerResumo.isVisible = false
                        }

                        is UiState.Success -> {
                            progressResumo.isVisible = false
                            containerResumo.isVisible = true

                            val res = ui.data

                            // Funcionários
                            tvFuncAtivosCount.text =
                                if (res.countFuncAtivos == 0)
                                    getString(R.string.func_none_active)
                                else
                                    resources.getQuantityString(
                                        R.plurals.func_count_plural,
                                        res.countFuncAtivos,
                                        res.countFuncAtivos
                                    )
                            tvFuncInativosCount.text =
                                if (res.countFuncInativos == 0)
                                    getString(R.string.func_none_inactive)
                                else
                                    resources.getQuantityString(
                                        R.plurals.func_count_plural,
                                        res.countFuncInativos,
                                        res.countFuncInativos
                                    )
                            tvFuncTotalGeral.text = resources.getQuantityString(
                                R.plurals.func_total_label,
                                res.countFuncionarios,
                                res.countFuncionarios
                            )

                            val totalFunStr = formatMoneyBR(res.totalMaoDeObra)
                            tvFuncTotal.text =
                                getString(R.string.resumo_func_total_mask, totalFunStr)

                            // Financeiro
                            tvSaldoInicialResumo.text = formatMoneyBR(res.saldoInicial)

                            // Saldo com aportes
                            if (res.aportes.isEmpty()) {
                                tvSaldoAjustadoResumo.text = "-"
                            } else {
                                val saldoComAportes = res.saldoInicial + res.totalAportes
                                tvSaldoAjustadoResumo.text = formatMoneyBR(saldoComAportes)
                            }


                            // Lista de aportes
                            val ordenados = res.aportes.sortedByDescending { it.data } // data ISO
                            aporteAdapter.submitList(ordenados)
                            tvAportesEmpty.isVisible = ordenados.isEmpty()
                            // Títulos com plural
                            binding.tvAportesHeader.text = if (ordenados.size == 1)
                                getString(R.string.aporte_singular)
                            else
                                getString(R.string.aporte_plural)
                            binding.tvAportesHeaderTitle.text = if (ordenados.size == 1)
                                getString(R.string.aporte_singular)
                            else
                                getString(R.string.aporte_plural)
                            binding.tvSaldoAjustadoLabel.text = resources.getQuantityString(
                                R.plurals.resumo_adjusted_balance_header_plural,
                                res.aportes.size
                            )

                            tvSaldoRestanteResumo.text = formatMoneyBR(res.saldoRestante)

                            // vermelho se negativo, cor padrão do tema caso contrário
                            val normalColor = ContextCompat.getColor(
                                requireContext(),
                                R.color.md_theme_light_onSurfaceVariant
                            )
                            val errorColor = ContextCompat.getColor(
                                requireContext(),
                                R.color.md_theme_light_error
                            )

                            tvSaldoRestanteResumo.setTextColor(
                                if (res.saldoRestante < 0) errorColor else normalColor
                            )


                            // TOAST quando um novo aporte é adicionado (evita disparar no 1º load)
                            if (aportesInitialized && ordenados.size > lastAporteCount) {
                                android.widget.Toast.makeText(
                                    requireContext(),
                                    getString(R.string.aporte_toast_added),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            lastAporteCount = ordenados.size
                            aportesInitialized = true
                        }

                        is UiState.ErrorRes -> {
                            progressResumo.isVisible = false
                            showSnackbarFragment(
                                Constants.SnackType.ERROR.name,
                                getString(R.string.generic_error),
                                getString(ui.resId),
                                getString(R.string.generic_ok_upper_case)
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    private fun observeDeleteAporteState() = with(binding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deleteAporteState.collect { ui ->
                    when (ui) {
                        is UiState.Loading -> {
                            // Feedback simples de loading
                            progressResumo.isVisible = true
                        }

                        is UiState.Success -> {
                            progressResumo.isVisible = false
                            android.widget.Toast.makeText(
                                requireContext(),
                                getString(R.string.aporte_deleted_toast),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            viewModel.resetDeleteAporteState()
                        }

                        is UiState.ErrorRes -> {
                            progressResumo.isVisible = false
                            showSnackbarFragment(
                                Constants.SnackType.ERROR.name,
                                getString(R.string.generic_error),
                                getString(ui.resId),
                                getString(R.string.generic_ok_upper_case)
                            )
                            viewModel.resetDeleteAporteState()
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    private fun observeNotas() = with(binding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                notasViewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Success -> {
                            val list: List<Nota> = ui.data

                            // "Em aberto": A Receber OR A Pagar
                            val dueList = list.filter {
                                it.status.equals("A Receber", true) ||
                                        it.status.equals("A Pagar", true)
                            }
                            val paidList =
                                list.filter { it.status.equals("Pago", true) }

                            val dueCount = dueList.size
                            val paidCount = paidList.size

                            val dueTotal = dueList.sumOf { it.valor }
                            val paidTotal = paidList.sumOf { it.valor }
                            val totalGeral = dueTotal + paidTotal

                            tvNotasDueCount.text =
                                if (dueCount == 0)
                                    getString(R.string.nota_none)
                                else
                                    resources.getQuantityString(
                                        R.plurals.resumo_notas_qtd, // ✅ agora usa o plural de notas
                                        dueCount,
                                        dueCount
                                    )
                            tvNotasPaidCount.text =
                                if (paidCount == 0)
                                    getString(R.string.nota_none)
                                else
                                    resources.getQuantityString(
                                        R.plurals.resumo_notas_qtd,
                                        paidCount,
                                        paidCount
                                    )

                            tvNotasDueTotal.isVisible = dueCount > 0
                            if (dueCount > 0) {
                                tvNotasDueTotal.text = formatMoneyBR(dueTotal)
                            } else {
                                tvNotasDueTotal.text = "" // evita sobra
                            }
                            tvNotasPaidTotal.isVisible = paidCount > 0
                            if (paidCount > 0) {
                                tvNotasPaidTotal.text = formatMoneyBR(paidTotal)
                            } else {
                                tvNotasPaidTotal.text = ""
                            }

                            val valorTotal = formatMoneyBR(totalGeral)
                            tvNotasTotalGeral.text =
                                getString(R.string.nota_total_geral, valorTotal)
                        }

                        is UiState.ErrorRes -> {
                            showSnackbarFragment(
                                Constants.SnackType.ERROR.name,
                                getString(R.string.generic_error),
                                getString(ui.resId),
                                getString(R.string.generic_ok_upper_case)
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    private fun observeCronograma() = with(binding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                cronogramaViewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Success -> {
                            val list = ui.data
                            val cPend =
                                list.count { it.status == CronogramaPagerAdapter.STATUS_PENDENTE }
                            val cAnd =
                                list.count { it.status == CronogramaPagerAdapter.STATUS_ANDAMENTO }
                            val cConc =
                                list.count { it.status == CronogramaPagerAdapter.STATUS_CONCLUIDO }

                            tvCronPendentes.text = if (cPend == 0)
                                getString(R.string.cron_none_pending)
                            else
                                resources.getQuantityString(
                                    R.plurals.cron_pending_count,
                                    cPend,
                                    cPend
                                )

                            tvCronAndamento.text = if (cAnd == 0)
                                getString(R.string.cron_none_progress)
                            else
                                resources.getQuantityString(
                                    R.plurals.cron_progress_count,
                                    cAnd,
                                    cAnd
                                )

                            tvCronConcluidos.text = if (cConc == 0)
                                getString(R.string.cron_none_done)
                            else
                                resources.getQuantityString(R.plurals.cron_done_count, cConc, cConc)
                        }

                        is UiState.ErrorRes -> {
                            showSnackbarFragment(
                                Constants.SnackType.ERROR.name,
                                getString(R.string.generic_error),
                                getString(ui.resId),
                                getString(R.string.generic_ok_upper_case)
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    private fun observeMateriais() = with(binding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                materialViewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Success -> {
                            val list = ui.data
                            val ativos = list.count {
                                it.status.equals(MaterialPagerAdapter.STATUS_ATIVO, true)
                            }
                            val inativos = list.count {
                                it.status.equals(MaterialPagerAdapter.STATUS_INATIVO, true)
                            }

                            tvMateriaisAtivos.text = if (ativos == 0)
                                getString(R.string.material_none_active)
                            else
                                resources.getQuantityString(
                                    R.plurals.material_active_count,
                                    ativos,
                                    ativos
                                )

                            tvMateriaisInativos.text = if (inativos == 0)
                                getString(R.string.material_none_inactive)
                            else
                                resources.getQuantityString(
                                    R.plurals.material_inactive_count,
                                    inativos,
                                    inativos
                                )
                        }

                        is UiState.ErrorRes -> {
                            showSnackbarFragment(
                                Constants.SnackType.ERROR.name,
                                getString(R.string.generic_error),
                                getString(ui.resId),
                                getString(R.string.generic_ok_upper_case)
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    /*──────────────  Helpers  ──────────────*/
    private fun setupExpandable(
        containerRoot: AndroidViewGroup,
        header: View,
        content: View,
        arrow: ImageView,
        startExpanded: Boolean,
        scrollToEndOnExpand: Boolean = false,
        onStateChange: (Boolean) -> Unit
    ) {

        fun applyState(expanded: Boolean, animate: Boolean) {
            // 1) Atualiza flags/hero
            onStateChange(expanded)

            if (animate) {
                // Não deixa piscar durante a transição
                hideFabStrong(binding.fabScrollDownResumo)

                val transition = androidx.transition.AutoTransition().apply { duration = 180 }
                transition.addListener(object : androidx.transition.Transition.TransitionListener {
                    override fun onTransitionEnd(t: androidx.transition.Transition) {
                        t.removeListener(this)

                        // Mantém seu comportamento de rolar após expandir/contrair
                        binding.scrollResumo.post {
                            if (expanded) {
                                if (scrollToEndOnExpand) {
                                    val bottom = (binding.scrollResumo.getChildAt(0).height -
                                            binding.scrollResumo.height + binding.scrollResumo.paddingBottom)
                                        .coerceAtLeast(0)
                                    binding.scrollResumo.smoothScrollTo(0, bottom)
                                } else {
                                    val rect = android.graphics.Rect()
                                    content.getDrawingRect(rect)
                                    binding.scrollResumo.offsetDescendantRectToMyCoords(
                                        content,
                                        rect
                                    )

                                    val rawTargetY =
                                        rect.bottom - binding.scrollResumo.height + binding.scrollResumo.paddingBottom
                                    val maxScroll =
                                        (binding.scrollResumo.getChildAt(0).height - binding.scrollResumo.height).coerceAtLeast(
                                            0
                                        )
                                    binding.scrollResumo.smoothScrollTo(
                                        0,
                                        rawTargetY.coerceIn(0, maxScroll)
                                    )
                                }
                            }

                            // Decida FAB só DEPOIS do smoothScrollTo e do layout assentar
                            updateFabForScrollState()
                        }
                    }

                    override fun onTransitionStart(t: androidx.transition.Transition) {}
                    override fun onTransitionCancel(t: androidx.transition.Transition) {
                        t.removeListener(this)
                    }

                    override fun onTransitionPause(t: androidx.transition.Transition) {}
                    override fun onTransitionResume(t: androidx.transition.Transition) {}
                })

                androidx.transition.TransitionManager.beginDelayedTransition(
                    containerRoot,
                    transition
                )
            }

            // 3) Visibilidade do conteúdo e seta (inalterado)
            content.isVisible = expanded
            arrow.animate().rotation(if (expanded) 180f else 0f).setDuration(180).start()

            // 4) Sem animação (estado inicial/restaurado): mantém o comportamento de rolagem original
            if (!animate) {
                hideFabStrong(binding.fabScrollDownResumo)
                content.post {
                    if (expanded) {
                        if (scrollToEndOnExpand) {
                            // ⚠️ Restaurando estado (ex.: após rotação) e a aba Financeiro já estava aberta:
                            // NÃO levar para o final aqui. Só fazemos isso quando o usuário abrir a aba
                            // (no caminho animate = true). Portanto, intencionalmente não rolamos.
                        } else {
                            val rect = android.graphics.Rect()
                            content.getDrawingRect(rect)
                            binding.scrollResumo.offsetDescendantRectToMyCoords(content, rect)

                            val rawTargetY = rect.bottom - binding.scrollResumo.height +
                                    binding.scrollResumo.paddingBottom
                            val maxScroll = (binding.scrollResumo.getChildAt(0).height -
                                    binding.scrollResumo.height).coerceAtLeast(0)
                            binding.scrollResumo.smoothScrollTo(
                                0,
                                rawTargetY.coerceIn(0, maxScroll)
                            )
                        }
                    }
                    // Decide FAB somente depois
                    updateFabForScrollState()
                }
            }
        }

        content.post { applyState(startExpanded, animate = false) }
        header.setOnClickListener { applyState(!content.isVisible, animate = true) }
    }

    // Formata saída dos valores
    private fun formatMoneyBR(value: Double): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

    // Controla exibição do FAB
    private fun updateFabForScrollState() {
        val sv = binding.scrollResumo
        val fab = binding.fabScrollDownResumo

        // Decide após o próximo frame (layout estabilizado)
        sv.post {
            val child = sv.getChildAt(0)
            val hasOverflow = child != null && child.height > sv.height

            if (!hasOverflow) {
                // Sem overflow: some com o FAB e DESLIGA todos os listeners
                fab.isVisible = false
                sv.setOnScrollChangeListener(
                    null as androidx.core.widget.NestedScrollView.OnScrollChangeListener?
                )
                resumoFabGlobalLayoutListener?.let {
                    sv.viewTreeObserver.removeOnGlobalLayoutListener(it)
                    resumoFabGlobalLayoutListener = null
                }
            } else {
                // Com overflow: rebind para resetar qualquer estado anterior
                sv.setOnScrollChangeListener(
                    null as androidx.core.widget.NestedScrollView.OnScrollChangeListener?
                )
                resumoFabGlobalLayoutListener?.let {
                    sv.viewTreeObserver.removeOnGlobalLayoutListener(it)
                    resumoFabGlobalLayoutListener = null
                }
                resumoFabGlobalLayoutListener = bindScrollToBottomFabForResumo(
                    fab = fab,
                    scrollView = sv
                )

                // Visibilidade inicial correta: só mostra se dá para rolar PARA BAIXO
                // Se houver um estado salvo da rotação, ele tem prioridade (apenas uma vez).
                val initialOverride = restoreFabVisible
                restoreFabVisible = null  // consome o override (só na 1ª vez)
                val wantShow = initialOverride ?: sv.canScrollVertically(1)

                if (wantShow) {
                    showFabStrong(fab)
                } else {
                    hideFabStrong(fab)
                }
            }
        }
    }

    private fun showFabStrong(fab: View) {
        fab.animate().cancel()
        fab.clearAnimation()
        // Se for Extended FAB, garanta que volte ao estado "extendido"
        (fab as? com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton)?.let {
            it.extend()   // volta do shrink
            it.show()     // garante visibilidade com animação nativa (estado interno ok)
        }
        // Se for FAB normal
        (fab as? com.google.android.material.floatingactionbutton.FloatingActionButton)?.show()
        // Se não for nenhum dos dois, faz o fallback "forte"
        if (fab !is com.google.android.material.floatingactionbutton.FloatingActionButton &&
            fab !is com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
        ) {
            fab.visibility = View.VISIBLE
        }
        // Zera qualquer escala/resíduo de animação
        fab.scaleX = 1f
        fab.scaleY = 1f
        fab.alpha = 1f
    }

    private fun hideFabStrong(fab: View) {
        fab.animate().cancel()
        fab.clearAnimation()
        // Se for Extended FAB, dá show+shrink ou apenas hide; escolha consistente
        (fab as? com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton)?.hide()
        // Se for FAB normal
        (fab as? com.google.android.material.floatingactionbutton.FloatingActionButton)?.hide()
        // Fallback
        if (fab !is com.google.android.material.floatingactionbutton.FloatingActionButton &&
            fab !is com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
        ) {
            fab.visibility = View.GONE
        }
        // Garante que não “volte pequeno” na próxima vez
        fab.scaleX = 1f
        fab.scaleY = 1f
        fab.alpha = 0f
    }

    // ——— Animação de entrada da imagem ———
    private fun runHeroEnterAnimation() = with(binding) {
        if (!imgResumo.isVisible) return@with

        val interp = FastOutSlowInInterpolator()
        val dy = 16f * resources.displayMetrics.density

        imgResumo.alpha = 0f
        imgResumo.translationY = -dy

        imgResumo.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .setStartDelay(40L)
            .setInterpolator(interp)
            .start()
    }

    // ——— Controle de visibilidade da hero conforme abas ———
    private fun updateHeroVisibility(animate: Boolean) = with(binding) {

        // Se neste contexto (ex.: landscape phone) o hero não deve aparecer, garanta GONE e saia
        if (!canShowHeroInThisContext) {
            if (imgResumo.isVisible) {
                if (animate) {
                    imgResumo.animate()
                        .alpha(0f)
                        .setDuration(120L)
                        .withEndAction {
                            imgResumo.alpha = 1f
                            imgResumo.isVisible = false
                        }
                        .start()
                } else {
                    imgResumo.clearAnimation()
                    imgResumo.isVisible = false
                    imgResumo.alpha = 1f
                }
            }
            return@with
        }

        val anyExpanded =
            isFunExpanded || isNotasExpanded || isCronExpanded ||
                    isMatExpanded || isSaldoExpanded || isAportesExpanded

        if (anyExpanded) {
            if (imgResumo.isVisible) {
                if (animate) {
                    // Fade-out curto
                    imgResumo.animate()
                        .alpha(0f)
                        .setDuration(120L)
                        .withEndAction {
                            imgResumo.alpha = 1f
                            imgResumo.isVisible = false
                        }
                        .start()
                } else {
                    // Sem animação (ex.: estado restaurado/inicial)
                    imgResumo.clearAnimation()
                    imgResumo.isVisible = false
                    imgResumo.alpha = 1f
                }
            }
        } else {
            if (!imgResumo.isVisible) {
                imgResumo.isVisible = true
                if (animate) {
                    // Reaparecendo quando TODAS as abas estão fechadas
                    runHeroEnterAnimation()
                }
            } else {
                // Ao entrar no fragment, você chamou runHeroEnterAnimation() manualmente.
                // Só repita aqui se explicitamente pedir animação.
                if (animate) runHeroEnterAnimation()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_FUN_EXP, isFunExpanded)
        outState.putBoolean(KEY_NOTAS_EXP, isNotasExpanded)
        outState.putBoolean(KEY_CRON_EXP, isCronExpanded)
        outState.putBoolean(KEY_MAT_EXP, isMatExpanded)
        outState.putBoolean(KEY_SALDO_EXP, isSaldoExpanded)
        outState.putBoolean(KEY_APORTES_EXP, isAportesExpanded)

        outState.putBoolean(KEY_PREV_HERO_VISIBLE, lastHeroVisible)
        outState.putBoolean(KEY_FAB_VISIBLE, binding.fabScrollDownResumo.isShown)
    }

    override fun onDestroyView() {
        // Remover listeners do FAB/scroll para evitar leaks
        binding.scrollResumo.setOnScrollChangeListener(
            null as androidx.core.widget.NestedScrollView.OnScrollChangeListener?
        )
        binding.fabScrollDownResumo.setOnClickListener(null)
        resumoFabGlobalLayoutListener?.let {
            binding.scrollResumo.viewTreeObserver.removeOnGlobalLayoutListener(it)
            resumoFabGlobalLayoutListener = null
        }

        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_FUN_EXP = "fun_expanded"
        private const val KEY_NOTAS_EXP = "notas_expanded"
        private const val KEY_CRON_EXP = "cron_expanded"
        private const val KEY_MAT_EXP = "mat_expanded"
        private const val KEY_SALDO_EXP = "saldo_expanded"
        private const val KEY_APORTES_EXP = "aportes_expanded"

        private const val KEY_PREV_HERO_VISIBLE = "prevHeroVisible"
        private const val KEY_FAB_VISIBLE = "fab_visible"
    }
}