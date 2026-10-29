package app.luxion.shogunai.domain.usecase

import kotlin.test.Test
import kotlin.test.assertFalse

class ClaudeProcessTest {

    @Test
    fun `a running process that isn't claude doesn't count as a live Claude session`() {
        assertFalse(isLiveClaudeProcess(ProcessHandle.current().pid()))
    }

    @Test
    fun `a process that doesn't exist isn't a live Claude session`() {
        assertFalse(isLiveClaudeProcess(Long.MAX_VALUE))
    }
}
