package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.ui.components.NoticeGroup
import app.luxion.shogunai.ui.components.groupNotices
import app.luxion.shogunai.ui.i18n.Strings

sealed interface PoolNoticeKey {
    data class Released(val kind: ResourceKind) : PoolNoticeKey
    data object Trimmed : PoolNoticeKey
}

data class PoolNoticeBanner(val summary: String, val details: List<String>)

fun poolNoticeBanner(notices: List<PoolNotice>, strings: Strings): PoolNoticeBanner? {
    if (notices.isEmpty()) return null
    val summary = groupNotices(notices, ::noticeKey).joinToString("\n") { it.summary(strings) }
    return PoolNoticeBanner(summary, notices.map { it.message(strings) })
}

private fun noticeKey(notice: PoolNotice): PoolNoticeKey = when (val content = notice.content) {
    is PoolNoticeContent.Released -> PoolNoticeKey.Released(content.kind)
    is PoolNoticeContent.Trimmed -> PoolNoticeKey.Trimmed
}

private fun NoticeGroup<PoolNoticeKey, PoolNotice>.summary(strings: Strings): String {
    if (count == 1) return items.single().message(strings)
    return when (val groupKey = key) {
        is PoolNoticeKey.Released -> strings.releasedSummary(groupKey.kind, count)
        PoolNoticeKey.Trimmed -> {
            val trims = items.map { it.content as PoolNoticeContent.Trimmed }
            strings.trimmedNotice(trims.sumOf { it.count }, trims.last().limit)
        }
    }
}

fun PoolNotice.message(strings: Strings): String = when (val content = content) {
    is PoolNoticeContent.Released -> strings.releasedNotice(content.kind, content.released, content.forWorktree)
    is PoolNoticeContent.Trimmed -> strings.trimmedNotice(content.count, content.limit)
}
