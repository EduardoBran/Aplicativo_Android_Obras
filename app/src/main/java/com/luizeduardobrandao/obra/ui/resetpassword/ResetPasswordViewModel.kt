package com.luizeduardobrandao.obra.ui.resetpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.data.repository.AuthRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import com.luizeduardobrandao.obra.ui.extensions.isValidEmail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsável pelo envio de e-mail de redefinição de senha.
 *
 * Emite [UiState<Unit>] para:
 *  • Idle    – estado inicial
 *  • Loading – operação em andamento
 *  • Success – e-mail enviado com sucesso
 *  • ErrorRes– falha com mensagem via resource ID
 */

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val state: StateFlow<UiState<Unit>> = _state.asStateFlow()

    /**
     * Envia e-mail de recuperação de senha.
     *
     * Validação local:
     *  • e-mail válido (padrão RFC 5322)
     */
    fun sendResetEmail(email: String) {
        val trimmedEmail = email.trim()
        if (!trimmedEmail.isValidEmail()) {
            _state.value = UiState.ErrorRes(R.string.reset_password_email_error)
            return
        }

        viewModelScope.launch(io) {
            _state.value = UiState.Loading

            val result = authRepo.sendPasswordReset(trimmedEmail)
            val nextState = result.fold(
                onSuccess = {
                    UiState.Success(Unit)
                },
                onFailure = {
                    UiState.ErrorRes(R.string.reset_password_error_generic)
                }
            )

            _state.value = nextState
        }
    }
}