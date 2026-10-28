package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReleaseNotesTest {

    private val bilingualBody = """
        <!-- shogunai:notes:en -->

        ## What's new

        - **Notes inside the app.** Read them in your language.

        <!-- /shogunai:notes:en -->

        <details>
        <summary>Español</summary>

        <!-- shogunai:notes:es -->

        ## Novedades

        - **Notas dentro de la app.** Léelas en tu idioma.

        <!-- /shogunai:notes:es -->

        </details>
    """.trimIndent()

    @Test
    fun `reads each language between its markers`() {
        val notes = ReleaseNotes.parse(bilingualBody)

        assertEquals("## What's new\n\n- **Notes inside the app.** Read them in your language.", notes.english)
        assertEquals("## Novedades\n\n- **Notas dentro de la app.** Léelas en tu idioma.", notes.spanish)
    }

    @Test
    fun `reads markers in a body with Windows line endings`() {
        val notes = ReleaseNotes.parse(bilingualBody.replace("\n", "\r\n"))

        assertEquals("## Novedades\n\n- **Notas dentro de la app.** Léelas en tu idioma.", notes.spanish)
    }

    @Test
    fun `a body with only the English markers has no Spanish notes`() {
        val notes = ReleaseNotes.parse("<!-- shogunai:notes:en -->\n- Faster start-up\n<!-- /shogunai:notes:en -->")

        assertEquals("- Faster start-up", notes.english)
        assertNull(notes.spanish)
    }

    @Test
    fun `cleans GitHub's generated notes`() {
        val body = """
            <!-- Release notes generated using configuration in .github/release.yml -->

            ## What's Changed
            * Upgrade Compose by @someone in https://github.com/owner/repo/pull/12
            * Fix the sidebar order by @dependabot[bot] in https://github.com/owner/repo/pull/13

            ## New Contributors
            * @someone made their first contribution in https://github.com/owner/repo/pull/12


            **Full Changelog**: https://github.com/owner/repo/compare/v1.2.0...v1.3.0
        """.trimIndent()

        val notes = ReleaseNotes.parse(body)

        assertEquals("* Upgrade Compose\n* Fix the sidebar order", notes.english)
        assertNull(notes.spanish)
    }

    @Test
    fun `keeps the sections that follow the new contributors`() {
        val body = "## New Contributors\n* @someone made their first contribution in https://example.test/pull/1\n\n## Notes\nPlain text"

        assertEquals("## Notes\nPlain text", ReleaseNotes.parse(body).english)
    }

    @Test
    fun `an empty body or one with only generated boilerplate has no notes`() {
        assertEquals(ReleaseNotes.NONE, ReleaseNotes.parse(""))
        assertEquals(ReleaseNotes.NONE, ReleaseNotes.parse(null))
        assertEquals(ReleaseNotes.NONE, ReleaseNotes.parse("**Full Changelog**: https://example.test/compare/v1.2.0...v1.3.0"))
        assertEquals(ReleaseNotes.NONE, ReleaseNotes.parse("<!-- shogunai:notes:en -->\n\n<!-- /shogunai:notes:en -->"))
    }

    @Test
    fun `picks the language and falls back to English`() {
        val both = ReleaseNotes(english = "English", spanish = "Español")
        val englishOnly = ReleaseNotes(english = "English", spanish = null)

        assertEquals("English", both.forLanguage(AppLanguage.ENGLISH))
        assertEquals("Español", both.forLanguage(AppLanguage.SPANISH))
        assertEquals("English", englishOnly.forLanguage(AppLanguage.SPANISH))
        assertNull(ReleaseNotes.NONE.forLanguage(AppLanguage.SPANISH))
    }
}
