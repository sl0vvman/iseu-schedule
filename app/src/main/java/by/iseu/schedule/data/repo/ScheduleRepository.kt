package by.iseu.schedule.data.repo

import by.iseu.schedule.data.db.CatalogDao
import by.iseu.schedule.data.db.CatalogEntry
import by.iseu.schedule.data.db.WeekCacheDao
import by.iseu.schedule.data.db.toEntity
import by.iseu.schedule.data.db.toWeekSchedule
import by.iseu.schedule.data.model.IseuException
import by.iseu.schedule.data.model.Mode
import by.iseu.schedule.data.model.Option
import by.iseu.schedule.data.model.SchedulePage
import by.iseu.schedule.data.model.ScheduleSelection
import by.iseu.schedule.data.model.ThemeMode
import by.iseu.schedule.data.model.WeekSchedule
import by.iseu.schedule.data.network.AspNetSession
import by.iseu.schedule.data.network.MemoryCookieJar
import by.iseu.schedule.data.network.SiteHttpException
import by.iseu.schedule.data.parser.ScheduleParser
import by.iseu.schedule.data.parser.WeekSelector
import by.iseu.schedule.data.prefs.SelectionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

interface ScheduleRepository {
    fun selectionFlow(): Flow<ScheduleSelection?>
    fun themeFlow(): Flow<ThemeMode>
    suspend fun saveSelection(selection: ScheduleSelection)
    suspend fun setTheme(mode: ThemeMode)
    suspend fun loadFaculties(force: Boolean = false): List<Option>
    suspend fun loadForms(faculty: Option, force: Boolean = false): List<Option>
    suspend fun loadCourses(faculty: Option, form: Option, force: Boolean = false): List<Option>
    suspend fun loadGroups(faculty: Option, form: Option, course: Option, force: Boolean = false): List<Option>
    suspend fun loadTeachers(force: Boolean = false): List<Option>
    fun cachedWeek(selection: ScheduleSelection, monday: LocalDate): Flow<WeekSchedule?>
    suspend fun cachedWeeks(selection: ScheduleSelection): List<Option>
    suspend fun loadWeek(
        selection: ScheduleSelection,
        monday: LocalDate,
        force: Boolean = false,
    ): SchedulePage
    suspend fun clearCache()
}

class DefaultScheduleRepository(
    private val client: OkHttpClient,
    private val cookieJar: MemoryCookieJar,
    private val weekDao: WeekCacheDao,
    private val catalogDao: CatalogDao,
    private val selectionStore: SelectionStore,
    private val now: () -> Instant = Instant::now,
) : ScheduleRepository {

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun selectionFlow(): Flow<ScheduleSelection?> = selectionStore.selectionFlow()

    override fun themeFlow(): Flow<ThemeMode> = selectionStore.themeFlow()

    override suspend fun saveSelection(selection: ScheduleSelection) {
        selectionStore.saveSelection(selection)
    }

    override suspend fun setTheme(mode: ThemeMode) {
        selectionStore.setTheme(mode)
    }

    override suspend fun loadFaculties(force: Boolean): List<Option> =
        cachedOptions("faculties", force) {
            AspNetSession(client, GROUP_URL, cookieJar).options("ddlFac").usable()
        }

    override suspend fun loadForms(faculty: Option, force: Boolean): List<Option> =
        cachedOptions("forms:${faculty.value}", force) {
            val session = AspNetSession(client, GROUP_URL, cookieJar)
            session.postback("ddlFac", mapOf("ddlFac" to faculty.value))
            session.options("ddlDep").usable()
        }

    override suspend fun loadCourses(
        faculty: Option,
        form: Option,
        force: Boolean,
    ): List<Option> = cachedOptions("courses:${faculty.value}:${form.value}", force) {
        val session = AspNetSession(client, GROUP_URL, cookieJar)
        session.postback("ddlFac", mapOf("ddlFac" to faculty.value))
        session.postback("ddlDep", mapOf("ddlDep" to form.value))
        session.options("ddlCourse").usable()
    }

    override suspend fun loadGroups(
        faculty: Option,
        form: Option,
        course: Option,
        force: Boolean,
    ): List<Option> = cachedOptions("groups:${faculty.value}:${form.value}:${course.value}", force) {
        val session = AspNetSession(client, GROUP_URL, cookieJar)
        session.postback("ddlFac", mapOf("ddlFac" to faculty.value))
        session.postback("ddlDep", mapOf("ddlDep" to form.value))
        session.postback("ddlCourse", mapOf("ddlCourse" to course.value))
        session.options("ddlGroup").usable()
    }

    override suspend fun loadTeachers(force: Boolean): List<Option> =
        cachedOptions("teachers", force) {
            AspNetSession(client, TEACHER_URL, cookieJar).options("DropDownList1").usable()
        }

    override fun cachedWeek(
        selection: ScheduleSelection,
        monday: LocalDate,
    ): Flow<WeekSchedule?> = weekDao.observe(weekKey(selection, monday))
        .map { entity -> entity?.toWeekSchedule() }

    override suspend fun cachedWeeks(selection: ScheduleSelection): List<Option> = readWeeks(selection)

    override suspend fun loadWeek(
        selection: ScheduleSelection,
        monday: LocalDate,
        force: Boolean,
    ): SchedulePage {
        val key = weekKey(selection, monday)
        val cached = weekDao.get(key)
        if (!force && cached != null &&
            !CachePolicy.shouldRefresh(Instant.ofEpochMilli(cached.fetchedAtEpochMs), now())
        ) {
            return SchedulePage(readWeeks(selection), cached.toWeekSchedule())
        }

        val page = network { fetchPage(selection, monday) }
        weekDao.put(page.schedule.toEntity(key, json))
        catalogDao.put(
            CatalogEntry(
                key = weeksKey(selection),
                json = json.encodeToString(page.weeks),
                fetchedAtEpochMs = now().toEpochMilli(),
            ),
        )
        return page
    }

    override suspend fun clearCache() {
        weekDao.clear()
        catalogDao.clear()
    }

    private suspend fun cachedOptions(
        key: String,
        force: Boolean,
        fetch: suspend () -> List<Option>,
    ): List<Option> {
        val cached = catalogDao.get(key)
        val cachedOptions = cached?.let {
            runCatching { json.decodeFromString<List<Option>>(it.json) }.getOrNull()
        }
        if (!force && !cachedOptions.isNullOrEmpty() &&
            !CachePolicy.shouldRefresh(
                cached?.let { Instant.ofEpochMilli(it.fetchedAtEpochMs) },
                now(),
                CachePolicy.catalogTtl,
            )
        ) {
            return cachedOptions
        }

        val options = network { fetch() }
        if (options.isEmpty()) throw IseuException.CatalogStale()
        catalogDao.put(
            CatalogEntry(
                key = key,
                json = json.encodeToString(options),
                fetchedAtEpochMs = now().toEpochMilli(),
            ),
        )
        return options
    }

    private suspend fun fetchPage(
        selection: ScheduleSelection,
        monday: LocalDate,
    ): SchedulePage {
        return when (selection.mode) {
            Mode.GROUP -> fetchGroupPage(selection, monday)
            Mode.TEACHER -> fetchTeacherPage(selection, monday)
        }
    }

    private fun fetchGroupPage(
        selection: ScheduleSelection,
        monday: LocalDate,
    ): SchedulePage {
        val faculty = selection.faculty ?: throw IseuException.CatalogStale()
        val form = selection.form ?: throw IseuException.CatalogStale()
        val course = selection.course ?: throw IseuException.CatalogStale()
        val group = selection.group ?: throw IseuException.CatalogStale()

        val session = AspNetSession(client, GROUP_URL, cookieJar)
        session.postback("ddlFac", mapOf("ddlFac" to faculty.value))
        session.postback("ddlDep", mapOf("ddlDep" to form.value))
        session.postback("ddlCourse", mapOf("ddlCourse" to course.value))

        val groups = session.options("ddlGroup").usable()
        if (groups.none { it.value == group.value }) throw IseuException.CatalogStale()
        session.postback("ddlGroup", mapOf("ddlGroup" to group.value))

        val weeks = session.options("ddlWeek").sortedWeeks()
        if (weeks.isEmpty()) {
            return emptyPage(selection, monday, session.message())
        }
        val week = WeekSelector.selectWeek(weeks, monday) ?: weeks.first()
        session.postback("ddlWeek", mapOf("ddlWeek" to week.value))
        val html = session.submit(mapOf("ShowTT" to "Показать"))
        val lessons = ScheduleParser.parseGroupTable(html)
        return buildPage(selection, week, weeks, lessons, session.message())
    }

    private fun fetchTeacherPage(
        selection: ScheduleSelection,
        monday: LocalDate,
    ): SchedulePage {
        val teacher = selection.teacher ?: throw IseuException.CatalogStale()

        val session = AspNetSession(client, TEACHER_URL, cookieJar)
        session.postback("DropDownList1", mapOf("DropDownList1" to teacher.value))

        val weeks = session.options("ddlWeek").sortedWeeks()
        if (weeks.isEmpty()) {
            return emptyPage(selection, monday, session.message())
        }
        val week = WeekSelector.selectWeek(weeks, monday) ?: weeks.first()
        session.postback("ddlWeek", mapOf("ddlWeek" to week.value))
        val html = session.submit(
            mapOf(
                "Show" to "Показать",
                "cbShowDraftForTeacher" to "on",
            ),
        )
        val lessons = ScheduleParser.parseTeacherTable(html)
        return buildPage(selection, week, weeks, lessons, session.message())
    }

    private fun buildPage(
        selection: ScheduleSelection,
        week: Option,
        weeks: List<Option>,
        lessons: List<by.iseu.schedule.data.model.Lesson>,
        message: String?,
    ): SchedulePage {
        val weekMonday = WeekSelector.parseOptionDate(week.label) ?: today()
        return SchedulePage(
            weeks = weeks,
            schedule = WeekSchedule(
                weekMonday = weekMonday,
                mode = selection.mode,
                targetId = selection.targetId,
                targetLabel = selection.targetLabel,
                lessons = lessons,
                siteMessage = message,
                fetchedAt = now(),
            ),
        )
    }

    private fun emptyPage(
        selection: ScheduleSelection,
        monday: LocalDate,
        message: String?,
    ): SchedulePage = SchedulePage(
        weeks = emptyList(),
        schedule = WeekSchedule(
            weekMonday = monday,
            mode = selection.mode,
            targetId = selection.targetId,
            targetLabel = selection.targetLabel,
            lessons = emptyList(),
            siteMessage = message,
            fetchedAt = now(),
        ),
    )

    private suspend fun readWeeks(selection: ScheduleSelection): List<Option> {
        val entry = catalogDao.get(weeksKey(selection)) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Option>>(entry.json) }
            .getOrDefault(emptyList())
    }

    private suspend fun <T> network(block: suspend () -> T): T = onIo {
        mutex.withLock {
            var last: Throwable? = null
            repeat(2) {
                try {
                    return@withLock block()
                } catch (error: SiteHttpException) {
                    if (error.status !in 500..599) throw IseuException.Site(error.status)
                    last = error
                } catch (error: IOException) {
                    last = error
                } catch (error: IseuException) {
                    throw error
                } catch (error: Exception) {
                    throw IseuException.Parse(error)
                }
            }
            when (val error = last) {
                is SiteHttpException -> throw IseuException.Site(error.status)
                is IOException -> throw IseuException.Network(error)
                else -> throw IseuException.Network(IOException("unknown failure", error))
            }
        }
    }

    private fun weekKey(selection: ScheduleSelection, monday: LocalDate): String =
        "${selection.cacheKey}:$monday"

    private fun weeksKey(selection: ScheduleSelection): String =
        "weeks:${selection.cacheKey}"

    private fun List<Option>.usable(): List<Option> =
        filter { it.value.isNotBlank() && it.value != "0" && it.label.isNotBlank() }

    private fun List<Option>.sortedWeeks(): List<Option> =
        sortedBy { WeekSelector.parseOptionDate(it.label) ?: LocalDate.MAX }

    private fun today(): LocalDate = LocalDate.now()

    private companion object {
        const val GROUP_URL = "http://rsp.iseu.by/Raspisanie/TimeTable/umu.aspx"
        const val TEACHER_URL = "http://rsp.iseu.by/Raspisanie/TimeTable/umuteachers.aspx"
    }
}

internal suspend fun <T> onIo(block: suspend () -> T): T = withContext(Dispatchers.IO) {
    block()
}
