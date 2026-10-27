package com.mrfilefixit.core.processing.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.image.EncodeTarget
import com.mrfilefixit.core.domain.image.ImageEffects
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.ImageOp
import com.mrfilefixit.core.domain.image.ImageRequest
import com.mrfilefixit.core.domain.image.NaturalEnhance
import com.mrfilefixit.core.domain.image.OutputSpec
import com.mrfilefixit.core.domain.image.ProcessedImage
import com.mrfilefixit.core.domain.image.ResizeMode
import com.mrfilefixit.core.domain.logging.NoOpLogger
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.security.InputLimits
import java.io.File
import kotlin.math.abs
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

/** The image engine on a real device: outputs are never empty, size targets hold, Enhance keeps colours. */
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
    fun enhanceChangesDetailButNotColour() {
        val source = photo(600, 800)
        val before = ok(run(source, OutputSpec(ImageFormat.PNG), name = "before"))
        val enhance = ImageEffects(enhance = NaturalEnhance(1f))
        val after = ok(run(source, OutputSpec(ImageFormat.PNG), effects = enhance, name = "after"))
        val a = BitmapFactory.decodeFile(before.file.path)
        val b = BitmapFactory.decodeFile(after.file.path)
        assertEquals(a.width, b.width)
        val meanA = means(a)
        val meanB = means(b)
        // Colour (each channel's mean) stays put: no whitening, no tint.
        for (c in 0..2) assertTrue("channel $c: ${meanA[c]} vs ${meanB[c]}", abs(meanA[c] - meanB[c]) < 3.0)
        // But the pixels did change.
        assertTrue("enhance changed nothing", meanAbsDiff(a, b) > 0.5)
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
}
