package com.mrfilefixit.core.storage.di

import com.mrfilefixit.core.domain.batch.BatchQuotaRepository
import com.mrfilefixit.core.domain.file.FileRepository
import com.mrfilefixit.core.domain.file.JobIdGenerator
import com.mrfilefixit.core.domain.file.TempFileStore
import com.mrfilefixit.core.domain.prefs.UserPreferencesRepository
import com.mrfilefixit.core.storage.files.CacheTempFileStore
import com.mrfilefixit.core.storage.files.SafFileRepository
import com.mrfilefixit.core.storage.prefs.DataStoreBatchQuota
import com.mrfilefixit.core.storage.prefs.DataStoreUserPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.UUID

@Module
@InstallIn(SingletonComponent::class)
internal interface StorageModule {
    @Binds
    fun bindFileRepository(impl: SafFileRepository): FileRepository

    @Binds
    fun bindTempFileStore(impl: CacheTempFileStore): TempFileStore

    @Binds
    fun bindPreferences(impl: DataStoreUserPreferences): UserPreferencesRepository

    @Binds
    fun bindBatchQuota(impl: DataStoreBatchQuota): BatchQuotaRepository

    companion object {
        @Provides
        fun provideJobIdGenerator(): JobIdGenerator = JobIdGenerator { UUID.randomUUID().toString() }

        @Provides
        fun provideClock(): Clock = Clock.systemDefaultZone()
    }
}
