package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.CustomPool
import app.luxion.shogunai.domain.model.CustomPoolAcquisition
import app.luxion.shogunai.domain.model.CustomPoolRelease
import app.luxion.shogunai.domain.model.CustomPoolState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant

internal fun poolAcquisitionJson(acquisition: CustomPoolAcquisition): JsonObject = buildJsonObject {
    put("pool", acquisition.pool)
    put("slot", acquisition.slot)
    put("expiresAt", acquisition.expiresAt.toString())
    put("renewed", acquisition.renewed)
    put("takenFrom", acquisition.takenFrom)
    put("waited", acquisition.waitedSeconds)
    putJsonObject("variables") { acquisition.variables.forEach { (name, value) -> put(name, value) } }
}

internal fun poolReleaseJson(release: CustomPoolRelease): JsonObject = buildJsonObject {
    put("pool", release.pool)
    put("slot", release.slot)
    put("released", release.slot != null)
}

internal fun customPoolsJson(states: List<CustomPoolState>): JsonArray = buildJsonArray {
    states.forEach { state ->
        addJsonObject {
            put("name", state.pool.name)
            put("max", state.pool.max)
            putJsonArray("holders") {
                state.holders.forEach { holder ->
                    addJsonObject {
                        put("worktree", holder.lease.worktreePath)
                        put("slot", holder.lease.slot)
                        put("expiresAt", holder.expiresAt.toString())
                        put("agentWorking", holder.note.agentWorking)
                        put("running", holder.running)
                    }
                }
            }
            putJsonArray("waiting") {
                state.waiting.forEach { ticket ->
                    addJsonObject {
                        put("worktree", ticket.worktreePath)
                        put("since", Instant.ofEpochMilli(ticket.since).toString())
                    }
                }
            }
        }
    }
}

internal fun worktreePoolsJson(states: List<CustomPoolState>, worktreePath: String): JsonArray = buildJsonArray {
    states.forEach { state ->
        val held = state.holders.firstOrNull { samePoolPath(it.lease.worktreePath, worktreePath) }
        val position = state.waiting.indexOfFirst { samePoolPath(it.worktreePath, worktreePath) }
        val lost = state.losses.lastOrNull { samePoolPath(it.worktreePath, worktreePath) }
        when {
            held != null -> addJsonObject {
                put("name", state.pool.name)
                put("role", "holder")
                put("slot", held.lease.slot)
                put("expiresAt", held.expiresAt.toString())
            }
            position >= 0 -> addJsonObject {
                put("name", state.pool.name)
                put("role", "waiting")
                put("position", position + 1)
                put("since", Instant.ofEpochMilli(state.waiting[position].since).toString())
            }
            lost != null -> addJsonObject {
                put("name", state.pool.name)
                put("role", "lost")
                put("at", Instant.ofEpochMilli(lost.at).toString())
                put("forWorktree", lost.forWorktree)
            }
        }
    }
}

internal fun trustPoolsJson(pools: List<CustomPool>): JsonArray = buildJsonArray {
    pools.forEach { pool ->
        addJsonObject {
            put("name", pool.name)
            put("max", pool.max)
            put("acquire", JsonArray(pool.acquireSource.map(::JsonPrimitive)))
            put("release", pool.releaseSource?.let { release -> JsonArray(release.map(::JsonPrimitive)) } ?: JsonNull)
            putJsonArray("env") {
                pool.variables.forEach { variable ->
                    addJsonObject {
                        put("name", variable.name)
                        put("template", variable.template.source)
                    }
                }
            }
        }
    }
}

