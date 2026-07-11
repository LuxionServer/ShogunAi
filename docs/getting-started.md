# Primeros pasos

Guía para clonar el repo, levantar el proyecto por primera vez y saber por dónde seguir.

## Requisitos

- **JDK 21** — Gradle usa toolchains (`gradle/gradle-daemon-jvm.properties`) y lo descarga solo si no lo tienes instalado.
- **Git**.
- (Opcional) IntelliJ IDEA o Android Studio con el plugin de Kotlin Multiplatform, para usar las run configurations del IDE.

## Clonar y compilar

```bash
git clone <url-del-repo>
cd ShogunAi
./gradlew build
```

## Ejecutar la app

```bash
./gradlew :desktopApp:run            # ejecución estándar
./gradlew :desktopApp:hotRun --auto  # hot reload
```

También puedes usar el run widget del IDE.

## Ejecutar los tests

```bash
./gradlew test
```

## Orientarte en el código

El proyecto es Kotlin Multiplatform con un único target real (JVM de escritorio). Todo el dominio, la infraestructura y la UI viven en `desktopApp`:

```
desktopApp/src/main/kotlin/app/luxion/shogunai/
├── domain/           # modelos, puertos (interfaces) y casos de uso
├── infrastructure/   # implementaciones concretas de los puertos (Git, filesystem)
├── ui/               # pantallas Compose, ViewModels y navegación
├── AppContainer.kt   # cablea dominio + infraestructura, sin framework de DI
└── main.kt           # punto de entrada
```

`shared` está declarado pero sin contenido propio (ver [Arquitectura › Visión general](architecture/overview.md) para el porqué).

Para el detalle de cada capa: [Capa de dominio](architecture/domain.md), [Capa de infraestructura](architecture/infrastructure.md), [Capa de UI](architecture/ui.md).

## Cómo se planifican los cambios

Los cambios no triviales se planifican con [OpenSpec](process/openspec.md) antes de escribir código: propuesta → aplicación → archivado. Revisa `openspec/changes/` para ver cambios en curso y `openspec/changes/archive/` para el historial.

## Si vas a trabajar con un agente de IA

Lee `AGENTS.md` en la raíz del repo — recoge las convenciones de estilo de código y de documentación que deben seguir los agentes (Claude, Copilot...) en este proyecto.

## Ver esta documentación en local

```bash
pip install -r requirements-docs.txt
mkdocs serve
```

## Siguiente parada

Ver la tabla "Dónde mirar" en [Inicio](index.md) para saber a qué página ir según lo que necesites entender.
