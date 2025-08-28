package com.luizeduardobrandao.obra.ui.funcionario

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.Pagamento
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.data.repository.FuncionarioRepository
import com.luizeduardobrandao.obra.data.repository.NotaRepository
import com.luizeduardobrandao.obra.data.repository.ObraRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de funcionários de uma obra.
 *
 * • Recebe [obraId] via Safe-Args/SavedStateHandle.
 * • Mantém um único listener (Job cancelável) para não duplicar coleta.
 * • Após CRUD, recalcula gastoTotal (mão de obra + notas) e atualiza via ObraRepository.
 */

@HiltViewModel
class FuncionarioViewModel @Inject constructor(
    private val repoFun: FuncionarioRepository,
    private val repoNota: NotaRepository,
    private val repoObra: ObraRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // 1) obraId vem do nav-graph (Safe-Args) — evita lateinit e NPE
    private val obraId: String = savedStateHandle["obraId"]
        ?: error("SafeArgs deve conter obraId")

    // Estado exposto para a UI: Idle | Loading | Success(lista) | ErrorRes(msg)
    private val _state = MutableStateFlow<UiState<List<Funcionario>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Funcionario>>> = _state.asStateFlow()

    private val _opState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val opState: StateFlow<UiState<Unit>> = _opState.asStateFlow()

    // Job para manter somente um listener ativo
    private var loadJob: Job? = null


    /* ───────────────────────── Funcionários ───────────────────────── */

    // Inicia (ou reinicia) escuta dos funcionários no Firebase.
    fun loadFuncionarios() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(io) {
            repoFun.observeFuncionarios(obraId)
                // primeiro intercepta erros
                .catch { _state.value = UiState.ErrorRes(R.string.func_load_error) }
                // depois emite sucesso
                .onEach { lista -> _state.value = UiState.Success(lista) }
                // materializa o fluxo
                .collect {}
        }
    }

    fun observeFuncionario(obraId: String, funcionarioId: String): Flow<Funcionario?> =
        repoFun.observeFuncionario(obraId, funcionarioId)

    // Adiciona e depois recarrega + recalcula gastoTotal.
    fun addFuncionario(func: Funcionario) {
        viewModelScope.launch(io) {
            _opState.value = UiState.Loading
            val result = repoFun.addFuncionario(obraId, func)
            _opState.value = result.fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = { UiState.ErrorRes(R.string.func_save_error) }
            )
            if (_opState.value is UiState.Success) {
                // O recálculo global acontece no repo; aqui apenas atualizamos a lista.
                loadFuncionarios()
            }
        }
    }

    // Atualiza e depois recarrega + recalcula gastoTotal.
    fun updateFuncionario(func: Funcionario) {
        viewModelScope.launch(io) {
            _opState.value = UiState.Loading
            val result = repoFun.updateFuncionario(obraId, func)
            _opState.value = result.fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = { UiState.ErrorRes(R.string.func_update_error) }
            )
            if (_opState.value is UiState.Success) {
                loadFuncionarios()
            }
        }
    }

    // Remove e depois recarrega + recalcula gastoTotal.
    fun deleteFuncionario(funcId: String) {
        viewModelScope.launch(io) {
            // obtém antes o objeto para saber o valor já lançado (opcional)
            repoFun.observeFuncionario(obraId, funcId).firstOrNull()?.let {
                val result = repoFun.deleteFuncionario(obraId, funcId)
                _opState.value = result.fold(
                    onSuccess = { UiState.Success(Unit) },
                    onFailure = { UiState.ErrorRes(R.string.func_delete_error) }
                )
                if (_opState.value is UiState.Success) {
                    loadFuncionarios()
                }
            }
        }
    }


    /* ───────────────────────── Pagamentos ───────────────────────── */

    /** Observa o histórico de pagamentos de um funcionário. */
    fun observePagamentos(funcionarioId: String): Flow<List<Pagamento>> =
        repoFun.observePagamentos(obraId, funcionarioId)

    /** Adiciona um novo pagamento para o funcionário. */
    fun addPagamento(funcionarioId: String, pagamento: Pagamento) {
        viewModelScope.launch(io) {
            _opState.value = UiState.Loading
            val res = repoFun.addPagamento(obraId, funcionarioId, pagamento)
            _opState.value = res.fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = { UiState.ErrorRes(R.string.func_payment_add_error) }
            )
            // Nada a recarregar aqui; a lista de pagamentos é um Flow observado pela UI.
            // O gastoTotal é recalculado no repository.
        }
    }

    /** Atualiza um pagamento existente (valor/data). */
    fun updatePagamento(funcionarioId: String, pagamento: Pagamento) {
        viewModelScope.launch(io) {
            _opState.value = UiState.Loading
            val res = repoFun.updatePagamento(obraId, funcionarioId, pagamento)
            _opState.value = res.fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = { UiState.ErrorRes(R.string.func_payment_update_error) }
            )
        }
    }

    /** Exclui um pagamento do funcionário. */
    fun deletePagamento(funcionarioId: String, pagamentoId: String) {
        viewModelScope.launch(io) {
            _opState.value = UiState.Loading
            val res = repoFun.deletePagamento(obraId, funcionarioId, pagamentoId)
            _opState.value = res.fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = { UiState.ErrorRes(R.string.func_payment_delete_error) }
            )
        }
    }

    /** Mapa agregado (funcId -> total pago), útil para ResumoFuncionarioFragment. */
    fun observeTotaisPorFuncionario(): Flow<Map<String, Double>> =
        repoFun.observeTotalPagamentosPorFuncionario(obraId)


    /* ───────────────────────── Recalcular (compatibilidade) ───────────────────────── */

    /**
     * Soma PAGAMENTOS de todos os funcionários + valor “A Pagar” das notas
     * e grava em /obras/{obraId}/gastoTotal.
     * Hoje o recálculo é disparado no Repository, então este helper é opcional.
     */
    private suspend fun recalcGastoTotal() {
        val totalFun = repoFun
            .observeTotalPagamentosPorFuncionario(obraId)
            .first()
            .values
            .sum()

        val notas = repoNota.observeNotas(obraId).first()
        val totalMat = notas.filter { it.status == "A Pagar" }.sumOf { it.valor }

        repoObra.updateGastoTotal(obraId, totalFun + totalMat)
    }
}