package com.luizeduardobrandao.obra.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.UserProfileChangeRequest
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.repository.AuthRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.ui.extensions.isValidEmail
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel responsável pelo fluxo de cadastro de novos usuários.
 *
 * Emite [UiState<AuthResult>] para:
 *  • Idle        – estado inicial (nenhuma ação em andamento)
 *  • Loading     – operação de cadastro em progresso
 *  • Success     – cadastro realizado com sucesso (contendo [AuthResult])
 *  • ErrorRes    – falha com mensagem via resource ID
 */

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepo: AuthRepository,               // Repositório de autenticação Firebase
    @IoDispatcher private val io: CoroutineDispatcher   // Dispatcher para operações de I/O
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<AuthResult>>(UiState.Idle)
    val state: StateFlow<UiState<AuthResult>> = _state.asStateFlow()

    /**
     * Inicia o registro de uma nova conta e atualiza o perfil com o nome fornecido.
     *
     * @param name     Nome do usuário (mínimo 3 caracteres, após trim).
     * @param email    E-mail para cadastro (deve passar isValidEmail()).
     * @param password Senha (mínimo 6 caracteres).
     */
    fun register(name: String, email: String, password: String) {

        // 1) Validação de nome
        val trimmedName = name.trim()
        if (trimmedName.length < 3) {
            _state.value = UiState.ErrorRes(R.string.register_name_error)
            return
        }

        // 2) Validação de e-mail
        val trimmedEmail = email.trim()
        if(!trimmedEmail.isValidEmail()) {
            _state.value = UiState.ErrorRes(R.string.register_email_error)
            return
        }

        // 3) Validação de senha
        if (password.length < 6) {
            _state.value = UiState.ErrorRes(R.string.register_password_error)
            return
        }

        // 4) Cadastro no Firebase em background (dispatcher de I/O)
        viewModelScope.launch(io) {
            _state.value = UiState.Loading

            // 4.1) Cria conta no Firebase Auth
            val result = authRepo.signUp(trimmedEmail, password)

            // 4.2) Converte o Result em UiState
            val nextState: UiState<AuthResult> = result.fold(
                onSuccess = { authResult ->
                    // 4.3) Atualiza o displayName do usuário criado
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(trimmedName)
                        .build()

                    // await() para garantir a conclusão da atualização
                    authResult.user?.updateProfile(profileUpdates)
                        ?.await()

                    UiState.Success(authResult)
                },

                onFailure = { ex ->
                    // 4.4) Se e-mail já existe, usa mensagem específica
                    if (ex is FirebaseAuthUserCollisionException) {
                        UiState.ErrorRes(R.string.register_email_in_use_error)
                    } else {
                        UiState.ErrorRes(R.string.register_error_generic)
                    }
                }
            )

            // 4.5) Emite estado final (Success ou ErrorRes)
            _state.value = nextState
        }
    }
}