package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RecentWorktree(val projectId: String, val path: String)
