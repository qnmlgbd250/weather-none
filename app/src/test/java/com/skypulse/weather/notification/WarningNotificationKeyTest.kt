package com.skypulse.weather.notification

import com.skypulse.weather.model.AlertContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WarningNotificationKeyTest {

    @Test
    fun `same warning content produces same key`() {
        val alert = baseAlert()

        val first = WarningNotificationKey.from(alert, "大风蓝色预警")
        val second = WarningNotificationKey.from(alert, "大风蓝色预警")

        assertEquals(first, second)
    }

    @Test
    fun `changed warning content produces different key`() {
        val first = WarningNotificationKey.from(baseAlert(), "大风蓝色预警")
        val second = WarningNotificationKey.from(
            baseAlert(description = "预计今天夜间阵风明显增强，请注意防范"),
            "大风蓝色预警"
        )

        assertNotEquals(first, second)
    }

    @Test
    fun `changed warning level produces different key`() {
        val first = WarningNotificationKey.from(baseAlert(level = "大风蓝色预警"), "大风蓝色预警")
        val second = WarningNotificationKey.from(baseAlert(level = "大风黄色预警"), "大风黄色预警")

        assertNotEquals(first, second)
    }

    private fun baseAlert(
        description: String = "预计今天白天有明显大风，请注意防范",
        level: String = "大风蓝色预警"
    ): AlertContent = AlertContent(
        id = "alert-1",
        regionCode = "110000",
        areaCode = "110100",
        type = "11",
        level = level,
        title = "北京市发布大风蓝色预警",
        description = description,
        status = "active",
        publishTime = 1783334400L
    )
}
