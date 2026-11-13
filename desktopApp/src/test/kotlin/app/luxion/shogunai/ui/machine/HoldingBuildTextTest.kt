package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.AgentSessionRef
import app.luxion.shogunai.domain.model.GradleClient
import app.luxion.shogunai.domain.model.HoldingBuild
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class HoldingBuildTextTest {

    private val now = Instant.parse("2026-01-10T10:00:00Z")
    private val session = AgentSessionRef("1a2b3c4d-0000-0000-0000-000000000000", "claude", "~/projects/worktrees/TASK-123")

    private fun build(tasks: List<String>? = null, startedMinutesAgo: Long? = null, session: AgentSessionRef? = null) = HoldingBuild(
        directory = "~/projects/worktrees/TASK-123",
        startedAt = startedMinutesAgo?.let { now.minusSeconds(it * 60) },
        client = tasks?.let { GradleClient(pid = 4242, tasks = it, session = session) },
    )

    @Test
    fun `build launched by an agent session names its tasks, start and session`() {
        val holder = build(listOf(":app:assembleDebug"), startedMinutesAgo = 3, session = session)

        assertEquals(
            "Building :app:assembleDebug · started 3 minutes ago · agent session claude 1a2b3c4d",
            holdingBuildText(holder, EnglishStrings, now),
        )
        assertEquals(
            "Compilando :app:assembleDebug · empezó hace 3 minutos · sesión de agente claude 1a2b3c4d",
            holdingBuildText(holder, SpanishStrings, now),
        )
    }

    @Test
    fun `client outside an agent session names its tasks and start only`() {
        val holder = build(listOf("clean", "test"), startedMinutesAgo = 1)

        assertEquals("Building clean test · started 1 minute ago", holdingBuildText(holder, EnglishStrings, now))
        assertEquals("Compilando clean test · empezó hace 1 minuto", holdingBuildText(holder, SpanishStrings, now))
    }

    @Test
    fun `build without a client found says a build is running`() {
        val holder = build(startedMinutesAgo = 2)

        assertEquals("Running a build · started 2 minutes ago", holdingBuildText(holder, EnglishStrings, now))
        assertEquals("Ejecutando una build · empezó hace 2 minutos", holdingBuildText(holder, SpanishStrings, now))
    }

    @Test
    fun `client that asked for no tasks keeps its session`() {
        val holder = build(emptyList(), session = session)

        assertEquals("Running a build · agent session claude 1a2b3c4d", holdingBuildText(holder, EnglishStrings, now))
    }

    @Test
    fun `build with nothing known but its directory says a build is running`() {
        assertEquals("Running a build", holdingBuildText(build(), EnglishStrings, now))
        assertEquals("Ejecutando una build", holdingBuildText(build(), SpanishStrings, now))
    }
}
