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
    fun `toAlertContentList keeps status 2 published warnings`() {
        val response = CaiyunAlertResponse(
            alerts = listOf(
                CaiyunAlert(
                    id = "alert-2",
                    regionCode = "CN",
                    areaCode = "440300",
                    alertType = 156110203,
                    publishTime = 1783375680,
                    status = 2,
                    data = listOf(
                        AlertLocalizedData(
                            languageCode = "zh-CN",
                            title = "深圳市气象台发布暴雨黄色预警[III级/较重]",
                            text = "目前全市暴雨黄色预警信号生效中，请继续防御暴雨可能引发的局部内涝。",
                            level = "yellow",
                            name = "暴雨黄色预警"
                        )
                    )
                )
            )
        )

        val alerts = response.toAlertContentList()

        assertEquals(1, alerts.size)
        assertEquals("alert-2", alerts[0].id)
        assertEquals("active", alerts[0].status)
        assertEquals("暴雨黄色预警", alerts[0].level)
    }

    @Test
    fun `toAlertContentList sorts active warnings by publish time descending`() {
        val response = CaiyunAlertResponse(
            alerts = listOf(
                activeAlert(id = "old-alert", publishTime = 1783334400L, title = "旧预警"),
                activeAlert(id = "new-alert", publishTime = 1783375680L, title = "新预警"),
                activeAlert(id = "middle-alert", publishTime = 1783350000L, title = "中间预警")
            )
        )

        val alerts = response.toAlertContentList()

        assertEquals(listOf("new-alert", "middle-alert", "old-alert"), alerts.map { it.id })
    }

    @Test
    fun `toAlertContentList drops cancelled warnings`() {
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

    @Test
    fun `toAlertContentList drops inactive warnings`() {
        val response = CaiyunAlertResponse(
            alerts = listOf(
                CaiyunAlert(
                    id = "alert-3",
                    status = 3,
                    data = listOf(
                        AlertLocalizedData(
                            languageCode = "zh-CN",
                            title = "深圳市气象台发布暴雨黄色预警",
                            text = "暴雨黄色预警信号生效中。"
                        )
                    )
                )
            )
        )

        assertEquals(0, response.toAlertContentList().size)
    }

    private fun activeAlert(id: String, publishTime: Long, title: String): CaiyunAlert {
        return CaiyunAlert(
            id = id,
            publishTime = publishTime,
            status = 1,
            data = listOf(
                AlertLocalizedData(
                    languageCode = "zh-CN",
                    title = title,
                    text = "$title 正在生效"
                )
            )
        )
    }
}
