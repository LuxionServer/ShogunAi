package app.luxion.shogunai.ui.worktree

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.io.PostCreateApproval
import app.luxion.shogunai.domain.io.PostCreateDecision
import kotlinx.coroutines.CompletableDeferred

class DialogPostCreateApproval : PostCreateApproval {

    class Request(val repository: String, val commands: List<List<String>>) {
        internal val decision = CompletableDeferred<PostCreateDecision>()
    }

    var pending by mutableStateOf<Request?>(null)
        private set

    override suspend fun decide(repository: String, commands: List<List<String>>): PostCreateDecision {
        val request = Request(repository, commands)
        pending = request
        try {
            return request.decision.await()
        } finally {
            pending = null
        }
    }

    fun resolve(decision: PostCreateDecision) {
        pending?.decision?.complete(decision)
    }
}
