package com.luizeduardobrandao.obra.di

import com.luizeduardobrandao.obra.data.repository.AiAutofillRepository
import com.luizeduardobrandao.obra.data.repository.impl.ChatGptAutofillRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Faz o bind da implementação do repositório de IA.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiBindModule {

    @Binds
    @Singleton
    abstract fun bindAiAutofillRepository(
        impl: ChatGptAutofillRepositoryImpl
    ): AiAutofillRepository
}