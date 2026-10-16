# Capa de UI

Vive en `desktopApp/src/main/kotlin/app/luxion/shogunai/ui`, más `AppContainer.kt` en la raíz del paquete.

## Navegación

Sin `navigation-compose`: `AppRoot` guarda un `var screen by remember { mutableStateOf<Screen>(...) }` y hace `when` sobre un `sealed class Screen` (`ProjectList`, `ProjectConfigForm`, `WorktreeManagement`) para decidir qué pantalla componer. Suficiente para tres pantallas lineales sin deep linking ni back-stack complejo.

La barra superior, encima de todas las pantallas, contiene los selectores de idioma y de tema.

## Pantallas y ViewModels

| Pantalla | ViewModel | Responsabilidad |
|---|---|---|
| `ProjectListScreen` | `ProjectListViewModel` | Catálogo de proyectos de `ProjectRepository`: búsqueda por nombre, orden A-Z / Z-A, abrir, editar y eliminar (con confirmación). |
| `ProjectConfigScreen` | `ProjectConfigViewModel` | Formulario de un `Project` (nombre, ruta del repositorio base, carpeta raíz de worktrees, rama principal, archivos de secretos, comando del agente y switch de Headroom); valida los campos requeridos y pregunta antes de descartar cambios sin guardar. |
| `WorktreeScreen` | `WorktreeViewModel` | Lista los worktrees del proyecto activo con su estado; los crea desde una rama nueva o existente, los elimina (opcionalmente borrando la rama, y reintentando con `--force` cuando Git lo pide) y copia su comando de lanzamiento. |

Cada ViewModel usa `androidx.lifecycle` viewmodel-compose (`viewModelScope`) para llamar a los casos de uso sin bloquear el hilo de UI. Las listas se recargan cuando la ventana recupera el foco, así los cambios hechos fuera de la app (una rama creada desde la terminal, un worktree borrado a mano) aparecen sin pulsar "Recargar".

### Crear worktrees

`WorktreeViewModel` posee el estado transitorio del formulario "Nuevo worktree" (`taskId`, `branchType`, `createMode`, `selectedBranch`). `updateTaskId` aplica la normalización (trim + colapso de espacios a `-`) antes de guardar el valor, así el campo siempre muestra el id ya normalizado mientras se escribe.

En el modo "Rama existente" las ramas de `ListLocalBranchesUseCase` se muestran en un `DropdownSelector` agrupadas por el segmento anterior a la primera `/` (u "otras" para las ramas sin ella). Las ramas que ya tienen worktree aparecen deshabilitadas y no se pueden seleccionar, y "Crear worktree" solo se habilita con una rama disponible (`isSelectedBranchAvailable`).

### Estado de los worktrees

Cada fila muestra el estado de su worktree debajo del nombre de la rama. `WorktreeViewModel` lo guarda en `statuses`, un mapa de ruta del worktree a `WorktreeStatusState` (`Loading`, `Loaded` o `Unavailable`):

- Una vez leída la lista, se resuelve la rama principal con `ResolveMainBranchUseCase` (expuesta como `mainBranch`) y se lee el estado de cada worktree con `ReadWorktreeStatusUseCase`, como mucho 4 a la vez. La lista aparece al momento y cada fila muestra un pequeño indicador de progreso hasta que llega su estado.
- Un worktree cuyo estado no se puede leer (p. ej. porque se borró su carpeta) muestra "Estado no disponible" sin afectar a los demás ni al error de la pantalla.
- Recargar la lista, también cuando la ventana recupera el foco, vuelve a leer todos los estados; los resultados de una carga anterior se descartan. Crear un worktree solo lee el estado del nuevo, y eliminarlo descarta su estado.

`WorktreeStatusLine` pinta el estado como segmentos separados por `·` (`statusSegments`): cambios sin commitear (solo si los hay), `↑N ↓N upstream`, `↑N ↓N <rama principal>` y la fecha del último commit en tiempo relativo (`relativeTime`, en `RelativeTime.kt`). Una rama mergeada muestra la etiqueta "mergeada en <rama principal>" en el color primario en lugar de su divergencia con la rama principal.

### Comando de lanzamiento

Cada fila de worktree tiene un botón "Copiar comando" que llama a `WorktreeViewModel.copyLaunchCommand(worktree)`; el botón muestra "Copiado" durante un par de segundos. El usuario pega el comando (`cd "<ruta>" && <comando del agente>`) en la terminal que prefiera.

### Mover un repositorio

Cuando el usuario edita un proyecto porque su repositorio se movió en disco:

- `ProjectConfigViewModel` convierte en relativas las entradas absolutas de archivos de secretos que están dentro del repositorio base, para que sigan al repositorio.
- Cambiar la ruta del repositorio base también mueve la carpeta raíz de worktrees cuando estaba dentro del repositorio, era su carpeta padre o una carpeta hermana, salvo que el usuario la haya editado a mano (`RepositoryRelocation.kt`).
- Al guardar con otra ruta base u otra carpeta raíz de worktrees, `AppRoot` abre la pantalla de worktrees con la carpeta raíz anterior, y `WorktreeViewModel.repairAfterMove` ejecuta `RepairWorktreesUseCase` antes de listar. Si la reparación falla, el error queda visible.
- Los ViewModels del formulario y de la pantalla de worktrees se identifican por la configuración del proyecto, no solo por su id, así un proyecto editado nunca reutiliza casos de uso ligados a las rutas antiguas.

Las cargas de la lista llevan un número de generación: un resultado solo se aplica si no ha empezado otra carga más reciente entretanto, así una carga anterior lenta no puede pisar un estado más nuevo (ni borrar un error más nuevo).

## Componentes compartidos (`ui/components`)

- **`Spacing`**: escala de espaciado (`xs=4dp`, `sm=8dp`, `md=16dp`, `lg=24dp`) usada en vez de valores `dp` sueltos.
- **`SegmentedSelector<T>`**: control segmentado (`SingleChoiceSegmentedButtonRow`/`SegmentedButton`) para un conjunto pequeño y fijo de opciones siempre visibles (p. ej. `CreateMode`, `BranchType`). Las etiquetas se dimensionan a su texto completo (`Modifier.width(IntrinsicSize.Max)`): la fila calcula el ancho de sus segmentos a partir de su ancho mínimo intrínseco, que para un texto normal es solo su palabra más larga.
- **`DropdownSelector<T>`**: dropdown (`ExposedDropdownMenuBox`) para listas más largas o cargadas dinámicamente, con grupos plegables opcionales (`groupBy`) y opciones deshabilitadas (`enabled`), que se ignoran sea cual sea el método de entrada.
- **`SectionCard`**: `OutlinedCard` con título, usada para agrupar secciones de un formulario largo (todas las secciones de `ProjectConfigScreen`, y el bloque "Nuevo worktree" de `WorktreeScreen`).
- **Atajos de teclado** (`KeyboardShortcuts.kt`): `onEnterKey` lanza la acción principal de un campo de texto (p. ej. añadir un archivo de secretos), y `onNewItemShortcut` asocia Cmd+N (macOS) / Ctrl+N (otros sistemas) a "Nuevo proyecto" y "Crear worktree".

## Tema (`ui/theme`)

`ShogunAiTheme` aplica el esquema de colores claro u oscuro según el `ThemeMode` guardado, que se elige con `ThemeModeToggle` en la barra superior. En modo `SYSTEM`, `rememberSystemInDarkTheme` consulta el tema del sistema operativo cada segundo, porque `isSystemInDarkTheme()` de Compose Desktop no reacciona a cambios con la app abierta.

## `AppContainer`

Instancia `JsonProjectRepository`, los repositorios de preferencias, `NioFileManager`, `ProcessBuilderShellCommandExecutor` y `AwtClipboardWriter` una sola vez al arrancar la app. Los casos de uso de worktrees dependen de un proyecto, así que `worktreeUseCases(config)` construye un `WorktreeUseCases` para la `ProjectConfig` seleccionada. No hay framework de DI.

## `FilePicker`

Envuelve `JFileChooser` para los botones "Examinar…" de la pantalla de configuración (ruta del repo base, carpeta de worktrees, archivos de secretos). Se eligió sobre un selector nativo de Compose porque Compose Desktop no trae uno y `JFileChooser` ya está disponible en el target JVM sin dependencias nuevas. `relativeToBase` convierte la ruta absoluta de un archivo de secretos elegido a la forma relativa al repo que espera `ProjectConfig.secretFiles`.

## Idiomas (`ui/i18n`)

La interfaz está disponible en inglés (por defecto) y español. Todos los textos visibles viven en `interface Strings`, con una implementación por idioma (`EnglishStrings`, `SpanishStrings`); los textos con parámetros son funciones (`deleteProjectMessage(projectName)`), así los argumentos se comprueban al compilar.

- `AppRoot` carga el `AppLanguage` guardado (`LanguagePreferenceRepository`) y provee la tabla activa con `CompositionLocalProvider(LocalStrings provides language.strings())` por encima del `when` de pantallas: cambiar de idioma recompone los textos sin recrear ViewModels ni perder el estado de la pantalla.
- Los composables leen los textos con `val strings = LocalStrings.current`. El código no composable (como `FilePicker`) recibe el texto ya traducido como parámetro.
- `LanguageToggle`, junto a `ThemeModeToggle` en la barra superior, muestra cada idioma por su propio nombre ("English", "Español").
- Los ViewModels exponen datos, no mensajes: `WorktreeViewModel` expone `error: Throwable?` y `success: WorktreeSuccess?` (worktree creado, worktrees reparados tras mover el repositorio), y la pantalla los formatea con `strings.worktreeError(error)` y el texto correspondiente de `Strings`. `worktreeError` es exhaustivo sobre la jerarquía sellada `WorktreeError`, así una variante nueva no compila hasta que se traduce; un proceso que no puede arrancar (`CommandExecutionException`) tiene su propio mensaje, y cualquier otro error usa uno genérico. La salida de error de Git se muestra tal cual, sin traducir.

**Añadir un texto**: declarar el miembro en `Strings` e implementarlo en cada idioma (el compilador señala cualquiera que falte). **Añadir un idioma**: nuevo valor en `AppLanguage`, nuevo `object` que implemente `Strings` y su rama en `AppLanguage.strings()`.
