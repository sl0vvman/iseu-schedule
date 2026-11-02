package by.iseu.schedule.ui.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import by.iseu.schedule.data.repo.ScheduleRepository

@Composable
fun ScheduleScreen(
    repository: ScheduleRepository,
    onChangeTarget: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Schedule")
    }
}
