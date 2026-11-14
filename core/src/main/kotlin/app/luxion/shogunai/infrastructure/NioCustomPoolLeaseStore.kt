package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.CustomPoolLeaseStore
import app.luxion.shogunai.domain.model.CustomPoolLease
import app.luxion.shogunai.domain.model.CustomPoolLeases
import app.luxion.shogunai.domain.model.CustomPoolLoss
import app.luxion.shogunai.domain.model.ProcessIdentity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

class NioCustomPoolLeaseStore(
    private val machineDirectory: Path,
) : CustomPoolLeaseStore {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override fun read(): CustomPoolLeases {
        val file = machineDirectory.resolve(LEASE_FILE_NAME)
        if (!file.exists()) return CustomPoolLeases()
        val stored = try {
            json.decodeFromString(LeaseFile.serializer(), file.readText())
        } catch (_: IllegalArgumentException) {
            return CustomPoolLeases()
        }
        return CustomPoolLeases(stored.leases.map { it.toLease() }, stored.losses.map { it.toLoss() })
    }

    override fun write(leases: CustomPoolLeases) {
        val file = machineDirectory.resolve(LEASE_FILE_NAME)
        if (leases.leases.isEmpty() && leases.losses.isEmpty()) {
            Files.deleteIfExists(file)
            return
        }
        Files.createDirectories(machineDirectory)
        val content = LeaseFile(leases.leases.map { it.toEntry() }, leases.losses.map { it.toEntry() })
        val tempFile = Files.createTempFile(machineDirectory, ".$LEASE_FILE_NAME", ".tmp")
        Files.writeString(tempFile, json.encodeToString(LeaseFile.serializer(), content) + "\n")
        Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    @Serializable
    internal data class LeaseFile(val leases: List<LeaseEntry>, val losses: List<LossEntry>)

    @Serializable
    internal data class LeaseEntry(
        val repository: String,
        val repositoryPath: String,
        val pool: String,
        val slot: Int,
        val adminDirectory: String,
        val worktreePath: String,
        val acquiredAt: Long,
        val renewedAt: Long,
        val runningPid: Long?,
        val runningStartedAt: Long?,
    ) {
        fun toLease() = CustomPoolLease(
            repository,
            repositoryPath,
            pool,
            slot,
            adminDirectory,
            worktreePath,
            acquiredAt,
            renewedAt,
            runningPid?.let { ProcessIdentity(it, runningStartedAt) },
        )
    }

    @Serializable
    internal data class LossEntry(
        val repository: String,
        val pool: String,
        val slot: Int,
        val adminDirectory: String,
        val worktreePath: String,
        val at: Long,
        val forWorktree: String,
    ) {
        fun toLoss() = CustomPoolLoss(repository, pool, slot, adminDirectory, worktreePath, at, forWorktree)
    }

    private fun CustomPoolLease.toEntry() = LeaseEntry(
        repository,
        repositoryPath,
        pool,
        slot,
        adminDirectory,
        worktreePath,
        acquiredAt,
        renewedAt,
        runningBy?.pid,
        runningBy?.startedAt,
    )

    private fun CustomPoolLoss.toEntry() = LossEntry(repository, pool, slot, adminDirectory, worktreePath, at, forWorktree)

    companion object {
        const val LEASE_FILE_NAME = "pool-leases.json"
    }
}
