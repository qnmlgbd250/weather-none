package com.skypulse.weather.notification

import com.skypulse.weather.model.AlertContent
import java.security.MessageDigest

internal object WarningNotificationKey {

    fun from(alert: AlertContent, cleanTitle: String): String {
        val title = cleanTitle.ifBlank { alert.title.orEmpty() }
        val contentHash = sha256(
            listOf(
                alert.title,
                alert.description,
                alert.level,
                alert.type,
                alert.status
            ).joinToString("|") { it.orEmpty().trim() }
        ).take(16)

        val eventSource = if (!alert.id.isNullOrBlank()) {
            "id=${alert.id.trim()}"
        } else {
            listOf(
                "region=${alert.regionCode.orEmpty().trim()}",
                "area=${alert.areaCode.orEmpty().trim()}",
                "type=${alert.type.orEmpty().trim()}",
                "title=${title.trim()}"
            ).joinToString("|")
        }

        return listOf(
            eventSource,
            "publish=${alert.publishTime ?: 0L}",
            "level=${alert.level.orEmpty().trim()}",
            "content=$contentHash"
        ).joinToString("|")
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }
}
