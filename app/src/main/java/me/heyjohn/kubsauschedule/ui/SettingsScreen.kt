package me.heyjohn.kubsauschedule.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import me.heyjohn.kubsauschedule.data.NotificationSettings
import me.heyjohn.kubsauschedule.notifications.LessonAlarmScheduler
import me.heyjohn.kubsauschedule.notifications.Notifications

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: NotificationSettings,
    onUpdate: ((NotificationSettings) -> NotificationSettings) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    var notificationsAllowed by remember { mutableStateOf(Notifications.canPost(context)) }
    var exactAlarmsAllowed by remember { mutableStateOf(LessonAlarmScheduler.canScheduleExact(context)) }
    // Пользователь мог поменять разрешения в системных настройках: перечитываем при возврате.
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = Notifications.canPost(context)
        val exactNow = LessonAlarmScheduler.canScheduleExact(context)
        if (exactNow != exactAlarmsAllowed) {
            exactAlarmsAllowed = exactNow
            // Разрешение на точные будильники изменилось: перепланируем напоминания.
            onUpdate { it }
        }
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = Notifications.canPost(context)
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAllowed) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }

    fun requestExactAlarms() {
        // На Android 12 разрешение выдано по умолчанию, на Android 13+ его нужно запросить явно.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            )
        }
    }

    val anyEnabled = settings.lessonsEnabled || settings.changesEnabled

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Уведомления") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (anyEnabled && !notificationsAllowed) {
                WarningCard(
                    text = "Уведомления запрещены системой. Без разрешения напоминания не будут показаны.",
                    actionLabel = "Разрешить",
                    onAction = ::requestNotifications,
                )
            }

            SettingsCard(title = "Напоминания о парах") {
                SwitchRow(
                    title = "Напоминать о парах",
                    subtitle = "Пуш с предметом, аудиторией и преподавателем перед началом пары",
                    checked = settings.lessonsEnabled,
                    onCheckedChange = { enabled ->
                        onUpdate { it.copy(lessonsEnabled = enabled) }
                        if (enabled && !notificationsAllowed) requestNotifications()
                    },
                )
                Text(
                    text = "За сколько минут до начала",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    NotificationSettings.MINUTES_OPTIONS.forEach { minutes ->
                        FilterChip(
                            selected = settings.minutesBefore == minutes,
                            enabled = settings.lessonsEnabled,
                            onClick = { onUpdate { it.copy(minutesBefore = minutes) } },
                            label = { Text("$minutes") },
                        )
                    }
                }
                if (settings.lessonsEnabled && !exactAlarmsAllowed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    WarningCard(
                        text = "Точные будильники отключены: напоминания могут приходить с задержкой до нескольких минут.",
                        actionLabel = "Разрешить",
                        onAction = ::requestExactAlarms,
                    )
                }
            }

            SettingsCard(title = "Изменения расписания") {
                SwitchRow(
                    title = "Сообщать об изменениях",
                    subtitle = "Приложение раз в час проверяет расписание в фоне. Если пары на сегодня " +
                        "изменились, придёт уведомление.",
                    checked = settings.changesEnabled,
                    onCheckedChange = { enabled ->
                        onUpdate { it.copy(changesEnabled = enabled) }
                        if (enabled && !notificationsAllowed) requestNotifications()
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun WarningCard(text: String, actionLabel: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                Text(actionLabel)
            }
        }
    }
}
