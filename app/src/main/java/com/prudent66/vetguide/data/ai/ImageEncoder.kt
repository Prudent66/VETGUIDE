package com.prudent66.vetguide.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Image → Base64 pipeline for the AI image flow (roadmap §4.2): captured or
 * gallery bytes are decoded, scaled down, and JPEG-compressed BEFORE Base64 —
 * never the original full-res image to the network (§4.2). The pure Base64
 * helpers are JVM-unit-testable; the Android decode/compress half is verified
 * on device (instrumented / manual QA).
 */
object ImageEncoder {

    /** Longest side cap for uploads — resolution stays diagnostic-reviewable. */
    const val DEFAULT_MAX_DIMENSION = 1024

    /** JPEG quality trade-off: small payload, adequate clinical detail. */
    const val DEFAULT_JPEG_QUALITY = 65

    fun toBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun fromBase64(base64: String): ByteArray = Base64.getDecoder().decode(base64)

    /** Decode → scale down → JPEG compress → Base64. */
    fun compressToBase64(
        rawBytes: ByteArray,
        maxDimension: Int = DEFAULT_MAX_DIMENSION,
        quality: Int = DEFAULT_JPEG_QUALITY,
    ): String {
        val bitmap = decode(rawBytes)
        return toBase64(compress(scaleDown(bitmap, maxDimension), quality))
    }

    /** Decode → center-crop to a square → scale down → JPEG compress → Base64. */
    fun centerCropSquareToBase64(
        rawBytes: ByteArray,
        maxDimension: Int = DEFAULT_MAX_DIMENSION,
        quality: Int = DEFAULT_JPEG_QUALITY,
    ): String {
        val bitmap = decode(rawBytes)
        val side = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        val square = Bitmap.createBitmap(bitmap, left, top, side, side)
        return toBase64(compress(scaleDown(square, maxDimension), quality))
    }

    private fun decode(rawBytes: ByteArray): Bitmap =
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: throw IllegalArgumentException("Unsupported or corrupt image data")

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest.toFloat()
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
}
