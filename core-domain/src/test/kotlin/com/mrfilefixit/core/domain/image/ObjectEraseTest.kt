package com.mrfilefixit.core.domain.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ObjectEraseTest {
    @Test
    fun `a stroke covers its path and nothing far from it`() {
        val erase = ObjectErase(listOf(EraseStroke(listOf(0.2f, 0.5f, 0.8f, 0.5f), radius = 0.05f)))
        val mask = erase.rasterize(100, 100, growPixels = 0)
        assertTrue(mask[50 * 100 + 50])
        assertTrue(mask[50 * 100 + 21])
        assertFalse(mask[10 * 100 + 50])
        assertFalse(mask[50 * 100 + 5])
    }

    @Test
    fun `a fast swipe leaves no gaps`() {
        val erase = ObjectErase(listOf(EraseStroke(listOf(0f, 0.5f, 1f, 0.5f), radius = 0.01f)))
        val mask = erase.rasterize(400, 100, growPixels = 0)
        assertTrue((0 until 400).all { mask[50 * 400 + it] })
    }

    @Test
    fun `a tap erases a dot and no strokes erase nothing`() {
        assertTrue(ObjectErase(emptyList()).isEmpty)
        assertTrue(ObjectErase(listOf(EraseStroke(emptyList(), 0.1f))).isEmpty)
        val tap = ObjectErase(listOf(EraseStroke(listOf(0.5f, 0.5f), 0.1f)))
        assertFalse(tap.isEmpty)
        assertTrue(tap.rasterize(50, 50)[25 * 50 + 25])
        assertThrows<IllegalArgumentException> { EraseStroke(listOf(0f, 0f), 0f) }
    }

    @Test
    fun `a hole in a plain wall is filled with the wall colour`() {
        val w = 40
        val h = 30
        val wall = 0xFF8090A0.toInt()
        val pixels = IntArray(w * h) { wall }
        val mask = BooleanArray(w * h)
        for (y in 10 until 20) for (x in 15 until 25) {
            pixels[y * w + x] = 0xFFFF0000.toInt()
            mask[y * w + x] = true
        }
        Inpainting.fill(pixels, w, h, mask)
        assertTrue(pixels.all { it == wall })
    }

    @Test
    fun `horizontal stripes carry on through the hole`() {
        val w = 60
        val h = 40
        val dark = 0xFF202020.toInt()
        val light = 0xFFE0E0E0.toInt()
        val stripe = { y: Int -> if ((y / 4) % 2 == 0) dark else light }
        val pixels = IntArray(w * h) { stripe(it / w) }
        val mask = BooleanArray(w * h)
        for (y in 12 until 28) for (x in 20 until 40) {
            pixels[y * w + x] = 0xFF00FF00.toInt()
            mask[y * w + x] = true
        }
        Inpainting.fill(pixels, w, h, mask)
        val wrong = (12 until 28).sumOf { y -> (20 until 40).count { x -> pixels[y * w + x] != stripe(y) } }
        assertTrue(wrong <= 16 * 20 / 20, "too many pixels off the stripe pattern: $wrong")
    }

    @Test
    fun `pixels outside the mask never change`() {
        val w = 20
        val h = 20
        val pixels = IntArray(w * h) { it * 7919 or (0xFF shl 24) }
        val before = pixels.copyOf()
        val mask = BooleanArray(w * h) { it % w in 5..9 && it / w in 5..9 }
        Inpainting.fill(pixels, w, h, mask)
        pixels.indices.filterNot { mask[it] }.forEach { assertEquals(before[it], pixels[it]) }
    }

    @Test
    fun `the fill goes from the edge of the hole inwards`() {
        val known = BooleanArray(25) { true }
        for (y in 1..3) for (x in 1..3) known[y * 5 + x] = false
        val order = Inpainting.fillOrder(5, 5, known)
        assertEquals(9, order.size)
        assertEquals(2 * 5 + 2, order.last())
    }
}
