package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.HolderProtection
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.toLocalClockTime
import app.luxion.shogunai.domain.usecase.GitFailureCause

internal fun spanishErrorTexts(error: Throwable, gitCause: GitFailureCause): ErrorTexts = when (error) {
    is WorktreeError.GitCommandFailed -> spanishGitTexts(gitCause)
    is WorktreeError -> spanishWorktreeTexts(error)
    is CommandExecutionException -> ErrorTexts(
        "No se pudo iniciar ${error.command.first()}.",
        "Instala ${error.command.first()} o añádelo al PATH y vuelve a intentarlo.",
    )
    else -> ErrorTexts("Ocurrió un error inesperado.", "Revisa los detalles técnicos.")
}

private fun spanishGitTexts(cause: GitFailureCause): ErrorTexts = when (cause) {
    is GitFailureCause.LockFileExists -> ErrorTexts(
        "Parece que otro proceso de Git se está ejecutando en el repositorio.",
        "Espera a que termine, o elimina ${cause.path?.let { "el archivo de bloqueo $it" } ?: "el archivo de bloqueo"} si no hay ningún proceso de Git en marcha.",
    )
    is GitFailureCause.PermissionDenied -> ErrorTexts(
        "Git no tiene permiso${cause.path?.let { " sobre $it" }.orEmpty()}.",
        "Revisa los permisos de esa carpeta y vuelve a intentarlo.",
    )
    GitFailureCause.NotARepository -> ErrorTexts(
        "La carpeta no es un repositorio de Git.",
        "Revisa la ruta del repositorio en los ajustes del proyecto.",
    )
    is GitFailureCause.BranchExists -> ErrorTexts(
        "La rama \"${cause.name}\" ya existe.",
        "Usa otro id de tarea, o crea el worktree desde esa rama existente.",
    )
    GitFailureCause.LocalChanges -> ErrorTexts(
        "El worktree tiene cambios locales.",
        "Haz commit o descártalos primero, o elimina el worktree forzándolo.",
    )
    GitFailureCause.Unknown -> ErrorTexts("La operación de Git falló.", "Revisa los detalles técnicos.")
}

private fun spanishWorktreeTexts(error: WorktreeError): ErrorTexts = when (error) {
    is WorktreeError.BaseRepositoryNotFound -> ErrorTexts(
        "No se encontró el repositorio del proyecto en ${error.path}.",
        "Actualiza la ruta del repositorio en los ajustes del proyecto.",
    )
    WorktreeError.AppNotAvailable -> ErrorTexts(
        "La app de escritorio de ShogunAi no está disponible en esta instalación.",
        "Instala la app de escritorio para usar 'shogun app'.",
    )
    is WorktreeError.ProjectNotRegistered -> ErrorTexts(
        "No hay ningún proyecto registrado para ${error.path}.",
        "Añade antes el repositorio como proyecto.",
    )
    is WorktreeError.RepositoryNotFound -> ErrorTexts(
        "No se encontró ningún repositorio de Git en ${error.path}.",
        "Actualiza la ruta del repositorio en los ajustes del proyecto.",
    )
    is WorktreeError.BareRepositoryNotSupported -> ErrorTexts(
        "${error.path} es un repositorio bare, que todavía no está soportado.",
        "Usa un repositorio con árbol de trabajo.",
    )
    WorktreeError.RepositoryHasNoCommits -> ErrorTexts(
        "El repositorio todavía no tiene commits.",
        "Haz un primer commit y vuelve a intentarlo.",
    )
    is WorktreeError.InvalidClaudeSettings -> ErrorTexts(
        "El archivo de ajustes de Claude Code ${error.path} no es un JSON válido, así que no se ha modificado.",
        "Corrige el archivo (${error.detail}) e inténtalo de nuevo.",
    )
     is WorktreeError.LocalFileUnreadable ->
        if (error.path.endsWith("settings.json")) {
            ErrorTexts(
                "No se pudo leer el archivo de ajustes ${error.path}, así que se usan los ajustes por defecto.",
                "El archivo ilegible se conserva a su lado. Corrígelo o bórralo para quitar este aviso.",
            )
        } else {
            ErrorTexts(
                "No se pudo leer el archivo de proyectos ${error.path}, así que no se muestra ningún proyecto.",
                "Corrige el archivo. Si añades o quitas un proyecto, el archivo ilegible se conserva antes a su lado.",
            )
        }
    is WorktreeError.WorktreeAlreadyExists -> ErrorTexts(
        "La carpeta ${error.path} ya existe.",
        "Usa otro id de tarea, o elimina la carpeta existente.",
    )
    is WorktreeError.InvalidTaskId -> ErrorTexts(
        "\"${error.rawInput}\" no es un id de tarea válido.",
        "Usa solo letras, dígitos, \"-\", \"_\" y \"/\".",
    )
    is WorktreeError.SecretFileNotFound -> ErrorTexts(
        "No se encontraron estos archivos locales en ${error.basePath}: ${error.files.joinToString()}.",
        "Créalos en el repositorio base, o quítalos de los archivos locales del proyecto.",
    )
    is WorktreeError.InvalidRepositoryConfig -> ErrorTexts(
        "No se pudo leer ${error.path}.",
        "Corrige el archivo siguiendo los detalles técnicos.",
    )
    is WorktreeError.RepositoryConfigChanged -> ErrorTexts(
        "${error.path} cambió en disco desde que se abrieron los ajustes.",
        "Recarga los ajustes y aplica de nuevo tus cambios.",
    )
    is WorktreeError.UpdateCheckFailed -> ErrorTexts(
        "No se pudo buscar actualizaciones" + (error.reason?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "."),
        "Comprueba tu conexión e inténtalo de nuevo más tarde.",
    )
    is WorktreeError.NotAWorktree -> ErrorTexts(
        "${error.path} no es un worktree de este repositorio.",
        "Recarga la lista; puede que la carpeta se haya eliminado.",
    )
    is WorktreeError.SecretCopyFailed -> ErrorTexts(
        "No se pudieron copiar los archivos locales al worktree.",
        "Comprueba que la carpeta del worktree admite escritura.",
    )
    is WorktreeError.PostCreateCommandFailed -> ErrorTexts(
        "El worktree se creó, pero falló uno de los comandos posteriores a la creación del repositorio.",
        "Lee los detalles técnicos, corrige el comando o su causa y ejecútalo dentro del worktree.",
    )
    is WorktreeError.ClipboardWriteFailed -> ErrorTexts(
        "No se pudo copiar el comando de lanzamiento al portapapeles.",
        "Vuelve a intentarlo; puede que otra aplicación esté usando el portapapeles.",
    )
    WorktreeError.GitLfsNotFound -> ErrorTexts(
        "El repositorio usa Git LFS, pero git-lfs no está instalado.",
        "Instala Git LFS desde https://git-lfs.com y ejecuta \"git lfs install\".",
    )
    is WorktreeError.BranchNotFound -> ErrorTexts(
        "No se encontró la rama \"${error.branch}\".",
        "Recarga la lista de ramas; puede que la rama se haya eliminado.",
    )
    is WorktreeError.StartPointNotFound -> ErrorTexts(
        "\"${error.ref}\" no es un commit.",
        "Elige una rama o un commit existente como punto de partida.",
    )
    is WorktreeError.BranchAlreadyCheckedOut -> ErrorTexts(
        "La rama \"${error.branch}\" ya está en uso${error.path?.let { " en $it" }.orEmpty()}.",
        "Abre el worktree existente de esa rama, o elige otra rama.",
    )
    is WorktreeError.ProcessListingFailed -> ErrorTexts(
        "No se pudieron listar los procesos en ejecución.",
        "La detección de agentes no está disponible; comprueba que lsof está instalado.",
    )
    is WorktreeError.DockerUnavailable -> ErrorTexts(
        "Docker no está disponible" + if (error.reason.isNullOrBlank()) "." else ": ${error.reason}",
        "Arranca Docker e inténtalo de nuevo.",
    )
    is WorktreeError.NoFreePort -> ErrorTexts(
        "No queda ningún puerto libre entre ${error.from} y ${error.to}.",
        "Libera puertos de ese rango, o elimina worktrees que no uses.",
    )
    is WorktreeError.NoEmulatorPort -> ErrorTexts(
        "No queda ningún puerto de emulador entre ${error.from} y ${error.to}.",
        "Detén los emuladores de otros worktrees, o elimina worktrees que no uses.",
    )
    is WorktreeError.ResourcePoolFull -> ErrorTexts(
        "Se alcanzó el límite de ${error.limit} ${spanishKindName(error.kind)} y ninguno se puede liberar: " +
            error.holders.joinToString("; ") { "${it.displayName} (${spanishHolderState(it.state)}${spanishProtection(it)})" } +
            spanishWaiting(error.waiting) + ".",
        "Espera a que expire una concesión, detén uno de ellos, o sube el límite en Preferencias.",
    )
    is WorktreeError.DeviceWaitTimedOut -> ErrorTexts(
        "No se liberó ningún ${spanishKindSingular(error.kind)} en ${error.waited.toMinutes()} min: " +
            error.holders.joinToString("; ") { "${it.displayName} (${spanishHolderState(it.state)}${spanishProtection(it)})" } + ".",
        "Detén uno de ellos, o sube el límite de ${error.limit} en Preferencias.",
    )
    is WorktreeError.AndroidSdkNotFound -> ErrorTexts(
        "No se encontró el SDK de Android.",
        "Instala el SDK de Android o define ANDROID_HOME.",
    )
    WorktreeError.NoAvdAvailable -> ErrorTexts(
        "No hay ningún emulador de Android disponible.",
        "Crea uno en el Device Manager de Android Studio.",
    )
    is WorktreeError.EmulatorBootTimeout -> ErrorTexts(
        "${error.serial} no terminó de arrancar en ${error.seconds} s.",
        "Vuelve a intentarlo; el primer arranque puede tardar.",
    )
    is WorktreeError.EmulatorExitedEarly -> ErrorTexts(
        "El emulador de ${error.serial} se detuvo antes de terminar de arrancar.",
        "Revisa el log del emulador en los detalles técnicos; puede que el AVD esté roto.",
    )
    is WorktreeError.AndroidToolFailed -> ErrorTexts(
        "Falló una herramienta de Android.",
        "Revisa los detalles técnicos.",
    )
    WorktreeError.NoIosRuntime -> ErrorTexts(
        "No hay ningún runtime de simulador de iOS instalado.",
        "Instala uno en Xcode > Settings > Components.",
    )
    is WorktreeError.SimulatorBootTimeout -> ErrorTexts(
        "El simulador \"${error.name}\" no terminó de arrancar en ${error.seconds} s.",
        "Vuelve a intentarlo; el primer arranque puede tardar.",
    )
    WorktreeError.IosSimulatorProviderNotDeclared -> ErrorTexts(
        "Este repositorio no usa el proveedor de simuladores de iOS.",
        "Activa el proveedor en los ajustes del proyecto.",
    )
    WorktreeError.EmulatorProviderNotDeclared -> ErrorTexts(
        "Este repositorio no usa el proveedor de emuladores.",
        "Activa el proveedor en los ajustes del proyecto.",
    )
    WorktreeError.IosSimulatorsNotSupported -> ErrorTexts(
        "Los simuladores de iOS no están disponibles en este equipo.",
        "Necesitan macOS con Xcode instalado.",
    )
    is WorktreeError.AppleToolFailed -> ErrorTexts(
        "Falló una herramienta de Xcode.",
        "Revisa los detalles técnicos.",
    )
    is WorktreeError.EnvironmentFileTracked -> ErrorTexts(
        "Git rastrea el archivo de entorno \"${error.path}\", así que no se sobrescribió.",
        "Elige un archivo de entorno que Git no rastree.",
    )
    is WorktreeError.EnvironmentFileOutsideWorktree -> ErrorTexts(
        "El archivo de entorno \"${error.path}\" resuelve fuera del worktree, así que no se escribió.",
        "Elige un archivo de entorno cuyas carpetas sean carpetas reales dentro del worktree.",
    )
    is WorktreeError.WorktreeTargetNotFound -> ErrorTexts(
        "Ningún worktree coincide con \"${error.target}\".",
        "Revisa el id de tarea, la ruta o la rama.",
    )
    is WorktreeError.AmbiguousWorktreeTarget -> ErrorTexts(
        "\"${error.target}\" coincide con más de un worktree.",
        "Usa la ruta del que quieres: ${error.candidates.joinToString()}.",
    )
    is WorktreeError.CannotRemoveMainWorktree -> ErrorTexts(
        "${error.path} es el worktree principal.",
        "El worktree principal no se puede eliminar.",
    )
    is WorktreeError.WorktreeFolderInUse -> ErrorTexts(
        "Un programa está usando la carpeta de ${error.path}.",
        "Cierra la terminal, el editor o el cliente de sincronización que la use y vuelve a eliminar el worktree.${if (error.unregistered) " Git ya lo había desregistrado, así que eliminarlo de nuevo termina el trabajo." else ""}",
    )
    is WorktreeError.AgentRunningInWorktree -> ErrorTexts(
        "${error.agents.joinToString()} se está ejecutando en ${error.path}.",
        "Páralo primero, o elimina el worktree forzándolo.",
    )
    is WorktreeError.InvalidMultirepo -> ErrorTexts(
        "No se puede usar el multirepo ${error.path}: ${error.detail}.",
        "Corrígelo con los comandos shogun multirepo o en los ajustes del multirepo.",
    )
    is WorktreeError.MultirepoNotFound -> ErrorTexts(
        "No se encontró ningún multirepo en ${error.path}.",
        "Añade antes la carpeta como multirepo.",
    )
    is WorktreeError.MultirepoNeedsReview -> ErrorTexts(
        "El multirepo ${error.path} no está aprobado en este equipo.",
        "Revisa sus repositorios y rutas y apruébalo desde la vista del multirepo o con shogun multirepo trust.",
    )
    is WorktreeError.BaseBranchNotFound -> ErrorTexts(
        "No se pudo resolver la rama base de ${error.repository}.",
        "Indica la rama principal del proyecto en sus ajustes.",
    )
    is WorktreeError.TourCreationFailed -> ErrorTexts(
        "No se pudo crear el repositorio de ejemplo del recorrido: falló ${error.command.joinToString(" ")}${error.detail.asSuffix()}.",
        "Comprueba que Git está instalado y funciona en una terminal, y vuelve a empezar el recorrido. No ha quedado nada a medias.",
    )
    is WorktreeError.TourAlreadyExists -> ErrorTexts(
        "La carpeta del recorrido ${error.path} ya existe.",
        "Reinicia el recorrido para sustituirla, o borra la carpeta.",
    )
    is WorktreeError.InvalidRef -> ErrorTexts(
        "\"${error.ref}\" no es una rama ni un punto de partida válidos.",
        "Los nombres de rama y los puntos de partida no pueden empezar por \"-\".",
    )
    is WorktreeError.EnvironmentFileWriteFailed -> ErrorTexts(
        "No se pudo escribir el archivo de entorno \"${error.path}\".",
        "Comprueba que la carpeta admite escritura.",
    )
    WorktreeError.HerdrNotInstalled -> ErrorTexts(
        "Herdr no está instalado.",
        "Instálalo desde https://herdr.dev y vuelve a intentarlo.",
    )
    is WorktreeError.HerdrCommandFailed -> ErrorTexts(
        "Herdr no pudo abrir el worktree.",
        "Revisa los detalles técnicos.",
    )
    is WorktreeError.TerminalLaunchFailed -> ErrorTexts(
        "${error.terminal.displayName} no pudo abrir el worktree.",
        "Revisa los detalles técnicos.",
    )
    is WorktreeError.TerminalAutomationRefused -> ErrorTexts(
        "macOS no permitió que ShogunAi controle ${error.terminal.displayName}.",
        "Permítelo en Ajustes del Sistema > Privacidad y seguridad > Automatización y vuelve a intentarlo.",
    )
    is WorktreeError.TerminalUnsupported -> ErrorTexts(
        "No se puede usar ${error.terminal.displayName}: ${error.reason}.",
        "Elige otro terminal en Preferencias.",
    )
    is WorktreeError.EditorLaunchFailed -> ErrorTexts(
        "${error.editor.displayName} no pudo abrir el worktree.",
        "Revisa los detalles técnicos.",
    )
    is WorktreeError.ClaudeDesktopLaunchFailed -> ErrorTexts(
        "Claude Desktop no pudo abrir el worktree.",
        "Revisa los detalles técnicos.",
    )
    is WorktreeError.FolderOpenFailed -> ErrorTexts(
        "No se pudo mostrar \"${error.path}\".",
        "Revisa los detalles técnicos.",
    )
    is WorktreeError.GitCommandFailed -> spanishGitTexts(GitFailureCause.Unknown)
}

private fun spanishKindName(kind: ResourceKind): String = when (kind) {
    ResourceKind.EMULATOR -> "emuladores"
    ResourceKind.IOS_SIMULATOR -> "simuladores de iOS"
    ResourceKind.GRADLE_DAEMON -> "daemons de Gradle"
}

private fun spanishHolderState(state: PoolHolderState): String = when (state) {
    PoolHolderState.STOPPED -> "detenido"
    PoolHolderState.BOOTING -> "arrancando"
    PoolHolderState.RUNNING -> "en ejecución"
    PoolHolderState.SHUTDOWN -> "apagado"
    PoolHolderState.BOOTED -> "arrancado"
    PoolHolderState.BUSY -> "ocupado"
    PoolHolderState.IDLE -> "inactivo"
    PoolHolderState.UNKNOWN -> "desconocido"
}

private fun spanishWaiting(waiting: Int): String = when (waiting) {
    0 -> ""
    1 -> "; 1 worktree espera uno"
    else -> "; $waiting worktrees esperan uno"
}

private fun spanishKindSingular(kind: ResourceKind): String = when (kind) {
    ResourceKind.EMULATOR -> "emulador"
    ResourceKind.IOS_SIMULATOR -> "simulador"
    ResourceKind.GRADLE_DAEMON -> "daemon de Gradle"
}

private fun spanishProtection(holder: PoolHolder): String = when (holder.protection) {
    HolderProtection.AGENT_WORKING -> ", agente trabajando"
    HolderProtection.LEASED -> ", concesión hasta ${holder.leaseExpiresAt?.toLocalClockTime()}"
    null -> ""
}
