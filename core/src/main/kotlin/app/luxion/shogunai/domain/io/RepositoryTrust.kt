package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.RepositoryApprovalSubject

interface RepositoryTrust {

    fun isApproved(repository: String, subject: RepositoryApprovalSubject): Boolean

    fun approve(repository: String, subject: RepositoryApprovalSubject)

    fun revoke(repository: String)

    companion object {
        val NONE: RepositoryTrust = object : RepositoryTrust {
            override fun isApproved(repository: String, subject: RepositoryApprovalSubject): Boolean = subject.isEmpty

            override fun approve(repository: String, subject: RepositoryApprovalSubject) = Unit

            override fun revoke(repository: String) = Unit
        }
    }
}

enum class ApprovalDecision { APPROVE, SKIP }

fun interface RepositoryApproval {

    suspend fun decide(repository: String, subject: RepositoryApprovalSubject): ApprovalDecision

    companion object {
        val SKIP: RepositoryApproval = RepositoryApproval { _, _ -> ApprovalDecision.SKIP }
    }
}
