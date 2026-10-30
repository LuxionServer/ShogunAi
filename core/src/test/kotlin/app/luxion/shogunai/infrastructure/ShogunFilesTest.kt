package app.luxion.shogunai.infrastructure

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ShogunFilesTest {

    @Test
    fun `keeps every file in the shogunai folder of the home`() {
        val files = ShogunFiles("/home/dev")
        val folder = Path.of("/home/dev", ".shogunai")

        assertEquals(folder, files.folder)
        assertEquals(
            listOf(
                "projects.json", "settings.json", "trust.json", "multirepos.json", "compose-projects.json", "orca.json",
                "derived-data-sizes.json", "agent-sessions", "app-requests", "app.lock", "logs", "headless",
            ).map(folder::resolve),
            listOf(
                files.projects, files.settings, files.trust, files.multirepos, files.composeProjects, files.orcaWorkspaces,
                files.derivedDataSizes, files.agentSessions, files.appRequests, files.appLock, files.logs, files.headless,
            ),
        )
    }
}
