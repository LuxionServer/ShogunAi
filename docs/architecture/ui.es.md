# Capa de UI

Vive en el módulo `desktopApp`: `desktopApp/src/main/kotlin/app/luxion/shogunai/ui`, más `AppContainer.kt` en la raíz del paquete. Usa el dominio y la infraestructura del módulo `core`.

## Navegación

Sin `navigation-compose`: `AppRoot` guarda un `var screen by remember { mutableStateOf<Screen>(...) }` y hace `when` sobre un `sealed class Screen` (`ProjectList`, `ProjectConfigForm`, `WorktreeManagement`) para decidir qué pantalla componer. Suficiente para tres pantallas lineales sin deep linking ni back-stack complejo.

La barra superior, encima de todas las pantallas, contiene los selectores de idioma y de tema.

## Pantallas y ViewModels

| Pantalla | ViewModel | Responsabilidad |
|---|---|---|
| `ProjectListScreen` | `ProjectListViewModel` | Catálogo de proyectos de `ProjectRepository`: búsqueda por nombre, orden A-Z / Z-A, abrir, editar y eliminar (con confirmación). |
| `ProjectConfigScreen` | `ProjectConfigViewModel` | Formulario de un `Project` (nombre, ruta del repositorio base, carpeta raíz de worktrees, rama principal, el provisionado del repositorio, archivos locales, comando del agente y switch de Headroom); valida los campos requeridos y pregunta antes de descartar cambios sin guardar. |
| `WorktreeScreen` | `WorktreeViewModel` | Lista los worktrees del proyecto activo con su estado; los crea desde una rama nueva o existente, los elimina (opcionalmente borrando la rama, y reintentando con `--force` cuando Git lo pide) y copia su comando de lanzamiento. |

Cada ViewModel usa `androidx.lifecycle` viewmodel-compose (`viewModelScope`) para llamar a los casos de uso sin bloquear el hilo de UI. Las listas se recargan cuando la ventana recupera el foco, así los cambios hechos fuera de la app (una rama creada desde la terminal, un worktree borrado a mano) aparecen sin pulsar "Recargar".

### Crear worktrees

`WorktreeViewModel` posee el estado transitorio del formulario "Nuevo worktree" (`taskId`, `branchType`, `createMode`, `selectedBranch`). `updateTaskId` aplica la normalización (trim + colapso de espacios a `-`) antes de guardar el valor, así el campo siempre muestra el id ya normalizado mientras se escribe.

En el modo "Rama existente" las ramas de `ListLocalBranchesUseCase` se muestran en un `DropdownSelector` agrupadas por el segmento anterior a la primera `/` (u "otras" para las ramas sin ella). Las ramas que ya tienen worktree aparecen deshabilitadas y no se pueden seleccionar, y "Crear worktree" solo se habilita con una rama disponible (`isSelectedBranchAvailable`).

### Estado de los worktrees

Cada fila muestra el estado de su worktree debajo del nombre de la rama. `WorktreeViewModel` lo guarda en `statuses`, un mapa de ruta del worktree a `WorktreeStatusState` (`Loading`, `Loaded` o `Unavailable`):

- Una vez leída la lista, se resuelve la rama principal con `ResolveMainBranchUseCase` (expuesta como `mainBranch`) y se lee el estado de cada worktree con `ReadWorktreeStatusUseCase`, como mucho 4 a la vez. La lista aparece al momento y cada fila muestra un pequeño indicador de progreso hasta que llega su estado.
- Un worktree cuyo estado no se puede leer muestra "Estado no disponible" sin afectar a los demás ni al error de la pantalla. Un worktree que ha perdido su carpeta (`isPrunable`) muestra en su lugar "Carpeta no encontrada", y uno bloqueado (`isLocked`, p. ej. en uso por una sesión de un agente) muestra la etiqueta "bloqueado" junto a su rama.
- Recargar la lista, también cuando la ventana recupera el foco, vuelve a leer todos los estados; los resultados de una carga anterior se descartan. Crear un worktree solo lee el estado del nuevo, y eliminarlo descarta su estado.

`WorktreeStatusLine` pinta el estado como segmentos separados por `·` (`statusSegments`): cambios sin commitear (solo si los hay), `↑N ↓N upstream`, `↑N ↓N <rama principal>` y la fecha del último commit en tiempo relativo (`relativeTime`, en `RelativeTime.kt`). Una rama mergeada muestra la etiqueta "mergeada en <rama principal>" en el color primario en lugar de su divergencia con la rama principal.

### Refresco automático

Mientras se muestra `WorktreeScreen`, un `DisposableEffect` llama a `WorktreeViewModel.startWatching()`, y a `stopWatching()` cuando sale de la composición. Cada 3 segundos el ViewModel lee `ReadGitMetadataFingerprintUseCase`: si la huella difiere de la anterior llama a `refresh()`; si no, solo vuelve a ejecutar `DetectRunningAgentsUseCase`, así los agentes que arrancan o terminan aparecen sin una recarga completa. La comprobación se salta mientras la lista se está cargando o hay una acción de crear, eliminar o limpiar en curso. `refresh()` guarda la huella de la que parte, así que una recarga provocada por la propia app no se repite en la siguiente comprobación. El refresco al recuperar el foco y la recarga manual siguen funcionando como antes.

### Vista completa de worktrees

`WorktreeScreen` recibe la `ProjectConfig` del proyecto para clasificar el origen de cada fila con `classifyOrigin`. Tras cada carga de la lista, `WorktreeViewModel` ejecuta además `DetectRunningAgentsUseCase` una vez (`agents`, `null` cuando no se pueden listar los procesos) y mide el tamaño de cada worktree de uno en uno con `MeasureWorktreeSizeUseCase` (`sizes`, sumados en `totalSize`); los resultados de una carga anterior se descartan con `listGeneration`. También lee los puertos reservados de cada worktree (`ports`, con `WorktreeUseCases.readPorts`, que resuelve el entorno con `reserve = false`, así que abrir la pantalla nunca reserva un puerto) y `WorktreeOverviewLine` los muestra después del tamaño con `portsLabel` (`PORT 20001 · api 20002`).

- `WorktreeBranchLine` muestra, tras la rama y la etiqueta "bloqueado", el origen (ShogunAi, Claude Code u otra ubicación) en texto atenuado y cada agente en ejecución como "● nombre" en el color de éxito.
- `WorktreeOverviewLine`, bajo el estado, muestra el tamaño (`formatSize`, unidades binarias con un decimal) y la etiqueta "inactivo" cuando se cumple `isInactive`.
- El título "Worktrees" va seguido de "N worktrees · <tamaño total>".

### Limpiar worktrees

El botón "Limpiar", junto al título "Worktrees", se habilita cuando `cleanupCandidates` (calculado con `selectCleanupCandidates` sobre los worktrees listados y sus estados cargados) no está vacío. Abre `CleanupDialog`, que lista los candidatos por nombre de carpeta y rama, y ofrece "Borrar también sus ramas locales", desmarcado por defecto. Al confirmar se ejecuta `CleanUpWorktreesUseCase`, se muestra un indicador de progreso con las acciones del diálogo deshabilitadas, y después se cierra el diálogo, se muestra "N worktrees limpiados" como mensaje de éxito y se recarga la lista. Los fallos y las ramas conservadas quedan visibles en `CleanupIssues` hasta que se cierran.

Cuando el repositorio declara el provider de Gradle, el diálogo ofrece también "Parar también sus daemons de Gradle ociosos", marcado por defecto (`cleanupStopGradleDaemons`): parar un daemon ocioso solo cuesta un arranque en la siguiente build. Antes de limpiar, `confirmCleanup` lista los daemons con los worktrees tal como estaban; después de las eliminaciones para los ociosos cuya última build fue en un worktree eliminado (`idleDaemonsOfRemovedWorktrees`, `StopGradleDaemonsUseCase`). El mensaje de éxito añade entonces cuántos daemons se pararon y cuántos se conservaron. Un daemon que no se puede listar o parar nunca convierte la limpieza en un fallo.

### Emuladores de Android

Solo en repositorios cuyo `.shogunai.json` declara el provider de emulador (`EmulatorUseCases.declared`, que se lee en cada carga de la lista); si no, no se muestra nada de lo siguiente ni se ejecuta ninguna herramienta de Android.

- **Fila del worktree**: `WorktreeViewModel` carga `emulators` (`ReadEmulatorStatesUseCase`, un único `adb devices` para todas las filas, sin reservar) después de cada carga de la lista y en cada comprobación del refresco automático sin cambios. `EmulatorLine` muestra `Emulador · emulator-5556 · en marcha` con Parar, o Arrancar cuando está parado o aún sin reservar. `startEmulator(worktree)` marca el worktree en `startingEmulators` (un indicador de progreso sustituye al botón), ejecuta `StartWorktreeEmulatorUseCase` (que reserva el puerto si hace falta y espera a que arranque) y después recarga emuladores y puertos; los errores van al aviso de error de la pantalla.
- **Limpieza**: el diálogo añade "Parar también sus emuladores", marcado por defecto (`cleanupStopEmulators`). Los seriales de los emuladores en marcha de los candidatos se toman de los estados cargados antes de eliminar (eliminar borra sus reservas) y se paran después (`stopSerials`); el mensaje de éxito añade cuántos se pararon.
- **Configuración del proyecto**: `EmulatorSettingsSection` muestra la sección "Emulador de Android" cuando `ProjectConfigViewModel.emulatorSection` no es `Hidden`: la lista de AVDs cargada en segundo plano con `AppContainer.loadAvds` (con "Automática" primero, y una AVD guardada que esta máquina no tiene marcada como ausente), el interruptor "Ejecutar sin ventana" y el mensaje de SDK no encontrado con los sitios revisados. Los ajustes se guardan en `ProjectConfig.emulator` y cuentan como cambios sin guardar. La sección sigue a la ruta del repositorio base mientras se edita.

### Simuladores de iOS

Solo en macOS y en repositorios cuyo `.shogunai.json` declara el provider de simulador de iOS (`SimulatorUseCases.declared`, que se lee en cada carga de la lista); si no, no se muestra nada de lo siguiente ni se ejecuta ninguna herramienta de Xcode.

- **Fila del worktree**: `WorktreeViewModel` carga `simulators` (`ReadSimulatorStatesUseCase`, un único `simctl list devices` para todas las filas, sin crear nada) después de cada carga de la lista. `SimulatorLine` muestra `Simulador de iOS · arrancado` con Apagar, o Arrancar cuando está apagado o aún no existe. `startSimulator(worktree)` marca el worktree en `startingSimulators`, ejecuta `StartWorktreeSimulatorUseCase` (que crea el simulador si hace falta y espera a que arranque) y recarga los estados; los errores van al aviso de error de la pantalla.
- **Limpieza**: el diálogo añade "Borrar también sus simuladores de iOS", marcado por defecto (`cleanupDeleteSimulators`), y lista los simuladores huérfanos de ShogunAi (`orphanedSimulators`, cargados con la lista) que también se borrarán. "Limpiar" se activa cuando hay huérfanos aunque no haya candidatos (`canCleanUp`). Los UDIDs de los candidatos se toman de los estados cargados antes de eliminar y se borran después, junto con los huérfanos, que se vuelven a buscar tras eliminar (un worktree cuya carpeta ya no existía no tiene simulador legible antes, y pasa a ser huérfano al eliminarlo); el mensaje de éxito añade cuántos se borraron.
- **Configuración del proyecto**: `SimulatorSettingsSection` muestra la sección "Simulador de iOS" cuando `ProjectConfigViewModel.simulatorSection` no es `Hidden`: desplegables de runtime y dispositivo construidos con los runtimes de iOS de la máquina (`AppContainer.simulatorSetup`, "Automático" primero, un nombre guardado que la máquina no tiene marcado como ausente) y, mientras los tests KMP no estén cableados (`KmpTestWiring`), el fragmento para el repositorio con Copiar e "Instalar para todos los proyectos de esta máquina", que instala el init script de Gradle solo tras un diálogo de confirmación. Los ajustes se guardan en `ProjectConfig.simulator` y cuentan como cambios sin guardar.

### Daemons de Gradle

Cuando `.shogunai.json` declara `"providers": { "gradle": {} }` (`GradleUseCases.provider`, que se lee en cada carga de la lista), `WorktreeScreen` muestra `GradleDaemonsSection` debajo de la lista de worktrees. `WorktreeViewModel` carga `gradleDaemons` (`Loading`, `Loaded` o `Unavailable` cuando no se pueden listar los procesos) con `ListGradleDaemonsUseCase` después de cada carga de la lista y en cada comprobación del refresco automático sin cambios, igual que los agentes en ejecución, y lee `isGradleBuildCacheEnabled` con `CheckGradleBuildCacheUseCase`.

La sección muestra el número de daemons y su memoria total, una fila por daemon (tipo y versión, PID, memoria, "ocupado" / "ocioso · hace 12 minutos" / "estado desconocido", y el nombre de la carpeta del worktree, "worktree eliminado" u "otro proyecto"), un botón "Parar" por fila y "Parar los ociosos". Un botón solo se habilita para los daemons que las reglas de parada permiten (`isStoppable`: un daemon de Gradle ocioso, o uno de Kotlin mientras ningún daemon de Gradle está ocupado o en estado desconocido); `StopGradleDaemonsUseCase` vuelve a comprobar el estado de todos modos. El resultado (`daemonStopReport`) queda visible hasta que se cierra, y la lista se recarga. Sin la caché de build, una nota informativa explica cómo activarla. Cuando el provider declara ajustes (`gradleSettings`), una línea los indica para las builds de los worktrees, p. ej. "Builds de los worktrees: heap de 2 GB · inactivo tras 30 min · Kotlin 1 GB" (`formatMemorySetting` convierte `2g` en "2 GB").

### Comando de lanzamiento

Cada fila de worktree tiene un botón "Copiar comando" que llama a `WorktreeViewModel.copyLaunchCommand(worktree)`; el botón muestra "Copiado" durante un par de segundos. El usuario pega el comando (`cd "<ruta>" && <comando del agente>`) en la terminal que prefiera.

### Provisionado del repositorio y archivos locales

El formulario de configuración tiene dos secciones de archivos:

- **Del repositorio** (solo lectura): los patrones de `.shogunai.json` y `.worktreeinclude` del repositorio base, cada uno con el fichero del que viene. `ProjectConfigViewModel.repositoryProvisioning` guarda un `RepositoryProvisioningState` (`None`, `Declared`, o `Unreadable` con el fichero y el error de lectura), leído a través de `RepositoryProvisioningSource` al abrir el formulario y cada vez que cambia la ruta del repositorio base.
- **Archivos locales**: los `secretFiles` del proyecto, rutas exactas guardadas solo en `~/.shogunai/projects.json`.

"Mover a .shogunai.json" (`moveLocalFilesToRepository`) se ofrece cuando algún archivo local está dentro del repositorio (relativo, sin empezar por `..`) y `.shogunai.json` no está ilegible. Convierte esas entradas en patrones anclados a la raíz (`local.properties` → `/local.properties`), los muestra en "Del repositorio" marcados "al guardar" y los quita de la lista local; las entradas absolutas se quedan como locales. Todavía no se escribe nada: los patrones pendientes cuentan como cambio sin guardar, así que cancelar los descarta. `save()` devuelve `Result<Project>`: primero añade los patrones pendientes con `addCopyPatterns` y después guarda el proyecto; si falla la escritura del fichero del repositorio, el proyecto no se guarda y la pantalla muestra el error sobre los botones, sin cerrar el formulario.

### Mover un repositorio

Cuando el usuario edita un proyecto porque su repositorio se movió en disco:

- `ProjectConfigViewModel` convierte en relativas las entradas absolutas de archivos locales que están dentro del repositorio base, para que sigan al repositorio.
- Cambiar la ruta del repositorio base también mueve la carpeta raíz de worktrees cuando estaba dentro del repositorio, era su carpeta padre o una carpeta hermana, salvo que el usuario la haya editado a mano (`RepositoryRelocation.kt`).
- Al guardar con otra ruta base u otra carpeta raíz de worktrees, `AppRoot` abre la pantalla de worktrees con la carpeta raíz anterior, y `WorktreeViewModel.repairAfterMove` ejecuta `RepairWorktreesUseCase` antes de listar. Si la reparación falla, el error queda visible.
- Los ViewModels del formulario y de la pantalla de worktrees se identifican por la configuración del proyecto, no solo por su id, así un proyecto editado nunca reutiliza casos de uso ligados a las rutas antiguas.

Las cargas de la lista llevan un número de generación: un resultado solo se aplica si no ha empezado otra carga más reciente entretanto, así una carga anterior lenta no puede pisar un estado más nuevo (ni borrar un error más nuevo).

## Componentes compartidos (`ui/components`)

- **`Spacing`**: escala de espaciado (`xs=4dp`, `sm=8dp`, `md=16dp`, `lg=24dp`) usada en vez de valores `dp` sueltos.
- **`SegmentedSelector<T>`**: control segmentado (`SingleChoiceSegmentedButtonRow`/`SegmentedButton`) para un conjunto pequeño y fijo de opciones siempre visibles (p. ej. `CreateMode`, `BranchType`). Las etiquetas se dimensionan a su texto completo (`Modifier.width(IntrinsicSize.Max)`): la fila calcula el ancho de sus segmentos a partir de su ancho mínimo intrínseco, que para un texto normal es solo su palabra más larga.
- **`DropdownSelector<T>`**: dropdown (`ExposedDropdownMenuBox`) para listas más largas o cargadas dinámicamente, con grupos plegables opcionales (`groupBy`) y opciones deshabilitadas (`enabled`), que se ignoran sea cual sea el método de entrada.
- **`SectionCard`**: `OutlinedCard` con título, usada para agrupar secciones de un formulario largo (todas las secciones de `ProjectConfigScreen`, y el bloque "Nuevo worktree" de `WorktreeScreen`).
- **Atajos de teclado** (`KeyboardShortcuts.kt`): `onEnterKey` lanza la acción principal de un campo de texto (p. ej. añadir un archivo local), y `onNewItemShortcut` asocia Cmd+N (macOS) / Ctrl+N (otros sistemas) a "Nuevo proyecto" y "Crear worktree".

## Tema (`ui/theme`)

`ShogunAiTheme` aplica el esquema de colores claro u oscuro según el `ThemeMode` guardado, que se elige con `ThemeModeToggle` en la barra superior. En modo `SYSTEM`, `rememberSystemInDarkTheme` consulta el tema del sistema operativo cada segundo, porque `isSystemInDarkTheme()` de Compose Desktop no reacciona a cambios con la app abierta.

## `AppContainer`

Instancia `JsonProjectRepository`, los repositorios de preferencias, `NioFileManager`, `ProcessBuilderShellCommandExecutor` y `AwtClipboardWriter` una sola vez al arrancar la app. Los casos de uso de worktrees dependen de un proyecto, así que `worktreeUseCases(config)` construye un `WorktreeUseCases` para la `ProjectConfig` seleccionada, incluidos sus `GradleUseCases` sobre un único `ProcessGradleDaemonSource`. No hay framework de DI.

## `FilePicker`

Envuelve `JFileChooser` para los botones "Examinar…" de la pantalla de configuración (ruta del repo base, carpeta de worktrees, archivos locales). Se eligió sobre un selector nativo de Compose porque Compose Desktop no trae uno y `JFileChooser` ya está disponible en el target JVM sin dependencias nuevas. `relativeToBase` convierte la ruta absoluta de un archivo local elegido a la forma relativa al repo que espera `ProjectConfig.secretFiles`.

## Idiomas (`ui/i18n`)

La interfaz está disponible en inglés (por defecto) y español. Todos los textos visibles viven en `interface Strings`, con una implementación por idioma (`EnglishStrings`, `SpanishStrings`); los textos con parámetros son funciones (`deleteProjectMessage(projectName)`), así los argumentos se comprueban al compilar.

- `AppRoot` carga el `AppLanguage` guardado (`LanguagePreferenceRepository`) y provee la tabla activa con `CompositionLocalProvider(LocalStrings provides language.strings())` por encima del `when` de pantallas: cambiar de idioma recompone los textos sin recrear ViewModels ni perder el estado de la pantalla.
- Los composables leen los textos con `val strings = LocalStrings.current`. El código no composable (como `FilePicker`) recibe el texto ya traducido como parámetro.
- `LanguageToggle`, junto a `ThemeModeToggle` en la barra superior, muestra cada idioma por su propio nombre ("English", "Español").
- Los ViewModels exponen datos, no mensajes: `WorktreeViewModel` expone `error: Throwable?` y `success: WorktreeSuccess?` (worktree creado, worktrees reparados tras mover el repositorio), y la pantalla los formatea con `strings.worktreeError(error)` y el texto correspondiente de `Strings`. `worktreeError` es exhaustivo sobre la jerarquía sellada `WorktreeError`, así una variante nueva no compila hasta que se traduce; un proceso que no puede arrancar (`CommandExecutionException`) tiene su propio mensaje, y cualquier otro error usa uno genérico. La salida de error de Git se muestra tal cual, sin traducir.

**Añadir un texto**: declarar el miembro en `Strings` e implementarlo en cada idioma (el compilador señala cualquiera que falte). **Añadir un idioma**: nuevo valor en `AppLanguage`, nuevo `object` que implemente `Strings` y su rama en `AppLanguage.strings()`.
