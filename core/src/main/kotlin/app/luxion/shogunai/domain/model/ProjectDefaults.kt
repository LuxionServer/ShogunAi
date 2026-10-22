package app.luxion.shogunai.domain.model

data class ProjectDefaults(val name: String, val worktreesRoot: String)

fun projectDefaultsFor(repositoryTopLevel: String): ProjectDefaults {
    val topLevel = repositoryTopLevel.trimEnd('/', '\\')
    val name = topLevel.substringAfterLast('/').substringAfterLast('\\')
    val parent = topLevel.dropLast(name.length).trimEnd('/', '\\')
    return ProjectDefaults(name, "$parent/$name-worktrees")
}

fun newProject(id: String, repositoryTopLevel: String): Project {
    val defaults = projectDefaultsFor(repositoryTopLevel)
    return Project(
        id = id,
        name = defaults.name,
        config = ProjectConfig(
            baseRepositoryPath = repositoryTopLevel,
            worktreesRoot = defaults.worktreesRoot,
            secretFiles = emptyList(),
        ),
    )
}
