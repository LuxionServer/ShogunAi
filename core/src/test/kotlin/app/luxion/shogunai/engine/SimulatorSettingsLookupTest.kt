package app.luxion.shogunai.engine

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SimulatorSettings
import java.io.IOException
import java.nio.file.Files
import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class SimulatorSettingsLookupTest {

    private val settings = SimulatorSettings(device = "iPhone 16", runtime = "iOS 18.5")

    private fun project(base: String) = Project("1", "main-repo", ProjectConfig(base, "", emptyList(), simulator = settings))

    private fun engine(projects: ProjectRepository) = ShogunEngine(fakeEnginePorts(projectRepository = projects))

    @Test
    fun `a project registered through a symbolic link keeps its simulator settings`() {
        val root = createTempDirectory("simulator-settings-lookup").toRealPath()
        val repository = root.resolve("main-repo").createDirectory()
        val link = Files.createSymbolicLink(root.resolve("main-repo-link"), repository)

        val found = engine(FakeProjectRepository(listOf(project(link.toString())))).simulatorSettingsFor(repository.toString())

        assertEquals(settings, found)
    }

    @Test
    fun `a trailing slash still matches`() {
        val found = engine(FakeProjectRepository(listOf(project("/home/dev/projects/main-repo/")))).simulatorSettingsFor("/home/dev/projects/main-repo")

        assertEquals(settings, found)
    }

    @Test
    fun `no matching project uses the default settings`() {
        val found = engine(FakeProjectRepository(listOf(project("/home/dev/projects/other")))).simulatorSettingsFor("/home/dev/projects/main-repo")

        assertEquals(SimulatorSettings(), found)
    }

    @Test
    fun `an unreadable project catalog uses the default settings`() {
        val unreadable = object : ProjectRepository by FakeProjectRepository() {
            override fun list(): List<Project> = throw IOException("projects.json is unreadable")
        }

        assertEquals(SimulatorSettings(), engine(unreadable).simulatorSettingsFor("/home/dev/projects/main-repo"))
    }
}
