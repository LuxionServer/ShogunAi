package app.luxion.shogunai.e2e

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onAllNodesWithText
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
    waitUntil(timeoutMillis = E2E_TIMEOUT_MILLIS) { hasNodeWithText(text, substring) }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.awaitNoText(text: String) {
    waitUntil(timeoutMillis = E2E_TIMEOUT_MILLIS) { !hasNodeWithText(text, substring = false) }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.awaitCondition(condition: () -> Boolean) {
    waitUntil(timeoutMillis = E2E_TIMEOUT_MILLIS, condition = condition)
}

private fun SemanticsNodeInteractionsProvider.hasNodeWithText(text: String, substring: Boolean): Boolean =
    onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
