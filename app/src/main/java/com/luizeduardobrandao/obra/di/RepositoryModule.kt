package com.luizeduardobrandao.obra.di

import com.luizeduardobrandao.obra.data.repository.AuthRepository
import com.luizeduardobrandao.obra.data.repository.CronogramaRepository
import com.luizeduardobrandao.obra.data.repository.FuncionarioRepository
import com.luizeduardobrandao.obra.data.repository.MaterialRepository
import com.luizeduardobrandao.obra.data.repository.NotaRepository
import com.luizeduardobrandao.obra.data.repository.ObraRepository
import com.luizeduardobrandao.obra.data.repository.impl.AuthRepositoryImpl
import com.luizeduardobrandao.obra.data.repository.impl.CronogramaRepositoryImpl
import com.luizeduardobrandao.obra.data.repository.impl.FuncionarioRepositoryImpl
import com.luizeduardobrandao.obra.data.repository.impl.MaterialRepositoryImpl
import com.luizeduardobrandao.obra.data.repository.impl.NotaRepositoryImpl
import com.luizeduardobrandao.obra.data.repository.impl.ObraRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Liga as interfaces de repositório às implementações concretas para o Hilt.

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindObraRepository(
        impl: ObraRepositoryImpl
    ): ObraRepository

    @Binds
    @Singleton
    abstract fun bindFuncionarioRepository(
        impl: FuncionarioRepositoryImpl
    ): FuncionarioRepository

    @Binds
    @Singleton
    abstract fun bindNotaRepository(
        impl: NotaRepositoryImpl
    ): NotaRepository

    @Binds
    @Singleton
    abstract fun bindCronogramaRepository(
        impl: CronogramaRepositoryImpl
    ): CronogramaRepository

    @Binds
    @Singleton
    abstract fun bindMaterialRepository(
        impl: MaterialRepositoryImpl
    ): MaterialRepository
}