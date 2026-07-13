# Capa de UI

Vive en `desktopApp/src/main/kotlin/app/luxion/shogunai/ui`, más `AppContainer.kt` en la raíz del paquete. Añadida en el cambio `project-worktree-ui` (ver `openspec/changes/archive/`).

## Navegación

Sin `navigation-compose`: `AppRoot` guarda un `var screen by remember { mutableStateOf<Screen>(...) }` y hace `when` sobre un `sealed class Screen` (`ProjectList`, `ProjectConfigForm`, `WorktreeManagement`) para decidir qué pantalla componer. Suficiente para tres pantallas lineales sin deep linking ni back-stack complejo.

## Pantallas y ViewModels

| Pantalla | ViewModel | Responsabilidad |
|---|---|---|
| `ProjectListScreen` | `ProjectListViewModel` | Carga proyectos vía `ProjectRepository` al iniciar; permite seleccionar uno o crear uno nuevo. |
| `ProjectConfigScreen` | `ProjectConfigViewModel` | Formulario de `ProjectConfig` (nombre, rutas, archivos de secretos, preferencia de terminal, comando de agente); valida campos requeridos antes de habilitar guardar. |
| `WorktreeScreen` | `WorktreeViewModel` | Lista, crea y elimina worktrees del proyecto activo, y abre una terminal en el path de un worktree vía los casos de uso existentes; traduce `WorktreeError` a mensajes de usuario. |

Cada ViewModel usa `androidx.lifecycle` viewmodel-compose (`viewModelScope`) para llamar a los casos de uso sin bloquear el hilo de UI.

`WorktreeRow` tiene un botón "Terminal" junto a "Eliminar" que llama a `WorktreeViewModel.openTerminal(worktree)`. `ProjectConfigScreen` tiene una sección "Terminal" (grupo de radio buttons sobre `TerminalSelectionMode`, con selector de `TerminalEmulator` cuando es `FIXED` y un campo de texto para la plantilla argv cuando es `CUSTOM`) y una sección "Agente" (comando del agente, switch de Headroom).

## `AppContainer`

Instancia `JsonProjectRepository`, `NioFileManager`, `ProcessBuilderShellCommandExecutor`, `ProcessTerminalLauncher`, `SystemTerminalEmulatorDetector` y los casos de uso de worktree una sola vez al arrancar la app, y los expone a `AppRoot` para construir cada ViewModel. No hay framework de DI.

## `FilePicker`

Envuelve `JFileChooser` para los botones "Examinar…" de la pantalla de configuración (ruta del repo base, carpeta de worktrees, archivos de secretos). Se eligió sobre un selector nativo de Compose porque Compose Desktop no trae uno y `JFileChooser` ya está disponible en el target JVM sin dependencias nuevas. `relativeToBase` convierte la ruta absoluta de un archivo de secretos elegido a la forma relativa al repo que espera `ProjectConfig.secretFiles`.
