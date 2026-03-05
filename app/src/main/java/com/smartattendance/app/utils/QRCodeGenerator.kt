package com.smartattendance.app.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.smartattendance.app.models.QRToken
import java.util.UUID

/**
 * Generates dynamic QR codes that rotate every [Constants.QR_TOKEN_VALIDITY_MS] milliseconds.
 *
 * QR payload (JSON):
 * {
 *   "sessionId"  : "...",
 *   "token"      : "...",   // UUID, re-generated every 10 s
 *   "timestamp"  : 1234567890123,
 *   "expiresAt"  : 1234567900123
 * }
 */
object QRCodeGenerator {

    private val gson = Gson()
    private val writer = QRCodeWriter()

    /**
     * Build a fresh [QRToken] for the given session.
     * This must be called every 10 s by the teacher's timer.
     */
    fun buildToken(sessionId: String): QRToken {
        val now = System.currentTimeMillis()
        return QRToken(
            sessionId = sessionId,
            token     = UUID.randomUUID().toString(),
            timestamp = now,
            expiresAt = now + Constants.QR_TOKEN_VALIDITY_MS
        )
    }

    /**
     * Encode a [QRToken] to a [Bitmap].
     * @param token  The token to encode.
     * @param sizePx Bitmap width / height in pixels (default 800).
     * @return A square bitmap, or null if encoding fails.
     */
    fun tokenToBitmap(token: QRToken, sizePx: Int = Constants.QR_IMAGE_SIZE_PX): Bitmap? {
        val json = gson.toJson(token)
        return encode(json, sizePx)
    }

    /**
     * Raw encode: turn any string into a QR [Bitmap].
     */
    fun encode(content: String, sizePx: Int = Constants.QR_IMAGE_SIZE_PX): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.CHARACTER_SET    to "UTF-8",
                EncodeHintType.MARGIN           to 2
            )
            val matrix: BitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            bitMatrixToBitmap(matrix)
        } catch (e: WriterException) {
            e.printStackTrace()
            null
        }
    }

    private fun bitMatrixToBitmap(matrix: BitMatrix): Bitmap {
        val width  = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    /**
     * Parse a raw QR scan string back into a [QRToken].
     * Returns null if parsing fails.
     */
    fun parseToken(raw: String): QRToken? =
        try { gson.fromJson(raw, QRToken::class.java) } catch (e: Exception) { null }

    /**
     * Client-side quick check — server always does the authoritative check.
     */
    fun isTokenExpired(token: QRToken): Boolean =
        System.currentTimeMillis() > token.expiresAt
}
