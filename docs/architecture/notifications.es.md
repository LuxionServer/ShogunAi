# Notificaciones de agentes

ShogunAi muestra una notificación del sistema cuando un agente deja de trabajar porque necesita al usuario, o porque ha terminado, y nada más: ni una notificación por evento, ni sonidos propios, ni ventanas flotantes. La fila del worktree y el menú de la barra de menús siguen siendo la señal principal; la notificación es para cuando el usuario mira a otra parte.

## Cuándo se muestra una notificación

Las reglas viven en `core`, en la función pura `AgentNotificationTracker.advance`, a la que llaman tanto la app de escritorio como la línea de comandos con lo que han observado:

- Solo cuenta un cambio de trabajando a bloqueado o terminado. Un agente visto por primera vez, un estado repetido y cualquier otro cambio (de bloqueado a terminado, de inactivo a terminado) nunca notifican.
- El cambio espera 8 segundos y se confirma leyendo de nuevo el agente. Un agente que para entonces vuelve a trabajar, como entre turnos encadenados y subagentes, o que ya no está, se descarta.
- Un agente confirmado calla cuando su panel de Herdr está enfocado, cuando su interruptor en Ajustes está apagado (Bloqueado está encendido por defecto y Terminado apagado), o cuando su runner ya notifica ese cambio por sí mismo.
- Todo lo que se confirma a la vez forma una sola notificación que cuenta los agentes y los enumera, primero los bloqueados; al pulsarla se abre el worktree del primero.

### Runners que ya notifican

Para no duplicar una notificación, ShogunAi lee los ajustes de los propios runners, solo en lectura y guardados un minuto:

- Herdr notifica en el escritorio cuando `[ui.toast] delivery` es `terminal` o `system` en su `config.toml`; ShogunAi deja entonces a Herdr los agentes de sus paneles.
- Orca guarda sus ajustes en su base de datos SQLite privada (`profiles/<perfil>/profile-state.db`). Sus `notifications` deciden si avisa de los agentes que necesitan intervención (`agentNeedsInput`, que sigue a `agentTaskComplete` cuando no está fijado) y de los que terminan (`agentTaskComplete`). Si la base de datos no se puede leer, ShogunAi supone los valores por defecto de Orca y calla para sus agentes.

## Quién observa los agentes

- **App de escritorio.** `WorktreeBoard` alimenta a `AgentNotificationWatch` tras cada detección de agentes, y el vigilante le pide una detección más cuando vence el margen de un cambio. Con la ventana oculta y algún interruptor encendido, el tablero sigue detectando agentes al intervalo sin foco de la cadencia de refresco, sin leer Git ni dispositivos.
- **Línea de comandos, con la app cerrada.** `shogun hook agent-state` ve el cambio de Claude entre el registro anterior de la sesión y el nuevo. Añade la sesión a `~/.shogunai/notifications/pending.json` y arranca `shogun hook notify-pending` en segundo plano con la misma JVM, para no retrasar a Claude Code. Ese proceso espera el margen, reconstruye el estado de cada sesión con el almacén de sesiones y Herdr, y notifica una vez todo lo que vence. Si entretanto se ha abierto la app, descarta lo pendiente.

## Cómo lo muestra cada sistema

`SystemNotifier` es el puerto, y `notificationPorts` elige el adaptador del equipo:

| Sistema | Mecanismo | Clic |
|---|---|---|
| macOS | `ShogunAi Notifications.app`, un pequeño helper en Swift incluido en los recursos de la app, publica con `UNUserNotificationCenter` bajo el nombre de ShogunAi. | macOS vuelve a abrir el helper, que escribe una petición `open-worktree` en `~/.shogunai/app-requests` y abre ShogunAi. |
| Linux | `notify-send --action … --wait`. Sin `notify-send`, no se muestra nada. | La app lee la acción en la salida de `notify-send`. La línea de comandos publica sin acción. |
| Windows | Un icono del área de notificación (`java.awt.TrayIcon`). | El listener de acción del icono. La línea de comandos no notifica en Windows. |

Cada notificación lleva una insignia dibujada por ShogunAi, `RenderedNotificationImages`: ámbar con «!» cuando el agente necesita al usuario, verde con una marca de verificación cuando ha terminado, con la inicial del agente o, si son varios, cuántos son. Se dibuja una vez en `~/.shogunai/notifications/images` y se adjunta a la notificación de macOS (macOS mantiene el icono de la app a la izquierda y muestra la insignia como miniatura) o se pasa como icono a `notify-send`. No se usan los logos de los fabricantes. Si no se puede dibujar, la notificación sale sin ella.

La primera notificación en macOS pide permiso; la elección queda en Ajustes del Sistema, en Notificaciones, como «ShogunAi». En macOS no se usan las notificaciones de bandeja de la JVM: pasan por el obsoleto `NSUserNotificationCenter`, no se muestran en las versiones actuales de macOS y no informan de los clics.

El helper lo compila la tarea de Gradle `buildNotifier` con `swiftc` en macOS (en los demás sistemas se omite), lo firma con la identidad Developer ID de la app cuando se indica y ad hoc en caso contrario, y lo coloca en los recursos de la app de Compose, de modo que `./gradlew :desktopApp:run` también lo usa.

## Abrir el worktree

Un clic pasa por la misma petición que usa la línea de comandos: `AppRequest` con la acción `open-worktree`, un proyecto y la ruta de un worktree. `shogun app <ruta>` también la envía cuando la ruta está dentro de un worktree enlazado. La app muestra su ventana y revela el worktree en «Todos los worktrees», o muestra el proyecto si el worktree ya no existe. Las versiones anteriores de la app ignoran la acción.
