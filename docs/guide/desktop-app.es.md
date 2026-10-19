# App de escritorio

La app de escritorio gestiona un catálogo de proyectos y, para cada uno, sus worktrees. Todo lo que hace está disponible también desde el [comando `shogun`](cli.md).

## Proyectos

La primera vez que abres la app verás el catálogo de proyectos vacío. Pulsa "Nuevo proyecto" (Cmd+N en macOS, Ctrl+N en los demás) y rellena:

- **Nombre** y **repositorio base**: el repositorio Git del que salen los worktrees.
- **Carpeta raíz de worktrees**: donde se crean los worktrees nuevos.
- **Rama principal** (opcional): la rama con la que se comparan el estado y si una rama está mergeada; se detecta si se deja vacía.
- **Archivos locales**: archivos que Git no versiona y que cada worktree nuevo necesita en esta máquina (p. ej. `local.properties`). Solo se guardan en esta máquina; para compartirlos con todo el que clone el repositorio, decláralos en [`.shogunai.json`](configuration.md#configuracion-del-repositorio-shogunaijson). "Mover a .shogunai.json" mueve allí los que están dentro del repositorio.
- **Comando del agente** y **Headroom**: el comando que ejecutan "Copiar comando" y "Abrir en Herdr" en un worktree (`claude` por defecto).

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

- Su **rama**, la etiqueta "bloqueado" cuando una sesión de agente lo retiene, su **origen** (ShogunAi, Claude Code, Herdr u otra ubicación) y los **agentes en ejecución** en él.
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
| Te necesitan | sin commit o sin subir | Cambios sin commit, o commits sin subir a su upstream (o a la rama principal si no tiene). |
| En curso | subido, sin fusionar | Limpio, subido, sin fusionar y con algún commit en los últimos 14 días, o una rama que nunca tuvo commits propios ("sin empezar"). |
| Fusionados | ya en la rama principal | Sin commits propios por delante de la rama principal, habiéndolos tenido. Se puede limpiar. Una rama que nunca tuvo commits y no tiene upstream se muestra como "sin empezar" y nunca se ofrece para limpiar. |
| Inactivos | sin commits en 14 días | Limpio, sin agente y sin commits en los últimos 14 días. |
| Carpeta no encontrada | carpeta borrada | Git aún lo lista, pero su carpeta ya no existe. Se puede limpiar. |
| Otros | principal o sin leer | El worktree principal cuando está limpio, o un worktree cuyo estado aún no se ha leído. |

## Trabajar en un worktree

- **Copiar comando** copia `cd "<worktree>" && <comando del agente>` para pegarlo en cualquier terminal.
- **Abrir en Herdr** abre el worktree en [Herdr](../agents.md#herdr) con su entorno y ejecuta allí el comando del agente.

## Eliminar y limpiar

- **Eliminar** elimina un worktree, con la opción de borrar también su rama local. El worktree principal no se puede eliminar.
- **Limpiar**, junto al título "Worktrees", lista los worktrees mergeados o cuya carpeta ya no existe y los elimina de una vez, con la opción de borrar sus ramas. Según los providers del repositorio, ofrece también parar sus daemons de Gradle ociosos y sus emuladores, y borrar sus simuladores de iOS y los DerivedData de Xcode huérfanos.

Eliminar un worktree abierto en Herdr cierra también su espacio de trabajo de Herdr; los diálogos lo avisan antes.

## Secciones de los providers

En repositorios que los declaran en `.shogunai.json`, la pantalla de worktrees añade una sección "Daemons de Gradle" y otra "DerivedData de Xcode". Consulta [Providers](providers.md).

## Tema e idioma

La barra superior tiene un selector de tema (claro, oscuro, o automático para seguir al sistema) y un selector de idioma (English o Español). Las dos elecciones se recuerdan entre reinicios.
