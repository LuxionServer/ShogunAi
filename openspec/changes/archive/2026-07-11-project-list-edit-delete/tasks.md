## 1. ViewModel

- [x] 1.1 Añadir `deleteProject(project: Project)` a `ProjectListViewModel`: llama a `projectRepository.delete(project.id)` y luego `refresh()`.

## 2. Pantalla de listado

- [x] 2.1 Añadir parámetro `onEditProject: (Project) -> Unit` a `ProjectListScreen`.
- [x] 2.2 Añadir `IconButton` de editar y de eliminar al `trailingContent` de cada `ListItem`, sin interferir con el `clickable` de la fila (navegar a worktrees).
- [x] 2.3 Al pulsar editar, invocar `onEditProject(project)`.
- [x] 2.4 Al pulsar eliminar, guardar el proyecto en un estado local (`remember { mutableStateOf<Project?>(null) }`) para abrir el diálogo de confirmación en vez de eliminar directamente.
- [x] 2.5 Añadir un `AlertDialog` de confirmación ("¿Eliminar proyecto?") que, al confirmar, llame a `viewModel.deleteProject(project)` y cierre el diálogo; al cancelar, solo cierre el diálogo sin eliminar.

## 3. Navegación (AppRoot)

- [x] 3.1 Pasar `onEditProject = { screen = Screen.ProjectConfigForm(existingProject = it) }` al `ProjectListScreen` en `AppRoot`.

## 4. Verificación

- [x] 4.1 Ejecutar `./gradlew test` y confirmar que sigue en verde.
- [x] 4.2 Probar manualmente con `./gradlew :desktopApp:run`: editar un proyecto existente y confirmar que los cambios persisten; eliminar un proyecto y confirmar que desaparece de la lista y del JSON persistido; cancelar una eliminación y confirmar que el proyecto permanece.
