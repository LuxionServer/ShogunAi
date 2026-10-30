package app.luxion.shogunai.infrastructure

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FileSystemXcodeWorkspaceFinderTest {

    private val root: File = Files.createTempDirectory("xcode-finder").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun folder(relative: String) = File(root, relative).apply { mkdirs() }

    private suspend fun found(maxDepth: Int = 4): String =
        FileSystemXcodeWorkspaceFinder(maxDepth).find(root.path).replace(File.separatorChar, '/').removePrefix(root.path.replace(File.separatorChar, '/')).removePrefix("/")

    @Test
    fun `prefers a workspace over a project`() = runTest {
        folder("iosApp/App.xcodeproj")
        folder("iosApp/App.xcworkspace")

        assertEquals("iosApp/App.xcworkspace", found())
    }

    @Test
    fun `takes the project when there is no workspace`() = runTest {
        folder("iosApp/App.xcodeproj")

        assertEquals("iosApp/App.xcodeproj", found())
    }

    @Test
    fun `takes a deeper workspace over a shallower project`() = runTest {
        folder("App.xcodeproj")
        folder("ios/App.xcworkspace")

        assertEquals("ios/App.xcworkspace", found())
    }

    @Test
    fun `the shallowest workspace wins`() = runTest {
        folder("Samples/Demo/Demo.xcworkspace")
        folder("App.xcworkspace")

        assertEquals("App.xcworkspace", found())
    }

    @Test
    fun `ties break alphabetically by path`() = runTest {
        folder("zeta/Z.xcworkspace")
        folder("alpha/A.xcworkspace")

        assertEquals("alpha/A.xcworkspace", found())
    }

    @Test
    fun `ignores the workspace inside a project`() = runTest {
        folder("iosApp/App.xcodeproj/project.xcworkspace")

        assertEquals("iosApp/App.xcodeproj", found())
    }

    @Test
    fun `skips dependency, build and hidden folders`() = runTest {
        listOf("Pods", "build", "DerivedData", "node_modules", "Carthage", ".hidden").forEach {
            folder("$it/Skipped.xcworkspace")
        }

        assertEquals("", found())
    }

    @Test
    fun `does not look deeper than the depth limit`() = runTest {
        folder("a/b/c/Deep.xcworkspace")

        assertEquals("a/b/c/Deep.xcworkspace", found())
        assertEquals("", found(maxDepth = 3))
    }

    @Test
    fun `falls back to the worktree folder`() = runTest {
        folder("src/main")

        assertEquals("", found())
    }

    @Test
    fun `without depth the worktree itself is used`() = runTest {
        folder("App.xcworkspace")

        assertEquals(root.path, FileSystemXcodeWorkspaceFinder(maxDepth = 0).find(root.path))
    }
}
