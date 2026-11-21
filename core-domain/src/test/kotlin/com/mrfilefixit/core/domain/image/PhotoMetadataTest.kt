package com.mrfilefixit.core.domain.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhotoMetadataTest {
    @Test
    fun `removing everything keeps no tags`() {
        assertTrue(MetadataGroup.keptTags(MetadataGroup.ALL).isEmpty())
    }

    @Test
    fun `removing only the phone model keeps location, date and camera settings`() {
        val kept = MetadataGroup.keptTags(setOf(MetadataGroup.DEVICE))
        assertFalse("Make" in kept)
        assertFalse("Model" in kept)
        assertTrue("GPSLatitude" in kept)
        assertTrue("DateTimeOriginal" in kept)
        assertTrue("ExposureTime" in kept)
    }

    @Test
    fun `removing the date also drops the GPS time stamps when location stays`() {
        val kept = MetadataGroup.keptTags(setOf(MetadataGroup.DATE_TIME))
        assertTrue("GPSLatitude" in kept)
        assertFalse("GPSDateStamp" in kept)
        assertFalse("GPSTimeStamp" in kept)
        assertFalse("DateTime" in kept)
    }

    @Test
    fun `groups do not overlap`() {
        val all = MetadataGroup.entries.flatMap { it.tags }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `device name joins maker and model without repeating the maker`() {
        assertEquals("samsung SM-S918B", MetadataSummary.deviceName("samsung", "SM-S918B"))
        assertEquals("Google Pixel 8", MetadataSummary.deviceName("Google", "Pixel 8"))
        assertEquals("Xiaomi 14", MetadataSummary.deviceName("Xiaomi", "Xiaomi 14"))
        assertEquals("Pixel 8", MetadataSummary.deviceName(null, " Pixel 8 "))
        assertEquals(null, MetadataSummary.deviceName(" ", null))
    }

    @Test
    fun `a summary lists the groups the photo has`() {
        val summary = MetadataSummary(device = "Pixel 8", takenAt = null, hasLocation = true)
        assertEquals(setOf(MetadataGroup.DEVICE, MetadataGroup.LOCATION), summary.groups)
        assertTrue(MetadataSummary.NONE.isEmpty)
    }
}
