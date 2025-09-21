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
import com.luizeduardobrandao.obra.ui.ia.dialogs.QuestionTypeDialog.Category
import com.luizeduardobrandao.obra.ui.ia.dialogs.QuestionTypeDialog.CalcSub
import com.luizeduardobrandao.obra.ui.ia.dialogs.QuestionTypeDialog.Selection
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

    // Seleção atual (default: Dúvida Geral)
    private val _selection = MutableStateFlow(Selection(Category.GERAL, null))
    val selection: StateFlow<Selection> = _selection
    fun setSelection(sel: Selection) { _selection.value = sel }

    // --- NOVO: flag para lembrar se o usuário já confirmou um tipo ---
    private val _hasChosenType = MutableStateFlow(false)
    val hasChosenType: StateFlow<Boolean> = _hasChosenType
    fun setHasChosenType(value: Boolean) { _hasChosenType.value = value }

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
     * Envia com seleção explícita e um contexto extra opcional (ex.: localização).
     * - `selection`: tipo de dúvida escolhido no diálogo.
     * - `extraContext`: linhas adicionais que serão adicionadas ANTES do {{USER_TEXT}}
     *   (ex.: "Localização atual: -23.55, -46.63").
     */
    fun sendQuestion(
        userText: String,
        selection: Selection,
        extraContext: String?
    ) {
        viewModelScope.launch(io) {
            val hasImage = imageBytes != null && imageMime != null
            val promptRes = resolvePromptRes(selection, hasImage)
            val template = readRawSafe(promptRes)

            if (template.isBlank()) {
                _sendState.value = UiState.ErrorRes(R.string.ia_prompt_missing)
                return@launch
            }

            val sb = StringBuilder()
            if (!extraContext.isNullOrBlank()) {
                sb.appendLine(extraContext.trim())
                sb.appendLine()
            }
            sb.append(userText.trim())
            val finalUserText = sb.toString()

            _sendState.value = UiState.Loading
            aiRepo.ask(
                prompt = template.replace("{{USER_TEXT}}", finalUserText),
                imageBytes = imageBytes,
                imageMime = imageMime
            ).onSuccess { txt ->
                _sendState.value = UiState.Success(txt.trim())
            }.onFailure {
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

    // Função util para ler vários templates
    private fun readRawSafe(@RawRes resId: Int?): String {
        if (resId == null) return ""
        return runCatching { readRaw(resId) }.getOrElse { "" }
    }

    // Resolver prompt por Selection + presença de imagem
    @RawRes
    private fun resolvePromptRes(selection: Selection, hasImage: Boolean): Int {
        return when (selection.category) {
            Category.GERAL ->
                if (hasImage) R.raw.prompt_general_image else R.raw.prompt_general_no_image

            Category.CALCULO_MATERIAL -> {
                when (selection.sub ?: CalcSub.ALVENARIA_E_ESTRUTURA) {
                    CalcSub.ALVENARIA_E_ESTRUTURA ->
                        if (hasImage) R.raw.prompt_calculo_alvenaria_image else R.raw.prompt_calculo_alvenaria_no_image
                    CalcSub.ELETRICA ->
                        if (hasImage) R.raw.prompt_calculo_eletrica_image else R.raw.prompt_calculo_eletrica_no_image
                    CalcSub.HIDRAULICA ->
                        if (hasImage) R.raw.prompt_calculo_hidraulica_image else R.raw.prompt_calculo_hidraulica_no_image
                    CalcSub.PINTURA ->
                        if (hasImage) R.raw.prompt_calculo_pintura_image else R.raw.prompt_calculo_pintura_no_image
                    CalcSub.PISO ->
                        if (hasImage) R.raw.prompt_calculo_piso_image else R.raw.prompt_calculo_piso_no_image
                }
            }

            Category.ALVENARIA_E_ESTRUTURA ->
                if (hasImage) R.raw.prompt_masonry_image else R.raw.prompt_masonry_no_image

            Category.INSTALACOES_ELETRICAS ->
                if (hasImage) R.raw.prompt_eletrical_image else R.raw.prompt_eletrical_no_image

            Category.INSTALACOES_HIDRAULICAS ->
                if (hasImage) R.raw.prompt_plumbing_image else R.raw.prompt_plumbing_no_image

            Category.PINTURA_E_ACABAMENTOS -> {
                // Não há prompts dedicados listados; usamos o "GERAL".
                if (hasImage) R.raw.prompt_general_image else R.raw.prompt_general_no_image
            }

            Category.PLANEJAMENTO_E_CONSTRUCAO ->
                if (hasImage) R.raw.prompt_construction_image else R.raw.prompt_construction_no_image

            Category.LIMPEZA_POS_OBRA ->
                if (hasImage) R.raw.prompt_cleaning_image else R.raw.prompt_cleaning_no_image

            Category.PESQUISA_DE_LOJA ->
                if (hasImage) R.raw.prompt_store_image else R.raw.prompt_store_no_image
        }
    }

    fun resetSendState() {
        _sendState.value = UiState.Idle
    }
}