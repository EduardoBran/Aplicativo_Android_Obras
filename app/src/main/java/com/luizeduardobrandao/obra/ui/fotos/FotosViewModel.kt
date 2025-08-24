package com.luizeduardobrandao.obra.ui.fotos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Imagem
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.data.repository.ImagemRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FotosViewModel @Inject constructor(
    private val repo: ImagemRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val obraId: String = savedStateHandle["obraId"]
        ?: error("SafeArgs deve conter obraId")

    private val _rawState = MutableStateFlow<UiState<List<Imagem>>>(UiState.Loading)

    private val _filter = MutableStateFlow(ImagemFilter.TODAS)
    val filter: StateFlow<ImagemFilter> = _filter.asStateFlow()

    /** Lista filtrada observável pela UI */
    val state: StateFlow<UiState<List<Imagem>>> =
        combine(_rawState, _filter) { base, f ->
            if (base !is UiState.Success) return@combine base
            val list = base.data

            val filtered = when (f) {
                ImagemFilter.NOME -> list.sortedBy { it.nome.lowercase() }

                ImagemFilter.TODAS -> list.sortedBy { dataToEpoch(it.data) }

                ImagemFilter.PINTURA ->
                    list.filter { it.tipo.equals("Pintura", true) }
                        .sortedBy { dataToEpoch(it.data) }

                ImagemFilter.PEDREIRO ->
                    list.filter { it.tipo.equals("Pedreiro", true) }
                        .sortedBy { dataToEpoch(it.data) }

                ImagemFilter.LADRILHEIRO ->
                    list.filter { it.tipo.equals("Ladrilheiro", true) }
                        .sortedBy { dataToEpoch(it.data) }

                ImagemFilter.HIDRAULICA ->
                    list.filter { it.tipo.equals("Hidráulica", true) }
                        .sortedBy { dataToEpoch(it.data) }

                ImagemFilter.ELETRICA ->
                    list.filter { it.tipo.equals("Elétrica", true) }
                        .sortedBy { dataToEpoch(it.data) }

                ImagemFilter.OUTRO ->
                    list.filter { it.tipo.equals("Outro", true) }
                        .sortedBy { dataToEpoch(it.data) }
            }
            UiState.Success(filtered)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    private var loadJob: Job? = null

    init {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(io) {
            repo.observeImagens(obraId)
                .catch { _rawState.value = UiState.ErrorRes(R.string.work_load_error) }
                .onEach { _rawState.value = UiState.Success(it) }
                .collect()
        }
    }

    fun setFilter(f: ImagemFilter) {
        _filter.value = f
    }

    fun addImagem(
        imagem: Imagem,
        bytes: ByteArray,
        mime: String
    ): Flow<UiState<String>> = flow {
        emit(UiState.Loading)
        val res = repo.addImagem(obraId, imagem, bytes, mime)
        val state = res.fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.ErrorRes(R.string.imagens_save_error) }
        )
        emit(state)
        // recarrega lista após salvar
        load()
    }.flowOn(io)

    fun observeImagem(imagemId: String) = repo.observeImagem(obraId, imagemId)

    fun deleteImagem(imagem: Imagem): Flow<UiState<Unit>> = flow {
        emit(UiState.Loading)
        val res = repo.deleteImagem(obraId, imagem)
        emit(
            res.fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = { UiState.ErrorRes(R.string.work_load_error) }
            ))
        load()
    }.flowOn(io)

    private fun dataToEpoch(data: String?): Long {
        if (data.isNullOrBlank()) return Long.MIN_VALUE
        val s = data.trim()
        val formatos = arrayOf("dd/MM/yyyy", "d/M/yyyy", "d/MM/yyyy", "dd/M/yyyy")
        for (f in formatos) {
            try {
                val sdf = java.text.SimpleDateFormat(f, java.util.Locale("pt", "BR"))
                sdf.isLenient = false
                val time = sdf.parse(s)?.time
                if (time != null) return time
            } catch (_: Exception) {
            }
        }
        return Long.MIN_VALUE
    }
}
