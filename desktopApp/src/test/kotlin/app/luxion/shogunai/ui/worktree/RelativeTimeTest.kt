package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {

    private val now = Instant.parse("2026-10-16T12:00:00Z")

    private fun englishAgo(elapsed: Duration) = relativeTime(now.minus(elapsed), now, EnglishStrings)

    private fun spanishAgo(elapsed: Duration) = relativeTime(now.minus(elapsed), now, SpanishStrings)

    @Test
    fun `under a minute is just now`() {
        assertEquals("just now", englishAgo(Duration.ofSeconds(59)))
        assertEquals("ahora mismo", spanishAgo(Duration.ofSeconds(59)))
    }

    @Test
    fun `a commit in the future is just now`() {
        assertEquals("just now", relativeTime(now.plusSeconds(30), now, EnglishStrings))
    }

    @Test
    fun `minutes up to an hour`() {
        assertEquals("1 minute ago", englishAgo(Duration.ofMinutes(1)))
        assertEquals("59 minutes ago", englishAgo(Duration.ofMinutes(59)))
        assertEquals("hace 1 minuto", spanishAgo(Duration.ofMinutes(1)))
        assertEquals("hace 59 minutos", spanishAgo(Duration.ofMinutes(59)))
    }

    @Test
    fun `hours up to a day`() {
        assertEquals("1 hour ago", englishAgo(Duration.ofHours(1)))
        assertEquals("23 hours ago", englishAgo(Duration.ofHours(23)))
        assertEquals("hace 1 hora", spanishAgo(Duration.ofHours(1)))
        assertEquals("hace 23 horas", spanishAgo(Duration.ofHours(23)))
    }

    @Test
    fun `days up to a month`() {
        assertEquals("1 day ago", englishAgo(Duration.ofDays(1)))
        assertEquals("2 days ago", englishAgo(Duration.ofDays(2)))
        assertEquals("29 days ago", englishAgo(Duration.ofDays(29)))
        assertEquals("hace 2 días", spanishAgo(Duration.ofDays(2)))
    }

    @Test
    fun `months up to a year`() {
        assertEquals("1 month ago", englishAgo(Duration.ofDays(30)))
        assertEquals("12 months ago", englishAgo(Duration.ofDays(364)))
        assertEquals("hace 1 mes", spanishAgo(Duration.ofDays(30)))
        assertEquals("hace 12 meses", spanishAgo(Duration.ofDays(364)))
    }

    @Test
    fun `years from then on`() {
        assertEquals("1 year ago", englishAgo(Duration.ofDays(365)))
        assertEquals("2 years ago", englishAgo(Duration.ofDays(800)))
        assertEquals("hace 1 año", spanishAgo(Duration.ofDays(365)))
        assertEquals("hace 2 años", spanishAgo(Duration.ofDays(800)))
    }
}
