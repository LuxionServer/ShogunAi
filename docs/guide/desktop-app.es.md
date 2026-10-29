# App de escritorio

La app de escritorio gestiona un catálogo de proyectos y, para cada uno, sus worktrees. Todo lo que hace está disponible también desde el [comando `shogun`](cli.md).

## Recorrido guiado

El primer arranque, sin proyectos todavía, ofrece **Hacer el recorrido** junto a "Añadir proyecto", y Ayuda > **Recorrido de ShogunAi** lo inicia en cualquier momento. Si ya tenías proyectos al actualizar, un aviso lo ofrece una vez.

El recorrido:

1. Ofrece instalar el [comando `shogun`](installation.md#el-comando-shogun) si aún no está en tu `PATH`, porque lo usan los agentes y los hooks de Claude Code. Puedes saltarlo.
2. Crea un repositorio de ejemplo desechable en `~/.shogunai/tour`, con un remoto local, un [`.shogunai.json`](configuration.md#configuracion-del-repositorio-shogunaijson) y worktrees en cada estado que muestra la lista ("Te necesitan", "Nuevos", "Parados", "Fusionados", "Inactivos", uno bloqueado y "Carpeta no encontrada"), y lo registra como el proyecto "ShogunAi tour".
3. Abre el agente del recorrido en el worktree principal del ejemplo, en la terminal, el runner en uso (Herdr u Orca) o Claude Desktop, o copia su comando de arranque. El agente es Claude Code (Codex desde el menú de agentes), arrancado con "Start the ShogunAi tour"; el guion que sigue es el `AGENTS.md` del ejemplo, y habla el idioma de la app. Recorre estado, limpieza, aprovisionamiento, la línea de comandos, hooks, entorno y providers paso a paso, esperándote entre pasos, y no cambia nada fuera de `~/.shogunai/tour`. Antes de empezar, el agente pregunta si confías en la carpeta del ejemplo: acéptalo («Yes, I trust this folder» en Claude Code).

Mientras exista el ejemplo, su proyecto muestra un banner para volver a abrir el agente o **Terminar recorrido**, que elimina el proyecto, sus worktrees y `~/.shogunai/tour` tras preguntar. Volver a iniciar el recorrido ofrece abrir el ejemplo existente o reiniciarlo desde cero.

## Proyectos

La primera vez que abres la app verás el catálogo de proyectos vacío. Pulsa "Nuevo proyecto" (Cmd+N en macOS, Ctrl+N en los demás) y rellena:

- **Nombre** y **repositorio base**: el repositorio Git del que salen los worktrees.
- **Carpeta raíz de worktrees**: donde se crean los worktrees nuevos.
- **Rama principal** (opcional): la rama con la que se comparan el estado y si una rama está mergeada; se detecta si se deja vacía.
- **Archivos locales**: archivos que Git no versiona y que cada worktree nuevo necesita en esta máquina (p. ej. `local.properties`): rutas de archivos o carpetas, o patrones como `*.jks` (relativos al repositorio, y que solo seleccionan archivos sin versionar). Cada entrada se copia, o se enlaza si activas su interruptor Enlazar; una carpeta copiada dice cuántos enlaces simbólicos suyos se omiten. Solo se guardan en esta máquina; para compartirlos con todo el que clone el repositorio, decláralos en [`.shogunai.json`](configuration.md#configuracion-del-repositorio-shogunaijson). "Mover a .shogunai.json" mueve allí los que están dentro del repositorio, los enlazados a `link`.
- **Secretos encontrados en este repositorio**: los `local.properties`, keystores y archivos de Firebase (`google-services.json`, `GoogleService-Info.plist`) que el repositorio tiene sin versionar y que nada provisiona todavía, cada uno con "Enlazar desde .shogunai.json", que añade un patrón `link` anclado que se escribe al guardar; y un aviso por cada ruta de keystore de esos ficheros de propiedades que sale del repositorio y no se resolverá desde un worktree. Ver [detección de stacks](providers.md#deteccion-de-stacks).
- **Agentes**: cada uno con un nombre opcional, su **comando** con los argumentos por defecto y su opción de **Headroom** (`headroom wrap`, desactivada por defecto; los agentes guardados por versiones anteriores conservan la que tenían). El primero es el predeterminado y el que ejecutan "Copiar comando", "Abrir en Herdr", "Abrir en Orca" y "Abrir en terminal" en un worktree (`claude` por defecto). Añade más para cambiar de agente, por ejemplo cuando uno agota su cuota; los nombres deben ser distintos, y "Hacer predeterminado", los botones de mover y "Quitar agente" los reordenan (un proyecto conserva al menos uno).
- **IDE en este equipo** (opcional): el IDE en el que se abren los worktrees de este proyecto cuando no es el predeterminado de Preferencias (p. ej. Android Studio para un repositorio Android). Solo se guarda en esta máquina.

El formulario muestra también, en solo lectura, lo que el repositorio declara en `.shogunai.json` y `.worktreeinclude`, y los ajustes por máquina de los providers de [emulador](providers.md#emulador-de-android) y [simulador de iOS](providers.md#simulador-de-ios) cuando el repositorio los usa.

La barra lateral lista los proyectos y multirepos por orden alfabético hasta que los reordenas: arrastra una entrada arriba o abajo dentro de su nivel (el nivel superior, o los proyectos de dentro de la carpeta de un multirepo), o usa "Mover arriba" y "Mover abajo" en su menú contextual, también en la barra lateral compacta. Así un proyecto nunca entra ni sale de un multirepo. El orden se conserva entre ejecuciones, los proyectos y multirepos nuevos aparecen al final de su nivel, y "Ordenar alfabéticamente" en el menú contextual vuelve al orden por defecto de ese nivel.

En la parte inferior de la ventana, la **barra de estado** resume los recursos compartidos de la máquina, con un elemento por cada uno que aplica en este equipo: el turno de escritorio, los daemons de Gradle, los emuladores de Android, los simuladores de iOS (macOS), DerivedData de Xcode (macOS), los daemons de Maven y los proyectos de Compose, cada uno en cuanto algún proyecto declara su provider. Cada elemento muestra su cifra: el uso del pool frente a su límite (`2/3`), el tamaño de DerivedData frente a su cuota, o un recuento. Pasa el puntero por encima para ver el resumen completo, y actívalo, con el puntero o el teclado, para abrir su vista. Un elemento pasa al color de aviso cuando su pool supera el límite o DerivedData supera su cuota, y al color de error, con el motivo en el tooltip, cuando no se ha podido leer; un guion indica que aún no se ha medido. En una ventana estrecha los elementos conservan solo el icono y la cifra. La paleta de comandos abre las mismas vistas.

El catálogo se puede buscar por nombre y ordenar; cada proyecto se puede abrir, editar o eliminar. Si mueves un repositorio en disco, edita su ruta en el proyecto: la app mueve con él la carpeta de worktrees cuando estaba junto al repositorio, y vuelve a enlazar los worktrees (`git worktree repair`).

## Multirepos

Un [multirepo](multirepo.md) agrupa los repositorios de un producto. Suelta en la ventana una carpeta que no sea un repositorio Git, o pulsa "Añadir multirepo" en la barra lateral y elige una. Antes de escribir nada, el diálogo lista los repositorios Git que hay directamente dentro de la carpeta con un alias propuesto para cada uno (el nombre de la carpeta sin el prefijo del multirepo, así que `product-app` pasa a ser `app`), marca los que ya son proyectos, propone `<worktreesRoot>/<alias>` como carpeta de worktrees de cada uno (marcado en los proyectos nuevos y desmarcado en los existentes, que se quedan como están salvo que lo marques) y lista los grupos que ya forman las ramas coincidentes. "Añadir un repositorio de otra ubicación…" añade un repositorio de fuera de la carpeta. Una carpeta dentro de un repositorio no puede contener un multirepo, y el diálogo lo dice. Una carpeta que ya tiene declaración, por ejemplo un multirepo cuya carpeta se ha movido, se revisa como se explica más abajo; al confirmar se aprueba y se registra un proyecto para cada repositorio declarado que no lo tenga, como hace ahí `shogun multirepo init`. La app también olvida los multirepos cuya carpeta ya no existe. Al confirmar se escribe el mismo `.shogunai-multirepo.json` que con `shogun multirepo init` y `add-repo`, con todos los repositorios elegidos como predeterminados.

En la barra lateral, un multirepo es una carpeta con su nombre visible (el de su carpeta salvo que los ajustes le den otro) y sus proyectos dentro, sangrados un nivel bajo su nombre; muestra la suma de sus worktrees activos y el indicador de agente, se pliega con su flecha y lo recuerda. Al seleccionarlo se abre la vista del multirepo:

- **Por cambio** (por defecto): un bloque por grupo, titulado con la rama sin su prefijo (`TASK-12`), con la rama completa, sus repositorios y un estado combinado, "Fusionados" solo cuando lo están todos sus worktrees y, si no, el estado más urgente entre ellos. Su cabecera tiene un botón, "Abrir todo en <runner por defecto>", y un menú ⋯ con "Abrir todo con…" (el otro runner cuando está instalado; Orca abre los worktrees uno tras otro, un mensaje dice por repositorio si su agente se arrancó o se enfocó, y un worktree que falla muestra su error), "Añadir repositorio" (un worktree en la rama del grupo en un repositorio que aún no está, para ramas `feature/` y `fix/`) y, al final, "Limpiar grupo", activo solo cuando todos los worktrees están limpios y fusionados sin ningún agente en marcha; deshabilitado, dice qué repositorio lo impide. "Sin grupo" lista el resto de worktrees. Cada fila empieza por el alias de su repositorio.
- **Por estado**: los worktrees del multirepo agrupados por estado, también con su alias.

La cabecera tiene "Nuevo cambio", que crea el grupo en los repositorios marcados (de entrada, los predeterminados) como `shogun new <id> --repos` e informa del resultado de cada repositorio, los ajustes del multirepo y, en su menú "Más acciones" (⋯), "Abrir agente del multirepo en <runner por defecto>", "Abrir agente del multirepo con…" con el otro runner (solo cuando está instalado) y "Limpiar". "Nuevo cambio" tiene además un selector para abrir los worktrees nuevos una vez creados: "No abrir", "Herdr" u "Orca", empezando en el runner por defecto cuando se ofrece y en "No abrir" si no, cada uno solo cuando está instalado y el selector oculto sin ninguno. Con uno elegido, el diálogo abre los worktrees que existen como "Abrir todo" y muestra, tras el resultado de creación de cada repositorio, el de su apertura. "Abrir agente del multirepo en Orca" funciona como `shogun open --orca` en la carpeta del multirepo: un mensaje dice si el agente se arrancó o se enfocó, y una carpeta que no es un repositorio Git sin espacio de trabajo en Orca muestra cómo añadirla en la app de Orca (ver [Multirepos y grupos](multirepo.md#grupos)). "Limpiar grupo" marca los worktrees con terminales de Orca, como "Limpiar". En las demás vistas, un worktree de un grupo muestra la etiqueta `TASK-12 · +api` antes de su carpeta; al pulsarla se abre la vista del multirepo en ese grupo. Agrupando por proyecto, la cabecera de un proyecto dice `product / app`.

Cuando la carpeta del multirepo es un repositorio Git, es el repositorio de workspace del multirepo: sus worktrees se listan bajo el multirepo (no como un proyecto suelto, y ShogunAi registra su proyecto una sola vez por ti), la barra lateral lo lista dentro de la carpeta con su alias (`workspace`) para que no se repita el nombre del multirepo, "Nuevo cambio" lo lista como `workspace` y "Añadir proyecto" o soltar la carpeta explica que ya pertenece al multirepo en vez de añadirla. La vista del multirepo muestra el agente del multirepo, el que corre en la carpeta, con su estado junto a "Abrir agente del multirepo en Herdr", y la fila del multirepo en la barra lateral refleja su actividad; un agente que corre en un repositorio miembro aparece, en cambio, en el worktree de ese repositorio.

Los ajustes tienen una sección "Repositorio workspace" cuando la carpeta es un repositorio Git: aparece marcado como detectado hasta que lo declaras, con una casilla para declararlo, un campo para su alias, un botón "Desactivar" para dejar de tratar la carpeta como repositorio del multirepo y, cuando está declarado o desactivado, "Volver a la detección", que quita el ajuste para que la carpeta vuelva a detectarse; figura entre los repositorios predeterminados. También fijan el nombre del multirepo, añaden, renombran y quitan repositorios, eligen los repositorios predeterminados, editan las [variables compartidas](multirepo.md#variables-compartidas) (una fila por variable con su repositorio, nombre y plantilla; una fila con una plantilla inválida dice por qué e impide guardar), fijan la carpeta de worktrees que se propone a los repositorios que se añadan después, editan el agente del multirepo con los mismos campos que el agente de un proyecto y eliminan el multirepo, lo que borra solo la declaración. Los cambios hechos con `shogun multirepo` aparecen en el siguiente refresco de la app.

ShogunAi solo usa un multirepo aprobado en este equipo (ver [Aprobar un multirepo](multirepo.md#aprobar-un-multirepo)). Cuando la carpeta que añades ya tiene una declaración, por ejemplo un repositorio de workspace clonado, el diálogo también lista sus repositorios con la ruta a la que apunta cada uno, su carpeta de worktrees y su repositorio de workspace, marcando cualquier ruta fuera de la carpeta padre; al confirmar se aprueba. Un multirepo cuya declaración ha cambiado fuera de ShogunAi, o que registró una versión anterior, sigue en la barra lateral con una marca de aviso y sus repositorios aparecen como proyectos sueltos. Su vista muestra la misma revisión con "Confiar" en lugar de sus grupos, y "Nuevo cambio", "Abrir agente del multirepo en Herdr", "Limpiar" y guardar sus ajustes quedan desactivados hasta que confíes en él. El agente del multirepo que editas en los ajustes se guarda en este equipo, nunca en la declaración; un `agent` que haya dejado en la declaración una versión anterior se ignora, y los ajustes lo indican.

## Crear un worktree

Abre un proyecto y, en "Nuevo worktree", elige:

- **Rama nueva**: escribe el id de la tarea (p. ej. `TASK-123`) y el tipo de rama, `feature` o `fix`. La app crea `feature/TASK-123` en el commit elegido en **Partir de**: por defecto la rama principal del proyecto (`origin/develop` si existe), la rama actual del repositorio base, o cualquier rama local o de `origin/`; si no se resuelve ninguna rama principal, el valor por defecto es la rama actual. Para partir de un tag o de un commit, usa [`shogun new --from`](cli.md#new). Los espacios del id se convierten en `-`, y los ids que Git no aceptaría como nombre de rama se rechazan.
- **Rama existente**: elige una rama de la lista, agrupadas por prefijo. Las ramas que ya tienen worktree no se pueden elegir. También se listan las ramas que existen solo en `origin`, marcadas como remotas; al elegir una se crea una rama local con ese nombre que la sigue.

Cuando el repositorio tiene un remoto `origin`, **Hacer fetch de origin antes** hace fetch antes de crear, en cualquiera de los dos modos, para que la rama elegida esté al día. Está desactivado por defecto y cada proyecto recuerda su último valor en este equipo. El fetch nunca espera credenciales: si falla, el diálogo muestra el error de Git con "Volver a intentar" y "Crear sin hacer fetch", y no se crea nada hasta que elijas una.

"Crear worktree" (o Cmd+N / Ctrl+N) lo crea y copia los archivos que declaran el repositorio y el proyecto. Si una copia falla, el worktree se vuelve a eliminar para no dejar nada a medias.

## La lista de worktrees

Cada fila tiene dos líneas y un sitio fijo para cada dato, así que las filas se alinean y se comparan de un vistazo:

- Una **insignia de estado**: un círculo de color con un icono distinto para cada [estado](#estados), de modo que se distingue sin color. Al pasar el ratón muestra el nombre del estado.
- La columna de **ejecución**: el agente en la primera línea, como un punto de color con su nombre y su estado: trabajando, te necesita (bloqueado o esperándote), terminado, inactivo, o en ejecución cuando ninguna herramienta informa de su estado (pasa el ratón para ver todos los agentes, qué herramienta informó del estado y cuándo se leyó) y, en la segunda, si el worktree tiene el [turno del escritorio](cli.md#desktop) o lo espera (con su puesto en la cola), el emulador o simulador mientras arranca o está en marcha, y su [app en segundo plano](headless-app.md). Con varios agentes, o varias cosas en la segunda línea, muestra la primera con un "+N" y el tooltip las lista todas. En una lista estrecha se reduce al punto del agente y a los iconos de los dispositivos, que conservan sus colores; pasa el ratón para ver las etiquetas.
- El **nombre**: la rama en la primera línea; en la segunda, la carpeta cuando difiere del último segmento de la rama, el **origen** (ShogunAi, Claude Code, Herdr, Orca u otra ubicación) y, en las vistas que abarcan varios proyectos y no están agrupadas por proyecto, el proyecto.
- La columna **Git**: los cambios sin commitear en la primera línea y, en la segunda, los commits por delante y por detrás de su base (la rama de la que se creó, o la rama principal si no hay ninguna registrada), o "fusionado". Un guion significa que no se pudo leer el estado.
- La columna de **tiempo**: cuándo estuvo activo el worktree por última vez, diciendo qué mide ("commit hace 3 horas" cuando su rama tiene commits propios, "creado hace 2 días" en otro caso), y debajo las marcas "principal" y "bloqueado".

El detalle muestra más, siempre en el mismo orden: la rama, el origen y la ruta, las acciones y después las secciones **Estado**, **Agente**, **Entorno**, **Recursos** y **Archivos provisionados**. Haz clic en el título de una sección para plegarla; sigue plegada al pasar de un worktree a otro. Una sección cuyos datos aún no se han leído muestra un marcador, y la que no aplica al worktree no aparece. Los valores se pueden seleccionar, y al pasar el ratón sobre un puerto o una variable se ofrece copiarlo.

- Sus **agentes**, con su estado y, en los que informa [Orca](../agents.md#orca), la herramienta a la que esperan con su entrada, el prompt que recibieron y su último mensaje, cada uno recortado a tres líneas.
- Su **estado**: cambios sin commitear, commits por delante y por detrás de su upstream y de su base, nombrada en la fila "vs <rama>" (la rama principal si no hay base registrada), la fecha del último commit, la fecha de creación, y "fusionado" en la fila de la rama en la que está fusionada (su base o la rama principal) o "sin empezar" cuando su rama nunca tuvo commits propios. "Carpeta no encontrada" cuando su carpeta se borró a mano.
- Sus **archivos provisionados**: si los archivos y enlaces declarados para los worktrees nuevos coinciden con los del repositorio base, listando los que faltan, difieren o no están enlazados. En un worktree creado por otra herramienta, o cuyas copias están desactualizadas, **Provisionar** los copia, reserva sus puertos y escribe su archivo de entorno, como hace `shogun provision`.
- Su **entorno**: los puertos y cada variable de `"env"` en `.shogunai.json` con su valor, en el orden en que las imprime `shogun env`. **Copiar como .env** las pone en el portapapeles en formato dotenv. Los valores no se ocultan, porque salen de plantillas del repositorio. Mientras [Orca](../agents.md#orca) está en marcha, también lista los puertos en los que Orca ve escuchar al worktree, con el proceso y la dirección que un servidor de desarrollo imprimió en una terminal de Orca, y marca como escuchando los puertos reservados que estén entre ellos.
- Su **tamaño** y los **puertos** reservados para él (ver [entorno por worktree](../agents.md#entorno-por-worktree)).
- Las líneas de emulador y simulador con Arrancar y Parar, en repositorios que usan esos [providers](providers.md).

La lista se actualiza sola: los cambios hechos desde un terminal o por un agente aparecen en pocos segundos, y las ediciones que aún no están en el índice aparecen al recuperar el foco la ventana. Los estados y tamaños se siguen mostrando mientras se vuelven a leer. Los tamaños se miden cuando un worktree aparece por primera vez; "Recargar" los vuelve a medir todos, por ejemplo tras una compilación.

"Activos" y "Todos los worktrees" abarcan todos los proyectos; su filtro de proyecto reduce la vista actual a un proyecto sin salir de ella. Al seleccionar un proyecto en la barra lateral se abre, en cambio, la vista propia de ese proyecto, que no tiene filtro de proyecto.

Todas las vistas tienen un selector de orden junto al de agrupación: **Nombre** (el predeterminado; ramas en orden natural, así que `TASK-9` va antes que `TASK-10`) o **Última actividad** (la más reciente primero). El worktree principal siempre va primero en su grupo. Cada vista recuerda su propia elección.

## Estados

Cada worktree está en el primer estado que se cumple. Agrupada por estado, la cabecera de cada grupo muestra una pista breve junto a su título, y al pasar el ratón por el título se ve su criterio completo. "Activos" muestra los cuatro primeros grupos; "Todos los worktrees", todos.

| Grupo | Pista | Criterio |
|---|---|---|
| Te necesitan | agente esperando, sin commit o sin subir | Un agente está bloqueado o ha terminado, o ningún agente trabaja y hay cambios sin commit o commits sin subir a su upstream (o a su base si no tiene). |
| Con agente | agente en marcha | Un agente de IA está trabajando en el worktree, o se ejecuta sin estado conocido. Un agente inactivo no decide el estado. |
| Nuevos | aún sin commits | Sin commits propios, sin cambios sin commit y sin agente en marcha. |
| Parados | subido, en espera | Con commits propios, todos subidos, sin cambios pendientes, sin fusionar y sin agente trabajando. |
| Fusionados | ya en su base | Sin commits propios por delante de su base o de la rama principal, habiéndolos tenido. Se puede limpiar. Una rama que nunca tuvo commits y no tiene upstream se muestra como "nueva" y nunca se ofrece para limpiar. |
| Inactivos | sin actividad en 3+ días | Limpio, sin agente en marcha y sin actividad en los últimos 3 días (ver abajo). |
| Carpeta no encontrada | carpeta borrada | Git aún lo lista, pero su carpeta ya no existe. Se puede limpiar. |
| Otros | principal o sin leer | El worktree principal cuando está limpio, o un worktree cuyo estado aún no se ha leído. |

La actividad de un worktree es la fecha de su último commit cuando su rama tiene commits propios, y la fecha en que se creó en otro caso, de modo que un worktree nuevo no se fecha por el commit antiguo del que partió. Un worktree sin commits propios nunca está inactivo. El umbral de inactividad es de 3 días por defecto; cámbialo en [Preferencias](#preferencias) (de 1 a 90 días) y todas las vistas se actualizan a la vez.

## Trabajar en un worktree

- **Copiar comando** copia `cd "<worktree>" && <comando del agente>` para pegarlo en cualquier terminal.
- **Abrir en Herdr** abre el worktree en [Herdr](../agents.md#herdr) con su entorno y ejecuta allí el comando del agente; si el worktree ya tiene un agente en Herdr, enfoca el panel de ese agente en su lugar, y si su espacio de trabajo está abierto sin agente, arranca el agente en el único panel de ese espacio de trabajo cuando ese panel está en el worktree con su shell en el prompt, o en un panel nuevo de ese espacio de trabajo cuando tiene varios paneles o su panel ejecuta algo (un servidor de desarrollo, por ejemplo); no se cierra ningún panel. Un panel reutilizado conserva el entorno que recibió al abrirse el espacio de trabajo: para tener el actual, cierra el panel y vuelve a abrir el worktree. Si Herdr no detecta el agente en 10 segundos, el worktree muestra el error con el panel y el comando, y el panel queda abierto.
- **Abrir en Orca** abre el worktree en [Orca](../agents.md#orca) bajo su repositorio, arrancando Orca si hace falta, en una terminal enfocada con su entorno que ejecuta el comando del agente; si Orca ya informa de un agente en una de las terminales del worktree, enfoca esa terminal en su lugar. Un mensaje dice si el agente se arrancó o se enfocó. Si Orca no informa del agente en 10 segundos, el worktree muestra el error con la terminal y el comando, y la terminal queda abierta.
- **Abrir en terminal** abre una ventana o pestaña nueva de tu terminal en la carpeta del worktree, con las variables de entorno del worktree y ejecutando el comando del agente; el shell sigue abierto cuando el agente termina. Se admiten Terminal e iTerm2 (macOS), Windows Terminal (Windows), y tmux (3.0 o posterior) y Zellij (macOS y Linux); tmux y Zellij abren una ventana o pestaña nueva en la última sesión que usaste, por lo que solo se ofrecen mientras haya una en marcha. La primera vez, la app pregunta qué terminal usar. En macOS, Terminal e iTerm2 piden tu permiso la primera vez que ShogunAi los controla; si lo rechazas, permítelo en Ajustes del Sistema > Privacidad y seguridad > Automatización.
- **Abrir en IDE** abre la carpeta del worktree en VS Code, IntelliJ IDEA o Android Studio, o su workspace en Xcode (solo en macOS), según el IDE predeterminado o el elegido para el proyecto. Para Xcode busca un `.xcworkspace` y, si no hay, un `.xcodeproj` en el worktree y sus subcarpetas (p. ej. `iosApp/`), y abre la carpeta si no encuentra ninguno.
- **Abrir carpeta** muestra la carpeta del worktree en Finder, el Explorador o el gestor de archivos.

Con varios agentes, "Abrir con…" tiene una sección **Con agente** donde fijas el agente del worktree sin lanzarlo, y Herdr, Orca, el terminal y "Copiar comando de lanzamiento" tienen un submenú "con agente" para lanzar con otro agente; la paleta de comandos también los encuentra ("Abrir en Orca con Codex"). Lanzar con otro agente lo convierte en el agente del worktree, y sus siguientes lanzamientos continúan con él. La elección se recuerda solo en esta máquina, y elegir el agente predeterminado o quitar del proyecto el elegido vuelve al predeterminado. Los agentes en ejecución se reconocen para cada comando de agente del proyecto.

El detalle del worktree reúne estas acciones en una barra: **Abrir en <runner por defecto>** (Herdr u Orca, elegido en [Preferencias](#preferencias)) es el botón principal, y su ▾ abre **Abrir con…**, que lista todas las formas de abrir el worktree (primero el runner por defecto), "Copiar comando de lanzamiento" y "Elegir runner por defecto…". Después vienen los iconos "Copiar comando de lanzamiento", "Abrir en terminal" y "Abrir carpeta", y **⋯** con "Copiar ruta" y, al final, "Eliminar worktree…". El menú contextual de la fila ofrece las mismas acciones, y la paleta de comandos las ofrece para el worktree seleccionado. Solo se ofrecen para las herramientas que hay en esta máquina, y cuando la carpeta del worktree ya no existe las acciones de abrir quedan desactivadas y dicen por qué.

Atajos sobre el worktree seleccionado: Cmd+O (Ctrl+O en los demás) lo abre en el runner por defecto, Mayús+Cmd+O abre "Abrir con…", Mayús+Cmd+C copia su comando de lanzamiento y Cmd+Retroceso pide eliminarlo. Cmd+E, que antes lo abría en Herdr, sigue abriéndolo en el runner por defecto durante una versión más.

## Eliminar y limpiar

- **Eliminar** elimina un worktree, con la opción de borrar también su rama local, que solo se borra si está mergeada en su base o en la rama principal del proyecto. Si no lo está (por ejemplo tras un squash merge), el worktree se elimina igualmente y un aviso indica que la rama se conservó, con la acción **Borrar la rama igualmente**, que pide confirmación antes. El worktree principal no se puede eliminar.
- **Limpiar**, junto al título "Worktrees", lista los worktrees mergeados o cuya carpeta ya no existe y los elimina de una vez, con la opción de borrar sus ramas. Según los providers del repositorio, ofrece también parar sus daemons de Gradle ociosos y sus emuladores, y borrar sus simuladores de iOS y los DerivedData de Xcode huérfanos.

Eliminar un worktree abierto en Herdr cierra también su espacio de trabajo de Herdr, y eliminar uno con terminales de Orca cierra esas terminales y los agentes que tienen; los diálogos lo avisan antes. Orca nunca se arranca para eliminar un worktree y, si sus terminales no se pueden cerrar, el worktree se elimina igualmente y un aviso muestra el mensaje de Orca. Eliminar o limpiar un worktree también para su app en segundo plano y libera su turno del escritorio; si la app no se puede parar, el worktree se elimina igualmente y un aviso lo dice hasta que lo descartas.

## El turno del escritorio

Los agentes que comprueban algo con computer use se turnan para manejar el escritorio ([`shogun desktop`](cli.md#desktop)). Mientras un worktree tiene el turno o alguno lo espera, la barra de estado muestra un elemento **Escritorio** con quién lo tiene y cuántos esperan, también cuando ningún proyecto declara un provider. Su vista muestra quién lo tiene, desde cuándo y hasta cuándo, y los worktrees que esperan por orden; actívalos para ir a su worktree. El detalle del worktree también muestra su turno o su puesto en la cola. **Liberar** libera el escritorio lo tenga quien lo tenga, tras preguntar, y lo recibe el primer worktree que espera; úsalo cuando un agente se queda atascado con el turno o necesitas el escritorio tú.

## Secciones de los providers

En repositorios que los declaran en `.shogunai.json`, la pantalla de worktrees añade una sección "Daemons de Gradle" y otra "DerivedData de Xcode". Las carpetas de DerivedData se listan enseguida y muestran "Calculando…" en sus tamaños hasta que se miden en segundo plano, salvo las que recuerdan su tamaño de una ejecución anterior. Salvo que elijas "Sin cuota" para DerivedData en Preferencias > Recursos (por defecto, una cuota automática calculada a partir del espacio libre en disco), la vista muestra también el uso frente a la cuota y, si se supera, libera las carpetas menos usadas recientemente de los worktrees que no están en uso. Consulta [Providers](providers.md).

## Preferencias

Preferencias (Cmd+Coma en macOS, Ctrl+Coma en los demás) reúne el tema, el idioma, el **umbral de inactividad** en días, la sección **Herramientas**, donde eliges el **runner por defecto** (Herdr u Orca, el que usan el botón principal del detalle y Cmd+O; Herdr si no lo cambias; ver [Elegir el runner](../agents.md#elegir-el-runner)), el terminal y el IDE predeterminado entre los que ShogunAi ha encontrado en esta máquina (si no encuentra ninguno, la sección lo dice y las acciones correspondientes no se ofrecen), y **Buscar actualizaciones automáticamente**, activada por defecto. Consulta [Actualizaciones](#actualizaciones).

## Icono en la barra de menús y en la bandeja del sistema

Mientras ShogunAi se ejecuta, un icono en la barra de menús de macOS (o en la bandeja del sistema en los escritorios de Windows y Linux que la tienen) abre un menú con:

- **Mostrar ShogunAi** y **Nuevo worktree…**, que traen la ventana al frente.
- **Proyectos recientes**: hasta 5, los que abriste más recientemente.
- **Worktrees recientes**: hasta 8, los que seleccionaste, creaste o lanzaste más recientemente. Cada uno es un submenú con **Mostrar en ShogunAi**, **Abrir en <runner por defecto>**, **Abrir con** (las demás herramientas que hay en esta máquina) y **Copiar comando de lanzamiento**, que se comportan como en el detalle del worktree con el agente del worktree. Las acciones de abrir se ejecutan sin traer la ventana al frente; si una falla, la ventana pasa al frente y muestra el error.
- **Preferencias** y **Salir**.

La etiqueta de un worktree es `<rama> — <proyecto>`, con el prefijo `●` cuando un agente trabaja en él y `!` cuando te necesita (cambios sin confirmar, commits sin subir, o un agente bloqueado o terminado). Los recientes se recuerdan entre reinicios y los proyectos y worktrees eliminados desaparecen de ellos.

Preferencias tiene dos opciones para esto, que solo aparecen donde el sistema tiene bandeja:

- **Mostrar el icono en la barra de menús** (bandeja del sistema en Windows y Linux), activada por defecto.
- **Seguir ejecutándose al cerrar la ventana**, desactivada por defecto y disponible solo mientras el icono se muestra. Con ella activada, cerrar la ventana la oculta y la app sigue en el icono; **Mostrar ShogunAi** devuelve la ventana y **Salir** (o Cmd/Ctrl+Q) cierra la app. Los ajustes de proyecto sin guardar siguen preguntando antes de cerrar la ventana. Con ella desactivada, cerrar la ventana cierra la app, como antes.

## Tema e idioma

La barra superior tiene un selector de tema (claro, oscuro, o automático para seguir al sistema) y un selector de idioma (English o Español). Las dos elecciones se recuerdan entre reinicios.

## Actualizaciones

Cada vez que arranca, y después una vez al día mientras sigue abierta, la app pregunta a GitHub por las versiones publicadas recientes. Si alguna es más reciente que la que usas, aparece un aviso breve arriba de la ventana («¡Ya está aquí ShogunAi 1.3.0! Tienes la 1.2.0.»). Su icono ⓘ y su botón **Novedades** abren las notas de la versión dentro de la app, en el idioma de la app (en inglés cuando una versión no tiene notas en español). Si vas varias versiones por detrás, ves las notas de cada versión que te has saltado, de la más nueva a la más antigua, cada una bajo su número de versión; **Ver en GitHub** abre la página de la versión. Las versiones antiguas, publicadas antes de que las notas se escribieran a mano, muestran la lista de cambios de GitHub. El aviso no interrumpe tu trabajo y te recuerda cómo actualizar según cómo se instaló ShogunAi:

| Instalada con | El aviso |
|---|---|
| Homebrew | `brew upgrade --cask shogunai`, con un botón **Copiar** |
| Scoop | `scoop update shogunai`, con un botón **Copiar** |
| winget | `winget upgrade LuxionServer.ShogunAi`, con un botón **Copiar** |
| El `.dmg`, `.msi` o `.deb` | Un botón **Descargar** para el nuevo instalador (con el `.deb`, también el comando `sudo apt install ./<archivo>` que ejecutar después) |
| Otra forma | Un enlace a la página de la versión |

La app nunca descarga, instala ni ejecuta nada por sí sola. **Omitir esta versión** oculta el aviso hasta que salga una versión más nueva; cerrarlo lo oculta hasta el siguiente arranque. A veces la versión se publica unos minutos antes de que llegue el paquete de Homebrew, Scoop o winget, así que el comando puede tardar un momento en funcionar.

**Buscar actualizaciones…** en el menú Ayuda comprueba al instante, sin tener en cuenta el horario ni las versiones omitidas, y te dice si ShogunAi está al día, qué versión hay disponible (con el mismo **Novedades**) o por qué falló la comprobación. Las compilaciones de desarrollo no comprueban por su cuenta.

La única petición que la app hace por su cuenta es un `GET` anónimo a `https://api.github.com/repos/LuxionServer/ShogunAi/releases?per_page=30`, que trae las notas con ella y lleva la versión de la app en la cabecera `User-Agent` y nada más. Desactiva **Buscar actualizaciones automáticamente** en Preferencias y la app solo contactará con GitHub cuando elijas **Buscar actualizaciones…**.

## Diagnóstico

La app guarda un registro de diagnóstico local en `~/.shogunai/logs/diagnostics.log` (rota a 1 MB, como mucho tres ficheros). Registra los errores que nada gestionó, los errores mostrados en la app y los bloqueos de la interfaz (cuando deja de responder durante 2 segundos o más, con cuánto duró y qué estaba haciendo). Antes de escribir nada, las rutas absolutas, tu nombre de usuario, los nombres de tus proyectos guardados y las ramas de sus worktrees se sustituyen por marcadores como `<path>` o `<project>`.

Si la ventana de Preferencias o la de Acerca de sufre un error inesperado, solo se cierra esa ventana: el error queda en el registro y la ventana principal muestra un mensaje, de modo que el resto de la app y tu trabajo siguen funcionando.

**Copiar informe de diagnóstico** en el menú Ayuda copia al portapapeles la versión de la app, tu sistema operativo y arquitectura, el runtime de Java y las entradas más recientes del registro, listo para pegar en un reporte de error. Nunca se envía nada a ningún sitio: el registro se queda en tu máquina y el informe solo llega al portapapeles cuando lo pides.
