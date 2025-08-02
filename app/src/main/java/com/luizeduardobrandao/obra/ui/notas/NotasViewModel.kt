package com.luizeduardobrandao.obra.ui.notas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Nota
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de notas de material de uma obra.
 *
 * • Recebe [obraId] via Safe-Args/SavedStateHandle.
 * • Mantém um único listener (Job cancelável).
 * • Após CRUD, recalcula gastoTotal e dispara ObraRepository.updateGastoTotal().
 */

@HiltViewModel
class NotasViewModel @Inject constructor(
    private val repoNota: NotaRepository,
    private val repoFun: FuncionarioRepository,
    private val repoObra: ObraRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // obraId seguro vindo do nav-graph
    private val obraId: String = savedStateHandle["obraId"]
        ?: error("SafeArgs deve conter obraId")

    private val _state = MutableStateFlow<UiState<List<Nota>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Nota>>> = _state.asStateFlow()

    private var loadJob: Job? = null


    // Inicia (ou reinicia) escuta das notas no Firebase.
    fun loadNotas() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(io) {
            repoNota.observeNotas(obraId)
                .catch { _state.value = UiState.ErrorRes(R.string.nota_load_error) }
                .onEach { lista ->
                    _state.value = UiState.Success(lista)
                }
                .collect { }
        }
    }

    // Adiciona nota e recarrega + recalcula gastoTotal.
    fun addNota(nota: Nota) {
        viewModelScope.launch(io) {
            repoNota.addNota(obraId, nota)
            recalcGastoTotal()
            loadNotas()
        }
    }

    // Atualiza nota (antiga x nova) e recarrega + recalcula gastoTotal.
    fun updateNota(old: Nota, novo: Nota) {
        viewModelScope.launch(io) {
            repoNota.updateNota(obraId, old, novo)
            recalcGastoTotal()
            loadNotas()
        }
    }

    // Remove nota e recarrega + recalcula gastoTotal.
    fun deleteNota(nota: Nota) {
        viewModelScope.launch(io) {
            repoNota.deleteNota(obraId, nota)
            recalcGastoTotal()
            loadNotas()
        }
    }

    // Mesmo cálculo compartilhado: soma mão de obra + materiais “A Pagar”.
    private suspend fun recalcGastoTotal() {
        val funs  = repoFun.observeFuncionarios(obraId).first()
        val notas = repoNota.observeNotas(obraId).first()
        val totalFun = funs.sumOf { it.totalGasto }
        val totalMat = notas.filter { it.status == "A Pagar" }.sumOf { it.valor }
        repoObra.updateGastoTotal(obraId, totalFun + totalMat)
    }
}