package by.iseu.schedule.data.parser

import by.iseu.schedule.data.model.Option
import org.jsoup.Jsoup

object OptionParser {
    private val WHITESPACE = Regex("""\s+""")

    fun parseOptions(html: String, selectName: String): List<Option> {
        val doc = Jsoup.parse(html)
        val select = doc.select("select").firstOrNull {
            it.attr("name") == selectName || it.id() == selectName
        } ?: return emptyList()

        return select.select("option").mapNotNull { option ->
            val value = option.attr("value")
            val label = option.text().replace('\u00A0', ' ').replace(WHITESPACE, " ").trim()
            if (value.isBlank() && label.isBlank()) null else Option(value, label)
        }
    }
}
