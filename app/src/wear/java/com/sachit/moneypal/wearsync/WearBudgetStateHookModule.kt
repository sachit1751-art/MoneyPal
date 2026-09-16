package com.sachit.moneypal.wearsync

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object WearBudgetStateHookModule {
    @Provides
    @IntoSet
    fun providePublisherHook(impl: PublishWearBudgetStateHook): WearBudgetStateHook = impl
}
