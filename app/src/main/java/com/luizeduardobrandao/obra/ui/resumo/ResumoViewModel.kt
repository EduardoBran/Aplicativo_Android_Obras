package com.luizeduardobrandao.obra.ui.resumo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.data.model.Aporte
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.data.repository.FuncionarioRepository
import com.luizeduardobrandao.obra.data.repository.NotaRepository
import com.luizeduardobrandao.obra.data.repository.ObraRepository
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResumoViewModel @Inject constructor(
    private val repoObra: ObraRepository,
    private val repoFun: FuncionarioRepository,
    private val repoNota: NotaRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // obraId vindo do nav-graph via Safe-Args
    private val obraId: String =
        savedStateHandle["obraId"] ?: error("SafeArgs deve conter obraId em ResumoFragment")

    private val _deleteAporteState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deleteAporteState: StateFlow<UiState<Unit>> = _deleteAporteState.asStateFlow()

    // ★ Atualizamos a estrutura para carregar também os aportes
    data class ResumoData(
        val countFuncionarios: Int,
        val totalDias: Int,
        val totalMaoDeObra: Double,
        val countNotas: Int,
        val totalMateriais: Double,
        val totalPorTipo: Map<String, Double>,
        val saldoInicial: Double,
        val gastoTotal: Double,
        val totalAportes: Double,          // ★ NOVO
        val aportes: List<Aporte>,         // ★ NOVO (para listar no “Financeiro”)
        val saldoRestante: Double          // ★ recalculado com totalAportes
    )

    private val _state = MutableStateFlow<UiState<ResumoData>>(UiState.Loading)
    val state: StateFlow<UiState<ResumoData>> = _state.asStateFlow()

    init {
        // Flows independentes
        val obraFlow = repoObra.observeObras()
            .map { list -> list.firstOrNull { it.obraId == obraId } }

        val funFlow = repoFun.observeFuncionarios(obraId)
        val notaFlow = repoNota.observeNotas(obraId)

        // ★ NOVO: fluxos de aportes da obra
        val aporteFlow = repoObra.observeAportes(obraId)

        // ★ Incluímos aporteFlow no combine
        combine(obraFlow, funFlow, notaFlow, aporteFlow) { obra, funs, notas, aportes ->

            requireNotNull(obra) { "Obra não encontrada." }

            // 1) Funcionários
            val totalDias = funs.sumOf { it.diasTrabalhados }
            val totalMao  = funs.sumOf(Funcionario::totalGasto)

            // 2) Notas / Materiais
            val totalNotas = notas.sumOf(Nota::valor)
            val porTipo = buildMap<String, Double> {
                notas.forEach { n ->
                    n.tipos.forEach { t ->
                        this[t] = (this[t] ?: 0.0) + n.valor
                    }
                }
            }

            // 3) Aportes
            val totalAportes = aportes.sumOf { it.valor }

            // 4) Saldos (recalcula com aportes)
            val saldoRestante = obra.saldoInicial + totalAportes - obra.gastoTotal

            ResumoData(
                countFuncionarios = funs.size,
                totalDias = totalDias,
                totalMaoDeObra = totalMao,
                countNotas = notas.size,
                totalMateriais = totalNotas,
                totalPorTipo = porTipo,
                saldoInicial = obra.saldoInicial,
                gastoTotal = obra.gastoTotal,
                totalAportes = totalAportes,   // ★
                aportes = aportes,             // ★
                saldoRestante = saldoRestante  // ★
            )
        }
            .map<ResumoData, UiState<ResumoData>> { UiState.Success(it) }
            .catch { emit(UiState.ErrorRes(R.string.resumo_generic_error)) }
            .onEach { _state.value = it }
            .flowOn(io)
            .launchIn(viewModelScope)
    }

    fun deleteAporte(aporteId: String) {
        viewModelScope.launch(io) {
            _deleteAporteState.value = UiState.Loading
            _deleteAporteState.value = repoObra.deleteAporte(obraId, aporteId)
                .fold(
                    onSuccess = { UiState.Success(Unit) },
                    onFailure = { UiState.ErrorRes(R.string.resumo_generic_error) }
                )
        }
    }

    fun resetDeleteAporteState() {
        _deleteAporteState.value = UiState.Idle
    }
}