package app.luxion.shogunai.ui.multirepo

import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.usecase.AddedRepository
import app.luxion.shogunai.domain.usecase.MultirepoMember
import app.luxion.shogunai.domain.usecase.RegisteredMultirepo
import app.luxion.shogunai.domain.usecase.WorktreeGroup
import java.nio.file.Path

data class FolderInspection(val enclosingRepository: String?, val repositories: List<String>)

interface MultirepoSetup {
    suspend fun inspect(folder: String): FolderInspection
    suspend fun isRepository(path: String): Boolean
    suspend fun belongsToRepository(path: String): Boolean
    suspend fun detectGroups(members: List<MultirepoMember>): List<WorktreeGroup>
    fun projects(): List<Project>
    fun read(folder: String): Multirepo?
    suspend fun init(folder: String): Result<Multirepo>
    suspend fun trust(multirepo: Multirepo): Result<Multirepo>
    suspend fun register(reviewed: Multirepo): Result<RegisteredMultirepo>
    fun setName(multirepo: Multirepo, name: String?): Result<Multirepo>
    suspend fun addRepository(multirepo: Multirepo, alias: String, path: String, useProposedFolder: Boolean): Result<AddedRepository>
    fun removeRepository(multirepo: Multirepo, alias: String): Result<Multirepo>
    fun renameRepository(multirepo: Multirepo, alias: String, newAlias: String): Result<Multirepo>
    fun setDefaults(multirepo: Multirepo, aliases: List<String>): Result<Multirepo>
    fun setWorkspace(multirepo: Multirepo, alias: String, default: Boolean): Result<Multirepo>
    fun clearWorkspace(multirepo: Multirepo): Result<Multirepo>
    fun detectWorkspace(multirepo: Multirepo): Result<Multirepo>
    fun setSharedVariables(multirepo: Multirepo, byAlias: Map<String, Map<String, String>>): Result<Multirepo>
    fun setAgent(multirepo: Multirepo, agent: AgentLaunchConfig): Result<Multirepo>
    fun setWorktreesRoot(multirepo: Multirepo, folder: String): Result<Multirepo>
    fun delete(multirepo: Multirepo): Result<Unit>
}

fun sameFolder(first: String, second: String): Boolean = realPath(first) == realPath(second)

private fun realPath(path: String): String =
    runCatching { Path.of(path).toRealPath().toString() }
        .getOrElse { Path.of(path).toAbsolutePath().normalize().toString() }
        .replace('\\', '/')
        .trimEnd('/')
