package com.mrfilefixit.feature.image.work

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.image.EncodeTarget
import com.mrfilefixit.core.domain.image.ImageBatchSpec
import com.mrfilefixit.core.domain.image.ImageEffects
import com.mrfilefixit.core.domain.image.ImageFilter
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.NaturalEnhance
import com.mrfilefixit.core.domain.image.OutputSpec
import com.mrfilefixit.core.domain.image.PhotoStamp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class BatchSpecSerializationTest {
    private val sources = listOf(FileRef("content://a", "a.jpg", "image/jpeg", 10))

    @Test
    fun `effects survive the trip through the job file`() {
        val spec = ImageBatchSpec(
            operations = emptyList(),
            output = OutputSpec(ImageFormat.PNG, EncodeTarget.Quality(80)),
            suffix = "-edited",
            effects = ImageEffects(
                filter = ImageFilter.DOCUMENT,
                stamp = PhotoStamp(listOf("Name", "25-10-2026")),
                enhance = NaturalEnhance(0.6f, 2),
            ),
        )
        val (decodedSources, decoded) = BatchSpecSerialization.decode(BatchSpecSerialization.encode(sources, spec))
        assertEquals(sources, decodedSources)
        assertEquals(spec.effects, decoded.effects)
    }

    @Test
    fun `jobs without effects stay as they were`() {
        val spec = ImageBatchSpec(emptyList(), OutputSpec(ImageFormat.JPEG, EncodeTarget.Quality(80)), "")
        val text = BatchSpecSerialization.encode(sources, spec)
        assertFalse(text.contains("effects"))
        val old = """{"sources":[{"uri":"content://a","name":"a.jpg","mime":"image/jpeg","size":10}],""" +
            """"ops":[],"format":"JPEG","quality":80,"suffix":""}"""
        assertEquals(ImageEffects.NONE, BatchSpecSerialization.decode(old).second.effects)
    }
}
