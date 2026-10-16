package ru.stepan.chemodan.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.stepan.chemodan.domain.BudgetCalculator
import ru.stepan.chemodan.domain.ExchangeCalculator

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides @Singleton fun exchangeCalculator() = ExchangeCalculator()

    @Provides @Singleton fun budgetCalculator() = BudgetCalculator()
}
