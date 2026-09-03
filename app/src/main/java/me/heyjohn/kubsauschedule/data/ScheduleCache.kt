package me.heyjohn.kubsauschedule.data

import android.content.Context
import java.io.File

/**
 * Кэш последнего результата: исходный HTML страницы расписания и название группы.
 * При старте HTML заново разбирается библиотекой, так что сериализовать модель не нужно.
 */
class ScheduleCache(context: Context) {

    class Entry(val group: String, val html: String, val fetchedAt: Long)

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val htmlFile = File(context.filesDir, HTML_FILE)

    fun load(): Entry? {
        val group = prefs.getString(KEY_GROUP, null) ?: return null
        val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
        if (!htmlFile.isFile) return null
        val html = runCatching { htmlFile.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        return Entry(group, html, fetchedAt)
    }

    fun save(group: String, html: String, fetchedAt: Long) {
        val tmp = File(htmlFile.parentFile, "$HTML_FILE.tmp")
        tmp.writeText(html, Charsets.UTF_8)
        if (!tmp.renameTo(htmlFile)) {
            htmlFile.delete()
            check(tmp.renameTo(htmlFile)) { "Не удалось сохранить кэш" }
        }
        prefs.edit()
            .putString(KEY_GROUP, group)
            .putLong(KEY_FETCHED_AT, fetchedAt)
            .apply()
    }

    private companion object {
        const val PREFS = "schedule_cache"
        const val HTML_FILE = "last_schedule.html"
        const val KEY_GROUP = "group"
        const val KEY_FETCHED_AT = "fetched_at"
    }
}
