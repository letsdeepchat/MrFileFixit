package com.mrfilefixit.core.processing.di

import com.mrfilefixit.core.domain.convert.DocumentConverter
import com.mrfilefixit.core.domain.image.ImageProcessor
import com.mrfilefixit.core.domain.ocr.OcrProcessor
import com.mrfilefixit.core.domain.pdf.PdfProcessor
import com.mrfilefixit.core.domain.resume.DocumentExporter
import com.mrfilefixit.core.domain.scan.ScanEnhancer
import com.mrfilefixit.core.processing.convert.AndroidDocumentConverter
import com.mrfilefixit.core.processing.convert.AndroidDocumentExporter
import com.mrfilefixit.core.processing.image.AndroidImageProcessor
import com.mrfilefixit.core.processing.ocr.MlKitOcrProcessor
import com.mrfilefixit.core.processing.pdf.PdfBoxPdfProcessor
import com.mrfilefixit.core.processing.scan.AndroidScanEnhancer
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
    fun bindPdfProcessor(impl: PdfBoxPdfProcessor): PdfProcessor

    @Binds
    @Singleton
    fun bindOcrProcessor(impl: MlKitOcrProcessor): OcrProcessor

    @Binds
    @Singleton
    fun bindScanEnhancer(impl: AndroidScanEnhancer): ScanEnhancer

    @Binds
    @Singleton
    fun bindDocumentConverter(impl: AndroidDocumentConverter): DocumentConverter

    @Binds
    @Singleton
    fun bindDocumentExporter(impl: AndroidDocumentExporter): DocumentExporter
}
