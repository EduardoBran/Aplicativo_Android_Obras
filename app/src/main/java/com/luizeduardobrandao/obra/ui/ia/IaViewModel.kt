package com.luizeduardobrandao.obra.ui.ia

import android.content.Context
import androidx.annotation.RawRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.SolutionHistory
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.data.repository.AiSolutionRepository
import com.luizeduardobrandao.obra.data.repository.SolutionHistoryRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class IaViewModel @Inject constructor(
    private val aiRepo: AiSolutionRepository,
    private val historyRepo: SolutionHistoryRepository,
    @ApplicationContext private val appCtx: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val obraId: String = savedStateHandle["obraId"] ?: error("obraId ausente")

    private val _sendState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val sendState: StateFlow<UiState<String>> = _sendState

    private var imageBytes: ByteArray? = null
    private var imageMime: String? = null

    fun setImage(bytes: ByteArray, mime: String) {
        imageBytes = bytes
        imageMime = mime
    }

    fun clearImage() {
        imageBytes = null
        imageMime = null
    }

    private fun readRaw(@RawRes resId: Int): String =
        appCtx.resources.openRawResource(resId).use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        }

    /**
     * Monta o prompt final e envia à IA.
     * @param userText Texto digitado pelo usuário (obrigatório).
     */
    fun sendQuestion(userText: String) {
        viewModelScope.launch(io) {
            val hasImage = imageBytes != null && imageMime != null
            val resId = if (hasImage) R.raw.prompt_solution else R.raw.prompt_solution_no_image

            val template = runCatching { readRaw(resId) }.getOrElse { "" }
            if (template.isBlank()) {
                _sendState.value = UiState.ErrorRes(R.string.ia_prompt_missing)
                return@launch
            }

            val prompt = template.replace("{{USER_TEXT}}", userText.trim())

            _sendState.value = UiState.Loading
            aiRepo.ask(prompt, imageBytes, imageMime)
                .onSuccess { txt ->
                    _sendState.value = UiState.Success(txt.trim())
                }
                .onFailure {
                    _sendState.value = UiState.Error(it.message ?: "Erro na IA")
                }
        }
    }

    fun saveToHistory(title: String, content: String) {
        val today =
            SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())
        viewModelScope.launch(io) {
            historyRepo.add(
                obraId,
                SolutionHistory(
                    title = title.take(100).trim(),
                    content = content.trim(),
                    date = today
                )
            )
        }
    }

    fun resetSendState() {
        _sendState.value = UiState.Idle
    }
}