package com.luizeduardobrandao.obra.data.repository

import com.google.firebase.auth.AuthResult
import kotlinx.coroutines.flow.Flow

/**
 * Operações de autenticação.
 *
 * • Todas as funções retornam [Result] encapsulando o sucesso ou a exceção lançada pelo Firebase.
 * • [authState] emite o status do usuário logado – útil para splash / session check.
 */

interface AuthRepository {

    // FirebaseAuth.currentUser?.uid ou `null` se não logado.
    val currentUid: String?

    // Flow com o usuário atual (true = logado, false = null).
    val authState: Flow<Boolean>

    // Login com e-mail e senha.
    suspend fun signIn(email: String, password: String): Result<AuthResult>

    // Criação de conta.
    suspend fun signUp(email: String, password: String): Result<AuthResult>

    // Envia link de redefinição de senha.
    suspend fun sendPasswordReset(email: String): Result<Unit>

    // Logout global.
    fun signOut()
}