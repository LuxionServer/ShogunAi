package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataDeletionFailure
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataKind
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.DerivedDataTrim
import app.luxion.shogunai.domain.model.SwiftBuildDeletion
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun xcodeJson(active: Boolean, listing: DerivedDataListing?): JsonObject = buildJsonObject {
    put("active", active)
    put(
        "folders",
        buildJsonArray {
            listing?.ofWorktrees().orEmpty().forEach {
                add(
                    buildJsonObject {
                        put("kind", it.folder.kind.jsonLabel)
                        put("path", it.folder.path)
                        put("sizeBytes", it.folder.sizeOrZero())
                        put("sourcePackagesBytes", it.folder.packagesBytes)
                        put("worktree", (it.owner as DerivedDataOwner.Worktree).path)
                    },
                )
            }
        },
    )
    put("orphans", derivedDataFolders(listing?.orphans.orEmpty()))
    put("unavailableVolumeFolders", listing?.folders.orEmpty().count { it.owner == DerivedDataOwner.UnavailableVolume })
    put("compilationCacheBytes", listing?.compilationCacheBytes)
    put("swiftPmCacheBytes", listing?.let { it.packageCaches.swiftPmBytes })
    put("cocoaPodsCacheBytes", listing?.let { it.packageCaches.cocoaPodsBytes })
}

private fun derivedDataFolders(folders: List<DerivedDataFolder>): JsonArray = buildJsonArray {
    folders.forEach {
        add(
            buildJsonObject {
                put("path", it.path)
                put("sizeBytes", it.sizeOrZero())
                put("sourcePackagesBytes", it.packagesBytes)
            },
        )
    }
}

internal fun DerivedDataFolder.sizeOrZero(): Long = sizeBytes ?: 0L

fun xcodeCleanJson(active: Boolean, deletion: DerivedDataDeletion?): JsonObject = buildJsonObject {
    put("active", active)
    put("deleted", strings(deletion?.deleted.orEmpty().map { it.path }))
    put("freedBytes", deletion?.bytesFreed ?: 0L)
    put("failures", failuresJson(deletion?.failures.orEmpty()))
}

fun xcodeTrimJson(active: Boolean, trim: DerivedDataTrim?): JsonObject = buildJsonObject {
    put("active", active)
    put("quotaMode", trim?.let { it.quotaMode.name.lowercase() })
    put("quotaBytes", trim?.quotaBytes)
    put("dryRun", trim?.dryRun ?: false)
    put("usageBeforeBytes", trim?.usageBefore ?: 0L)
    put("usageAfterBytes", trim?.usageAfter ?: 0L)
    put("exceeded", trim?.isExceeded ?: false)
    put("released", strings(trim?.released.orEmpty().map { it.folder.path }))
    put("freedBytes", trim?.bytesFreed ?: 0L)
    put("inUse", strings(trim?.inUse.orEmpty().map { (it.owner as DerivedDataOwner.Worktree).path }))
    put("failures", failuresJson(trim?.failures.orEmpty()))
}

fun xcodeCleanSwiftBuildJson(active: Boolean, deletion: SwiftBuildDeletion?): JsonObject = buildJsonObject {
    put("active", active)
    put("deleted", strings(deletion?.deleted.orEmpty().map { it.path }))
    put("freedBytes", deletion?.bytesFreed ?: 0L)
    put("inUse", strings(deletion?.inUse.orEmpty().map { it.path }))
    put("failures", failuresJson(deletion?.failures.orEmpty()))
}

private fun failuresJson(failures: List<DerivedDataDeletionFailure>): JsonArray = buildJsonArray {
    failures.forEach {
        add(
            buildJsonObject {
                put("path", it.path)
                put("message", it.detail)
            },
        )
    }
}

private val DerivedDataKind.jsonLabel: String
    get() = when (this) {
        DerivedDataKind.XCODE -> "derived-data"
        DerivedDataKind.SWIFT_BUILD -> "swift-build"
    }
