package me.heyjohn.kubsauschedule.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.heyjohn.kubsau.Lesson
import me.heyjohn.kubsau.Schedule
import me.heyjohn.kubsau.ScheduleParser
import me.heyjohn.kubsauschedule.AppIconSwitcher
import me.heyjohn.kubsauschedule.notifications.LessonAlarmScheduler
import me.heyjohn.kubsauschedule.notifications.Notifications
import java.time.LocalDate

/**
 * Единая точка получения расписания для экрана, фонового воркера и ресиверов:
 * сеть, кэш, планирование напоминаний о парах и уведомление об изменениях.
 */
class ScheduleRepository(context: Context) {

    class Snapshot(val schedule: Schedule, val fetchedAt: Long, val fromCache: Boolean)

    private val appContext = context.applicationContext
    private val api = KubSauApi()
    private val cache = ScheduleCache(appContext)
    val settings = SettingsStore(appContext)
    val alarms = LessonAlarmScheduler(appContext)

    private val _latest = MutableStateFlow<Snapshot?>(null)
    /** Последнее известное расписание: из кэша при старте, затем из сети. */
    val latest: StateFlow<Snapshot?> = _latest.asStateFlow()

    private val mutex = Mutex()

    /** Читает кэш (один раз), результат остаётся в [latest]. */
    suspend fun loadCached(): Snapshot? = mutex.withLock {
        _latest.value ?: withContext(Dispatchers.IO) {
            cache.load()?.let { entry ->
                runCatching {
                    Snapshot(ScheduleParser.parse(entry.html, entry.group), entry.fetchedAt, fromCache = true)
                }.getOrNull()
            }
        }?.also { _latest.value = it }
    }

    /**
     * Загружает расписание с сайта, сохраняет в кэш и перепланирует напоминания о парах.
     * В фоновом режиме ([background]) дополнительно сравнивает пары на сегодня
     * с предыдущим результатом и при расхождении шлёт уведомление.
     *
     * @throws me.heyjohn.kubsau.KubSauException при сетевой ошибке или ошибке разбора
     */
    suspend fun refresh(group: String, background: Boolean): Snapshot {
        val previous = loadCached()?.schedule
        val loaded = withContext(Dispatchers.IO) { api.loadSchedule(group) }
        val snapshot = Snapshot(loaded.schedule, System.currentTimeMillis(), fromCache = false)

        mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching { cache.save(snapshot.schedule.group(), loaded.html, snapshot.fetchedAt) }
            }
            _latest.value = snapshot
            val current = settings.settings.value
            applyLessonAlarms(snapshot.schedule, current)

            if (background && previous != null && previous.group() == snapshot.schedule.group()) {
                if (contentDiffers(previous, snapshot.schedule)) {
                    AppIconSwitcher.setUpdated(appContext, true)
                }
                val today = LocalDate.now()
                val before = lessonsOn(previous, today)
                val after = lessonsOn(snapshot.schedule, today)
                if (current.changesEnabled && before != null && after != null && before != after) {
                    Notifications.showScheduleChanged(appContext, today, after)
                }
            }
        }
        return snapshot
    }

    /** Отличаются ли пары хотя бы в один день (флаг «сегодня» и служебные поля не учитываются). */
    private fun contentDiffers(a: Schedule, b: Schedule): Boolean = lessonsByDate(a) != lessonsByDate(b)

    private fun lessonsByDate(schedule: Schedule): Map<LocalDate, List<Lesson>> =
        schedule.weeks().flatMap { it.days() }.associate { it.date() to it.lessons() }

    /** Применяет текущие настройки: планирует или снимает напоминания о парах. */
    suspend fun applySettings() {
        val schedule = loadCached()?.schedule
        mutex.withLock { applyLessonAlarms(schedule, settings.settings.value) }
    }

    private fun applyLessonAlarms(schedule: Schedule?, current: NotificationSettings) {
        if (current.lessonsEnabled && schedule != null) {
            alarms.schedule(schedule, current.minutesBefore)
        } else {
            alarms.cancelAll()
        }
    }

    private fun lessonsOn(schedule: Schedule, date: LocalDate): List<Lesson>? =
        schedule.weeks().asSequence()
            .flatMap { it.days().asSequence() }
            .firstOrNull { it.date() == date }
            ?.lessons()
}
