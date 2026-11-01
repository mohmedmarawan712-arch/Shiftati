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
     * Resolves or generates a persistent device ID matching the Android ID format.
     */
    fun getDeviceId(context: Context): String {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                androidId
            } else {
                "b4cfd84c7a095b7f"
            }
        } catch (_: Exception) {
            "b4cfd84c7a095b7f"
        }
    }
}
