package me.heyjohn.kubsauschedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import me.heyjohn.kubsau.Day
import me.heyjohn.kubsau.Group
import me.heyjohn.kubsau.Lesson
import me.heyjohn.kubsau.LessonType
import me.heyjohn.kubsau.Schedule
import me.heyjohn.kubsau.Week
import me.heyjohn.kubsauschedule.TestTags
import me.heyjohn.kubsauschedule.UiState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val RU = Locale("ru")
private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", RU)
private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", RU)
private val SHORT_DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", RU)
private val STAMP_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", RU)
private val ErrorRed = Color(0xFFD32F2F)

@Composable
fun ScheduleScreen(
    state: UiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSuggestionClick: (Group) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var fieldFocused by remember { mutableStateOf(false) }
    var searchBlockHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    val showSuggestions = fieldFocused && state.query.isNotBlank() && state.suggestions.isNotEmpty()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { searchBlockHeightPx = it.height }
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(TestTags.GROUP_FIELD)
                                .onFocusChanged { fieldFocused = it.isFocused },
                            singleLine = true,
                            label = { Text("Группа") },
                            placeholder = { Text("Например, БИ2601") },
                            trailingIcon = {
                                IconButton(onClick = {
                                    focusManager.clearFocus()
                                    onSearch()
                                }) {
                                    Icon(Icons.Filled.Search, contentDescription = "Найти")
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Search,
                            ),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                onSearch()
                            }),
                        )
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .padding(start = 4.dp, top = 6.dp)
                                .testTag(TestTags.SETTINGS_BUTTON),
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = "Настройки уведомлений")
                        }
                    }
                    StatusLine(state, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                    if (state.loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                val schedule = state.schedule
                when {
                    schedule != null -> ScheduleContent(
                        schedule = schedule,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                enabled = showSuggestions,
                                onClick = { focusManager.clearFocus() },
                            ),
                    )
                    state.initializing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    else -> EmptyPlaceholder(modifier = Modifier.fillMaxSize())
                }
            }

            if (showSuggestions) {
                SuggestionsDropdown(
                    suggestions = state.suggestions,
                    onClick = { group ->
                        focusManager.clearFocus()
                        onSuggestionClick(group)
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .zIndex(1f)
                        .padding(horizontal = 16.dp)
                        .padding(top = with(density) { searchBlockHeightPx.toDp() } - 8.dp)
                        .fillMaxWidth(),
                )
            }

            if (state.failed && !state.loading) {
                RetryDot(
                    onClick = onRetry,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .imePadding()
                        .padding(16.dp)
                        .testTag(TestTags.RETRY_DOT),
                )
            }
        }
    }
}

@Composable
private fun StatusLine(state: UiState, modifier: Modifier = Modifier) {
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    val text: String
    val color: Color
    when {
        state.notFoundGroup != null -> {
            text = "Группа «${state.notFoundGroup}» не найдена"
            color = MaterialTheme.colorScheme.error
        }
        state.loading -> {
            text = if (state.schedule != null && state.fromCache) {
                "Показан сохранённый результат, обновление…"
            } else {
                "Загрузка…"
            }
            color = variant
        }
        state.failed -> {
            text = if (state.schedule != null) {
                "Не удалось обновить. Данные от ${formatStamp(state.fetchedAt)}"
            } else {
                "Не удалось загрузить расписание"
            }
            color = MaterialTheme.colorScheme.error
        }
        state.schedule != null -> {
            val site = state.schedule.updatedAt()
            text = buildString {
                append("Обновлено ").append(formatStamp(state.fetchedAt))
                if (site != null) append(" · на сайте от ").append(site.format(STAMP_FMT))
            }
            color = variant
        }
        else -> {
            text = "Введите номер группы и нажмите поиск"
            color = variant
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun SuggestionsDropdown(
    suggestions: List<Group>,
    onClick: (Group) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
            items(suggestions, key = { it.name() }) { group ->
                Text(
                    text = group.name(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClick(group) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EmptyPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "Здесь появится расписание группы.\nВведите её номер в поле выше.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScheduleContent(schedule: Schedule, modifier: Modifier = Modifier) {
    val weeks = schedule.weeks()
    val today = remember { LocalDate.now() }
    // Неделя и день, к которым проматываем при запуске: сегодня или ближайший следующий учебный день.
    val target = remember(schedule.group()) { findTodayPosition(weeks, today) }
    var selected by rememberSaveable(schedule.group()) { mutableIntStateOf(target.first) }
    if (selected >= weeks.size) selected = 0
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scrolledToToday by rememberSaveable(schedule.group()) { mutableStateOf(false) }
    LaunchedEffect(schedule.group()) {
        if (!scrolledToToday) {
            listState.scrollToItem(target.second)
            scrolledToToday = true
        }
    }

    Column(modifier = modifier) {
        if (weeks.size > 1) {
            TabRow(selectedTabIndex = selected) {
                weeks.forEachIndexed { index, week ->
                    Tab(
                        selected = selected == index,
                        onClick = {
                            if (selected != index) {
                                selected = index
                                scope.launch { listState.scrollToItem(0) }
                            }
                        },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (index == 0) "Эта неделя" else "Следующая")
                                Text(
                                    text = weekRange(week),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }
        val week = weeks[selected]
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(week.days(), key = { it.date().toString() }) { day ->
                DayCard(day = day, isToday = day.date() == today)
            }
        }
    }
}

@Composable
private fun DayCard(day: Day, isToday: Boolean) {
    val colors = if (isToday) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = colors) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dayName(day.date()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isToday) {
                    Text(
                        text = "сегодня",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Text(
                    text = day.date().format(DAY_FMT),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (day.lessons().isEmpty()) {
                Text(
                    text = "Нет занятий",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                day.lessons().forEachIndexed { index, lesson ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    LessonRow(lesson)
                }
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson) {
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.width(52.dp)) {
            Text(
                text = formatTime(lesson.start()),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatTime(lesson.end()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = lesson.subject(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                if (lesson.type() == LessonType.LECTURE) {
                    Text(
                        text = "Лекция",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (lesson.rooms().isNotEmpty()) {
                    Text(
                        text = lesson.rooms().joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (lesson.teachers().isNotEmpty()) {
                Text(
                    text = lesson.teachers().joinToString(", ") { it.toString() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Маленький красный кружок с белой стрелкой обновления: ретрай по нажатию, авторетраев нет. */
@Composable
private fun RetryDot(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = ErrorRed,
        contentColor = Color.White,
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Повторить обновление",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Индекс недели и дня для сегодняшней даты; если она позади, берём ближайший следующий день, иначе начало. */
private fun findTodayPosition(weeks: List<Week>, today: LocalDate): Pair<Int, Int> {
    weeks.forEachIndexed { weekIndex, week ->
        val dayIndex = week.days().indexOfFirst { !it.date().isBefore(today) }
        if (dayIndex >= 0) return weekIndex to dayIndex
    }
    return 0 to 0
}

private fun weekRange(week: Week): String {
    val days = week.days()
    if (days.isEmpty()) return ""
    return "${days.first().date().format(SHORT_DAY_FMT)} – ${days.last().date().format(SHORT_DAY_FMT)}"
}

private fun dayName(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, RU)
        .replaceFirstChar { it.titlecase(RU) }

private fun formatTime(time: LocalTime): String = time.format(TIME_FMT)

private fun formatStamp(epochMillis: Long?): String {
    if (epochMillis == null || epochMillis <= 0L) return "—"
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
    return if (dateTime.toLocalDate() == LocalDate.now()) {
        dateTime.format(DateTimeFormatter.ofPattern("HH:mm", RU))
    } else {
        dateTime.format(STAMP_FMT)
    }
}
