package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectDefaultsTest {

    @Test
    fun `names the project after the folder and puts worktrees next to it`() {
        val defaults = projectDefaultsFor("/home/dev/projects/main-repo")

        assertEquals("main-repo", defaults.name)
        assertEquals("/home/dev/projects/main-repo-worktrees", defaults.worktreesRoot)
    }

    @Test
    fun `ignores trailing separators`() {
        val defaults = projectDefaultsFor("/home/dev/projects/main-repo/")

        assertEquals("main-repo", defaults.name)
        assertEquals("/home/dev/projects/main-repo-worktrees", defaults.worktreesRoot)
    }

    @Test
    fun `handles Windows paths`() {
        val defaults = projectDefaultsFor("C:\\Users\\dev\\projects\\main-repo")

        assertEquals("main-repo", defaults.name)
        assertEquals("C:\\Users\\dev\\projects/main-repo-worktrees", defaults.worktreesRoot)
    }

    @Test
    fun `a new project has the app's defaults`() {
        val project = newProject("project-1", "/home/dev/projects/main-repo")

        assertEquals(
            Project(
                id = "project-1",
                name = "main-repo",
                config = ProjectConfig(
                    baseRepositoryPath = "/home/dev/projects/main-repo",
                    worktreesRoot = "/home/dev/projects/main-repo-worktrees",
                    secretFiles = emptyList(),
                ),
            ),
            project,
        )
        assertEquals(AgentLaunchConfig(), project.config.agentLaunchConfig)
        assertEquals(null, project.config.mainBranch)
    }
}
