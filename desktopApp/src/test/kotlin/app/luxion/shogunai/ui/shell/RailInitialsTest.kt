package app.luxion.shogunai.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals

class RailInitialsTest {

    @Test
    fun `projects with different first letters take their first two letters`() {
        assertEquals(mapOf("api" to "AP", "web" to "WE"), railInitials(listOf("api", "web")))
    }

    @Test
    fun `projects with the same first two letters take a letter the others lack`() {
        assertEquals(mapOf("app" to "AP", "api" to "AI"), railInitials(listOf("app", "api")))
    }

    @Test
    fun `a name of several words takes the initials of its first two`() {
        assertEquals("MR", railInitials(listOf("main-repo")).getValue("main-repo"))
        assertEquals("MR", railInitials(listOf("mainRepo")).getValue("mainRepo"))
        assertEquals("MR", railInitials(listOf("main_repo docs")).getValue("main_repo docs"))
    }

    @Test
    fun `a collision that no letter resolves is kept`() {
        assertEquals(mapOf("aa" to "AA", "aaa" to "AA"), railInitials(listOf("aa", "aaa")))
    }

    @Test
    fun `a one letter name keeps its letter`() {
        assertEquals("X", railInitials(listOf("x")).getValue("x"))
    }

    @Test
    fun `a name without letters keeps its tile`() {
        assertEquals(mapOf("--" to "--", "__" to "__"), railInitials(listOf("--", "__")))
    }
}
