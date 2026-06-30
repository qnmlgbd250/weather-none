package com.skypulse.weather.domain

import android.content.Context
import com.skypulse.weather.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 检查 App 更新的 UseCase。
 *
 * 封装 GitHub Releases API 调用和版本号比较逻辑。
 * ViewModel 不直接发起网络请求，通过此 UseCase 获取更新信息。
 */
@Singleton
class CheckUpdateUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val GITHUB_API_URL =
            "https://api.github.com/repos/qnmlgbd250/weather-none/releases/latest"
        private const val CONNECT_TIMEOUT = 10_000
        private const val READ_TIMEOUT = 10_000
    }

    sealed class Result {
        data object UpToDate : Result()
        data class UpdateAvailable(val version: String, val url: String) : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun checkForUpdate(): Result {
        return try {
            val body = withContext(Dispatchers.IO) {
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()
                response
            }
            val json = JSONObject(body)
            val tagName = json.getString("tag_name").removePrefix("v")
            val current = BuildConfig.VERSION_NAME
            if (isNewerVersion(tagName, current)) {
                val htmlUrl = json.getString("html_url")
                Result.UpdateAvailable(tagName, htmlUrl)
            } else {
                Result.UpToDate
            }
        } catch (e: Exception) {
            Result.Error("检查更新失败，请稍后重试")
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
