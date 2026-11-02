package by.iseu.schedule.data.network

import by.iseu.schedule.data.model.Option
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AspNetSessionTest {

    private lateinit var server: MockWebServer
    private lateinit var jar: MemoryCookieJar
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        jar = MemoryCookieJar()
        client = OkHttpClient.Builder().cookieJar(jar).build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `stores and clears cookies`() {
        val url = server.url("/").toString().toHttpUrl()
        jar.saveFromResponse(url, listOf(Cookie.parse(url, "a=1; Path=/")!!))
        assertEquals("1", jar.loadForRequest(url).single().value)
        jar.clear()
        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test
    fun `submits postback with hidden fields and sends cookie`() {
        server.enqueue(
            MockResponse()
                .setBody(page("STATE1", ""))
                .addHeader("Set-Cookie", "ASP.NET_SessionId=abc; Path=/"),
        )
        server.enqueue(MockResponse().setBody(page("STATE2", "Сообщение")))
        server.enqueue(MockResponse().setBody(page("STATE3", "")))

        val session = AspNetSession(client, server.url("/").toString(), jar)
        server.takeRequest() // initial GET

        assertEquals(
            listOf(Option("2", "Факультет"), Option("3", "Другой")),
            session.options("ddlFac"),
        )

        session.postback("ddlFac", mapOf("ddlFac" to "2"))
        val post = server.takeRequest()
        val body = post.body.readUtf8()
        assertTrue(body.contains("__EVENTTARGET=ddlFac"))
        assertTrue(body.contains("__VIEWSTATE=STATE1"))
        assertTrue(body.contains("ddlFac=2"))
        assertTrue(post.getHeader("Cookie")!!.contains("ASP.NET_SessionId=abc"))

        assertEquals("Сообщение", session.message())

        session.submit(mapOf("ShowTT" to "Показать"))
        val submit = server.takeRequest()
        val submitBody = submit.body.readUtf8()
        assertTrue(submitBody.contains("__VIEWSTATE=STATE2"))
        assertTrue(submitBody.contains("ShowTT="))
        assertTrue(submitBody.contains("__EVENTTARGET="))
    }

    private fun page(state: String, message: String) = """
        <html><body><form method="post" action="/">
        <input type="hidden" name="__VIEWSTATE" value="$state" />
        <input type="hidden" name="__EVENTVALIDATION" value="EV" />
        <select name="ddlFac">
          <option value="2" selected>Факультет</option>
          <option value="3">Другой</option>
        </select>
        <input type="submit" name="ShowTT" value="Показать" />
        <span id="lMessage">$message</span>
        </form></body></html>
    """.trimIndent()
}
