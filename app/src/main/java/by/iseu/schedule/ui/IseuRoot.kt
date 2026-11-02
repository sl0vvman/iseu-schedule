package by.iseu.schedule.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import by.iseu.schedule.data.model.ScheduleSelection
import by.iseu.schedule.data.repo.ScheduleRepository
import by.iseu.schedule.ui.schedule.ScheduleScreen
import by.iseu.schedule.ui.select.SelectScreen
import by.iseu.schedule.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.map

private sealed interface StartState {
    data object Loading : StartState
    data class Ready(val selection: ScheduleSelection?) : StartState
}

private enum class RootScreen { MAIN, SETTINGS }

@Composable
fun IseuRoot(repository: ScheduleRepository) {
    var screen by remember { mutableStateOf(RootScreen.MAIN) }
    var editing by remember { mutableStateOf(false) }

    val startState by remember(repository) {
        repository.selectionFlow().map<ScheduleSelection?, StartState> { StartState.Ready(it) }
    }.collectAsStateWithLifecycle(initialValue = StartState.Loading)

    BackHandler(enabled = screen == RootScreen.SETTINGS) {
        screen = RootScreen.MAIN
    }
    BackHandler(enabled = editing) {
        editing = false
    }

    when (screen) {
        RootScreen.SETTINGS -> SettingsScreen(
            repository = repository,
            onChangeTarget = {
                screen = RootScreen.MAIN
                editing = true
            },
            onBack = { screen = RootScreen.MAIN },
        )

        RootScreen.MAIN -> when (val state = startState) {
            StartState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is StartState.Ready -> {
                val selection = state.selection
                if (selection == null || editing) {
                    SelectScreen(
                        repository = repository,
                        onSaved = { editing = false },
                        onCancel = if (selection != null) {
                            { editing = false }
                        } else {
                            null
                        },
                    )
                } else {
                    ScheduleScreen(
                        repository = repository,
                        onChangeTarget = { editing = true },
                        onSettings = { screen = RootScreen.SETTINGS },
                    )
                }
            }
        }
    }
}
