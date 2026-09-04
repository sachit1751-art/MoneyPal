package com.sachit.moneypal.data.di

import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.data.repository.BudgetRepositoryImpl
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.data.repository.SettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBudgetRepository(
        impl: BudgetRepositoryImpl
    ): BudgetRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository
}