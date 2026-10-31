package com.ks.attendance.data.remote

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * EssApiClient — mirrors the logic from 1.py but in Kotlin/OkHttp.
 * All calls are suspend functions that run on Dispatchers.IO.
 */
class EssApiClient {

    companion object {
        private const val BASE_URL =
            "https://essmcd.knowledgetouch.com/AP_ESS_PROD/servlet/scheduleservlet"
    }

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .addInterceptor { chain ->
            val original = chain.request()
            val req = original.newBuilder()
                .header("User-Agent",
                    "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/156.0.0.0 Mobile Safari/537.36")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "*/*")
                .header("Origin", "https://essmcd.knowledgetouch.com")
                .header("Referer", "https://essmcd.knowledgetouch.com/+++/index.html")
                .build()
            chain.proceed(req)
        }
        .build()

    // ─── Session state ────────────────────────────────────────────────────────
    var sessionId: String? = null
    var instanceId: String? = null
    var staffId: String? = null
    var schedulingUnitId: String? = null
    var detectedEmployeeName: String? = null

    val isLoggedIn get() = sessionId != null

    fun logout() {
        sessionId = null
        instanceId = null
        staffId = null
        schedulingUnitId = null
        detectedEmployeeName = null
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private suspend fun post(fields: Map<String, String>): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply {
                fields.forEach { (k, v) -> add(k, v) }
            }.build()
            val req = Request.Builder().url(BASE_URL).post(body).build()
            val resp = client.newCall(req).execute()
            val json = resp.body?.string() ?: "{}"
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            gson.fromJson(json, type)
        }

    // ─── API calls ────────────────────────────────────────────────────────────

    suspend fun login(email: String, password: String): Result<List<EssInstance>> = runCatching {
        val result = post(mapOf(
            "external" to "html",
            "command" to "Login",
            "idType" to "email",
            "idValue" to email,
            "password" to md5(password),
            "deviceType" to "ANDROID",
            "deviceLocale" to "ar"
        ))
        val status = (result["status"] as? Double)?.toInt() ?: -1
        if (status != 0) throw Exception("فشل تسجيل الدخول — كود: $status")
        sessionId = result["sessionId"]?.toString()
            ?: throw Exception("لم يُرجع الخادم sessionId")

        // Try extracting user name directly from login response
        detectedEmployeeName = (result["userName"] ?: result["displayName"] ?: result["fullName"] ?: result["name"])?.toString()

        // Fetch customer server list
        val customer = post(mapOf(
            "external" to "html",
            "command" to "GetCustomerServer",
            "SessionId" to sessionId!!
        ))
        if (detectedEmployeeName.isNullOrBlank()) {
            detectedEmployeeName = (customer["userName"] ?: customer["displayName"] ?: customer["fullName"])?.toString()
        }

        val serverList = customer["serverList"] as? Map<*, *>
        val instances = (serverList?.get("instances") as? List<*>)?.mapNotNull { item ->
            (item as? Map<*, *>)?.let {
                EssInstance(
                    instanceId = it["instanceId"]?.toString() ?: return@mapNotNull null,
                    instanceName = it["instanceName"]?.toString() ?: "",
                    staffId = it["staffId"]?.toString() ?: return@mapNotNull null
                )
            }
        } ?: emptyList()

        instances
    }

    suspend fun selectInstance(instance: EssInstance): Result<Unit> = runCatching {
        instanceId = instance.instanceId
        staffId = instance.staffId

        // Query Attachments (for schedulingUnit) and StaffDetails (for employee name as per curl)
        val patternJson = """{"applicationId":"PeopleScheduler","containers":[{"type":"Static","container":"Staff","facets":[{"facet":"Attachments","parameters":{"ids":[{"objectId":"$staffId"}]}},{"facet":"StaffDetails","parameters":{"ids":[{"objectId":"$staffId"}]}},{"facet":"Demographics","parameters":{"ids":[{"objectId":"$staffId"}]}}]}]}"""
        val result = post(mapOf(
            "external" to "html",
            "command" to "GetFacet",
            "userId" to staffId!!,
            "SessionId" to sessionId!!,
            "instanceId" to instanceId!!,
            "pattern" to patternJson
        ))

        val responseValues = result["responseValues"] as? List<*>
        responseValues?.forEach { value ->
            val v = value as? Map<*, *> ?: return@forEach
            val containers = v["containers"] as? List<*> ?: return@forEach
            containers.forEach { container ->
                val c = container as? Map<*, *> ?: return@forEach
                val containerData = c["containerData"] as? List<*> ?: return@forEach
                containerData.forEach { row ->
                    val r = row as? Map<*, *> ?: return@forEach
                    val facets = r["facets"] as? List<*> ?: return@forEach
                    facets.forEach { facet ->
                        val f = facet as? Map<*, *> ?: return@forEach
                        val facetName = f["facet"]?.toString()
                        val fd = f["facetData"]

                        if (facetName == "Attachments") {
                            val atts = (fd as? Map<*, *>)?.get("attachments") as? List<*>
                            val sorted = atts?.sortedByDescending { ((it as? Map<*, *>)?.get("start") as? String) ?: "" }
                            val suIdObj = (sorted?.firstOrNull() as? Map<*, *>)?.get("suId") as? Map<*, *>
                            schedulingUnitId = suIdObj?.get("objectId")?.toString()
                        } else if (facetName == "StaffDetails" || facetName == "Demographics") {
                            val extracted = findNameInMap(fd)
                            if (!extracted.isNullOrBlank()) {
                                detectedEmployeeName = extracted
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findNameInMap(data: Any?): String? {
        if (data == null) return null
        if (data is Map<*, *>) {
            val first = (data["firstName"] ?: data["first_name"])?.toString()?.trim() ?: ""
            val last = (data["lastName"] ?: data["last_name"])?.toString()?.trim() ?: ""
            if (first.isNotBlank() || last.isNotBlank()) {
                val full = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ")
                if (full.isNotBlank()) return toTwoTokens(full)
            }
            val direct = (data["fullName"] ?: data["name"] ?: data["staffName"] ?: data["preferredName"] ?: data["knownAs"] ?: data["displayName"])?.toString()?.trim()
            if (!direct.isNullOrBlank() && !direct.all { it.isDigit() }) {
                return toTwoTokens(direct)
            }
            for (value in data.values) {
                val res = findNameInMap(value)
                if (!res.isNullOrBlank()) return toTwoTokens(res)
            }
        } else if (data is List<*>) {
            for (item in data) {
                val res = findNameInMap(item)
                if (!res.isNullOrBlank()) return toTwoTokens(res)
            }
        }
        return null
    }

    private fun toTwoTokens(raw: String): String {
        return raw.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.take(2).joinToString(" ")
    }

    /**
     * Fetch clockings for a date range and return raw [ClockingItem] list.
     * Mirrors fetch_period_clockings() from 1.py.
     */
    suspend fun fetchClockings(startDate: Date, endDate: Date): Result<List<ClockingItem>> = runCatching {
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        val allItems = mutableListOf<ClockingItem>()
        val seen = mutableSetOf<String>()

        val cal = Calendar.getInstance()
        cal.time = startDate
        cal.add(Calendar.DAY_OF_YEAR, -2)
        var cursor = cal.time

        val fetchEnd = Calendar.getInstance().apply {
            time = endDate
            add(Calendar.DAY_OF_YEAR, 2)
        }.time

        while (!cursor.after(fetchEnd)) {
            val chunkEnd = Calendar.getInstance().apply {
                time = cursor
                add(Calendar.DAY_OF_YEAR, 6)
                if (time.after(fetchEnd)) time = fetchEnd
            }.time

            val result = post(mapOf(
                "external" to "html",
                "command" to "GetClockings",
                "SessionId" to (sessionId ?: throw Exception("غير مسجل دخول")),
                "instanceId" to (instanceId ?: throw Exception("لم تختر حساباً")),
                "staffId" to (staffId ?: throw Exception("لم تختر حساباً")),
                "schedulingUnitId" to (schedulingUnitId ?: throw Exception("لم يُعثر على schedulingUnitId")),
                "startDate" to sdf.format(cursor),
                "endDate" to sdf.format(chunkEnd)
            ))

            val responseValues = result["responseValues"] as? List<*>
            responseValues?.forEach { value ->
                val map = value as? Map<*, *> ?: return@forEach
                val clockings = map["clockings"] as? List<*> ?: return@forEach
                clockings.forEach { item ->
                    val c = item as? Map<*, *> ?: return@forEach
                    val key = "${c["clockedTime"]}_${c["eventType"]}_${c["status"]}"
                    if (seen.add(key)) {
                        allItems.add(ClockingItem(
                            clockedTime = c["clockedTime"]?.toString(),
                            eventType = c["eventType"]?.toString(),
                            status = c["status"]
                        ))
                    }
                }
            }

            cal.time = chunkEnd
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cursor = cal.time
        }
        allItems
    }

    fun logout() {
        sessionId = null
        instanceId = null
        staffId = null
        schedulingUnitId = null
    }
}
