package app.luxion.shogunai.ui.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.BuildInfo
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AboutContentUiTest {

    @Test
    fun `the about window shows the version and opens the repository`() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { AboutContent(openUrl = { opened += it }) }
            }
        }

        onNodeWithText(EnglishStrings.aboutVersion(BuildInfo.VERSION)).assertIsDisplayed()
        onNodeWithText(EnglishStrings.aboutRepositoryLink).performClick()

        assertEquals(listOf(RepositoryUrl), opened)
    }
}
