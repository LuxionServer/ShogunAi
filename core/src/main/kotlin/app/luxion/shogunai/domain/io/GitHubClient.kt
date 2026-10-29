package app.luxion.shogunai.domain.io

interface GitHubClient {

    suspend fun pullRequestFor(directory: String, branch: String): Int?

    suspend fun issueExists(directory: String, number: Int): Boolean

    companion object {
        val NONE = object : GitHubClient {
            override suspend fun pullRequestFor(directory: String, branch: String): Int? = null

            override suspend fun issueExists(directory: String, number: Int): Boolean = false
        }
    }
}
