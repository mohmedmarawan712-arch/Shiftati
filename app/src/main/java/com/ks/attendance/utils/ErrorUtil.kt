package com.ks.attendance.utils

import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException

object ErrorUtil {

    /**
     * Converts technical exceptions (OkHttp, network, timeouts, server, credentials)
     * into concise, friendly Arabic messages understandable by everyday users.
     */
    fun getUserFriendlyMessage(throwable: Throwable?, actionPrefix: String = ""): String {
        if (throwable == null) {
            return if (actionPrefix.isNotBlank()) actionPrefix else "حدث خطأ غير متوقع"
        }

        var current: Throwable? = throwable
        var isNoInternet = false
        var isTimeout = false
        var isServerDown = false
        var isSslError = false

        while (current != null) {
            when (current) {
                is UnknownHostException -> isNoInternet = true
                is SocketTimeoutException -> isTimeout = true
                is ConnectException -> isServerDown = true
                is SocketException -> isNoInternet = true
                is SSLException -> isSslError = true
            }

            val msg = current.message?.lowercase(Locale.ROOT) ?: ""
            if (msg.contains("unable to resolve host") ||
                msg.contains("no address associated") ||
                msg.contains("network unreachable") ||
                msg.contains("enonet") ||
                msg.contains("ehostunreach")
            ) {
                isNoInternet = true
            } else if (msg.contains("timeout") || msg.contains("timed out")) {
                isTimeout = true
            } else if (msg.contains("failed to connect") ||
                msg.contains("connection refused") ||
                msg.contains("connection reset") ||
                msg.contains("econnrefused")
            ) {
                isServerDown = true
            }
            current = current.cause
        }

        val friendlyReason = when {
            isNoInternet -> "لا يوجد اتصال بالإنترنت"
            isTimeout -> "استغرق الخادم وقتاً أطول للاستجابة"
            isServerDown -> "تعذر الوصول إلى خادم الخدمة"
            isSslError -> "فشل إنشاء اتصال آمن بالخادم"
            else -> {
                val rawMsg = throwable.localizedMessage ?: throwable.message ?: ""
                when {
                    rawMsg.contains("فشل تسجيل الدخول — كود") || rawMsg.contains("كود: -1") || rawMsg.contains("كود: 1") ->
                        "البريد الإلكتروني أو كلمة المرور غير صحيحة"
                    rawMsg.contains("غير مسجل دخول") ->
                        "انتهت الجلسة، يرجى إعادة تسجيل الدخول"
                    rawMsg.contains("لم تختر حساباً") ->
                        "يرجى تحديد حساب ESS أولاً"
                    rawMsg.contains("لا توجد تسجيلات حضور أو انصراف") ->
                        rawMsg
                    rawMsg.isNotBlank() && !rawMsg.contains("Exception") && !rawMsg.contains("okhttp") ->
                        rawMsg
                    else -> "حدث خطأ غير متوقع، يرجى المحاولة لاحقاً"
                }
            }
        }

        return if (actionPrefix.isNotBlank()) {
            "$actionPrefix: $friendlyReason"
        } else {
            friendlyReason
        }
    }
}
