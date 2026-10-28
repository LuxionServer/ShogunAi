# Proceso de release

Cómo se corta y publica una nueva versión del instalador de escritorio (`.dmg`/`.msi`/`.deb`).

## Flujo

```mermaid
graph LR
    A["Crear rama release/X.X.X"] --> B["Push a release/X.X.X"]
    B --> C["Tests"]
    C --> D["PR release/X.X.X → main"]
    D --> E["Merge"]
    E --> F["Release: tag vX.X.X + build + publish"]
```

1. **Crear la rama** `release/X.X.X` (donde `X.X.X` es la versión a publicar) desde `develop`, escribir la feature principal de la release en `.github/release-title` (ver [Título de la release](#título-de-la-release)) y sus notas en `.github/release-notes/X.X.X.md` y `X.X.X.es.md` (ver [Notas de la release](#notas-de-la-release)).
2. **Validar** — cada push a `release/**` dispara el workflow `Tests` (`.github/workflows/tests.yml`), que corre `./gradlew test`. Sirve para iterar y corregir antes de abrir la PR. El mismo workflow corre también en cada pull request hacia `develop` o una rama `epic/**` y en cada push a ellas, así que las regresiones se detectan antes de llegar a una rama de release.
3. **Abrir la PR** de `release/X.X.X` a `main` y revisarla como cualquier otra.
4. **Mergear** — al mergearse una PR cuya rama de origen matchea `release/*`, el workflow `Release` (`.github/workflows/release.yml`):
      1. valida la versión tomada del nombre de la rama (o del tag): debe ser `X.Y.Z` solo con dígitos; si no, el run falla antes de compilar nada,
      2. ejecuta la suite completa de tests sobre el commit de la release en Linux, macOS y Windows (`.github/workflows/test-suite.yml`, el mismo que usa `Tests`). Nada más corre hasta que pasa,
      3. crea y pushea el tag `vX.X.X` sobre el commit de merge (la versión sale del nombre de la rama, no hay que escribirla dos veces) y crea el GitHub Release en modo **draft** con las notas de la release como cuerpo (ver [Notas de la release](#notas-de-la-release)) y el título `vX.X.X — <feature>` (la feature se lee de `.github/release-title`, ver [Título de la release](#título-de-la-release)); una sola vez, antes de compilar,
      4. compila los instaladores en macOS Apple Silicon, macOS Intel, Windows y Linux (`packageReleaseDmg`/`Msi`/`Deb`) pasando esa versión, con un token de solo lectura. Cada `.dmg` de macOS se renombra `ShogunAi-X.X.X-<arch>.dmg` (`arm64`/`x64`, tomado de la arquitectura del propio runner) justo después del build, y cada instalador se guarda como artefacto del workflow,
      5. en paralelo, compila una vez en Linux la herramienta de línea de comandos independiente (`:cli:distZip`, ver [Línea de comandos](../architecture/cli.md)) y guarda `shogun-X.X.X.zip` como artefacto,
      6. una vez compilados los cuatro instaladores y el zip, un job que solo tiene los permisos para firmar atestaciones (no hace checkout del repositorio ni ejecuta la build) genera una atestación de procedencia de build firmada para cada uno (`actions/attest`). La release no se publica hasta que existen todas,
      7. con todas las atestaciones creadas, el único job con permiso de escritura en el repositorio genera `SHA256SUMS` con el checksum de cada asset, lo sube todo al draft y publica la release (saca el draft),
      8. regenera el Cask de Homebrew a partir de `.github/homebrew/shogunai.rb` con la nueva versión y los checksums de los `.dmg` leídos de `SHA256SUMS`, y lo pushea al tap `LuxionServer/homebrew-shogunai`. El Cask instala la app y enlaza su lanzador como el comando `shogun`,
      9. regenera `bucket/shogunai.json` en el bucket `LuxionServer/scoop-shogunai` a partir de `.github/scoop/shogunai.json` con la nueva versión y el checksum del `.msi` leído de `SHA256SUMS`, y lo pushea,
      10. abre un pull request a `microsoft/winget-pkgs` que actualiza `LuxionServer.ShogunAi` al nuevo `.msi` (acción `winget-releaser`).

Los pasos 8, 9 y 10 se ejecutan en paralelo y son independientes: si uno falla, la release sigue publicada y los demás se ejecutan igualmente.

Todo el flujo desde el paso 4 ocurre en un único run de CI, sin intervención manual. La release se crea en draft y recién se publica al final porque una release ya publicada es inmutable para GitHub (rechaza nuevos assets) y porque escribir las notas una sola vez evita que se dupliquen si cada build del matrix las escribiera por su cuenta.

## Título de la release

Cada release lee su título de `.github/release-title`: la primera línea de ese fichero es la feature principal, y el workflow titula la release `vX.X.X — <feature>`. Por ejemplo, si la 0.9.0 trae soporte multirepo, se publica como `v0.9.0 — Multi-repo support`.

Escríbelo al crear la rama de release, en el mismo commit que sube la versión. Si el fichero está vacío o falta, el workflow vuelve al título simple `vX.X.X` y registra un warning, así que un título olvidado nunca bloquea una release — pero siempre merece una entrada en las notas de la release.

## Notas de la release

Cada release tiene unas notas escritas a mano para quien la usa, en inglés y en español, en dos ficheros con el nombre de la versión: `.github/release-notes/X.X.X.md` y `.github/release-notes/X.X.X.es.md`. Escríbelas en la rama de release, en el mismo commit que `.github/release-title`, y revísalas en la pull request de la release. Que lleven el nombre de la versión evita que un olvido republique las notas de la release anterior, y la carpeta guarda el historial de todas las releases.

Cómo escribirlas:

- Describe qué cambia para quien usa ShogunAi, en lenguaje llano: qué puede hacer ahora, qué funciona mejor, qué se ha corregido.
- Sin números de pull request, autores, nombres de ramas, nombres de clases internas ni nombres de cambios de OpenSpec.
- Breves: un encabezado por grupo (por ejemplo «What's new» y «Fixes»), una viñeta por cambio, la feature en negrita seguida de una o dos frases.
- El fichero en español dice lo mismo que el inglés; no es un resumen.
- Solo encabezados (`#` a `###`), viñetas (un nivel de anidamiento), párrafos, `**negrita**`, `` `código` ``, enlaces y URLs `https://` sueltas: la app pinta eso y muestra lo demás como texto plano.

Por ejemplo:

```markdown
## Novedades

- **Notas de la versión dentro de la app.** Pulsa el icono ⓘ del aviso de actualización para leer lo que trae una versión nueva, en tu idioma.

## Correcciones

- Eliminar un worktree en Windows ya no falla con rutas muy largas.
```

### Formato del cuerpo de la release

El workflow construye el cuerpo del GitHub Release con los dos ficheros: las notas en inglés visibles y las españolas en una sección «Español» plegada. Cada idioma va entre comentarios HTML, que GitHub no muestra:

```markdown
<!-- shogunai:notes:en -->

…contenido de X.X.X.md…

<!-- /shogunai:notes:en -->

<details>
<summary>Español</summary>

<!-- shogunai:notes:es -->

…contenido de X.X.X.es.md…

<!-- /shogunai:notes:es -->

</details>
```

La app de escritorio lee cada idioma entre sus marcadores para mostrar las notas en el aviso de actualización (ver [App de escritorio](../guide/desktop-app.md)), así que no cambies los marcadores sin cambiar la app. Las releases publicadas antes de este formato solo tienen las notas que genera GitHub; la app las muestra sin autores, enlaces a pull requests ni la línea «Full Changelog».

Si falta `X.X.X.md`, el workflow publica la release con las notas generadas por GitHub y registra un warning que nombra los ficheros que faltan, así que unas notas olvidadas nunca bloquean una release. Si solo falta `X.X.X.es.md`, el cuerpo lleva solo la sección en inglés y el workflow registra un warning; la app muestra entonces las notas en inglés a quien la use en español.

## Gestores de paquetes de Windows

Preparación única por parte del mantenedor, antes de la primera release que ejecute los pasos 8 y 9 (hasta entonces esos jobs fallan sin afectar a la release):

1. Crear el repositorio público `LuxionServer/scoop-shogunai` con una carpeta `bucket/` y un README con los comandos de instalación.
2. Crear un token fine-grained con `Contents: read and write` solo sobre ese repositorio y guardarlo como secreto `SCOOP_BUCKET_TOKEN`.
3. Enviar a mano la primera versión a winget con `wingetcreate new https://github.com/LuxionServer/ShogunAi/releases/download/v<versión>/ShogunAi-<versión>.msi`, con el identificador `LuxionServer.ShogunAi`, y esperar a que se fusione (la acción solo puede actualizar un paquete existente).
4. Crear un token clásico con `public_repo` y guardarlo como secreto `WINGET_TOKEN`.

El `.msi` declara un código de actualización fijo (`upgradeUuid` en `desktopApp/build.gradle.kts`), así que un `.msi` más nuevo reemplaza la versión instalada; no lo cambies. jpackage elimina `java.exe` del runtime incluido, así que `desktopApp/build.gradle.kts` lo copia de vuelta desde el JDK de la compilación tras `createRuntimeImage`: el comando `shogun` de Scoop lo ejecuta.

Tras actualizar Compose Desktop o jpackage, comprueba en Windows que la estructura del `.msi` sigue coincidiendo con el manifiesto de Scoop: instala desde el manifiesto renderizado (`scoop install` con una copia local de `.github/scoop/shogunai.json` con la versión y el `sha256` rellenados) y ejecuta `shogun --version`.

## Checksums de la release

Cada release incluye un archivo `SHA256SUMS`. Para verificar una descarga: `shasum -a 256 -c SHA256SUMS --ignore-missing`.

## Procedencia de la build

Cada instalador y el zip del CLI tienen una atestación de procedencia de build firmada que los vincula al run del workflow de release y al commit desde el que se compilaron. Para verificar una descarga: `gh attestation verify ShogunAi-X.X.X-arm64.dmg --repo LuxionServer/ShogunAi`. Las releases publicadas antes del cambio `release-provenance-strict-warnings` no tienen atestación.

## Acciones fijadas

Las acciones ajenas a `actions/*` y `gradle/*` se referencian por SHA de commit, porque reciben secretos. Dependabot (`.github/dependabot.yml`) propone actualizaciones cada semana.

| Acción | Tag | Commit |
|---|---|---|
| `vedantmgoyal9/winget-releaser` | `v2` | `b7370d4d152efdce803a26cc6ca9eee58cb2ad54` |

## Versión del instalador

`compose.desktop.application.nativeDistributions.packageVersion` (en `desktopApp/build.gradle.kts`) lee la propiedad de Gradle `appVersion`:

```kotlin
packageVersion = (findProperty("appVersion") as String?) ?: "0.0.0"
```

- En CI siempre se pasa `-PappVersion=X.X.X` (extraído del tag o del nombre de la rama `release/X.X.X`), así que el instalador publicado siempre coincide con el tag.
- En builds locales (sin esa propiedad) cae al default `0.0.0`, que es también la versión que muestra la app (`BuildInfo.VERSION`, generado por la tarea `generateBuildInfo`, que se vuelve a ejecutar cada vez que cambia `appVersion`).
- La versión del bundle de macOS se deriva de ella con una única regla, `macBundleVersion` en `desktopApp/build.gradle.kts`: mayor + 1 (`0.9.0` → `1.9.0`, `1.4.0` → `2.4.0`). Siempre es válida para macOS, crece con la versión de la release y nunca colisiona. Los `.dmg` se nombran con la versión de la release, nunca con la del bundle.
- jpackage exige versiones estrictamente numéricas (`MAJOR.MINOR.PATCH`), no acepta sufijos como `-dev`. En macOS además el primer número tiene que ser mayor que cero, así que empaquetar en local (`packageDmg`, `createDistributable`) necesita una versión explícita, p. ej. `./gradlew :desktopApp:packageDmg -PappVersion=1.0.0`; `./gradlew :desktopApp:run` funciona con el default.

## Vía manual (escape hatch)

El workflow `Release` también se dispara con un push de tag directo (`git tag vX.X.X && git push origin vX.X.X`), sin pasar por una rama `release/`. Útil para hotfixes puntuales, pero el flujo normal es el de ramas `release/*` descrito arriba.

## Por qué el tag no dispara el workflow por sí solo en el caso del merge

Si el job de merge pusheara el tag usando el `GITHUB_TOKEN` por defecto, GitHub **no** dispararía el `on: push: tags` del mismo workflow (restricción para evitar loops). Por eso el build y la publicación no dependen de ese re-trigger: corren en el mismo run que crea el tag, usando `needs` entre jobs.
