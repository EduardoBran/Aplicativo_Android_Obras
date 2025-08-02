package com.luizeduardobrandao.obra.ui.funcionario

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.data.repository.FuncionarioRepository
import com.luizeduardobrandao.obra.data.repository.NotaRepository
import com.luizeduardobrandao.obra.data.repository.ObraRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
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

    // Job para manter somente um listener ativo
    private var loadJob: Job? = null


    // Inicia (ou reinicia) escuta dos funcionários no Firebase.
    fun loadFuncionarios() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(io) {
            repoFun.observeFuncionarios(obraId)
                // primeiro intercepta erros
                .catch { _state.value = UiState.ErrorRes(R.string.func_load_error) }
                // depois emite sucesso
                .onEach { lista ->
                    _state.value = UiState.Success(lista)
                }
                // materializa o fluxo
                .collect()
        }
    }

    // Adiciona e depois recarrega + recalcula gastoTotal.
    fun addFuncionario(func: Funcionario) {
        viewModelScope.launch(io) {
            repoFun.addFuncionario(obraId, func)
            recalcGastoTotal()
            loadFuncionarios()
        }
    }

    // Atualiza e depois recarrega + recalcula gastoTotal.
    fun updateFuncionario(func: Funcionario) {
        viewModelScope.launch(io) {
            repoFun.updateFuncionario(obraId, func)
            recalcGastoTotal()
            loadFuncionarios()
        }
    }

    // Remove e depois recarrega + recalcula gastoTotal.
    fun deleteFuncionario(funcId: String) {
        viewModelScope.launch(io) {
            // obtém antes o objeto para saber o valor já lançado (opcional)
            repoFun.observeFuncionario(obraId, funcId).firstOrNull()?.let {
                repoFun.deleteFuncionario(obraId, funcId)
                recalcGastoTotal()
                loadFuncionarios()
            }
        }
    }


    /**
     * Soma totalGasto de todos os funcionários + valor “A Pagar” das notas,
     * grava em /obras/{obraId}/gastoTotal para o Obra model recalcular saldoRestante.
     */
    private suspend fun recalcGastoTotal() {
        val funs = repoFun.observeFuncionarios(obraId).first()
        val notas = repoNota.observeNotas(obraId).first()
        val totalFun = funs.sumOf { it.totalGasto }
        val totalMat = notas.filter { it.status == "A Pagar" }.sumOf { it.valor }

        val novoGasto = totalFun + totalMat
        repoObra.updateGastoTotal(obraId, novoGasto)
    }
}