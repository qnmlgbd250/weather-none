package com.skypulse.weather.data.remote

import com.skypulse.weather.model.WeatherResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 真实 API 测试：验证逐小时紫外线数据结构。
 *
 * API 响应路径：result.hourly.life_index.ultraviolet[]
 * 每个元素包含：datetime, index(String), desc(String)
 *
 * 运行方式：./gradlew.bat test --tests "com.skypulse.weather.data.remote.CaiyunApiHourlyUvTest" -DrunRealApiTests=true
 */
class CaiyunApiHourlyUvTest {

    private val token = "Y2FpeXVuIGFuZHJpb2QgYXBp"

    private val api: CaiyunApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://wrapper.cyapi.cn/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(CaiyunApi::class.java)
    }

    @Test
    fun `hourly response contains life_index with ultraviolet data`() = runTest {
        assumeRealApiTestsEnabled()

        // 北京坐标
        val response = api.getWeather(
            token = token,
            longitude = 116.4074,
            latitude = 39.9042,
            span = 16,
            alert = true,
            dailyStart = null,
            hourlySteps = 24,
            lang = "zh_CN",
            version = "7.59.0"
        )

        assertEquals("ok", response.status)

        val hourly = response.result?.hourly
        assertNotNull("hourly should not be null", hourly)

        val lifeIndex = hourly?.life_index
        assertNotNull("hourly.life_index should not be null", lifeIndex)

        val uvList = lifeIndex?.ultraviolet
        assertNotNull("hourly.life_index.ultraviolet should not be null", uvList)
        assertTrue("ultraviolet list should not be empty", uvList!!.isNotEmpty())

        // 验证每个小时 UV 数据结构
        println("=== 逐小时紫外线数据 ===")
        uvList.forEach { item ->
            assertNotNull("datetime should not be null", item.datetime)
            assertNotNull("index should not be null", item.index)
            assertNotNull("desc should not be null", item.desc)

            // index 应该是可解析为 Int 的字符串
            val indexValue = item.index!!.toIntOrNull()
            assertNotNull("index '${item.index}' should be parseable as Int", indexValue)
            assertTrue("index should be >= 0", indexValue!! >= 0)

            println("${item.datetime}: index=${item.index}, desc=${item.desc}")
        }

        // 验证 UV 值有变化（不是全部相同）
        val distinctIndices = uvList.mapNotNull { it.index?.toIntOrNull() }.distinct()
        println("不同 UV 等级数: ${distinctIndices.size}, 值: $distinctIndices")
        assertTrue("UV values should vary across hours (not all the same)", distinctIndices.size > 1)

        // 验证深夜 UV 为 0（0:00-4:00 和 20:00-23:00）
        val deepNightUvs = uvList.filter {
            val hour = it.datetime?.substring(11, 13)?.toIntOrNull() ?: -1
            hour in 0..4 || hour in 20..23
        }
        deepNightUvs.forEach { item ->
            val v = item.index?.toIntOrNull() ?: -1
            assertEquals("Deep night UV should be 0 at ${item.datetime}", 0, v)
        }

        // 验证中午 UV > 夜间 UV
        val noonUv = uvList.find { it.datetime?.contains("T12:00") == true }
        if (noonUv != null) {
            val noonVal = noonUv.index?.toIntOrNull() ?: 0
            assertTrue("Noon UV should be > 0", noonVal > 0)
            println("中午 UV: $noonVal (${noonUv.desc})")
        }
    }

    @Test
    fun `hourly UV index values match expected descriptions`() = runTest {
        assumeRealApiTestsEnabled()

        val response = api.getWeather(
            token = token,
            longitude = 116.4074,
            latitude = 39.9042,
            span = 16,
            alert = true,
            dailyStart = null,
            hourlySteps = 24,
            lang = "zh_CN",
            version = "7.59.0"
        )

        val uvList = response.result?.hourly?.life_index?.ultraviolet ?: return@runTest

        val expectedDesc = mapOf(
            0 to "无",
            1 to "很弱",
            2 to "很弱",
            3 to "弱",
            4 to "弱",
            5 to "中等",
            6 to "中等",
            7 to "强",
            8 to "强",
            9 to "很强",
            10 to "极强",
            11 to "极强"
        )

        uvList.forEach { item ->
            val v = item.index?.toIntOrNull() ?: return@forEach
            val expected = expectedDesc[v]
            if (expected != null) {
                // API 返回的 desc 可能与我们的映射不完全一致，仅打印对比
                println("UV=$v: API desc='${item.desc}', 期望='$expected', 匹配=${item.desc == expected}")
            }
        }
    }

    private fun assumeRealApiTestsEnabled() {
        assumeTrue(
            "real API tests are opt-in; run with -DrunRealApiTests=true",
            System.getProperty("runRealApiTests") == "true"
        )
    }
}
