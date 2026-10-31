package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KeepTopInViewUiTest {

    private val rows = (1..40).map { "row-$it" }

    @Test
    fun `items inserted above a list at its top stay in view`() = runComposeUiTest {
        var keys by mutableStateOf(rows)
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            KeepTopInView(state, keys.first())
            LazyColumn(state = state, modifier = Modifier.height(300.dp).testTag("list")) {
                items(keys, key = { it }) { Text(it, Modifier.height(40.dp)) }
            }
        }
        waitForIdle()

        keys = listOf("waiting-header", "waiting-row") + rows
        waitForIdle()

        assertEquals(0, state.firstVisibleItemIndex)
        assertEquals(0, state.firstVisibleItemScrollOffset)
    }

    @Test
    fun `a list the user scrolled down keeps its place when items are inserted above`() = runComposeUiTest {
        var keys by mutableStateOf(rows)
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            KeepTopInView(state, keys.first())
            LazyColumn(state = state, modifier = Modifier.height(300.dp).testTag("list")) {
                items(keys, key = { it }) { Text(it, Modifier.height(40.dp)) }
            }
        }
        waitForIdle()
        onNodeWithTag("list").performTouchInput { swipeUp(startY = bottom - 10f, endY = top + 10f) }
        waitForIdle()
        val scrolledTo = state.firstVisibleItemIndex

        keys = listOf("waiting-header", "waiting-row") + rows
        waitForIdle()

        assertTrue(scrolledTo > 0)
        assertEquals(scrolledTo + 2, state.firstVisibleItemIndex)
    }
}
