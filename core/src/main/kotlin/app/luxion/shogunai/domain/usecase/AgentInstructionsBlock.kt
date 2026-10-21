package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.FileManager

enum class GuideBlockState { INSTALLED, OUTDATED, MISSING }

data class GuideBlockStatus(val path: String, val state: GuideBlockState, val duplicatesOutside: Boolean) {
    val hasBlock: Boolean get() = state != GuideBlockState.MISSING
}

class AgentInstructionsBlock(private val fileManager: FileManager) {

    fun status(path: String): GuideBlockStatus {
        val text = fileManager.readText(path).orEmpty()
        val match = BLOCK.find(text)
        val state = when {
            match == null -> GuideBlockState.MISSING
            match.value == current() -> GuideBlockState.INSTALLED
            else -> GuideBlockState.OUTDATED
        }
        val outside = if (match == null) text else text.removeRange(match.range)
        return GuideBlockStatus(path, state, DUPLICATED_WORKFLOW.containsMatchIn(outside))
    }

    fun install(path: String): GuideBlockStatus {
        val text = fileManager.readText(path).orEmpty()
        val match = BLOCK.find(text)
        val updated = when {
            match != null -> text.replaceRange(match.range, current())
            text.isEmpty() -> current() + "\n"
            else -> text + (if (text.endsWith("\n")) "\n" else "\n\n") + current() + "\n"
        }
        if (updated != text) fileManager.writeText(path, updated)
        return status(path)
    }

    fun uninstall(path: String): GuideBlockStatus {
        val text = fileManager.readText(path).orEmpty()
        val match = BLOCK.find(text) ?: return status(path)
        val end = if (text.startsWith("\n", match.range.last + 1)) match.range.last + 2 else match.range.last + 1
        val preceding = text.substring(0, match.range.first)
        val before = if (preceding.endsWith("\n\n")) preceding.dropLast(1) else preceding
        fileManager.writeText(path, before + text.substring(end))
        return status(path)
    }

    companion object {
        const val VERSION = 1

        private const val OPEN = "<!-- shogun:guide v$VERSION -->"
        private const val CLOSE = "<!-- /shogun:guide -->"
        private const val BODY = "ShogunAi's workflow rules for agents (worktrees, parallel sessions, shared resources, cleanup): run `shogun guide`."

        private val BLOCK = Regex("<!-- shogun:guide v\\d+ -->.*?<!-- /shogun:guide -->", RegexOption.DOT_MATCHES_ALL)
        private val DUPLICATED_WORKFLOW = Regex("shogun (new|open|clean)\\b")

        fun current(): String = "$OPEN\n$BODY\n$CLOSE"
    }
}
