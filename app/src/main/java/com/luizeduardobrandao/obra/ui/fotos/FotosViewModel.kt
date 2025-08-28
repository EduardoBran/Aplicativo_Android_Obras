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
import java.util.Locale
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

    // >>> Dados pendentes da imagem (para o BottomSheet consumir)
    private var pendingBytes: ByteArray? = null
    private var pendingMime: String? = null
    fun setPendingPhoto(bytes: ByteArray, mime: String) {
        pendingBytes = bytes
        pendingMime = mime
    }

    fun consumePendingPhoto(): Pair<ByteArray?, String?> {
        val pair = pendingBytes to pendingMime
        // não limpamos automaticamente para permitir reabertura do sheet se fechar acidentalmente
        return pair
    }

    fun clearPendingPhoto() {
        pendingBytes = null
        pendingMime = null
    }

    // Comparador: data desc + nome asc
    private val byDateDescThenName = compareByDescending<Imagem> { dataToEpoch(it.data) }
        .thenBy { it.nome.lowercase(Locale.ROOT) }

    /** Lista filtrada observável pela UI */
    val state: StateFlow<UiState<List<Imagem>>> =
        combine(_rawState, _filter) { base, f ->
            if (base !is UiState.Success) return@combine base
            val list = base.data

            val filtered = when (f) {
                ImagemFilter.NOME ->
                    list.sortedBy { it.nome.lowercase(Locale.ROOT) }

                ImagemFilter.TODAS ->
                    list.sortedWith(byDateDescThenName)

                ImagemFilter.PINTURA ->
                    list.filter { it.tipo.equals("Pintura", true) }
                        .sortedWith(byDateDescThenName)

                ImagemFilter.PEDREIRO ->
                    list.filter { it.tipo.equals("Pedreiro", true) }
                        .sortedWith(byDateDescThenName)

                ImagemFilter.LADRILHEIRO ->
                    list.filter { it.tipo.equals("Ladrilheiro", true) }
                        .sortedWith(byDateDescThenName)

                ImagemFilter.HIDRAULICA ->
                    list.filter { it.tipo.equals("Hidráulica", true) }
                        .sortedWith(byDateDescThenName)

                ImagemFilter.ELETRICA ->
                    list.filter { it.tipo.equals("Elétrica", true) }
                        .sortedWith(byDateDescThenName)

                ImagemFilter.OUTRO ->
                    list.filter { it.tipo.equals("Outro", true) }
                        .sortedWith(byDateDescThenName)
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
                val sdf = java.text.SimpleDateFormat(f, Locale("pt", "BR"))
                sdf.isLenient = false
                val time = sdf.parse(s)?.time
                if (time != null) return time
            } catch (_: Exception) {
            }
        }
        return Long.MIN_VALUE
    }
}
