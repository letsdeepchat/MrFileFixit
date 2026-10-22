package com.mrfilefixit.app.di

import android.content.ContentResolver
import android.content.Context
import com.mrfilefixit.app.logging.TimberLogger
import com.mrfilefixit.core.domain.dispatchers.ApplicationScope
import com.mrfilefixit.core.domain.dispatchers.DefaultDispatcherProvider
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.logging.AppLogger
import com.mrfilefixit.core.domain.security.InputLimits
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AppModule {
    @Binds
    fun bindDispatchers(impl: DefaultDispatcherProvider): DispatcherProvider

    @Binds
    fun bindLogger(impl: TimberLogger): AppLogger

    companion object {
        @Provides
        fun provideContentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver

        @Provides
        fun provideInputLimits(): InputLimits = InputLimits.DEFAULT

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
