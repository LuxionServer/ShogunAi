package app.luxion.shogunai.ui.components

data class NoticeGroup<K, T>(val key: K, val items: List<T>) {
    val count: Int
        get() = items.size
}

fun <K, T> groupNotices(items: List<T>, key: (T) -> K): List<NoticeGroup<K, T>> =
    items.groupBy(key).map { (groupKey, grouped) -> NoticeGroup(groupKey, grouped) }
