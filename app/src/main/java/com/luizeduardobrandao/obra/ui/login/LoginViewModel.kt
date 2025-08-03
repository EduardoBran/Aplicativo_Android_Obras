package com.luizeduardobrandao.obra.ui.login

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthResult
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

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<AuthResult>>(UiState.Idle)
    val state: StateFlow<UiState<AuthResult>> = _state.asStateFlow()


    /**
     * Tenta autenticar o usuário com e-mail e senha.
     * Validações:
     *  • e-mail conforme padrão Android (RFC aproximado)
     *  • senha com ao menos 6 caracteres
     */
    fun login(email: String, password: String) {
        val trimmedEmail = email.trim()

        // 1) Valida e-mail
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _state.value = UiState.ErrorRes(R.string.login_email_error)
            return
        }

        // 2) Valida senha
        if (password.length < 6) {
            _state.value = UiState.ErrorRes(R.string.login_password_error)
            return
        }

        // 3) Realiza login
        viewModelScope.launch(io) {
            _state.value = UiState.Loading
            val result = authRepo.signIn(trimmedEmail, password)

            _state.value = result.fold(
                onSuccess = { authResult ->
                    UiState.Success(authResult)
                },
                onFailure = {
                    UiState.ErrorRes(R.string.login_error_generic)
                }
            )
        }
    }

    /**
     * Reseta o estado para Idle após consumirmos um evento de erro ou sucesso.
     * Deve ser chamado pela View logo depois de exibir o snackbar.
     */
    fun resetState() {
        _state.value = UiState.Idle
    }
}