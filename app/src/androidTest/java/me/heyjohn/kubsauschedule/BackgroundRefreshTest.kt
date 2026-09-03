package me.heyjohn.kubsauschedule

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import me.heyjohn.kubsauschedule.data.KubSauApi
import me.heyjohn.kubsauschedule.data.ScheduleCache
import me.heyjohn.kubsauschedule.notifications.LessonAlarmReceiver
import me.heyjohn.kubsauschedule.notifications.Notifications
import me.heyjohn.kubsauschedule.work.ScheduleRefreshWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Фоновое обновление: воркер видит, что пары на сегодня отличаются от кэша,
 * шлёт уведомление, меняет иконку и перепланирует напоминания.
 * Требует доступа к s.kubsau.ru и запуска в свежем процессе (репозиторий не должен быть загружен).
 */
@RunWith(AndroidJUnit4::class)
class BackgroundRefreshTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun grantNotifications() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun workerDetectsTodayChangeAndNotifies() = runBlocking {
        notificationManager.cancel(Notifications.ID_SCHEDULE_CHANGED)
        AppIconSwitcher.setUpdated(context, false)

        val loaded = KubSauApi().loadSchedule(GROUP)
        val today = LocalDate.now()
        val todayLessons = loaded.schedule.weeks().flatMap { it.days() }
            .firstOrNull { it.date() == today }?.lessons().orEmpty()
        assumeTrue("Сегодня нет пар, сравнивать нечего", todayLessons.isNotEmpty())

        val subject = todayLessons.first().subject()
        val tampered = loaded.html.replace(subject, "$subject (старое)")
        assumeTrue("Название пары не найдено в HTML как есть", tampered != loaded.html)
        ScheduleCache(context).save(loaded.schedule.group(), tampered, System.currentTimeMillis() - 3_600_000)

        val repository = App.repository(context)
        repository.settings.update { it.copy(lessonsEnabled = true, minutesBefore = 10, changesEnabled = true) }

        val worker = TestListenableWorkerBuilder<ScheduleRefreshWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())

        assertTrue("иконка должна смениться на «обновлено»", AppIconSwitcher.isUpdated(context))
        assertTrue(
            "должно появиться уведомление об изменении",
            notificationManager.activeNotifications.any { it.id == Notifications.ID_SCHEDULE_CHANGED },
        )
        assertTrue("напоминания о парах должны быть запланированы", repository.alarms.scheduledCount() > 0)
        assertFalse("кэш должен обновиться свежим HTML", ScheduleCache(context).load()!!.html.contains("(старое)"))
    }

    @Test
    fun lessonAlarmReceiverShowsNotification() {
        val intent = Intent(context, LessonAlarmReceiver::class.java)
            .putExtra(LessonAlarmReceiver.EXTRA_ID, 4242)
            .putExtra(LessonAlarmReceiver.EXTRA_TITLE, "Программирование в 8:00")
            .putExtra(LessonAlarmReceiver.EXTRA_TEXT, "8:00–9:30 · 733гл · Самойленкова В. А. · Лекция")
        LessonAlarmReceiver().onReceive(context, intent)

        assertTrue(notificationManager.activeNotifications.any { it.id == 4242 })
    }

    private companion object {
        const val GROUP = "БИ2601"
    }
}
