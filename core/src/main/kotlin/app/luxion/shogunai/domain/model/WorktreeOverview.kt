package app.luxion.shogunai.domain.model

enum class WorktreeOrigin { SHOGUNAI, CLAUDE_CODE, OTHER }

data class RunningAgent(val name: String)
