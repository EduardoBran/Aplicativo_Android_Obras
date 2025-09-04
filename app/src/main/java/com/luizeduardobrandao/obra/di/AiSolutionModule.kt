package com.luizeduardobrandao.obra.di

import com.luizeduardobrandao.obra.data.repository.AiSolutionRepository
import com.luizeduardobrandao.obra.data.repository.SolutionHistoryRepository
import com.luizeduardobrandao.obra.data.repository.impl.ChatGptSolutionRepositoryImpl
import com.luizeduardobrandao.obra.data.repository.impl.SolutionHistoryRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiSolutionModuleBindings {
    @Binds
    @Singleton
    abstract fun bindAiSolutionRepository(
        impl: ChatGptSolutionRepositoryImpl
    ): AiSolutionRepository

    @Binds
    @Singleton
    abstract fun bindSolutionHistoryRepository(
        impl: SolutionHistoryRepositoryImpl
    ): SolutionHistoryRepository
}