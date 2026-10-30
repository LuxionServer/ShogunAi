# Roadmap

Ideas aceptadas para el futuro de ShogunAi, por orden de prioridad. Esta página marca una dirección, no un compromiso de fechas: cada punto se convierte en un cambio de OpenSpec (`/opsx:propose`) cuando se aborda, y su diseño se decide allí. Cuando un cambio se archiva, su punto se elimina de esta página.

## Dirección

ShogunAi está evolucionando de una app de escritorio que crea worktrees a **la capa de entorno para agentes de IA que trabajan en paralelo en una máquina**: da a cada worktree todo lo que su agente necesita para compilar y ejecutarse sin chocar con los demás y te da una vista de todos ellos, los haya creado quien los haya creado. No incrusta terminales ni ejecuta agentes; Claude, Codex o cualquier otro agente lo sigue haciendo, dentro de los worktrees que ShogunAi prepara y supervisa.

Esa dirección se construye en tres capas:

| Capa | Papel | Qué aporta |
|---|---|---|
| **Motor y CLI** | La base | Provisionado de worktrees definido en el repositorio (`.shogunai.json`) y un comando `shogun` que usan la app de escritorio, los agentes y sus hooks, con un entorno por worktree (puertos reservados y variables) que llega a la shell de cada agente, y salida JSON en todos los comandos para scripts. **Disponible.** |
| **Torre de control** | La vista | La app de escritorio muestra el estado de todos los worktrees, incluidos los creados por otras herramientas, los mantiene limpios y sigue los cambios según ocurren. Abre cada uno en el terminal, el IDE o Herdr con los agentes del proyecto, y queda a un clic en la barra de menús. Los multirepos agrupan los repositorios de un producto, de modo que un cambio abarca varios con un worktree y un agente por repositorio, coordinados por un agente de todo el multirepo, desde el CLI y la app, y el multirepo puede compartir variables entre ellos, como el puerto del backend con el que habla la app de cada grupo, editadas desde el CLI y la app. **Disponible.** |
| **Plataformas** | El contenido | Providers para los stacks en los que más cuesta preparar un worktree, empezando por Android, KMP, iOS y JVM: Gradle, secretos locales, emuladores, simuladores e IDEs. **Empezado**: los repositorios Gradle muestran sus daemons, pueden parar los ociosos y acotar la memoria y la vida de los daemons que arrancan los agentes, los repositorios Android pueden dar a cada worktree su propio emulador, los KMP e iOS su propio simulador de iOS, ambos adquiridos bajo demanda dentro de límites que siguen un perfil de capacidad de la máquina, con `--wait` para hacer cola ante un pool lleno, los repositorios Xcode ven y limpian sus DerivedData, los mantienen dentro de una cuota de disco, automática por defecto, comparten compilaciones entre worktrees y ven cuánto de ellos son paquetes Swift junto a las cachés compartidas de SwiftPM y CocoaPods, los proyectos CocoaPods reciben clonados los pods del repositorio base en cada worktree nuevo cuando su lock coincide, las apps de Compose Desktop se ejecutan ocultas en segundo plano para que los agentes las comprueben en macOS, los agentes se turnan para manejar el escritorio real con `shogun desktop acquire --wait`, que Claude toma por sí solo antes de que actúe su propio computer use o el de Orca, y que solo lo detiene cuando otro worktree tiene el turno, con quién lo tiene y la cola en la torre de control y en `shogun status`, y los backends JVM obtienen compilaciones de Maven acotadas en memoria, ven sus daemons de Maven y pueden parar los ociosos, y obtienen un proyecto de Docker Compose y puertos por worktree, que se arrancan y construyen con `shogun compose up` y se limpian junto con los volúmenes, redes e imágenes que dejan los worktrees eliminados, y los repositorios Flutter reciben `flutter pub get` en cada worktree nuevo, con su build de Gradle en `android/` gestionado por los providers de Gradle y del emulador. La app y `shogun stacks` también detectan el stack de un repositorio y sugieren los providers que encajan, y los `local.properties`, keystores y archivos de Firebase que enlazar en cada worktree; los archivos locales se pueden enlazar, seleccionar con patrones y mantener al día cuando la carpeta base pierde archivos. |

Se conecta con las herramientas que ejecutan agentes en vez de sustituirlas: los hooks de worktree y de sesión de Claude Code, y [Herdr](https://herdr.dev), el multiplexor de terminal para agentes, cuyos worktrees provisiona un plugin de ShogunAi y en el que la app de escritorio abre un worktree con su entorno y su agente y muestra qué está haciendo cada agente. **Disponible.** [Orca](https://www.onorca.dev), una app de escritorio para ejecutar agentes en worktrees, se le está uniendo como segundo runner de primera clase: los worktrees de un repositorio se abren en Orca con su entorno y su agente, los scripts `orca.yaml` de Orca provisionan y liberan sus propios worktrees, y la torre de control muestra los estados que informa Orca, con la herramienta a la que espera un agente, su prompt y su último mensaje, y los puertos en los que Orca ve escuchar a cada worktree; los grupos de un multirepo y su agente del multirepo también se abren en Orca, y «Nuevo cambio» puede abrir sus worktrees en cualquiera de los dos runners; el emulador de Android de un worktree aparece en la vista de emuladores de Orca cuando arranca o cuando el worktree se abre en Orca; el runner por defecto se elige en las preferencias, y `shogun open`, la guía de flujo de trabajo y el contexto de inicio de sesión lo siguen. **Disponible.** Lo que solo ofrece Orca. **Planificado.**

## Cómo se fijan las prioridades

Las herramientas de agentes están aprendiendo a crear worktrees, copiar en ellos los archivos locales y mostrar qué sesión trabaja dónde, así que ShogunAi mantiene esa parte pequeña y llega a ella a través de sus hooks. La prioridad es lo que ninguna puede hacer por las demás: el entorno y los recursos de la máquina de cada stack, repartidos con justicia entre agentes de cualquier proveedor, declarados una vez en el repositorio (`.shogunai.json`), y una sola vista de todos esos agentes. Dentro de eso, va primero lo que encuentran las personas que usan ShogunAi y, por encima de todo, lo que ShogunAi ya hace tiene que funcionar en cualquier máquina: la fiabilidad va antes que las funciones nuevas.

## Planificado

Numerados en el orden en que se abordarán: el número es la prioridad.

### 1. Fiabilidad en cualquier máquina

ShogunAi tiene que funcionar a la primera en una máquina que no es en la que se desarrolla, y cada regresión tiene que detectarse antes de que una versión llegue a nadie. Una demo en otro Mac mostró por qué: la app, abierta desde el Dock, recibe el `PATH` mínimo de macOS, así que dio por fuera del `PATH` un comando `shogun` instalado con Homebrew, el recorrido guiado se negó a empezar y los hooks de Claude Code aparecieron como inutilizables; y crear un worktree falló al copiar sus archivos locales. Ninguno de los dos fallos apareció en la máquina de desarrollo, cuya propia configuración los tapa. Por orden:

- Tests que obligan al producto a cumplirlo:
  - Subir hasta el 100% los suelos de cobertura que exige CI: las ramas de `core` (hoy 79%) y `cli` (83%), y las líneas (88%) y ramas (74%) de la app de escritorio fuera de sus composables, que cubren los tests end to end. Las líneas de `core` y `cli` ya están al 100%, y también los textos de la app de escritorio. La app de escritorio sube un paquete por cambio, en este orden: los ajustes de proyecto, los worktrees y su inspector, el shell y el icono de la barra de menús, las vistas de la máquina y los multirepos, los componentes compartidos y, por último, el cableado de la app, cuando su entorno del host se resuelva en un solo sitio.
  - Que los tests end to end de la app de escritorio fijen si `shogun` está instalado, cuando el entorno del host se resuelva en un solo sitio: hoy los tests del recorrido y de los hooks siguen lo que tenga la máquina, porque en macOS siempre se busca en `/opt/homebrew/bin`, y "falta el comando" solo se cubre con dobles.
- Una prueba de humo de la app empaquetada en una máquina limpia en CI antes de cada versión: instalar el `.dmg` en un runner de macOS, abrir la app con el entorno que le da el Dock, instalar el comando y los hooks, añadir un proyecto con archivos locales, ejecutar `shogun new` y comprobar los archivos copiados. Una versión que no la pasa no se publica. Después, lo mismo en Windows y Linux con sus paquetes, que cubre también instalar el comando desde la app en Windows y en un equipo sin `~/.local/bin` en el `PATH`, y elegir una carpeta con el diálogo del sistema, que en Linux pasa por el portal del escritorio y, sin él, recurre al selector de Java.
- Lo que las herramientas de los providers necesitan además de estar instaladas, en `shogun doctor` y «Comprobar instalación»: el runtime de Java de Gradle y Maven, el daemon de Docker en marcha y versiones mínimas; hoy comprueban que cada herramienta se encuentra y se ejecuta.

### 2. Correcciones de la revisión de UX de la interfaz v2

Una revisión de UX de la interfaz v2 ya implementada, hecha sobre `develop` en `aaf42f94` con datos de ejemplo, concluyó que la app debería responder primero «¿qué me necesita?» y «¿dónde está cada agente?», y que hoy no responde ninguna de las dos de un vistazo. Sus correcciones se reparten en cambios, por orden:

- El modelo de atención (un cambio de OpenSpec):
  - «Te necesitan» se divide en «Esperándote», para un agente bloqueado o terminado, bajo una cabecera de aviso, y «Sin terminar», para trabajo sin commitear o sin subir, bajo una neutra. El checkout principal y los worktrees inactivos salen de los dos grupos, pero conservan su marca ✎.
  - El contador de Activos de la barra lateral cuenta solo «Esperándote», y «Parados» empieza plegado.
  - «↑» significa solo commits no subidos al upstream: la comparación con la base pasa al inspector, y una rama sin upstream muestra un icono de «sin subir».
  - Una vista de Activos vacía y tranquila: «Nada te necesita», con cuántos agentes están trabajando y «Ver todos los worktrees».
- Una fila por cambio de multirepo en Activos, con el peor estado de sus repositorios, que se despliega en una fila hija por repositorio; las filas hijas no repiten la rama.
- Una lista y un inspector más tranquilos:
  - Sin selección no hay inspector, y el resumen pasa a la cabecera de la vista. El inspector se acopla cuando el contenido mide al menos 980 dp de ancho, y por debajo es una capa superpuesta.
  - La columna de ejecución se pliega cuando ninguna fila la usa.
  - El origen solo se muestra cuando no es ShogunAi, y el checkout principal se muestra como su rama con un chip neutro «main».
  - La ruta del worktree es relativa a la carpeta de worktrees, con puntos suspensivos en medio.
  - El resumen de una sección solo se muestra mientras está plegada, y Provisionado solo aparece cuando algo está desfasado o ha fallado.
  - La sección Agente ocupa una línea cuando no hay ningún agente en marcha, y separa el agente en marcha de «Se ejecuta con».
  - En ventanas estrechas el prefijo del tipo se trunca primero, para que el id de la tarea se vea siempre, y las teselas del rail usan dos letras que distinguen proyectos hermanos.
  - Las cifras dentro de frases usan la fuente de la interfaz con cifras tabulares; las cifras sueltas siguen en la fuente de datos.
- La entrada a la máquina y sus alarmas:
  - La barra de estado empieza siempre por «Máquina», y un engranaje al pie de la barra lateral abre los Ajustes.
  - Una herramienta que falta o es opcional se muestra neutra con su motivo; el color de peligro queda para los fallos reales.
  - La vista de daemons de Gradle quita la columna de estado cuando todos los valores son desconocidos, explica por qué «Parar los ociosos» está desactivado y muestra qué worktree usa cada daemon cuando se sabe.
- Arreglos rápidos que no necesitan OpenSpec:
  - La barra de estado, su tooltip y la vista toman el número de daemons de Gradle de una sola fuente; hoy no coinciden.
  - El aviso del recorrido guiado pasa a ser una línea en el pie de la barra lateral.
  - Una insignia «Nuevo» legible.
  - La paleta de comandos toma Ajustes y Preferencias como sinónimos y lista los worktrees para «abrir» cuando no hay nada seleccionado, descarta las coincidencias aproximadas que no son una subsecuencia del nombre visible, y dice en su pie, en la fuente de la interfaz, qué hacen ↵ y ⌘↵, tomado del catálogo de acciones.
  - Comprobar a mano dos problemas, que solo se arreglan si se reproducen: un proyecto añadido con `shogun project add` no aparece hasta reiniciar la app, y ⌘A seguido de borrar en la paleta borra un solo carácter.

### 3. Orca, un segundo runner de primera clase

[Orca](https://www.onorca.dev), la app de escritorio para ejecutar agentes de programación en worktrees, recibe todo lo que ShogunAi hace con Herdr, más lo que Orca ofrece y Herdr no. Un spike comparó los dos: los hooks de repositorio de Orca (`orca.yaml`) pueden provisionar sus worktrees con `shogun`, su CLI puede abrir un worktree de ShogunAi con su entorno y su agente, y informa de estados de agente más ricos que sobreviven a reiniciar la app. Por orden:

- Lo que solo ofrece Orca:
  - El tablero de Orca más allá de lo que ShogunAi ya mantiene al día (el estado, un comentario de resumen y los enlaces a la pull request o la issue de GitHub, mientras la app de escritorio está abierta o al abrir): sincronizar sin la app de escritorio, por ejemplo desde los hooks de Claude Code, estados de tablero personalizados y enlaces de Linear o GitLab.
  - Los worktrees de un grupo mostrados como hijos del espacio del agente del multirepo. Orca 1.4.243 acepta como padre de un worktree existente un espacio Git o de «Abrir como carpeta», pero no un folder workspace.
  - Un worktree con su emulador y su simulador en marcha a la vez mostrado en la vista de emuladores de Orca. Orca 1.4.243 muestra un dispositivo por worktree y apaga el simulador al que sustituye, así que por ahora ShogunAi no muestra ninguno.
- Comprobar:
  - En Windows y en Linux (`orca-ide`), la ubicación de la CLI y la shell que ejecutan las terminales de Orca, que solo cubren pruebas con dobles.
  - Que Orca detecta un agente envuelto por Headroom.
  - Que los avisos de Orca y su app móvil siguen a los agentes que ShogunAi arranca en Orca.
- Fuera a propósito: los worktrees remotos y por SSH de Orca, porque ShogunAi prepara worktrees en esta máquina; y las ejecuciones de orquestación de Orca, porque el agente del multirepo ya coordina un grupo.

### 4. Provisionado y recursos de los stacks móviles

Provisionado y recursos acotados para los stacks en los que un worktree recién creado está más lejos de compilar:

- Decir al usuario de la app de escritorio qué carpetas de un worktree nuevo aún necesitan `pod install`; hoy la app clona los pods y solo `shogun new`, `shogun provision` y el hook de creación informan de lo que falta.
- Los paquetes de los repositorios de paquetes Swift que se compilan con `swift build` (su carpeta `.build` por worktree), que el provider de Xcode no ve.
- Seguimiento de Flutter: comprobar el provider de principio a fin en un proyecto real creado con `flutter create` (un worktree que compila con `flutter build apk --debug` y `flutter build ios --simulator` sin pasos manuales); mostrar el tamaño de la caché de pub (`~/.pub-cache` o `PUB_CACHE`) junto a las demás cachés compartidas; encontrar un SDK de Flutter fuera del `PATH` desde la app de escritorio abierta desde Finder; y los monorrepositorios de Dart con pub workspaces o Melos, que hoy declaran su propio bootstrap como comando post-creación.
- Decidir si la regla del inicio de sesión de ejecutar `shogun emulator start` antes de cada uso del emulador sigue haciendo falta ahora que la concesión del dispositivo se renueva mientras la sesión trabaja (una sesión larga que nunca envía un prompt aún puede perder su emulador), o si los fallos de `adb` deberían remitir a `shogun emulator start`, tras observarlo en uso.
- Esperar un dispositivo desde la app de escritorio y mostrar en sus vistas de máquina los worktrees que esperan cada pool; hoy solo esperan `shogun emulator start --wait` y `shogun simulator start --wait`, y la app hace fallar un arranque mientras otros esperan.

### 5. Todos los agentes, no solo Claude Code

La vista de ShogunAi solo es neutral si ve a todos los agentes:

- Estados de agente para agentes distintos de Claude Code (Codex, Gemini…), que hoy no tienen hooks equivalentes.
- Una notificación cuando un agente (informado por Herdr o por los hooks de Claude Code) pasa a bloqueado o terminado; hoy la señal es la fila del worktree y el menú del icono de la barra de menús.
- Comprobar que Herdr detecta un agente envuelto por Headroom (`headroom wrap claude`): abrir en Herdr espera a que Herdr detecte el agente que escribió, y si Herdr no ve a través del envoltorio la apertura falla aunque el agente esté en marcha.
- Los ficheros de usuario de otros agentes para la guía de flujo, a medida que su documentación los confirme (Cursor guarda hoy las reglas de usuario en sus ajustes), y una sección en Ajustes de la app de escritorio para instalar, actualizar y quitar el bloque de `AGENTS.md`, como ya hace con los hooks de Claude Code.
- Registrar la rama base de los worktrees que crea Herdr, si su evento `worktree.created` llega a incluir el punto de partida; hoy se registra a mano con `shogun base --set`, mientras que `shogun new`, el hook de Claude Code, la app de escritorio y los grupos de los multirepos la registran solos.
- Mostrar la rama base de cada worktree en las filas de la app de escritorio; hoy la columna Git se compara con ella y la nombra en su tooltip y en el detalle, y la informan `shogun base` y `shogun status`.

### 6. Multirepos para todo el equipo

Los multirepos y los grupos por rama están hechos en el CLI y en la app de escritorio, y son también la forma en que quienes no escriben el código siguen un cambio a través de los repositorios. Quedaron fuera:

- Comprobar todos los repositorios de un grupo antes de crear ninguno de sus worktrees: los archivos locales de cada repositorio se comprueban antes de crear su propio worktree, así que un grupo cuyo segundo repositorio tiene un archivo no disponible conserva el worktree ya creado en el primero.
- Llevarse el agente del multirepo fijado en este equipo cuando su carpeta se mueve: `shogun multirepo init` (o "Añadir multirepo") en la carpeta nueva la registra tras revisarla y olvida la entrada antigua, pero el agente local se queda con ella y hay que volver a fijarlo.
- Asociar worktrees cuyas ramas no coinciden, por ejemplo `shogun group add <id> <worktree>`. Necesita un diseño propio: hoy los grupos no tienen estado, y la asociación llegaría a la detección de grupos, a los puertos contra los que se resuelven las variables compartidas, a la limpieza y a todas las vistas de grupo.
- Mostrar en las vistas de la app de escritorio los nombres de las sesiones de Claude de los worktrees de un grupo y del agente del multirepo; hoy los informan `shogun open --herdr` y `shogun status`, y la app solo nombra las sesiones que abre.
- Una única sesión para todo el grupo, con un entorno combinado, como opción junto a un agente por repositorio.
- El estado del pull request de cada repositorio de un grupo, que depende de la integración con GitHub en estudio.

### 7. Robustez y mantenimiento

- Detectar los merges por squash y por rebase, que dejan una rama como no mergeada en su base ni en la rama principal, así que la limpieza no la ofrece y `--delete-branch` la conserva.
- Encontrar la causa raíz de los errores que cerraban la ventana de Preferencias, a partir de las entradas que el aislamiento deja ahora en el registro de diagnóstico, y del crash nativo visto en otra máquina que dejó un informe de diagnóstico vacío.
- Comprobar en un Windows real que borrar y limpiar worktrees con salidas de build de más de 260 caracteres de profundidad funciona ahora (el soporte de rutas largas de Git y el borrado del resto solo están cubiertos con pruebas con dobles).
- Sacar del hilo de la interfaz los puertos por proyecto de `ProjectWorktrees` que aún se ejecutan en el dispatcher de quien los llama (estados de emuladores y simuladores, puertos, liberar recursos), como ya hacen las vistas de la máquina.
- Detectar un plugin de Herdr roto: una instalación enlazada a una carpeta que ya no existe deja de provisionar los worktrees de Herdr sin ningún error, así que `shogun hooks status` y la app deberían comprobar `herdr plugin list` y ofrecer reinstalarlo desde el repositorio.
- Comprobar el estado de los daemons de Maven con un daemon real de mvnd 2.x: se comprobó con mvnd 1.0.6, y 2.x está cubierto por su código fuente y por tests con su argumento de log.
- Watchers del sistema de ficheros en vez de polling para los cambios de metadatos de Git.
- Soporte de repositorios bare con worktrees (el esquema «bare clone»), que cambia lo que significan el repositorio base y copiar desde él. Hoy ShogunAi los rechaza con un error específico.
- Dividir `ProjectWorktrees.kt` y `ProjectSettingsView.kt` en holders y secciones más pequeños.
- Comprobar el recorrido guiado en Windows y Linux con una app instalada real: la creación del ejemplo con el Git del sistema, y el comando del agente del recorrido, cuyo prompt se entrecomilla para la shell que ejecuta cada terminal.
- Una página de changelog en la web de documentación construida con las notas de `.github/release-notes/`, que la app ya muestra en su aviso de actualización.
- Quitar Cmd/Ctrl+E, que se mantiene una versión como alias de Cmd/Ctrl+O desde que el atajo pasó al runner por defecto.
- Mostrar en la vista de Gradle de la app de escritorio la build que ocupa un daemon; hoy `shogun gradle` la nombra, con su cliente, sus tareas y la sesión de agente que la lanzó.
- Escribir en la fuente de la interfaz los textos de estado que quedan en las vistas de Máquina: la vista de Compose aún escribe el título de los restos y un worktree eliminado como dueño en la fuente de datos.

## Más adelante

Aceptados, pero aplazados hasta que alguien los necesite, porque amplían dónde abre ShogunAi un worktree y no el entorno que prepara, y es probable que los cubran las herramientas en las que se ejecutan los agentes:

- La cobertura de los IDEs en los que se puede abrir un worktree, empezando por una exploración para decidir qué falta.
- Más terminales para «Abrir en terminal» (Ghostty, Warp, Alacritty, Kitty, GNOME Terminal), cada uno como un adaptador más.
- «Abrir en Claude Desktop» fuera de macOS (no está verificado que su enlace `claude://code/new?folder=` admita una carpeta en Windows).
- Mostrar los emuladores que arranca ShogunAi dentro de la ventana Running Devices de Android Studio, como los que arranca el propio Android Studio. Necesita un spike: Android Studio podría incrustar un emulador externo arrancado con las mismas opciones que usa él.

## En estudio

### Replantear los Ajustes

Los Ajustes deberían permitir personalizar ShogunAi sin obligar a configurarlo todo: valores por defecto sensatos, pocas opciones de primer nivel y los detalles solo para quien los quiera. Eso incluye revisar la decisión provisional de ligar la cadencia de refresco de la app a los perfiles de capacidad (ver [Cadencia de refresco](guide/providers.es.md#cadencia-de-refresco)), que reutiliza los perfiles pensados para los límites de dispositivos y daemons en lugar de ofrecer una elección propia. La revisión de UX de la interfaz v2 (ver el punto 2 de arriba) deja material para ello:

- Los perfiles de capacidad Automático y Equilibrado apenas se diferencian.
- Los minutos de la reserva de dispositivos y del turno del escritorio son detalles internos de los agentes que ve todo el mundo; podrían pasar a una subsección «Avanzado» plegada.
- Recursos se muestra aunque ningún proyecto use dispositivos; podría mostrarse solo cuando un proyecto declara providers.
- La ventana se llama «Preferencias», mientras que macOS y los proyectos dicen «Ajustes».
- Con el modelo de atención, el umbral de inactividad pesa más, porque decide cuándo el trabajo sin terminar deja de pedir atención.

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
