package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.NodeDependencies
import app.luxion.shogunai.domain.model.NodeDependenciesFailure
import app.luxion.shogunai.domain.model.NodePackageManager
import app.luxion.shogunai.domain.model.NodeProject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NodeDependenciesTextTest {

    private val pnpm = NodeProject(NodePackageManager.PNPM, "pnpm-lock.yaml")

    private fun json(dependencies: NodeDependencies): JsonObject = nodeJson(dependencies).getValue("dependencies").jsonObject

    @Test
    fun `cloned dependencies name their folders`() {
        val cloned = NodeDependencies.Cloned(NodePackageManager.PNPM, listOf("node_modules", "packages/api/node_modules"))

        assertEquals("node dependencies: node_modules, packages/api/node_modules cloned from the base repository (pnpm)", nodeDependenciesLine(cloned))
        assertEquals("cloned", json(cloned).getValue("status").jsonPrimitive.content)
        assertEquals("pnpm", json(cloned).getValue("manager").jsonPrimitive.content)
        assertEquals(listOf("node_modules", "packages/api/node_modules"), json(cloned).getValue("folders").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `present dependencies have only a status`() {
        assertEquals("node dependencies: node_modules already present", nodeDependenciesLine(NodeDependencies.Present))
        assertEquals(setOf("status"), json(NodeDependencies.Present).keys)
        assertEquals("present", json(NodeDependencies.Present).getValue("status").jsonPrimitive.content)
    }

    @Test
    fun `needed dependencies without a manager have no command`() {
        val needed = NodeDependencies.Needed(null, NodeDependenciesFailure.Unexpected(".shogunai.json is invalid"))

        assertEquals("node dependencies needed: .shogunai.json is invalid", nodeDependenciesLine(needed))
        assertFalse("command" in json(needed))
        assertFalse("manager" in json(needed))
    }

    @Test
    fun `reasons`() {
        assertEquals("pnpm wasn't found", nodeDependenciesReason(NodeDependenciesFailure.NotFound("pnpm")))
        assertEquals("exited with code 1", nodeDependenciesReason(NodeDependenciesFailure.Exited(1, emptyList())))
        assertEquals("exited with code 1: a | b", nodeDependenciesReason(NodeDependenciesFailure.Exited(1, listOf("a", "b"))))
        assertEquals("there is no lockfile in the main folder", nodeDependenciesReason(NodeDependenciesFailure.NoLockfile))
        assertEquals(
            "node dependencies needed: pnpm wasn't found; run 'pnpm install --frozen-lockfile' in the worktree",
            nodeDependenciesLine(NodeDependencies.Needed(pnpm, NodeDependenciesFailure.NotFound("pnpm"))),
        )
    }
}
