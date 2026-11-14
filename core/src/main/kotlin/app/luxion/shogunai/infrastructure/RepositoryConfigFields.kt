package app.luxion.shogunai.infrastructure

internal const val COPY_FIELD = "copy"
internal const val LINK_FIELD = "link"
internal const val HOOKS_FIELD = "hooks"
internal const val POST_CREATE_FIELD = "postCreate"
internal const val PROVIDERS_FIELD = "providers"
internal const val GRADLE_PROVIDER = "gradle"
internal const val DAEMON_MAX_HEAP_FIELD = "daemonMaxHeap"
internal const val IDLE_TIMEOUT_MINUTES_FIELD = "idleTimeoutMinutes"
internal const val KOTLIN_DAEMON_MAX_HEAP_FIELD = "kotlinDaemonMaxHeap"
internal const val EMULATOR_PROVIDER = "emulator"
internal const val AVD_FIELD = "avd"
internal const val IOS_SIMULATOR_PROVIDER = "ios-simulator"
internal const val DEVICE_FIELD = "device"
internal const val RUNTIME_FIELD = "runtime"
internal const val XCODE_PROVIDER = "xcode"
internal const val COMPILATION_CACHE_FIELD = "compilationCache"
internal const val COCOA_PODS_FIELD = "cocoaPods"
internal const val MAVEN_PROVIDER = "maven"
internal const val MAVEN_MAX_HEAP_FIELD = "mavenMaxHeap"
internal const val DOCKER_COMPOSE_PROVIDER = "docker-compose"
internal const val PORT_VARIABLES_FIELD = "portVariables"
internal const val DESKTOP_APP_PROVIDER = "desktop-app"
internal const val MODULE_FIELD = "module"
internal const val ISOLATED_HOME_FIELD = "isolatedHome"
internal const val FLUTTER_PROVIDER = "flutter"
internal const val NODE_PROVIDER = "node"
internal const val ENV_FIELD = "env"
internal const val ENV_FILE_FIELD = "envFile"

internal val PROVIDER_OPTION_FIELDS = mapOf(
    GRADLE_PROVIDER to listOf(DAEMON_MAX_HEAP_FIELD, IDLE_TIMEOUT_MINUTES_FIELD, KOTLIN_DAEMON_MAX_HEAP_FIELD),
    EMULATOR_PROVIDER to listOf(AVD_FIELD),
    IOS_SIMULATOR_PROVIDER to listOf(DEVICE_FIELD, RUNTIME_FIELD),
    XCODE_PROVIDER to listOf(COMPILATION_CACHE_FIELD, COCOA_PODS_FIELD),
    MAVEN_PROVIDER to listOf(MAVEN_MAX_HEAP_FIELD),
    DOCKER_COMPOSE_PROVIDER to listOf(PORT_VARIABLES_FIELD),
    FLUTTER_PROVIDER to emptyList(),
    NODE_PROVIDER to emptyList(),
)
