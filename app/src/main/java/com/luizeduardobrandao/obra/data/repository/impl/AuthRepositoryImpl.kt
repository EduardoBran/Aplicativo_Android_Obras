package com.luizeduardobrandao.obra.data.repository.impl

import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.luizeduardobrandao.obra.data.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth
) : AuthRepository {

    override val currentUid: String?
        get() = auth.currentUser?.uid

    // Emite true/false de acordo com o estado do usuário.
    override val authState: Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser != null) }

        auth.addAuthStateListener(listener)

        // valor inicial
        trySend(auth.currentUser != null)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signIn(
        email: String,
        password: String
    ): Result<AuthResult> = kotlin.runCatching {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun signUp(
        email: String,
        password: String
    ): Result<AuthResult> = kotlin.runCatching {
        auth.createUserWithEmailAndPassword(email, password).await()
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        kotlin.runCatching { auth.sendPasswordResetEmail(email).await() }

    override fun signOut() = auth.signOut()
}