package app.luxion.shogunai.ui.worktree.inspector

import androidx.compose.runtime.Composable
import app.luxion.shogunai.ui.components.InspectorSection
import app.luxion.shogunai.ui.components.PropertyList
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.worktree.WorktreeStatusState
import java.time.Instant

@Composable
fun StatusSection(status: WorktreeStatusState?, mainBranchName: String?) {
    val strings = LocalStrings.current
    val loaded = (status as? WorktreeStatusState.Loaded)?.status
    InspectorSection(
        id = InspectorSectionId.STATUS,
        title = strings.statusTitle,
        state = statusSectionState(status, strings),
        summary = loaded?.let { statusSummary(it, strings) },
    ) {
        loaded?.let { PropertyList(statusProperties(it, mainBranchName, Instant.now(), strings)) }
    }
}
