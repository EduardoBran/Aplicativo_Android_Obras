package com.luizeduardobrandao.obra.ui.resumo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.*
import com.luizeduardobrandao.obra.data.repository.*
import com.luizeduardobrandao.obra.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class ExportSummaryViewModel @Inject constructor(
    repoObra: ObraRepository,
    repoFun: FuncionarioRepository,
    repoNota: NotaRepository,
    repoCron: CronogramaRepository,
    repoMat: MaterialRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val obraId: String =
        savedStateHandle["obraId"]
            ?: error("Safe-Args deve conter obraId em ExportSummaryFragment")

    data class FuncionarioComTotal(
        val funcionario: Funcionario,
        val totalPago: Double
    )

    data class ExportSummaryData(
        val obra: Obra,

        // Funcionários
        val funcionariosAtivos: List<FuncionarioComTotal>,
        val funcionariosInativos: List<FuncionarioComTotal>,
        val totalGastoFuncionarios: Double,

        // Notas
        val notasAReceber: List<Nota>,
        val notasPagas: List<Nota>,
        val totalNotasAReceber: Double,
        val totalNotasPagas: Double,
        val totalNotasGeral: Double,

        // Cronograma
        val cronPendentes: List<Etapa>,
        val cronAndamento: List<Etapa>,
        val cronConcluidos: List<Etapa>,
        val countCronPendentes: Int,
        val countCronAndamento: Int,
        val countCronConcluidos: Int,

        // Materiais
        val materiaisAtivos: List<Material>,
        val materiaisInativos: List<Material>,
        val totalMateriaisAtivos: Int,
        val totalMateriaisInativos: Int,
        val totalMateriaisGeral: Int,

        // Aportes / Saldos
        val aportes: List<Aporte>,
        val totalAportes: Double,
        val saldoInicial: Double,
        val saldoComAportes: Double,
        val saldoRestante: Double
    )

    private val _state = MutableStateFlow<UiState<ExportSummaryData>>(UiState.Loading)
    val state: StateFlow<UiState<ExportSummaryData>> = _state.asStateFlow()

    init {
        // Flows individuais
        val obraFlow = repoObra.observeObras()
            .map { list -> list.firstOrNull { it.obraId == obraId } }

        val funcionariosFlow = repoFun.observeFuncionarios(obraId)
        val totalPagoPorFuncFlow = repoFun.observeTotalPagamentosPorFuncionario(obraId)
        val notasFlow = repoNota.observeNotas(obraId)
        val etapasFlow = repoCron.observeEtapas(obraId)
        val materiaisFlow = repoMat.observeMateriais(obraId)
        val aportesFlow = repoObra.observeAportes(obraId)

        // Para compatibilidade (muitos flows de tipos diferentes), usamos a sobrecarga de Array
        @Suppress("UNCHECKED_CAST")
        combine(
            listOf(
                obraFlow as kotlinx.coroutines.flow.Flow<Any?>,
                funcionariosFlow as kotlinx.coroutines.flow.Flow<Any?>,
                totalPagoPorFuncFlow as kotlinx.coroutines.flow.Flow<Any?>,
                notasFlow as kotlinx.coroutines.flow.Flow<Any?>,
                etapasFlow as kotlinx.coroutines.flow.Flow<Any?>,
                materiaisFlow as kotlinx.coroutines.flow.Flow<Any?>,
                aportesFlow as kotlinx.coroutines.flow.Flow<Any?>
            )
        ) { arr: Array<Any?> ->
            val obra = arr[0] as Obra?
            val funcionarios = arr[1] as List<Funcionario>
            val totalPagoMap = arr[2] as Map<String, Double>
            val notas = arr[3] as List<Nota>
            val etapas = arr[4] as List<Etapa>
            val materiais = arr[5] as List<Material>
            val aportes = arr[6] as List<Aporte>

            requireNotNull(obra) { "Obra não encontrada." }

            // ---------------- Funcionários ----------------
            val ativos = funcionarios.filter { it.status.equals("ativo", true) }
            val inativos = funcionarios.filter { it.status.equals("inativo", true) }

            val funcAtivosComTotal = ativos.map { f ->
                FuncionarioComTotal(f, totalPagoMap[f.id] ?: 0.0)
            }
            val funcInativosComTotal = inativos.map { f ->
                FuncionarioComTotal(f, totalPagoMap[f.id] ?: 0.0)
            }
            val totalGastoFuncionarios = totalPagoMap.values.sum()

            // ---------------- Notas ----------------
            val notasAReceber = notas.filter { it.status.equals("A Pagar", true) }
            val notasPagas = notas.filter { it.status.equals("Pago", true) }
            val totalNotasAReceber = notasAReceber.sumOf { it.valor }
            val totalNotasPagas = notasPagas.sumOf { it.valor }
            val totalNotasGeral = totalNotasAReceber + totalNotasPagas

            // ---------------- Cronograma ----------------
            val cronPendentes = etapas.filter { it.status.equals("Pendente", true) }
            val cronAndamento = etapas.filter {
                it.status.equals("Em andamento", true) ||
                        it.status.equals("Em Andamento", true)
            }
            val cronConcluidos = etapas.filter {
                it.status.equals("Concluído", true) ||
                        it.status.equals("Concluido", true)
            }

            // ---------------- Materiais ----------------
            val materiaisAtivos =
                materiais.filter { it.status.equals("Ativo", true) }
            val materiaisInativos =
                materiais.filter { it.status.equals("Inativo", true) }
            val totalMateriaisAtivos = materiaisAtivos.size
            val totalMateriaisInativos = materiaisInativos.size
            val totalMateriaisGeral = totalMateriaisAtivos + totalMateriaisInativos

            // ---------------- Aportes / Saldos ----------------
            val totalAportes = aportes.sumOf { it.valor }
            val saldoComAportes = obra.saldoInicial + totalAportes
            val saldoRestante = obra.saldoInicial + totalAportes - obra.gastoTotal

            ExportSummaryData(
                obra = obra,

                funcionariosAtivos = funcAtivosComTotal,
                funcionariosInativos = funcInativosComTotal,
                totalGastoFuncionarios = totalGastoFuncionarios,

                notasAReceber = notasAReceber,
                notasPagas = notasPagas,
                totalNotasAReceber = totalNotasAReceber,
                totalNotasPagas = totalNotasPagas,
                totalNotasGeral = totalNotasGeral,

                cronPendentes = cronPendentes,
                cronAndamento = cronAndamento,
                cronConcluidos = cronConcluidos,
                countCronPendentes = cronPendentes.size,
                countCronAndamento = cronAndamento.size,
                countCronConcluidos = cronConcluidos.size,

                materiaisAtivos = materiaisAtivos,
                materiaisInativos = materiaisInativos,
                totalMateriaisAtivos = totalMateriaisAtivos,
                totalMateriaisInativos = totalMateriaisInativos,
                totalMateriaisGeral = totalMateriaisGeral,

                aportes = aportes,
                totalAportes = totalAportes,
                saldoInicial = obra.saldoInicial,
                saldoComAportes = saldoComAportes,
                saldoRestante = saldoRestante
            )
        }
            .map<ExportSummaryData, UiState<ExportSummaryData>> { UiState.Success(it) }
            .catch { emit(UiState.ErrorRes(R.string.resumo_generic_error)) }
            .onEach { value -> _state.value = value }
            .flowOn(io)
            .launchIn(viewModelScope)
    }
}
