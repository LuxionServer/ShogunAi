package app.luxion.shogunai.cli.e2e

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectLocalFilesEndToEndTest {

    private val sandbox = Sandbox()
    private val repository = sandbox.repository(
        "main-repo",
        mapOf("README.md" to "main-repo\n", ".gitignore" to "google-services.json\ncerts/\n*.keystore\nsigning/\n"),
    )
    private val worktree = sandbox.root.resolve("main-repo-worktrees/TASK-1")

    init {
        sandbox.write(repository, "app/google-services.json", "{ \"project\": \"placeholder\" }\n")
        sandbox.write(repository, "certs/dev.pem", "dev\n")
        sandbox.write(repository, "certs/nested/ca.pem", "ca\n")
        sandbox.write(repository, "debug.keystore", "debug\n")
        sandbox.write(repository, "app/release.keystore", "release\n")
        sandbox.write(repository, "signing/upload.jks", "upload\n")
        sandbox.shogunSucceeds(repository, "project", "add")
        setProjectLocalFiles(
            files = listOf("app/google-services.json", "certs/", "*.keystore", "signing/"),
            linked = listOf("signing/"),
        )
    }

    @AfterTest
    fun cleanUp() = sandbox.close()

    @Test
    fun `new copies the project's local files, folders and patterns and links the linked ones`() {
        sandbox.shogunSucceeds(repository, "new", "TASK-1")

        listOf("app/google-services.json", "certs/dev.pem", "certs/nested/ca.pem", "debug.keystore", "app/release.keystore").forEach { path ->
            val copy = worktree.resolve(path)
            assertTrue(copy.exists(), "$path is missing from the worktree")
            assertFalse(copy.isSymbolicLink(), "$path is a link instead of a copy")
            assertEquals(repository.resolve(path).readText(), copy.readText())
        }
        val signing = worktree.resolve("signing/upload.jks")
        assertEquals("upload\n", signing.readText())
        if (!Sandbox.isWindows) {
            assertEquals(repository.resolve("signing/upload.jks").toRealPath(), signing.toRealPath())
        }
    }

    @Test
    fun `provision restores a deleted local file of the project`() {
        sandbox.shogunSucceeds(repository, "new", "TASK-1")
        Files.delete(worktree.resolve("certs/nested/ca.pem"))

        val check = sandbox.shogun(worktree, "provision", "--check")
        assertEquals(3, check.exitCode, check.describe())
        assertTrue(check.stdout.contains("ca.pem"), check.describe())

        sandbox.shogunSucceeds(worktree, "provision")

        assertEquals("ca\n", worktree.resolve("certs/nested/ca.pem").readText())
    }

    private fun setProjectLocalFiles(files: List<String>, linked: List<String>) {
        val catalog = sandbox.home.resolve(".shogunai/projects.json")
        val projects = Json.parseToJsonElement(catalog.readText()).jsonArray.map { project ->
            val config = project.jsonObject.getValue("config").jsonObject
            val updated = JsonObject(config + mapOf("secretFiles" to strings(files), "linkedSecretFiles" to strings(linked)))
            JsonObject(project.jsonObject + ("config" to updated))
        }
        catalog.writeText(JsonArray(projects).toString())
    }

    private fun strings(values: List<String>): JsonArray = JsonArray(values.map(::JsonPrimitive))
}
