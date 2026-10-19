package app.luxion.shogunai.domain.io

interface RepositoryTrust {

    fun isApproved(repository: String, commands: List<List<String>>): Boolean

    fun approve(repository: String, commands: List<List<String>>)

    fun revoke(repository: String)

    companion object {
        val NONE: RepositoryTrust = object : RepositoryTrust {
            override fun isApproved(repository: String, commands: List<List<String>>): Boolean = false

            override fun approve(repository: String, commands: List<List<String>>) = Unit

            override fun revoke(repository: String) = Unit
        }
    }
}

enum class PostCreateDecision { RUN, SKIP }

fun interface PostCreateApproval {

    suspend fun decide(repository: String, commands: List<List<String>>): PostCreateDecision

    companion object {
        val SKIP: PostCreateApproval = PostCreateApproval { _, _ -> PostCreateDecision.SKIP }
    }
}
