package com.skypulse.weather.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 临时文件日志工具 - 用于测试调试
 * 日志存储路径: /storage/emulated/0/Android/data/com.skypulse.weather/files/skypulselog/
 *
 * ⚠️ 此文件为临时测试用途，正式发版前请删除此文件及相关调用
 */
object FileLogger {

    private const val LOG_DIR = "skypulselog"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private var logDir: File? = null

    /**
     * 初始化日志目录（需在 Application 中调用）
     * 使用应用专属目录，无需额外权限
     */
    fun init(context: Context) {
        try {
            // 使用应用专属外部存储目录，无需权限
            logDir = context.getExternalFilesDir(LOG_DIR)
            logDir?.mkdirs()
            android.util.Log.i("FileLogger", "日志目录: ${logDir?.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("FileLogger", "初始化失败: ${e.message}")
        }
    }

    private fun getLogFile(): File {
        val dir = logDir ?: return File("/dev/null")
        val dateStr = dateFormat.format(Date())
        return File(dir, "log_$dateStr.txt")
    }

    /**
     * 写入日志
     */
    @Synchronized
    fun log(tag: String, level: String, message: String) {
        val dir = logDir ?: return

        try {
            val timeStr = timeFormat.format(Date())
            val logLine = "[$timeStr] [$level] [$tag] $message\n"

            val logFile = getLogFile()
            FileWriter(logFile, true).use { writer ->
                writer.append(logLine)
            }
        } catch (e: Exception) {
            android.util.Log.e("FileLogger", "写入日志失败: ${e.message}")
        }
    }

    fun d(tag: String, message: String) = log(tag, "D", message)
    fun i(tag: String, message: String) = log(tag, "I", message)
    fun w(tag: String, message: String) = log(tag, "W", message)
    fun e(tag: String, message: String) = log(tag, "E", message)
    fun e(tag: String, message: String, throwable: Throwable) = log(tag, "E", "$message\n${throwable.stackTraceToString()}")

    /**
     * 清理指定天数之前的日志文件
     */
    fun cleanOldLogs(keepDays: Int = 7) {
        val dir = logDir ?: return

        try {
            if (!dir.exists()) return

            val cutoff = System.currentTimeMillis() - (keepDays * 24 * 60 * 60 * 1000L)
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("log_") && file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FileLogger", "清理日志失败: ${e.message}")
        }
    }
}
