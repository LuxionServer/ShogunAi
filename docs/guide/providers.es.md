# Providers

Un provider añade soporte para un tipo de herramienta a los worktrees de un repositorio. Un repositorio activa providers en su [`.shogunai.json`](configuration.md#configuracion-del-repositorio-shogunaijson), bajo `providers`; los repositorios sin un provider nunca ven sus secciones, botones ni comandos, y ninguna de sus herramientas se ejecuta.

```json
{ "providers": { "gradle": {}, "emulator": {}, "ios-simulator": {}, "xcode": {}, "maven": {}, "docker-compose": {}, "desktop-app": { "module": ":desktopApp" } } }
```

| Provider | Para | Plataformas |
|---|---|---|
| [`gradle`](#gradle) | Repositorios Android, KMP y JVM: los daemons de Gradle y su memoria. | Todas; la lista de daemons necesita `ps` (macOS, Linux) |
| [`emulator`](#emulador-de-android) | Un emulador de Android por worktree. | Todas, con el Android SDK |
| [`ios-simulator`](#simulador-de-ios) | Un simulador de iOS por worktree. | macOS |
| [`xcode`](#xcode) | Los DerivedData de Xcode, la caché de compilación compartida, los paquetes Swift y los pods de CocoaPods. | macOS |
| [`maven`](#maven) | Compilaciones de Maven: un límite de memoria y los daemons de Maven. | Todas; la lista de daemons necesita `ps` (macOS, Linux) |
| [`docker-compose`](#docker-compose) | Un proyecto de Docker Compose y puertos por worktree. | Todas, con la CLI de Docker |
| [`desktop-app`](#app-de-compose-desktop) | Una app de Compose Desktop por worktree, ejecutada en segundo plano sin ocupar el escritorio. | macOS |

## Detección de stacks

No hace falta saber qué providers existen. ShogunAi mira los archivos de la carpeta principal del repositorio y de sus subcarpetas directas (nunca las ocultas, `build`, `node_modules` ni `Pods`) y reconoce cinco stacks:

| Stack | Lo delatan | Sugiere |
|---|---|---|
| Gradle | `gradlew`, `settings.gradle` o `settings.gradle.kts` en la carpeta principal | `gradle` |
| Android | `com.android.` en un `build.gradle(.kts)` de esas carpetas, o en `gradle/libs.versions.toml` | `emulator` |
| Xcode | una carpeta `.xcodeproj`, o `Package.swift` en la carpeta principal | `ios-simulator` y `xcode` (solo macOS) |
| Maven | `pom.xml` o `mvnw` en la carpeta principal | `maven` |
| Docker Compose | `compose.yaml`, `compose.yml`, `docker-compose.yaml` o `docker-compose.yml` en la carpeta principal | `docker-compose` |

En los ajustes del proyecto, "Configuración del repositorio" lista los providers que el repositorio podría usar y aún no declara, con los archivos que los delataron, y "Activar" enciende el provider como un cambio sin guardar: no se escribe nada hasta que guardas y confirmas la vista previa. `shogun stacks` imprime lo mismo desde un terminal y no cambia nada. Una sugerencia es solo una pista (un `pom.xml` en una carpeta de pruebas también cuenta), por eso siempre se muestra la evidencia.

ShogunAi también busca, a cualquier profundidad fuera de las carpetas de compilación y de herramientas (`build`, `.gradle`, `node_modules`, `Pods`, `DerivedData`), los archivos sin versionar sin los que un worktree móvil recién creado no compila: `local.properties`, `keystore.properties`, `key.properties`, `*.jks`, `*.keystore`, `google-services.json` y `GoogleService-Info.plist`. Cada uno que todavía no provisiona nada (archivos locales, `copy`, `link` o `.worktreeinclude`) se sugiere como un patrón `link` anclado, como `/app/google-services.json`, para que al rotarlo en el repositorio base se actualicen todos los worktrees. También lee las claves `storeFile` de esos ficheros de propiedades (cualquier clave que termine en `storeFile`, como `RELEASE_STORE_FILE`): un keystore que nombren dentro del repositorio también se sugiere, y una ruta relativa que sale del repositorio se avisa, porque no se resolverá desde un worktree que vive en otra carpeta. Los ajustes del proyecto los listan en "Archivos del worktree" y `shogun stacks` los imprime; no se escribe nada hasta que aceptas y guardas.

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

## Maven

```json
{ "providers": { "maven": { "mavenMaxHeap": "1g" } } }
```

Las compilaciones en paralelo en varios worktrees arrancan cada una una JVM con el heap por defecto de Maven, y los daemons de Maven (mvnd) siguen vivos tras la compilación. `mavenMaxHeap` es opcional, un tamaño como `512m` o `1g`: con él, el entorno de cada worktree (el principal incluido) recibe `MAVEN_OPTS=-Xmx<tamaño>`. Un `MAVEN_OPTS` declarado en `env` tiene prioridad, uno que pongas tú en ese shell se reemplaza, y las compilaciones lanzadas desde un IDE no lo heredan.

Para un repositorio Maven la app muestra una sección "Daemons de Maven" con cada daemon mvnd de la máquina: su memoria, si está ocupado u ocioso, y el worktree de su última build (mvnd mueve el directorio de trabajo del daemon a la carpeta de cada build: el worktree más profundo que la contiene, "worktree eliminado" si la carpeta ya no existe dentro de la carpeta de worktrees, "otro proyecto" en otro caso, nada si no se puede leer). Las compilaciones con `mvn` a secas y los IDE no aparecen.

El estado sale del propio log del daemon, cuya ubicación pone mvnd en la línea de comandos del daemon; funciona con mvnd 1.x y 2.x y con cualquier `mvnd.daemonStorage`. Un daemon está ocupado mientras corre una build y ocioso después; uno cuyo log no se puede leer tiene estado desconocido. Desde ahí puedes parar los daemons ociosos, y "Limpiar" ofrece parar los daemons de Maven ociosos de los worktrees que elimina. Un daemon ocupado, o de estado desconocido, nunca se para; el estado se vuelve a leer justo antes de pararlo.

```bash
shogun maven                       # lista los daemons de Maven, su memoria, su estado y su worktree
shogun maven stop                  # para todos los daemons de Maven ociosos (o: shogun maven stop <pid>...)
shogun clean --yes --stop-maven-daemons  # para también los daemons de Maven ociosos de los worktrees eliminados
```

La inspección de daemons necesita `ps` y `lsof` (macOS y Linux).

## Docker Compose

```json
{ "providers": { "docker-compose": { "portVariables": ["DB_PORT", "API_PORT"] } } }
```

Dos worktrees que ejecutan `docker compose up` comparten nombres de contenedores, redes y volúmenes, y publican los mismos puertos del host. El provider da a cada worktree los suyos:

- `COMPOSE_PROJECT_NAME` es `<carpeta del repositorio>-<carpeta del worktree>`, en minúsculas y con cada carácter que no sea letra, dígito, `-` o `_` sustituido por `-` (`main-repo` y `TASK-123` dan `main-repo-task-123`). Un nombre de carpeta que normalizar volvería ambiguo (`fix login (2)`, o mayúsculas y minúsculas mezcladas) recibe un hash corto y estable, y un nombre de más de 50 caracteres se acorta conservándolo, de modo que worktrees distintos obtienen nombres distintos. Un `COMPOSE_PROJECT_NAME` declarado en `env` tiene prioridad.
- `portVariables` es opcional: cada nombre se convierte en una variable con un puerto libre entre 20000 y 29999 reservado para ese worktree (las mismas reglas que `${port}`), de modo que el archivo de Compose puede publicar `${DB_PORT}:5432` sin colisiones. Los nombres siguen la regla de nombres de variable y no pueden ser nombres reservados; un nombre también declarado en `env` recibe el valor declarado y ningún puerto.

### Arrancar y construir los servicios

`shogun compose up` arranca en segundo plano los servicios del worktree en el que estás, con su `COMPOSE_PROJECT_NAME` y sus variables de puerto, así que nunca pasas `-p` ni puertos a mano; `--build` construye antes, y todo lo que va tras `--` llega tal cual a `docker compose` (nombres de servicio, `--wait`). `shogun compose build` solo construye. La salida de Docker Compose se muestra mientras se ejecuta. El detalle del worktree en la app tiene una sección Compose con el nombre y el estado del proyecto (`Sin arrancar` cuando Docker no conoce ese proyecto) y las acciones Arrancar, Construir y Bajar. El contexto de SessionStart le dice a Claude que use `shogun compose up`.

### Lo que dejan los worktrees eliminados

Bajar un proyecto conserva sus volúmenes, y Compose nunca elimina las imágenes que construyó, así que los worktrees eliminados dejan volúmenes, redes e imágenes. ShogunAi apunta en `~/.shogunai/compose-projects.json` el nombre de proyecto que da a cada worktree (cuando se resuelve su entorno y en cada `up` o `build`); un volumen, una red o una imagen cuya etiqueta `com.docker.compose.project` es un nombre apuntado cuyo worktree ya no existe es un resto de ese worktree. Solo cuenta un nombre que ahora no usa ningún otro worktree y que ningún otro repositorio ha apuntado, así que los proyectos de otros repositorios, aunque tengan un prefijo parecido, nunca se tocan. Las imágenes descargadas de un registro (como `postgres`) no llevan etiqueta de Compose y nunca se eliminan.

La vista "Proyectos de Compose" de la sección Máquina lista los proyectos que `docker compose ls` informa para los worktrees del repositorio (estado y worktree), los proyectos de worktrees eliminados (los apuntados, y aquellos cuyos archivos de Compose estaban en el repositorio o en su carpeta de worktrees) y, tras ellos, los restos de los worktrees eliminados. "Bajar los proyectos de los worktrees eliminados" ejecuta `docker compose -p <nombre> down`, que elimina contenedores y redes y conserva los volúmenes. "Eliminar redes e imágenes" elimina los restos salvo los volúmenes; "Eliminar también los volúmenes" pregunta antes, porque sus datos se pierden. Nunca se fuerza nada: una imagen o una red que aún usa un contenedor se conserva y se informa.

"Limpiar" tiene una opción marcada para bajar los proyectos de los worktrees que elimina y eliminar sus redes e imágenes construidas, y otra sin marcar para eliminar también sus volúmenes; si Docker no está disponible, los worktrees se eliminan igualmente y el informe dice que los proyectos se conservaron.

Los worktrees eliminados antes de que existiera este registro no están apuntados: sus proyectos con contenedores se siguen encontrando por sus archivos de Compose, y el resto se puede eliminar con `docker volume rm` y `docker image rm`.

```bash
shogun compose up                  # arranca en segundo plano los servicios de este worktree con su nombre de proyecto y puertos
shogun compose up --build -- api   # construye antes, y solo el servicio api
shogun compose build               # construye los servicios de este worktree
shogun compose                     # los proyectos de Compose de los worktrees y lo que dejaron los eliminados
shogun compose down                # baja los proyectos de worktrees eliminados (conserva volúmenes)
shogun compose down TASK-123       # baja el proyecto de un worktree
shogun compose prune               # lista las redes e imágenes que dejaron los worktrees eliminados
shogun compose prune --yes         # las elimina (añade --volumes para eliminar también sus volúmenes)
shogun clean --yes --down-compose-projects  # baja además los proyectos de los eliminados y elimina sus imágenes
```

Docker solo se ejecuta en repositorios que declaran el provider, y solo se usa la CLI de Docker.

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
- Cuando el worktree está abierto en Orca, arrancar su emulador también lo muestra en la vista de emuladores de Orca; ver [Orca](../agents.md#orca).
- El SDK se busca en `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `sdk.dir` de `local.properties` o la ubicación por defecto de Android Studio.
- Todos los worktrees comparten el servidor de adb: los agentes nunca deben reiniciarlo (`adb kill-server`) ni forzar un dispositivo con `-s`, `-e` o `-d`. El hook `SessionStart` se lo dice a Claude.

## App de Compose Desktop

Un agente que comprueba una app de Compose Desktop con computer use te quita la pantalla, el ratón y el teclado, y solo un agente puede hacerlo a la vez. Activa el provider de app de escritorio para que el agente de cada worktree ejecute su app en segundo plano:

```json
{ "providers": { "desktop-app": { "module": ":desktopApp" } } }
```

`module` es la ruta Gradle del módulo de la app, que debe usar Compose Hot Reload (Compose Multiplatform 1.12 o posterior). `isolatedHome` (por defecto `true`) da a la app su propio home de usuario por worktree, para que nunca toque tus datos; ponlo a `false` para una app que deba leer el home real.

```bash
shogun headless start      # lanza la app oculta, sin quitar el foco
shogun headless capture    # PNG de su ventana, aunque esté oculta
shogun headless status
shogun headless stop
```

El agente maneja la app con el servidor MCP de Compose Hot Reload. Consulta [Comprobar una app de escritorio en segundo plano](headless-app.md) para el flujo completo, los permisos de macOS que necesita y lo que todavía necesita el escritorio.

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

- Cada worktree que tiene un dispositivo tiene una **concesión** sobre él. Arrancar el dispositivo y cada momento en que su agente trabaja renuevan la concesión, que caduca tras la duración de la concesión sin actividad (20 minutos por defecto, se ajusta en Preferencias > Recursos y nunca en el repositorio). Solo se pueden liberar los dispositivos cuya concesión ha caducado.
- Cuando arrancas un dispositivo y su pool está lleno, ShogunAi libera uno con la concesión caducada, en este orden (el usado menos recientemente primero dentro de cada paso): un dispositivo detenido cuyo worktree no tiene sesión de agente, uno detenido cuyo agente ha terminado o está inactivo, uno en marcha (se detiene antes) y, el último, uno cuyo agente está bloqueado. Un dispositivo cuyo agente está trabajando, o cuya concesión sigue vigente, nunca se libera; si no se puede liberar ninguno, el arranque falla y nombra los worktrees que los tienen, con cuándo caduca cada concesión.
- `shogun emulator start --wait` y `shogun simulator start --wait` esperan en cola a un pool lleno en vez de fallar, como mucho 30 minutos (`--wait-timeout <minutos>` lo cambia). Quienes esperan se atienden por orden de llegada en toda la máquina y, mientras alguien espera, otros arranques no pueden quedarse con el dispositivo que espera. Los agentes deben ejecutar un arranque que espera en segundo plano; el aviso de la sesión lo dice.
- Un emulador liberado pierde su puerto y un simulador liberado se elimina. El worktree liberado recuerda por qué (`shogun emulator status` dice «released for …»), su puerto no se entrega a otro worktree mientras haya otro libre, y recupera el mismo puerto cuando vuelve a arrancar. Los agentes deben ejecutar `shogun emulator start` antes de cada uso del dispositivo: es idempotente, adquiere uno de nuevo si fue liberado e imprime el serial que hay que usar. El aviso de inicio de sesión lo indica.
- Los daemons de Gradle los arrancan tus builds, así que no se pueden rechazar: la app (cada 30 segundos) y `shogun pool trim` detienen los inactivos, primero los que llevan más tiempo, hasta quedar dentro del límite.
- `shogun pool status` muestra cada pool con su límite, sus holders y quienes esperan, y las vistas de máquina de la app muestran lo mismo con un aviso de lo que se liberó.

## Xcode

Xcode guarda una carpeta de DerivedData por ruta de proyecto, así que cada worktree tiene la suya (varios gigabytes en una app real) y Xcode nunca borra la de un worktree eliminado. Activa el provider de Xcode (solo macOS):

```json
{ "providers": { "xcode": { "compilationCache": true } } }
```

La pantalla de worktrees muestra entonces una sección "DerivedData de Xcode" con la carpeta de cada worktree, su tamaño y su último acceso, y las carpetas huérfanas de la máquina: las de proyectos que ya no existen, las cree la herramienta que las cree. Las carpetas aparecen enseguida; sus tamaños y los totales muestran "Calculando…" hasta que cada carpeta se mide en segundo plano, varias a la vez, sin congelar la app. ShogunAi recuerda el tamaño de cada carpeta entre ejecuciones, tanto en la app como en el comando `shogun`, y solo vuelve a medir las carpetas que Xcode ha usado desde entonces, así que tras la primera ejecución la mayoría de tamaños aparecen enseguida. El cálculo continúa si sales de la vista (la entrada de la barra lateral muestra el total cuando está listo), y recargar la vista vuelve a medir todas las carpetas, mostrando los tamaños anteriores hasta que llegan los nuevos. "Eliminar huérfanas" las borra, y "Limpiar" (marcado por defecto) también borra las huérfanas tras eliminar worktrees, incluidas las carpetas de los worktrees que elimina.

Fuera de la cuota, solo se borran carpetas huérfanas, y nunca los DerivedData de un proyecto que sigue existiendo, nunca las cachés compartidas de Xcode (`CompilationCache.noindex`, `ModuleCache.noindex`, …) y nunca una carpeta cuyo proyecto está en un volumen no montado. ShogunAi busca los DerivedData en la ubicación por defecto de Xcode o en la personalizada de sus preferencias.

### Cuota de DerivedData

El DerivedData crece con cada worktree en el que un agente compila, así que ShogunAi mantiene las carpetas dentro de una cuota, elegida en Preferencias > Recursos:

- **Automática**, la opción por defecto: una cuarta parte del espacio que DerivedData podría usar en su disco (el espacio libre más lo que ya ocupa DerivedData), entre 20 y 200 GB. Con 100 GB libres y 60 GB de DerivedData, la cuota es de 40 GB. Se calcula cada vez que se aplica la cuota; si no se puede leer el espacio libre, no se libera nada y la vista dice que la cuota es desconocida.
- **Tamaño fijo**: un número de gigabytes.
- **Sin cuota**: no se libera nada para respetar una cuota; solo se borran las huérfanas, cuando lo pides.

La cuota es un ajuste de la máquina, no forma parte de los perfiles de capacidad y nunca se lee del repositorio. Los ajustes guardados antes de que existiera la cuota automática conservan su tamaño fijo si lo tenían y, si no, pasan a Automática.

Cuando las carpetas suman más que la cuota, ShogunAi libera primero las carpetas huérfanas y después las de los worktrees que no están en uso, de la menos a la más recientemente accedida, y se detiene en cuanto el total cabe. Un worktree está en uso mientras su agente trabaja o está bloqueado, o su carpeta se accedió hace menos de una hora. Nunca se liberan las carpetas de otros proyectos, las de volúmenes no disponibles ni la caché de compilación; si solo quedan esas, la vista dice que la cuota sigue superada y nombra los worktrees en uso. La app aplica la cuota cada vez que se han medido los tamaños de las carpetas, y muestra el uso frente a la cuota (marcada como "automática" si lo es), también en la barra lateral, y las carpetas que liberó hasta que cierres el aviso. `shogun xcode trim` la aplica desde la línea de comandos.

```bash
shogun xcode                               # carpetas de DerivedData de este repositorio con sus paquetes, huérfanas y cachés compartidas
shogun xcode clean                         # borrar las carpetas huérfanas
shogun xcode trim [--dry-run]              # liberar carpetas hasta que el DerivedData quepa en la cuota
shogun clean --yes --delete-derived-data   # borrarlas también tras eliminar worktrees
```

### Caché de compilación

`compilationCache` es opcional. Con él, ShogunAi escribe un `~/.shogunai/xcode/compilation-cache.xcconfig` de máquina que activa la caché de compilación de Xcode con prefix mapping de Swift y Clang, y el entorno de cada worktree recibe `XCODE_XCCONFIG_FILE` apuntando a él, así que el primer `xcodebuild` de un worktree nuevo reutiliza lo que los demás ya compilaron (en un proyecto de prueba, de 16,5 s a 1,8 s). La sección muestra el tamaño de la caché de compilación compartida, que Xcode hace crecer sin que ShogunAi la limite.

- Necesita Xcode 26 o posterior y ayuda a proyectos `.xcodeproj` y `.xcworkspace`; los paquetes Swift no reutilizaron nada entre worktrees.
- Llega a los agentes como cualquier [variable por worktree](../agents.md#entorno-por-worktree). Un `XCODE_XCCONFIG_FILE` que pongas tú en esa shell se sustituye; decláralo en `env` y tendrá prioridad sobre el del provider.
- Xcode abierto desde el Dock no hereda la shell del agente. Pon los ajustes de build en el proyecto si también quieres la caché ahí.

### Paquetes Swift y cachés de paquetes

Xcode resuelve los paquetes Swift de un proyecto dentro de su carpeta de DerivedData (`SourcePackages`), así que cada worktree tiene sus propios checkouts, que cuentan en la cuota y desaparecen con la carpeta. Salen de la caché de repositorios compartida de Xcode, así que un worktree nuevo los resuelve sin red. La sección y `shogun xcode` muestran cuánto de la carpeta de cada worktree son paquetes ("App.xcodeproj · paquetes 800 MB"), medido y recordado junto al tamaño de la carpeta; los checkouts no se comparten entre worktrees.

Las cachés de paquetes compartidas, la de SwiftPM (`~/Library/Caches/org.swift.swiftpm`) y la de CocoaPods (`~/Library/Caches/CocoaPods`), se muestran debajo de la caché de compilación cuando existen. Como ella, no cuentan en la cuota y ShogunAi nunca las borra; solo se miden sus ubicaciones por defecto.

### CocoaPods

La carpeta `Pods` de un proyecto CocoaPods suele estar ignorada por Git, así que un worktree nuevo no compila hasta que se ejecuta `pod install`. Activa `cocoaPods` y ShogunAi da a cada worktree nuevo o provisionado los pods del repositorio base:

```json
{ "providers": { "xcode": { "cocoaPods": true } } }
```

Por cada `Podfile.lock` versionado en Git, a cualquier profundidad, cuyo worktree aún no tenga `Pods`, ShogunAi lo compara con el `Pods/Manifest.lock` de la base, la misma comprobación que hace la fase de build "Check Pods Manifest.lock" de CocoaPods. Si coinciden, el `Pods` de la base se clona en el worktree con copy-on-write de APFS (`cp -c`): es instantáneo y no ocupa disco hasta que cambia un fichero. Si no coinciden, la base nunca ejecutó `pod install` o el volumen no puede clonar, no se copia nada (nunca una copia completa lenta) y `shogun new`, `shogun provision` y el hook de creación de worktrees dicen qué carpetas necesitan `pod install` y por qué (`pods` en su JSON). Los pods se preparan después de copiar los ficheros y antes de los comandos post-creación, así que un `pod install` declarado ahí los encuentra en su sitio; un problema con los pods nunca hace fallar el worktree.
