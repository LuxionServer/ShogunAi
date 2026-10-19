# App de escritorio

La app de escritorio gestiona un catálogo de proyectos y, para cada uno, sus worktrees. Todo lo que hace está disponible también desde el [comando `shogun`](cli.md).

## Proyectos

La primera vez que abres la app verás el catálogo de proyectos vacío. Pulsa "Nuevo proyecto" (Cmd+N en macOS, Ctrl+N en los demás) y rellena:

- **Nombre** y **repositorio base**: el repositorio Git del que salen los worktrees.
- **Carpeta raíz de worktrees**: donde se crean los worktrees nuevos.
- **Rama principal** (opcional): la rama con la que se comparan el estado y si una rama está mergeada; se detecta si se deja vacía.
- **Archivos locales**: archivos que Git no versiona y que cada worktree nuevo necesita en esta máquina (p. ej. `local.properties`). Solo se guardan en esta máquina; para compartirlos con todo el que clone el repositorio, decláralos en [`.shogunai.json`](configuration.md#configuracion-del-repositorio-shogunaijson). "Mover a .shogunai.json" mueve allí los que están dentro del repositorio.
- **Agentes**: cada uno con un nombre opcional, su **comando** con los argumentos por defecto y su opción de **Headroom**. El primero es el predeterminado y el que ejecutan "Copiar comando", "Abrir en Herdr" y "Abrir en terminal" en un worktree (`claude` por defecto). Añade más para cambiar de agente, por ejemplo cuando uno agota su cuota; los nombres deben ser distintos, y "Hacer predeterminado", los botones de mover y "Quitar agente" los reordenan (un proyecto conserva al menos uno).
- **IDE en este equipo** (opcional): el IDE en el que se abren los worktrees de este proyecto cuando no es el predeterminado de Preferencias (p. ej. Android Studio para un repositorio Android). Solo se guarda en esta máquina.

El formulario muestra también, en solo lectura, lo que el repositorio declara en `.shogunai.json` y `.worktreeinclude`, y los ajustes por máquina de los providers de [emulador](providers.md#emulador-de-android) y [simulador de iOS](providers.md#simulador-de-ios) cuando el repositorio los usa.

El catálogo se puede buscar por nombre y ordenar; cada proyecto se puede abrir, editar o eliminar. Si mueves un repositorio en disco, edita su ruta en el proyecto: la app mueve con él la carpeta de worktrees cuando estaba junto al repositorio, y vuelve a enlazar los worktrees (`git worktree repair`).

## Crear un worktree

Abre un proyecto y, en "Nuevo worktree", elige:

- **Rama nueva**: escribe el id de la tarea (p. ej. `TASK-123`) y el tipo de rama, `feature` o `fix`. La app crea `feature/TASK-123` en el commit elegido en **Partir de**: por defecto la rama principal del proyecto (`origin/develop` si existe), la rama actual del repositorio base, o cualquier rama local o de `origin/`; si no se resuelve ninguna rama principal, el valor por defecto es la rama actual. Para partir de un tag o de un commit, usa [`shogun new --from`](cli.md#new). Los espacios del id se convierten en `-`, y los ids que Git no aceptaría como nombre de rama se rechazan.
- **Rama existente**: elige una rama de la lista, agrupadas por prefijo. Las ramas que ya tienen worktree no se pueden elegir. También se listan las ramas que existen solo en `origin`, marcadas como remotas; al elegir una se crea una rama local con ese nombre que la sigue.

Cuando el repositorio tiene un remoto `origin`, **Hacer fetch de origin antes** hace fetch antes de crear, en cualquiera de los dos modos, para que la rama elegida esté al día. Está desactivado por defecto y cada proyecto recuerda su último valor en este equipo. El fetch nunca espera credenciales: si falla, el diálogo muestra el error de Git con "Volver a intentar" y "Crear sin hacer fetch", y no se crea nada hasta que elijas una.

"Crear worktree" (o Cmd+N / Ctrl+N) lo crea y copia los archivos que declaran el repositorio y el proyecto. Si una copia falla, el worktree se vuelve a eliminar para no dejar nada a medias.

## La lista de worktrees

Cada fila muestra un worktree con:

- Su **rama**, la etiqueta "bloqueado" cuando una sesión de agente lo retiene, su **origen** (ShogunAi, Claude Code, Herdr u otra ubicación) y los **agentes en ejecución** en él. Con Herdr en marcha, cada agente muestra además su estado (trabajando, bloqueado, terminado o inactivo).
- Su **estado**: cambios sin commitear, commits por delante y por detrás de su upstream y de la rama principal, la fecha del último commit, "mergeada en <rama principal>", o "sin empezar" cuando su rama nunca tuvo commits propios. "Carpeta no encontrada" cuando su carpeta se borró a mano.
- Sus **archivos provisionados**: si los archivos y enlaces declarados para los worktrees nuevos coinciden con los del repositorio base, listando los que faltan, difieren o no están enlazados. En un worktree creado por otra herramienta, o cuyas copias están desactualizadas, **Provisionar** los copia, reserva sus puertos y escribe su archivo de entorno, como hace `shogun provision`.
- Su **entorno**: los puertos y cada variable de `"env"` en `.shogunai.json` con su valor, en el orden en que las imprime `shogun env`. **Copiar como .env** las pone en el portapapeles en formato dotenv. Los valores no se ocultan, porque salen de plantillas del repositorio.
- Su **tamaño**, los **puertos** reservados para él (ver [entorno por worktree](../agents.md#entorno-por-worktree)) y la etiqueta "inactivo" cuando no tiene cambios sin commitear, ningún agente en ejecución ni commits en los últimos 14 días.
- Las líneas de emulador y simulador con Arrancar y Parar, en repositorios que usan esos [providers](providers.md).

La lista se actualiza sola: los cambios hechos desde un terminal o por un agente aparecen en pocos segundos, y las ediciones que aún no están en el índice aparecen al recuperar el foco la ventana. Los estados y tamaños se siguen mostrando mientras se vuelven a leer. Los tamaños se miden cuando un worktree aparece por primera vez; "Recargar" los vuelve a medir todos, por ejemplo tras una compilación.

"Activos" y "Todos los worktrees" abarcan todos los proyectos; su filtro de proyecto reduce la vista actual a un proyecto sin salir de ella. Al seleccionar un proyecto en la barra lateral se abre, en cambio, la vista propia de ese proyecto, que no tiene filtro de proyecto.

## Estados

Agrupada por estado, la cabecera de cada grupo muestra una pista breve junto a su título, y al pasar el ratón por el título se ve su criterio completo. "Activos" muestra los tres primeros grupos; "Todos los worktrees", todos.

| Grupo | Pista | Criterio |
|---|---|---|
| Con agente | agente en marcha | Hay un agente de IA en marcha en el worktree. |
| Te necesitan | agente esperando, sin commit o sin subir | Un agente bloqueado o terminado en Herdr, cambios sin commit, o commits sin subir a su upstream (o a la rama principal si no tiene). |
| En curso | subido, sin fusionar | Limpio, subido, sin fusionar y con algún commit en los últimos 14 días, o una rama que nunca tuvo commits propios ("sin empezar"). |
| Fusionados | ya en la rama principal | Sin commits propios por delante de la rama principal, habiéndolos tenido. Se puede limpiar. Una rama que nunca tuvo commits y no tiene upstream se muestra como "sin empezar" y nunca se ofrece para limpiar. |
| Inactivos | sin commits en 14 días | Limpio, sin agente y sin commits en los últimos 14 días. |
| Carpeta no encontrada | carpeta borrada | Git aún lo lista, pero su carpeta ya no existe. Se puede limpiar. |
| Otros | principal o sin leer | El worktree principal cuando está limpio, o un worktree cuyo estado aún no se ha leído. |

## Trabajar en un worktree

- **Copiar comando** copia `cd "<worktree>" && <comando del agente>` para pegarlo en cualquier terminal.
- **Abrir en Herdr** abre el worktree en [Herdr](../agents.md#herdr) con su entorno y ejecuta allí el comando del agente; si el worktree ya tiene un agente en Herdr, enfoca el panel de ese agente en su lugar.
- **Abrir en terminal** abre una ventana o pestaña nueva de tu terminal en la carpeta del worktree, con las variables de entorno del worktree y ejecutando el comando del agente; el shell sigue abierto cuando el agente termina. Se admiten Terminal e iTerm2 (macOS), Windows Terminal (Windows), y tmux (3.0 o posterior) y Zellij (macOS y Linux); tmux y Zellij abren una ventana o pestaña nueva en la última sesión que usaste, por lo que solo se ofrecen mientras haya una en marcha. La primera vez, la app pregunta qué terminal usar. En macOS, Terminal e iTerm2 piden tu permiso la primera vez que ShogunAi los controla; si lo rechazas, permítelo en Ajustes del Sistema > Privacidad y seguridad > Automatización.
- **Abrir en IDE** abre la carpeta del worktree en VS Code, IntelliJ IDEA o Android Studio, según el IDE predeterminado o el elegido para el proyecto.
- **Abrir carpeta** muestra la carpeta del worktree en Finder, el Explorador o el gestor de archivos.

Con varios agentes, el detalle del worktree muestra un campo **Agente** y las tres acciones de lanzamiento nombran el agente que usan ("Copiar comando · Codex"). Elige otro agente en el campo, con la flecha junto a cada acción, en el menú contextual del worktree o en la paleta de comandos; lanzar con otro agente lo convierte en el agente del worktree, y sus siguientes lanzamientos continúan con él. La elección se recuerda solo en esta máquina, y elegir el agente predeterminado o quitar del proyecto el elegido vuelve al predeterminado. Los agentes en ejecución se reconocen para cada comando de agente del proyecto.

"Copiar comando" sigue siendo el botón principal; las demás acciones están a su lado, en el menú contextual del worktree y en la paleta de comandos (para el worktree seleccionado). Solo se ofrecen para las herramientas que hay en esta máquina, y "Abrir en IDE" y "Abrir carpeta" desaparecen cuando la carpeta del worktree ya no existe.

## Eliminar y limpiar

- **Eliminar** elimina un worktree, con la opción de borrar también su rama local. El worktree principal no se puede eliminar.
- **Limpiar**, junto al título "Worktrees", lista los worktrees mergeados o cuya carpeta ya no existe y los elimina de una vez, con la opción de borrar sus ramas. Según los providers del repositorio, ofrece también parar sus daemons de Gradle ociosos y sus emuladores, y borrar sus simuladores de iOS y los DerivedData de Xcode huérfanos.

Eliminar un worktree abierto en Herdr cierra también su espacio de trabajo de Herdr; los diálogos lo avisan antes.

## Secciones de los providers

En repositorios que los declaran en `.shogunai.json`, la pantalla de worktrees añade una sección "Daemons de Gradle" y otra "DerivedData de Xcode". Consulta [Providers](providers.md).

## Preferencias

Preferencias (Cmd+Coma en macOS, Ctrl+Coma en los demás) reúne el tema, el idioma, la sección **Herramientas**, donde eliges el terminal y el IDE predeterminado entre los que ShogunAi ha encontrado en esta máquina (si no encuentra ninguno, la sección lo dice y las acciones correspondientes no se ofrecen), y **Buscar actualizaciones automáticamente**, activada por defecto. Consulta [Actualizaciones](#actualizaciones).

## Icono en la barra de menús y en la bandeja del sistema

Mientras ShogunAi se ejecuta, un icono en la barra de menús de macOS (o en la bandeja del sistema en los escritorios de Windows y Linux que la tienen) abre un menú con:

- **Mostrar ShogunAi** y **Nuevo worktree…**, que traen la ventana al frente.
- **Proyectos recientes**: hasta 5, los que abriste más recientemente.
- **Worktrees recientes**: hasta 8, los que seleccionaste, creaste o lanzaste más recientemente. Cada uno es un submenú con **Mostrar en ShogunAi**, **Copiar comando de lanzamiento** y, según estén disponibles, **Abrir en <terminal>**, **Abrir en <IDE>** y **Abrir carpeta**, que se comportan como en el detalle del worktree. Las acciones de terminal, IDE y carpeta se ejecutan sin traer la ventana al frente; si una falla, la ventana pasa al frente y muestra el error.
- **Preferencias** y **Salir**.

La etiqueta de un worktree es `<rama> — <proyecto>`, con el prefijo `●` cuando un agente trabaja en él y `!` cuando te necesita (cambios sin confirmar, commits sin subir, o un agente bloqueado o terminado). Los recientes se recuerdan entre reinicios y los proyectos y worktrees eliminados desaparecen de ellos.

Preferencias tiene dos opciones para esto, que solo aparecen donde el sistema tiene bandeja:

- **Mostrar el icono en la barra de menús** (bandeja del sistema en Windows y Linux), activada por defecto.
- **Seguir ejecutándose al cerrar la ventana**, desactivada por defecto y disponible solo mientras el icono se muestra. Con ella activada, cerrar la ventana la oculta y la app sigue en el icono; **Mostrar ShogunAi** devuelve la ventana y **Salir** (o Cmd/Ctrl+Q) cierra la app. Los ajustes de proyecto sin guardar siguen preguntando antes de cerrar la ventana. Con ella desactivada, cerrar la ventana cierra la app, como antes.

## Tema e idioma

La barra superior tiene un selector de tema (claro, oscuro, o automático para seguir al sistema) y un selector de idioma (English o Español). Las dos elecciones se recuerdan entre reinicios.

## Actualizaciones

Cada vez que arranca, y después una vez al día mientras sigue abierta, la app pregunta a GitHub por la última versión publicada. Si es más reciente que la que usas, aparece un aviso breve arriba de la ventana («¡Ya está aquí ShogunAi 1.3.0! Tienes la 1.2.0.») con un enlace **Novedades** a las notas de la versión. El aviso no interrumpe tu trabajo y te recuerda cómo actualizar según cómo se instaló ShogunAi:

| Instalada con | El aviso |
|---|---|
| Homebrew | `brew upgrade --cask shogunai`, con un botón **Copiar** |
| Scoop | `scoop update shogunai`, con un botón **Copiar** |
| winget | `winget upgrade LuxionServer.ShogunAi`, con un botón **Copiar** |
| El `.dmg`, `.msi` o `.deb` | Un botón **Descargar** para el nuevo instalador (con el `.deb`, también el comando `sudo apt install ./<archivo>` que ejecutar después) |
| Otra forma | Un enlace a la página de la versión |

La app nunca descarga, instala ni ejecuta nada por sí sola. **Omitir esta versión** oculta el aviso hasta que salga una versión más nueva; cerrarlo lo oculta hasta el siguiente arranque. A veces la versión se publica unos minutos antes de que llegue el paquete de Homebrew, Scoop o winget, así que el comando puede tardar un momento en funcionar.

**Buscar actualizaciones…** en el menú Ayuda comprueba al instante, sin tener en cuenta el horario ni las versiones omitidas, y te dice si ShogunAi está al día, qué versión hay disponible o por qué falló la comprobación. Las compilaciones de desarrollo no comprueban por su cuenta.

La única petición que la app hace por su cuenta es un `GET` anónimo a `https://api.github.com/repos/LuxionServer/ShogunAi/releases/latest`, que lleva la versión de la app en la cabecera `User-Agent` y nada más. Desactiva **Buscar actualizaciones automáticamente** en Preferencias y la app solo contactará con GitHub cuando elijas **Buscar actualizaciones…**.
