package ru.feskolech.libriatv.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Scope that outlives screens: work that must finish after the viewer has left (saving progress). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

@Module @InstallIn(SingletonComponent::class)
object AppScopeModule {
    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
