package by.iseu.schedule.data.parser

import by.iseu.schedule.data.model.Option
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeacherMatcherTest {

    private val teachers = listOf(
        Option("-414", "Авдеева Екатерина Владимировна (пр.)"),
        Option("-446", "Авласович Алексей Михайлович (доц.)"),
        Option("-364", "Аксенчик Светлана Владимировна (ст.пр.)"),
        Option("-999", "Шахаб Сиямак Насер (проф.)"),
        Option("-100", "Авласович Пётр Сергеевич (пр.)"),
        Option("-101", "Иванов Иван Иванович"),
    )

    @Test
    fun `matches staff label with position to teacher catalog label`() {
        assertEquals(
            "-446",
            TeacherMatcher.match("доц. Авласович Алексей Михайлович", teachers)?.value,
        )
        assertEquals(
            "-414",
            TeacherMatcher.match("пр. Авдеева Екатерина Владимировна", teachers)?.value,
        )
        assertEquals(
            "-364",
            TeacherMatcher.match("ст.пр. Аксенчик Светлана Владимировна", teachers)?.value,
        )
        assertEquals(
            "-999",
            TeacherMatcher.match("доктор Шахаб Сиямак Насер", teachers)?.value,
        )
    }

    @Test
    fun `matches when the catalog label has no position`() {
        assertEquals(
            "-101",
            TeacherMatcher.match("Иванов Иван Иванович", teachers)?.value,
        )
    }

    @Test
    fun `does not confuse teachers with the same surname`() {
        assertEquals(
            "-446",
            TeacherMatcher.match("доц. Авласович Алексей Михайлович", teachers)?.value,
        )
        assertEquals(
            "-100",
            TeacherMatcher.match("пр. Авласович Пётр Сергеевич", teachers)?.value,
        )
    }

    @Test
    fun `returns null for unknown and blank staff`() {
        assertNull(TeacherMatcher.match("пр. Неизвестный Иван Иванович", teachers))
        assertNull(TeacherMatcher.match("", teachers))
        assertNull(TeacherMatcher.match("   ", teachers))
    }
}
