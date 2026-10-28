# Roadmap

Ideas aceptadas para el futuro de ShogunAi, por orden de prioridad. Esta página marca una dirección, no un compromiso de fechas: cada punto se convierte en un cambio de OpenSpec (`/opsx:propose`) cuando se aborda, y su diseño se decide allí. Cuando un cambio se archiva, su punto se elimina de esta página.

## Dirección

ShogunAi está evolucionando de una app de escritorio que crea worktrees a **la capa de entorno para agentes de IA que trabajan en paralelo en una máquina**: da a cada worktree todo lo que su agente necesita para compilar y ejecutarse sin chocar con los demás y te da una vista de todos ellos, los haya creado quien los haya creado. No incrusta terminales ni ejecuta agentes; Claude, Codex o cualquier otro agente lo sigue haciendo, dentro de los worktrees que ShogunAi prepara y supervisa.

Esa dirección se construye en tres capas:

| Capa | Papel | Qué aporta |
|---|---|---|
| **Motor y CLI** | La base | Provisionado de worktrees definido en el repositorio (`.shogunai.json`) y un comando `shogun` que usan la app de escritorio, los agentes y sus hooks, con un entorno por worktree (puertos reservados y variables) que llega a la shell de cada agente, y salida JSON en todos los comandos para scripts. **Disponible.** |
| **Torre de control** | La vista | La app de escritorio muestra el estado de todos los worktrees, incluidos los creados por otras herramientas, los mantiene limpios y sigue los cambios según ocurren. Abre cada uno en el terminal, el IDE o Herdr con los agentes del proyecto, y queda a un clic en la barra de menús. Los multirepos agrupan los repositorios de un producto, de modo que un cambio abarca varios con un worktree y un agente por repositorio, coordinados por un agente de todo el multirepo, desde el CLI y la app, y el multirepo puede compartir variables entre ellos, como el puerto del backend con el que habla la app de cada grupo. **Disponible.** |
| **Plataformas** | El contenido | Providers para los stacks en los que más cuesta preparar un worktree, empezando por Android, KMP, iOS y JVM: Gradle, secretos locales, emuladores, simuladores e IDEs. **Empezado**: los repositorios Gradle muestran sus daemons, pueden parar los ociosos y acotar la memoria y la vida de los daemons que arrancan los agentes, los repositorios Android pueden dar a cada worktree su propio emulador, los KMP e iOS su propio simulador de iOS, ambos adquiridos bajo demanda dentro de límites que siguen un perfil de capacidad de la máquina, con `--wait` para hacer cola ante un pool lleno, los repositorios Xcode ven y limpian sus DerivedData, los mantienen dentro de una cuota de disco, automática por defecto, y comparten compilaciones entre worktrees, las apps de Compose Desktop se ejecutan ocultas en segundo plano para que los agentes las comprueben en macOS, los agentes se turnan para manejar el escritorio real con `shogun desktop acquire --wait`, con quién lo tiene y la cola en la torre de control y en `shogun status`, y los backends JVM obtienen compilaciones de Maven acotadas en memoria y un proyecto de Docker Compose y puertos por worktree, que se arrancan y construyen con `shogun compose up` y se limpian junto con los volúmenes, redes e imágenes que dejan los worktrees eliminados. La app y `shogun stacks` también detectan el stack de un repositorio y sugieren los providers que encajan, y los `local.properties`, keystores y archivos de Firebase que enlazar en cada worktree; los archivos locales se pueden enlazar, seleccionar con patrones y mantener al día cuando la carpeta base pierde archivos. |

Se conecta con las herramientas que ejecutan agentes en vez de sustituirlas: los hooks de worktree y de sesión de Claude Code, y [Herdr](https://herdr.dev), el multiplexor de terminal para agentes, cuyos worktrees provisiona un plugin de ShogunAi y en el que la app de escritorio abre un worktree con su entorno y su agente y muestra qué está haciendo cada agente. **Disponible.**

## Cómo se fijan las prioridades

Las herramientas de agentes están aprendiendo a crear worktrees, copiar en ellos los archivos locales y mostrar qué sesión trabaja dónde, así que ShogunAi mantiene esa parte pequeña y llega a ella a través de sus hooks. La prioridad es lo que ninguna puede hacer por las demás: el entorno y los recursos de la máquina de cada stack, repartidos con justicia entre agentes de cualquier proveedor, declarados una vez en el repositorio (`.shogunai.json`), y una sola vista de todos esos agentes. Dentro de eso, va primero lo que encuentran las personas que usan ShogunAi.

## Planificado

Numerados en el orden en que se abordarán: el número es la prioridad.

### 1. El entorno de backends y servicios

Cada worktree de un backend necesita sus propios servicios en marcha, no solo su propio código. La detección de stacks, los providers de Maven y Docker Compose, los puertos por worktree y el ciclo de vida de Compose (`up`, `build` y la limpieza de los volúmenes, redes e imágenes que dejan los worktrees eliminados) y las variables que un multirepo comparte entre sus repositorios están hechos; queda:

- Parar los daemons de Maven cuando su estado se pueda leer con fiabilidad.

### 2. Provisionado y recursos de los stacks móviles

Provisionado y recursos acotados para los stacks en los que un worktree recién creado está más lejos de compilar:

- Las cachés de paquetes de iOS.
- Averiguar qué necesita un repositorio Flutter en un worktree y cuáles de los providers de Android e iOS puede reutilizar.
- Esperar un dispositivo desde la app de escritorio y mostrar en sus vistas de máquina los worktrees que esperan cada pool; hoy solo esperan `shogun emulator start --wait` y `shogun simulator start --wait`, y la app hace fallar un arranque mientras otros esperan.

### 3. Todos los agentes, no solo Claude Code

La vista de ShogunAi solo es neutral si ve a todos los agentes:

- Estados de agente para agentes distintos de Claude Code (Codex, Gemini…), que hoy no tienen hooks equivalentes.
- Una notificación cuando un agente (informado por Herdr o por los hooks de Claude Code) pasa a bloqueado o terminado; hoy la señal es la fila del worktree y el menú del icono de la barra de menús.
- Comprobar que Herdr detecta un agente envuelto por Headroom (`headroom wrap claude`): abrir en Herdr espera a que Herdr detecte el agente que escribió, y si Herdr no ve a través del envoltorio la apertura falla aunque el agente esté en marcha.
- Los ficheros de usuario de otros agentes para la guía de flujo, a medida que su documentación los confirme (Cursor guarda hoy las reglas de usuario en sus ajustes), y una sección en Ajustes de la app de escritorio para instalar, actualizar y quitar el bloque de `AGENTS.md`, como ya hace con los hooks de Claude Code.
- Registrar la ref de origen de cada worktree, para que la regla de elegir la base de una pull request pudiera responderla `shogun` en lugar de seguir siendo una política del equipo.

### 4. Multirepos para todo el equipo

Los multirepos y los grupos por rama están hechos en el CLI y en la app de escritorio, y son también la forma en que quienes no escriben el código siguen un cambio a través de los repositorios. Quedaron fuera:

- Llevarse el agente del multirepo fijado en este equipo cuando su carpeta se mueve: `shogun multirepo init` (o "Añadir multirepo") en la carpeta nueva la registra tras revisarla y olvida la entrada antigua, pero el agente local se queda con ella y hay que volver a fijarlo.
- Asociar worktrees cuyas ramas no coinciden, por ejemplo `shogun group add <id> <worktree>`.
- Mostrar en las vistas de la app de escritorio los nombres de las sesiones de Claude de los worktrees de un grupo y del agente del multirepo; hoy los informan `shogun open --herdr` y `shogun status`, y la app solo nombra las sesiones que abre.
- Una única sesión para todo el grupo, con un entorno combinado, como opción junto a un agente por repositorio.
- Devolver un repositorio de workspace declarado a la detección (quitando el campo `workspace`), desde el CLI y los ajustes del multirepo; hoy `workspace clear` lo desactiva y para deshacerlo hay que editar el archivo a mano.
- Editar las variables compartidas de un multirepo desde el CLI y los ajustes del multirepo; hoy se editan en `.shogunai-multirepo.json` y se revisan con `shogun multirepo trust`.
- El estado del pull request de cada repositorio de un grupo, que depende de la integración con GitHub en estudio.

### 5. Robustez y mantenimiento

- Encontrar la causa raíz de los errores que cerraban la ventana de Preferencias, a partir de las entradas que el aislamiento deja ahora en el registro de diagnóstico, y del crash nativo visto en otra máquina que dejó un informe de diagnóstico vacío.
- Comprobar la instalación del comando `shogun` desde la app con una app instalada de verdad en Windows (el `shogun.cmd` y el cambio del `PATH` de usuario solo están cubiertos con pruebas con dobles) y en un equipo sin `~/.local/bin` en el `PATH`.
- Watchers del sistema de ficheros en vez de polling para los cambios de metadatos de Git.
- Soporte de repositorios bare con worktrees (el esquema «bare clone»), que cambia lo que significan el repositorio base y copiar desde él. Hoy ShogunAi los rechaza con un error específico.
- Dividir `ProjectWorktrees.kt` y `ProjectSettingsView.kt` en holders y secciones más pequeños.
- Comprobar el recorrido guiado en Windows y Linux con una app instalada real: la creación del ejemplo con el Git del sistema, y el comando del agente del recorrido, cuyo prompt se entrecomilla para la shell que ejecuta cada terminal.

## Más adelante

Aceptados, pero aplazados hasta que alguien los necesite, porque amplían dónde abre ShogunAi un worktree y no el entorno que prepara, y es probable que los cubran las herramientas en las que se ejecutan los agentes:

- La cobertura de los IDEs en los que se puede abrir un worktree, empezando por una exploración para decidir qué falta.
- Más terminales para «Abrir en terminal» (Ghostty, Warp, Alacritty, Kitty, GNOME Terminal), cada uno como un adaptador más.
- «Abrir en Claude Desktop» fuera de macOS (no está verificado que su enlace `claude://code/new?folder=` admita una carpeta en Windows).
- Mostrar los emuladores que arranca ShogunAi dentro de la ventana Running Devices de Android Studio, como los que arranca el propio Android Studio. Necesita un spike: Android Studio podría incrustar un emulador externo arrancado con las mismas opciones que usa él.

## En estudio

### Apps de escritorio en segundo plano más allá de macOS y del teclado

Las apps de Compose Desktop ya se pueden comprobar en segundo plano en macOS (`shogun headless` y el provider `desktop-app`), pero quedan dos huecos. La entrada real de teclado no llega a una app cuya ventana no está activa: un puente que publique los eventos en la cola de la propia app, o una herramienta de teclas en el servidor MCP de Compose Hot Reload (una petición a JetBrains), podrían cerrarlo. Y ocultar y capturar una ventana solo funciona en macOS: Windows y Linux necesitan su propia forma de mantener la app fuera de la vista y capturar su ventana, o una pantalla virtual. Cada uno necesita un spike antes de convertirse en un cambio. El turno del escritorio que piden los agentes para computer use (`shogun desktop`) es cooperativo y solo para agentes: esperarlo desde la app de escritorio, y darse cuenta de que la persona está usando el escritorio para que los agentes también la esperen, quedan para cuando el turno esté en uso.

### Una vista de seguimiento para quien no ejecuta agentes

Quienes no escriben el código, como un product owner o un manager, ya siguen los cambios a través de los grupos de los multirepos: qué ramas existen en cada repositorio, a qué distancia está cada una de su base y si está mergeada. Un modo de la torre de control para ellos mostraría esa vista, sin las acciones ni los estados de agente que solo tienen sentido en el equipo donde se ejecutan los agentes. Hay que decidir qué lee (clones locales, o solo las ramas remotas) y cómo se relaciona con el estado de los pull requests de la integración con GitHub de más abajo.

### Integración con GitHub y gestores de tareas

Crear un worktree desde un issue de GitHub o una tarea de un gestor (derivando el id y el título de la tarea) y mostrar el estado del pull request de cada rama (abierto, en revisión, CI en rojo, mergeado). El valor está claro, pero el modelo de integración todavía no: qué proveedores, cómo autenticarse y cuánto depender de CLIs externas como `gh`. Hay que pensarlo más antes de convertirlo en un cambio.

### Reporte remoto de errores con opt-in

Enviar errores y cuelgues anónimos a un servicio remoto, al estilo de los reporteros de fallos de las apps móviles. Solo existiría como opt-in explícito sobre el diagnóstico local, y hay que decidir adónde van los reportes y cómo garantizar que no se envían rutas, nombres de proyecto ni de rama, porque el repositorio es público y los usuarios son desarrolladores.

## Fuera de alcance por ahora

### Ejecutar agentes dentro de la app

ShogunAi no incrusta terminales ni gestiona sesiones de agentes: los agentes y los terminales ya lo hacen bien. Se centra en los worktrees en los que trabajan.

### Firma y notarización en macOS

Queda fuera por ahora: requiere el programa de desarrollador de Apple, que es de pago. Mientras tanto, el README explica cómo abrir la app sin notarizar.
