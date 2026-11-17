package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.model.CustomPool
import app.luxion.shogunai.domain.model.CustomPoolVariable
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.parsePoolTemplate
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ApprovalPoolLinesUiTest {

    private val db = CustomPool(
        "db",
        3,
        listOf(parsePoolTemplate("./scripts/db-up.sh"), parsePoolTemplate("\${slot}")),
        listOf(parsePoolTemplate("./scripts/db-down.sh"), parsePoolTemplate("\${slot}")),
        listOf(CustomPoolVariable("DATABASE_URL", parsePoolTemplate("postgres://localhost/app_\${slot}"))),
    )
    private val gpu = CustomPool("gpu", 1, listOf(parsePoolTemplate("claim")))

    @Test
    fun `the approval shows each pool with its commands and variables`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Column { ApprovalSubjectLines(RepositoryApprovalSubject(pools = listOf(db, gpu)), EnglishStrings) }
            }
        }

        onNodeWithText("Pools").assertExists()
        onNodeWithText("db, at most 3 at once").assertExists()
        onNodeWithText("acquire: ./scripts/db-up.sh '\${slot}'").assertExists()
        onNodeWithText("release: ./scripts/db-down.sh '\${slot}'").assertExists()
        onNodeWithText("DATABASE_URL = postgres://localhost/app_\${slot}").assertExists()
        onNodeWithText("gpu, at most 1 at once").assertExists()
        onNodeWithText("acquire: claim").assertExists()
    }

    @Test
    fun `the pool texts read in Spanish`() {
        assertEquals("db, como máximo 3 a la vez", SpanishStrings.approvalPool("db", 3))
        assertEquals("Pools", SpanishStrings.approvalPoolsTitle)
    }
}
