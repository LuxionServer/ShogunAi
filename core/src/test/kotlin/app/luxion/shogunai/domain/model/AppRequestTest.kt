package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppRequestTest {

    @Test
    fun `an open project request round-trips`() {
        val request = AppRequest.openProject(createdAt = 1_000, projectId = "project-1", settings = true)

        assertEquals(request, AppRequest.parse(request.toJson()))
    }

    @Test
    fun `an open app request round-trips`() {
        val request = AppRequest.openApp(createdAt = 1_000)

        assertEquals(request, AppRequest.parse(request.toJson()))
    }

    @Test
    fun `ignores fields it doesn't know`() {
        val text = """{"version":1,"createdAt":5,"action":"open-app","future":"value"}"""

        assertEquals(AppRequest.openApp(5), AppRequest.parse(text))
    }

    @Test
    fun `discards an unknown action`() {
        assertNull(AppRequest.parse("""{"version":1,"createdAt":5,"action":"open-group"}"""))
    }

    @Test
    fun `discards an unknown version`() {
        assertNull(AppRequest.parse("""{"version":2,"createdAt":5,"action":"open-app"}"""))
    }

    @Test
    fun `discards an open project request without a project`() {
        assertNull(AppRequest.parse("""{"version":1,"createdAt":5,"action":"open-project"}"""))
    }

    @Test
    fun `discards text that isn't a request`() {
        assertNull(AppRequest.parse("not json"))
        assertNull(AppRequest.parse("[]"))
        assertNull(AppRequest.parse(""))
    }
}
