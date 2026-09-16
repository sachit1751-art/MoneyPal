package com.sachit.moneypal.wearsync

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object FossWearBudgetStateHookModule {
    @Provides
    @IntoSet
    fun provideNoopHook(impl: NoopWearBudgetStateHook): WearBudgetStateHook = impl
}
