package com.luizeduardobrandao.obra.ui.login

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
     *  • e-mail conforme RFC 5322
     *  • senha com ao menos 6 caracteres
     */
    fun login(email: String, password: String) {
        // validações locais
        val trimmedEmail = email.trim()
        if (!trimmedEmail.isValidEmail()) {
            _state.value = UiState.ErrorRes(R.string.login_email_error)
            return
        }
        if (password.length < 6) {
            _state.value = UiState.ErrorRes(R.string.login_password_error)
            return
        }

        viewModelScope.launch(io) {
            // dispara o loading
            _state.value = UiState.Loading

            // chama o repositório
            val result = authRepo.signIn(trimmedEmail, password)

            // mapeia o Result<AuthResult> para UiState<AuthResult>
            val newState: UiState<AuthResult> = result.fold(
                onSuccess  = { authResult ->
                    UiState.Success(authResult)
                },
                onFailure  = {
                    UiState.ErrorRes(R.string.login_error_generic)
                }
            )

            // só aqui fazemos o emit/assign
            _state.value = newState
        }
    }
}