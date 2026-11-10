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
        assertEquals(1024, ImageEncoder.DEFAULT_MAX_DIMENSION)
        assertEquals(65, ImageEncoder.DEFAULT_JPEG_QUALITY)
    }
}
