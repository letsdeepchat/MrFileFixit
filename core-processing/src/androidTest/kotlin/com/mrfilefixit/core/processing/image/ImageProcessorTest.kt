package com.mrfilefixit.core.processing.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.image.EncodeTarget
import com.mrfilefixit.core.domain.image.ImageEffects
import com.mrfilefixit.core.domain.image.ImageFilter
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.ImageOp
import com.mrfilefixit.core.domain.image.ImageRequest
import com.mrfilefixit.core.domain.image.NaturalEnhance
import com.mrfilefixit.core.domain.image.OutputSpec
import com.mrfilefixit.core.domain.image.OverlayFont
import com.mrfilefixit.core.domain.image.ProcessedImage
import com.mrfilefixit.core.domain.image.ResizeMode
import com.mrfilefixit.core.domain.logging.NoOpLogger
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.security.InputLimits
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The image engine on a real device: outputs are never empty, size targets hold, Enhance and B&W are visible. */
@RunWith(AndroidJUnit4::class)
class ImageProcessorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private val dispatchers = object : DispatcherProvider {
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val main: CoroutineDispatcher = Dispatchers.Main
    }
    private val processor by lazy {
        val resolver = context.contentResolver
        AndroidImageProcessor(
            resolver,
            ImageDecoding(resolver, InputLimits.DEFAULT),
            ImageEncoding(),
            InputLimits.DEFAULT,
            dispatchers,
            NoOpLogger,
            BackgroundRemoval(),
            OverlayTypefaces(context),
        )
    }

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "image-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** A detailed "photo": a warm gradient with fine noise, which is hard to compress. */
    private fun photo(width: Int = 1200, height: Int = 1600): FileRef {
        val random = Random(7)
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val n = random.nextInt(-24, 25)
            Color.rgb(
                (150 + x * 80 / width + n).coerceIn(0, 255),
                (110 + y * 60 / height + n).coerceIn(0, 255),
                (90 + n).coerceIn(0, 255),
            )
        }
        val bitmap = createBitmap(width, height).apply { setPixels(pixels, 0, width, 0, 0, width, height) }
        val file = File(dir, "source-$width.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return FileRef(Uri.fromFile(file).toString(), file.name, "image/jpeg", file.length())
    }

    private fun run(
        source: FileRef,
        output: OutputSpec,
        ops: List<ImageOp> = emptyList(),
        effects: ImageEffects = ImageEffects.NONE,
        name: String = "out",
    ): AppResult<ProcessedImage> = runBlocking {
        processor.process(ImageRequest(source, dir.path, name, ops, output, effects))
    }

    private fun ok(result: AppResult<ProcessedImage>): ProcessedImage {
        assertTrue("$result", result is Result.Success)
        val image = (result as Result.Success).value
        val file = File(image.file.path)
        assertTrue("output exists", file.isFile)
        assertTrue("output is never 0 B", file.length() > 0)
        assertEquals(file.length(), image.file.sizeBytes)
        return image
    }

    @Test
    fun everyFormatGivesANonEmptyDecodableFile() {
        val source = photo()
        listOf(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP).forEach { format ->
            val image = ok(run(source, OutputSpec(format), listOf(ImageOp.Resize(600, 600)), name = format.name))
            val decoded = BitmapFactory.decodeFile(image.file.path)
            assertEquals(format.name, 450, decoded.width)
            assertEquals(format.name, 600, decoded.height)
        }
    }

    @Test
    fun compressHitsTwentyToFiftyKilobytes() {
        val source = photo()
        listOf(20 to 50, 20 to 30, 40 to 50).forEach { (min, max) ->
            val target = EncodeTarget.SizeRange(min * KB, max * KB, allowPadding = true, allowDownscale = true)
            val image = ok(run(source, OutputSpec(ImageFormat.JPEG, target), name = "c$min-$max"))
            assertTrue("$min-$max KB: ${image.file.sizeBytes}", image.file.sizeBytes in min * KB..max * KB)
        }
    }

    @Test
    fun aTinyImageIsPaddedUpToTheMinimum() {
        val source = photo(120, 160)
        val target = EncodeTarget.SizeRange(20 * KB, 50 * KB, allowPadding = true)
        val image = ok(run(source, OutputSpec(ImageFormat.JPEG, target)))
        assertTrue("${image.file.sizeBytes}", image.file.sizeBytes in 20 * KB..50 * KB)
    }

    @Test
    fun examPhotoPresetGivesExactPixelsAndSize() {
        val source = photo()
        val target = EncodeTarget.SizeRange(20 * KB, 50 * KB, allowPadding = true)
        val resize = listOf(ImageOp.Resize(200, 230, ResizeMode.FILL))
        val image = ok(run(source, OutputSpec(ImageFormat.JPEG, target), resize))
        val decoded = BitmapFactory.decodeFile(image.file.path)
        assertEquals(200, decoded.width)
        assertEquals(230, decoded.height)
        assertTrue("${image.file.sizeBytes}", image.file.sizeBytes in 20 * KB..50 * KB)
    }

    @Test
    fun enhanceVisiblyImprovesButKeepsSkinTone() {
        val source = photo(600, 800)
        val before = ok(run(source, OutputSpec(ImageFormat.PNG), name = "before"))
        val enhance = ImageEffects(enhance = NaturalEnhance(1f))
        val after = ok(run(source, OutputSpec(ImageFormat.PNG), effects = enhance, name = "after"))
        val a = BitmapFactory.decodeFile(before.file.path)
        val b = BitmapFactory.decodeFile(after.file.path)
        assertEquals(a.width, b.width)
        val meanA = means(a)
        val meanB = means(b)
        // The warm "skin" photo keeps its tone: overall brightness and each channel stay close, nothing whitened.
        val lumaA = 0.299 * meanA[0] + 0.587 * meanA[1] + 0.114 * meanA[2]
        val lumaB = 0.299 * meanB[0] + 0.587 * meanB[1] + 0.114 * meanB[2]
        assertTrue("brightness $lumaA vs $lumaB", abs(lumaA - lumaB) < 4.0)
        for (c in 0..2) assertTrue("channel $c: ${meanA[c]} vs ${meanB[c]}", abs(meanA[c] - meanB[c]) < 6.0)
        assertTrue("hue order changed", meanB[0] > meanB[1] && meanB[1] > meanB[2])
        // But the pixels did change.
        assertTrue("enhance changed nothing", meanAbsDiff(a, b) > 0.5)
    }

    @Test
    fun enhanceGivesADullFlatPhotoMoreContrast() {
        val width = 400
        val height = 300
        val random = Random(5)
        val pixels = IntArray(width * height) { i ->
            val v = 100 + (i % width) * 60 / width + random.nextInt(-3, 4)
            Color.rgb(v - 6, v + 6, v - 2)
        }
        val bitmap = createBitmap(width, height).apply { setPixels(pixels, 0, width, 0, 0, width, height) }
        val file = File(dir, "dull.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val source = FileRef(Uri.fromFile(file).toString(), file.name, "image/png", file.length())
        val after = ok(run(source, OutputSpec(ImageFormat.PNG), effects = ImageEffects(enhance = NaturalEnhance(1f))))
        val before = BitmapFactory.decodeFile(file.path)
        val out = BitmapFactory.decodeFile(after.file.path)
        assertTrue("contrast ${lumaSpread(before)} -> ${lumaSpread(out)}", lumaSpread(out) > lumaSpread(before) * 1.25)
    }

    @Test
    fun blackAndWhiteIsTrulyMonochromeAndHighContrast() {
        val source = photo(300, 400)
        val bw = ImageEffects(filter = ImageFilter.GRAYSCALE)
        val image = ok(run(source, OutputSpec(ImageFormat.PNG), effects = bw, name = "bw"))
        val original = BitmapFactory.decodeFile(File(source.uri.removePrefix("file://")).path)
        val out = BitmapFactory.decodeFile(image.file.path)
        for (y in 0 until out.height step 7) {
            for (x in 0 until out.width step 7) {
                val p = out.getPixel(x, y)
                assertTrue("not grey at $x,$y", Color.red(p) == Color.green(p) && Color.green(p) == Color.blue(p))
            }
        }
        assertTrue("B&W spread ${lumaSpread(out)} vs ${lumaSpread(original)}", lumaSpread(out) > lumaSpread(original))
    }

    /** Standard deviation of luminance over a grid of pixels. */
    private fun lumaSpread(bitmap: Bitmap): Double {
        val values = ArrayList<Double>()
        for (y in 0 until bitmap.height step 3) {
            for (x in 0 until bitmap.width step 3) {
                val p = bitmap.getPixel(x, y)
                values += 0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
            }
        }
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    @Test
    fun brokenFilesFailCleanly() {
        val junk = File(dir, "broken.jpg").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)) }
        val empty = File(dir, "empty.png").apply { writeBytes(ByteArray(0)) }
        listOf(junk, empty).forEach { file ->
            val ref = FileRef(Uri.fromFile(file).toString(), file.name, "image/jpeg", file.length())
            val outName = "out-${file.nameWithoutExtension}"
            val result = run(ref, OutputSpec(ImageFormat.JPEG), name = outName)
            assertTrue("${file.name}: $result", result is Result.Failure)
            // No output file at all, so nothing empty is ever offered (the input itself may be empty).
            val leftovers = dir.listFiles().orEmpty().filter { it.name.startsWith("$outName.") }
            assertTrue("${file.name} left $leftovers", leftovers.none { it.length() == 0L })
        }
    }

    private fun means(bitmap: Bitmap): DoubleArray {
        val pixels = IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
        val sums = DoubleArray(3)
        pixels.forEach { p ->
            sums[0] += Color.red(p)
            sums[1] += Color.green(p)
            sums[2] += Color.blue(p)
        }
        return DoubleArray(3) { sums[it] / pixels.size }
    }

    private fun meanAbsDiff(a: Bitmap, b: Bitmap): Double {
        var sum = 0L
        for (y in 0 until a.height step 2) {
            for (x in 0 until a.width step 2) {
                val p = a.getPixel(x, y)
                val q = b.getPixel(x, y)
                sum += abs(Color.red(p) - Color.red(q)) + abs(Color.green(p) - Color.green(q))
            }
        }
        return sum.toDouble() / ((a.width / 2) * (a.height / 2) * 2)
    }

    private companion object {
        const val KB = 1024L
    }

    @Test
    fun everyOverlayFontIsBundledAndLooksDifferentInHindiToo() {
        val typefaces = OverlayTypefaces(context)
        val text = "नमस्ते Hello"
        val widths = OverlayFont.entries.map { font ->
            val typeface = typefaces[font]
            assertTrue("${font.name} was not loaded from ${font.asset}", typeface !== Typeface.DEFAULT_BOLD)
            val paint = Paint().apply {
                this.typeface = typeface
                textSize = 100f
            }
            // Every glyph is drawn by this font: the Devanagari line is as wide as real letters, not boxes.
            assertTrue(paint.hasGlyph("न") && paint.hasGlyph("H"))
            paint.measureText(text).roundToInt()
        }
        assertEquals("fonts should not look alike: $widths", OverlayFont.entries.size, widths.toSet().size)
    }
}
