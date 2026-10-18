package app.luxion.shogunai.ui.worktree

import kotlin.test.Test
import kotlin.test.assertEquals

class ListScrollTest {

    @Test
    fun `a fully visible row below the header does not scroll`() {
        assertEquals(0, scrollDeltaToReveal(itemOffset = 40, itemSize = 40, headerHeight = 32, viewportBottom = 400))
    }

    @Test
    fun `a row under the sticky header scrolls up to sit below it`() {
        assertEquals(-22, scrollDeltaToReveal(itemOffset = 10, itemSize = 40, headerHeight = 32, viewportBottom = 400))
    }

    @Test
    fun `a row cut by the bottom scrolls down just enough`() {
        assertEquals(20, scrollDeltaToReveal(itemOffset = 380, itemSize = 40, headerHeight = 32, viewportBottom = 400))
    }
}
