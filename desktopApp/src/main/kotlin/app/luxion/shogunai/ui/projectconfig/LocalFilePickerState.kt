package app.luxion.shogunai.ui.projectconfig

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.model.LocalFileCandidate
import app.luxion.shogunai.domain.model.isLocalPattern
import app.luxion.shogunai.ui.isAbsolutePath

sealed interface LocalFileCandidatesLoad {
    data object Loading : LocalFileCandidatesLoad
    data class Failed(val error: Throwable) : LocalFileCandidatesLoad
    data class Loaded(val candidates: List<LocalFileCandidate>) : LocalFileCandidatesLoad
}

data class LocalFileSelection(val added: List<String>, val removed: List<String>, val patterns: List<String>)

class LocalFilePickerState(private val declared: Set<String>) {
    var load by mutableStateOf<LocalFileCandidatesLoad>(LocalFileCandidatesLoad.Loading)
    var filter by mutableStateOf("")
    var checked by mutableStateOf(declared)
        private set
    var patterns by mutableStateOf(emptyList<String>())
        private set

    private val candidates: List<LocalFileCandidate>
        get() = (load as? LocalFileCandidatesLoad.Loaded)?.candidates.orEmpty()
            .sortedWith(compareBy<LocalFileCandidate> { it.secretKind == null }.thenBy { it.path })

    val visible: List<LocalFileCandidate>
        get() {
            val text = filter.trim()
            return when {
                text.isEmpty() -> candidates
                isLocalPattern(text) -> candidates.filter { matchesLocalPattern(text, it.path) }
                else -> candidates.filter { it.path.contains(text, ignoreCase = true) }
            }
        }

    val filterPattern: String?
        get() = filter.trim().takeIf { isLocalPattern(it) }

    val isFilterPatternAbsolute: Boolean
        get() = filterPattern?.let(::isAbsolutePath) == true

    val offeredPattern: String?
        get() = filterPattern?.takeIf { !isAbsolutePath(it) && it !in declared && it !in patterns }

    val selectedCount: Int
        get() = candidates.count { isChecked(it) } + patterns.size

    fun isChecked(candidate: LocalFileCandidate): Boolean = candidate.path in checked

    fun setChecked(candidate: LocalFileCandidate, value: Boolean) {
        if (candidate.providedByRepository) return
        checked = if (value) checked + candidate.path else checked - candidate.path
    }

    fun addOfferedPattern() {
        val pattern = offeredPattern ?: return
        patterns = patterns + pattern
        filter = ""
    }

    fun removePattern(pattern: String) {
        patterns = patterns - pattern
    }

    fun selection(): LocalFileSelection {
        val listed = candidates.filterNot { it.providedByRepository }.map { it.path }
        return LocalFileSelection(
            added = listed.filter { it in checked && it !in declared },
            removed = listed.filter { it in declared && it !in checked },
            patterns = patterns,
        )
    }
}

internal fun matchesLocalPattern(pattern: String, path: String): Boolean {
    val trimmed = pattern.removeSuffix("/")
    val anchored = trimmed.startsWith("/")
    val body = trimmed.removePrefix("/")
    val target = if (anchored || '/' in body) path else path.substringAfterLast('/')
    return globRegex(body).matches(target)
}

private fun globRegex(glob: String): Regex {
    val regex = StringBuilder()
    var index = 0
    while (index < glob.length) {
        val char = glob[index]
        when {
            glob.startsWith("**/", index) -> {
                regex.append("(?:.*/)?")
                index += 2
            }
            glob.startsWith("**", index) -> {
                regex.append(".*")
                index++
            }
            char == '*' -> regex.append("[^/]*")
            char == '?' -> regex.append("[^/]")
            char == '[' && glob.indexOf(']', index + 2) > 0 -> {
                val end = glob.indexOf(']', index + 2)
                val content = glob.substring(index + 1, end)
                val negated = content.startsWith("!") || content.startsWith("^")
                val members = if (negated) content.drop(1) else content
                regex.append('[').append(if (negated) "^" else "").append(members.replace("\\", "\\\\").replace("[", "\\[")).append(']')
                index = end
            }
            else -> regex.append(Regex.escape(char.toString()))
        }
        index++
    }
    return Regex(regex.toString())
}
