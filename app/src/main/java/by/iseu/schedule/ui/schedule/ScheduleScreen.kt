package by.iseu.schedule.ui.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import by.iseu.schedule.data.model.Lesson
import by.iseu.schedule.data.model.LessonType
import by.iseu.schedule.data.model.Mode
import by.iseu.schedule.data.model.Option
import by.iseu.schedule.data.parser.WeekSelector
import by.iseu.schedule.data.repo.ScheduleRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
private val timeOnly: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val fullTime: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM, HH:mm").withZone(ZoneId.systemDefault())
private val shortDayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    repository: ScheduleRepository,
    onChangeTarget: () -> Unit,
    onSettings: () -> Unit,
) {
    val viewModel: ScheduleViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ScheduleViewModel(repository) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.isTeacherPreview) {
        viewModel.closeTeacherPreview()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ISEU", fontWeight = FontWeight.SemiBold)
                        state.activeSelection?.targetLabel?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onChangeTarget) {
                        Text("Сменить")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            WeekHeader(
                weekMonday = state.weekMonday,
                weeks = state.weeks,
                onPrev = viewModel::previousWeek,
                onNext = viewModel::nextWeek,
                onCurrent = viewModel::currentWeek,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (state.isTeacherPreview) {
                TeacherPreviewBanner(
                    label = state.activeSelection?.targetLabel.orEmpty(),
                    onBack = viewModel::closeTeacherPreview,
                )
            }
            state.notice?.let { notice ->
                NoticeBanner(message = notice, onDismiss = viewModel::clearNotice)
            }
            WeekContent(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun WeekHeader(
    weekMonday: LocalDate,
    weeks: List<Option>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit,
) {
    val sorted = remember(weeks) {
        weeks.sortedBy { WeekSelector.parseOptionDate(it.label) ?: LocalDate.MAX }
    }
    val index = sorted.indexOfFirst {
        WeekSelector.parseOptionDate(it.label) == weekMonday
    }
    val canPrev = index != 0 || index == -1
    val canNext = index != sorted.lastIndex || index == -1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, enabled = canPrev) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Предыдущая неделя")
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${weekMonday.format(dayMonth)} – ${weekMonday.plusDays(6).format(dayMonth)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            TextButton(onClick = onCurrent) {
                Text("Текущая")
            }
        }
        IconButton(onClick = onNext, enabled = canNext) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Следующая неделя")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekContent(
    state: ScheduleUiState,
    viewModel: ScheduleViewModel,
) {
    val today = LocalDate.now()
    val content = state.content()
    val dayLessons = remember(state.lessons, state.selectedDay) {
        state.lessons.filter { it.date == state.selectedDay }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(state.selectedDay, state.weekMonday) {
        listState.scrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (content == ScheduleContent.LESSONS) {
            DayTabs(
                weekMonday = state.weekMonday,
                selectedDay = state.selectedDay,
                today = today,
                onSelect = viewModel::selectDay,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.weight(1f),
        ) {
            when (content) {
                ScheduleContent.LOADING -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                ScheduleContent.ERROR -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::retry,
                )

                ScheduleContent.EMPTY -> EmptyWeek(state.siteMessage)

                ScheduleContent.LESSONS -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (state.error != null) {
                        item(key = "error") {
                            ErrorBanner(state.error.orEmpty(), viewModel::retry)
                        }
                    }
                    if (state.isFromCache) {
                        item(key = "cache") {
                            CacheBanner(state.fetchedAt, viewModel::refresh)
                        }
                    }
                    if (dayLessons.isEmpty()) {
                        item(key = "empty-day") {
                            EmptyDay()
                        }
                    } else {
                        itemsIndexed(dayLessons, key = { index, _ -> index }) { index, lesson ->
                            LessonCard(
                                lesson = lesson,
                                mode = state.activeSelection?.mode ?: Mode.GROUP,
                                onStaffClick = viewModel::openTeacher,
                            )
                            if (index != dayLessons.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayTabs(
    weekMonday: LocalDate,
    selectedDay: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val days = remember(weekMonday) {
        (0..6).map { weekMonday.plusDays(it.toLong()) }
    }
    val selectedIndex = days.indexOf(selectedDay).takeIf { it >= 0 } ?: 0

    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color.Transparent,
        divider = {},
    ) {
        days.forEach { date ->
            Tab(
                selected = date == selectedDay,
                onClick = { onSelect(date) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = shortDayNames[date.dayOfWeek.value - 1],
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (date == today) FontWeight.Bold else FontWeight.Medium,
                        )
                        Text(
                            text = date.format(dayMonth),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun EmptyDay() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Нет занятий",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LessonCard(
    lesson: Lesson,
    mode: Mode,
    onStaffClick: (String) -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawLine(
                    color = outline,
                    start = Offset(stroke / 2f, 4.dp.toPx()),
                    end = Offset(stroke / 2f, size.height - 4.dp.toPx()),
                    strokeWidth = stroke,
                    pathEffect = if (lesson.subgroup != null) {
                        PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 6.dp.toPx()),
                        )
                    } else {
                        null
                    },
                )
            }
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
    ) {
        Column(modifier = Modifier.width(62.dp)) {
            Text(
                text = lesson.start.format(timeOnly),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = lesson.end.format(timeOnly),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = lesson.discipline,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                lesson.subgroup?.let { SubgroupBadge(it) }
            }

            TypeBadge(lesson.type)

            if (mode == Mode.GROUP) {
                lesson.staff?.let { staff ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClickLabel = "Открыть расписание преподавателя",
                            ) { onStaffClick(staff) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = staff,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textDecoration = TextDecoration.Underline,
                        )
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                lesson.contingent?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            lesson.topic?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            lesson.audience?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TypeBadge(type: LessonType) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = type.label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubgroupBadge(subgroup: Int) {
    val filled = subgroup % 2 == 1
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (filled) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = "$subgroup п/гр",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (filled) {
                MaterialTheme.colorScheme.background
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun TeacherPreviewBanner(label: String, onBack: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Расписание преподавателя",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onBack) {
                Text("К группе")
            }
        }
    }
}

@Composable
private fun NoticeBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onDismiss) {
                Text("Понятно")
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onRetry) {
                Text("Повторить")
            }
        }
    }
}

@Composable
private fun CacheBanner(fetchedAt: Instant?, onRefresh: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = buildString {
                    append("Показано из кэша")
                    fetchedAt?.let { append(" · обновлено ${fullTime.format(it)}") }
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRefresh) {
                Text("Обновить")
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onRetry) {
                Text("Повторить")
            }
        }
    }
}

@Composable
private fun EmptyWeek(siteMessage: String?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Пар нет", style = MaterialTheme.typography.titleMedium)
            siteMessage?.let {
                Text(
                    text = it,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
