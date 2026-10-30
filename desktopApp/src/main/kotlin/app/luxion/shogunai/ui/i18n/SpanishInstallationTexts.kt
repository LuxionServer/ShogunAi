package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.io.HostPathSource
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.InstallationReport
import app.luxion.shogunai.domain.model.UnavailableLocalFile
import app.luxion.shogunai.domain.model.UnavailableReason
import app.luxion.shogunai.domain.usecase.CommandLineToolState
import app.luxion.shogunai.domain.usecase.HooksInstallState

private const val SPANISH_PREFERENCES = "Preferencias › Claude Code"
private const val GIT_DOWNLOADS = "https://git-scm.com/downloads"

internal fun spanishInstallationOverview(report: InstallationReport): String {
    val failed = report.checks.count { it.state == CheckState.FAILED }
    val warnings = report.checks.count { it.state == CheckState.WARNING }
    return when {
        failed == 0 && warnings == 0 -> "Todo lo que ShogunAi necesita está en su sitio."
        failed == 0 -> if (warnings == 1) "1 aviso; ShogunAi funciona, pero revisa lo de abajo." else "$warnings avisos; ShogunAi funciona, pero revisa lo de abajo."
        else -> (if (failed == 1) "1 problema impide que ShogunAi funcione" else "$failed problemas impiden que ShogunAi funcione") +
            if (warnings > 0) " y hay $warnings ${if (warnings == 1) "aviso" else "avisos"}." else "."
    }
}

internal fun spanishInstallationState(state: CheckState): String = when (state) {
    CheckState.PASSED -> "Correcto"
    CheckState.WARNING -> "Aviso"
    CheckState.FAILED -> "Fallo"
    CheckState.NOT_FOUND -> "No instalado"
}

internal fun spanishInstallationSummary(check: InstallationCheck): String = when (val finding = check.finding) {
    is InstallationFinding.Git -> when {
        finding.location == null && finding.detail != null -> "No se ha podido buscar Git: ${finding.detail}"
        finding.location == null -> "Git no está en el PATH"
        finding.version == null -> "No se ha podido leer la versión de Git de ${finding.location}${finding.detail.asSuffix()}"
        check.state == CheckState.PASSED -> "Git ${finding.version} en ${finding.location}"
        else -> "Git ${finding.version} en ${finding.location} es anterior a ${finding.minimum}, la que ShogunAi necesita"
    }
    is InstallationFinding.PathSource -> when (finding.source) {
        HostPathSource.LOGIN_SHELL -> "El PATH viene de tu shell de inicio de sesión (${finding.shell})"
        HostPathSource.PROCESS -> "El PATH viene del proceso que ha iniciado ShogunAi"
        HostPathSource.FALLBACK -> "No se ha podido leer el PATH de tu shell de inicio de sesión (${finding.shell ?: "SHELL no está definida"}), así que se han usado las carpetas de instalación habituales"
    }
    is InstallationFinding.ShogunCommand -> when (finding.state) {
        CommandLineToolState.AVAILABLE -> "El comando shogun está en ${finding.launcher}"
        CommandLineToolState.INSTALLED_NOT_ON_PATH -> "El comando shogun está en ${finding.launcher}, pero ${finding.folder} no está en el PATH"
        CommandLineToolState.BROKEN -> "El comando shogun de ${finding.launcher} apunta a una app que ya no está"
        CommandLineToolState.MISSING -> "El comando shogun no está instalado"
    }
    is InstallationFinding.Hooks -> when {
        finding.state == null -> "No se han podido leer los ajustes de Claude Code ${finding.settingsPath}${finding.invalidDetail.asSuffix()}"
        finding.state == HooksInstallState.MISSING -> "Los hooks de ShogunAi para Claude Code no están instalados"
        finding.state == HooksInstallState.OUTDATED -> "Los hooks de ShogunAi para Claude Code están desactualizados (${finding.events.joinToString()})"
        check.state == CheckState.PASSED -> "Los hooks de ShogunAi para Claude Code están instalados en ${finding.settingsPath}"
        finding.shogun == CommandLineToolState.INSTALLED_NOT_ON_PATH -> "Los hooks de Claude Code ejecutan shogun, que no está en el PATH"
        finding.shogun == CommandLineToolState.BROKEN -> "Los hooks de Claude Code ejecutan shogun, cuyo lanzador ya no está"
        else -> "Los hooks de Claude Code ejecutan shogun, que no está instalado"
    }
    is InstallationFinding.AgentTool ->
        if (finding.location == null) "${finding.tool.displayName} no está instalado" else "${finding.tool.displayName} está en ${finding.location}"
    is InstallationFinding.ProjectFiles -> when {
        finding.baseMissing -> "${finding.project}: su carpeta ${finding.basePath} no existe"
        finding.detail != null -> "${finding.project}: no se han podido comprobar sus archivos locales: ${finding.detail}"
        finding.unavailable.isNotEmpty() ->
            "${finding.project}: ${spanishFiles(finding.unavailable.size)} no se ${if (finding.unavailable.size == 1) "puede" else "pueden"} copiar ni enlazar desde ${finding.basePath}"
        finding.checked == 0 -> "${finding.project}: ningún archivo local que copiar o enlazar"
        else -> "${finding.project}: ${spanishFiles(finding.checked)} ${if (finding.checked == 1) "disponible" else "disponibles"}"
    }
}

internal fun spanishInstallationFix(check: InstallationCheck): String? {
    if (check.state == CheckState.PASSED) return null
    return when (val finding = check.finding) {
        is InstallationFinding.Git -> when {
            finding.location == null -> "Instala Git ${finding.minimum} o posterior desde $GIT_DOWNLOADS y comprueba que está en tu PATH."
            finding.version == null -> "Comprueba que ${finding.location} se ejecuta, o instala Git ${finding.minimum} o posterior desde $GIT_DOWNLOADS."
            else -> "Actualiza Git a ${finding.minimum} o posterior desde $GIT_DOWNLOADS."
        }
        is InstallationFinding.PathSource ->
            "Haz que ${finding.shell ?: "tu shell de inicio de sesión"} arranque sin errores ni preguntas en menos de 5 segundos y vuelve a abrir ShogunAi."
        is InstallationFinding.ShogunCommand -> spanishShogunFix(finding.state, finding.folder, finding.restartNeeded)
        is InstallationFinding.Hooks -> when {
            finding.state == null -> "Corrige ${finding.settingsPath} para que sea JSON válido y vuelve a comprobar."
            finding.state != HooksInstallState.INSTALLED -> "Instálalos desde $SPANISH_PREFERENCES, o ejecuta shogun hooks install."
            else -> spanishShogunFix(finding.shogun, null, false)
        }
        is InstallationFinding.AgentTool -> "Instala ${finding.tool.displayName} si lo usas: ${finding.tool.website}"
        is InstallationFinding.ProjectFiles -> when {
            finding.baseMissing -> "Restaura ${finding.basePath}, o quita el proyecto de ShogunAi."
            finding.detail != null -> "Comprueba que ${finding.basePath} es un repositorio Git que puedes leer."
            else -> (finding.unavailable.map(::spanishLocalFileProblem) + spanishLocalFilesFix(finding.unavailable)).joinToString("\n")
        }
    }
}

private fun spanishShogunFix(state: CommandLineToolState, folder: String?, restartNeeded: Boolean): String = when (state) {
    CommandLineToolState.INSTALLED_NOT_ON_PATH ->
        if (restartNeeded) "Reinicia tu terminal y Claude Code para que vean ${folder ?: "su carpeta"}."
        else "Añade ${folder ?: "su carpeta"} a tu PATH."
    CommandLineToolState.BROKEN -> "Vuelve a instalar el comando desde $SPANISH_PREFERENCES."
    CommandLineToolState.MISSING, CommandLineToolState.AVAILABLE -> "Instala el comando desde $SPANISH_PREFERENCES."
}

private fun spanishLocalFileProblem(file: UnavailableLocalFile): String = when (val reason = file.reason) {
    UnavailableReason.Missing -> "${file.path}: no está en ${file.fullPath}"
    is UnavailableReason.BrokenLink -> "${file.path}: es un enlace a ${reason.target}, que no existe"
    is UnavailableReason.Unreadable -> "${file.path}: no se puede leer${reason.detail.asSuffix()}"
}

private fun spanishFiles(count: Int): String = if (count == 1) "1 archivo local" else "$count archivos locales"
