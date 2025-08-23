package com.luizeduardobrandao.obra.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt responsável por expor instâncias únicas do Firebase Auth e Realtime Database.
 *
 * • `FirebaseAuth`   → autenticação de usuários
 * • `FirebaseDatabase` → database em tempo real
 * • `DatabaseReference` → nó raiz “/obras” onde serão gravadas as obras
 *
 * Observação: cada ViewModel obtém o UID do usuário logado (`auth.currentUser?.uid`)
 * e cria referências filhas dinamicamente (ex.: `/obras/$uid/...`).
 */

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    // Instância singleton de [FirebaseAuth]
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    // Instância singleton de [FirebaseDatabase] (usando URL padrão do projeto).
    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase = FirebaseDatabase.getInstance()

    /**
     * Referência ao nó “/obras”.
     *
     * Exemplo de uso:
     * ```kotlin
     * val obrasRef = databaseRef.child(uid)      // /obras/{uid}
     * ```
     */
    @Provides
    @Singleton
    fun provideRootObrasReference(
        database: FirebaseDatabase
    ): DatabaseReference = database.reference.child("obras")

    // ─────────────────────────────────────────────────────────────
    // Storage (NOVO)
    // ─────────────────────────────────────────────────────────────

    /** Instância singleton do Firebase Storage. */
    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    /** (Opcional) Referência raiz "obras/" no Storage, útil para organizar uploads. */
    @Provides
    @Singleton
    fun provideRootObrasStorageReference(
        storage: FirebaseStorage
    ): StorageReference = storage.reference.child("obras")
}