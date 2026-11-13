package app.luxion.shogunai.cli.e2e

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OverlapEndToEndTest {

    private val sandbox = Sandbox()
    private val repository = sandbox.repository(
        "main-repo",
        mapOf(
            "README.md" to "main-repo\n",
            "src/App.kt" to "app\n",
            "build.gradle.kts" to "build\n",
            "docs/roadmap.md" to "roadmap\n",
            ".shogunai.json" to """{ "overlap": { "ignore": ["docs/roadmap*.md"] } }""" + "\n",
        ),
    )
    private val worktreesRoot = sandbox.root.resolve("main-repo-worktrees")

    @AfterTest
    fun cleanUp() = sandbox.close()

    private fun fileLines(output: String): List<String> = output.lines().filter { it.isNotEmpty() && !it.startsWith(" ") }

    @Test
    fun `lists the files two worktrees change against their base, from commits, edits and new files`() {
        sandbox.shogunSucceeds(repository, "project", "add")
        sandbox.shogunSucceeds(repository, "new", "TASK-1")
        sandbox.shogunSucceeds(repository, "new", "TASK-2")
        val task1 = worktreesRoot.resolve("TASK-1")
        val task2 = worktreesRoot.resolve("TASK-2")
        sandbox.commit(task1, "src/App.kt", "app from TASK-1\n", "Change the app")
        sandbox.write(task1, "src/New.kt", "new\n")
        sandbox.write(task1, "docs/roadmap.md", "roadmap from TASK-1\n")
        sandbox.write(task2, "src/App.kt", "app from TASK-2\n")
        sandbox.write(task2, "src/New.kt", "new\n")
        sandbox.write(task2, "docs/roadmap.md", "roadmap from TASK-2\n")
        sandbox.commit(task2, "README.md", "readme from TASK-2\n", "Change the readme")
        sandbox.commit(repository, "build.gradle.kts", "build on main\n", "Change the build")
        sandbox.write(task1, "build.gradle.kts", "build from TASK-1\n")

        assertEquals(listOf("src/App.kt", "src/New.kt"), fileLines(sandbox.shogunSucceeds(repository, "overlap").output))
        assertEquals(
            listOf("docs/roadmap.md", "src/App.kt", "src/New.kt"),
            fileLines(sandbox.shogunSucceeds(task1, "overlap", "--all").output),
        )

        val json = Json.parseToJsonElement(sandbox.shogunSucceeds(task2, "overlap", "--json").stdout).jsonObject
        val app = json.getValue("files").jsonArray.first().jsonObject
        assertEquals("src/App.kt", app.getValue("path").jsonPrimitive.content)
        assertEquals(
            listOf("feature/TASK-1" to "main", "feature/TASK-2" to "main"),
            app.getValue("worktrees").jsonArray.map { it.jsonObject.getValue("branch").jsonPrimitive.content to it.jsonObject.getValue("base").jsonPrimitive.content },
        )
    }

    @Test
    fun `says so when no file is shared`() {
        sandbox.shogunSucceeds(repository, "project", "add")
        sandbox.shogunSucceeds(repository, "new", "TASK-1")
        sandbox.write(worktreesRoot.resolve("TASK-1"), "src/App.kt", "app from TASK-1\n")

        assertEquals("No file is changed by more than one worktree.", sandbox.shogunSucceeds(repository, "overlap").output)
    }
}
