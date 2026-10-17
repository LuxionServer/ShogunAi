# Capa de infraestructura

Vive en `core/src/main/kotlin/app/luxion/shogunai/infrastructure`, salvo `AwtClipboardWriter`, que necesita el toolkit de escritorio y se queda en `desktopApp` con el mismo paquete. Implementa los puertos definidos en el dominio.

## `ProcessBuilderShellCommandExecutor`

Implementación de `ShellCommandExecutor` sobre `ProcessBuilder`. Detalles de robustez:

- **Lee stdout y stderr de forma concurrente** (dos `async`). Si se leyeran en serie, un proceso que llena el buffer del stream no leído se bloquearía (deadlock clásico de `ProcessBuilder`).
- `waitFor()` se envuelve en `runInterruptible` para que la cancelación de la corrutina interrumpa la espera del proceso.
- El trabajo bloqueante se confina en `Dispatchers.IO` (inyectable por constructor para tests).
- En el `finally` se hace `process.destroy()` si el proceso sigue vivo, para no dejar procesos huérfanos si la corrutina se cancela.
- **Directorios de Homebrew en el `PATH`**: las apps lanzadas desde el Finder o el Dock de macOS no heredan el `PATH` del shell, así que no encontrarían herramientas instaladas con Homebrew (p. ej. `git-lfs`). `HomebrewPath.merge` antepone `/opt/homebrew/bin`, `/opt/homebrew/sbin` y `/usr/local/bin` al `PATH` del proceso hijo, sin duplicados.
- Si el proceso no llega a arrancar, la `IOException` se envuelve en `CommandExecutionException`.

## `NioFileManager`

Implementación de `FileManager` sobre `java.nio.file`. `copy` crea las carpetas padre que falten en el destino y usa `REPLACE_EXISTING` y `COPY_ATTRIBUTES`. `directorySize` recorre el árbol con `Files.walkFileTree` sin seguir enlaces simbólicos, suma el tamaño de los archivos regulares, se salta las carpetas excluidas e ignora los archivos que no puede leer. `modificationStamps` recorre el árbol del mismo modo y anota la fecha de modificación de cada entrada. `readProperties` carga el fichero con `java.util.Properties`. Funciona igual en todos los sistemas operativos soportados.

## `NioEnvironmentStateStore` / `SocketPortProbe`

- **Estado**: las reservas de cada worktree viven en `shogunai-env.json` dentro de su directorio de administración de Git, como `{ "ports": { "PORT": 20001, "port:api": 20002 }, "resources": { "ios-simulator": "<UDID>" } }`; los ficheros sin `resources` se cargan como antes. Git ignora ahí los ficheros que no conoce y borra el directorio entero con `git worktree remove` y `prune`, así que las reservas desaparecen con el worktree, lo haya creado quien lo haya creado. Los demás campos del objeto se conservan para futuros providers; un fichero que se queda sin campos se borra; un fichero ilegible cuenta como sin reservas. Las escrituras son atómicas (fichero temporal y movimiento).
- **Listado de la máquina**: los ficheros del repositorio actual (el del directorio común, para el worktree principal, más cada `worktrees/*/shogunai-env.json`) y cada directorio de administración del índice de la máquina `~/.shogunai/reservations.json` (`{ "worktrees": [ … ] }`). Escribir los puertos de un worktree añade su directorio al índice, o lo quita cuando ya no tiene ninguno; al listar se descartan las entradas cuyo fichero de estado ya no existe (el worktree se eliminó o se podó), así que el índice se repara solo y los puertos de worktrees eliminados de cualquier repositorio quedan libres.
- **Lock**: `withLock` toma un lock exclusivo de `FileChannel` sobre `~/.shogunai/reservations.lock`, para que creaciones en paralelo, incluso desde varios procesos (varias sesiones de Claude Code) y repositorios distintos, no reserven el mismo puerto. Un lock de proceso protege el lock del fichero, y una llamada anidada en el mismo hilo se ejecuta directamente.
- **Puerto libre**: `SocketPortProbe` hace `bind` de un `ServerSocket` con `reuseAddress = false` en la dirección comodín y en la de loopback; el puerto está libre solo si ambos funcionan.

## `SimctlAppleSimulators` / `FileKmpTestWiring`

- **Simuladores**: `xcrun simctl list -j runtimes` (runtimes disponibles con sus `supportedDeviceTypes`) y `list -j devices` (`Booted`, `Shutdown`; cualquier otro estado cuenta como arrancando), `create <nombre> <tipo de dispositivo> <runtime>` (imprime el UDID), `boot`, `shutdown`, `delete`, todo con el ejecutor de comandos. `show` ejecuta `open -b com.apple.iphonesimulator --args -CurrentDeviceUDID <udid>` y, si la app Simulator no está instalada (Xcode 27 la sustituyó por Device Hub), `open -b com.apple.dt.Devices`, para que el dispositivo tenga ventana; `simctl boot` por sí solo lo arranca sin ella. "Unable to boot/shutdown device in current state: Booted/Shutdown" cuenta como éxito. `isSupported()` solo es cierto en macOS.
- **Cableado KMP**: se busca `IOS_SIMULATOR_UDID` en los ficheros `*.gradle.kts` y `*.gradle` del repositorio, hasta cinco niveles de profundidad y saltando `build`, `.gradle`, `.git`, `.kotlin`, `.idea` y `node_modules`, y con el mismo recorrido se busca `multiplatform` (sin distinguir mayúsculas) para reconocer un repositorio Kotlin Multiplatform, lo que cubre `kotlin("multiplatform")`, el id del plugin y alias del catálogo de versiones como `libs.plugins.kotlinMultiplatform`. El init script es `$GRADLE_USER_HOME/init.d/shogunai-ios-simulator.init.gradle.kts`, en Kotlin DSL porque los scripts Groovy no compilan con JDKs recientes; como el plugin de Kotlin no está en su classpath, reconoce las tareas `KotlinNativeSimulatorTest` por el nombre de la clase y fija `device` por reflexión, y no hace nada si `IOS_SIMULATOR_UDID` no está definida. Al desinstalarlo también se borra `init.d` si queda vacío. `REPOSITORY_SNIPPET` es el fragmento de Kotlin DSL que se muestra al usuario.

## `LocalAndroidSdk`

Implementación de `AndroidSdk` sobre las herramientas del propio SDK:

- **Localización**: el primero de `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `sdk.dir` del `local.properties` del repositorio y la ubicación por defecto de la plataforma (`~/Library/Android/sdk`, `~/Android/Sdk`, `%LOCALAPPDATA%\Android\Sdk`) que contenga `emulator/emulator`; en Windows los ejecutables llevan `.exe`.
- **AVDs**: `emulator -list-avds`, saltando las líneas informativas del emulador.
- **Estados**: un único `adb devices`, quedándose con los seriales `emulator-*`; uno `offline` está arrancando, y uno `device` está en marcha cuando `adb -s <serial> shell getprop sys.boot_completed` devuelve `1`.
- **Arranque**: `emulator -avd <avd> -port <puerto> -read-only -no-boot-anim [-no-window]` con `ProcessBuilder`, no con el ejecutor de comandos, porque tiene que seguir en marcha cuando termina quien lo llamó (el CLI); la salida va a `~/.shogunai/logs/emulator-<puerto>.log`. `-read-only` es lo que permite ejecutar una AVD una vez por worktree; los cambios en los datos del emulador se descartan al salir.
- **Parada**: `adb -s <serial> emu kill`.

## `HerdrConfigFile`

Implementación de `HerdrConfigSource`. Lee el `config.toml` de Herdr del mismo sitio que Herdr: `HERDR_CONFIG_PATH`, si no `$XDG_CONFIG_HOME/herdr/`, si no `%APPDATA%\herdr\` en Windows y si no `~/.config/herdr/`. Un lector de líneas sencillo toma la clave `directory` de la tabla `[worktrees]` (cadena básica o literal, admite comentarios al final) y expande la `~` inicial; no es un parser de TOML completo, porque es la única clave que ShogunAi necesita. Si el fichero falta, no se puede leer o no se entiende, o no tiene esa clave, se usa la carpeta por defecto de Herdr, `~/.herdr/worktrees`.

## `CliHerdrClient`

Implementación de `HerdrClient` sobre el comando `herdr`, con `ShellCommandExecutor` y listas de argumentos: `worktree open --cwd <repositorio> --path <worktree> --label … --focus`, `pane split <pane> --direction right --cwd … --env NOMBRE=valor… --focus`, `pane close <pane>` y `pane run <pane> <comando>`. Del JSON de Herdr solo lee los campos que necesita (`result.workspace.workspace_id`, `result.root_pane.pane_id`, `result.already_open`, `result.pane.pane_id`), así que los demás pueden cambiar. El binario es `~/.local/bin/herdr` o `~/.cargo/bin/herdr` si existen (donde lo dejan el script de instalación de Herdr y `cargo install`, que no están en el `PATH` de una app abierta desde Finder) y, si no, `herdr` del `PATH` (`herdr.exe` en Windows). Un comando que no puede arrancar es `HerdrNotInstalled`; una salida distinta de cero o un objeto `error` es `HerdrCommandFailed` con el `error.message` de Herdr, sacado del JSON de la salida estándar o de la de error (donde Herdr escribe sus errores), o la salida de error tal cual si no es JSON. Herdr no arranca un servidor para estos comandos, así que si no hay ninguno en marcha el mensaje pide ejecutar `herdr`.

## `ProcessGradleDaemonSource`

Implementación de `GradleDaemonSource` que no depende de un binario de Gradle ni del formato interno de su registro:

- **Procesos**: ejecuta `ps -A -ww -o pid=,rss=,command=` y se queda con los procesos cuyos argumentos contienen `org.gradle.launcher.daemon.bootstrap.GradleDaemon` (el argumento siguiente es la versión de Gradle) u `org.jetbrains.kotlin.daemon.KotlinCompileDaemon`. `rss` se convierte de KiB a bytes. Si `ps` falla (p. ej. en Windows) lanza `ProcessListingFailed`.
- **Estado**: cada daemon de Gradle escribe `$GRADLE_USER_HOME/daemon/<versión>/daemon-<pid>.out.log` (`GRADLE_USER_HOME` es `~/.gradle` por defecto). Se recorre hacia atrás el último MiB de ese log buscando el último `Marking the daemon as busy|idle` (con su fecha, para `Idle(since)`; cualquier otro estado cuenta como ocupado) y el último `Received command: Build{…, currentDir=<ruta>}`, que da el último directorio de build: el directorio desde el que se lanzó el cliente de Gradle, así que un `./gradlew -p <worktree>` lanzado desde otro sitio se atribuye a donde se lanzó. Ambas líneas se registran a nivel INFO en cada build. Un log que falta o no se puede leer, o sin marcas en ese tramo, da `Unknown`, que nunca permite parar.
- No se usan `--status` ni `--stop` de Gradle: solo ven los daemons de la versión de Gradle que los ejecuta, y `--stop` para también los ocupados.

## `MacDerivedDataSource`

Implementación de `DerivedDataSource` para macOS (`isSupported` comprueba `os.name`):

- **Ubicación**: `defaults read com.apple.dt.Xcode <clave>` como argv, primero `IDEDerivedDataPathOverride` y después `IDECustomDerivedDataLocation` (claves confirmadas en `IDEFoundation` de Xcode 27; se expande un `~` inicial); si `defaults` falla (no existe la clave) se usa `~/Library/Developer/Xcode/DerivedData`. Una ubicación que no existe no tiene carpetas.
- **Carpetas**: cada hijo directo con un `info.plist` regular que tenga `WorkspacePath`, leído con el parser XML del JDK con entidades externas y DTDs desactivados. Un plist binario (`bplist00`) o un fichero ilegible se salta, igual que las cachés compartidas de Xcode, que no tienen. `LastAccessedDate` da `lastAccessed`; el tamaño sale de `FileManager.directorySize`.
- **Caché de compilación**: `IDECustomCompilationCacheLocation` si está definida y, si no, `<ubicación>/CompilationCache.noindex`.
- **Volúmenes**: una ruta bajo `/Volumes/<nombre>` está en un volumen no disponible si ese punto de montaje no existe.
- **Borrado**: rechaza cualquier ruta que no sea hijo directo de la ubicación actual o no tenga `info.plist`, y después borra la carpeta recursivamente.

## `RepositoryProvisioningFiles`

Implementación de `RepositoryProvisioningSource` sobre `java.nio.file` y `kotlinx.serialization`, sobre `<repo>/.shogunai.json` y `<repo>/.worktreeinclude`.

- **Lectura**: un `.shogunai.json` inexistente o vacío significa que no hay patrones. El fichero se parsea como un objeto JSON genérico, así que los campos desconocidos se ignoran; un JSON inválido, una raíz que no es un objeto, un `copy` que no es una lista de strings, o un `providers` (o un provider conocido dentro de él) que no es un objeto lanzan `InvalidRepositoryConfig`. Los ids de provider desconocidos se saltan. `env` tiene que ser un objeto de strings cuyas plantillas se puedan interpretar (`parseEnvironmentVariable`), y `envFile` un string con una ruta relativa que no salga del worktree una vez normalizada.
- **Escritura** (`addCopyPatterns`): lee el objeto, sustituye solo `copy` (primero los patrones existentes, después los nuevos sin duplicados) y lo vuelve a escribir con formato, de forma atómica (fichero temporal en la misma carpeta + `ATOMIC_MOVE`), así que los demás campos se conservan. Se niega a reescribir un fichero que no puede parsear.

La coincidencia de patrones se deja a Git (`git ls-files --others --ignored -z -x <patrón>…`, ver `CreateWorktreeUseCase` en el dominio), así que sigue exactamente las reglas de gitignore, incluidos sus límites: no se puede reincluir un fichero con `!` si su carpeta padre está excluida (`.idea/` + `!.idea/workspace.xml` sigue listando `workspace.xml`; usa `.idea/*` en su lugar). Puedes probar un patrón desde el repositorio base con el mismo comando:

```bash
git ls-files --others --ignored -x '/local.properties' -x '.env*' -x '.idea/runConfigurations/'
```

## `AwtClipboardWriter`

En `desktopApp`. Implementación de `ClipboardWriter` con el portapapeles del sistema de AWT (`Toolkit.getDefaultToolkit().systemClipboard`), envuelta en `runCatching`.

## Catálogo de proyectos (`projects.json`)

`JsonProjectRepository` guarda los proyectos en `~/.shogunai/projects.json` con `kotlinx.serialization` (`ignoreUnknownKeys`, para que se sigan cargando ficheros con campos de versiones anteriores). Cada escritura reemplaza el fichero completo de forma atómica: se escribe en un temporal de la misma carpeta y se mueve sobre el original con `ATOMIC_MOVE`, así un cierre inesperado nunca deja el catálogo a medio escribir.

## Preferencias de usuario (`settings.json`)

`JsonSettingsFile` es el fichero `~/.shogunai/settings.json`, compartido por `JsonThemePreferenceRepository` (puerto `ThemePreferenceRepository`), `JsonLanguagePreferenceRepository` (puerto `LanguagePreferenceRepository`) y `JsonShellPreferenceRepository` (puerto `ShellPreferenceRepository`). Guarda `{ "themeMode": ..., "language": ..., "lastDestination": ..., "windowBounds": ..., "sidebarCollapsed": ... }`; las tres últimas son opcionales, así que un fichero escrito por una versión anterior abre en el primer proyecto con los bounds por defecto.

- **Cada guardado reescribe solo su campo**: `update` lee el contenido actual, aplica el cambio y escribe el fichero completo de forma atómica (mismo patrón que `JsonProjectRepository`). Así cambiar el idioma no pisa el tema y viceversa.
- **Valores por defecto**: `SYSTEM` para el tema e `ENGLISH` para el idioma, si el fichero no existe, está vacío o no se puede parsear. Los ficheros escritos por versiones anteriores (solo `themeMode`) se leen sin problema: el idioma toma su valor por defecto.

## Dobles de test

En `core/src/testFixtures/kotlin/app/luxion/shogunai/domain/`, los test fixtures de `core`, que usan los tests de `core` y los de `desktopApp` (`testImplementation(testFixtures(project(":core")))`):

- `FakeShellCommandExecutor` — sustituye `ShellCommandExecutor`: responde a cada argv con un `CommandResult` preparado y registra los comandos ejecutados.
- `FakeFileManager` — sustituye `FileManager` con un conjunto en memoria de rutas existentes y ficheros de propiedades.
- `FakeEnvironmentStateStore` / `FakePortProbe` — sustituyen el almacén de reservas (reservas en memoria por directorio de administración, contando escrituras y locks) y la comprobación de puertos (un conjunto de puertos ocupados).
- `FakeAppleSimulators` / `FakeKmpTestWiring` — sustituyen los simuladores (runtimes, dispositivos que arrancan tras un número de consultas, registro de creaciones, arranques, ventanas mostradas (o un fallo al mostrarlas), apagados y borrados, un interruptor de macOS) y el cableado KMP.
- `FakeHerdrConfigSource` — devuelve una carpeta de worktrees de Herdr fija.
- `FakeHerdrClient` — registra los worktrees abiertos, los panes divididos y cerrados y los comandos ejecutados, da por abiertos los worktrees que se le indican y puede hacer fallar la apertura o la ejecución con un error dado.
- `FakeAndroidSdk` — sustituye `AndroidSdk` con AVDs, estados de emulador que pasan de arrancando a en marcha tras un número de consultas, un interruptor de SDK ausente y registro de arranques y paradas.
- `FakeGradleDaemonSource` — sustituye `GradleDaemonSource` con una lista mutable de daemons (o un fallo) y cuenta los listados; `gradleDaemon(...)` y `kotlinDaemon(...)` los construyen.
- `FakeDerivedDataSource` — sustituye `DerivedDataSource` con carpetas, proyectos existentes, volúmenes no montados, un tamaño de caché de compilación, un interruptor de macOS y borrados que fallan, y registra los borrados; `derivedData(...)` construye una carpeta.
- `FakeClipboardWriter` — sustituye `ClipboardWriter`.
- `FakeProjectRepository` — sustituye `ProjectRepository` en los tests de ViewModels.
- `FakeRepositoryProvisioningSource` — sustituye `RepositoryProvisioningSource` con provisionado por ruta de repositorio, repositorios ilegibles y escrituras que fallan, y registra los patrones añadidos.

Gracias a que el dominio depende de interfaces, los tests de casos de uso (`*UseCaseTest`) y los de ViewModels no tocan disco ni lanzan procesos reales. La propia infraestructura se cubre con `ProcessBuilderShellCommandExecutorTest`, `JsonProjectRepositoryTest`, `JsonSettingsFileTest`, `RepositoryProvisioningFilesTest`, `NioEnvironmentStateStoreTest` (directorios temporales y un socket real escuchando), `LocalAndroidSdkTest` (orden de búsqueda del SDK, salida real de `adb devices`, el comando de arranque), `SimctlAppleSimulatorsTest` (JSON real de `simctl`), `FileKmpTestWiringTest` (repositorios y Gradle user home temporales) y `ProcessGradleDaemonSourceTest` (sobre líneas reales de `ps` y fragmentos de logs de daemons), que sí usan procesos reales y ficheros temporales.
