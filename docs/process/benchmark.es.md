# Benchmark del coste en reposo

La app de escritorio consulta periódicamente los repositorios, agentes, dispositivos y recursos de la máquina que muestra, y cada consulta arranca procesos externos (`git`, `ps`, `lsof`, `herdr`, `orca`, `xcrun simctl`, `adb`…). `scripts/benchmark-idle.sh` mide ese coste, y el tiempo de `shogun status`, para que se note un cambio que los empeore. La cadencia que mide se describe en [Cadencia de refresco](../guide/providers.es.md#cadencia-de-refresco).

Lo vigilan dos tipos de comprobaciones:

- **Tests que cuentan**, parte de `./gradlew test`: ejecutores falsos cuentan los comandos que arranca cada paso. Comprueban que la detección de agentes no ejecuta `lsof` sin ningún proceso de agente y solo pregunta a `lsof` por procesos de agente, que los directorios Git de un worktree se resuelven con `git rev-parse` una vez mientras su entrada `.git` no cambie, que un cambio solo de índice lee los estados sin volver a listar los worktrees, que las comprobaciones siguen la cadencia de cada perfil y estado de la ventana, y que `shogun status` lee los worktrees a la vez y en el orden del listado.
- **El script**, que se ejecuta a mano en una sesión de escritorio cuando un cambio toca las consultas periódicas, porque necesita una ventana real y unos minutos.

## Ejecutar el script

```bash
scripts/benchmark-idle.sh                         # las dos medidas, 10 worktrees, Equilibrado
scripts/benchmark-idle.sh --status-only --runs 10
scripts/benchmark-idle.sh --app-only --profile LIGHT --seconds 120
```

Crea un repositorio temporal con `--worktrees` worktrees enlazados (10 por defecto) y un home aislado (`user.home`) que lo registra y guarda el `--profile` (`LIGHT`, `BALANCED`, `PER_WORKTREE` o `AUTOMATIC`), así que nunca lee tus propios proyectos ni ajustes. Después:

1. Construye el CLI (`:cli:installDist`) y cronometra `shogun status` en ese repositorio `--runs` veces, imprimiendo cada tiempo y la mediana.
2. Arranca la app con `:desktopApp:run`, pasándole el home aislado y una grabación de Java Flight Recorder mediante propiedades de Gradle (`benchmarkHome`, `benchmarkJfr`, `benchmarkDelaySeconds`, `benchmarkSeconds`). La grabación empieza tras `--warmup` segundos (20) y dura `--seconds` (60). Luego imprime los procesos por minuto que arrancó la app y los comandos más frecuentes, a partir de los eventos `jdk.ProcessStart` de la grabación, y cierra la app.

La app arrancada desde Gradle suele abrirse sin el foco, así que la medida por defecto es la de la ventana visible en segundo plano; haz clic en ella durante el calentamiento para medirla con el foco. Necesita un JDK con la herramienta `jfr`: el script la busca en el `PATH`, en las toolchains de Gradle y en `/Library/Java/JavaVirtualMachines`, o la toma de `JFR_BIN`.

El repositorio generado no tiene agentes ni dispositivos propios, así que el script mide el coste fijo de cada comprobación. Los agentes que se ejecutan en cualquier parte de la máquina (por ejemplo, la sesión que lanza el script) son candidatos para `lsof`, y a Orca y a Herdr se les pregunta si están instalados.

## Resultados

En un Mac con 24 GB y 12 núcleos, 10 worktrees, Orca en marcha y unas diez sesiones de Claude Code abiertas en otras partes de la máquina:

| Medida | Antes de `idle-polling-cost` | Después |
|---|---|---|
| `shogun status`, mediana de 5 | 0,79 s | 0,58 s |
| App, Equilibrado, ventana en segundo plano | 100 procesos por minuto (una comprobación cada 3 s) | 24 por minuto (una comprobación cada 15 s) |
| App, Ligero, ventana en segundo plano | 100 por minuto | 6 por minuto (una comprobación cada 60 s) |
| CPU de la detección de agentes por comprobación | 167 ms (`lsof` sobre todos los procesos de la máquina) | 61 ms con procesos de agente en marcha, 29 ms sin ellos (solo `ps`) |

Con la ventana con foco en Equilibrado la app sigue comprobando cada 3 segundos, como antes, pero cada comprobación cuesta en torno a un tercio de la CPU en la detección de agentes, y que cambie el índice de un worktree ya no recarga todo su proyecto.
