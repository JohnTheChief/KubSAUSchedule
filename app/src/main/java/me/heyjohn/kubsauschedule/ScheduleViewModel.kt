package me.heyjohn.kubsauschedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.heyjohn.kubsau.Group
import me.heyjohn.kubsau.GroupNotFoundException
import me.heyjohn.kubsau.Schedule
import me.heyjohn.kubsauschedule.data.KubSauApi
import me.heyjohn.kubsauschedule.data.NotificationSettings

data class UiState(
    /** Идёт чтение кэша при старте: расписания ещё нет, но и "пусто" показывать рано. */
    val initializing: Boolean = true,
    val query: String = "",
    val suggestions: List<Group> = emptyList(),
    /** Показанное расписание (из кэша или из сети). */
    val schedule: Schedule? = null,
    /** Когда показанное расписание было получено с сайта (epoch millis). */
    val fetchedAt: Long? = null,
    /** Показанные данные взяты из кэша и ещё не обновлены. */
    val fromCache: Boolean = false,
    /** Идёт сетевой запрос расписания. */
    val loading: Boolean = false,
    /** Последний запрос провалился: доступен ретрай по красному кружку. */
    val failed: Boolean = false,
    /** Сайт не знает такую группу (ретрай бессмыслен, кружок не показываем). */
    val notFoundGroup: String? = null,
)

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = App.repository(app)
    private val api = KubSauApi()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    val settings: StateFlow<NotificationSettings> = repository.settings.settings

    private var loadJob: Job? = null
    private var suggestJob: Job? = null
    private var lastRequestedGroup: String? = null

    init {
        viewModelScope.launch {
            repository.latest.collect { snapshot ->
                _state.update {
                    it.copy(
                        schedule = snapshot?.schedule,
                        fetchedAt = snapshot?.fetchedAt,
                        fromCache = snapshot?.fromCache == true,
                    )
                }
            }
        }
        viewModelScope.launch {
            val cached = repository.loadCached()
            _state.update { it.copy(initializing = false, query = cached?.schedule?.group() ?: it.query) }
            if (cached != null) load(cached.schedule.group())
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        suggestJob?.cancel()
        val q = query.trim()
        if (q.length < MIN_SUGGEST_LENGTH) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            val groups = withContext(Dispatchers.IO) { runCatching { api.queryGroups(q) }.getOrNull() }
            if (groups != null && _state.value.query.trim() == q) {
                _state.update { it.copy(suggestions = groups) }
            }
        }
    }

    fun search(group: String = _state.value.query) {
        val name = group.trim()
        if (name.isEmpty()) return
        suggestJob?.cancel()
        _state.update { it.copy(query = name, suggestions = emptyList()) }
        load(name)
    }

    /** Повтор последнего неудачного запроса. */
    fun retry() {
        lastRequestedGroup?.let { load(it) }
    }

    fun updateSettings(transform: (NotificationSettings) -> NotificationSettings) {
        repository.settings.update(transform)
        viewModelScope.launch { repository.applySettings() }
    }

    private fun load(group: String) {
        loadJob?.cancel()
        lastRequestedGroup = group
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, failed = false, notFoundGroup = null) }
            try {
                repository.refresh(group, background = false)
                _state.update { it.copy(loading = false, failed = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GroupNotFoundException) {
                _state.update { it.copy(loading = false, notFoundGroup = group) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    private companion object {
        const val MIN_SUGGEST_LENGTH = 2
        const val SUGGEST_DEBOUNCE_MS = 300L
    }
}
