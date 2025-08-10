package com.luizeduardobrandao.obra.ui.material

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Material
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.data.repository.MaterialRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de Materiais da obra.
 *
 * • Recebe [obraId] via Safe-Args (SavedStateHandle).
 * • Mantém um único listener (Job cancelável) para evitar duplicidade.
 * • Apenas gerencia Materiais (não altera gastoTotal).
 */
@HiltViewModel
class MaterialViewModel @Inject constructor(
    private val repo: MaterialRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // obraId vem tipado via Safe-Args
    private val obraId: String = savedStateHandle["obraId"]
        ?: error("Safe-Args deve conter obraId")

    // Estado da lista para a UI
    private val _state = MutableStateFlow<UiState<List<Material>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Material>>> = _state.asStateFlow()

    // Estado de operações (Salvar/Atualizar/Excluir)
    private val _opState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val opState: StateFlow<UiState<Unit>> = _opState.asStateFlow()

    // Garante apenas um listener ativo
    private var loadJob: Job? = null


    /** Inicia (ou reinicia) o listener em /obras/{uid}/{obraId}/materiais */
    fun loadMateriais() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(io) {
            repo.observeMateriais(obraId)
                .catch { _state.value = UiState.ErrorRes(R.string.material_load_error) }
                .onEach { list -> _state.value = UiState.Success(list) }
                .collect()
        }
    }

    /** Stream de um material específico (útil em detalhe/edição). */
    fun observeMaterial(materialId: String) =
        repo.observeMaterial(obraId, materialId)

    /** Adiciona material. Em caso de sucesso, recarrega a lista. */
    fun addMaterial(material: Material) {
        viewModelScope.launch(io) {
            _opState.value = UiState.Loading
            val result = repo.addMaterial(obraId, material)
            _opState.value = result.fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = { UiState.ErrorRes(R.string.material_save_error) }
            )
            if (_opState.value is UiState.Success) loadMateriais()
        }
    }

    /** Atualiza material. Em caso de sucesso, recarrega a lista. */
    fun updateMaterial(material: Material) {
        viewModelScope.launch(io) {
            _opState.value = UiState.Loading
            val result = repo.updateMaterial(obraId, material)
            _opState.value = result.fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = { UiState.ErrorRes(R.string.material_update_error) }
            )
            if (_opState.value is UiState.Success) loadMateriais()
        }
    }

    /** Exclui material. Em caso de sucesso, recarrega a lista. */
    fun deleteMaterial(materialId: String) {
        viewModelScope.launch(io) {
            _opState.value = UiState.Loading
            val result = repo.deleteMaterial(obraId, materialId)
            _opState.value = result.fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = { UiState.ErrorRes(R.string.material_delete_error) }
            )
            if (_opState.value is UiState.Success) loadMateriais()
        }
    }
}