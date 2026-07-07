package com.skypulse.weather.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件日志工具
 *
 * 日志按功能分文件存储：
 * - log_YYYY-MM-DD.txt    → 主页/通用日志
 * - widget_YYYY-MM-DD.txt → 小组件日志
 * - notif_YYYY-MM-DD.txt  → 通知日志
 *
 * 日志最多保留 3 天，超期自动清理。
 * 存储路径: /storage/emulated/0/Android/data/com.skypulse.weather/files/skypulselog/
 */
object FileLogger {

    private const val LOG_DIR = "skypulselog"
    private const val KEEP_DAYS = 3
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private var logDir: File? = null
    private var appVersion: String = "unknown"

    /** 小组件相关 TAG */
    private val widgetTags = setOf(
        "WidgetProvider", "WidgetWorker", "WidgetUpdater", "WidgetRefreshPolicy"
    )

    /** 通知相关 TAG */
    private val notifTags = setOf(
        "UrgentNotifWorker", "WeatherNotifWorker", "WeatherNotifScheduler",
        "NotificationDedup"
    )

    /**
     * 初始化日志目录（需在 Application 中调用）
     */
    fun init(context: Context) {
        try {
            logDir = context.getExternalFilesDir(LOG_DIR)
            logDir?.mkdirs()
            appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
            } catch (_: Exception) { "unknown" }
            android.util.Log.i("FileLogger", "日志目录: ${logDir?.absolutePath}")
            // 启动时清理旧日志
            cleanOldLogs()
        } catch (e: Exception) {
            android.util.Log.e("FileLogger", "初始化失败: ${e.message}")
        }
    }

    private fun getNamedLogFile(prefix: String): File {
        val dir = logDir ?: return File("/dev/null")
        val dateStr = dateFormat.format(Date())
        return File(dir, "${prefix}_$dateStr.txt")
    }

    /**
     * 注册全局崩溃捕获器，将未捕获异常写入日志文件
     */
    fun initCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = File(logDir, "crash_${dateFormat.format(Date())}.txt")
                val timeStr = timeFormat.format(Date())
                val deviceInfo = buildString {
                    appendLine("=== SkyPulse Crash Report ===")
                    appendLine("Time: $timeStr")
                    appendLine("App Version: $appVersion")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("Board: ${Build.BOARD}")
                    appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
                    appendLine()
                    appendLine("=== Exception ===")
                    appendLine("${throwable.javaClass.name}: ${throwable.message}")
                    appendLine()
                    appendLine("=== Stack Trace ===")
                    appendLine(throwable.stackTraceToString())
                    var cause = throwable.cause
                    var depth = 0
                    while (cause != null && depth < 5) {
                        appendLine()
                        appendLine("=== Caused by (depth ${++depth}) ===")
                        appendLine("${cause.javaClass.name}: ${cause.message}")
                        appendLine(cause.stackTraceToString())
                        cause = cause.cause
                    }
                }
                FileWriter(crashFile, true).use { it.append(deviceInfo) }
                android.util.Log.e("FileLogger", "崩溃已记录: ${crashFile.absolutePath}")
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 根据 TAG 判断日志分类，返回对应文件
     */
    private fun getLogFile(tag: String): File {
        val dir = logDir ?: return File("/dev/null")
        val dateStr = dateFormat.format(Date())
        val prefix = when {
            widgetTags.contains(tag) -> "widget"
            notifTags.contains(tag) -> "notif"
            else -> "log"
        }
        return File(dir, "${prefix}_$dateStr.txt")
    }

    /**
     * 写入日志
     */
    @Synchronized
    fun log(tag: String, level: String, message: String) {
        if (logDir == null) return

        try {
            val timeStr = timeFormat.format(Date())
            val logLine = "[$timeStr] [$level] [$tag] $message\n"

            val logFile = getLogFile(tag)
            FileWriter(logFile, true).use { writer ->
                writer.append(logLine)
            }
        } catch (e: Exception) {
            android.util.Log.e("FileLogger", "写入日志失败: ${e.message}")
        }
    }

    @Synchronized
    fun location(tag: String, level: String, message: String) {
        if (logDir == null) return

        try {
            val timeStr = timeFormat.format(Date())
            val threadName = Thread.currentThread().name
            val logLine = "[$timeStr] [$level] [$tag] [thread=$threadName] $message\n"

            val logFile = getNamedLogFile("loca")
            FileWriter(logFile, true).use { writer ->
                writer.append(logLine)
            }
        } catch (e: Exception) {
            android.util.Log.e("FileLogger", "write location log failed: ${e.message}")
        }
    }

    @Synchronized
    fun weather(tag: String, level: String, message: String) {
        if (logDir == null) return

        try {
            val timeStr = timeFormat.format(Date())
            val threadName = Thread.currentThread().name
            val logLine = "[$timeStr] [$level] [$tag] [thread=$threadName] $message\n"

            val logFile = getNamedLogFile("weather")
            FileWriter(logFile, true).use { writer ->
                writer.append(logLine)
            }
        } catch (e: Exception) {
            android.util.Log.e("FileLogger", "write weather log failed: ${e.message}")
        }
    }

    @Synchronized
    fun refresh(tag: String, level: String, message: String) {
        if (logDir == null) return

        try {
            val timeStr = timeFormat.format(Date())
            val threadName = Thread.currentThread().name
            val logLine = "[$timeStr] [$level] [$tag] [thread=$threadName] $message\n"

            val logFile = getNamedLogFile("refresh")
            FileWriter(logFile, true).use { writer ->
                writer.append(logLine)
            }
        } catch (e: Exception) {
            android.util.Log.e("FileLogger", "write refresh log failed: ${e.message}")
        }
    }

    fun d(tag: String, message: String) = log(tag, "D", message)
    fun i(tag: String, message: String) = log(tag, "I", message)
    fun w(tag: String, message: String) = log(tag, "W", message)
    fun e(tag: String, message: String) = log(tag, "E", message)
    fun e(tag: String, message: String, throwable: Throwable) = log(tag, "E", "$message\n${throwable.stackTraceToString()}")

    fun locD(tag: String, message: String) = location(tag, "D", message)
    fun locI(tag: String, message: String) = location(tag, "I", message)
    fun locW(tag: String, message: String) = location(tag, "W", message)
    fun locE(tag: String, message: String) = location(tag, "E", message)
    fun locE(tag: String, message: String, throwable: Throwable) = location(tag, "E", "$message\n${throwable.stackTraceToString()}")

    fun weatherD(tag: String, message: String) = weather(tag, "D", message)
    fun weatherI(tag: String, message: String) = weather(tag, "I", message)
    fun weatherW(tag: String, message: String) = weather(tag, "W", message)
    fun weatherE(tag: String, message: String) = weather(tag, "E", message)
    fun weatherE(tag: String, message: String, throwable: Throwable) = weather(tag, "E", "$message\n${throwable.stackTraceToString()}")

    fun refreshD(tag: String, message: String) = refresh(tag, "D", message)
    fun refreshI(tag: String, message: String) = refresh(tag, "I", message)
    fun refreshW(tag: String, message: String) = refresh(tag, "W", message)
    fun refreshE(tag: String, message: String) = refresh(tag, "E", message)
    fun refreshE(tag: String, message: String, throwable: Throwable) = refresh(tag, "E", "$message\n${throwable.stackTraceToString()}")

    /**
     * 清理超过 keepDays 天的日志文件（所有类型：log_ widget_ notif_ crash_）
     */
    fun cleanOldLogs(keepDays: Int = KEEP_DAYS) {
        try {
            val dir = logDir ?: return
            if (!dir.exists()) return

            val cutoff = System.currentTimeMillis() - (keepDays * 24 * 60 * 60 * 1000L)
            val prefixes = listOf("log_", "widget_", "notif_", "loca_", "weather_", "refresh_", "crash_")
            var deletedCount = 0
            dir.listFiles()?.forEach { file ->
                if (file.isFile && prefixes.any { file.name.startsWith(it) } && file.lastModified() < cutoff) {
                    file.delete()
                    deletedCount++
                }
            }
            if (deletedCount > 0) {
                android.util.Log.i("FileLogger", "清理了 $deletedCount 个过期日志文件")
            }
        } catch (e: Exception) {
            android.util.Log.e("FileLogger", "清理日志失败: ${e.message}")
        }
    }
}
