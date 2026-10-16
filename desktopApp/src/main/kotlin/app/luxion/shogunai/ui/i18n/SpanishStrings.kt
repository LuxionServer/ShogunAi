package app.luxion.shogunai.ui.i18n

object SpanishStrings : Strings {
    override val languageName = "Español"

    override val themeLight = "Claro"
    override val themeDark = "Oscuro"
    override val themeSystem = "Automático"

    override val reload = "Recargar"
    override val cancel = "Cancelar"
    override val delete = "Eliminar"
    override val browse = "Examinar…"
    override val expand = "Expandir"
    override val collapse = "Contraer"

    override val selectFolderDialogTitle = "Selecciona una carpeta"
    override val selectLocalFilesDialogTitle = "Selecciona archivos locales"

    override val projectsTitle = "Proyectos"
    override val newProject = "Nuevo proyecto"
    override val search = "Buscar"
    override val noProjectsYet = "No hay proyectos todavía. Crea uno para empezar."
    override fun noProjectsMatch(query: String) = "Ningún proyecto coincide con \"$query\"."
    override val editProject = "Editar proyecto"
    override val deleteProject = "Eliminar proyecto"
    override val deleteProjectTitle = "¿Eliminar proyecto?"
    override fun deleteProjectMessage(projectName: String) = "Se eliminará \"$projectName\" de la lista de proyectos."

    override val projectConfigTitle = "Configuración de proyecto"
    override val identitySection = "Identidad"
    override val nameLabel = "Nombre"
    override val nameEmptyError = "El nombre no puede estar vacío"
    override val pathsSection = "Rutas"
    override val baseRepositoryPathLabel = "Ruta del repositorio base"
    override val baseRepositoryPathEmptyError = "La ruta del repositorio no puede estar vacía"
    override val worktreesRootLabel = "Carpeta raíz de worktrees"
    override val mainBranchLabel = "Rama principal"
    override val mainBranchPlaceholder = "Detectada a partir del repositorio"
    override val localFilesSection = "Archivos locales"
    override val removeLocalFile = "Eliminar archivo local"
    override val newLocalFileLabel = "Nuevo archivo local"
    override val localFilesDescription =
        "Rutas exactas que se copian a cada nuevo worktree. Solo se guardan en esta máquina."
    override val repositoryProvisioningSection = "Del repositorio"
    override val repositoryProvisioningDescription =
        "Patrones versionados en .shogunai.json y .worktreeinclude. Cada archivo no versionado que coincida se copia a cada nuevo worktree."
    override val repositoryProvisioningEmpty = "El repositorio no declara nada que copiar."
    override fun repositoryConfigUnreadable(path: String) =
        "No se puede leer $path. Corrígelo antes de crear worktrees."
    override val moveToRepositoryConfig = "Mover a .shogunai.json"
    override val pendingSave = "al guardar"
    override fun saveFailed(detail: String?) = "No se pudo guardar" + detail.asSuffix()
    override val add = "Añadir"
    override val agentSection = "Agente"
    override val agentCommandLabel = "Comando del agente"
    override val useHeadroomLabel = "Usar Headroom (headroom wrap)"
    override val headroomDescription = "Headroom envuelve el comando del agente para comprimir el contexto antes de lanzarlo."
    override val save = "Guardar"
    override val discardChangesTitle = "¿Descartar cambios?"
    override val discardChangesMessage = "Hay cambios sin guardar que se perderán si continúas."
    override val discard = "Descartar"
    override val keepEditing = "Seguir editando"

    override val closeError = "Cerrar error"
    override val closeMessage = "Cerrar mensaje"
    override val newWorktreeSection = "Nuevo worktree"
    override val newBranchMode = "Rama nueva"
    override val existingBranchMode = "Rama existente"
    override val taskIdLabel = "Id de tarea"
    override val taskIdHint = "Ej: TASK-123 (los espacios se convierten en guiones)"
    override val invalidTaskId = "Id de tarea inválido"
    override val createWorktree = "Crear worktree"
    override val loadingBranches = "Cargando ramas..."
    override val noBranchesAvailable = "No hay ramas disponibles para crear un worktree."
    override fun branchAlreadyHasWorktree(branch: String) = "$branch (ya tiene un worktree)"
    override val selectBranchPlaceholder = "Selecciona una rama"
    override val otherBranchesGroup = "otras"
    override val worktreesTitle = "Worktrees"
    override val removeWorktreeTitle = "¿Eliminar worktree?"
    override fun removeWorktreeMessage(path: String) = "¿Eliminar el worktree \"$path\"?"
    override val forceRemoveTitle = "¿Forzar eliminación?"
    override fun forceRemoveMessage(path: String) =
        "\"$path\" tiene cambios sin guardar. Se perderán si se elimina el worktree."
    override fun alsoDeleteLocalBranch(branch: String) = "Eliminar también la rama local ($branch)"
    override val forceRemove = "Forzar eliminación"
    override val detachedHead = "(detached)"
    override fun uncommittedChanges(count: Int) = if (count == 1) "1 cambio" else "$count cambios"
    override val upstream = "upstream"
    override fun mergedInto(branch: String) = "mergeada en $branch"
    override val statusUnavailable = "Estado no disponible"
    override val folderMissing = "Carpeta no encontrada"
    override val originShogunAi = "ShogunAi"
    override val originClaudeCode = "Claude Code"
    override val originOther = "otra ubicación"
    override val inactive = "inactivo"
    override fun worktreesSummary(count: Int, totalSize: String) =
        (if (count == 1) "1 worktree" else "$count worktrees") + " · $totalSize"
    override val locked = "bloqueado"
    override val cleanUp = "Limpiar"
    override val cleanUpTitle = "Limpiar worktrees"
    override fun cleanUpMessage(count: Int) =
        if (count == 1) "Este worktree está mergeado o ha perdido su carpeta:" else "Estos $count worktrees están mergeados o han perdido su carpeta:"
    override val alsoDeleteLocalBranches = "Borrar también sus ramas locales"
    override fun worktreesCleanedUp(count: Int) = if (count == 1) "1 worktree limpiado" else "$count worktrees limpiados"
    override fun cleanupFailed(path: String, detail: String) = "No se pudo eliminar \"$path\": $detail"
    override fun branchKept(branch: String) = "Rama \"$branch\" conservada: no está mergeada en la rama principal"
    override val alsoStopIdleGradleDaemons = "Parar también sus daemons de Gradle ociosos"
    override val gradleDaemonsTitle = "Daemons de Gradle"
    override fun gradleDaemonsSummary(count: Int, totalMemory: String) =
        (if (count == 1) "1 daemon" else "$count daemons") + " · $totalMemory"
    override val noGradleDaemons = "No hay daemons de Gradle ni de Kotlin en ejecución"
    override val gradleDaemonsUnavailable = "No se pueden inspeccionar los daemons en este sistema"
    override fun gradleDaemonLabel(version: String?) = listOfNotNull("Gradle", version).joinToString(" ")
    override val kotlinDaemonLabel = "Compilador de Kotlin"
    override val daemonBusy = "ocupado"
    override fun daemonIdle(since: String) = "ocioso · $since"
    override val daemonStateUnknown = "estado desconocido"
    override val daemonOwnerRemoved = "worktree eliminado"
    override val daemonOwnerOther = "otro proyecto"
    override val stopDaemon = "Parar"
    override val stopIdleDaemons = "Parar los ociosos"
    override val gradleBuildCacheOff =
        "La caché de build de Gradle está desactivada: añade org.gradle.caching=true a gradle.properties para que un worktree nuevo reutilice lo que otros worktrees ya compilaron."
    override fun daemonsStopped(stopped: Int, kept: Int, failed: Int) = listOfNotNull(
        if (stopped == 1) "1 daemon parado" else "$stopped daemons parados",
        kept.takeIf { it > 0 }?.let { if (it == 1) "1 conservado porque está en uso" else "$it conservados porque están en uso" },
        failed.takeIf { it > 0 }?.let { if (it == 1) "1 no se pudo parar" else "$it no se pudieron parar" },
    ).joinToString(", ")
    override val justNow = "ahora mismo"
    override fun minutesAgo(count: Long) = if (count == 1L) "hace 1 minuto" else "hace $count minutos"
    override fun hoursAgo(count: Long) = if (count == 1L) "hace 1 hora" else "hace $count horas"
    override fun daysAgo(count: Long) = if (count == 1L) "hace 1 día" else "hace $count días"
    override fun monthsAgo(count: Long) = if (count == 1L) "hace 1 mes" else "hace $count meses"
    override fun yearsAgo(count: Long) = if (count == 1L) "hace 1 año" else "hace $count años"
    override val copied = "Copiado"
    override val copyCommand = "Copiar comando"
    override fun worktreeCreated(path: String) = "Worktree \"$path\" creado"
    override val worktreesRepairedAfterMove = "Worktrees reparados tras mover el repositorio"

    override fun baseRepositoryNotFound(path: String) = "El repositorio base no existe: $path"
    override fun worktreeAlreadyExists(path: String) = "El directorio del worktree ya existe: $path"
    override fun invalidTaskIdError(rawInput: String) = "Id de tarea inválido: \"$rawInput\""
    override fun secretFilesNotFound(files: List<String>, basePath: String) =
        "No se encontraron archivos locales en $basePath: ${files.joinToString()}"
    override fun invalidRepositoryConfig(path: String, detail: String?) = "No se puede leer $path" + detail.asSuffix()
    override fun repositoryNotFound(path: String) = "No hay un repositorio Git en $path"
    override fun notAWorktree(path: String) = "$path no es un worktree enlazado de este repositorio"
    override fun gitCommandFailed(command: List<String>, exitCode: Int) =
        "Falló el comando de Git (código de salida $exitCode): ${command.joinToString(" ")}"
    override fun secretCopyFailed(detail: String?) = "No se pudieron copiar los archivos al worktree" + detail.asSuffix()
    override fun clipboardWriteFailed(detail: String?) =
        "No se pudo copiar el comando de lanzamiento al portapapeles" + detail.asSuffix()
    override val gitLfsNotFound =
        "El repositorio requiere Git LFS pero 'git-lfs' no está instalado. Instálalo desde https://git-lfs.com y vuelve a intentarlo."
    override fun branchNotFound(branch: String) = "No se encontró la rama: $branch"
    override fun branchAlreadyCheckedOut(branch: String, path: String?) =
        "La rama '$branch' ya está en uso" + if (path != null) " en $path" else ""
    override fun noFreePort(from: Int, to: Int) = "No queda ningún puerto libre entre $from y $to para este worktree"
    override fun environmentFileTracked(path: String) =
        "El fichero de entorno \"$path\" está versionado en Git, así que no se ha sobrescrito"
    override fun environmentFileWriteFailed(path: String, detail: String?) =
        "No se pudo escribir el fichero de entorno \"$path\"" + if (detail.isNullOrBlank()) "" else ": $detail"
    override fun processListingFailed(command: List<String>, exitCode: Int) =
        "No se pudieron listar los procesos (código de salida $exitCode): ${command.joinToString(" ")}"
    override fun commandCouldNotStart(command: List<String>) =
        "No se pudo iniciar el comando: ${command.joinToString(" ")}"
    override fun unexpectedError(detail: String?) = "Error inesperado" + detail.asSuffix()
}
