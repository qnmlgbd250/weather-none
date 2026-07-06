package com.skypulse.weather.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CaiyunAlertResponseTest {

    @Test
    fun `toAlertContentList keeps active warning metadata`() {
        val response = CaiyunAlertResponse(
            alerts = listOf(
                CaiyunAlert(
                    id = "alert-1",
                    regionCode = "110000",
                    areaCode = "110100",
                    alertType = 11,
                    publishTime = 1783334400,
                    status = 1,
                    data = listOf(
                        AlertLocalizedData(
                            languageCode = "zh-CN",
                            title = "北京市发布大风蓝色预警",
                            text = "大风蓝色预警持续生效",
                            level = "蓝色",
                            name = "大风蓝色预警"
                        )
                    )
                )
            )
        )

        val alerts = response.toAlertContentList()

        assertEquals(1, alerts.size)
        assertEquals("alert-1", alerts[0].id)
        assertEquals("110000", alerts[0].regionCode)
        assertEquals("110100", alerts[0].areaCode)
        assertEquals(1783334400L, alerts[0].publishTime)
        assertEquals("active", alerts[0].status)
    }

    @Test
    fun `toAlertContentList drops inactive warnings`() {
        val response = CaiyunAlertResponse(
            alerts = listOf(
                CaiyunAlert(
                    id = "alert-1",
                    status = 2,
                    data = listOf(
                        AlertLocalizedData(
                            languageCode = "zh-CN",
                            title = "北京市解除大风蓝色预警",
                            text = "预警已解除"
                        )
                    )
                )
            )
        )

        assertEquals(0, response.toAlertContentList().size)
    }
}
