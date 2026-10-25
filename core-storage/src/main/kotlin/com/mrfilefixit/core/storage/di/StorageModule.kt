package com.mrfilefixit.core.storage.di

import com.mrfilefixit.core.domain.file.FileRepository
import com.mrfilefixit.core.domain.file.JobIdGenerator
import com.mrfilefixit.core.domain.file.TempFileStore
import com.mrfilefixit.core.domain.prefs.UserPreferencesRepository
import com.mrfilefixit.core.domain.resume.ResumeRepository
import com.mrfilefixit.core.storage.files.CacheTempFileStore
import com.mrfilefixit.core.storage.files.SafFileRepository
import com.mrfilefixit.core.storage.prefs.DataStoreUserPreferences
import com.mrfilefixit.core.storage.resume.FileResumeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
    fun bindResumeRepository(impl: FileResumeRepository): ResumeRepository

    companion object {
        @Provides
        fun provideJobIdGenerator(): JobIdGenerator = JobIdGenerator { UUID.randomUUID().toString() }
    }
}
