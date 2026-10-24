package app.luxion.shogunai.ui.worktree

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.io.RepositoryApproval
import app.luxion.shogunai.domain.io.ApprovalDecision
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import kotlinx.coroutines.CompletableDeferred

class DialogRepositoryApproval : RepositoryApproval {

    class Request(val repository: String, val subject: RepositoryApprovalSubject) {
        internal val decision = CompletableDeferred<ApprovalDecision>()
    }

    var pending by mutableStateOf<Request?>(null)
        private set

    override suspend fun decide(repository: String, subject: RepositoryApprovalSubject): ApprovalDecision {
        val request = Request(repository, subject)
        pending = request
        try {
            return request.decision.await()
        } finally {
            pending = null
        }
    }

    fun resolve(decision: ApprovalDecision) {
        pending?.decision?.complete(decision)
    }
}
