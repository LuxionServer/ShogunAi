package app.luxion.shogunai.ui.worktree

import kotlin.test.Test
import kotlin.test.assertEquals

class SizeFormatTest {

    @Test
    fun `bytes below a kilobyte are shown as bytes`() {
        assertEquals("0 B", formatSize(0))
        assertEquals("1023 B", formatSize(1023))
    }

    @Test
    fun `each unit starts at 1024 of the previous one`() {
        assertEquals("1.0 KB", formatSize(1024))
        assertEquals("1.0 MB", formatSize(1024L * 1024))
        assertEquals("1.2 GB", formatSize(1_288_490_189))
        assertEquals("1.0 TB", formatSize(1024L * 1024 * 1024 * 1024))
    }

    @Test
    fun `terabytes are the largest unit`() {
        assertEquals("2048.0 TB", formatSize(2048L * 1024 * 1024 * 1024 * 1024))
    }
}
