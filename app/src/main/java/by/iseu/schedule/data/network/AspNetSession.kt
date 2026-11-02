package by.iseu.schedule.data.network

import by.iseu.schedule.data.model.Option
import by.iseu.schedule.data.parser.OptionParser
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

class AspNetSession(
    private val client: OkHttpClient,
    private val url: String,
    private val cookieJar: MemoryCookieJar,
) {
    private var document: Document

    init {
        cookieJar.clear()
        document = fetch(null)
    }

    fun options(selectName: String): List<Option> =
        OptionParser.parseOptions(document.outerHtml(), selectName)

    fun postback(eventTarget: String, values: Map<String, String> = emptyMap()) {
        val fields = collectFields()
        fields.putAll(values)
        fields["__EVENTTARGET"] = eventTarget
        fields["__EVENTARGUMENT"] = ""
        document = fetch(fields)
    }

    fun submit(values: Map<String, String> = emptyMap()): String {
        val fields = collectFields()
        fields.putAll(values)
        fields["__EVENTTARGET"] = ""
        fields["__EVENTARGUMENT"] = ""
        document = fetch(fields)
        return document.outerHtml()
    }

    fun message(): String? =
        document.selectFirst("[id=lMessage]")?.text()?.trim()?.takeIf { it.isNotEmpty() }

    private fun fetch(fields: Map<String, String>?): Document {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", url)

        val request = if (fields == null) {
            builder.get().build()
        } else {
            val body = FormBody.Builder().apply {
                fields.forEach { (name, value) -> add(name, value) }
            }.build()
            builder.post(body).build()
        }

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val html = response.body?.string().orEmpty()
            return Jsoup.parse(html, url)
        }
    }

    private fun collectFields(): MutableMap<String, String> {
        val fields = linkedMapOf<String, String>()
        val form = document.selectFirst("form") ?: return fields

        form.select("input[name]").forEach { input ->
            val type = input.attr("type").lowercase()
            when (type) {
                "submit", "button", "image", "reset", "file" -> Unit
                "checkbox", "radio" -> {
                    if (input.hasAttr("checked")) {
                        fields[input.attr("name")] = input.attr("value").ifBlank { "on" }
                    }
                }
                else -> fields[input.attr("name")] = input.attr("value")
            }
        }
        form.select("select[name]").forEach { select ->
            val option = select.selectFirst("option[selected]") ?: select.selectFirst("option")
            if (option != null) fields[select.attr("name")] = option.attr("value")
        }
        form.select("textarea[name]").forEach { textarea ->
            fields[textarea.attr("name")] = textarea.text()
        }
        return fields
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) Chrome/124.0 Mobile"
    }
}
