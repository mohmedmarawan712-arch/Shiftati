package com.ks.attendance.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.ks.attendance.data.local.entity.AttendanceRecord
import com.ks.attendance.data.local.entity.DayNote
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

object BackupUtil {

    data class BackupData(
        val version: Int,
        val exportedAt: String,
        val preferences: Map<String, Any?>,
        val records: List<AttendanceRecord>,
        val notes: List<DayNote>
    )

    /**
     * Serializes complete application state (records, notes, settings, credentials) into JSON.
     */
    fun buildBackupJson(
        records: List<AttendanceRecord>,
        notes: List<DayNote>,
        preferences: Map<String, Any?>
    ): String {
        val root = JSONObject()
        root.put("app", "Shiftati")
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))

        // 1. Preferences
        val prefsJson = JSONObject()
        for ((key, value) in preferences) {
            prefsJson.put(key, value ?: JSONObject.NULL)
        }
        root.put("preferences", prefsJson)

        // 2. Records
        val recordsArray = JSONArray()
        for (rec in records) {
            val r = JSONObject()
            r.put("id", rec.id)
            r.put("workDate", rec.workDate)
            r.put("checkInTime", rec.checkInTime)
            if (rec.checkOutTime != null) r.put("checkOutTime", rec.checkOutTime)
            else r.put("checkOutTime", JSONObject.NULL)
            r.put("hoursWorked", rec.hoursWorked)
            r.put("note", rec.note)
            r.put("source", rec.source)
            r.put("isFromServer", rec.isFromServer)
            recordsArray.put(r)
        }
        root.put("records", recordsArray)

        // 3. Notes
        val notesArray = JSONArray()
        for (note in notes) {
            val n = JSONObject()
            n.put("id", note.id)
            n.put("date", note.date)
            n.put("noteText", note.noteText)
            n.put("createdAt", note.createdAt)
            notesArray.put(n)
        }
        root.put("notes", notesArray)

        return root.toString(2)
    }

    /**
     * Saves JSON content to a backup file and returns its path.
     */
    fun saveBackupToFile(context: Context, jsonContent: String): Result<String> = runCatching {
        val dir = getBackupDir(context)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Shiftati_Backup_$timeStamp.json"
        val file = File(dir, fileName)

        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                writer.write(jsonContent)
                writer.flush()
            }
        }
        file.absolutePath
    }

    /**
     * Parses a JSON backup string into a structured [BackupData] payload.
     */
    fun parseBackupJson(jsonString: String): Result<BackupData> = runCatching {
        val root = JSONObject(jsonString)
        val app = root.optString("app", "")
        if (app != "Shiftati" && !root.has("records")) {
            throw IllegalArgumentException("الملف المحدد ليس نسخة احتياطية صالحة لتطبيق شفتاتي")
        }

        val version = root.optInt("version", 1)
        val exportedAt = root.optString("exportedAt", "")

        // Preferences
        val prefsMap = mutableMapOf<String, Any?>()
        if (root.has("preferences")) {
            val prefsObj = root.getJSONObject("preferences")
            val keys = prefsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                prefsMap[key] = if (prefsObj.isNull(key)) null else prefsObj.get(key)
            }
        }

        // Records
        val recordsList = mutableListOf<AttendanceRecord>()
        if (root.has("records")) {
            val arr = root.getJSONArray("records")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val outTime = if (obj.isNull("checkOutTime")) null else obj.optLong("checkOutTime")
                recordsList.add(
                    AttendanceRecord(
                        id = obj.optLong("id", 0L),
                        workDate = obj.getString("workDate"),
                        checkInTime = obj.getLong("checkInTime"),
                        checkOutTime = outTime,
                        hoursWorked = obj.optDouble("hoursWorked", 0.0),
                        note = obj.optString("note", ""),
                        source = obj.optString("source", "manual"),
                        isFromServer = obj.optBoolean("isFromServer", false)
                    )
                )
            }
        }

        // Notes
        val notesList = mutableListOf<DayNote>()
        if (root.has("notes")) {
            val arr = root.getJSONArray("notes")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                notesList.add(
                    DayNote(
                        id = obj.optLong("id", 0L),
                        date = obj.getString("date"),
                        noteText = obj.getString("noteText"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        BackupData(
            version = version,
            exportedAt = exportedAt,
            preferences = prefsMap,
            records = recordsList,
            notes = notesList
        )
    }

    /**
     * Share backup file via Android Share sheet.
     */
    fun shareBackupFile(context: Context, filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) return

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية لتطبيق شفتاتي")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "مشاركة أو حفظ النسخة الاحتياطية").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {}
    }

    private fun getBackupDir(context: Context): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloads != null && downloads.exists()) {
            val folder = File(downloads, "Shiftati")
            if (!folder.exists()) folder.mkdirs()
            if (folder.canWrite()) return folder
        }
        val extDocs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (extDocs != null) {
            if (!extDocs.exists()) extDocs.mkdirs()
            return extDocs
        }
        val internal = File(context.filesDir, "backups")
        if (!internal.exists()) internal.mkdirs()
        return internal
    }
}
