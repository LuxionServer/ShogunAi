package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.io.DirectoryListing
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalEntriesTest {

    private val basePath = "/home/dev/projects/main-repo"
    private val provisioningSource = FakeRepositoryProvisioningSource()
    private val projectRepository = FakeProjectRepository()
    private val folders = mutableMapOf<String, DirectoryListing>()

    private fun project(secretFiles: List<String>, linked: List<String> = emptyList()) = Project(
        id = "p",
        name = "Project",
        config = ProjectConfig(
            baseRepositoryPath = basePath,
            worktreesRoot = "/home/dev/projects",
            secretFiles = secretFiles,
            linkedSecretFiles = linked,
        ),
    )

    private fun viewModel(project: Project) = ProjectConfigViewModel(
        projectRepository,
        provisioningSource,
        project,
        listLocalFolder = { folders[it] },
    )

    @Test
    fun `switching an entry to link is an unsaved change saved with the project`() {
        val viewModel = viewModel(project(listOf("keys", "local.properties")))

        viewModel.setEntryLinked("keys", true)

        assertTrue(viewModel.hasUnsavedChanges)
        val saved = viewModel.save().getOrThrow()
        assertEquals(listOf("keys"), saved.config.linkedSecretFiles)
        assertTrue(saved.config.isLinked("keys"))
        assertFalse(saved.config.isLinked("local.properties"))
    }

    @Test
    fun `a removed entry forgets its mode`() {
        val viewModel = viewModel(project(listOf("keys"), linked = listOf("keys")))

        viewModel.removeSecretFile("keys")
        viewModel.addSecretFile("keys")

        assertFalse(viewModel.isLinkedEntry("keys"))
        assertEquals(emptyList(), viewModel.save().getOrThrow().config.linkedSecretFiles)
    }

    @Test
    fun `the mode is kept when the base repository moves`() {
        val viewModel = viewModel(project(listOf("local.properties"), linked = listOf("local.properties")))

        viewModel.baseRepositoryPath = "/home/dev/moved/main-repo"
        val saved = viewModel.save().getOrThrow()

        assertEquals(listOf("local.properties"), saved.config.linkedSecretFiles)
    }

    @Test
    fun `entries saved by an earlier version are copied`() {
        val viewModel = viewModel(project(listOf("local.properties")))

        assertFalse(viewModel.isLinkedEntry("local.properties"))
        assertFalse(viewModel.hasUnsavedChanges)
    }

    @Test
    fun `pattern entries are marked and absolute patterns are refused`() {
        val viewModel = viewModel(project(emptyList()))

        viewModel.addSecretFile("*.jks")
        viewModel.addSecretFile("/Users/me/keys/*.jks")

        assertEquals(listOf("*.jks"), viewModel.secretFiles)
        assertTrue(viewModel.isPatternEntry("*.jks"))
        assertEquals("/Users/me/keys/*.jks", viewModel.rejectedLocalPattern)

        viewModel.addSecretFile("local.properties")

        assertNull(viewModel.rejectedLocalPattern)
    }

    @Test
    fun `a copied folder says how many symbolic links it skips`() {
        folders["$basePath/keys"] = DirectoryListing(listOf("a.pem"), listOf("shared.pem", "ci"))
        folders["$basePath/certs"] = DirectoryListing(listOf("ca.pem"))
        val viewModel = viewModel(project(listOf("keys", "certs")))

        assertEquals(mapOf("keys" to 2), viewModel.skippedLinksByFolder)

        viewModel.setEntryLinked("keys", true)

        assertEquals(emptyMap(), viewModel.skippedLinksByFolder)
    }

    @Test
    fun `moving entries sends linked ones to link and patterns unchanged`() {
        val viewModel = viewModel(project(listOf("local.properties", "*.jks", "/Users/me/keys/release.jks"), linked = listOf("local.properties")))

        viewModel.moveLocalFilesToRepository()
        viewModel.submit(previewConfirmed = true)

        assertEquals(listOf(basePath to listOf("*.jks")), provisioningSource.addedPatterns)
        assertEquals(listOf(basePath to listOf("/local.properties")), provisioningSource.addedLinkPatterns)
        val saved = projectRepository.list().single().config
        assertEquals(listOf("/Users/me/keys/release.jks"), saved.secretFiles)
        assertEquals(emptyList(), saved.linkedSecretFiles)
    }
}
