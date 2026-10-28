package app.luxion.shogunai.domain.model

data class ReleaseNotes(val english: String?, val spanish: String?) {

    fun forLanguage(language: AppLanguage): String? = when (language) {
        AppLanguage.ENGLISH -> english
        AppLanguage.SPANISH -> spanish ?: english
    }

    companion object {
        val NONE = ReleaseNotes(english = null, spanish = null)

        private val GeneratedChangeSuffix = Regex("""\s+by\s+@\S+\s+in\s+https?://\S+\s*$""")
        private val GeneratedHeading = Regex("""^#+\s*What's Changed\s*$""", RegexOption.IGNORE_CASE)
        private val NewContributorsHeading = Regex("""^#+\s*New Contributors\s*$""", RegexOption.IGNORE_CASE)
        private val FullChangelogLine = Regex("""^\*\*Full Changelog\*\*.*$""", RegexOption.IGNORE_CASE)
        private val HtmlComment = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
        private val ExtraBlankLines = Regex("""\n{3,}""")

        fun parse(body: String?): ReleaseNotes {
            val text = body.orEmpty().replace("\r\n", "\n")
            val english = text.between("en")
            val spanish = text.between("es")
            if (english != null || spanish != null) return ReleaseNotes(english.nonBlank(), spanish.nonBlank())
            return ReleaseNotes(cleanGenerated(text).nonBlank(), spanish = null)
        }

        private fun String.between(language: String): String? =
            Regex("""<!--\s*shogunai:notes:$language\s*-->(.*?)<!--\s*/shogunai:notes:$language\s*-->""", RegexOption.DOT_MATCHES_ALL)
                .find(this)
                ?.groupValues
                ?.get(1)

        private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

        private fun cleanGenerated(text: String): String {
            var inNewContributors = false
            val kept = text.replace(HtmlComment, "").lines().mapNotNull { line ->
                val trimmed = line.trim()
                when {
                    NewContributorsHeading.matches(trimmed) -> {
                        inNewContributors = true
                        null
                    }
                    inNewContributors && trimmed.startsWith("#") -> {
                        inNewContributors = false
                        if (GeneratedHeading.matches(trimmed)) null else line
                    }
                    inNewContributors -> null
                    GeneratedHeading.matches(trimmed) -> null
                    FullChangelogLine.matches(trimmed) -> null
                    else -> line.replace(GeneratedChangeSuffix, "")
                }
            }
            return kept.joinToString("\n").replace(ExtraBlankLines, "\n\n")
        }
    }
}

data class VersionNotes(val version: AppVersion, val pageUrl: String, val notes: ReleaseNotes)
