package me.heyjohn.kubsauschedule.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.heyjohn.kubsau.Lesson
import me.heyjohn.kubsau.LessonType
import me.heyjohn.kubsauschedule.MainActivity
import me.heyjohn.kubsauschedule.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Notifications {

    const val CHANNEL_LESSONS = "lessons"
    const val CHANNEL_CHANGES = "schedule_changes"
    const val ID_SCHEDULE_CHANGED = 1

    private val RU = Locale("ru")
    private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", RU)
    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", RU)

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LESSONS, "Напоминания о парах", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Напоминание перед началом пары"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CHANGES, "Изменения расписания", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Расписание на сегодня изменилось"
            }
        )
    }

    /** Разрешены ли уведомления (на Android 13+ учитывает POST_NOTIFICATIONS). */
    fun canPost(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission")
    fun showLesson(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_LESSONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    @SuppressLint("MissingPermission")
    fun showScheduleChanged(context: Context, date: LocalDate, lessons: List<Lesson>) {
        if (!canPost(context)) return
        val text = if (lessons.isEmpty()) {
            "Занятий сегодня нет"
        } else {
            lessons.joinToString("\n") { lesson ->
                buildString {
                    append(lesson.start().format(TIME_FMT)).append(' ').append(lesson.subject())
                    if (lesson.rooms().isNotEmpty()) append(" · ").append(lesson.rooms().joinToString(", "))
                }
            }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_CHANGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Расписание на ${date.format(DATE_FMT)} изменилось")
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_SCHEDULE_CHANGED, notification)
    }

    /** Заголовок и текст напоминания о паре. */
    fun lessonTexts(lesson: Lesson): Pair<String, String> {
        val title = "${lesson.subject()} в ${lesson.start().format(TIME_FMT)}"
        val parts = mutableListOf<String>()
        parts += "${lesson.start().format(TIME_FMT)}–${lesson.end().format(TIME_FMT)}"
        if (lesson.rooms().isNotEmpty()) parts += lesson.rooms().joinToString(", ")
        if (lesson.teachers().isNotEmpty()) parts += lesson.teachers().joinToString(", ") { it.toString() }
        if (lesson.type() == LessonType.LECTURE) parts += "Лекция"
        return title to parts.joinToString(" · ")
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
