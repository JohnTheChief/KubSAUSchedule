package me.heyjohn.kubsauschedule.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationSettings(
    /** Напоминать о парах. */
    val lessonsEnabled: Boolean = false,
    /** За сколько минут до начала пары приходит напоминание. */
    val minutesBefore: Int = DEFAULT_MINUTES_BEFORE,
    /** Сообщать, если расписание на сегодня изменилось при фоновом обновлении. */
    val changesEnabled: Boolean = false,
) {
    companion object {
        const val DEFAULT_MINUTES_BEFORE = 15
        val MINUTES_OPTIONS = listOf(5, 10, 15, 30, 60)
    }
}

/** Настройки уведомлений в SharedPreferences с реактивным чтением. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<NotificationSettings> = _settings.asStateFlow()

    fun update(transform: (NotificationSettings) -> NotificationSettings): NotificationSettings {
        val updated = transform(_settings.value)
        prefs.edit()
            .putBoolean(KEY_LESSONS, updated.lessonsEnabled)
            .putInt(KEY_MINUTES, updated.minutesBefore)
            .putBoolean(KEY_CHANGES, updated.changesEnabled)
            .apply()
        _settings.value = updated
        return updated
    }

    private fun read() = NotificationSettings(
        lessonsEnabled = prefs.getBoolean(KEY_LESSONS, false),
        minutesBefore = prefs.getInt(KEY_MINUTES, NotificationSettings.DEFAULT_MINUTES_BEFORE),
        changesEnabled = prefs.getBoolean(KEY_CHANGES, false),
    )

    private companion object {
        const val PREFS = "settings"
        const val KEY_LESSONS = "lessons_enabled"
        const val KEY_MINUTES = "minutes_before"
        const val KEY_CHANGES = "changes_enabled"
    }
}
