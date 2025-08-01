package com.luizeduardobrandao.obra.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton


/** Qualificadores para distinguir distintos dispatchers. */
@Qualifier annotation class IoDispatcher
@Qualifier annotation class DefaultDispatcher
@Qualifier annotation class MainDispatcher

/**
 * Módulo Hilt que expõe os dispatchers do Kotlin Coroutines.
 * Deixa claro (via @Qualifier) qual tipo está sendo injetado.
 *
 * ➜ Use `@IoDispatcher`   para operações de I/O  (Firebase, disk/ network).
 * ➜ Use `@DefaultDispatcher` para cálculos / transformações pesadas.
 * ➜ Use `@MainDispatcher`    apenas quando precisar de acesso ao UI Thread
 *   dentro de classes injetadas que não tenham contexto de Android.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}