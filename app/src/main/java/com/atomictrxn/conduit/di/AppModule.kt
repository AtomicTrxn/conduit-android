package com.atomictrxn.conduit.di

import com.atomictrxn.conduit.data.local.SettingsDataStore
import com.atomictrxn.conduit.data.repository.ServerRepository
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
}
