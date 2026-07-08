package com.skypulse.weather.model

import com.squareup.moshi.JsonClass

/**
 * 彩云天气独立预警 API 响应模型。
 * 接口: https://starplucker.cyapi.cn/v3/alert/location?latitude=...&longitude=...
 */
@JsonClass(generateAdapter = true)
data class CaiyunAlertResponse(
    val admins: List<AlertAdmin>? = null,
    val alerts: List<CaiyunAlert>? = null
)

@JsonClass(generateAdapter = true)
data class AlertAdmin(
    val name: String? = null,
    val code: String? = null,
    val location: AlertLocation? = null
)

@JsonClass(generateAdapter = true)
data class AlertLocation(
    val latitude: Double? = null,
    val longitude: Double? = null
)

@JsonClass(generateAdapter = true)
data class CaiyunAlert(
    val id: String? = null,
    @com.squareup.moshi.Json(name = "region_code") val regionCode: String? = null,
    @com.squareup.moshi.Json(name = "area_code") val areaCode: String? = null,
    val source: Int? = null,
    @com.squareup.moshi.Json(name = "alert_type") val alertType: Int? = null,
    @com.squareup.moshi.Json(name = "publish_time") val publishTime: Long? = null,
    val color: AlertColor? = null,
    val status: Int? = null,
    val data: List<AlertLocalizedData>? = null
)

@JsonClass(generateAdapter = true)
data class AlertColor(
    val red: Int? = null,
    val green: Int? = null,
    val blue: Int? = null,
    val alpha: AlertAlpha? = null
)

@JsonClass(generateAdapter = true)
data class AlertAlpha(
    val value: Double? = null
)

@JsonClass(generateAdapter = true)
data class AlertLocalizedData(
    @com.squareup.moshi.Json(name = "language_code") val languageCode: String? = null,
    val title: String? = null,
    val text: String? = null,
    @com.squareup.moshi.Json(name = "details_url") val detailsUrl: String? = null,
    val level: String? = null,
    @com.squareup.moshi.Json(name = "icon_url") val iconUrl: String? = null,
    val name: String? = null
)

/**
 * 将彩云独立预警响应转换为通用的 AlertContent 列表，供 UI 使用。
 */
fun CaiyunAlertResponse.toAlertContentList(): List<AlertContent> {
    return alerts?.mapNotNull { alert ->
        val localized = alert.data?.firstOrNull { it.languageCode == "zh-CN" }
            ?: alert.data?.firstOrNull()
            ?: return@mapNotNull null
        if (!alert.isActiveWarning(localized)) return@mapNotNull null

        // 构建 level：优先用 name（如"台风白色预警"中的"白色"），再用 level 字段
        val level = localized.name ?: localized.level ?: ""

        AlertContent(
            province = admins?.lastOrNull()?.name,
            city = admins?.getOrNull(1)?.name,
            county = admins?.firstOrNull()?.name,
            title = localized.title,
            description = localized.text,
            level = level,
            type = alert.alertType?.toString(),
            status = "active",
            id = alert.id,
            regionCode = alert.regionCode,
            areaCode = alert.areaCode,
            publishTime = alert.publishTime
        )
    }.orEmpty().sortedByPublishTimeDescending()
}

private fun CaiyunAlert.isActiveWarning(localized: AlertLocalizedData): Boolean {
    val activeStatus = status == 1 || status == 2
    if (!activeStatus) return false

    val text = listOfNotNull(localized.title, localized.name, localized.text).joinToString(separator = " ")
    val isCancellation = listOf("解除", "取消", "终止").any { keyword -> text.contains(keyword) }
    return !isCancellation
}
