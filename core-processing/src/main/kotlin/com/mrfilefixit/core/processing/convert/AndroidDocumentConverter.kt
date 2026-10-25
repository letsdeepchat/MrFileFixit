package com.mrfilefixit.core.processing.convert

import android.content.ContentResolver
import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.mrfilefixit.core.domain.convert.ConversionKind
import com.mrfilefixit.core.domain.convert.ConversionRequest
import com.mrfilefixit.core.domain.convert.DocumentConverter
import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.convert.SheetOutput
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.document.PageSetup
import com.mrfilefixit.core.domain.document.Presentation
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.document.Sheet
import com.mrfilefixit.core.domain.document.SheetCell
import com.mrfilefixit.core.domain.document.Workbook
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.job.slice
import com.mrfilefixit.core.domain.job.step
import com.mrfilefixit.core.domain.pdf.PageSize
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.runCatchingAppFlat
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.core.office.OfficeContainer
import com.mrfilefixit.core.office.OfficeEncryptedException
import com.mrfilefixit.core.office.OfficeFormatException
import com.mrfilefixit.core.office.OfficeLimitException
import com.mrfilefixit.core.office.docx.DocxReader
import com.mrfilefixit.core.office.docx.DocxWriter
import com.mrfilefixit.core.office.layout.SheetDocument
import com.mrfilefixit.core.office.legacy.DocBinaryReader
import com.mrfilefixit.core.office.legacy.PptBinaryReader
import com.mrfilefixit.core.office.legacy.XlsBinaryReader
import com.mrfilefixit.core.office.pdftext.PdfPageText
import com.mrfilefixit.core.office.pdftext.PdfSlides
import com.mrfilefixit.core.office.pdftext.TableExtraction
import com.mrfilefixit.core.office.pdftext.TextLayout
import com.mrfilefixit.core.office.pptx.PptxReader
import com.mrfilefixit.core.office.pptx.PptxWriter
import com.mrfilefixit.core.office.xlsx.Csv
import com.mrfilefixit.core.office.xlsx.XlsxReader
import com.mrfilefixit.core.office.xlsx.XlsxWriter
import com.mrfilefixit.core.processing.io.InputRejectedException
import com.mrfilefixit.core.processing.io.mapEngineError
import com.mrfilefixit.core.processing.io.newOutputFile
import com.mrfilefixit.core.processing.io.openLimited
import com.mrfilefixit.core.processing.io.throwIfFailure
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Word, PowerPoint and Excel to and from PDF, on the device. Office files are parsed by :core-office
 * (no third-party code), laid out with Android's text engine and written with [android.graphics.pdf.PdfDocument].
 * PDFs are read with PDFBox, whose text positions are turned back into paragraphs, slides or tables.
 *
 * The source is first copied into the job directory (bounded by [InputLimits.maxFileBytes]); the copy is
 * deleted when the job ends, whatever the outcome.
 */
internal class AndroidDocumentConverter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: ContentResolver,
    private val limits: InputLimits,
    private val dispatchers: DispatcherProvider,
) : DocumentConverter {

    private val pdfBoxReady by lazy { PDFBoxResourceLoader.init(context) }

    override suspend fun convert(request: ConversionRequest, progress: ProgressReporter): AppResult<List<OutputFile>> =
        withContext(dispatchers.default) {
            runCatchingAppFlat(::mapConversionError) {
                File(request.outputDir).mkdirs()
                val source = copyToJob(request)
                try {
                    val outputs = when (request.kind) {
                        ConversionKind.WORD_TO_PDF -> documentToPdf(readWord(source), request, progress)
                        ConversionKind.PPT_TO_PDF -> slidesToPdf(readSlides(source), request, progress)
                        ConversionKind.EXCEL_TO_PDF -> sheetsToPdf(readWorkbook(source, request), request, progress)
                        ConversionKind.PDF_TO_WORD -> pdfToWord(source, request, progress)
                        ConversionKind.PDF_TO_PPT -> pdfToSlides(source, request, progress)
                        ConversionKind.PDF_TO_EXCEL -> pdfToSheets(source, request, progress)
                    }
                    progress.report(1f)
                    success(outputs)
                } finally {
                    source.delete()
                }
            }
        }

    // region office → PDF

    private fun formatOf(request: ConversionRequest): DocumentFormat? =
        DocumentFormat.detect(request.source.mimeType, request.source.displayName)

    private fun readWord(file: File): RichDocument = when (OfficeContainer.sniff(file)) {
        OfficeContainer.ZIP -> DocxReader().read(file)
        OfficeContainer.COMPOUND -> DocBinaryReader().read(file.readBytes())
        else -> throw OfficeFormatException("Not a Word document")
    }

    private fun readSlides(file: File): Presentation = when (OfficeContainer.sniff(file)) {
        OfficeContainer.ZIP -> PptxReader().read(file)
        OfficeContainer.COMPOUND -> PptBinaryReader().read(file.readBytes())
        else -> throw OfficeFormatException("Not a PowerPoint file")
    }

    private fun readWorkbook(file: File, request: ConversionRequest): Workbook = when (OfficeContainer.sniff(file)) {
        OfficeContainer.ZIP -> XlsxReader().read(file)
        OfficeContainer.COMPOUND -> XlsBinaryReader().read(file.readBytes())
        else -> if (formatOf(request) == DocumentFormat.CSV) {
            Workbook(listOf(Csv.read(file.readText(), request.outputBaseName)))
        } else {
            throw OfficeFormatException("Not a spreadsheet")
        }
    }

    private suspend fun documentToPdf(
        document: RichDocument,
        request: ConversionRequest,
        progress: ProgressReporter,
    ): List<OutputFile> {
        val out = newOutputFile(request.outputDir, request.outputBaseName, PDF_EXT)
        val pages = DocumentPdfWriter(MAX_OUTPUT_PAGES).write(document, out) { progress.report(it * WRITE_SHARE) }
        return listOf(out.toOutput(DocumentFormat.PDF, pages))
    }

    private suspend fun slidesToPdf(
        deck: Presentation,
        request: ConversionRequest,
        progress: ProgressReporter,
    ): List<OutputFile> {
        val out = newOutputFile(request.outputDir, request.outputBaseName, PDF_EXT)
        val pages = SlidesPdfWriter().write(deck, out) { progress.report(it * WRITE_SHARE) }
        return listOf(out.toOutput(DocumentFormat.PDF, pages))
    }

    private suspend fun sheetsToPdf(
        book: Workbook,
        request: ConversionRequest,
        progress: ProgressReporter,
    ): List<OutputFile> {
        val paper = request.options.pageSize.takeIf { it != PageSize.FIT_IMAGE } ?: PageSize.A4
        val document = SheetDocument.toRichDocument(book, paper.widthPt, paper.heightPt, request.outputBaseName)
        return documentToPdf(document, request, progress)
    }

    // endregion

    // region PDF → office

    private suspend fun pdfToWord(
        source: File,
        request: ConversionRequest,
        progress: ProgressReporter,
    ): List<OutputFile> {
        val pages = readPdfPages(source, request, withPictures = true, progress.slice(0f, READ_SHARE))
        if (pages.all { it.glyphs.isEmpty() && it.pictures.isEmpty() }) {
            throw InputRejectedException(
                AppError.NoTextFound,
            )
        }
        val first = pages.first()
        val document = TextLayout.toRichDocument(pages).copy(
            page = PageSetup(widthPt = first.widthPt, heightPt = first.heightPt),
            title = request.outputBaseName,
        )
        val out = newOutputFile(request.outputDir, request.outputBaseName, DocumentFormat.DOCX.extension)
        out.outputStream().use { DocxWriter().write(document, it) }
        return listOf(out.toOutput(DocumentFormat.DOCX, pages.size))
    }

    private suspend fun pdfToSheets(
        source: File,
        request: ConversionRequest,
        progress: ProgressReporter,
    ): List<OutputFile> {
        val pages = readPdfPages(source, request, withPictures = false, progress.slice(0f, READ_SHARE))
        val numbers = pageNumbers(request, pages.size)
        val sheets = pages.mapIndexed { i, page -> TableExtraction.sheet(page, "Page ${numbers[i]}") }
            .filter { it.rows.isNotEmpty() }
        if (sheets.isEmpty()) throw InputRejectedException(AppError.NoTextFound)
        val format = request.options.sheetOutput.format
        val out = newOutputFile(request.outputDir, request.outputBaseName, format.extension)
        when (request.options.sheetOutput) {
            SheetOutput.XLSX -> out.outputStream().use { XlsxWriter().write(Workbook(sheets), it) }
            SheetOutput.CSV -> out.writeBytes(Csv.write(joinSheets(sheets)))
        }
        return listOf(out.toOutput(format, pages.size))
    }

    /** One CSV for all pages: page tables follow each other with an empty row between them. */
    private fun joinSheets(sheets: List<Sheet>): List<List<SheetCell>> =
        sheets.flatMapIndexed { i, sheet -> if (i == 0) sheet.rows else listOf(emptyList<SheetCell>()) + sheet.rows }

    private suspend fun pdfToSlides(
        source: File,
        request: ConversionRequest,
        progress: ProgressReporter,
    ): List<OutputFile> {
        val pages = readPdfPages(source, request, withPictures = false, progress.slice(0f, SLIDE_TEXT_SHARE))
        val backgrounds = renderWithoutText(source, request, progress.slice(SLIDE_TEXT_SHARE, READ_SHARE))
        val slides = pages.mapIndexed { i, page -> PdfSlides.slide(page, backgrounds.getOrNull(i)) }
        val first = pages.first()
        val deck = Presentation(first.widthPt, first.heightPt, slides)
        val out = newOutputFile(request.outputDir, request.outputBaseName, DocumentFormat.PPTX.extension)
        out.outputStream().use { PptxWriter().write(deck, it) }
        return listOf(out.toOutput(DocumentFormat.PPTX, slides.size))
    }

    /** 1-based page numbers the request selects, in order. */
    private fun pageNumbers(request: ConversionRequest, pageCount: Int): List<Int> =
        request.options.pages.filter { it in 1..pageCount }.distinct().ifEmpty { (1..pageCount).toList() }

    private suspend fun readPdfPages(
        source: File,
        request: ConversionRequest,
        withPictures: Boolean,
        progress: ProgressReporter,
    ): List<PdfPageText> {
        pdfBoxReady
        return openPdf(source).use { document ->
            val numbers = pageNumbers(request, document.numberOfPages)
            val reader = PdfPageReader(limits.maxPixels)
            numbers.mapIndexed { i, number ->
                coroutineContext.ensureActive()
                progress.step(i, numbers.size)
                reader.read(document, number - 1, withPictures)
            }
        }
    }

    /** Renders the selected pages with their text removed, as JPEG slide backgrounds. */
    private suspend fun renderWithoutText(
        source: File,
        request: ConversionRequest,
        progress: ProgressReporter,
    ): List<ByteArray> {
        val stripped = File(source.parentFile, "${source.nameWithoutExtension}-notext.pdf")
        try {
            val numbers = openPdf(source).use { document ->
                val numbers = pageNumbers(request, document.numberOfPages)
                numbers.forEach { PdfTextRemoval.removeText(document, document.getPage(it - 1)) }
                document.save(stripped)
                numbers
            }
            return ParcelFileDescriptor.open(stripped, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    numbers.mapIndexed { i, number ->
                        coroutineContext.ensureActive()
                        progress.step(i, numbers.size)
                        renderer.openPage(number - 1).use { page -> renderPage(page) }
                    }
                }
            }
        } finally {
            stripped.delete()
        }
    }

    private fun renderPage(page: PdfRenderer.Page): ByteArray {
        val scale =
            minOf(BACKGROUND_DPI / POINTS_PER_INCH, MAX_BACKGROUND_SIDE.toFloat() / maxOf(page.width, page.height))
        val bitmap =
            createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1))
        try {
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return Pictures.encodeJpeg(bitmap, BACKGROUND_QUALITY)
        } finally {
            bitmap.recycle()
        }
    }

    private fun openPdf(file: File): PDDocument {
        val document = try {
            PDDocument.load(file, "", MemoryUsageSetting.setupTempFileOnly())
        } catch (_: InvalidPasswordException) {
            throw InputRejectedException(AppError.PasswordRequired)
        }
        if (document.isEncrypted && !document.currentAccessPermission.canExtractContent()) {
            document.close()
            throw InputRejectedException(AppError.PasswordRequired)
        }
        val pages = limits.checkPageCount(document.numberOfPages)
        if (pages is Result.Failure) {
            document.close()
            throw InputRejectedException(pages.error)
        }
        return document
    }

    // endregion

    private fun copyToJob(request: ConversionRequest): File {
        limits.checkFileSize(request.source.sizeBytes).throwIfFailure()
        val target = File(request.outputDir, "convert-src.tmp")
        resolver.openLimited(request.source.uri, limits.maxFileBytes).use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        if (target.length() == 0L) throw InputRejectedException(AppError.EmptyFile)
        return target
    }

    private fun File.toOutput(format: DocumentFormat, pages: Int) =
        OutputFile(absolutePath, name, format.mimeType, length(), pageCount = pages)

    private fun mapConversionError(throwable: Throwable): AppError = when (throwable) {
        is OfficeEncryptedException -> AppError.DocumentProtected
        is OfficeLimitException -> AppError.DocumentTooComplex
        is OfficeFormatException -> AppError.CorruptFile
        is InvalidPasswordException -> AppError.PasswordRequired
        is StackOverflowError -> AppError.DocumentTooComplex
        else -> mapEngineError(throwable)
    }

    private companion object {
        const val PDF_EXT = "pdf"
        const val MAX_OUTPUT_PAGES = 2_000
        const val WRITE_SHARE = 0.95f
        const val READ_SHARE = 0.8f
        const val SLIDE_TEXT_SHARE = 0.3f
        const val POINTS_PER_INCH = 72f
        const val BACKGROUND_DPI = 150f
        const val MAX_BACKGROUND_SIDE = 2400
        const val BACKGROUND_QUALITY = 80
    }
}
