# Roadmap

Ideas aceptadas para el futuro de ShogunAi, por orden de prioridad. Esta página marca una dirección, no un compromiso de fechas: cada punto se convierte en un cambio de OpenSpec (`/opsx:propose`) cuando se aborda, y su diseño se decide allí. Cuando un cambio se archiva, su punto se elimina de esta página.

## Dirección

ShogunAi está evolucionando de una app de escritorio que crea worktrees a **la capa de entorno para agentes de IA que trabajan en paralelo en una máquina**: da a cada worktree todo lo que su agente necesita para compilar y ejecutarse sin chocar con los demás y te da una vista de todos ellos, los haya creado quien los haya creado. No incrusta terminales ni ejecuta agentes; Claude, Codex o cualquier otro agente lo sigue haciendo, dentro de los worktrees que ShogunAi prepara y supervisa.

Esa dirección se construye en tres capas:

| Capa | Papel | Qué aporta |
|---|---|---|
| **Motor y CLI** | La base | Provisionado de worktrees definido en el repositorio (`.shogunai.json`) y un comando `shogun` que usan la app de escritorio, los agentes y sus hooks, con un entorno por worktree (puertos reservados y variables) que llega a la shell de cada agente, y salida JSON en todos los comandos para scripts. **Disponible.** |
| **Torre de control** | La vista | La app de escritorio muestra el estado de todos los worktrees, incluidos los creados por otras herramientas, los mantiene limpios y sigue los cambios según ocurren. Abre cada uno en el terminal, el IDE o Herdr con los agentes del proyecto, y queda a un clic en la barra de menús. **Disponible.** |
| **Plataformas** | El contenido | Providers para los stacks en los que más cuesta preparar un worktree, empezando por Android, KMP, iOS y JVM: Gradle, secretos locales, emuladores, simuladores e IDEs. **Empezado**: los repositorios Gradle muestran sus daemons, pueden parar los ociosos y acotar la memoria y la vida de los daemons que arrancan los agentes, los repositorios Android pueden dar a cada worktree su propio emulador, los KMP e iOS su propio simulador de iOS, y los repositorios Xcode ven y limpian sus DerivedData y comparten compilaciones entre worktrees. |

Se conecta con las herramientas que ejecutan agentes en vez de sustituirlas: los hooks de worktree y de sesión de Claude Code, y [Herdr](https://herdr.dev), el multiplexor de terminal para agentes, cuyos worktrees provisiona un plugin de ShogunAi y en el que la app de escritorio abre un worktree con su entorno y su agente y muestra qué está haciendo cada agente. **Disponible.**

## Planificado

Numerados en el orden en que se abordarán: el número es la prioridad.

### 1. Vista de DerivedData que no se cuelga y diagnóstico local

En máquinas con mucho DerivedData, listarlo puede colgar la app: el tamaño de cada carpeta se calcula entero antes de mostrar nada. Los tamaños se calcularán de forma perezosa y cancelable, mostrando primero la lista y un estado «calculando» por carpeta. Junto a eso, los errores y cuelgues se registran en un log local rotado, y la app ofrece una acción «Copiar informe de diagnóstico» (versión, sistema operativo y errores recientes, sin rutas reales ni nombres de proyecto) para adjuntarla a un reporte de fallo. Nada sale de la máquina.

### 2. Emuladores y simuladores bajo demanda, con límites configurables

Un worktree recibe un emulador o simulador, y el puerto que lo acompaña, cuando lo necesita y no cuando se crea, de modo que los worktrees que nunca ejecutan la app no gastan el rango limitado de puertos de emulador. El límite pasa a ser configurable. El modelo que hay detrás es un pool: emuladores, simuladores, daemons de Gradle y DerivedData son recursos de capacidad limitada que un worktree adquiere y libera, y los providers deberían compartir ese modelo en vez de resolverlo cada uno por su cuenta.

### 3. Estados de worktree y diseño de la fila rediseñados

Los grupos actuales no distinguen lo que importa: un worktree con agente se come a la mayoría de los demás, y el grupo de los que necesitan al usuario es el más útil. Los estados separarán los worktrees que te necesitan, los nuevos que nunca se han arrancado, los parados por otro motivo y los que tienen un agente trabajando. La fecha de cada fila dirá qué mide (hoy es el último commit, que en un worktree nuevo se hereda de la rama base y parece actividad), y la información de la fila se ordenará y tendrá tamaños fijos para que sea más fácil de recorrer, sin repetir el nombre del proyecto.

### 4. Plataformas e IDEs más allá de Android

El proyecto creció alrededor de Android. Este punto revisa la cobertura de los demás stacks (iOS y Xcode, backends JVM) y de los IDEs en los que se puede abrir un worktree. Empieza con una exploración para decidir qué falta antes de dividirlo en cambios.

### 5. Pendientes de los cambios del 2026-10-19

La revisión de todo el proyecto del 2026-10-19 y los cambios hechos a partir de ella están archivados. Estos pendientes quedaron fuera de ellos:

- Soporte de repositorios bare con worktrees (el esquema «bare clone»), que cambia lo que significan el repositorio base y copiar desde él. Hoy ShogunAi los rechaza con un error específico.
- Watchers del sistema de ficheros en vez de polling para los cambios de metadatos de Git.
- Dividir `ProjectWorktrees.kt` y `ProjectSettingsView.kt` en holders y secciones más pequeños.
- Atestaciones de procedencia de build para los assets de las releases, y tratar los avisos del compilador como errores ahora que la build está limpia.
- Más terminales para «Abrir en terminal» (Ghostty, Warp, Alacritty, Kitty, GNOME Terminal), cada uno como un adaptador más.
- Una notificación cuando un agente de Herdr pasa a bloqueado o terminado; hoy la señal es la fila del worktree y el menú del icono de la barra de menús.

### 6. Demo guiada de bienvenida

Un recorrido de primer uso basado en la revisión hecha antes de mergear la capa de worktrees: la app crea un repositorio de ejemplo desechable con worktrees en todos los estados y abre un agente con un prompt preparado que guía al usuario por cada funcionalidad (estado, limpieza, provisionado, el CLI, los hooks, el entorno y los providers), paso a paso.

### 7. Provisionado Android, KMP y JVM

Provisionado para los stacks en los que un worktree recién creado está más lejos de compilar:

- `local.properties`, keystores y `google-services.json`.
- Mostrar los emuladores que arranca ShogunAi dentro de la ventana Running Devices de Android Studio, como los que arranca el propio Android Studio, para poder ordenar varios en una sola ventana. Necesita un spike: Android Studio podría incrustar un emulador externo arrancado con las mismas opciones que usa él.

## En estudio

### Integración con GitHub y gestores de tareas

Crear un worktree desde un issue de GitHub o una tarea de un gestor (derivando el id y el título de la tarea) y mostrar el estado del pull request de cada rama (abierto, en revisión, CI en rojo, mergeado). El valor está claro, pero el modelo de integración todavía no: qué proveedores, cómo autenticarse y cuánto depender de CLIs externas como `gh`. Hay que pensarlo más antes de convertirlo en un cambio.

### Reporte remoto de errores con opt-in

Enviar errores y cuelgues anónimos a un servicio remoto, al estilo de los reporteros de fallos de las apps móviles. Solo existiría como opt-in explícito sobre el diagnóstico local del punto 1, y hay que decidir adónde van los reportes y cómo garantizar que no se envían rutas, nombres de proyecto ni de rama, porque el repositorio es público y los usuarios son desarrolladores.

## Fuera de alcance por ahora

### Ejecutar agentes dentro de la app

ShogunAi no incrusta terminales ni gestiona sesiones de agentes: los agentes y los terminales ya lo hacen bien. Se centra en los worktrees en los que trabajan.

### Firma y notarización en macOS

Queda fuera por ahora: requiere el programa de desarrollador de Apple, que es de pago. Mientras tanto, el README explica cómo abrir la app sin notarizar.
