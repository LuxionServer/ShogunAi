package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeDependenciesTest {

    @Test
    fun `finds the manager from its lockfile`() {
        assertEquals(NodeProject(NodePackageManager.PNPM, "pnpm-lock.yaml"), chooseNodeProject(setOf("pnpm-lock.yaml"), null, false))
        assertEquals(NodeProject(NodePackageManager.NPM, "npm-shrinkwrap.json"), chooseNodeProject(setOf("npm-shrinkwrap.json"), null, false))
        assertEquals(NodeProject(NodePackageManager.BUN, "bun.lockb"), chooseNodeProject(setOf("bun.lockb"), null, false))
        assertNull(chooseNodeProject(emptySet(), "npm", false))
    }

    @Test
    fun `packageManager decides between several lockfiles, and otherwise pnpm, yarn, bun and npm in that order`() {
        val lockfiles = setOf("yarn.lock", "package-lock.json")

        assertEquals(NodePackageManager.NPM, chooseNodeProject(lockfiles, "npm", false)?.manager)
        assertEquals(NodePackageManager.YARN, chooseNodeProject(lockfiles, null, false)?.manager)
        assertEquals(NodePackageManager.YARN, chooseNodeProject(lockfiles, "pnpm", false)?.manager)
    }

    @Test
    fun `reads the manager's name from packageManager`() {
        assertEquals("npm", packageManagerName("""{ "name": "web", "packageManager" : "npm@10.9.0" }"""))
        assertEquals("pnpm", packageManagerName("""{"packageManager":"pnpm@9.12.0+sha512.abc"}"""))
        assertNull(packageManagerName("""{ "name": "web" }"""))
        assertNull(packageManagerName(null))
    }

    @Test
    fun `installs with a frozen lockfile for every manager`() {
        assertEquals(listOf("npm", "ci"), NodeProject(NodePackageManager.NPM, "package-lock.json").installCommand)
        assertEquals(listOf("pnpm", "install", "--frozen-lockfile"), NodeProject(NodePackageManager.PNPM, "pnpm-lock.yaml").installCommand)
        assertEquals(listOf("yarn", "install", "--frozen-lockfile"), chooseNodeProject(setOf("yarn.lock"), null, false)?.installCommand)
        assertEquals(listOf("yarn", "install", "--immutable"), chooseNodeProject(setOf("yarn.lock"), null, true)?.installCommand)
        assertEquals(listOf("bun", "install", "--frozen-lockfile"), NodeProject(NodePackageManager.BUN, "bun.lock").installCommand)
    }

    @Test
    fun `yarn berry only applies to yarn`() {
        assertEquals(false, chooseNodeProject(setOf("package-lock.json"), null, true)?.yarnBerry)
    }

    @Test
    fun `the agent rule names the repository's manager and lockfile`() {
        val pnpm = nodeRuleText(NodeProject(NodePackageManager.PNPM, "pnpm-lock.yaml"))
        val unknown = nodeRuleText(null)

        assertTrue("run `pnpm install` after `package.json` or `pnpm-lock.yaml` change" in pnpm)
        assertTrue("never link or copy node_modules from another worktree" in pnpm)
        assertTrue("`\${port}`" in pnpm)
        assertTrue("run the package manager's install after `package.json` or the lockfile change" in unknown)
    }
}
