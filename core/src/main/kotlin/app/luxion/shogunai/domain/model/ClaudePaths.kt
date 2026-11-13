package app.luxion.shogunai.domain.model

fun claudeSessionsDirectory(configDirectory: String?, userHome: String): String =
    claudeConfigDirectory(configDirectory, userHome) + "/sessions"

fun claudeConfigDirectory(configDirectory: String?, userHome: String): String =
    (configDirectory?.takeIf { it.isNotBlank() } ?: "$userHome/.claude").trimEnd('/')
