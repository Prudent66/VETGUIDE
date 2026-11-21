package com.prudent66.vetguide.data.ai

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** JVM tests for the pure Base64 half of the image pipeline (§4.2). */
class ImageEncoderTest {

    @Test
    fun `base64 round trip preserves bytes`() {
        val bytes = listOf(0, 1, 2, 254, 255).map { it.toByte() }.toByteArray()

        assertArrayEquals(bytes, ImageEncoder.fromBase64(ImageEncoder.toBase64(bytes)))
    }

    @Test
    fun `base64 of a known vector matches`() {
        assertEquals("VkVUR1VJREU=", ImageEncoder.toBase64("VETGUIDE".toByteArray()))
    }

    @Test
    fun `fromBase64 rejects invalid payloads`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageEncoder.fromBase64("not!!valid-base64!")
        }
    }

    @Test
    fun `upload budget constants are present`() {
        assertEquals(800, ImageEncoder.DEFAULT_MAX_DIMENSION)
        assertEquals(70, ImageEncoder.DEFAULT_JPEG_QUALITY)
        assertEquals(90 * 1024, ImageEncoder.MAX_UPLOAD_BYTES)
        assertEquals(40, ImageEncoder.MIN_JPEG_QUALITY)
    }

    @Test
    fun `budget loop steps quality down only until the payload fits`() {
        val attempted = mutableListOf<Int>()

        val bytes = ImageEncoder.encodeWithinBudget(60, 40, 100) { quality ->
            attempted += quality
            ByteArray(if (quality >= 60) 200 else 50)
        }

        assertEquals(listOf(60, 50), attempted)
        assertEquals(50, bytes.size)
    }

    @Test
    fun `budget loop never drops below the quality floor`() {
        val attempted = mutableListOf<Int>()

        val bytes = ImageEncoder.encodeWithinBudget(60, 40, 10) { quality ->
            attempted += quality
            ByteArray(500)
        }

        assertEquals(listOf(60, 50, 40), attempted)
        assertEquals(500, bytes.size)
    }
}
