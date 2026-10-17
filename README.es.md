# ShogunAi

[English](./README.md) · **Español**

Orquestador de escritorio que automatiza la creación y gestión de **Git worktrees** aislados, con aprovisionamiento automático de secretos locales — pensado para eliminar el cambio de contexto manual cuando trabajas (o un agente de IA como Claude/Copilot trabaja) en varias tareas a la vez.

## Qué hace

- Crea y elimina **Git worktrees** aislados para cada tarea, cada uno en su propia rama nueva (`feature/` o `fix/`) o en una rama que ya exista en local.
- Copia automáticamente a cada worktree nuevo los **archivos locales** que necesita para compilar (p. ej. `local.properties` de Android, ficheros `.env`), según lo que declare un `.shogunai.json` versionado en el repositorio o el `.worktreeinclude` de Claude Code.
- Gestiona varios proyectos desde un catálogo, cada uno con su propia configuración (repositorio base, carpeta de worktrees, comando del agente).
- Copia el comando para arrancar tu agente de IA en un worktree (`cd <worktree> && claude`, opcionalmente envuelto con Headroom), listo para pegar en cualquier terminal.
- Si mueves un repositorio, actualiza su ruta en el proyecto y la app vuelve a enlazar sus worktrees (`git worktree repair`).
- Incluye el comando `shogun`, para crear, provisionar, consultar y limpiar worktrees desde un terminal, un script o un hook de un agente.

## Instalación

### macOS (Homebrew)

```bash
brew tap LuxionServer/shogunai
brew install --cask shogunai
```

> [!NOTE]
> Homebrew elige automáticamente el instalador para la arquitectura de tu Mac (Apple Silicon o Intel). La app no está notarizada por Apple: si al abrirla por primera vez macOS avisa que "no se puede verificar el desarrollador", hazlo con clic derecho → Abrir, o ejecuta `xattr -dr com.apple.quarantine "/Applications/ShogunAi.app"`.

### Descarga manual

Descarga el instalador para tu sistema operativo desde la [última versión publicada](https://github.com/LuxionServer/ShogunAi/releases/latest):

| Sistema operativo | Instalador |
|---|---|
| macOS (Apple Silicon) | `-arm64.dmg` |
| macOS (Intel) | `-x64.dmg` |
| Windows | `.msi` |
| Linux | `.deb` |

Instálalo como cualquier otra app de escritorio y ábrelo. No necesitas tener JDK ni herramientas de desarrollo instaladas: el instalador incluye su propio runtime.

### El comando `shogun`

- **Homebrew**: se instala con el Cask; `shogun --version` funciona en un terminal nuevo.
- **Linux (`.deb`)**: el lanzador de la app es el comando. Enlázalo una vez: `sudo ln -s /opt/shogunai/bin/ShogunAi /usr/local/bin/shogun`.
- **Windows, o sin instalar la app**: descarga `shogun-<versión>.zip` de la release, descomprímelo y añade su carpeta `bin` al `PATH`. Necesita Java 21 o posterior.

## Uso básico

1. **Crea un proyecto** — al abrir la app por primera vez verás el catálogo de proyectos vacío. Pulsa "Nuevo proyecto" e indica un nombre, la ruta del repositorio base y la carpeta donde se crearán los worktrees. Opcionalmente añade archivos locales que deben copiarse a cada worktree nuevo en esta máquina.
2. **Entra a un proyecto** — selecciónalo desde el catálogo para ver sus worktrees existentes.
3. **Crea un worktree** — indica el id de la tarea y el tipo de rama (`feature` o `fix`); la app crea el worktree, la rama y copia los archivos que declaran el repositorio y el proyecto. Para trabajar en una rama que ya tienes, cambia a "Rama existente" y elígela de la lista.
   Para compartir lo que necesita un worktree con todo el que clone el repositorio, añade un `.shogunai.json` en su raíz con patrones al estilo gitignore; cada archivo que Git no versiona y coincide con alguno se copia a cada worktree nuevo, en la misma ruta. Un `.worktreeinclude` (el fichero de Claude Code) se lee igual. El botón "Mover a .shogunai.json" de la configuración del proyecto escribe ahí tus archivos locales.

   ```json
   { "copy": ["/local.properties", ".env*", ".idea/runConfigurations/"] }
   ```

4. **Ponte a trabajar** — pulsa "Copiar comando" en un worktree y pégalo en tu terminal para abrir allí tu agente de IA.
5. **Elimina un worktree** — cuando termines una tarea, elimínalo desde la lista, con la opción de borrar también la rama local. El worktree principal no se puede eliminar.

La app está en inglés por defecto; puedes cambiarla a español desde el selector de idioma de la barra superior (junto al selector de tema). La elección se recuerda entre reinicios.

## Línea de comandos

`shogun` trabaja sobre el repositorio Git que contiene el directorio actual, usando su proyecto de la app de escritorio si lo hay:

```bash
shogun new TASK-123                # worktree nuevo en feature/TASK-123, provisionado; imprime su ruta
shogun new --branch hotfix/login   # worktree para una rama existente
shogun provision ../otro-worktree  # copia los archivos declarados en un worktree creado por otra herramienta
shogun status                      # todos los worktrees con sus cambios, divergencia y si están mergeados
shogun clean --yes                 # elimina los worktrees mergeados o sin carpeta (sin --yes solo los lista)
eval "$(shogun env)"               # carga las variables de este worktree desde "env" en .shogunai.json (puertos, …)
```

Ejecuta `shogun help` para ver todas las opciones. Sin proyecto en la app, `shogun new` necesita `--worktrees-root <dir>`.

### Con agentes de IA

Haz que `claude --worktree` cree sus worktrees a través de ShogunAi añadiendo los hooks a `.claude/settings.json`:

```json
{
  "hooks": {
    "WorktreeCreate": [{ "hooks": [{ "type": "command", "command": "shogun hook worktree-create" }] }],
    "WorktreeRemove": [{ "hooks": [{ "type": "command", "command": "shogun hook worktree-remove" }] }],
    "SessionStart": [{ "hooks": [{ "type": "command", "command": "shogun hook session-start" }] }]
  }
}
```

`SessionStart` da a cada sesión las variables propias de su worktree, como el puerto de un servidor de desarrollo, declaradas en `.shogunai.json` (`"env": { "PORT": "${port}" }`). Las recetas para Cursor, Codex, direnv y cualquier otra herramienta están en [Uso con agentes](./docs/agents.es.md).

## Repositorios Gradle

Declara un repositorio Gradle (Android, KMP o JVM) en su `.shogunai.json`:

```json
{ "copy": ["/local.properties"], "providers": { "gradle": {} } }
```

Cada build en un worktree arranca un daemon de Gradle (y normalmente un daemon de compilación de Kotlin) cuando no hay uno ocioso que reutilizar, y cada daemon conserva su memoria durante horas después de la build. En un repositorio Gradle, la pantalla de worktrees muestra una sección "Daemons de Gradle" con todos los daemons de la máquina: su memoria, si están ocupados u ociosos y el worktree de su última build. Desde ahí puedes parar los ociosos, y "Limpiar" ofrece parar los daemons ociosos de los worktrees que elimina. Un daemon ocupado nunca se para.

Si el repositorio no activa la caché de build de Gradle (`org.gradle.caching=true` en `gradle.properties`), ShogunAi te lo dice: con ella activada, la primera build de un worktree nuevo reutiliza lo que otros worktrees ya compilaron.

```bash
shogun gradle                      # lista los daemons, su memoria, estado y worktree
shogun gradle stop                 # para todos los daemons ociosos (o solo los ids de proceso indicados)
shogun clean --yes --stop-gradle-daemons  # para también los daemons ociosos de los worktrees eliminados
```

### Memoria y vida de los daemons por worktree

Las builds en paralelo no pueden compartir un daemon ocupado, así que tres agentes compilando a la vez arrancan tres daemons, cada uno con el heap que pide tu `gradle.properties`, y siguen vivos tres horas. El provider puede acotar los daemons que arrancan las builds de los worktrees:

```json
{ "providers": { "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30, "kotlinDaemonMaxHeap": "1g" } } }
```

Todos los ajustes son opcionales. Los tamaños de memoria son un número seguido de `k`, `m` o `g`; el timeout va en minutos. Con al menos uno, el entorno de cada worktree (el principal incluido) recibe el mismo `GRADLE_OPTS`, que se impone a `gradle.properties` en las builds lanzadas desde esa shell. `daemonMaxHeap` solo sustituye `-Xmx`: se conservan los demás `org.gradle.jvmargs` de tu `gradle.properties` de usuario o, si no hay, los del repositorio. La sección "Daemons de Gradle" muestra los ajustes en vigor.

- Llega a los agentes como cualquier [variable por worktree](./docs/agents.es.md#entorno-por-worktree). Un `GRADLE_OPTS` que pongas tú en esa shell se sustituye; decláralo en `env` y tendrá prioridad sobre el del provider.
- Las builds lanzadas desde Android Studio o IntelliJ no heredan la shell del agente: siguen usando `gradle.properties` y no comparten daemons con las builds de los agentes. Pon también los límites en `gradle.properties` si los quieres en todas partes.
- Los ajustes nuevos se aplican a daemons nuevos; los daemons ociosos arrancados con otros argumentos no se reutilizan y caducan solos, o puedes pararlos desde la sección.
- El entrecomillado está pensado también para `gradlew.bat` de Windows, pero solo se ha verificado en macOS.

Inspeccionar los daemons necesita `ps` (macOS y Linux).

## Emuladores de Android

Las builds de Android instalan en todos los dispositivos conectados salvo que `ANDROID_SERIAL` indique uno, así que los agentes de worktrees en paralelo se pisan los emuladores. Activa el provider de emulador para que cada worktree tenga el suyo:

```json
{ "providers": { "emulator": { "avd": "Pixel_8_API_35" } } }
```

`avd` es opcional: la AVD que prefiere este repositorio cuando la máquina la tiene. Cada worktree reserva entonces un puerto de emulador y recibe `ANDROID_SERIAL=emulator-<puerto>` en su entorno (`shogun env`, el hook `SessionStart`, `envFile`). Los emuladores solo arrancan cuando lo pides:

```bash
shogun emulator start      # arranca el emulador de este worktree (solo lectura, así una AVD sirve a todos) y espera
shogun emulator status     # emulator-5556 running
shogun emulator stop
```

En cada máquina, la configuración del proyecto elige la AVD (o la deja automática) y si arranca sin ventana; `--avd` y `--no-window` las sustituyen en un arranque. La lista de worktrees muestra cada emulador con Arrancar y Parar, y "Limpiar" (o `shogun clean --stop-emulators`) para los emuladores de los worktrees que elimina. El SDK se busca en `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `sdk.dir` de `local.properties` o la ubicación por defecto de Android Studio. Los repositorios sin el provider no ven nada de esto.

## Simuladores de iOS

Si no, los agentes en paralelo de un proyecto KMP o iOS comparten los mismos simuladores. Activa el provider de simulador de iOS (solo macOS) para que cada worktree tenga el suyo:

```json
{ "providers": { "ios-simulator": { "device": "iPhone 16", "runtime": "iOS 18.5" } } }
```

Las dos opciones son preferencias opcionales que se usan si la máquina las tiene. Cada worktree recibe un simulador nuevo (creado, nunca clonado: unos 17 MB, 360 MB tras arrancar) llamado `ShogunAi · <repositorio> · <worktree>`, y `IOS_SIMULATOR_UDID` en su entorno. Compila para él con `xcodebuild -destination "id=$IOS_SIMULATOR_UDID"`.

```bash
shogun simulator start     # crea si hace falta y arranca el simulador de este worktree, esperando a que esté listo
shogun simulator status    # su estado, y si los tests KMP lo usan
shogun simulator stop
```

Los tests de simulador iOS de KMP eligen el dispositivo en el script de build, así que añade esto al `build.gradle.kts` del módulo:

```kotlin
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    providers.environmentVariable("IOS_SIMULATOR_UDID").orNull?.let { device.set(it) }
}
```

Si prefieres no tocar el repositorio, `shogun simulator gradle-init install` (o el botón de la configuración del proyecto) instala un init script de Gradle que hace lo mismo en todos los proyectos de esta máquina, tras preguntar. La configuración del proyecto también elige el tipo de dispositivo y el runtime por máquina. "Limpiar" (o `shogun clean --delete-simulators`) borra los simuladores de los worktrees eliminados y los de ShogunAi cuyo worktree ya no existe. Los repositorios sin el provider no ven nada de esto.

## Repositorios Xcode

Xcode guarda una carpeta de DerivedData por ruta de proyecto, así que cada worktree tiene la suya (varios gigabytes en una app real) y Xcode nunca borra la de un worktree eliminado. Activa el provider de Xcode (solo macOS):

```json
{ "providers": { "xcode": { "compilationCache": true } } }
```

La pantalla de worktrees muestra entonces una sección "DerivedData de Xcode" con la carpeta de cada worktree, su tamaño y su último acceso, y las carpetas huérfanas de la máquina: las de proyectos que ya no existen, las cree la herramienta que las cree. "Eliminar huérfanas" las borra, y "Limpiar" (marcado por defecto) también borra las huérfanas tras eliminar worktrees, incluidas las carpetas de los worktrees que elimina. Solo se borran carpetas huérfanas: nunca los DerivedData de un proyecto que sigue existiendo, nunca las cachés compartidas de Xcode (`CompilationCache.noindex`, `ModuleCache.noindex`, …) y nunca una carpeta cuyo proyecto está en un volumen no montado. ShogunAi busca los DerivedData en la ubicación por defecto de Xcode o en la personalizada de sus preferencias.

```bash
shogun xcode                               # carpetas de DerivedData de este repositorio, huérfanas y tamaño de la caché de compilación
shogun xcode clean                         # borrar las carpetas huérfanas
shogun clean --yes --delete-derived-data   # borrarlas también tras eliminar worktrees
```

`compilationCache` es opcional. Con él, ShogunAi escribe un `~/.shogunai/xcode/compilation-cache.xcconfig` de máquina que activa la caché de compilación de Xcode con prefix mapping de Swift y Clang, y el entorno de cada worktree recibe `XCODE_XCCONFIG_FILE` apuntando a él, así que el primer `xcodebuild` de un worktree nuevo reutiliza lo que los demás ya compilaron (en un proyecto de prueba, de 16,5 s a 1,8 s). La sección muestra el tamaño de la caché de compilación compartida, que Xcode hace crecer sin que ShogunAi la limite.

- Necesita Xcode 26 o posterior y ayuda a proyectos `.xcodeproj` y `.xcworkspace`; los paquetes Swift no reutilizaron nada entre worktrees.
- Llega a los agentes como cualquier [variable por worktree](./docs/agents.es.md#entorno-por-worktree). Un `XCODE_XCCONFIG_FILE` que pongas tú en esa shell se sustituye; decláralo en `env` y tendrá prioridad sobre el del provider.
- Xcode abierto desde el Dock no hereda la shell del agente. Pon los ajustes de build en el proyecto si también quieres la caché ahí.

Los repositorios sin el provider, y otros sistemas operativos, no ven nada de esto.

## Desarrollo

¿Quieres compilar el proyecto desde el código fuente o contribuir? Empieza por [`docs/getting-started.es.md`](./docs/getting-started.es.md).

## Documentación

Arquitectura y decisiones de diseño en [`/docs`](./docs) (en inglés y español), servida con MkDocs:

```bash
pip install -r requirements-docs.txt
mkdocs serve
```

## Licencia

[GNU GPLv3](./LICENSE)
