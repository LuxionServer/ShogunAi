package app.luxion.shogunai.domain.model

data class ClaudeSession(val pid: Long, val cwd: String, val name: String?, val startedAt: Long? = null)

data class ClaudeSessionRef(val name: String, val pid: Long)
