package app.luxion.shogunai.ui.projectconfig

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.io.DirectoryListing
import app.luxion.shogunai.domain.model.KeystoreReferenceWarning
import app.luxion.shogunai.domain.model.MobileSecret
import app.luxion.shogunai.domain.model.MobileSecretKind
import app.luxion.shogunai.domain.model.MobileSecretsReport
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LocalEntriesUiTest {

    private val basePath = "/home/dev/projects/main-repo"
    private val project = Project(
        id = "p",
        name = "Project",
        config = ProjectConfig(baseRepositoryPath = basePath, worktreesRoot = "/home/dev/projects", secretFiles = listOf("keys", "*.jks")),
    )

    @Test
    fun `a local entry shows its pattern mark, skipped links and link switch`() = runComposeUiTest {
        val viewModel = ProjectConfigViewModel(
            FakeProjectRepository(),
            FakeRepositoryProvisioningSource(),
            project,
            listLocalFolder = { if (it == "$basePath/keys") DirectoryListing(listOf("a.pem"), listOf("shared.pem")) else null },
        )
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme {
                    Column(Modifier.width(700.dp)) {
                        LocalEntryRow(viewModel, "keys")
                        LocalEntryRow(viewModel, "*.jks")
                    }
                }
            }
        }

        onNodeWithText("pattern").assertIsDisplayed()
        onNodeWithText("Skips 1 symbolic link inside the folder").assertIsDisplayed()
        onAllNodes(isToggleable())[0].performClick()

        assertTrue(viewModel.isLinkedEntry("keys"))
        onNodeWithText("Skips 1 symbolic link inside the folder").assertDoesNotExist()
    }

    @Test
    fun `secret suggestions offer the link action and show keystore warnings`() = runComposeUiTest {
        val secret = MobileSecret("app/google-services.json", MobileSecretKind.FIREBASE_CONFIG)
        val report = MobileSecretsReport(listOf(secret), listOf(KeystoreReferenceWarning("keystore.properties", "storeFile", "../signing/release.jks")))
        val viewModel = ProjectConfigViewModel(
            FakeProjectRepository(),
            FakeRepositoryProvisioningSource(),
            project.copy(config = project.config.copy(secretFiles = emptyList())),
            detectMobileSecrets = { report },
        )
        waitUntil { viewModel.mobileSecretSuggestions.isNotEmpty() }
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column(Modifier.width(700.dp)) { MobileSecretsSuggestions(viewModel) } }
            }
        }

        onNodeWithText("app/google-services.json · Firebase configuration").assertIsDisplayed()
        onNodeWithText("storeFile in keystore.properties", substring = true).assertIsDisplayed()
        onNodeWithText("Link from .shogunai.json").performClick()

        assertEquals(listOf("/app/google-services.json"), viewModel.pendingLinkPatterns)
    }
}
