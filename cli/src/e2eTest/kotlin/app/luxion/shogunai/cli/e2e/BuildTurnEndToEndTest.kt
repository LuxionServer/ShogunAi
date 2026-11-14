package app.luxion.shogunai.cli.e2e

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildTurnEndToEndTest {

    private val sandbox = Sandbox()
    private val repository = sandbox.repository("main-repo")

    @AfterTest
    fun cleanUp() = sandbox.close()

    @Test
    fun `a build runs here with its output and gives its slot back`() {
        val result = sandbox.shogun(repository, "build", "run", "--", "git", "rev-parse", "--abbrev-ref", "HEAD")

        assertEquals(0, result.exitCode, result.describe())
        assertEquals("main", result.output)
        assertFalse(sandbox.home.resolve(".shogunai/build-turns.json").exists())
        val status = Json.parseToJsonElement(sandbox.shogunSucceeds(repository, "build", "status", "--json").stdout).jsonObject
        assertTrue(status.getValue("running").jsonArray.isEmpty())
    }

    @Test
    fun `a failing build passes its exit code through`() {
        val result = sandbox.shogun(repository, "build", "run", "--", "git", "rev-parse", "--verify", "--quiet", "no-such-ref")

        assertEquals(1, result.exitCode, result.describe())
        assertEquals("", result.stderr)
    }

    @Test
    fun `a command that can't start exits with 127`() {
        val result = sandbox.shogun(repository, "build", "run", "--", "shogun-e2e-command-that-does-not-exist")

        assertEquals(127, result.exitCode, result.describe())
        assertTrue("couldn't start shogun-e2e-command-that-does-not-exist" in result.stderr, result.describe())
    }
}
