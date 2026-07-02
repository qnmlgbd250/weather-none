package com.skypulse.weather.domain

import com.skypulse.weather.BuildConfig
import com.skypulse.weather.data.remote.GithubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val githubApi: GithubApi
) {

    sealed class Result {
        data object UpToDate : Result()
        data class UpdateAvailable(val version: String, val url: String) : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun checkForUpdate(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val release = githubApi.getLatestRelease()
                val tagName = release.tagName.removePrefix("v")
                val current = BuildConfig.VERSION_NAME
                if (isNewerVersion(tagName, current)) {
                    Result.UpdateAvailable(tagName, release.htmlUrl)
                } else {
                    Result.UpToDate
                }
            } catch (e: Exception) {
                Result.Error("检查更新失败，请稍后重试")
            }
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
