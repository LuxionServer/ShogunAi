package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.model.DetectedStack
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.NodeProvider
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.Stack
import app.luxion.shogunai.domain.model.SuggestedProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeProviderSwitchTest {

    private val basePath = "/home/dev/projects/main-repo"
    private val project = Project(
        id = "p",
        name = "Project",
        config = ProjectConfig(baseRepositoryPath = basePath, worktreesRoot = "/home/dev/projects", secretFiles = emptyList()),
    )

    private fun source(providers: RepositoryProviders = RepositoryProviders()) =
        FakeRepositoryProvisioningSource(byRepository = mapOf(basePath to RepositoryProvisioning(providers = providers)))

    private fun viewModel(
        source: FakeRepositoryProvisioningSource,
        trust: FakeRepositoryTrust = FakeRepositoryTrust(),
        stacks: List<DetectedStack> = emptyList(),
    ) = ProjectConfigViewModel(FakeProjectRepository(), source, project, trust = trust, detectStacks = { stacks })

    @Test
    fun `turning node on writes it, keeps the other providers and approves the install the user turned on`() {
        val source = source(RepositoryProviders(gradle = GradleProvider()))
        val trust = FakeRepositoryTrust()
        val viewModel = viewModel(source, trust)

        viewModel.setNodeEnabled(true)
        viewModel.submit(previewConfirmed = true)

        val providers = source.writtenEdits.single().second.providers
        assertEquals(NodeProvider, providers.node)
        assertEquals(GradleProvider(), providers.gradle)
        assertTrue(trust.isApproved(basePath, RepositoryApprovalSubject(nodeInstall = true)))
    }

    @Test
    fun `accepting the node suggestion turns the switch on without writing anything`() {
        val source = source()
        val viewModel = viewModel(source, stacks = listOf(DetectedStack(Stack.NODE, listOf("package.json"))))

        assertEquals(SuggestedProvider.NODE, viewModel.stackSuggestions.single().provider)
        viewModel.acceptSuggestion(SuggestedProvider.NODE)

        assertTrue(viewModel.repositoryDraft.node)
        assertTrue(viewModel.hasUnsavedChanges)
        assertTrue(source.writtenEdits.isEmpty())
    }

    @Test
    fun `turning node off removes only its provider`() {
        val source = source(RepositoryProviders(node = NodeProvider, gradle = GradleProvider()))
        val viewModel = viewModel(source)

        assertTrue(viewModel.repositoryDraft.node)
        viewModel.setNodeEnabled(false)
        viewModel.submit(previewConfirmed = true)

        val providers = source.writtenEdits.single().second.providers
        assertNull(providers.node)
        assertEquals(GradleProvider(), providers.gradle)
    }

    @Test
    fun `a save never approves a node install declared by someone else`() {
        val source = source(RepositoryProviders(node = NodeProvider))
        val trust = FakeRepositoryTrust()
        val viewModel = viewModel(source, trust)

        viewModel.setGradleEnabled(true)
        viewModel.submit(previewConfirmed = true)

        assertTrue(trust.approvals.isEmpty())
        assertFalse(viewModel.approvalSubject.isEmpty)
    }
}
