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
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
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
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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

    // Estado das abas
    private var isFunExpanded = false
    private var isNotasExpanded = false
    private var isCronExpanded = false
    private var isMatExpanded = false
    private var isSaldoExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            isFunExpanded = it.getBoolean(KEY_FUN_EXP, false)
            isNotasExpanded = it.getBoolean(KEY_NOTAS_EXP, false)
            isCronExpanded = it.getBoolean(KEY_CRON_EXP, false)
            isMatExpanded = it.getBoolean(KEY_MAT_EXP, false)
            isSaldoExpanded = it.getBoolean(KEY_SALDO_EXP, false)
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

        observeResumo()      // funcionários + saldos
        observeNotas()       // notas por status
        observeCronograma()  // etapas por status
        observeMateriais()   // materiais por status

        // Dispara os carregamentos
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

                            val totalFunStr = getString(R.string.money_mask, res.totalMaoDeObra)
                            tvFuncTotal.text = getString(R.string.resumo_func_total_mask, totalFunStr)

                            // Saldos
                            tvSaldoInicialResumo.text =
                                getString(R.string.money_mask, res.saldoInicial)
                            tvSaldoAjustadoResumo.text =
                                getString(R.string.money_mask, res.saldoAjustado)
                            tvSaldoRestanteResumo.text =
                                getString(R.string.money_mask, res.saldoRestante)
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

    private fun observeNotas() = with(binding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                notasViewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Success -> {
                            val list: List<Nota> = ui.data

                            // "Em aberto": A Receber OR A Pagar
                            val dueList = list.filter {
                                it.status.equals(
                                    "A Receber",
                                    true
                                ) || it.status.equals("A Pagar", true)
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

                            tvNotasDueTotal.text = getString(R.string.money_mask, dueTotal)
                            tvNotasPaidTotal.text = getString(R.string.money_mask, paidTotal)
                            tvNotasTotalGeral.text = getString(R.string.money_mask, totalGeral)
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
                                it.status.equals(
                                    MaterialPagerAdapter.STATUS_ATIVO,
                                    true
                                )
                            }
                            val inativos = list.count {
                                it.status.equals(
                                    MaterialPagerAdapter.STATUS_INATIVO,
                                    true
                                )
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
                TransitionManager.beginDelayedTransition(
                    containerRoot,
                    AutoTransition().apply { duration = 180 }
                )
            }
            content.isVisible = expanded
            arrow.animate().rotation(if (expanded) 180f else 0f).setDuration(180).start()
            onStateChange(expanded)
        }
        content.post { applyState(startExpanded, animate = false) }
        header.setOnClickListener { applyState(!content.isVisible, animate = true) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_FUN_EXP, isFunExpanded)
        outState.putBoolean(KEY_NOTAS_EXP, isNotasExpanded)
        outState.putBoolean(KEY_CRON_EXP, isCronExpanded)
        outState.putBoolean(KEY_MAT_EXP, isMatExpanded)
        outState.putBoolean(KEY_SALDO_EXP, isSaldoExpanded)
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
    }
}