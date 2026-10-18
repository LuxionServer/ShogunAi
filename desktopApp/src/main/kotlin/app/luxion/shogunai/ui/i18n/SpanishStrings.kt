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
    override val addProject = "Añadir proyecto"
    override val editProject = "Editar proyecto"
    override val removeProject = "Quitar proyecto"
    override val removeProjectTitle = "¿Quitar proyecto?"
    override fun removeProjectMessage(projectName: String) =
        "Se quitará \"$projectName\" de ShogunAi. Su repositorio y sus worktrees se quedan en el disco."
    override val remove = "Quitar"
    override val emptyStateTitle = "Añade tu primer proyecto"
    override val emptyStateMessage =
        "Un proyecto es un repositorio Git cuyos worktrees gestiona ShogunAi: dónde se crean, qué ficheros locales reciben y qué agente los abre."
    override val collapseSidebar = "Contraer barra lateral"
    override val expandSidebar = "Expandir barra lateral"

    override val menuFile = "Archivo"
    override val menuView = "Ver"
    override val menuHelp = "Ayuda"
    override val menuWorktree = "Worktree"
    override val menuPreferences = "Preferencias…"
    override val menuQuit = "Salir"
    override val menuToggleSidebar = "Mostrar u ocultar barra lateral"
    override val menuAbout = "Acerca de ShogunAi"

    override val preferencesTitle = "Preferencias"
    override val appearanceLabel = "Apariencia"
    override val languageLabel = "Idioma"

    override val aboutTitle = "Acerca de ShogunAi"
    override val aboutTagline = "La capa de worktrees para cualquier agente de IA."
    override fun aboutVersion(version: String) = "Versión $version"
    override val aboutRepositoryLink = "Ver en GitHub"

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
    override val newWorktree = "Nuevo worktree"
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
    override val viewsTitle = "Vistas"
    override val allWorktrees = "Todos los worktrees"
    override val activeWorktrees = "Activos"
    override val groupAgentWorking = "Con agente"
    override val groupNeedsYou = "Te necesitan"
    override val groupInProgress = "En curso"
    override val groupMerged = "Fusionados"
    override val groupInactive = "Inactivos"
    override val groupFolderMissing = "Carpeta no encontrada"
    override val groupOther = "Otros"
    override val groupByState = "Por estado"
    override val groupByProject = "Por proyecto"
    override val readingWorktreeStatus = "Leyendo el estado de los worktrees…"
    override val nothingActiveTitle = "No hay nada activo"
    override val nothingActiveMessage = "No hay agentes en ejecución ni worktrees con trabajo en curso."
    override val seeAllWorktrees = "Ver todos los worktrees"
    override val menuActive = "Mostrar activos"
    override val menuAllWorktrees = "Mostrar todos los worktrees"
    override fun agentsRunning(names: List<String>) = "Agentes en ejecución: ${names.joinToString(", ")}"
    override val allProjects = "Todos los proyectos"
    override val projectLabel = "Proyecto"
    override val filterWorktreesPlaceholder = "Filtrar por rama o carpeta"
    override val noWorktreeMatches = "Ningún worktree coincide con el filtro"
    override val clearFilter = "Borrar filtro"
    override val menuFind = "Buscar"
    override val loadingWorktrees = "Cargando worktrees…"
    override fun worktreeCount(count: Int) = if (count == 1) "1 worktree" else "$count worktrees"
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
    override val mainWorktree = "principal"
    override val originShogunAi = "ShogunAi"
    override val originClaudeCode = "Claude Code"
    override val originHerdr = "Herdr"
    override val originOther = "otra ubicación"
    override val inactive = "inactivo"
    override fun worktreesSummary(count: Int, totalSize: String) =
        (if (count == 1) "1 worktree" else "$count worktrees") + " · $totalSize"
    override fun cleanupCandidatesSummary(count: Int) = "$count para limpiar"
    override val locked = "bloqueado"
    override val cleanUp = "Limpiar"
    override val copyPath = "Copiar ruta"
    override val statusTitle = "Estado"
    override val agentsTitle = "Agentes"
    override val environmentTitle = "Entorno"
    override val devicesTitle = "Dispositivos"
    override val diskTitle = "Disco"
    override val deleteWorktree = "Eliminar worktree"
    override val clearSelection = "Quitar selección"
    override val cleanUpTitle = "Limpiar worktrees"
    override fun cleanUpMessage(count: Int) =
        if (count == 1) "Este worktree está mergeado o ha perdido su carpeta:" else "Estos $count worktrees están mergeados o han perdido su carpeta:"
    override val alsoDeleteLocalBranches = "Borrar también sus ramas locales"
    override fun worktreesCleanedUp(count: Int) = if (count == 1) "1 worktree limpiado" else "$count worktrees limpiados"
    override fun cleanupFailed(path: String, detail: String) = "No se pudo eliminar \"$path\": $detail"
    override fun branchKept(branch: String) = "Rama \"$branch\" conservada: no está mergeada en la rama principal"
    override val simulatorSection = "Simulador de iOS"
    override val simulatorSectionDescription =
        "Este repositorio da a cada worktree su propio simulador de iOS (IOS_SIMULATOR_UDID). Estos ajustes solo se aplican en esta máquina."
    override val simulatorRuntimeLabel = "Runtime"
    override val simulatorDeviceLabel = "Dispositivo"
    override val simulatorAutomatic = "Automático"
    override fun notOnThisMachine(name: String) = "$name (no está en esta máquina)"
    override val simulatorTestsWired = "Los tests de simulador KMP usan el simulador de cada worktree."
    override val simulatorTestsNotWired =
        "Los tests de simulador KMP aún no usan el simulador de cada worktree. Añade esto al build.gradle.kts del módulo:"
    override val copySnippet = "Copiar"
    override val installInitScript = "Instalar para todos los proyectos de esta máquina"
    override val installInitScriptTitle = "¿Instalar el init script de Gradle?"
    override fun installInitScriptMessage(path: String) =
        "ShogunAi escribirá el init script de Gradle $path para que los tests de simulador iOS de todos los " +
            "proyectos Gradle de esta máquina se ejecuten en IOS_SIMULATOR_UDID cuando esté definida. Quítalo con 'shogun simulator gradle-init uninstall'."
    override val install = "Instalar"
    override val simulatorLabel = "Simulador de iOS"
    override val simulatorShutdown = "apagado"
    override val simulatorBooting = "arrancando"
    override val simulatorBooted = "arrancado"
    override val startSimulator = "Arrancar"
    override val stopSimulator = "Apagar"
    override val alsoDeleteSimulators = "Borrar también los simuladores de iOS de los worktrees que se eliminan y los simuladores huérfanos de ShogunAi"
    override fun orphanedSimulators(names: List<String>) =
        "Simuladores cuyo worktree ya no existe, que también se borran: ${names.joinToString()}"
    override fun simulatorsDeleted(count: Int) = if (count == 1) "1 simulador borrado" else "$count simuladores borrados"
    override val emulatorSection = "Emulador de Android"
    override val emulatorSectionDescription =
        "Este repositorio da a cada worktree su propio emulador (ANDROID_SERIAL). Estos ajustes solo se aplican en esta máquina."
    override val emulatorAvdLabel = "AVD"
    override val emulatorAutomaticAvd = "Automática (la AVD preferida del repositorio, o la primera)"
    override fun emulatorMissingAvd(name: String) = "$name (no está en esta máquina)"
    override val emulatorNoWindowLabel = "Ejecutar sin ventana"
    override val emulatorLabel = "Emulador"
    override val emulatorStopped = "parado"
    override val emulatorBooting = "arrancando"
    override val emulatorRunning = "en marcha"
    override val startEmulator = "Arrancar"
    override val stopEmulator = "Parar"
    override val alsoStopEmulators = "Parar también los emuladores de los worktrees que se eliminan"
    override fun emulatorsStopped(count: Int) = if (count == 1) "1 emulador parado" else "$count emuladores parados"
    override val alsoStopIdleGradleDaemons = "Parar también los daemons de Gradle ociosos de los worktrees que se eliminan"
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
    override fun gradleWorktreeSettings(daemonHeap: String?, idleTimeoutMinutes: Int?, kotlinDaemonHeap: String?) =
        "Builds de los worktrees: " + listOfNotNull(
            daemonHeap?.let { "heap de $it" },
            idleTimeoutMinutes?.let { "inactivo tras $it min" },
            kotlinDaemonHeap?.let { "Kotlin $it" },
        ).joinToString(" · ")
    override fun daemonsStopped(stopped: Int, kept: Int, failed: Int) = listOfNotNull(
        if (stopped == 1) "1 daemon parado" else "$stopped daemons parados",
        kept.takeIf { it > 0 }?.let { if (it == 1) "1 conservado porque está en uso" else "$it conservados porque están en uso" },
        failed.takeIf { it > 0 }?.let { if (it == 1) "1 no se pudo parar" else "$it no se pudieron parar" },
    ).joinToString(", ")
    override val justNow = "ahora mismo"

    override val xcodeDerivedDataTitle = "DerivedData de Xcode"
    override val xcodeDerivedDataUnavailable = "No se pueden inspeccionar los DerivedData en este sistema"
    override val noDerivedDataFolders = "Aún no hay DerivedData para estos worktrees"
    override fun derivedDataSummary(count: Int, totalSize: String) =
        (if (count == 1) "1 carpeta de DerivedData" else "$count carpetas de DerivedData") + " · $totalSize"
    override fun derivedDataOrphans(count: Int, size: String) =
        if (count == 1) "1 carpeta huérfana · $size" else "$count carpetas huérfanas · $size"
    override val deleteOrphans = "Eliminar huérfanas"
    override fun derivedDataOnUnavailableVolumes(count: Int) =
        if (count == 1) "1 carpeta está en un volumen no montado y se conserva"
        else "$count carpetas están en volúmenes no montados y se conservan"
    override fun derivedDataCompilationCache(size: String) = "Caché de compilación · $size"
    override fun derivedDataDeleted(count: Int, freed: String) =
        if (count == 1) "1 carpeta de DerivedData borrada · $freed liberados"
        else "$count carpetas de DerivedData borradas · $freed liberados"
    override val alsoDeleteDerivedData = "Borrar también los DerivedData de Xcode huérfanos"
    override fun orphanedDerivedData(count: Int, size: String) =
        if (count == 1) "DerivedData huérfanos, que también se borran: 1 carpeta · $size"
        else "DerivedData huérfanos, que también se borran: $count carpetas · $size"
    override fun minutesAgo(count: Long) = if (count == 1L) "hace 1 minuto" else "hace $count minutos"
    override fun hoursAgo(count: Long) = if (count == 1L) "hace 1 hora" else "hace $count horas"
    override fun daysAgo(count: Long) = if (count == 1L) "hace 1 día" else "hace $count días"
    override fun monthsAgo(count: Long) = if (count == 1L) "hace 1 mes" else "hace $count meses"
    override fun yearsAgo(count: Long) = if (count == 1L) "hace 1 año" else "hace $count años"
    override val copied = "Copiado"
    override val copyCommand = "Copiar comando"
    override val copyLaunchCommand = "Copiar comando de lanzamiento"
    override fun worktreeCreated(folder: String) = "Worktree creado: $folder"
    override val worktreesRepairedAfterMove = "Worktrees reparados tras mover el repositorio"
    override val openInHerdr = "Abrir en Herdr"
    override fun openedInHerdr(name: String) = "\"$name\" abierto en Herdr con su agente"
    override fun focusedInHerdr(name: String) = "\"$name\" ya estaba abierto en Herdr; se ha cambiado a su workspace"

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
    override fun noEmulatorPort(from: Int, to: Int) =
        "No queda ningún puerto de emulador entre $from y $to; para o elimina los emuladores de otros worktrees"
    override fun androidSdkNotFound(checked: List<String>) =
        "No se encuentra el SDK de Android. Se ha buscado en: ${checked.joinToString()}"
    override val noAvdAvailable =
        "No hay ningún dispositivo virtual de Android. Crea uno en el Device Manager de Android Studio y vuelve a intentarlo."
    override val herdrNotInstalled = "Herdr no está instalado. Instálalo desde https://herdr.dev y vuelve a intentarlo."
    override fun herdrCommandFailed(detail: String) = "No se pudo abrir el worktree en Herdr: $detail"
    override fun emulatorBootTimeout(serial: String, seconds: Long) =
        "$serial no ha terminado de arrancar en $seconds s; sigue arrancando"
    override fun androidToolFailed(command: List<String>, exitCode: Int) =
        "Falló una herramienta de Android (código $exitCode): ${command.joinToString(" ")}"
    override val noIosRuntime =
        "No hay ningún runtime de simulador de iOS instalado. Instala uno desde Ajustes > Componentes de Xcode y vuelve a intentarlo."
    override fun simulatorBootTimeout(name: String, seconds: Long) =
        "El simulador \"$name\" no ha terminado de arrancar en $seconds s; sigue arrancando"
    override val iosSimulatorProviderNotDeclared =
        "Este repositorio no usa el provider de simulador de iOS. Añade \"providers\": { \"ios-simulator\": {} } a .shogunai.json."
    override val iosSimulatorsNotSupported = "Los simuladores de iOS necesitan macOS con Xcode instalado."
    override fun appleToolFailed(command: List<String>, exitCode: Int) =
        "Falló una herramienta de Xcode (código $exitCode): ${command.joinToString(" ")}"
    override val emulatorProviderNotDeclared =
        "Este repositorio no usa el provider de emulador. Añade \"providers\": { \"emulator\": {} } a .shogunai.json."
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
