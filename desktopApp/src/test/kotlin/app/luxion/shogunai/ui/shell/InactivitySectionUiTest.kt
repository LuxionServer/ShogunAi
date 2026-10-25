package app.luxion.shogunai.ui.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.FakeShellPreferenceRepository
import app.luxion.shogunai.ui.worktree.InactivityPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class InactivitySectionUiTest {

    private val repository = FakeShellPreferenceRepository()
    private val inactivity = InactivityPreferences(repository)

    @Test
    fun `a value inside the range is saved`() = runComposeUiTest {
        setContent { MaterialTheme { InactivitySection(inactivity) } }

        onNodeWithTag(InactivityFieldTag).performTextReplacement("7")

        assertEquals(7, inactivity.days)
        assertEquals(7, repository.loadInactiveAfterDays())
    }

    @Test
    fun `values outside the range are rejected and the previous one is kept`() = runComposeUiTest {
        setContent { MaterialTheme { InactivitySection(inactivity) } }

        onNodeWithTag(InactivityFieldTag).performTextReplacement("0")
        onNodeWithText("Enter a whole number from 1 to 90.").assertIsDisplayed()
        onNodeWithTag(InactivityFieldTag).performTextReplacement("120")

        assertEquals(3, inactivity.days)
        assertEquals(3, repository.loadInactiveAfterDays())
    }
}
