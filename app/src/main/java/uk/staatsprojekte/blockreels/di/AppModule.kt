package uk.staatsprojekte.blockreels.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Application-wide bindings.
 *
 * [Clock] is provided rather than read from `System.currentTimeMillis()` at the call site because
 * the budget logic depends on time and has to be testable without sleeping. `DayBoundary` and
 * `BudgetTracker` take this as a constructor parameter; tests substitute a fixed or advancing
 * clock. See `AGENTS.md`.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
