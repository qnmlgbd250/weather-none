package com.skypulse.weather.notification

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Manages notification deduplication to avoid sending repeated alerts.
 *
 * Strategy:
 * - Each notification type uses a unique key (e.g. "warning_{eventKey}" or "rain").
 * - Before sending, check if an identical notification was already sent recently.
 * - Different types have different dedup windows:
 *   - Rain alerts: 2 hours (short-lived weather changes)
 *   - Weather warnings: 30 days by event/content key (long-lived warnings must not repeat)
 *   - Temperature alerts: 12 hours (daily fluctuation)
 *   - Wind alerts: 2 hours
 *   - Extreme weather: 6 hours
 */
class NotificationDeduplicator(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("notification_dedup", Context.MODE_PRIVATE)

    companion object {
        // Dedup windows in milliseconds
        private const val RAIN_WINDOW_MS = 2 * 60 * 60 * 1000L       // 2 hours
        private const val WARNING_EVENT_RETENTION_MS = 30L * 24 * 60 * 60 * 1000L // 30 days
        private const val TEMP_CHANGE_WINDOW_MS = 12 * 60 * 60 * 1000L // 12 hours
        private const val WIND_WINDOW_MS = 2 * 60 * 60 * 1000L       // 2 hours
        private const val EXTREME_WINDOW_MS = 6 * 60 * 60 * 1000L    // 6 hours

        private const val KEY_RAIN = "rain"
        private const val KEY_TEMP = "temp_change"
        private const val KEY_WIND = "wind"
        private const val KEY_EXTREME = "extreme_weather"
        private const val KEY_WARNING_PREFIX = "warning_"

        private const val PREF_KEY_RECORDS = "records"
    }

    /**
     * Returns true if the rain alert should be sent (not a duplicate).
     */
    fun shouldNotifyRain(): Boolean {
        return shouldNotify(KEY_RAIN, RAIN_WINDOW_MS)
    }

    /**
     * Returns true if this warning event/content has not been announced recently.
     * Long-lived warnings can stay active for days, so the retention is intentionally
     * much longer than the worker interval or the old title-only window.
     */
    fun shouldNotifyWarningEvent(eventKey: String): Boolean {
        val key = KEY_WARNING_PREFIX + eventKey
        return shouldNotify(key, WARNING_EVENT_RETENTION_MS)
    }

    /**
     * Returns true if the temperature change alert should be sent (not a duplicate).
     */
    fun shouldNotifyTempChange(): Boolean {
        return shouldNotify(KEY_TEMP, TEMP_CHANGE_WINDOW_MS)
    }

    /**
     * Returns true if the wind alert should be sent (not a duplicate).
     */
    fun shouldNotifyWind(): Boolean {
        return shouldNotify(KEY_WIND, WIND_WINDOW_MS)
    }

    /**
     * Returns true if the extreme weather alert should be sent (not a duplicate).
     */
    fun shouldNotifyExtreme(): Boolean {
        return shouldNotify(KEY_EXTREME, EXTREME_WINDOW_MS)
    }

    /**
     * Cleans up expired records to prevent unbounded growth.
     * Should be called periodically (e.g., once per worker run).
     */
    fun cleanup() {
        val now = System.currentTimeMillis()
        val records = loadRecords()
        val maxWindow = maxOf(
            RAIN_WINDOW_MS,
            WARNING_EVENT_RETENTION_MS,
            TEMP_CHANGE_WINDOW_MS,
            WIND_WINDOW_MS,
            EXTREME_WINDOW_MS
        )
        val iterator = records.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > maxWindow) {
                iterator.remove()
            }
        }
        saveRecords(records)
    }

    // --- Internal ---

    private fun shouldNotify(key: String, windowMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val records = loadRecords()
        val lastSent = records[key]

        return if (lastSent == null || now - lastSent > windowMs) {
            // Not sent before or expired — allow sending
            records[key] = now
            saveRecords(records)
            true
        } else {
            // Still within dedup window — skip
            false
        }
    }

    private fun loadRecords(): MutableMap<String, Long> {
        val json = prefs.getString(PREF_KEY_RECORDS, null) ?: return mutableMapOf()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, Long>()
            for (key in obj.keys()) {
                map[key] = obj.getLong(key)
            }
            map
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun saveRecords(records: Map<String, Long>) {
        val obj = JSONObject()
        for ((key, value) in records) {
            obj.put(key, value)
        }
        prefs.edit().putString(PREF_KEY_RECORDS, obj.toString()).apply()
    }
}
