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
 * 日志存储路径: /storage/emulated/0/Android/data/com.skypulse.weather/files/skypulselog/
 */
object FileLogger {

    private const val LOG_DIR = "skypulselog"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private var logDir: File? = null
    private var appVersion: String = "unknown"

    /**
     * 初始化日志目录（需在 Application 中调用）
     * 使用应用专属目录，无需额外权限
     */
    fun init(context: Context) {
        try {
            logDir = context.getExternalFilesDir(LOG_DIR)
            logDir?.mkdirs()
            appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
            } catch (_: Exception) { "unknown" }
            android.util.Log.i("FileLogger", "日志目录: ${logDir?.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("FileLogger", "初始化失败: ${e.message}")
        }
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
                    // 链式异常
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
            // 交给系统默认处理（弹窗 / 杀进程）
            defaultHandler?.uncaughtException(thread, throwable)
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
