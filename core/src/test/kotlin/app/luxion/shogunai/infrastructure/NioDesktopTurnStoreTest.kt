package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.DesktopTurn
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NioDesktopTurnStoreTest {

    private val machine: Path = Files.createTempDirectory("desktop-turn")
    private val store = NioDesktopTurnStore(machine)
    private val turn = DesktopTurn("/home/dev/projects/main-repo/.git/worktrees/TASK-1", "/home/dev/projects/worktrees/TASK-1", 1_000, 601_000)

    @AfterTest
    fun cleanUp() {
        machine.toFile().deleteRecursively()
    }

    @Test
    fun `a written turn reads back`() {
        store.write(turn)

        assertEquals(turn, store.read())
        assertEquals("/home/dev/projects/main-repo", store.read()?.repositoryPath)
        assertEquals("main-repo · TASK-1", store.read()?.displayName)
    }

    @Test
    fun `clearing the turn deletes the file`() {
        store.write(turn)

        store.write(null)

        assertNull(store.read())
        assertFalse(machine.resolve(NioDesktopTurnStore.TURN_FILE_NAME).exists())
    }

    @Test
    fun `a missing or corrupt file reads as free`() {
        assertNull(store.read())

        machine.createDirectories().resolve(NioDesktopTurnStore.TURN_FILE_NAME).writeText("not json")

        assertNull(store.read())
    }
}
