package by.iseu.schedule.data.parser

import by.iseu.schedule.data.model.Option

/**
 * Matches the teacher label shown inside a group lesson to the teacher options
 * loaded from the teacher schedule page. The group page uses a position prefix
 * ("доц. Иванов Иван Иванович") while the catalog uses a suffix
 * ("Иванов Иван Иванович (доц.)").
 */
object TeacherMatcher {
    private val PARENTHESIZED = Regex("""\([^)]*\)""")
    private val PUNCTUATION = Regex("""[.,;:()]""")
    private val WHITESPACE = Regex("""\s+""")
    private val POSITIONS = setOf(
        "доц",
        "доцент",
        "проф",
        "профессор",
        "пр",
        "преп",
        "преподав",
        "преподаватель",
        "ст",
        "старший",
        "доктор",
        "ассист",
        "ассистент",
    )

    fun match(staff: String, teachers: List<Option>): Option? {
        val key = nameKey(staff)
        if (key.isBlank()) return null
        return teachers.filter { nameKey(it.label) == key }.singleOrNull()
    }

    internal fun nameKey(raw: String): String =
        raw.replace('\u00A0', ' ')
            .replace(PARENTHESIZED, " ")
            .replace(PUNCTUATION, " ")
            .lowercase()
            .replace('ё', 'е')
            .split(WHITESPACE)
            .filter { it.isNotBlank() && it !in POSITIONS }
            .joinToString(" ")
            .trim()
}
