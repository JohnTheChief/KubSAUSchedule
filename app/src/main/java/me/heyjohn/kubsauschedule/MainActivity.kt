package me.heyjohn.kubsauschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import me.heyjohn.kubsauschedule.ui.ScheduleScreen
import me.heyjohn.kubsauschedule.ui.SettingsScreen
import me.heyjohn.kubsauschedule.ui.theme.KubSAUScheduleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KubSAUScheduleTheme {
                val viewModel: ScheduleViewModel = viewModel()
                val state by viewModel.state.collectAsState()
                val settings by viewModel.settings.collectAsState()
                var showSettings by rememberSaveable { mutableStateOf(false) }

                if (showSettings) {
                    SettingsScreen(
                        settings = settings,
                        onUpdate = viewModel::updateSettings,
                        onBack = { showSettings = false },
                    )
                } else {
                    ScheduleScreen(
                        state = state,
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = { viewModel.search() },
                        onSuggestionClick = { group -> viewModel.search(group.name()) },
                        onRetry = viewModel::retry,
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (AppIconSwitcher.isUpdated(this)) AppIconSwitcher.setUpdated(this, false)
    }
}
