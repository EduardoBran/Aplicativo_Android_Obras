package com.luizeduardobrandao.obra.ui.resumo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.view.ViewGroup as AndroidViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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

        toolbarResumoObra.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Abas expansíveis (padrão)
        setupExpandable(
            containerResumo,
            headerAbaFuncionarios,
            contentAbaFuncionarios,
            ivArrowFuncionarios,
            isFunExpanded
        ) { isFunExpanded = it }
        setupExpandable(
            containerResumo,
            headerAbaNotas,
            contentAbaNotas,
            ivArrowNotas,
            isNotasExpanded
        ) { isNotasExpanded = it }
        setupExpandable(
            containerResumo,
            headerAbaCronograma,
            contentAbaCronograma,
            ivArrowCronograma,
            isCronExpanded
        ) { isCronExpanded = it }
        setupExpandable(
            containerResumo,
            headerAbaMateriais,
            contentAbaMateriais,
            ivArrowMateriais,
            isMatExpanded
        ) { isMatExpanded = it }
        setupExpandable(
            containerResumo,
            headerAbaSaldo,
            contentAbaSaldo,
            ivArrowSaldo,
            isSaldoExpanded
        ) { isSaldoExpanded = it }
        // ✅ Nova sub-aba: Aportes (dentro de Financeiro)
        setupExpandable(
            containerResumo,
            headerAbaAportes,
            contentAbaAportes,
            ivArrowAportes,
            isAportesExpanded
        ) { isAportesExpanded = it }

        // ── Funcionários → ResumoFuncionarioFragment
        btnAbrirFuncionarios.setOnClickListener {
            findNavController().navigate(
                ResumoFragmentDirections.actionResumoToFuncionario(args.obraId)
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
                title = getString(R.string.snack_warning),
                msg = getString(R.string.resumo_aporte_delete_confirm),
                btnText = getString(R.string.snack_button_yes),
                onAction = { viewModel.deleteAporte(aporte.aporteId) },
                btnNegativeText = getString(R.string.snack_button_no),
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
                            val funcQtd = resources.getQuantityString(
                                R.plurals.resumo_func_qtd,
                                res.countFuncionarios,
                                res.countFuncionarios
                            )
                            tvFuncCount.text = getString(R.string.resumo_func_count_mask, funcQtd)

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
                            binding.tvAportesHeader.text = resources.getQuantityString(
                                R.plurals.resumo_aportes_header_plural,
                                ordenados.size
                            )
                            binding.tvSaldoAjustadoLabel.text = resources.getQuantityString(
                                R.plurals.resumo_adjusted_balance_header_plural,
                                res.aportes.size
                            )

                            tvSaldoRestanteResumo.text = formatMoneyBR(res.saldoRestante)


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
                                getString(R.string.snack_error),
                                getString(ui.resId),
                                getString(R.string.snack_button_ok)
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
                            val paidList = list.filter { it.status.equals("Pago", true) }

                            val dueCount = dueList.size
                            val paidCount = paidList.size

                            val dueTotal = dueList.sumOf { it.valor }
                            val paidTotal = paidList.sumOf { it.valor }
                            val totalGeral = dueTotal + paidTotal

                            tvNotasDueCount.text = resources.getQuantityString(
                                R.plurals.resumo_materiais_qtd,
                                dueCount,
                                dueCount
                            )
                            tvNotasPaidCount.text = resources.getQuantityString(
                                R.plurals.resumo_materiais_qtd,
                                paidCount,
                                paidCount
                            )

                            tvNotasDueTotal.text = formatMoneyBR(dueTotal)
                            tvNotasPaidTotal.text = formatMoneyBR(paidTotal)
                            tvNotasTotalGeral.text = formatMoneyBR(totalGeral)
                        }

                        is UiState.ErrorRes -> {
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

    /*──────────────  Helpers  ──────────────*/
    private fun setupExpandable(
        containerRoot: AndroidViewGroup,
        header: View,
        content: View,
        arrow: ImageView,
        startExpanded: Boolean,
        onStateChange: (Boolean) -> Unit
    ) {
        fun applyState(expanded: Boolean, animate: Boolean) {
            if (animate) {
                androidx.transition.TransitionManager.beginDelayedTransition(
                    containerRoot,
                    androidx.transition.AutoTransition().apply { duration = 180 }
                )
            }
            content.isVisible = expanded
            arrow.animate().rotation(if (expanded) 180f else 0f).setDuration(180).start()
            onStateChange(expanded)
        }
        content.post { applyState(startExpanded, animate = false) }
        header.setOnClickListener { applyState(!content.isVisible, animate = true) }
    }

    private fun formatMoneyBR(value: Double): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_FUN_EXP, isFunExpanded)
        outState.putBoolean(KEY_NOTAS_EXP, isNotasExpanded)
        outState.putBoolean(KEY_CRON_EXP, isCronExpanded)
        outState.putBoolean(KEY_MAT_EXP, isMatExpanded)
        outState.putBoolean(KEY_SALDO_EXP, isSaldoExpanded)
        outState.putBoolean(KEY_APORTES_EXP, isAportesExpanded)
    }

    override fun onDestroyView() {
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
    }
}