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
    override val selectSecretFilesDialogTitle = "Selecciona archivos de secretos"

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
    override val secretFilesSection = "Archivos de secretos"
    override val removeSecretFile = "Eliminar archivo de secretos"
    override val newSecretFileLabel = "Nuevo archivo de secretos"
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
    override val copied = "Copiado"
    override val copyCommand = "Copiar comando"
    override fun worktreeCreated(path: String) = "Worktree \"$path\" creado"

    override fun baseRepositoryNotFound(path: String) = "El repositorio base no existe: $path"
    override fun worktreeAlreadyExists(path: String) = "El directorio del worktree ya existe: $path"
    override fun invalidTaskIdError(rawInput: String) = "Id de tarea inválido: \"$rawInput\""
    override fun secretFilesNotFound(files: List<String>, basePath: String) =
        "No se encontraron archivos de secretos en $basePath: ${files.joinToString()}"
    override fun gitCommandFailed(command: List<String>, exitCode: Int) =
        "Falló el comando de Git (código de salida $exitCode): ${command.joinToString(" ")}"
    override fun secretCopyFailed(detail: String?) = "No se pudieron copiar los archivos de secretos" + detail.asSuffix()
    override fun clipboardWriteFailed(detail: String?) =
        "No se pudo copiar el comando de lanzamiento al portapapeles" + detail.asSuffix()
    override val gitLfsNotFound =
        "El repositorio requiere Git LFS pero 'git-lfs' no está instalado. Instálalo desde https://git-lfs.com y vuelve a intentarlo."
    override fun branchNotFound(branch: String) = "No se encontró la rama: $branch"
    override fun branchAlreadyCheckedOut(branch: String, path: String?) =
        "La rama '$branch' ya está en uso" + if (path != null) " en $path" else ""
    override fun unexpectedError(detail: String?) = "Error inesperado" + detail.asSuffix()
}
