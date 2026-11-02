package by.iseu.schedule.ui.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    BackHandler(
        enabled = state.tab == ScheduleTab.TEACHERS && state.teacherSelection != null,
    ) {
        viewModel.closeTeacher()
    }
    BackHandler(
        enabled = state.tab == ScheduleTab.TEACHERS && state.teacherSelection == null,
    ) {
        viewModel.selectTab(ScheduleTab.SCHEDULE)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (state.tab == ScheduleTab.TEACHERS && state.teacherSelection != null) {
                        IconButton(onClick = viewModel::closeTeacher) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "К списку преподавателей",
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text("ISEU", fontWeight = FontWeight.SemiBold)
                        val subtitle = when {
                            state.tab == ScheduleTab.TEACHERS &&
                                state.teacherSelection == null -> "Все преподаватели"

                            else -> state.activeSelection?.targetLabel
                        }
                        subtitle?.let { label ->
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
                    if (state.tab == ScheduleTab.SCHEDULE) {
                        TextButton(onClick = onChangeTarget) {
                            Text("Сменить")
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                },
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSurface,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NavigationBarItem(
                        selected = state.tab == ScheduleTab.SCHEDULE,
                        onClick = { viewModel.selectTab(ScheduleTab.SCHEDULE) },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        label = { Text("Расписание") },
                        colors = itemColors,
                    )
                    NavigationBarItem(
                        selected = state.tab == ScheduleTab.TEACHERS,
                        onClick = { viewModel.selectTab(ScheduleTab.TEACHERS) },
                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        label = { Text("Преподаватели") },
                        colors = itemColors,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.tab == ScheduleTab.TEACHERS && state.teacherSelection == null) {
                TeacherListPane(state = state, viewModel = viewModel)
            } else {
                WeekHeader(
                    weekMonday = state.weekMonday,
                    weeks = state.weeks,
                    onPrev = viewModel::previousWeek,
                    onNext = viewModel::nextWeek,
                    onCurrent = viewModel::currentWeek,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                WeekContent(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun TeacherListPane(
    state: ScheduleUiState,
    viewModel: ScheduleViewModel,
) {
    val teachers = remember(state.teachers, state.teacherQuery) {
        val query = state.teacherQuery.trim()
        if (query.isEmpty()) {
            state.teachers
        } else {
            state.teachers.filter { it.label.contains(query, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.teacherQuery,
            onValueChange = viewModel::setTeacherQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Поиск по ФИО или должности") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (state.teacherQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { viewModel.setTeacherQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Очистить поиск")
                    }
                }
            } else {
                null
            },
            singleLine = true,
        )

        when {
            state.teachersLoading && state.teachers.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.teachersError != null && state.teachers.isEmpty() -> ErrorState(
                message = state.teachersError,
                onRetry = viewModel::reloadTeachers,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                if (state.teachersError != null) {
                    item(key = "teachers-error") {
                        Box(modifier = Modifier.padding(16.dp)) {
                            ErrorBanner(
                                message = state.teachersError,
                                onRetry = viewModel::reloadTeachers,
                            )
                        }
                    }
                }
                if (teachers.isEmpty() && !state.teachersLoading) {
                    item(key = "teachers-empty") {
                        EmptyDay(
                            text = if (state.teacherQuery.isBlank()) {
                                "Список преподавателей пуст"
                            } else {
                                "Ничего не найдено"
                            },
                        )
                    }
                }
                items(teachers, key = { it.value }) { teacher ->
                    TeacherRow(teacher = teacher, onClick = { viewModel.selectTeacher(teacher) })
                }
            }
        }
    }
}

@Composable
private fun TeacherRow(teacher: Option, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = teacher.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
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
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущая неделя")
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
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующая неделя")
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
    val days = remember(state.weekMonday) {
        (0..6).map { state.weekMonday.plusDays(it.toLong()) }
    }
    val selectedIndex = days.indexOf(state.selectedDay).takeIf { it >= 0 } ?: 0
    val lessonsByDate = remember(state.lessons) { state.lessons.groupBy { it.date } }

    Column(modifier = Modifier.fillMaxSize()) {
        if (content == ScheduleContent.LESSONS) {
            DayTabs(
                days = days,
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

                ScheduleContent.LESSONS -> DayPager(
                    state = state,
                    days = days,
                    selectedIndex = selectedIndex,
                    lessonsByDate = lessonsByDate,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun DayPager(
    state: ScheduleUiState,
    days: List<LocalDate>,
    selectedIndex: Int,
    lessonsByDate: Map<LocalDate, List<Lesson>>,
    viewModel: ScheduleViewModel,
) {
    key(state.weekMonday) {
        val pagerState = rememberPagerState(initialPage = selectedIndex) { days.size }

        LaunchedEffect(selectedIndex) {
            if (pagerState.currentPage != selectedIndex) {
                pagerState.animateScrollToPage(selectedIndex)
            }
        }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                days.getOrNull(page)?.let(viewModel::selectDay)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            DayPage(
                lessons = lessonsByDate[days[page]].orEmpty(),
                mode = state.activeSelection?.mode ?: Mode.GROUP,
                error = state.error,
                isFromCache = state.isFromCache,
                fetchedAt = state.fetchedAt,
                onRetry = viewModel::retry,
                onRefresh = viewModel::refresh,
            )
        }
    }
}

@Composable
private fun DayPage(
    lessons: List<Lesson>,
    mode: Mode,
    error: String?,
    isFromCache: Boolean,
    fetchedAt: Instant?,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (error != null) {
            item(key = "error") {
                ErrorBanner(error, onRetry)
            }
        }
        if (isFromCache) {
            item(key = "cache") {
                CacheBanner(fetchedAt, onRefresh)
            }
        }
        if (lessons.isEmpty()) {
            item(key = "empty-day") {
                EmptyDay()
            }
        } else {
            itemsIndexed(lessons, key = { index, _ -> index }) { index, lesson ->
                LessonCard(lesson = lesson, mode = mode)
                if (index != lessons.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayTabs(
    days: List<LocalDate>,
    selectedDay: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
    ) {
        days.forEach { date ->
            val selected = date == selectedDay
            Column(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(date) },
                    )
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = shortDayNames[date.dayOfWeek.value - 1],
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected || date == today) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Medium
                    },
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = date.format(dayMonth),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(2.dp)
                        .background(
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(1.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun EmptyDay(text: String = "Нет занятий") {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LessonCard(
    lesson: Lesson,
    mode: Mode,
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
                lesson.staff?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
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
