package com.atomictrxn.conduit.di

import com.atomictrxn.conduit.data.api.DefaultOpenWebUIServiceFactory
import com.atomictrxn.conduit.data.api.OpenWebUIServiceFactory
import com.atomictrxn.conduit.data.local.SettingsDataStore
import com.atomictrxn.conduit.data.repository.ConduitRepository
import com.atomictrxn.conduit.data.repository.ServerRepository
import com.atomictrxn.conduit.ui.common.AndroidStringProvider
import com.atomictrxn.conduit.ui.common.StringProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideServerRepository(dataStore: SettingsDataStore): ServerRepository = ServerRepository(dataStore)

    @Provides
    @Singleton
    fun provideConduitRepository(repository: ServerRepository): ConduitRepository = repository

    @Provides
    @Singleton
    fun provideOpenWebUIServiceFactory(factory: DefaultOpenWebUIServiceFactory): OpenWebUIServiceFactory = factory

    @Provides
    @Singleton
    fun provideStringProvider(provider: AndroidStringProvider): StringProvider = provider
}
