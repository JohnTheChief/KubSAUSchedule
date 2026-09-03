package me.heyjohn.kubsauschedule.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import me.heyjohn.kubsau.Schedule
import java.time.ZoneId

/**
 * Планирует напоминания о парах через [AlarmManager]: по одному будильнику на каждую
 * будущую пару из расписания. Список выданных request code хранится в SharedPreferences,
 * чтобы при следующем планировании снять все старые будильники.
 */
class LessonAlarmScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun schedule(schedule: Schedule, minutesBefore: Int) {
        cancelAll()
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val exact = canScheduleExact(appContext)
        val codes = mutableSetOf<String>()
        var code = BASE_REQUEST_CODE

        for (week in schedule.weeks()) {
            for (day in week.days()) {
                for (lesson in day.lessons()) {
                    val startAt = day.date().atTime(lesson.start()).atZone(zone).toInstant().toEpochMilli()
                    val triggerAt = startAt - minutesBefore * 60_000L
                    if (triggerAt <= now) continue

                    val (title, text) = Notifications.lessonTexts(lesson)
                    val intent = Intent(appContext, LessonAlarmReceiver::class.java)
                        .putExtra(LessonAlarmReceiver.EXTRA_ID, code)
                        .putExtra(LessonAlarmReceiver.EXTRA_TITLE, title)
                        .putExtra(LessonAlarmReceiver.EXTRA_TEXT, text)
                    val pending = pendingIntent(code, intent)
                    if (exact) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                    }
                    codes += code.toString()
                    code++
                    if (codes.size >= MAX_ALARMS) break
                }
            }
        }
        prefs.edit().putStringSet(KEY_CODES, codes).apply()
    }

    fun cancelAll() {
        val codes = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty()
        for (code in codes) {
            val requestCode = code.toIntOrNull() ?: continue
            alarmManager.cancel(pendingIntent(requestCode, Intent(appContext, LessonAlarmReceiver::class.java)))
        }
        prefs.edit().remove(KEY_CODES).apply()
    }

    /** Сколько будильников сейчас запланировано. */
    fun scheduledCount(): Int = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty().size

    private fun pendingIntent(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            appContext, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        private const val PREFS = "lesson_alarms"
        private const val KEY_CODES = "codes"
        private const val BASE_REQUEST_CODE = 1000
        /** AlarmManager ограничивает приложение 500 будильниками; две недели пар в него укладываются с запасом. */
        private const val MAX_ALARMS = 200

        fun canScheduleExact(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        }
    }
}
