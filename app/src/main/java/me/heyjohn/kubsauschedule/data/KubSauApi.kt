package me.heyjohn.kubsauschedule.data

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import me.heyjohn.kubsau.Group
import me.heyjohn.kubsau.KubSauException
import me.heyjohn.kubsau.Schedule
import me.heyjohn.kubsau.ScheduleParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Сетевой слой поверх kubsau-schedule4j.
 *
 * Клиент библиотеки [me.heyjohn.kubsau.KubSauSchedule] построен на java.net.http.HttpClient,
 * которого в Android нет, поэтому HTTP-запросы идут через [HttpURLConnection].
 */
class KubSauApi(private val baseUrl: String = DEFAULT_BASE_URL) {

    /** Результат загрузки: разобранное расписание и исходный HTML (для кэша). */
    class Loaded(val schedule: Schedule, val html: String)

    /** Подсказки по названию группы. */
    fun queryGroups(query: String): List<Group> {
        val url = "$baseUrl$SUGGESTIONS_PATH?query=${encode(query.trim())}&type_schedule=$TYPE_GROUP"
        return parseSuggestions(get(url, "application/json, text/plain, */*"))
    }

    /** Расписание группы на две недели. */
    fun loadSchedule(group: String): Loaded {
        val name = group.trim()
        require(name.isNotEmpty()) { "Название группы пустое" }
        val url = "$baseUrl/?type_schedule=$TYPE_GROUP&val=${encode(name)}"
        val html = get(url, "text/html")
        return Loaded(ScheduleParser.parse(html, name), html)
    }

    private fun parseSuggestions(json: String): List<Group> {
        val root = try {
            JsonParser.parseString(json)
        } catch (e: JsonSyntaxException) {
            throw KubSauException("Не удалось разобрать ответ сервера подсказок", e)
        }
        if (!root.isJsonObject) throw KubSauException("Неожиданный ответ сервера подсказок")
        val suggestions = root.asJsonObject.get("suggestions")
        if (suggestions == null || !suggestions.isJsonArray) return emptyList()
        return suggestions.asJsonArray.mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val obj = item.asJsonObject
            val value = obj.get("value")?.takeUnless { it.isJsonNull }?.asString?.trim()
            val data = obj.get("data")?.takeUnless { it.isJsonNull }?.asString
            if (value.isNullOrBlank()) null else Group(value, data)
        }
    }

    private fun get(url: String, accept: String): String {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw KubSauException("Ошибка сети при запросе $url", e)
        }
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9")

            val status = connection.responseCode
            if (status !in 200..299) {
                throw KubSauException("Сервер вернул HTTP $status на запрос $url")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            throw KubSauException("Ошибка сети при запросе $url", e)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    companion object {
        /** Совпадает с KubSauSchedule.DEFAULT_BASE_URL; сам класс не трогаем, чтобы не тянуть HttpClient. */
        const val DEFAULT_BASE_URL = "https://s.kubsau.ru"
        private const val SUGGESTIONS_PATH =
            "/bitrix/components/atom/atom.education.schedule.remote.data/get.php"
        private const val TYPE_GROUP = "1"
        private const val USER_AGENT = "KubSauScheduleApp/1.0 (kubsau-schedule4j)"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
    }
}
