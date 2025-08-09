package com.luizeduardobrandao.obra.ui.cronograma

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Etapa
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.data.repository.CronogramaRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*      // para flowOn, catch, onEach, launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsável pelo cronograma da obra.
 *
 * • Recebe [obraId] via Safe-Args/SavedStateHandle.
 * • Mantém um único listener (Job cancelável) para não duplicar coleta.
 * • Não altera custos ([gastoTotal]) — só gerencia etapas.
 */

@HiltViewModel
class CronogramaViewModel @Inject constructor(
    private val repo: CronogramaRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // 1) obraId chega via Safe-Args, sem lateinit
    private val obraId: String = savedStateHandle["obraId"]
        ?: error("Safe-Args deve conter obraId")

    // 2) Estado interno de lista de etapas
    private val _state = MutableStateFlow<UiState<List<Etapa>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Etapa>>> = _state.asStateFlow()

    // 3) Job cancelável para não acumular listeners
    private var loadJob: Job? = null


    /**
     * Inicia ou reinicia o listener em /cronograma/{obraId}/etapas.
     *
     * • Usa `flowOn(io)` para trocar para o dispatcher de I/O.
     * • `catch { … }` intercepta erros do Firebase e emite `ErrorRes`.
     * • `onEach { … }` emite `Success`.
     * • `launchIn(viewModelScope)` dispara a coleta sem precisar de `collect { }`.
     */
    fun loadEtapas() {
        // cancela listener anterior, se houver
        loadJob?.cancel()

        loadJob = repo.observeEtapas(obraId)
            .flowOn(io) // garante que o listener rode no dispatcher de I/O
            .catch {
                // em caso de erro ao buscar etapas
                _state.value = UiState.ErrorRes(R.string.cronograma_load_error)
            }
            .onEach { etapas ->
                // emite lista de etapas obtida
                _state.value = UiState.Success(etapas)
            }
            .launchIn(viewModelScope)
    }

    /**
     * Adiciona uma nova [Etapa] e, ao concluir, recarrega a lista.
     */
    fun addEtapa(etapa: Etapa) = viewModelScope.launch(io) {
        repo.addEtapa(obraId, etapa)
        loadEtapas() // re-inicia listener para refletir inclusão
    }

    /**
     * Atualiza a etapa existente e, ao concluir, recarrega a lista.
     */
    /** Atualiza uma etapa e, ao concluir, recarrega a lista. */
    fun updateEtapa(etapaAtualizada: Etapa) = viewModelScope.launch(io) {
        repo.updateEtapa(obraId, etapaAtualizada)
        loadEtapas()          // se preferir, pode remover: o listener refletirá a mudança
    }

    /**
     * Remove uma etapa e, ao concluir, recarrega a lista.
     */
    fun deleteEtapa(etapa: Etapa) = viewModelScope.launch(io) {
        repo.deleteEtapa(obraId, etapa.id)
        loadEtapas() // re-inicia listener para refletir exclusão
    }
}