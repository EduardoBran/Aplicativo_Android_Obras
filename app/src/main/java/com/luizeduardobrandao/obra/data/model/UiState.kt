package com.luizeduardobrandao.obra.data.model

import androidx.annotation.StringRes

/**
 * Representa estados de tela ou fluxos assíncronos nos ViewModels.
 *
 * • [Idle]       → estado neutro / inicial (nenhuma ação em andamento)
 * • [Loading]    → operação em andamento
 * • [Success<T>] → operação bem-sucedida, trazendo o dado [T]
 * • [Error]      → falha com mensagem em texto livre
 * • [ErrorRes]   → falha com mensagem identificada por resource ID (StringRes)
 */
sealed interface UiState<out T> {

    // Estado neutro – útil após consumir um evento de sucesso/erro.
    object Idle : UiState<Nothing>

    // Indica operação em andamento.
    object Loading : UiState<Nothing>

    // Resultado positivo com dado genérico [T].
    data class Success<out T>(val data: T) : UiState<T>

    // Resultado de erro com mensagem legível ao usuário.
    data class Error(val message: String) : UiState<Nothing>

    /** Erro identificado por um String‐res ID. */
    data class ErrorRes(@StringRes val resId: Int) : UiState<Nothing>
}

/*

Em resumo, out T diz ao compilador:

- “Este tipo genérico só produz valores de T (não consome).”
- Permite atribuições seguras de subtipos para supertypos, deixando seu código mais flexível.

 */