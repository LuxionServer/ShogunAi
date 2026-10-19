package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.PostCreateApproval
import app.luxion.shogunai.domain.io.PostCreateDecision

class FakePostCreateApproval(private val decision: PostCreateDecision) : PostCreateApproval {

    val asked = mutableListOf<Pair<String, List<List<String>>>>()

    override suspend fun decide(repository: String, commands: List<List<String>>): PostCreateDecision {
        asked += repository to commands
        return decision
    }
}
