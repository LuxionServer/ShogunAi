package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.AppVersion
import app.luxion.shogunai.domain.model.ReleaseAsset
import app.luxion.shogunai.domain.model.ReleaseNotes
import app.luxion.shogunai.domain.model.WorktreeError
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GitHubReleaseSourceTest {

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val receivedHeaders = mutableMapOf<String, String?>()
    private var status = 200
    private var body = ""
    private var delayMillis = 0L

    init {
        server.createContext("/releases") { exchange ->
            receivedHeaders["Accept"] = exchange.requestHeaders.getFirst("Accept")
            receivedHeaders["User-Agent"] = exchange.requestHeaders.getFirst("User-Agent")
            Thread.sleep(delayMillis)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
    }

    private fun source(timeout: Duration = Duration.ofSeconds(5)) = GitHubReleaseSource(
        appVersion = "1.2.0",
        endpoint = "http://127.0.0.1:${server.address.port}/releases?per_page=30",
        timeout = timeout,
    )

    @Test
    fun `reads the version, the page, the assets and the notes of every release`() = runTest {
        body = """
            [
              {
                "tag_name": "v1.3.0",
                "html_url": "https://example.test/releases/v1.3.0",
                "name": "ShogunAi 1.3.0",
                "body": "<!-- shogunai:notes:en -->\nFaster\n<!-- /shogunai:notes:en -->\n<!-- shogunai:notes:es -->\nMás rápido\n<!-- /shogunai:notes:es -->",
                "assets": [
                  { "name": "ShogunAi-1.3.0.msi", "browser_download_url": "https://example.test/app.msi", "size": 10 },
                  { "name": "broken" }
                ]
              },
              {
                "tag_name": "v1.2.0",
                "html_url": "https://example.test/releases/v1.2.0",
                "body": "* Upgrade Compose by @someone in https://example.test/pull/12"
              }
            ]
        """.trimIndent()

        val releases = source().published().getOrThrow()

        assertEquals(listOf(AppVersion(1, 3, 0), AppVersion(1, 2, 0)), releases.map { it.version })
        val newest = releases.first()
        assertEquals("https://example.test/releases/v1.3.0", newest.pageUrl)
        assertEquals(listOf(ReleaseAsset("ShogunAi-1.3.0.msi", "https://example.test/app.msi")), newest.assets)
        assertEquals(ReleaseNotes("Faster", "Más rápido"), newest.notes)
        assertEquals(ReleaseNotes("* Upgrade Compose", null), releases[1].notes)
    }

    @Test
    fun `sends the GitHub accept header and a user agent with the version`() = runTest {
        body = """[{ "tag_name": "1.3.0", "html_url": "https://example.test/r" }]"""

        source().published().getOrThrow()

        assertEquals("application/vnd.github+json", receivedHeaders["Accept"])
        assertEquals("ShogunAi/1.2.0", receivedHeaders["User-Agent"])
    }

    @Test
    fun `skips drafts, pre-releases and tags that are not versions`() = runTest {
        body = """
            [
              { "tag_name": "v1.4.0", "draft": true },
              { "tag_name": "v1.4.0-rc1", "prerelease": true },
              { "tag_name": "v1.3.5", "prerelease": true },
              { "tag_name": "nightly" },
              { "tag_name": "v1.3.0", "draft": false, "prerelease": false, "html_url": "https://example.test/r" }
            ]
        """.trimIndent()

        assertEquals(listOf(AppVersion(1, 3, 0)), source().published().getOrThrow().map { it.version })
    }

    @Test
    fun `an empty listing has no releases`() = runTest {
        body = "[]"

        assertEquals(emptyList(), source().published().getOrThrow())
    }

    @Test
    fun `fails on a malformed body`() = runTest {
        body = "<html>not json</html>"

        assertIs<WorktreeError.UpdateCheckFailed>(source().published().exceptionOrNull())
    }

    @Test
    fun `fails on a single release instead of a listing`() = runTest {
        body = """{ "tag_name": "v1.3.0", "html_url": "https://example.test/r" }"""

        assertIs<WorktreeError.UpdateCheckFailed>(source().published().exceptionOrNull())
    }

    @Test
    fun `fails when GitHub refuses with 403`() = runTest {
        status = 403
        body = """{ "message": "API rate limit exceeded" }"""

        val failure = source().published().exceptionOrNull()

        assertIs<WorktreeError.UpdateCheckFailed>(failure)
        assertEquals("GitHub answered 403", failure.reason)
    }

    @Test
    fun `fails when GitHub doesn't answer in time`() = runTest {
        body = """[{ "tag_name": "1.3.0" }]"""
        delayMillis = 1_500

        assertIs<WorktreeError.UpdateCheckFailed>(source(Duration.ofMillis(200)).published().exceptionOrNull())
    }

    @Test
    fun `fails when there is no connection`() = runTest {
        server.stop(0)

        assertIs<WorktreeError.UpdateCheckFailed>(source().published().exceptionOrNull())
    }
}
