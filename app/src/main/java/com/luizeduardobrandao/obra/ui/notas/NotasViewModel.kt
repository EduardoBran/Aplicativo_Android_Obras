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

    // ─────────────── Foto pendente (para criação/edição) ───────────────
    private var pendingPhotoBytes: ByteArray? = null
    private var pendingPhotoMime: String? = null


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

    fun observeNota(obraId: String, notaId: String) =
        repoNota.observeNota(obraId, notaId)

    // Adiciona nota e recarrega + recalcula gastoTotal.
    fun addNota(nota: Nota) {
        viewModelScope.launch(io) {
            _state.value = UiState.Loading
            repoNota.addNota(obraId, nota)
            recalcGastoTotal()
            loadNotas()
        }
    }

    // Atualiza nota (antiga x nova) e recarrega + recalcula gastoTotal.
    fun updateNota(old: Nota, novo: Nota) {
        viewModelScope.launch(io) {
            _state.value = UiState.Loading
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
        // mão de obra agora vem dos PAGAMENTOS agregados por funcionário
        val totalFun = repoFun
            .observeTotalPagamentosPorFuncionario(obraId)
            .first()
            .values
            .sum()

        // materiais: mantém “A Pagar”, como já funciona
        val notas = repoNota.observeNotas(obraId).first()
        val totalMat = notas.filter { it.status == "A Pagar" }.sumOf { it.valor }

        repoObra.updateGastoTotal(obraId, totalFun + totalMat)
    }

    /** Armazena em memória a foto pendente (confirmada no BottomSheet da galeria/câmera). */
    fun setPendingPhoto(bytes: ByteArray, mime: String) {
        pendingPhotoBytes = bytes
        pendingPhotoMime = mime
    }

    /** Descarta a foto pendente (quando o usuário cancela/volta). */
    fun clearPendingPhoto() {
        pendingPhotoBytes = null
        pendingPhotoMime = null
    }

    /**
     * Cria a nota e, se houver foto pendente, faz o upload e atualiza os campos (fotoUrl/fotoPath).
     * Mantém o mesmo padrão de atualização de estado + recálculo já usado no ViewModel.
     */
    fun createNotaWithOptionalPhoto(nota: Nota) {
        viewModelScope.launch(io) {
            // indica operação em andamento para telas que observam este estado
            _state.value = UiState.Loading

            val addRes = repoNota.addNota(obraId, nota.copy(fotoUrl = null, fotoPath = null))
            addRes.onFailure {
                _state.value = UiState.ErrorRes(R.string.nota_load_error)
                return@launch
            }

            val notaId = addRes.getOrNull()!!

            val bytes = pendingPhotoBytes
            val mime = pendingPhotoMime

            if (bytes != null && mime != null) {
                val upRes = repoNota.uploadNotaPhoto(obraId, notaId, bytes, mime)
                upRes.onFailure {
                    // falhou upload: mantém a nota sem foto
                    _state.value = UiState.ErrorRes(R.string.nota_load_error)
                    // ainda assim recarrega a lista para não travar a UI
                    recalcGastoTotal()
                    loadNotas()
                    return@launch
                }

                val (url, path) = upRes.getOrNull()!!
                // atualiza somente os campos de foto
                repoNota.updateNota(
                    obraId,
                    old = nota.copy(id = notaId), // old usado só para assinatura atual
                    new = nota.copy(id = notaId, fotoUrl = url, fotoPath = path)
                )
                clearPendingPhoto()
            }

            // mantém seu comportamento: recálculo e recarga
            recalcGastoTotal()
            loadNotas()
        }
    }

    /**
     * Atualiza a nota. Se houver nova foto pendente:
     *  • exclui a antiga (se existir),
     *  • envia a nova,
     *  • grava os novos campos fotoUrl/fotoPath.
     * Se não houver foto pendente, apenas atualiza a nota normalmente.
     */
    fun updateNotaWithOptionalPhoto(old: Nota, updated: Nota) {
        viewModelScope.launch(io) {
            _state.value = UiState.Loading

            val bytes = pendingPhotoBytes
            val mime = pendingPhotoMime

            if (bytes == null || mime == null) {
                // sem troca de foto → fluxo normal
                repoNota.updateNota(obraId, old, updated)
                recalcGastoTotal()
                loadNotas()
                return@launch
            }

            // há nova foto → apaga antiga (se houver)
            old.fotoPath?.let { path ->
                repoNota.deleteNotaPhoto(obraId, old.id, path)
                    .onFailure {
                        _state.value = UiState.ErrorRes(R.string.nota_load_error)
                        recalcGastoTotal()
                        loadNotas()
                        return@launch
                    }
            }

            // upload da nova
            val upRes = repoNota.uploadNotaPhoto(obraId, old.id, bytes, mime)
            upRes.onFailure {
                _state.value = UiState.ErrorRes(R.string.nota_load_error)
                recalcGastoTotal()
                loadNotas()
                return@launch
            }

            val (url, path) = upRes.getOrNull()!!
            // atualiza a nota já com a nova foto
            repoNota.updateNota(
                obraId,
                old,
                updated.copy(fotoUrl = url, fotoPath = path)
            )
            clearPendingPhoto()

            recalcGastoTotal()
            loadNotas()
        }
    }

    /**
     * Exclui a foto da nota no Storage (se existir) e limpa os campos na Nota (fotoUrl/fotoPath = null).
     */
    fun deleteNotaPhotoAndClearField(nota: Nota) {
        viewModelScope.launch(io) {
            _state.value = UiState.Loading

            nota.fotoPath?.let { path ->
                val delRes = repoNota.deleteNotaPhoto(obraId, nota.id, path)
                delRes.onFailure {
                    _state.value = UiState.ErrorRes(R.string.nota_load_error)
                    recalcGastoTotal()
                    loadNotas()
                    return@launch
                }
            }

            // Atualiza a nota limpando os campos de foto
            repoNota.updateNota(
                obraId,
                old = nota,
                new = nota.copy(fotoUrl = null, fotoPath = null)
            )

            recalcGastoTotal()
            loadNotas()
        }
    }
}