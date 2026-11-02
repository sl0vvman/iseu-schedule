package by.iseu.schedule.ui.select

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import by.iseu.schedule.data.model.Mode
import by.iseu.schedule.data.model.Option
import by.iseu.schedule.data.repo.ScheduleRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectScreen(
    repository: ScheduleRepository,
    onSaved: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    val viewModel: SelectViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SelectViewModel(repository, onSaved) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val filteredTeachers = remember(state.teachers, state.teacherQuery) {
        if (state.teacherQuery.isBlank()) {
            state.teachers
        } else {
            state.teachers.filter { it.label.contains(state.teacherQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ISEU", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (onCancel != null) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Расписание", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Выберите группу или преподавателя",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                SegmentedButton(
                    selected = state.mode == Mode.GROUP,
                    onClick = { viewModel.setMode(Mode.GROUP) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("Группа") },
                )
                SegmentedButton(
                    selected = state.mode == Mode.TEACHER,
                    onClick = { viewModel.setMode(Mode.TEACHER) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("Преподаватель") },
                )
            }

            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.error?.let { message ->
                ErrorBanner(
                    message = message,
                    onRetry = {
                        viewModel.clearError()
                        viewModel.loadFaculties()
                        viewModel.loadTeachers()
                    },
                )
            }

            when (state.mode) {
                Mode.GROUP -> GroupSelector(
                    state = state,
                    onFaculty = viewModel::selectFaculty,
                    onForm = viewModel::selectForm,
                    onCourse = viewModel::selectCourse,
                    onGroup = viewModel::selectGroup,
                    onSave = viewModel::save,
                )

                Mode.TEACHER -> TeacherSelector(
                    state = state,
                    teachers = filteredTeachers,
                    onQuery = viewModel::setTeacherQuery,
                    onSelect = viewModel::selectTeacher,
                )
            }
        }
    }
}

@Composable
private fun GroupSelector(
    state: SelectUiState,
    onFaculty: (Option) -> Unit,
    onForm: (Option) -> Unit,
    onCourse: (Option) -> Unit,
    onGroup: (Option) -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OptionDropdown(
            label = "Факультет",
            value = state.faculty,
            options = state.faculties,
            enabled = state.faculties.isNotEmpty(),
            onSelect = onFaculty,
        )
        OptionDropdown(
            label = "Форма обучения",
            value = state.form,
            options = state.forms,
            enabled = state.faculty != null && state.forms.isNotEmpty(),
            onSelect = onForm,
        )
        OptionDropdown(
            label = "Курс",
            value = state.course,
            options = state.courses,
            enabled = state.form != null && state.courses.isNotEmpty(),
            onSelect = onCourse,
        )
        OptionDropdown(
            label = "Группа",
            value = state.group,
            options = state.groups,
            enabled = state.course != null && state.groups.isNotEmpty(),
            onSelect = onGroup,
        )
        Button(
            onClick = onSave,
            enabled = state.group != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            Text("Показать расписание")
        }
    }
}

@Composable
private fun TeacherSelector(
    state: SelectUiState,
    teachers: List<Option>,
    onQuery: (String) -> Unit,
    onSelect: (Option) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.teacherQuery,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск по ФИО или должности") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )

        if (state.loading && teachers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(teachers, key = { it.value }) { teacher ->
                val selected = state.teacher?.value == teacher.value
                Surface(
                    onClick = { onSelect(teacher) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Text(
                        text = teacher.label,
                        modifier = Modifier.padding(16.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            if (teachers.isEmpty() && !state.loading) {
                item {
                    Text(
                        "Ничего не найдено",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetry) {
                Text("Повторить")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionDropdown(
    label: String,
    value: Option?,
    options: List<Option>,
    enabled: Boolean,
    onSelect: (Option) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value?.label.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
