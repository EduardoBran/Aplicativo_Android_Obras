package com.luizeduardobrandao.obra.ui.cronograma

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Etapa
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentDetalheCronogramaBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.funcionario.FuncionarioViewModel
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.GanttUtils
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DetalheCronogramaFragment : Fragment() {

    private var _binding: FragmentDetalheCronogramaBinding? = null
    private val binding get() = _binding!!

    private val args: DetalheCronogramaFragmentArgs by navArgs()
    private val viewModel: CronogramaViewModel by viewModels()

    private val viewModelFuncionario: FuncionarioViewModel by viewModels()
    private val funcionariosCache = mutableListOf<Funcionario>()

    private var etapaAtual: Etapa? = null

    // Estado da barra de progresso (sobrevive à rotação)
    private var barAnimatedOnce: Boolean = false
    private var lastPct: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentDetalheCronogramaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Restaura estado da barra (evita reanimar após rotação)
        barAnimatedOnce = savedInstanceState?.getBoolean("det_bar_animated_once", false) ?: false
        lastPct = savedInstanceState?.getInt("det_bar_last_pct", 0) ?: 0
        // Se já temos valor salvo (rotação), pinta sem animar
        binding.progressStatusBar.setProgress(lastPct, animate = false)

        with(binding) {
            toolbarDetEtapa.setNavigationOnClickListener { findNavController().navigateUp() }

            // Carregar etapas para achar a etapa pelo ID
            viewModel.loadEtapas()

            // Carregar funcionários (para cálculo do valor)
            viewModelFuncionario.loadFuncionarios()

            // Observa funcionários e mantém cache
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModelFuncionario.state.collect { ui ->
                        if (ui is UiState.Success) {
                            funcionariosCache.clear()
                            // Para cálculo, considere TODOS (ativo/inativo). O cronograma pode ter sido criado antes.
                            funcionariosCache.addAll(ui.data)

                            // Se a etapa já está exibida, recalcule o valor agora
                            etapaAtual?.let { et ->
                                val novoValor = computeValorTotal(et)
                                binding.tvDetValor.text =
                                    if (novoValor == null) "-" else formatMoneyBR(novoValor)
                            }
                        }
                    }
                }
            }

            // Observa etapas e preenche UI
            lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.state.collect { ui ->
                        when (ui) {
                            is UiState.Success -> {
                                val et =
                                    ui.data.firstOrNull { it.id == args.etapaId } ?: return@collect
                                etapaAtual = et  // <<<<<<<<<< GUARDA A ETAPA ATUAL

                                toolbarDetEtapa.title = et.titulo

                                tvDetTitulo.text = et.titulo
                                tvDetDescricao.text = et.descricao?.ifBlank { "—" } ?: "—"

                                if (et.responsavelTipo == "EMPRESA") {
                                    tvDetFuncLabel.setText(R.string.det_empresa_label)  // "Nome da empresa"
                                    tvDetFunc.text = et.empresaNome?.ifBlank { "—" } ?: "—"
                                } else {
                                    tvDetFuncLabel.setText(R.string.cron_reg_func_hint) // "Funcionário(s)"
                                    tvDetFunc.text = et.funcionarios?.ifBlank { "—" } ?: "—"
                                }

                                // ► Valor (NOVO) - mesmas regras do Gantt, sem domingos
                                // Se os funcionários ainda não chegaram, computeValorTotal retorna null e mostramos "-"
                                // Quando a lista chegar (coletor acima), recalculamos e atualizamos tvDetValor.
                                val valorTotal = computeValorTotal(et)
                                tvDetValor.text =
                                    if (valorTotal == null) "-" else formatMoneyBR(valorTotal)

                                tvDetDataIni.text = et.dataInicio
                                tvDetDataFim.text = et.dataFim
                                tvDetStatus.text = et.status

                                // Calcula % da etapa (mesma regra do Gantt)
                                val pct = GanttUtils.calcularProgresso(
                                    et.diasConcluidos?.toSet() ?: emptySet(),
                                    et.dataInicio,
                                    et.dataFim
                                )
                                lastPct = pct

                                // Regra de animação: só anima na primeira entrada da tela
                                if (barAnimatedOnce) {
                                    binding.progressStatusBar.setProgress(pct, animate = false)
                                } else {
                                    // garante estado inicial e anima até o valor
                                    binding.progressStatusBar.setProgress(0, animate = false)
                                    binding.progressStatusBar.setProgress(pct, animate = true)
                                    barAnimatedOnce = true
                                }
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
    }

    /** --- Helpers de cálculo --- */
    private fun parseCsvNomes(csv: String?): List<String> =
        csv?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private val nfBR: java.text.NumberFormat =
        java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    private fun formatMoneyBR(v: Double?): String =
        if (v == null) "-" else nfBR.format(v)

    /** Regras (NÃO conta domingos):
     * - Empresa: usa empresaValor (se null → "-")
     * - Funcionários:
     *   - Diária:  diasUteis * salário
     *   - Semanal: ceil(diasUteis / 7.0)  * salário
     *   - Mensal:  ceil(diasUteis / 30.0) * salário
     *   - Tarefeiro: salário (uma vez)
     */
    private fun computeValorTotal(etapa: Etapa): Double? {
        if (etapa.responsavelTipo == "EMPRESA") {
            return etapa.empresaValor
        }

        val nomes = parseCsvNomes(etapa.funcionarios)
        if (etapa.responsavelTipo != "FUNCIONARIOS" || nomes.isEmpty()) return null

        val ini = GanttUtils.brToLocalDateOrNull(etapa.dataInicio)
        val fim = GanttUtils.brToLocalDateOrNull(etapa.dataFim)
        if (ini == null || fim == null || fim.isBefore(ini)) return null

        val diasUteis = GanttUtils
            .daysBetween(ini, fim).count { !GanttUtils.isSunday(it) }
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
                        tipo.contains("Semanal") -> ceil(diasUteis / 7.0).toInt() * salario

                tipo.contains("mensal") ||
                        tipo.contains("Mensal") -> ceil(diasUteis / 30.0).toInt() * salario

                tipo.contains("tarefeiro") ||
                        tipo.contains("tarefa") ||
                        tipo.contains("Terefeiro") -> salario

                else -> 0.0
            }
        }
        return total
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("det_bar_animated_once", barAnimatedOnce)
        outState.putInt("det_bar_last_pct", lastPct)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}