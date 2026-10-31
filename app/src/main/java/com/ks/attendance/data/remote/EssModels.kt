package com.ks.attendance.data.remote

import com.google.gson.annotations.SerializedName

// ─── Request models ───────────────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val passwordMd5: String,
    val deviceType: String = "ANDROID",
    val deviceLocale: String = "ar"
)

// ─── Response models ──────────────────────────────────────────────────────────

data class EssBaseResponse(
    @SerializedName("status") val status: Int = -1,
    @SerializedName("sessionId") val sessionId: String? = null,
    @SerializedName("responseValues") val responseValues: List<Map<String, Any>>? = null,
    @SerializedName("serverList") val serverList: ServerList? = null
)

data class ServerList(
    @SerializedName("instances") val instances: List<EssInstance> = emptyList()
)

data class EssInstance(
    @SerializedName("instanceId") val instanceId: String = "",
    @SerializedName("instanceName") val instanceName: String = "",
    @SerializedName("staffId") val staffId: String = ""
)

data class ClockingItem(
    @SerializedName("clockedTime") val clockedTime: String?,
    @SerializedName("eventType") val eventType: String?,   // "ClockIn" | "ClockOut"
    @SerializedName("status") val status: Any?
)
