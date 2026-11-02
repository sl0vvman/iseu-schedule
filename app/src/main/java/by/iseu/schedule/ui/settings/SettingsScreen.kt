package by.iseu.schedule.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import by.iseu.schedule.data.repo.ScheduleRepository

@Composable
fun SettingsScreen(
    repository: ScheduleRepository,
    onChangeTarget: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Settings")
    }
}
