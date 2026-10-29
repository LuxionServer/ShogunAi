package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultirepoTest {

    private val multirepo = Multirepo(
        folder = "/projects/product",
        declaration = MultirepoDeclaration(repos = mapOf("api" to "api", "app" to "app")),
        folderIsRepository = true,
    )

    @Test
    fun `a detected workspace isn't declared`() {
        assertTrue(multirepo.workspaceDetected)
        assertFalse(multirepo.workspaceDeclared)
        assertTrue(multirepo.copy(declaration = multirepo.declaration.copy(workspace = WorkspaceSetting())).workspaceDeclared)
    }

    @Test
    fun `parent folder is the folder above the multirepo`() {
        assertEquals("/projects", multirepo.parentFolder)
        assertEquals("/", Multirepo("/", MultirepoDeclaration()).parentFolder)
    }

    @Test
    fun `repositories are found by alias and by path`() {
        assertEquals(MultirepoRepository("api", "/projects/product/api"), multirepo.repository("api"))
        assertNull(multirepo.repository("web"))
        assertEquals("app", multirepo.aliasOf("/projects/product/app"))
        assertNull(multirepo.aliasOf("/projects/other"))
    }
}
