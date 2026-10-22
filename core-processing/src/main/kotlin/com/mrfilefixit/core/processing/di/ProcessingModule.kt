package com.mrfilefixit.core.processing.di

import com.mrfilefixit.core.domain.image.ImageProcessor
import com.mrfilefixit.core.domain.ocr.OcrProcessor
import com.mrfilefixit.core.processing.image.AndroidImageProcessor
import com.mrfilefixit.core.processing.ocr.MlKitOcrProcessor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds engine interfaces to their implementations. Swapping a library means changing one line here.
 * ContentResolver, InputLimits, DispatcherProvider and AppLogger are provided by :app.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface ProcessingModule {
    @Binds
    @Singleton
    fun bindImageProcessor(impl: AndroidImageProcessor): ImageProcessor

    @Binds
    @Singleton
    fun bindOcrProcessor(impl: MlKitOcrProcessor): OcrProcessor
}
