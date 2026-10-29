package app.luxion.shogunai.cli.e2e

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProvisioningEndToEndTest {

    private val sandbox = Sandbox()
    private val repository = sandbox.repository(
        "main-repo",
        mapOf(
            "README.md" to "main-repo\n",
            ".gitignore" to "local.properties\n.env*\n!.env.example\nkeys/\nsecrets/\nconfig/local/\n",
            ".env.example" to "TRACKED=1\n",
            ".shogunai.json" to """
                {
                  "copy": ["/local.properties", ".env*", "config/**/*.yml"],
                  "link": ["/keys/", "config/local/shared.yml"],
                  "hooks": { "postCreate": [["git", "config", "--local", "e2e.provisioned", "yes"]] }
                }
            """.trimIndent(),
            ".worktreeinclude" to "# secrets for the app\nsecrets/*.json\n",
        ),
    )
    private val worktreesRoot = sandbox.root.resolve("main-repo-worktrees")

    init {
        sandbox.write(repository, "local.properties", "sdk.dir=/opt/sdk\n")
        sandbox.write(repository, ".env.local", "API_KEY=placeholder\n")
        sandbox.write(repository, "app/.env.development", "MODE=dev\n")
        sandbox.write(repository, "config/local/app.yml", "port: 8080\n")
        sandbox.write(repository, "config/local/shared.yml", "shared: true\n")
        sandbox.write(repository, "keys/release.jks", "keystore\n")
        sandbox.write(repository, "keys/upload/upload.jks", "upload keystore\n")
        sandbox.write(repository, "secrets/firebase.json", "{}\n")
        sandbox.write(repository, "secrets/notes.txt", "not included\n")
        sandbox.write(repository, "nested/local.properties", "not anchored\n")
        sandbox.shogunSucceeds(repository, "project", "add")
    }

    @AfterTest
    fun cleanUp() = sandbox.close()

    @Test
    fun `new copies the files matched by copy patterns and worktreeinclude`() {
        sandbox.shogunSucceeds(repository, "new", "TASK-1")
        val worktree = worktreesRoot.resolve("TASK-1")

        assertCopied(worktree, "local.properties")
        assertCopied(worktree, ".env.local")
        assertCopied(worktree, "app/.env.development")
        assertCopied(worktree, "config/local/app.yml")
        assertCopied(worktree, "secrets/firebase.json")
        assertFalse(worktree.resolve("secrets/notes.txt").exists())
        assertFalse(worktree.resolve("nested/local.properties").exists())
        assertEquals("TRACKED=1\n", worktree.resolve(".env.example").readText())
        assertEquals("", sandbox.git(worktree, "status", "--porcelain"))
    }

    @Test
    fun `new links the files matched by link patterns, also when copy matches them`() {
        sandbox.shogunSucceeds(repository, "new", "TASK-1")
        val worktree = worktreesRoot.resolve("TASK-1")

        assertLinked(worktree, "keys/release.jks")
        assertLinked(worktree, "keys/upload/upload.jks")
        assertLinked(worktree, "config/local/shared.yml")
    }

    @Test
    fun `new skips the post-creation commands until the repository is trusted`() {
        val untrusted = sandbox.shogunSucceeds(repository, "new", "TASK-1")
        assertTrue(untrusted.stderr.contains("shogun trust"), untrusted.describe())
        assertEquals(null, provisionedMark(worktreesRoot.resolve("TASK-1")))

        sandbox.shogunSucceeds(repository, "trust", "--yes")
        sandbox.shogunSucceeds(repository, "new", "TASK-2")

        assertEquals("yes", provisionedMark(worktreesRoot.resolve("TASK-2")))
    }

    @Test
    fun `provision check reports a missing file and provision restores it`() {
        sandbox.shogunSucceeds(repository, "new", "TASK-1")
        val worktree = worktreesRoot.resolve("TASK-1")
        Files.delete(worktree.resolve("local.properties"))
        Files.delete(worktree.resolve("keys/release.jks"))

        val check = sandbox.shogun(repository, "provision", "--check", worktree.toString())
        assertEquals(3, check.exitCode, check.describe())
        assertTrue(check.stdout.contains("local.properties"), check.describe())
        assertTrue(check.stdout.contains("release.jks"), check.describe())

        sandbox.shogunSucceeds(worktree, "provision")

        assertCopied(worktree, "local.properties")
        assertLinked(worktree, "keys/release.jks")
        assertEquals(0, sandbox.shogun(worktree, "provision", "--check").exitCode)
    }

    @Test
    fun `provision check reports a copied file that changed in the base repository`() {
        sandbox.shogunSucceeds(repository, "new", "TASK-1")
        val worktree = worktreesRoot.resolve("TASK-1")
        sandbox.write(repository, ".env.local", "API_KEY=rotated\n")

        val check = sandbox.shogun(worktree, "provision", "--check")
        assertEquals(3, check.exitCode, check.describe())
        assertTrue(check.stdout.contains(".env.local"), check.describe())

        sandbox.shogunSucceeds(worktree, "provision")
        assertCopied(worktree, ".env.local")
    }

    @Test
    fun `new provisions a repository whose paths have spaces and accents`() {
        val spaced = sandbox.repository(
            "Projects ñ/main repo",
            mapOf(
                ".gitignore" to "local.properties\nkeys/\nconfig dir/\n",
                ".shogunai.json" to """{ "copy": ["/local.properties", "config dir/*.yml"], "link": ["/keys/"] }""",
            ),
        )
        sandbox.write(spaced, "local.properties", "sdk.dir=/opt/sdk\n")
        sandbox.write(spaced, "config dir/app settings.yml", "port: 8080\n")
        sandbox.write(spaced, "keys/clé.jks", "keystore\n")
        sandbox.shogunSucceeds(spaced, "project", "add")

        sandbox.shogunSucceeds(spaced, "new", "TASK-1")

        val worktree = spaced.resolveSibling("main repo-worktrees").resolve("TASK-1")
        assertEquals("sdk.dir=/opt/sdk\n", worktree.resolve("local.properties").readText())
        assertEquals("port: 8080\n", worktree.resolve("config dir/app settings.yml").readText())
        assertEquals("keystore\n", worktree.resolve("keys/clé.jks").readText())
        assertEquals(0, sandbox.shogun(worktree, "provision", "--check").exitCode)
    }

    private fun provisionedMark(worktree: Path): String? {
        val result = sandbox.run(listOf("git", "config", "--local", "e2e.provisioned"), worktree)
        return result.output.takeIf { result.exitCode == 0 }
    }

    private fun assertCopied(worktree: Path, path: String) {
        val copy = worktree.resolve(path)
        assertTrue(copy.exists(), "$path is missing from the worktree")
        assertFalse(copy.isSymbolicLink(), "$path is a link instead of a copy")
        assertEquals(repository.resolve(path).readText(), copy.readText())
    }

    private fun assertLinked(worktree: Path, path: String) {
        val file = worktree.resolve(path)
        assertTrue(file.exists(), "$path is missing from the worktree")
        if (file.isSymbolicLink()) {
            assertEquals(repository.resolve(path).toRealPath(), file.toRealPath())
        } else {
            assertTrue(Sandbox.isWindows, "$path is a copy instead of a link")
            assertEquals(repository.resolve(path).readText(), file.readText())
        }
    }
}
