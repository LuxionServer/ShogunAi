package app.luxion.shogunai.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class NoticeGroupsTest {

    private data class Event(val source: String, val kind: String, val text: String)

    @Test
    fun `notices are grouped by kind and source in order of first appearance`() {
        val events = listOf(
            Event("gradle", "trim", "1"),
            Event("gradle", "release", "2"),
            Event("emulators", "release", "3"),
            Event("gradle", "trim", "4"),
        )

        val groups = groupNotices(events) { it.source to it.kind }

        assertEquals(listOf("gradle" to "trim", "gradle" to "release", "emulators" to "release"), groups.map { it.key })
        assertEquals(listOf(2, 1, 1), groups.map { it.count })
        assertEquals(listOf("1", "4"), groups.first().items.map { it.text })
    }
}
