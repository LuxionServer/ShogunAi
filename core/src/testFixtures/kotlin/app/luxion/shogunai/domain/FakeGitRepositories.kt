package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import kotlin.time.Duration

class FakeGitRepositories : ShellCommandExecutor {

    class Repository(val base: String, var mainBranch: String) {
        val worktrees = linkedMapOf<String, String?>()
        val branches = mutableSetOf<String>()
        val remoteBranches = mutableSetOf<String>()
        val merged = mutableSetOf<String>()
        val mergedOnlyInto = mutableMapOf<String, String>()
        val recordedBases = linkedMapOf<String, String>()
        val dirty = mutableMapOf<String, Int>()
        var failAdd: String? = null
    }

    val repositories = mutableListOf<Repository>()
    val calls = mutableListOf<Pair<List<String>, String?>>()
    var lsof: String = ""
    var ps: String = ""

    fun repository(base: String, mainBranch: String = "develop", remote: Boolean = true): Repository =
        Repository(base, mainBranch).also {
            it.branches += mainBranch
            if (remote) it.remoteBranches += mainBranch
            repositories += it
        }

    fun Repository.worktree(path: String, branch: String?, merged: Boolean = false): Repository {
        worktrees[path] = branch
        branch?.let { branches += it }
        if (merged) this.merged += path
        return this
    }

    fun ran(vararg args: String): Boolean = calls.any { it.first == args.toList() }

    fun ranIn(directory: String, vararg args: String): Boolean = calls.any { it.first == args.toList() && it.second == directory }

    override suspend fun executeWithin(command: List<String>, timeout: Duration, workingDirectory: String?): CommandResult =
        execute(command, workingDirectory)

    override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
        calls += command to workingDirectory
        if (command.firstOrNull() == "lsof") return ok(command, lsof)
        if (command.firstOrNull() == "ps") return ok(command, ps)
        if (command.firstOrNull() != "git") return ok(command)
        val args = command.drop(1).filterNot { it == "--no-optional-locks" }
        val directory = workingDirectory?.trimEnd('/') ?: return fail(command)
        val repository = owner(directory) ?: return fail(command, "fatal: not a git repository")
        return when {
            "--git-common-dir" in args -> ok(command, "${repository.base}/.git\nfalse\n")
            args.take(2) == listOf("worktree", "list") -> ok(command, porcelain(repository))
            args.take(1) == listOf("show-ref") -> exists(command, repository, args.last())
            args.take(2) == listOf("rev-parse", "--verify") && !args.last().startsWith("refs/") -> ok(command, "abc123\n")
            args.take(2) == listOf("rev-parse", "--verify") -> exists(command, repository, args.last())
            args.take(1) == listOf("symbolic-ref") ->
                if ("origin/${repository.mainBranch}".removePrefix("origin/") in repository.remoteBranches) ok(command, "origin/${repository.mainBranch}\n") else fail(command)
            args.take(2) == listOf("worktree", "add") -> add(command, repository, args.drop(2))
            args.take(2) == listOf("worktree", "remove") -> {
                repository.worktrees.remove(args.last().trimEnd('/'))
                ok(command)
            }
            args.take(1) == listOf("status") -> ok(command, " M file\n".repeat(repository.dirty[directory] ?: 0))
            args.take(1) == listOf("rev-list") && args.last().endsWith("@{upstream}...HEAD") -> fail(command)
            args.take(1) == listOf("rev-list") -> ok(command, if (readsAsMerged(repository, directory, args.last())) "3\t0" else "0\t2")
            args.take(2) == listOf("config", "--get-regexp") ->
                ok(command, repository.recordedBases.entries.joinToString("") { (branch, base) -> "branch.$branch.gh-merge-base $base\n" })
            args.take(2) == listOf("branch", "-d") || args.take(2) == listOf("branch", "-D") -> {
                repository.branches.remove(args.last())
                ok(command)
            }
            args.take(1) == listOf("fetch") -> ok(command)
            else -> ok(command)
        }
    }

    private fun readsAsMerged(repository: Repository, directory: String, range: String): Boolean =
        repository.mergedOnlyInto[directory]?.let { range.substringBefore("...") == it } ?: (directory in repository.merged)

    private fun owner(directory: String): Repository? =
        repositories.flatMap { repository -> (listOf(repository.base) + repository.worktrees.keys).map { it to repository } }
            .filter { (path, _) -> directory == path || directory.startsWith("$path/") }
            .maxByOrNull { (path, _) -> path.length }
            ?.second

    private fun exists(command: List<String>, repository: Repository, ref: String): CommandResult = when {
        ref.startsWith("refs/heads/") && ref.removePrefix("refs/heads/") in repository.branches -> ok(command)
        ref.startsWith("refs/remotes/origin/") && ref.removePrefix("refs/remotes/origin/") in repository.remoteBranches -> ok(command)
        else -> fail(command)
    }

    private fun add(command: List<String>, repository: Repository, args: List<String>): CommandResult {
        repository.failAdd?.let { return fail(command, it) }
        val options = args.filter { it.startsWith("-") }
        val positional = args.filterNot { it.startsWith("-") }
        val branchIndex = args.indexOf("-b")
        val newBranch = if (branchIndex >= 0) args[branchIndex + 1] else null
        val path = positional.first { it != newBranch }
        val branch = newBranch ?: positional.last()
        if ("--track" in options || newBranch != null) repository.branches += branch
        repository.worktrees[path.trimEnd('/')] = branch
        return ok(command)
    }

    private fun porcelain(repository: Repository): String = buildString {
        appendLine("worktree ${repository.base}")
        appendLine("HEAD 1111111111111111111111111111111111111111")
        appendLine("branch refs/heads/${repository.mainBranch}")
        repository.worktrees.forEach { (path, branch) ->
            appendLine()
            appendLine("worktree $path")
            appendLine("HEAD 2222222222222222222222222222222222222222")
            if (branch == null) appendLine("detached") else appendLine("branch refs/heads/$branch")
        }
    }

    private fun ok(command: List<String>, stdout: String = "") = CommandResult(command, 0, stdout, "")

    private fun fail(command: List<String>, stderr: String = "boom") = CommandResult(command, 1, "", stderr)
}
