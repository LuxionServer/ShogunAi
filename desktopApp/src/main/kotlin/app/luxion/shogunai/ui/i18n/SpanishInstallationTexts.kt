package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.model.HostPathSource
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.JavaSource
import app.luxion.shogunai.domain.model.InstallationReport
import app.luxion.shogunai.domain.model.MULTIREPO_FILE_NAME
import app.luxion.shogunai.domain.model.MultirepoProblem
import app.luxion.shogunai.domain.model.ProviderToolId
import app.luxion.shogunai.domain.model.ProviderToolProblem
import app.luxion.shogunai.domain.model.UnavailableLocalFile
import app.luxion.shogunai.domain.model.UnavailableReason
import app.luxion.shogunai.domain.model.CommandLineToolState
import app.luxion.shogunai.domain.model.HooksInstallState

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
    is InstallationFinding.ProviderTool -> spanishProviderSummary(finding)
    is InstallationFinding.MultirepoCheck -> when {
        finding.problems.isEmpty() ->
            "Multirepo ${finding.name}: ${finding.repositories.joinToString()}; ${spanishCount(finding.groups, "grupo", "grupos")}"
        finding.problems.singleOrNull() == MultirepoProblem.DeclarationMissing -> "Multirepo ${finding.name}: ${finding.folder} ya no tiene $MULTIREPO_FILE_NAME"
        else -> "Multirepo ${finding.name}: ${spanishCount(finding.problems.size, "problema", "problemas")} en ${finding.folder}"
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
        is InstallationFinding.ProviderTool -> finding.problem?.let { spanishProviderFix(finding.tool, it) }
        is InstallationFinding.MultirepoCheck -> finding.problems.takeIf { it.isNotEmpty() }?.joinToString("\n") { spanishMultirepoFix(finding, it) }
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

private fun spanishProviderSummary(finding: InstallationFinding.ProviderTool): String {
    val subject = "${finding.project}: ${spanishToolName(finding.tool)}"
    return when (val problem = finding.problem) {
        null -> when (finding.tool) {
            ProviderToolId.ANDROID_SDK -> "$subject está en ${finding.location} (${spanishCount(finding.installed ?: 0, "dispositivo virtual", "dispositivos virtuales")})"
            ProviderToolId.IOS_SIMULATOR -> "$subject tiene ${spanishCount(finding.installed ?: 0, "runtime de iOS", "runtimes de iOS")}"
            ProviderToolId.GRADLE_JAVA, ProviderToolId.MAVEN_JAVA, ProviderToolId.GRADLE_DAEMON_JAVA -> spanishJavaPassing(finding, subject)
            ProviderToolId.GRADLE_TOOLCHAINS ->
                if (finding.provisioned) "$subject: Java ${finding.version}, que Gradle descarga si faltan"
                else "$subject: Java ${finding.version} instalados"
            ProviderToolId.ANDROID_GRADLE_PLUGIN_JAVA -> "${finding.project}: el Android Gradle Plugin ${finding.version} se ejecuta con Java ${finding.java}"
            ProviderToolId.DOCKER_DAEMON -> "${finding.project}: el daemon de Docker${finding.version?.let { " $it" }.orEmpty()} está en marcha"
            else -> "$subject${finding.version?.let { " $it" }.orEmpty()} está en ${finding.location}"
        }
        is ProviderToolProblem.NotFound -> "$subject no está instalado; lo necesita el proveedor ${finding.tool.provider} (se ha buscado en ${(problem.searched + problem.commands.map { "$it en el PATH" }).joinToString()})"
        is ProviderToolProblem.Unusable -> "$subject${finding.location?.let { " en $it" }.orEmpty()} no funciona${problem.detail.asSuffix()}"
        ProviderToolProblem.NeedsMacOs -> "${finding.project}: el proveedor ${finding.tool.provider} necesita macOS con Xcode"
        ProviderToolProblem.NoAndroidVirtualDevice -> "$subject en ${finding.location} no tiene ningún dispositivo virtual de Android"
        ProviderToolProblem.NoIosRuntime -> "${finding.project}: no hay ningún runtime de simulador de iOS instalado"
        is ProviderToolProblem.ProvidersUnreadable -> "${finding.project}: no se han podido leer sus proveedores${problem.detail.asSuffix()}"
        is ProviderToolProblem.JavaHomeInvalid -> "$subject no está instalado: JAVA_HOME es ${problem.javaHome}, que no tiene bin/java"
        is ProviderToolProblem.JavaTooOld ->
            "$subject es Java ${problem.java}${finding.location?.let { " en $it" }.orEmpty()}, pero ${spanishBuildTool(finding.tool)} ${problem.toolVersion} necesita Java ${problem.required} o posterior"
        is ProviderToolProblem.GradleTooOld ->
            "$subject es Java ${problem.java}${finding.location?.let { " en $it" }.orEmpty()}, que necesita Gradle ${problem.required} o posterior, pero el proyecto usa Gradle ${problem.gradle}"
        is ProviderToolProblem.JavaNotInstalled ->
            "$subject: Java ${problem.versions.joinToString(" y ")} no ${if (problem.versions.size == 1) "está instalado" else "están instalados"} (se ha buscado en ${problem.searched.joinToString()})"
        is ProviderToolProblem.GradleJavaHomeInvalid ->
            "$subject no está instalado: org.gradle.java.home es ${problem.javaHome} en ${problem.propertiesFile}, que no tiene bin/java"
        is ProviderToolProblem.DaemonCriteriaUnreadable -> "$subject: no se ha podido leer toolchainVersion en ${problem.path}"
        is ProviderToolProblem.AndroidGradlePluginNeedsJava ->
            "${finding.project}: el Android Gradle Plugin ${problem.agp} necesita Java ${problem.required} o posterior, pero el daemon de Gradle usa Java ${problem.java}"
        is ProviderToolProblem.TooOld ->
            "$subject ${problem.version} en ${finding.location} es anterior a ${problem.minimum}, la primera con el docker compose ls que usa ShogunAi"
        is ProviderToolProblem.DaemonNotRunning -> "${finding.project}: el daemon de Docker no está en marcha${problem.detail.asSuffix()}"
    }
}

private fun spanishToolName(tool: ProviderToolId): String = when (tool) {
    ProviderToolId.GRADLE_JAVA -> "Java de Gradle"
    ProviderToolId.MAVEN_JAVA -> "Java de Maven"
    ProviderToolId.DOCKER_DAEMON -> "Daemon de Docker"
    ProviderToolId.GRADLE_DAEMON_JAVA -> "Java del daemon de Gradle"
    ProviderToolId.GRADLE_TOOLCHAINS -> "Toolchains de Java"
    ProviderToolId.ANDROID_GRADLE_PLUGIN_JAVA -> "Java del Android Gradle Plugin"
    else -> tool.displayName
}

private fun spanishJavaPassing(finding: InstallationFinding.ProviderTool, subject: String): String =
    if (finding.provisioned) "$subject es Java ${finding.version}, que Gradle descarga si falta"
    else "$subject es Java ${finding.version} en ${finding.location}"

private fun spanishBuildTool(tool: ProviderToolId): String = if (tool == ProviderToolId.MAVEN_JAVA) "Maven" else "Gradle"

private fun spanishNewerJava(source: JavaSource, required: Int): String = when (source) {
    JavaSource.ENVIRONMENT -> "Instala Java $required o posterior y apunta JAVA_HOME a él."
    JavaSource.GRADLE_PROPERTY -> "Instala Java $required o posterior y apunta org.gradle.java.home a él."
    JavaSource.DAEMON_CRITERIA -> "Sube el Java del daemon a $required o posterior con ./gradlew updateDaemonJvm --jvm-version=$required."
}

private fun spanishOlderJava(source: JavaSource): String = when (source) {
    JavaSource.ENVIRONMENT -> "apunta JAVA_HOME a un Java anterior"
    JavaSource.GRADLE_PROPERTY -> "apunta org.gradle.java.home a un Java anterior"
    JavaSource.DAEMON_CRITERIA -> "baja toolchainVersion en gradle/gradle-daemon-jvm.properties"
}

private fun spanishMissingJava(tool: ProviderToolId, versions: String): String = if (tool == ProviderToolId.GRADLE_TOOLCHAINS) {
    "Instala un JDK de Java $versions (por ejemplo desde https://adoptium.net), o aplica el plugin org.gradle.toolchains.foojay-resolver-convention en el script de settings para que Gradle lo descargue."
} else {
    "Instala un JDK de Java $versions (por ejemplo desde https://adoptium.net), o añade un repositorio de descarga de toolchains y ejecuta ./gradlew updateDaemonJvm para que Gradle lo descargue."
}

private fun spanishProviderFix(tool: ProviderToolId, problem: ProviderToolProblem): String = when (problem) {
    ProviderToolProblem.NeedsMacOs -> "Usa el proveedor ${tool.provider} en un Mac con Xcode, o ignora este aviso en otros sistemas."
    ProviderToolProblem.NoAndroidVirtualDevice -> "Crea un dispositivo virtual en el Device Manager de Android Studio."
    ProviderToolProblem.NoIosRuntime -> "Instala un runtime de iOS desde Ajustes › Componentes de Xcode."
    is ProviderToolProblem.ProvidersUnreadable -> "Corrige el .shogunai.json del repositorio para que sea válido."
    is ProviderToolProblem.JavaHomeInvalid -> "Apunta JAVA_HOME a la carpeta de un JDK (la que tiene bin/java), o quítala para usar el java de tu PATH."
    is ProviderToolProblem.JavaTooOld -> spanishNewerJava(problem.source, problem.required)
    is ProviderToolProblem.GradleTooOld -> "Actualiza el wrapper de Gradle del proyecto a ${problem.required} o posterior, o ${spanishOlderJava(problem.source)}."
    is ProviderToolProblem.JavaNotInstalled -> spanishMissingJava(tool, problem.versions.joinToString(" y "))
    is ProviderToolProblem.GradleJavaHomeInvalid ->
        "Apunta org.gradle.java.home de ${problem.propertiesFile} a la carpeta de un JDK (la que tiene bin/java), o quítala."
    is ProviderToolProblem.DaemonCriteriaUnreadable -> "Regenera los criterios de JVM del daemon con ./gradlew updateDaemonJvm --jvm-version=<versión>."
    is ProviderToolProblem.AndroidGradlePluginNeedsJava -> spanishNewerJava(problem.source, problem.required)
    is ProviderToolProblem.TooOld -> "Actualiza Docker, o su plugin Compose, a Compose ${problem.minimum} o posterior desde https://docs.docker.com/compose/install/."
    is ProviderToolProblem.DaemonNotRunning -> when (problem.os) {
        HostOs.LINUX -> "Arranca el servicio de Docker con sudo systemctl start docker, o abre Docker Desktop, y vuelve a comprobarlo."
        HostOs.MACOS, HostOs.WINDOWS -> "Abre Docker Desktop, o el motor de Docker que uses, y vuelve a comprobarlo."
    }
    is ProviderToolProblem.NotFound, is ProviderToolProblem.Unusable -> when (tool) {
        ProviderToolId.GRADLE -> "Añade el wrapper de Gradle (gradlew) al repositorio, o instala Gradle desde https://gradle.org/install/."
        ProviderToolId.MAVEN -> "Añade el wrapper de Maven (mvnw) al repositorio, o instala Maven desde https://maven.apache.org/install.html."
        ProviderToolId.GRADLE_JAVA, ProviderToolId.MAVEN_JAVA, ProviderToolId.GRADLE_DAEMON_JAVA, ProviderToolId.GRADLE_TOOLCHAINS,
        ProviderToolId.ANDROID_GRADLE_PLUGIN_JAVA,
        ->
            "Instala un JDK (por ejemplo desde https://adoptium.net) y define JAVA_HOME con su carpeta o pon su java en tu PATH. En macOS, /usr/bin/java solo funciona con un JDK instalado."
        ProviderToolId.ANDROID_SDK -> "Instala el SDK de Android y su emulador con Android Studio (https://developer.android.com/studio), o define ANDROID_HOME con su ubicación."
        ProviderToolId.IOS_SIMULATOR, ProviderToolId.XCODE -> "Instala Xcode desde la App Store y selecciónalo con sudo xcode-select -s /Applications/Xcode.app."
        ProviderToolId.COCOAPODS -> "Instala CocoaPods desde https://cocoapods.org."
        ProviderToolId.DOCKER_COMPOSE, ProviderToolId.DOCKER_DAEMON -> "Instala Docker con su plugin Compose, por ejemplo Docker Desktop desde https://docs.docker.com/get-docker/."
        ProviderToolId.FLUTTER -> "Instala Flutter (https://docs.flutter.dev/get-started/install), o FVM si el repositorio lo usa, y comprueba que está en tu PATH."
        ProviderToolId.PROVIDERS -> "Corrige el .shogunai.json del repositorio para que sea válido."
    }
}

private fun spanishMultirepoFix(finding: InstallationFinding.MultirepoCheck, problem: MultirepoProblem): String = when (problem) {
    MultirepoProblem.DeclarationMissing -> "Restaura ${finding.folder}; si no, ShogunAi olvidará el multirepo la próxima vez que arranque."
    is MultirepoProblem.InvalidDeclaration -> "${problem.path}${problem.detail.asSuffix()}. Corrígelo."
    is MultirepoProblem.RepositoryMissing ->
        "${problem.alias}: ${problem.path} no es un repositorio Git. Restáuralo, o cambia su ruta en ${finding.folder}/$MULTIREPO_FILE_NAME."
    MultirepoProblem.NotTrusted -> "No es de confianza en este equipo. Revísalo y ejecuta shogun multirepo trust ${finding.folder}."
    is MultirepoProblem.ListingFailed -> "No se han podido listar los worktrees de ${problem.alias}${problem.detail.asSuffix()}. Comprueba que es un repositorio Git que puedes leer."
    is MultirepoProblem.GroupWorktreeMissing ->
        "Grupo ${problem.branch}: la carpeta ${problem.path} de su worktree de ${problem.alias} no existe. Restáurala, o ejecuta git worktree prune en ${problem.alias}."
}

private fun spanishCount(count: Int, one: String, many: String): String = if (count == 1) "1 $one" else "$count $many"
