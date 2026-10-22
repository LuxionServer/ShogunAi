package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGitRepositories
import app.luxion.shogunai.domain.FakeMultirepoSource
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig

class MultirepoTestWorld {
    val folder = "/home/dev/projects/product"
    val worktreesRoot = "/home/dev/projects/product-worktrees"
    val appBase = "$folder/product-app"
    val apiBase = "$folder/product-api"
    val docsBase = "$folder/product-docs"
    val git = FakeGitRepositories()
    val app = git.repository(appBase)
    val api = git.repository(apiBase)
    val docs = git.repository(docsBase, mainBranch = "main")
    val declaration = MultirepoDeclaration(
        repos = linkedMapOf("app" to "product-app", "api" to "product-api", "docs" to "product-docs"),
        worktreesRoot = "../product-worktrees",
        defaultRepos = listOf("app", "api"),
    )
    val source = FakeMultirepoSource(mapOf(folder to declaration))
    val fileManager = FakeFileManager(existing = listOf(folder, appBase, apiBase, docsBase))
    val appProject = Project("app", "product-app", ProjectConfig(appBase, "$worktreesRoot/app", emptyList()))
    val apiProject = Project("api", "product-api", ProjectConfig(apiBase, "$worktreesRoot/api", emptyList()))
    val docsProject = Project("docs", "product-docs", ProjectConfig(docsBase, "$worktreesRoot/docs", emptyList()))
    val projects = FakeProjectRepository(listOf(appProject, apiProject, docsProject))
    val multirepo = Multirepo(folder, declaration)
    val resolver = ResolveMultirepoUseCase(source, git, fileManager)

    fun members(): List<MultirepoMember> = multirepoMembers(multirepo, projects.list())

    fun member(alias: String): MultirepoMember = members().first { it.alias == alias }
}
