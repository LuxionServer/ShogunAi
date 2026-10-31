package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeProcessHandle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClaudeProcessTest {

    @Test
    fun `a running process that isn't claude doesn't count as a live Claude session`() {
        assertFalse(isLiveClaudeProcess(ProcessHandle.current().pid()))
    }

    @Test
    fun `a process that doesn't exist isn't a live Claude session`() {
        assertFalse(isLiveClaudeProcess(Long.MAX_VALUE))
    }

    @Test
    fun `a running claude counts on any platform`() {
        assertTrue(isLiveClaudeProcess(FakeProcessHandle(alive = true, command = "/usr/local/bin/claude")))
        assertTrue(isLiveClaudeProcess(FakeProcessHandle(alive = true, command = "C:\\Tools\\claude.exe")))
    }

    @Test
    fun `a process whose command can't be read is trusted to be claude`() {
        assertTrue(isLiveClaudeProcess(FakeProcessHandle(alive = true, command = null)))
    }

    @Test
    fun `a process that has exited doesn't count`() {
        assertFalse(isLiveClaudeProcess(FakeProcessHandle(alive = false, command = "/usr/local/bin/claude")))
    }
}
