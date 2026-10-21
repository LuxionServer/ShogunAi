package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.DaemonKeptReason
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonOwner
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.DaemonStopReport
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.DerivedDataTrim
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.GradleDaemon
import app.luxion.shogunai.domain.model.PoolUsage
import app.luxion.shogunai.domain.model.ReleaseRecord
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.ProvisionDrift
import app.luxion.shogunai.domain.model.ProvisionDriftState
import app.luxion.shogunai.domain.model.ProvisionMode
import app.luxion.shogunai.domain.model.ProvisionedFile
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.usecase.GradleTrim
import app.luxion.shogunai.domain.usecase.ResourceAcquisition
import app.luxion.shogunai.domain.usecase.GuideBlockStatus
import app.luxion.shogunai.domain.usecase.HookEntry
import app.luxion.shogunai.domain.usecase.HooksPreview
import app.luxion.shogunai.domain.usecase.HooksStatus
import app.luxion.shogunai.domain.usecase.totalResidentMemory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

fun JsonObject.compact(): String = Json.encodeToString(JsonElement.serializer(), this)

private fun strings(values: Iterable<String>): JsonArray = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }

private fun numbers(values: Iterable<Long>): JsonArray = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }

private fun commands(values: List<List<String>>): JsonArray = buildJsonArray { values.forEach { add(strings(it)) } }

fun postCreateFailureJson(failure: WorktreeError.PostCreateCommandFailed?): JsonElement =
    failure?.let {
        buildJsonObject {
            put("command", strings(it.command))
            put("exitCode", it.exitCode)
            put("stderr", it.errorOutput)
        }
    } ?: JsonNull

fun newJson(
    path: String,
    branch: String?,
    skippedPostCreateCommands: List<List<String>>,
    postCreateFailure: WorktreeError.PostCreateCommandFailed?,
): JsonObject = buildJsonObject {
    put("path", path)
    put("branch", branch)
    put("created", true)
    put("skippedPostCreateCommands", commands(skippedPostCreateCommands))
    put("postCreateFailure", postCreateFailureJson(postCreateFailure))
}

fun provisionJson(
    path: String,
    files: List<ProvisionedFile>,
    skippedPostCreateCommands: List<List<String>>,
    postCreateFailure: WorktreeError.PostCreateCommandFailed?,
): JsonObject = buildJsonObject {
    put("path", path)
    put("copied", strings(files.filter { it.mode == ProvisionMode.COPIED }.map { it.path }))
    put("linked", strings(files.filter { it.mode == ProvisionMode.LINKED }.map { it.path }))
    put("copiedInsteadOfLinking", strings(files.filter { it.mode == ProvisionMode.COPIED_INSTEAD_OF_LINKING }.map { it.path }))
    put("skippedPostCreateCommands", commands(skippedPostCreateCommands))
    put("postCreateFailure", postCreateFailureJson(postCreateFailure))
}

fun provisionCheckJson(path: String, drift: List<ProvisionDrift>): JsonObject = buildJsonObject {
    put("path", path)
    put(
        "drift",
        buildJsonArray {
            drift.forEach {
                add(
                    buildJsonObject {
                        put("path", it.path)
                        put("state", it.state.label)
                    },
                )
            }
        },
    )
}

private val ProvisionDriftState.label: String
    get() = when (this) {
        ProvisionDriftState.MISSING -> "missing"
        ProvisionDriftState.DIFFERS -> "differs"
        ProvisionDriftState.NOT_LINKED -> "not-linked"
    }

fun trustJson(repository: String, commands: List<List<String>>, approved: Boolean, changed: Boolean): JsonObject = buildJsonObject {
    put("repository", repository)
    put("commands", commands(commands))
    put("approved", approved)
    put("changed", changed)
}

data class CleanCandidateJson(val path: String, val branch: String?, val folderMissing: Boolean)

data class CleanKeptJson(val path: String, val agents: List<String>)

data class CleanSkippedJson(val path: String, val reason: String)

data class CleanFailureJson(val path: String, val message: String)

data class CleanJson(
    val dryRun: Boolean,
    val candidates: List<CleanCandidateJson> = emptyList(),
    val keptForAgents: List<CleanKeptJson> = emptyList(),
    val skipped: List<CleanSkippedJson> = emptyList(),
    val removed: List<String> = emptyList(),
    val deletedBranches: List<String> = emptyList(),
    val failures: List<CleanFailureJson> = emptyList(),
    val gradleDaemonsStopped: List<Long> = emptyList(),
    val emulatorsStopped: List<String> = emptyList(),
    val simulatorsDeleted: List<String> = emptyList(),
    val derivedDataDeleted: List<String> = emptyList(),
)

fun cleanJson(clean: CleanJson): JsonObject = buildJsonObject {
    put("dryRun", clean.dryRun)
    put(
        "candidates",
        buildJsonArray {
            clean.candidates.forEach {
                add(
                    buildJsonObject {
                        put("path", it.path)
                        put("branch", it.branch)
                        put("reason", if (it.folderMissing) "folder-missing" else "merged")
                    },
                )
            }
        },
    )
    put(
        "keptForAgents",
        buildJsonArray {
            clean.keptForAgents.forEach {
                add(
                    buildJsonObject {
                        put("path", it.path)
                        put("agents", strings(it.agents))
                    },
                )
            }
        },
    )
    put(
        "skipped",
        buildJsonArray {
            clean.skipped.forEach {
                add(
                    buildJsonObject {
                        put("path", it.path)
                        put("reason", it.reason)
                    },
                )
            }
        },
    )
    put("removed", strings(clean.removed))
    put("deletedBranches", strings(clean.deletedBranches))
    put("failures", failures(clean.failures))
    put(
        "providers",
        buildJsonObject {
            put("gradleDaemonsStopped", numbers(clean.gradleDaemonsStopped))
            put("emulatorsStopped", strings(clean.emulatorsStopped))
            put("simulatorsDeleted", strings(clean.simulatorsDeleted))
            put("derivedDataDeleted", strings(clean.derivedDataDeleted))
        },
    )
}

private fun failures(failures: List<CleanFailureJson>): JsonArray = buildJsonArray {
    failures.forEach {
        add(
            buildJsonObject {
                put("path", it.path)
                put("message", it.message)
            },
        )
    }
}

fun gradleJson(daemons: List<GradleDaemon>, attributed: Boolean): JsonObject = buildJsonObject {
    put(
        "daemons",
        buildJsonArray {
            daemons.forEach { daemon ->
                add(
                    buildJsonObject {
                        val process = daemon.process
                        put("pid", process.pid)
                        put("kind", process.kind.jsonLabel)
                        put("version", process.gradleVersion)
                        put("memoryBytes", process.residentMemoryBytes)
                        put("state", process.state.jsonLabel)
                        put("idleSince", (process.state as? DaemonState.Idle)?.since?.toString())
                        put("worktree", daemon.owner.takeIf { attributed }?.jsonLabel)
                    },
                )
            }
        },
    )
    put("totalMemoryBytes", totalResidentMemory(daemons))
}

fun gradleStopJson(report: DaemonStopReport): JsonObject = buildJsonObject {
    put("stopped", numbers(report.stopped.map { it.pid }))
    put(
        "kept",
        buildJsonArray {
            report.kept.forEach {
                add(
                    buildJsonObject {
                        put("pid", it.pid)
                        put("kind", it.kind.jsonLabel)
                        put("reason", it.reason.jsonLabel)
                    },
                )
            }
        },
    )
    put("notDaemons", numbers(report.notDaemons))
    put(
        "failures",
        buildJsonArray {
            report.failures.forEach {
                add(
                    buildJsonObject {
                        put("pid", it.pid)
                        put("message", it.detail)
                    },
                )
            }
        },
    )
}

val DaemonKind.jsonLabel: String
    get() = when (this) {
        DaemonKind.GRADLE -> "gradle"
        DaemonKind.KOTLIN -> "kotlin"
    }

private val DaemonKeptReason.jsonLabel: String
    get() = when (this) {
        DaemonKeptReason.BUSY -> "busy"
        DaemonKeptReason.STATE_UNKNOWN -> "state-unknown"
        DaemonKeptReason.BUILD_RUNNING -> "build-running"
    }

private val DaemonState.jsonLabel: String
    get() = when (this) {
        DaemonState.Busy -> "busy"
        is DaemonState.Idle -> "idle"
        DaemonState.Unknown -> "unknown"
        DaemonState.Untracked -> "untracked"
    }

private val DaemonOwner?.jsonLabel: String?
    get() = when (this) {
        is DaemonOwner.Worktree -> path
        DaemonOwner.RemovedWorktree -> "removed"
        DaemonOwner.OtherProject -> "other-project"
        null -> null
    }

fun xcodeJson(active: Boolean, listing: DerivedDataListing?): JsonObject = buildJsonObject {
    put("active", active)
    put(
        "folders",
        buildJsonArray {
            listing?.ofWorktrees().orEmpty().forEach {
                add(
                    buildJsonObject {
                        put("path", it.folder.path)
                        put("sizeBytes", it.folder.sizeBytes ?: 0L)
                        put("worktree", (it.owner as DerivedDataOwner.Worktree).path)
                    },
                )
            }
        },
    )
    put("orphans", derivedDataFolders(listing?.orphans.orEmpty()))
    put("unavailableVolumeFolders", listing?.folders?.count { it.owner == DerivedDataOwner.UnavailableVolume } ?: 0)
    put("compilationCacheBytes", listing?.compilationCacheBytes)
}

private fun derivedDataFolders(folders: List<DerivedDataFolder>): JsonArray = buildJsonArray {
    folders.forEach {
        add(
            buildJsonObject {
                put("path", it.path)
                put("sizeBytes", it.sizeBytes ?: 0L)
            },
        )
    }
}

fun xcodeCleanJson(active: Boolean, deletion: DerivedDataDeletion?): JsonObject = buildJsonObject {
    put("active", active)
    put("deleted", strings(deletion?.deleted.orEmpty().map { it.path }))
    put("freedBytes", deletion?.bytesFreed ?: 0L)
    put(
        "failures",
        buildJsonArray {
            deletion?.failures.orEmpty().forEach {
                add(
                    buildJsonObject {
                        put("path", it.path)
                        put("message", it.detail)
                    },
                )
            }
        },
    )
}

fun xcodeTrimJson(active: Boolean, trim: DerivedDataTrim?): JsonObject = buildJsonObject {
    put("active", active)
    put("quotaBytes", trim?.quotaBytes)
    put("dryRun", trim?.dryRun ?: false)
    put("usageBeforeBytes", trim?.usageBefore ?: 0L)
    put("usageAfterBytes", trim?.usageAfter ?: 0L)
    put("exceeded", trim?.isExceeded ?: false)
    put("released", strings(trim?.released.orEmpty().map { it.folder.path }))
    put("freedBytes", trim?.bytesFreed ?: 0L)
    put("inUse", strings(trim?.inUse.orEmpty().map { (it.owner as DerivedDataOwner.Worktree).path }))
    put(
        "failures",
        buildJsonArray {
            trim?.failures.orEmpty().forEach {
                add(
                    buildJsonObject {
                        put("path", it.path)
                        put("message", it.detail)
                    },
                )
            }
        },
    )
}

fun emulatorJson(
    worktree: String,
    emulator: WorktreeEmulator?,
    acquisition: ResourceAcquisition? = null,
    releaseRecord: ReleaseRecord? = null,
): JsonObject = buildJsonObject {
    put("worktree", worktree)
    put("serial", emulator?.serial)
    put("state", emulator?.state?.jsonLabel)
    put("acquired", emulator != null)
    acquisition?.let { put("acquiredNow", it.acquiredNow) }
    acquisition?.let { put("released", strings(it.releases.mapNotNull { release -> release.worktreePath })) }
    releaseRecord?.let {
        put(
            "releasedFor",
            buildJsonObject {
                put("worktree", it.forWorktree)
                put("at", Instant.ofEpochMilli(it.releasedAt).toString())
            },
        )
    }
}

private val EmulatorState.jsonLabel: String
    get() = name.lowercase()

fun simulatorJson(
    worktree: String,
    simulator: WorktreeSimulator?,
    acquisition: ResourceAcquisition? = null,
    releaseRecord: ReleaseRecord? = null,
): JsonObject = buildJsonObject {
    put("worktree", worktree)
    put("udid", simulator?.udid)
    put("name", simulator?.name)
    put("state", simulator?.state?.jsonLabel)
    put("acquired", simulator != null)
    acquisition?.let { put("acquiredNow", it.acquiredNow) }
    acquisition?.let { put("released", strings(it.releases.mapNotNull { release -> release.worktreePath })) }
    releaseRecord?.let {
        put(
            "releasedFor",
            buildJsonObject {
                put("worktree", it.forWorktree)
                put("at", Instant.ofEpochMilli(it.releasedAt).toString())
            },
        )
    }
}

fun poolJson(usages: List<PoolUsage>): JsonObject = buildJsonObject {
    put(
        "pools",
        buildJsonArray {
            usages.forEach { usage ->
                add(
                    buildJsonObject {
                        put("kind", usage.kind.jsonLabel)
                        put("limit", usage.limit)
                        put("effectiveLimit", usage.effectiveLimit)
                        put("inUse", usage.inUse)
                        put("overLimit", usage.isOverLimit)
                        put(
                            "holders",
                            buildJsonArray {
                                usage.holders.forEach { holder ->
                                    add(
                                        buildJsonObject {
                                            put("holder", holder.displayName)
                                            put("worktree", holder.worktreePath)
                                            put("reference", holder.reference)
                                            put("state", holder.state.name.lowercase())
                                            put("idle", holder.idle)
                                            put("lastUsed", holder.lastUsed?.toString())
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }
        },
    )
}

fun poolTrimJson(trim: GradleTrim): JsonObject = buildJsonObject {
    put("limit", trim.limit)
    put("stopped", numbers(trim.stopped.map { it.pid }))
    put(
        "kept",
        buildJsonArray {
            trim.kept.forEach {
                add(
                    buildJsonObject {
                        put("pid", it.pid)
                        put("reason", it.reason.name.lowercase())
                    },
                )
            }
        },
    )
    put("remaining", trim.remaining)
}

val ResourceKind.jsonLabel: String
    get() = when (this) {
        ResourceKind.EMULATOR -> "emulators"
        ResourceKind.IOS_SIMULATOR -> "simulators"
        ResourceKind.GRADLE_DAEMON -> "gradleDaemons"
    }

private val SimulatorState.jsonLabel: String
    get() = name.lowercase()

fun removeJson(
    path: String,
    branch: String?,
    branchDeleted: String?,
    branchKeptReason: String?,
    emulatorsStopped: List<String>,
    simulatorsStopped: List<String>,
    gradleDaemonsStopped: List<Long>,
    failures: List<String>,
): JsonObject = buildJsonObject {
    put("path", path)
    put("branch", branch)
    put("branchDeleted", branchDeleted)
    put("branchKeptReason", branchKeptReason)
    put(
        "providers",
        buildJsonObject {
            put("emulatorsStopped", strings(emulatorsStopped))
            put("simulatorsStopped", strings(simulatorsStopped))
            put("gradleDaemonsStopped", numbers(gradleDaemonsStopped))
        },
    )
    put("failures", strings(failures))
}

fun openJson(path: String, branch: String?, opening: HerdrOpening, agent: String?): JsonObject = buildJsonObject {
    put("path", path)
    put("branch", branch)
    put("herdr", opening.name.lowercase())
    put("agent", agent)
}

fun repairJson(repaired: List<String>): JsonObject = buildJsonObject {
    put("repaired", strings(repaired))
}

fun hooksResultJson(path: String, preview: HooksPreview, applied: Boolean): JsonObject = buildJsonObject {
    put("path", path)
    put("applied", applied)
    put("added", buildJsonArray { preview.added.forEach { add(hookEntryJson(it)) } })
    put("removed", buildJsonArray { preview.removed.forEach { add(hookEntryJson(it)) } })
    put(
        "replaced",
        buildJsonArray {
            preview.replaced.forEach { replacement ->
                add(
                    buildJsonObject {
                        put("event", replacement.event)
                        put("from", buildJsonArray { replacement.from.forEach { add(JsonPrimitive(it)) } })
                        put("to", replacement.to)
                    },
                )
            }
        },
    )
}

private fun hookEntryJson(entry: HookEntry): JsonObject = buildJsonObject {
    put("event", entry.event)
    put("command", entry.command)
}

fun hooksStatusJson(statuses: List<Pair<String, HooksStatus>>): JsonObject = buildJsonObject {
    put("user", JsonNull)
    put("project", JsonNull)
    statuses.forEach { (scope, status) ->
        put(
            scope,
            buildJsonObject {
                put("path", status.path)
                put("state", status.state.name.lowercase())
                put("events", buildJsonArray { status.events.forEach { add(JsonPrimitive(it)) } })
            },
        )
    }
}

fun guideResultJson(status: GuideBlockStatus, applied: Boolean, warnings: List<String>): JsonObject = buildJsonObject {
    put("path", status.path)
    put("applied", applied)
    put("state", status.state.name.lowercase())
    put("warnings", buildJsonArray { warnings.forEach { add(JsonPrimitive(it)) } })
}

fun guideStatusJson(targets: List<Pair<String, GuideBlockStatus>>, warnings: List<String>): JsonObject = buildJsonObject {
    put(
        "targets",
        buildJsonArray {
            targets.forEach { (target, status) ->
                add(
                    buildJsonObject {
                        put("target", target)
                        put("path", status.path)
                        put("state", status.state.name.lowercase())
                        put("duplicates", status.duplicatesOutside)
                    },
                )
            }
        },
    )
    put("warnings", buildJsonArray { warnings.forEach { add(JsonPrimitive(it)) } })
}
