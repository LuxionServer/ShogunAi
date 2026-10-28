package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.ClaudeSession

interface ClaudeSessionRegistry {

    fun list(): List<ClaudeSession>
}
