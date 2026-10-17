package app.luxion.shogunai.domain.model

enum class WorktreeOrigin { SHOGUNAI, CLAUDE_CODE, HERDR, OTHER }

data class RunningAgent(val name: String)
