package app.luxion.shogunai.ui.projectconfig

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.SimulatorSetup
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.ui.LocalPathPicker
import app.luxion.shogunai.ui.PathPicker
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings

internal class AnsweringPathPicker(var answer: String?) : PathPicker {
    val requests = mutableListOf<String>()

    override suspend fun pickDirectory(initialPath: String, dialogTitle: String): String? {
        requests += initialPath
        return answer
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.setSettingsContent(
    width: Dp = 700.dp,
    height: Dp? = null,
    pathPicker: PathPicker = AnsweringPathPicker(null),
    density: Float? = null,
    content: @Composable () -> Unit,
) {
    setContent {
        val screenDensity = if (density == null) LocalDensity.current else Density(density)
        CompositionLocalProvider(
            LocalStrings provides EnglishStrings,
            LocalPathPicker provides pathPicker,
            LocalDensity provides screenDensity,
        ) {
            MaterialTheme {
                val size = Modifier.width(width).let { if (height != null) it.height(height) else it }
                Box(size) { Column { content() } }
            }
        }
    }
}

internal class UiTestSimulatorSetup(
    var runtimes: suspend () -> Result<List<SimulatorRuntime>> = { Result.success(emptyList()) },
    var wired: Boolean = false,
    var kotlinMultiplatform: Boolean = true,
    var initScriptInstalled: Boolean = false,
    var install: Result<Unit> = Result.success(Unit),
    var uninstall: Result<Unit> = Result.success(Unit),
) : SimulatorSetup {
    override fun isAvailable(repositoryPath: String) = true
    override suspend fun runtimes() = runtimes.invoke()
    override fun isWired(repositoryPath: String) = wired
    override fun usesKotlinMultiplatform(repositoryPath: String) = kotlinMultiplatform
    override fun installInitScript() = install
    override fun isInitScriptInstalled() = initScriptInstalled
    override fun uninstallInitScript() = uninstall
    override val snippet = "tasks.withType<KotlinNativeSimulatorTest>()"
    override val initScriptPath = "/home/dev/.gradle/init.d/shogunai.init.gradle.kts"
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.clickOutsideDialog() {
    onAllNodes(isRoot()).onFirst().performTouchInput { click(Offset(2f, 2f)) }
}
