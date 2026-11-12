# Roadmap

Ideas aceptadas para el futuro de ShogunAi, por orden de prioridad.
Esta página marca una dirección, no un compromiso de fechas: cada punto se convierte en un cambio de OpenSpec (`/opsx:propose`) cuando se aborda, y su diseño se decide allí.
Cuando un cambio se archiva, su punto se elimina de esta página.

## Dirección

ShogunAi está evolucionando de una app de escritorio que crea worktrees a **la capa de entorno para agentes de IA que trabajan en paralelo en una máquina**: da a cada worktree todo lo que su agente necesita para compilar y ejecutarse sin chocar con los demás y te da una vista de todos ellos, los haya creado quien los haya creado.
No incrusta terminales ni ejecuta agentes; Claude, Codex o cualquier otro agente lo sigue haciendo, dentro de los worktrees que ShogunAi prepara y supervisa.

Esa dirección se construye en tres capas, y se conecta con los runners que ejecutan los agentes.
Esta sección solo los resume: el detalle de lo que ya funciona está en las specs y en las notas de versión.

### Motor y CLI

La base.
Estado: **Disponible**.

- Provisionado de worktrees definido en el repositorio (`.shogunai.json`).
- Un comando `shogun` que usan la app de escritorio, los agentes y sus hooks.
- Un entorno por worktree (puertos reservados y variables) que llega a la shell de cada agente.
- Salida JSON en todos los comandos, para scripts.

### Torre de control

La vista.
Estado: **Disponible**.

- La app de escritorio muestra el estado de todos los worktrees, incluidos los creados por otras herramientas.
- Los mantiene limpios y sigue los cambios según ocurren.
- Abre cada uno en el terminal, el IDE o Herdr con los agentes del proyecto.
- Queda a un clic en la barra de menús.
- Los multirepos agrupan los repositorios de un producto, de modo que un cambio abarca varios con un worktree y un agente por repositorio, coordinados por un agente de todo el multirepo, desde el CLI y la app.
- Un multirepo puede compartir variables entre sus repositorios, como el puerto del backend con el que habla la app de cada grupo, editadas desde el CLI y la app.

### Plataformas

El contenido: providers para los stacks en los que más cuesta preparar un worktree, empezando por Android, KMP, iOS y JVM (Gradle, secretos locales, emuladores, simuladores e IDEs).
Estado: **Empezado**.

- **Gradle**: los repositorios muestran sus daemons, pueden parar los ociosos y acotar la memoria y la vida de los daemons que arrancan los agentes.
- **Android**: cada worktree puede tener su propio emulador.
- **KMP e iOS**: cada worktree puede tener su propio simulador de iOS.
- **Límites de dispositivos**: los emuladores y simuladores se adquieren bajo demanda dentro de límites que siguen un perfil de capacidad de la máquina, con `--wait` para hacer cola ante un pool lleno.
- **Xcode**: los repositorios ven y limpian sus DerivedData, los mantienen dentro de una cuota de disco (automática por defecto), comparten compilaciones entre worktrees y ven cuánto de ellos son paquetes Swift junto a las cachés compartidas de SwiftPM y CocoaPods.
- **CocoaPods**: los proyectos reciben clonados los pods del repositorio base en cada worktree nuevo cuando su lock coincide.
- **Compose Desktop**: las apps se ejecutan ocultas en segundo plano para que los agentes las comprueben en macOS.
- **Turno del escritorio**: los agentes se turnan para manejar el escritorio real con `shogun desktop acquire --wait`, que Claude toma por sí solo antes de que actúe su propio computer use o el de Orca, y que solo lo detiene cuando otro worktree tiene el turno.
  Quién lo tiene y la cola se ven en la torre de control y en `shogun status`.
- **Dispositivos físicos**: los móviles y tablets Android, iPhone y iPad conectados a la máquina se registran en un inventario, se prestan a los agentes por defecto y se adquieren de un worktree en uno con `shogun device acquire`, con las mismas concesiones y la misma cola `--wait` que emuladores y simuladores.
  Se ven en `shogun status`, en una vista Dispositivos de la torre de control y en la vista de emulador de Orca (Android).
- **Maven**: los backends JVM obtienen compilaciones de Maven acotadas en memoria, ven sus daemons de Maven y pueden parar los ociosos.
- **Docker Compose**: los backends JVM obtienen un proyecto de Docker Compose y puertos por worktree, que se arrancan y construyen con `shogun compose up` y se limpian junto con los volúmenes, redes e imágenes que dejan los worktrees eliminados.
- **Flutter**: los repositorios reciben `flutter pub get` en cada worktree nuevo, con su build de Gradle en `android/` gestionado por los providers de Gradle y del emulador.
- **Detección del stack**: la app y `shogun stacks` detectan el stack de un repositorio y sugieren los providers que encajan, y los `local.properties`, keystores y archivos de Firebase que enlazar en cada worktree.
- **Archivos locales**: se pueden enlazar, seleccionar con patrones y mantener al día cuando la carpeta base pierde archivos.

### Runners

ShogunAi se conecta con las herramientas que ejecutan agentes en vez de sustituirlas.

#### Claude Code y Herdr

Estado: **Disponible**.

- Los hooks de worktree y de sesión de Claude Code.
- [Herdr](https://herdr.dev), el multiplexor de terminal para agentes, cuyos worktrees provisiona un plugin de ShogunAi.
- La app de escritorio abre un worktree en Herdr con su entorno y su agente, y muestra qué está haciendo cada agente.

#### Orca

[Orca](https://www.onorca.dev), una app de escritorio para ejecutar agentes en worktrees, se les está uniendo como segundo runner de primera clase.
Estado: **Disponible**.
Lo que solo ofrece Orca: **Planificado**.

- Los worktrees de un repositorio se abren en Orca con su entorno y su agente.
- Los scripts `orca.yaml` de Orca provisionan y liberan sus propios worktrees.
- La torre de control muestra los estados que informa Orca, con la herramienta a la que espera un agente, su prompt y su último mensaje, y los puertos en los que Orca ve escuchar a cada worktree.
- Los grupos de un multirepo y su agente del multirepo también se abren en Orca, y «Nuevo cambio» puede abrir sus worktrees en cualquiera de los dos runners.
- El emulador de Android de un worktree aparece en la vista de emuladores de Orca cuando arranca o cuando el worktree se abre en Orca.
- El runner por defecto se elige en las preferencias, y `shogun open`, la guía de flujo de trabajo y el contexto de inicio de sesión lo siguen.

## Cómo se fijan las prioridades

Las herramientas de agentes están aprendiendo a crear worktrees, copiar en ellos los archivos locales y mostrar qué sesión trabaja dónde, así que ShogunAi mantiene esa parte pequeña y llega a ella a través de sus hooks.
La prioridad es lo que ninguna puede hacer por las demás: el entorno y los recursos de la máquina de cada stack, repartidos con justicia entre agentes de cualquier proveedor, declarados una vez en el repositorio (`.shogunai.json`), y una sola vista de todos esos agentes.
Dentro de eso, va primero lo que encuentran las personas que usan ShogunAi y, por encima de todo, lo que ShogunAi ya hace tiene que funcionar en cualquier máquina: la fiabilidad va antes que las funciones nuevas.

## Planificado

En el orden en que se abordarán: el lugar de cada sección en la página es su prioridad, así que la primera es la siguiente.

### Fiabilidad en cualquier máquina

ShogunAi tiene que funcionar a la primera en una máquina que no es en la que se desarrolla, y cada regresión tiene que detectarse antes de que una versión llegue a nadie.
Una demo en otro Mac mostró por qué: la app, abierta desde el Dock, recibe el `PATH` mínimo de macOS, así que dio por fuera del `PATH` un comando `shogun` instalado con Homebrew, el recorrido guiado se negó a empezar y los hooks de Claude Code aparecieron como inutilizables; y crear un worktree falló al copiar sus archivos locales.
Ninguno de los dos fallos apareció en la máquina de desarrollo, cuya propia configuración los tapa.
Por orden:

- Tests que obligan al producto a cumplirlo:
  - Subir hasta el 100% los suelos de cobertura que exige CI: las líneas (hoy 91%) y ramas (78%) de la app de escritorio fuera de sus composables, que cubren los tests end to end.
    Las líneas de `core` y `cli`, las ramas de `cli`, todas las ramas de `core` a las que llega alguna entrada, y los textos y los ajustes de proyecto de la app de escritorio ya están cubiertos.
    La app de escritorio sube un paquete por cambio, en este orden: los worktrees y su inspector, el shell y el icono de la barra de menús, las vistas de la máquina y los multirepos, los componentes compartidos y, por último, el cableado de la app.
- Las comprobaciones de Gradle de `shogun doctor` y «Comprobar instalación» para un build de Gradle en una subcarpeta, como `android/` en un repositorio Flutter: hoy solo leen la raíz del repositorio.

### Orca, un segundo runner de primera clase

[Orca](https://www.onorca.dev), la app de escritorio para ejecutar agentes de programación en worktrees, recibe todo lo que ShogunAi hace con Herdr, más lo que Orca ofrece y Herdr no.
Un spike comparó los dos: los hooks de repositorio de Orca (`orca.yaml`) pueden provisionar sus worktrees con `shogun`, su CLI puede abrir un worktree de ShogunAi con su entorno y su agente, y informa de estados de agente más ricos que sobreviven a reiniciar la app.
Por orden:

- Lo que solo ofrece Orca:
  - El tablero de Orca más allá de lo que ShogunAi ya mantiene al día (el estado, un comentario de resumen y los enlaces a la pull request o la issue de GitHub, mientras la app de escritorio está abierta o al abrir): sincronizar sin la app de escritorio, por ejemplo desde los hooks de Claude Code, estados de tablero personalizados y enlaces de Linear o GitLab.
  - Los worktrees de un grupo mostrados como hijos del espacio del agente del multirepo.
    Orca 1.4.243 acepta como padre de un worktree existente un espacio Git o de «Abrir como carpeta», pero no un folder workspace.
  - Un worktree con su emulador y su simulador en marcha a la vez mostrado en la vista de emuladores de Orca.
    Orca 1.4.243 muestra un dispositivo por worktree y apaga el simulador al que sustituye, así que por ahora ShogunAi no muestra ninguno.
  - Un iPhone o iPad físico mostrado en la vista de emulador de Orca, cuando Orca pueda emitir uno.
- Comprobar:
  - Que Orca emite un dispositivo Android físico conectado por su serial de transporte, por USB y por depuración inalámbrica, y que el emulador del worktree sigue en marcha.
  - En Windows y en Linux (`orca-ide`), la ubicación de la CLI y la shell que ejecutan las terminales de Orca, que solo cubren pruebas con dobles.
  - Que Orca detecta un agente envuelto por Headroom.
  - Que los avisos de Orca y su app móvil siguen a los agentes que ShogunAi arranca en Orca.
- Fuera a propósito: los worktrees remotos y por SSH de Orca, porque ShogunAi prepara worktrees en esta máquina; y las ejecuciones de orquestación de Orca, porque el agente del multirepo ya coordina un grupo.

### Provisionado y recursos de los stacks móviles

Provisionado y recursos acotados para los stacks en los que un worktree recién creado está más lejos de compilar:

- Decir al usuario de la app de escritorio qué carpetas de un worktree nuevo aún necesitan `pod install`; hoy la app clona los pods y solo `shogun new`, `shogun provision` y el hook de creación informan de lo que falta.
- Los paquetes de los repositorios de paquetes Swift que se compilan con `swift build` (su carpeta `.build` por worktree), que el provider de Xcode no ve.
- Seguimiento de Flutter:
  - Comprobar el provider de principio a fin en un proyecto real creado con `flutter create` (un worktree que compila con `flutter build apk --debug` y `flutter build ios --simulator` sin pasos manuales).
  - Mostrar el tamaño de la caché de pub (`~/.pub-cache` o `PUB_CACHE`) junto a las demás cachés compartidas.
  - Encontrar un SDK de Flutter fuera del `PATH` desde la app de escritorio abierta desde Finder.
  - Los monorrepositorios de Dart con pub workspaces o Melos, que hoy declaran su propio bootstrap como comando post-creación.
- Decidir si la regla del inicio de sesión de ejecutar `shogun emulator start` antes de cada uso del emulador sigue haciendo falta ahora que la concesión del dispositivo se renueva mientras la sesión trabaja (una sesión larga que nunca envía un prompt aún puede perder su emulador), o si los fallos de `adb` deberían remitir a `shogun emulator start`, tras observarlo en uso.
- Esperar un dispositivo desde la app de escritorio y mostrar en sus vistas de máquina los worktrees que esperan cada pool; hoy solo esperan `shogun emulator start --wait` y `shogun simulator start --wait`, y la app hace fallar un arranque mientras otros esperan.
- Seguimiento de los dispositivos físicos:
  - Comprobarlos con dispositivos reales más allá de un móvil Android por USB: depuración inalámbrica, un iPhone o iPad por USB y por la red, y enchufar un dispositivo registrado mientras un worktree lo espera.
  - Preferir automáticamente un dispositivo físico o uno virtual, en lugar de que el agente elija entre `shogun device acquire` y `shogun emulator start`.
  - Desinstalar la app del proyecto de un dispositivo al liberarlo.
  - Mostrar los dispositivos físicos de un worktree en su fila de la lista de worktrees.
  - Dar más espacio al nombre del dispositivo en la vista Dispositivos, que lo corta con el ancho de ventana por defecto.

### Todos los agentes, no solo Claude Code

La vista de ShogunAi solo es neutral si ve a todos los agentes:

- Estados de agente para agentes distintos de Claude Code (Codex, Gemini…), que hoy no tienen hooks equivalentes.
- Una notificación cuando un agente (informado por Herdr o por los hooks de Claude Code) pasa a bloqueado o terminado; hoy la señal es la fila del worktree y el menú del icono de la barra de menús.
- Comprobar que Herdr detecta un agente envuelto por Headroom (`headroom wrap claude`): abrir en Herdr espera a que Herdr detecte el agente que escribió, y si Herdr no ve a través del envoltorio la apertura falla aunque el agente esté en marcha.
- Los ficheros de usuario de otros agentes para la guía de flujo, a medida que su documentación los confirme (Cursor guarda hoy las reglas de usuario en sus ajustes), y una sección en Ajustes de la app de escritorio para instalar, actualizar y quitar el bloque de `AGENTS.md`, como ya hace con los hooks de Claude Code.
- Registrar la rama base de los worktrees que crea Herdr, si su evento `worktree.created` llega a incluir el punto de partida; hoy se registra a mano con `shogun base --set`, mientras que `shogun new`, el hook de Claude Code, la app de escritorio y los grupos de los multirepos la registran solos.
- Mostrar la rama base de cada worktree en las filas de la app de escritorio; hoy la columna Git se compara con ella y la nombra en su tooltip y en el detalle, y la informan `shogun base` y `shogun status`.

### Multirepos para todo el equipo

Los multirepos y los grupos por rama están hechos en el CLI y en la app de escritorio, y son también la forma en que quienes no escriben el código siguen un cambio a través de los repositorios.
Quedaron fuera:

- Llevarse el agente del multirepo fijado en este equipo cuando su carpeta se mueve: `shogun multirepo init` (o "Añadir multirepo") en la carpeta nueva la registra tras revisarla, pero el agente local se queda en la entrada antigua y hay que volver a fijarlo.
- Olvidar desde la línea de comandos un multirepo conocido cuya declaración no se puede leer (`trust --revoke` y `delete` necesitan la declaración); hoy solo lo hace "Quitar" en la app de escritorio.
- Asociar worktrees cuyas ramas no coinciden, por ejemplo `shogun group add <id> <worktree>`.
  Necesita un diseño propio: hoy los grupos no tienen estado, y la asociación llegaría a la detección de grupos, a los puertos contra los que se resuelven las variables compartidas, a la limpieza y a todas las vistas de grupo.
- Mostrar en las vistas de la app de escritorio los nombres de las sesiones de Claude de los worktrees de un grupo y del agente del multirepo; hoy los informan `shogun open --herdr` y `shogun status`, y la app solo nombra las sesiones que abre.
- Una única sesión para todo el grupo, con un entorno combinado, como opción junto a un agente por repositorio.
- El estado del pull request de cada repositorio de un grupo, que depende de la integración con GitHub en estudio.

### Robustez y mantenimiento

- Reconocer una rama mergeada por squash tras resolver conflictos, o junto con otras ramas, cuyos cambios ya no coinciden con ningún commit de su destino, por ejemplo preguntando a GitHub si su pull request se mergeó.
  Los merges por squash y rebase cuyos cambios coinciden ya se detectan.
- Encontrar la causa raíz de los errores que cerraban la ventana de Preferencias, a partir de las entradas que el aislamiento deja ahora en el registro de diagnóstico, y del crash nativo visto en otra máquina que dejó un informe de diagnóstico vacío.
- Comprobar en un Windows real que borrar y limpiar worktrees con salidas de build de más de 260 caracteres de profundidad funciona ahora (el soporte de rutas largas de Git y el borrado del resto solo están cubiertos con pruebas con dobles).
- Sacar del hilo de la interfaz los puertos por proyecto de `ProjectWorktrees` que aún se ejecutan en el dispatcher de quien los llama (estados de emuladores y simuladores, puertos, liberar recursos), como ya hacen las vistas de la máquina.
- Detectar un plugin de Herdr roto: una instalación enlazada a una carpeta que ya no existe deja de provisionar los worktrees de Herdr sin ningún error, así que `shogun hooks status` y la app deberían comprobar `herdr plugin list` y ofrecer reinstalarlo desde el repositorio.
- Comprobar el estado de los daemons de Maven con un daemon real de mvnd 2.x: se comprobó con mvnd 1.0.6, y 2.x está cubierto por su código fuente y por tests con su argumento de log.
- Watchers del sistema de ficheros en vez de polling para los cambios de metadatos de Git.
- Soporte de repositorios bare con worktrees (el esquema «bare clone»), que cambia lo que significan el repositorio base y copiar desde él.
  Hoy ShogunAi los rechaza con un error específico.
- Dividir `ProjectWorktrees.kt` y `ProjectSettingsView.kt` en holders y secciones más pequeños.
- Comprobar el recorrido guiado en Windows y Linux con una app instalada real: la creación del ejemplo con el Git del sistema, y el comando del agente del recorrido, cuyo prompt se entrecomilla para la shell que ejecuta cada terminal.
- Un workflow que construya la web de documentación con `mkdocs build --strict` y ejecute los tests del hook del historial de cambios (`python3 -m unittest discover -s scripts/mkdocs`) en cada pull request que toque `docs/`, `mkdocs.yml`, `scripts/mkdocs/` o `.github/release-notes/`; hoy la web solo se construye en local.
- Recuperar la recarga en vivo de `mkdocs serve`: con las versiones recientes de Click, MkDocs 1.6.1 lo arranca sin recarga en vivo, así que solo recarga con `mkdocs serve --livereload`.
  Fijar Click en `requirements-docs.txt`, o documentar el flag, lo arregla.
- Quitar Cmd/Ctrl+E, que se mantiene una versión como alias de Cmd/Ctrl+O desde que el atajo pasó al runner por defecto.
- Mostrar en la vista de Gradle de la app de escritorio la build que ocupa un daemon; hoy `shogun gradle` la nombra, con su cliente, sus tareas y la sesión de agente que la lanzó.
- Escribir en la fuente de la interfaz los textos de estado que quedan en las vistas de Máquina: la vista de Compose aún escribe el título de los restos y un worktree eliminado como dueño en la fuente de datos.

## Más adelante

Aceptados, pero aplazados hasta que alguien los necesite, porque amplían dónde abre ShogunAi un worktree y no el entorno que prepara, y es probable que los cubran las herramientas en las que se ejecutan los agentes:

- La cobertura de los IDEs en los que se puede abrir un worktree, empezando por una exploración para decidir qué falta.
- Más terminales para «Abrir en terminal» (Ghostty, Warp, Alacritty, Kitty, GNOME Terminal), cada uno como un adaptador más.
- «Abrir en Claude Desktop» fuera de macOS (no está verificado que su enlace `claude://code/new?folder=` admita una carpeta en Windows).
- Mostrar los emuladores que arranca ShogunAi dentro de la ventana Running Devices de Android Studio, como los que arranca el propio Android Studio.
  Necesita un spike: Android Studio podría incrustar un emulador externo arrancado con las mismas opciones que usa él.

## En estudio

### Replantear los Ajustes

Los Ajustes deberían permitir personalizar ShogunAi sin obligar a configurarlo todo: valores por defecto sensatos, pocas opciones de primer nivel y los detalles solo para quien los quiera.
Eso incluye revisar la decisión provisional de ligar la cadencia de refresco de la app a los perfiles de capacidad (ver [Cadencia de refresco](guide/providers.es.md#cadencia-de-refresco)), que reutiliza los perfiles pensados para los límites de dispositivos y daemons en lugar de ofrecer una elección propia.
La revisión de UX de la interfaz v2 deja material para ello:

- Los perfiles de capacidad Automático y Equilibrado apenas se diferencian.
- Los minutos de la reserva de dispositivos y del turno del escritorio son detalles internos de los agentes que ve todo el mundo; podrían pasar a una subsección «Avanzado» plegada.
- Recursos se muestra aunque ningún proyecto use dispositivos; podría mostrarse solo cuando un proyecto declara providers.
- La ventana se llama «Preferencias», mientras que macOS y los proyectos dicen «Ajustes».
- Con el modelo de atención, el umbral de inactividad pesa más, porque decide cuándo el trabajo sin terminar deja de pedir atención.

### Apps de escritorio en segundo plano más allá de macOS y del teclado

Las apps de Compose Desktop ya se pueden comprobar en segundo plano en macOS (`shogun headless` y el provider `desktop-app`), pero quedan dos huecos.
La entrada real de teclado no llega a una app cuya ventana no está activa: un puente que publique los eventos en la cola de la propia app, o una herramienta de teclas en el servidor MCP de Compose Hot Reload (una petición a JetBrains), podrían cerrarlo.
Y ocultar y capturar una ventana solo funciona en macOS: Windows y Linux necesitan su propia forma de mantener la app fuera de la vista y capturar su ventana, o una pantalla virtual.
Cada uno necesita un spike antes de convertirse en un cambio.
El turno del escritorio que piden los agentes para computer use (`shogun desktop`) es cooperativo y solo para agentes: esperarlo desde la app de escritorio, y darse cuenta de que la persona está usando el escritorio para que los agentes también la esperen, quedan para cuando el turno esté en uso.

### Un contrato para herramientas de seguimiento externas

Herramientas ajenas a ShogunAi, como una vista de seguimiento para un product owner, podrían leer cómo avanza un cambio en cada repositorio solo desde GitHub si las convenciones que sigue ShogunAi quedaran escritas como contrato: ramas con el id de la tarea (`feature/TASK-123`) en cada repositorio que toca el cambio, cómo deducir la base de cada rama sin estado local (`develop`, o `epic/<nombre>` para un cambio que sale de un epic) y de dónde se lee un progreso aproximado, ya sea de las tareas marcadas del `tasks.md` de OpenSpec del cambio, cada una con el repositorio que toca, o de un trailer en los commits (`Progress: 3/9`) que escriba una skill o la guía de flujo para los agentes que no planifican con OpenSpec.
Hace falta que la primera de esas herramientas diga qué lee antes de convertirlo en un cambio.

### Integración con GitHub y gestores de tareas

Crear un worktree desde un issue de GitHub o una tarea de un gestor (derivando el id y el título de la tarea) y mostrar el estado del pull request de cada rama (abierto, en revisión, CI en rojo, mergeado).
El valor está claro, pero el modelo de integración todavía no: qué proveedores, cómo autenticarse y cuánto depender de CLIs externas como `gh`.
Hay que pensarlo más antes de convertirlo en un cambio.

### Reporte remoto de errores con opt-in

Enviar errores y cuelgues anónimos a un servicio remoto, al estilo de los reporteros de fallos de las apps móviles.
Solo existiría como opt-in explícito sobre el diagnóstico local, y hay que decidir adónde van los reportes y cómo garantizar que no se envían rutas, nombres de proyecto ni de rama, porque el repositorio es público y los usuarios son desarrolladores.

## Fuera de alcance por ahora

### Una vista de seguimiento para quien no ejecuta agentes

Un product owner o un manager que quiere saber qué repositorios de una tarea están terminados (el backend mergeado, Android en revisión, iOS aún en curso) no ejecuta agentes ni tiene clones, así que un modo de solo lectura de la torre de control serviría a otro público con casi nada del dominio de ShogunAi.
Se estudia como un proyecto aparte apoyado en la API de GitHub, y ShogunAi no crece en esa dirección: su parte es el contrato para herramientas de seguimiento externas que está en estudio.

### Ejecutar agentes dentro de la app

ShogunAi no incrusta terminales ni gestiona sesiones de agentes: los agentes y los terminales ya lo hacen bien.
Se centra en los worktrees en los que trabajan.
