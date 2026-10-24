package app.luxion.shogunai.domain.model

import java.time.Instant

data class DerivedDataFolder(
    val path: String,
    val workspacePath: String,
    val sizeBytes: Long?,
    val lastAccessed: Instant?,
)

data class RememberedSize(val sizeBytes: Long, val lastAccessed: Instant)

data class MeasuredSize(val path: String, val sizeBytes: Long?, val provisional: Boolean = false)

sealed interface DerivedDataOwner {
    data class Worktree(val path: String) : DerivedDataOwner
    data object OtherProject : DerivedDataOwner
    data object Orphaned : DerivedDataOwner
    data object UnavailableVolume : DerivedDataOwner
}

data class AttributedDerivedData(val folder: DerivedDataFolder, val owner: DerivedDataOwner)

data class DerivedDataListing(
    val folders: List<AttributedDerivedData>,
    val compilationCacheBytes: Long?,
) {
    val orphans: List<DerivedDataFolder> get() = folders.filter { it.owner == DerivedDataOwner.Orphaned }.map { it.folder }

    fun ofWorktrees(): List<AttributedDerivedData> = folders.filter { it.owner is DerivedDataOwner.Worktree }
}

data class DerivedDataDeletionFailure(val path: String, val detail: String)

data class DerivedDataDeletion(
    val deleted: List<DerivedDataFolder> = emptyList(),
    val failures: List<DerivedDataDeletionFailure> = emptyList(),
) {
    val bytesFreed: Long get() = deleted.sumOf { it.sizeBytes ?: 0L }
}

data class DerivedDataQuota(val mode: DerivedDataQuotaMode, val bytes: Long?) {
    val isUnknown: Boolean get() = mode == DerivedDataQuotaMode.AUTOMATIC && bytes == null
}

data class DerivedDataTrim(
    val quotaBytes: Long?,
    val usageBefore: Long,
    val usageAfter: Long,
    val released: List<AttributedDerivedData> = emptyList(),
    val inUse: List<AttributedDerivedData> = emptyList(),
    val failures: List<DerivedDataDeletionFailure> = emptyList(),
    val dryRun: Boolean = false,
    val quotaMode: DerivedDataQuotaMode = DerivedDataQuotaMode.FIXED,
) {
    val bytesFreed: Long get() = released.sumOf { it.folder.sizeBytes ?: 0L }
    val isExceeded: Boolean get() = quotaBytes != null && usageAfter > quotaBytes
}
