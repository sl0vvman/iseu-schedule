package by.iseu.schedule.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import by.iseu.schedule.data.model.Mode
import by.iseu.schedule.data.model.Option
import by.iseu.schedule.data.model.ScheduleSelection
import by.iseu.schedule.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "iseu")

class SelectionStore(private val context: Context) {

    fun selectionFlow(): Flow<ScheduleSelection?> = context.dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> readSelection(prefs) }

    fun themeFlow(): Flow<ThemeMode> = context.dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            prefs[Keys.theme]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM
        }

    suspend fun saveSelection(selection: ScheduleSelection) {
        context.dataStore.edit { prefs ->
            Keys.selectionKeys.forEach { prefs.remove(it) }
            prefs[Keys.mode] = selection.mode.name
            selection.faculty?.let {
                prefs[Keys.facultyValue] = it.value
                prefs[Keys.facultyLabel] = it.label
            }
            selection.form?.let {
                prefs[Keys.formValue] = it.value
                prefs[Keys.formLabel] = it.label
            }
            selection.course?.let {
                prefs[Keys.courseValue] = it.value
                prefs[Keys.courseLabel] = it.label
            }
            selection.group?.let {
                prefs[Keys.groupValue] = it.value
                prefs[Keys.groupLabel] = it.label
            }
            selection.teacher?.let {
                prefs[Keys.teacherValue] = it.value
                prefs[Keys.teacherLabel] = it.label
            }
        }
    }

    suspend fun clearSelection() {
        context.dataStore.edit { prefs ->
            Keys.selectionKeys.forEach { prefs.remove(it) }
        }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[Keys.theme] = mode.name }
    }

    private fun readSelection(prefs: Preferences): ScheduleSelection? {
        val mode = prefs[Keys.mode]
            ?.let { runCatching { Mode.valueOf(it) }.getOrNull() }
            ?: return null

        return when (mode) {
            Mode.GROUP -> {
                val faculty = option(prefs, Keys.facultyValue, Keys.facultyLabel) ?: return null
                val form = option(prefs, Keys.formValue, Keys.formLabel) ?: return null
                val course = option(prefs, Keys.courseValue, Keys.courseLabel) ?: return null
                val group = option(prefs, Keys.groupValue, Keys.groupLabel) ?: return null
                ScheduleSelection(
                    mode = Mode.GROUP,
                    faculty = faculty,
                    form = form,
                    course = course,
                    group = group,
                )
            }

            Mode.TEACHER -> {
                val teacher = option(prefs, Keys.teacherValue, Keys.teacherLabel) ?: return null
                ScheduleSelection(mode = Mode.TEACHER, teacher = teacher)
            }
        }
    }

    private fun option(
        prefs: Preferences,
        valueKey: Preferences.Key<String>,
        labelKey: Preferences.Key<String>,
    ): Option? {
        val value = prefs[valueKey] ?: return null
        return Option(value, prefs[labelKey] ?: value)
    }

    private object Keys {
        val mode = stringPreferencesKey("mode")
        val facultyValue = stringPreferencesKey("faculty_value")
        val facultyLabel = stringPreferencesKey("faculty_label")
        val formValue = stringPreferencesKey("form_value")
        val formLabel = stringPreferencesKey("form_label")
        val courseValue = stringPreferencesKey("course_value")
        val courseLabel = stringPreferencesKey("course_label")
        val groupValue = stringPreferencesKey("group_value")
        val groupLabel = stringPreferencesKey("group_label")
        val teacherValue = stringPreferencesKey("teacher_value")
        val teacherLabel = stringPreferencesKey("teacher_label")
        val theme = stringPreferencesKey("theme")

        val selectionKeys = listOf(
            mode,
            facultyValue,
            facultyLabel,
            formValue,
            formLabel,
            courseValue,
            courseLabel,
            groupValue,
            groupLabel,
            teacherValue,
            teacherLabel,
        )
    }
}
