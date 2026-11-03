package com.ks.attendance.utils.qr

import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object QrUtil {

    const val EVENT_CLOCK_IN = "ClockIn"
    const val EVENT_CLOCK_OUT = "ClockOut"

    /**
     * Builds the JSON payload matching ClockIn.txt and ClockOut.txt:
     * {"staffId":"46741","staffName":"Ahmed Marawan Mohamd Mohamd","suId":"1480197","event":"ClockIn","clockTime":"2026/10/24 19:07:18.050","lat":null,"lon":null,"deviceId":"b4cfd84c7a095b7f"}
     */
    fun buildAttendanceQrPayload(
        staffId: String,
        staffName: String,
        suId: String,
        event: String,
        clockTime: Date = Date(),
        deviceId: String
    ): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US)
        val formattedTime = sdf.format(clockTime)

        val json = JSONObject()
        json.put("staffId", staffId)
        json.put("staffName", staffName)
        json.put("suId", suId)
        json.put("event", event)
        json.put("clockTime", formattedTime)
        json.put("lat", JSONObject.NULL)
        json.put("lon", JSONObject.NULL)
        json.put("deviceId", deviceId)

        return json.toString()
    }

    /**
     * Generates a high-contrast black-and-white Bitmap QR Code.
     * Uses Project Nayuki's pure Java QR encoder.
     */
    fun generateQrBitmap(
        content: String,
        sizePx: Int = 600,
        border: Int = 2
    ): Bitmap {
        val qr = QrCode.encodeText(content, QrCode.Ecc.MEDIUM)
        val qrSize = qr.size
        val totalModules = qrSize + (border * 2)
        val scale = (sizePx / totalModules).coerceAtLeast(1)
        val finalSize = totalModules * scale

        val bitmap = Bitmap.createBitmap(finalSize, finalSize, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(finalSize * finalSize)

        val colorBlack = android.graphics.Color.BLACK
        val colorWhite = android.graphics.Color.WHITE

        for (y in 0 until finalSize) {
            val moduleY = (y / scale) - border
            for (x in 0 until finalSize) {
                val moduleX = (x / scale) - border
                val isDark = if (moduleX in 0 until qrSize && moduleY in 0 until qrSize) {
                    qr.getModule(moduleX, moduleY)
                } else {
                    false
                }
                pixels[y * finalSize + x] = if (isDark) colorBlack else colorWhite
            }
        }

        bitmap.setPixels(pixels, 0, finalSize, 0, 0, finalSize, finalSize)
        return bitmap
    }

    /**
     * Resolves the real hardware Android ID for the current phone.
     * If unavailable or emulator default, uses a persistent random 16-hex UUID stored in SharedPreferences.
     */
    fun getBaseDeviceId(context: Context): String {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                androidId.lowercase().trim()
            } else {
                getOrCreatePersistentFallbackId(context)
            }
        } catch (_: Exception) {
            getOrCreatePersistentFallbackId(context)
        }
    }

    /**
     * Resolves a unique 16-character hex device ID for a specific account on this phone.
     * - Primary account uses the genuine phone's Android ID.
     * - Additional/secondary accounts on the same phone receive a unique, deterministic 16-hex ID
     *   derived cryptographically from (Android ID + account email/identifier).
     */
    fun getDeviceIdForAccount(context: Context, accountEmail: String = "", isPrimary: Boolean = false): String {
        val baseId = getBaseDeviceId(context)
        if (accountEmail.isBlank() || isPrimary) {
            return baseId
        }
        return try {
            val input = "$baseId:${accountEmail.trim().lowercase()}"
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.take(8).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            baseId
        }
    }

    /** Legacy helper returning base device ID */
    fun getDeviceId(context: Context): String = getBaseDeviceId(context)

    private fun getOrCreatePersistentFallbackId(context: Context): String {
        val prefs = context.getSharedPreferences("ks_device_meta", Context.MODE_PRIVATE)
        var id = prefs.getString("persistent_device_id", null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString().replace("-", "").take(16).lowercase()
            prefs.edit().putString("persistent_device_id", id).apply()
        }
        return id
    }
}
