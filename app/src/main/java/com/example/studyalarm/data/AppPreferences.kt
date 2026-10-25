package com.example.studyalarm.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("study_alarm_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_MEMORIZE_SEC = 120
        const val DEFAULT_QUESTION_COUNT = 3
        const val DEFAULT_MAX_ATTEMPTS = 2
        const val DEFAULT_CATEGORY = "解剖學"

        val MEMORIZE_OPTIONS = listOf(30, 60, 120, 180)
        val QUESTION_OPTIONS = listOf(2, 3, 4, 5)
        val ATTEMPT_OPTIONS = listOf(1, 2, 3, 4) // 支援最高 4 次答題失敗機會
    }

    var alarmHour: Int
        get() = prefs.getInt("alarm_hour", 7)
        set(value) = prefs.edit { putInt("alarm_hour", value) }

    var alarmMinute: Int
        get() = prefs.getInt("alarm_minute", 0)
        set(value) = prefs.edit { putInt("alarm_minute", value) }

    var isAlarmEnabled: Boolean
        get() = prefs.getBoolean("is_alarm_enabled", false)
        set(value) = prefs.edit { putBoolean("is_alarm_enabled", value) }

    var selectedCategory: String
        get() = prefs.getString("selected_category", DEFAULT_CATEGORY) ?: DEFAULT_CATEGORY
        set(value) = prefs.edit { putString("selected_category", value) }

    var memorizeDurationSec: Int
        get() = prefs.getInt("memorize_sec", DEFAULT_MEMORIZE_SEC)
        set(value) = prefs.edit { putInt("memorize_sec", value) }

    var questionCount: Int
        get() = prefs.getInt("question_count", DEFAULT_QUESTION_COUNT)
        set(value) = prefs.edit { putInt("question_count", value) }

    var maxAttempts: Int
        get() = prefs.getInt("max_attempts", DEFAULT_MAX_ATTEMPTS)
        set(value) = prefs.edit { putInt("max_attempts", value) }

    // 客製化鈴聲 URI (未設定則預設使用系統鬧鐘聲音)
    var customRingtoneUri: String?
        get() = prefs.getString("custom_ringtone_uri", null)
        set(value) = prefs.edit { putString("custom_ringtone_uri", value) }

    // 🟢 統計紀錄數據：總測驗次數
    var totalQuizCount: Int
        get() = prefs.getInt("total_quiz_count", 0)
        set(value) = prefs.edit { putInt("total_quiz_count", value) }

    // 🟢 統計紀錄數據：成功通關次數
    var successQuizCount: Int
        get() = prefs.getInt("success_quiz_count", 0)
        set(value) = prefs.edit { putInt("success_quiz_count", value) }

    // 🟢 寫入測驗結果方法
    fun recordQuizResult(isSuccess: Boolean) {
        totalQuizCount += 1
        if (isSuccess) {
            successQuizCount += 1
        }
    }
}