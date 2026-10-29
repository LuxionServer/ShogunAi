package app.luxion.shogunai.e2e

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.luxion.shogunai.AppContainer
import app.luxion.shogunai.shellViewModelOf
import app.luxion.shogunai.ui.LocalPathPicker
import app.luxion.shogunai.ui.PathPicker
import app.luxion.shogunai.ui.components.LocalShownErrorLog
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.rememberScreenViewModelStoreOwner
import app.luxion.shogunai.ui.shell.AppShell
import app.luxion.shogunai.ui.shell.Appearance
import app.luxion.shogunai.ui.shell.AppearanceScope
import app.luxion.shogunai.ui.shell.ClaudeHooksSection
import app.luxion.shogunai.ui.worktree.InactivityPreferences
import java.io.File
import kotlin.test.fail

const val E2E_TIMEOUT_MILLIS = 30_000L

class AnsweringPathPicker(var folder: File? = null, var files: List<File> = emptyList()) : PathPicker {
    override fun pickDirectory(initialPath: String, dialogTitle: String): String? = folder?.absolutePath

    override fun pickFiles(initialPath: String, dialogTitle: String): List<String> = files.map { it.absolutePath }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.setShell(appContainer: AppContainer, pathPicker: PathPicker = AnsweringPathPicker()): Strings {
    val appearance = Appearance(appContainer.themePreferenceRepository, appContainer.languagePreferenceRepository)
    val inactivity = InactivityPreferences(appContainer.shellPreferenceRepository)
    setContent {
        CompositionLocalProvider(
            LocalViewModelStoreOwner provides rememberScreenViewModelStoreOwner(),
            LocalShownErrorLog provides appContainer.shownErrorLog,
            LocalPathPicker provides pathPicker,
        ) {
            val shellViewModel = viewModel { shellViewModelOf(appContainer) }
            AppearanceScope(appearance) {
                AppShell(appContainer, shellViewModel, onOpenPreferences = {}, inactivity = inactivity)
            }
        }
    }
    return appearance.strings
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.setHooksSection(appContainer: AppContainer): Strings {
    val appearance = Appearance(appContainer.themePreferenceRepository, appContainer.languagePreferenceRepository)
    setContent {
        CompositionLocalProvider(LocalShownErrorLog provides appContainer.shownErrorLog) {
            LaunchedEffect(Unit) { appContainer.claudeHooks.refresh() }
            AppearanceScope(appearance) { ClaudeHooksSection(appContainer.claudeHooks) }
        }
    }
    return appearance.strings
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.awaitText(text: String, substring: Boolean = false) {
    awaitCondition("the text \"$text\"") { hasNodeWithText(text, substring) }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.awaitNoText(text: String) {
    awaitCondition("no text \"$text\"") { !hasNodeWithText(text, substring = false) }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.awaitCondition(description: String, condition: () -> Boolean) {
    try {
        waitUntil(timeoutMillis = E2E_TIMEOUT_MILLIS, condition = condition)
    } catch (timeout: ComposeTimeoutException) {
        fail("Timed out waiting for $description. Texts on screen: ${textsOnScreen()}", timeout)
    }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.clickUntil(text: String, description: String, condition: () -> Boolean) {
    awaitCondition(description) {
        val targets = onAllNodesWithText(text)
        if (!condition() && targets.fetchSemanticsNodes().isNotEmpty()) targets.onFirst().performClick()
        condition()
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.textsOnScreen(): List<String> =
    onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap { root -> textsOf(root) }

private fun textsOf(node: SemanticsNode): List<String> =
    node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
        listOfNotNull(node.config.getOrNull(SemanticsProperties.EditableText)?.text) +
        node.children.flatMap(::textsOf)

private fun SemanticsNodeInteractionsProvider.hasNodeWithText(text: String, substring: Boolean): Boolean =
    onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
