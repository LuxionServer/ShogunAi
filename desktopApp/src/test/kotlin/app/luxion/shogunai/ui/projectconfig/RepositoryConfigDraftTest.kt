package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.EnvironmentVariable
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.RepositoryConfigIssue
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.TemplatePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryConfigDraftTest {

    @Test
    fun `invalid Gradle idle timeout and Kotlin daemon heap are reported on their fields`() {
        val draft = RepositoryConfigDraft(gradle = GradleDraft(idleTimeoutMinutes = "soon", kotlinDaemonMaxHeap = "lots"))

        assertEquals(RepositoryConfigIssue.InvalidMinutes, draft.issueFor(RepositoryConfigField.GradleIdleTimeout))
        assertEquals(RepositoryConfigIssue.InvalidMemorySize, draft.issueFor(RepositoryConfigField.GradleKotlinDaemonMaxHeap))
    }

    @Test
    fun `valid Gradle and Maven values and a valid env file have no problems`() {
        val draft = RepositoryConfigDraft(
            gradle = GradleDraft(daemonMaxHeap = "2g", idleTimeoutMinutes = "30", kotlinDaemonMaxHeap = "1g"),
            maven = MavenDraft(mavenMaxHeap = "1g"),
            envFile = ".env.local",
        )

        assertTrue(draft.problems.isEmpty())
    }

    @Test
    fun `Maven heap is checked only when it is filled in`() {
        assertTrue(RepositoryConfigDraft(maven = MavenDraft(mavenMaxHeap = "  ")).problems.isEmpty())
        assertEquals(
            RepositoryConfigIssue.InvalidMemorySize,
            RepositoryConfigDraft(maven = MavenDraft(mavenMaxHeap = "big")).issueFor(RepositoryConfigField.MavenMaxHeap),
        )
    }

    @Test
    fun `the iOS simulator draft becomes a provider with blank fields left out`() {
        val edit = RepositoryConfigDraft(iosSimulator = IosSimulatorDraft(device = " iPhone 16 ", runtime = "")).toEdit()

        assertEquals(IosSimulatorProvider(device = "iPhone 16", runtime = null), edit.providers.iosSimulator)
    }

    @Test
    fun `a missing repository file gives an empty draft`() {
        assertEquals(RepositoryConfigDraft(), RepositoryConfigDraft.of(null))
    }

    @Test
    fun `declared provider options are loaded into the draft`() {
        val provisioning = RepositoryProvisioning(
            providers = RepositoryProviders(
                gradle = GradleProvider(daemonMaxHeap = "2g", idleTimeoutMinutes = 30, kotlinDaemonMaxHeap = "1g"),
                emulator = EmulatorProvider(preferredAvd = "Pixel_8"),
                iosSimulator = IosSimulatorProvider(device = "iPhone 16", runtime = "iOS 18.5"),
            ),
            environment = EnvironmentDeclaration(envFile = ".env.worktree"),
        )

        val draft = RepositoryConfigDraft.of(provisioning)

        assertEquals(GradleDraft("2g", "30", "1g"), draft.gradle)
        assertEquals(EmulatorDraft("Pixel_8"), draft.emulator)
        assertEquals(IosSimulatorDraft("iPhone 16", "iOS 18.5"), draft.iosSimulator)
        assertEquals(".env.worktree", draft.envFile)
    }

    @Test
    fun `providers declared without options load as blank fields`() {
        val provisioning = RepositoryProvisioning(
            providers = RepositoryProviders(emulator = EmulatorProvider(), iosSimulator = IosSimulatorProvider()),
        )

        val draft = RepositoryConfigDraft.of(provisioning)

        assertEquals(EmulatorDraft(""), draft.emulator)
        assertEquals(IosSimulatorDraft("", ""), draft.iosSimulator)
        assertNull(draft.gradle)
    }

    @Test
    fun `every template part is written back as the template text it was parsed from`() {
        val parts = listOf(
            TemplatePart.Literal("http://localhost:"),
            TemplatePart.Port("port"),
            TemplatePart.Literal("/"),
            TemplatePart.Port("port:admin"),
            TemplatePart.Literal("-"),
            TemplatePart.WorktreeName,
            TemplatePart.Literal("-"),
            TemplatePart.RepositoryPort("api", "port:web"),
        )
        val provisioning = RepositoryProvisioning(
            environment = EnvironmentDeclaration(listOf(EnvironmentVariable("APP_URL", parts, "http://localhost"))),
        )

        val row = RepositoryConfigDraft.of(provisioning).environment.single()

        assertEquals("http://localhost:\${port}/\${port:admin}-\${worktree}-\${port:api.web}", row.template)
    }
}
