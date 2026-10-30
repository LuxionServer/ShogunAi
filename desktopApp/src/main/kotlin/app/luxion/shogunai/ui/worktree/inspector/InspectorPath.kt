package app.luxion.shogunai.ui.worktree.inspector

fun displayPath(path: String, worktreesRoot: String, home: String): String {
    val target = normalized(path)
    val homeFolder = normalized(home)
    target.relativeTo(normalized(expandedHome(worktreesRoot, homeFolder)))?.let { return it }
    target.relativeTo(homeFolder)?.let { return "~/$it" }
    return path
}

private fun normalized(path: String): String = path.replace('\\', '/').trimEnd('/')

private fun expandedHome(path: String, home: String): String = when {
    path == "~" -> home
    path.startsWith("~/") || path.startsWith("~\\") -> home + "/" + path.drop(2)
    else -> path
}

private fun String.relativeTo(folder: String): String? =
    if (folder.isNotEmpty() && startsWith("$folder/")) removePrefix("$folder/") else null
