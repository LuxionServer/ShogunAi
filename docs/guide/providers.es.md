# Providers

Un provider añade soporte para un tipo de herramienta a los worktrees de un repositorio. Un repositorio activa providers en su [`.shogunai.json`](configuration.md#configuracion-del-repositorio-shogunaijson), bajo `providers`; los repositorios sin un provider nunca ven sus secciones, botones ni comandos, y ninguna de sus herramientas se ejecuta.

```json
{ "providers": { "gradle": {}, "emulator": {}, "ios-simulator": {}, "xcode": {} } }
```

| Provider | Para | Plataformas |
|---|---|---|
| [`gradle`](#gradle) | Repositorios Android, KMP y JVM: los daemons de Gradle y su memoria. | Todas; la lista de daemons necesita `ps` (macOS, Linux) |
| [`emulator`](#emulador-de-android) | Un emulador de Android por worktree. | Todas, con el Android SDK |
| [`ios-simulator`](#simulador-de-ios) | Un simulador de iOS por worktree. | macOS |
| [`xcode`](#xcode) | Los DerivedData de Xcode y la caché de compilación compartida. | macOS |

## Gradle

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

Inspeccionar los daemons necesita `ps` (macOS y Linux).

### Memoria y vida de los daemons por worktree

Las builds en paralelo no pueden compartir un daemon ocupado, así que tres agentes compilando a la vez arrancan tres daemons, cada uno con el heap que pide tu `gradle.properties`, y siguen vivos tres horas. El provider puede acotar los daemons que arrancan las builds de los worktrees:

```json
{ "providers": { "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30, "kotlinDaemonMaxHeap": "1g" } } }
```

Todos los ajustes son opcionales. Los tamaños de memoria son un número seguido de `k`, `m` o `g`; el timeout va en minutos. Con al menos uno, el entorno de cada worktree (el principal incluido) recibe el mismo `GRADLE_OPTS`, que se impone a `gradle.properties` en las builds lanzadas desde esa shell. `daemonMaxHeap` solo sustituye `-Xmx`: se conservan los demás `org.gradle.jvmargs` de tu `gradle.properties` de usuario o, si no hay, los del repositorio. La sección "Daemons de Gradle" muestra los ajustes en vigor.

- Llega a los agentes como cualquier [variable por worktree](../agents.md#entorno-por-worktree). Un `GRADLE_OPTS` que pongas tú en esa shell se sustituye; decláralo en `env` y tendrá prioridad sobre el del provider.
- Las builds lanzadas desde Android Studio o IntelliJ no heredan la shell del agente: siguen usando `gradle.properties` y no comparten daemons con las builds de los agentes. Pon también los límites en `gradle.properties` si los quieres en todas partes.
- Los ajustes nuevos se aplican a daemons nuevos; los daemons ociosos arrancados con otros argumentos no se reutilizan y caducan solos, o puedes pararlos desde la sección.
- El entrecomillado está pensado también para `gradlew.bat` de Windows, pero solo se ha verificado en macOS.

## Emulador de Android

Las builds de Android instalan en todos los dispositivos conectados salvo que `ANDROID_SERIAL` indique uno, así que los agentes de worktrees en paralelo se pisan los emuladores. Activa el provider de emulador para que cada worktree tenga el suyo:

```json
{ "providers": { "emulator": { "avd": "Pixel_8_API_35" } } }
```

`avd` es opcional: la AVD que prefiere este repositorio cuando la máquina la tiene. Un worktree reserva un puerto de emulador y recibe `ANDROID_SERIAL=emulator-<puerto>` en su entorno (`shogun env`, el hook `SessionStart`, `envFile`) la primera vez que arrancas su emulador, no cuando se crea; `shogun emulator start` imprime la línea `export ANDROID_SERIAL=…` para ejecutarla en un shell que ya estaba abierto. Los emuladores solo arrancan cuando lo pides:

```bash
shogun emulator start      # arranca el emulador de este worktree (solo lectura, así una AVD sirve a todos) y espera
shogun emulator status     # emulator-5556 running
shogun emulator stop
```

- En cada máquina, la configuración del proyecto elige la AVD (o la deja automática) y si arranca sin ventana; `--avd` y `--no-window` las sustituyen en un arranque.
- La lista de worktrees muestra cada emulador con Arrancar y Parar, y "Limpiar" (o `shogun clean --stop-emulators`) para los emuladores de los worktrees que elimina.
- El SDK se busca en `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `sdk.dir` de `local.properties` o la ubicación por defecto de Android Studio.
- Todos los worktrees comparten el servidor de adb: los agentes nunca deben reiniciarlo (`adb kill-server`) ni forzar un dispositivo con `-s`, `-e` o `-d`. El hook `SessionStart` se lo dice a Claude.

## Simulador de iOS

Si no, los agentes en paralelo de un proyecto KMP o iOS comparten los mismos simuladores. Activa el provider de simulador de iOS (solo macOS) para que cada worktree tenga el suyo:

```json
{ "providers": { "ios-simulator": { "device": "iPhone 16", "runtime": "iOS 18.5" } } }
```

Las dos opciones son preferencias opcionales que se usan si la máquina las tiene. Un worktree recibe un simulador nuevo (creado, nunca clonado: unos 17 MB, 360 MB tras arrancar) llamado `ShogunAi · <repositorio> · <worktree>`, y `IOS_SIMULATOR_UDID` en su entorno, la primera vez que lo arrancas. Compila para él con `xcodebuild -destination "id=$IOS_SIMULATOR_UDID"`.

```bash
shogun simulator start     # crea si hace falta, arranca el simulador de este worktree y lo abre (Simulator, o Device Hub en Xcode 27)
shogun simulator status    # su estado, y en un repositorio KMP si sus tests lo usan
shogun simulator stop
```

### Tests de KMP

Los tests de simulador iOS de KMP eligen el dispositivo en el script de build, así que añade esto al `build.gradle.kts` del módulo:

```kotlin
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    providers.environmentVariable("IOS_SIMULATOR_UDID").orNull?.let { device.set(it) }
}
```

Si prefieres no tocar el repositorio, `shogun simulator gradle-init install` (o el botón de la configuración del proyecto) instala un init script de Gradle que hace lo mismo en todos los proyectos de esta máquina, tras preguntar. Una vez instalado, la configuración del proyecto ofrece desinstalarlo, como `shogun simulator gradle-init uninstall`.

- La configuración del proyecto también elige el tipo de dispositivo y el runtime por máquina.
- "Limpiar" (o `shogun clean --delete-simulators`) borra los simuladores de los worktrees eliminados y los de ShogunAi cuyo worktree ya no existe.

## Capacidad

Los emuladores, los simuladores de iOS y los daemons de Gradle están limitados por máquina y los comparten todos los repositorios y worktrees. Los límites salen del perfil de capacidad de Preferencias > Recursos y nunca se leen del repositorio: **Automático** (por defecto, según la memoria y los núcleos de la máquina), **Ligero** (1 emulador, 1 simulador, 2 daemons de Gradle), **Equilibrado** (2, 2 y 4), **Uno por worktree** (sin límite más allá de los 16 puertos de emulador) o **Personalizado** (un número o sin límite por tipo). Los aplican tanto la app como `shogun`.

- Cuando arrancas un dispositivo y su pool está lleno, se libera el inactivo usado menos recientemente: un emulador detenido pierde su puerto y un simulador apagado se elimina. Los dispositivos en marcha o arrancando nunca se liberan; si ninguno está inactivo, el arranque falla y nombra los worktrees que los tienen.
- Los daemons de Gradle los arrancan tus builds, así que no se pueden rechazar: la app (cada 30 segundos) y `shogun pool trim` detienen los inactivos, primero los que llevan más tiempo, hasta quedar dentro del límite.
- `shogun pool status` muestra cada pool con su límite y sus holders, y las vistas de máquina de la app muestran lo mismo con un aviso de lo que se liberó.

## Xcode

Xcode guarda una carpeta de DerivedData por ruta de proyecto, así que cada worktree tiene la suya (varios gigabytes en una app real) y Xcode nunca borra la de un worktree eliminado. Activa el provider de Xcode (solo macOS):

```json
{ "providers": { "xcode": { "compilationCache": true } } }
```

La pantalla de worktrees muestra entonces una sección "DerivedData de Xcode" con la carpeta de cada worktree, su tamaño y su último acceso, y las carpetas huérfanas de la máquina: las de proyectos que ya no existen, las cree la herramienta que las cree. Las carpetas aparecen enseguida; sus tamaños y los totales muestran "Calculando…" hasta que cada carpeta se mide en segundo plano, de una en una, sin congelar la app. El cálculo continúa si sales de la vista (la entrada de la barra lateral muestra el total cuando está listo) y vuelve a empezar al recargarla. "Eliminar huérfanas" las borra, y "Limpiar" (marcado por defecto) también borra las huérfanas tras eliminar worktrees, incluidas las carpetas de los worktrees que elimina.

Solo se borran carpetas huérfanas: nunca los DerivedData de un proyecto que sigue existiendo, nunca las cachés compartidas de Xcode (`CompilationCache.noindex`, `ModuleCache.noindex`, …) y nunca una carpeta cuyo proyecto está en un volumen no montado. ShogunAi busca los DerivedData en la ubicación por defecto de Xcode o en la personalizada de sus preferencias.

```bash
shogun xcode                               # carpetas de DerivedData de este repositorio, huérfanas y tamaño de la caché de compilación
shogun xcode clean                         # borrar las carpetas huérfanas
shogun clean --yes --delete-derived-data   # borrarlas también tras eliminar worktrees
```

### Caché de compilación

`compilationCache` es opcional. Con él, ShogunAi escribe un `~/.shogunai/xcode/compilation-cache.xcconfig` de máquina que activa la caché de compilación de Xcode con prefix mapping de Swift y Clang, y el entorno de cada worktree recibe `XCODE_XCCONFIG_FILE` apuntando a él, así que el primer `xcodebuild` de un worktree nuevo reutiliza lo que los demás ya compilaron (en un proyecto de prueba, de 16,5 s a 1,8 s). La sección muestra el tamaño de la caché de compilación compartida, que Xcode hace crecer sin que ShogunAi la limite.

- Necesita Xcode 26 o posterior y ayuda a proyectos `.xcodeproj` y `.xcworkspace`; los paquetes Swift no reutilizaron nada entre worktrees.
- Llega a los agentes como cualquier [variable por worktree](../agents.md#entorno-por-worktree). Un `XCODE_XCCONFIG_FILE` que pongas tú en esa shell se sustituye; decláralo en `env` y tendrá prioridad sobre el del provider.
- Xcode abierto desde el Dock no hereda la shell del agente. Pon los ajustes de build en el proyecto si también quieres la caché ahí.
