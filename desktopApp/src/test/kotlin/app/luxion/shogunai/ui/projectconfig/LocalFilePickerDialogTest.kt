package app.luxion.shogunai.ui.projectconfig

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.model.LocalFileCandidate
import app.luxion.shogunai.domain.model.MobileSecretKind
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LocalFilePickerDialogTest {

    private val repository = "/home/dev/projects/main-repo"
    private val env = LocalFileCandidate(".env", isFolder = false)
    private val keys = LocalFileCandidate("keys", isFolder = true)
    private val firebase = LocalFileCandidate("app/google-services.json", isFolder = false, secretKind = MobileSecretKind.FIREBASE_CONFIG)
    private val provided = LocalFileCandidate("local.properties", isFolder = false, secretKind = MobileSecretKind.LOCAL_PROPERTIES, providedByRepository = true)

    private class Calls {
        var applied = 0
        var cancelled = 0
        var retried = 0
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: LocalFilePickerState): Calls {
        val calls = Calls()
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme {
                    LocalFilePickerDialog(
                        state = state,
                        repository = repository,
                        onApply = { calls.applied++ },
                        onCancel = { calls.cancelled++ },
                        onRetry = { calls.retried++ },
                    )
                }
            }
        }
        return calls
    }

    private fun loaded(declared: Set<String>, vararg candidates: LocalFileCandidate) =
        LocalFilePickerState(declared).apply { load = LocalFileCandidatesLoad.Loaded(candidates.toList()) }

    @Test
    fun `lists the candidates with folders, suggestions and declared entries checked`() = runComposeUiTest {
        show(loaded(setOf(".env"), env, keys, firebase))

        onNodeWithText("Choose local files").assertIsDisplayed()
        onNodeWithText("Files and folders that Git doesn't track in $repository.").assertIsDisplayed()
        onNodeWithText("keys/").assertIsDisplayed()
        onNodeWithText("Suggested · Firebase configuration").assertIsDisplayed()
        onNodeWithTag("local-file-.env").assertIsOn()
        onNodeWithTag("local-file-keys").assertIsOff()
        onNodeWithText("1 selected").assertIsDisplayed()
    }

    @Test
    fun `an entry the repository provides is shown but can't be checked`() = runComposeUiTest {
        show(loaded(emptySet(), provided))

        onNodeWithText("Provided by the repository").assertIsDisplayed()
        onNodeWithTag("local-file-local.properties").assertIsNotEnabled()
    }

    @Test
    fun `checking a row and applying hands the selection over`() = runComposeUiTest {
        val state = loaded(emptySet(), env, keys)
        val calls = show(state)

        onNodeWithTag("local-file-keys").performClick()
        onNodeWithText("Apply").performClick()

        assertEquals(listOf("keys"), state.selection().added)
        assertEquals(1, calls.applied)
    }

    @Test
    fun `cancel closes without applying`() = runComposeUiTest {
        val calls = show(loaded(emptySet(), env))

        onNodeWithText("Cancel").performClick()

        assertEquals(1, calls.cancelled)
        assertEquals(0, calls.applied)
    }

    @Test
    fun `the filter narrows the list and offers a pattern`() = runComposeUiTest {
        val state = loaded(emptySet(), env, keys, LocalFileCandidate("release.jks", isFolder = false))
        show(state)

        onNodeWithTag("local-file-filter").performTextInput("*.jks")
        onNodeWithText(".env").assertDoesNotExist()
        onNodeWithText("release.jks").assertIsDisplayed()
        onNodeWithText("Add pattern *.jks").performClick()

        assertEquals(listOf("*.jks"), state.patterns)
        onNodeWithText("*.jks").assertIsDisplayed()
        onNodeWithText(".env").assertIsDisplayed()
    }

    @Test
    fun `an absolute pattern is refused`() = runComposeUiTest {
        show(loaded(emptySet(), env))

        onNodeWithTag("local-file-filter").performTextInput("/home/dev/keys/*.jks")

        onNodeWithText("Patterns are relative to the repository; use a path inside it.").assertIsDisplayed()
        onNodeWithText("Add pattern", substring = true).assertDoesNotExist()
    }

    @Test
    fun `says when nothing matches and when the repository has no candidates`() = runComposeUiTest {
        val state = loaded(emptySet(), env)
        show(state)

        onNodeWithTag("local-file-filter").performTextInput("zzz")
        onNodeWithText("Nothing matches the filter.").assertIsDisplayed()

        state.filter = ""
        state.load = LocalFileCandidatesLoad.Loaded(emptyList())
        onNodeWithText("The repository has no untracked or ignored files.").assertIsDisplayed()
    }

    @Test
    fun `shows progress while loading and the error with a retry when listing fails`() = runComposeUiTest {
        val state = LocalFilePickerState(emptySet())
        val calls = show(state)

        onNodeWithText("Apply").assertIsNotEnabled()

        state.load = LocalFileCandidatesLoad.Failed(WorktreeError.GitCommandFailed(listOf("git", "ls-files"), 128, "fatal: not a git repository"))
        waitForIdle()
        onNodeWithText(".env").assertDoesNotExist()
        onNodeWithText(EnglishStrings.tryAgain).performClick()

        assertEquals(1, calls.retried)
    }
}
