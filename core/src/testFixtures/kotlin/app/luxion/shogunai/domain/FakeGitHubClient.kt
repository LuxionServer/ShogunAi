package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.GitHubClient

class FakeGitHubClient(
    private val pullRequests: Map<String, Int> = emptyMap(),
    private val issues: Set<Int> = emptySet(),
) : GitHubClient {

    val pullRequestLookups = mutableListOf<Pair<String, String>>()
    val issueLookups = mutableListOf<Pair<String, Int>>()

    override suspend fun pullRequestFor(directory: String, branch: String): Int? {
        pullRequestLookups += directory to branch
        return pullRequests[branch]
    }

    override suspend fun issueExists(directory: String, number: Int): Boolean {
        issueLookups += directory to number
        return number in issues
    }
}
