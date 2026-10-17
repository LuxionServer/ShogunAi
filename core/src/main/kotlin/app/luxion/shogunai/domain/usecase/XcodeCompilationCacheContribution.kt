package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.FileManager

const val XCODE_COMPILATION_CACHE_KEY = "xcode-compilation-cache"
const val XCODE_XCCONFIG_FILE = "XCODE_XCCONFIG_FILE"

val XCODE_COMPILATION_CACHE_CONTENT = """
    COMPILATION_CACHE_ENABLE_CACHING = YES
    SWIFT_ENABLE_PREFIX_MAPPING = YES
    SWIFT_ENABLE_PROJECT_PREFIX_MAPPING = YES
    CLANG_ENABLE_PREFIX_MAPPING = YES
    CLANG_ENABLE_PROJECT_PREFIX_MAPPING = YES
""".trimIndent() + "\n"

fun xcodeCompilationCachePath(home: String): String =
    "${home.trimEnd('/', '\\')}/.shogunai/xcode/compilation-cache.xcconfig"

fun xcodeCompilationCacheContribution(fileManager: FileManager, home: String) = EnvironmentContribution(
    resources = listOf(
        ResourceRequest(
            key = XCODE_COMPILATION_CACHE_KEY,
            exists = { path -> fileManager.exists(path) && fileManager.readText(path) == XCODE_COMPILATION_CACHE_CONTENT },
            create = {
                val path = xcodeCompilationCachePath(home)
                fileManager.writeText(path, XCODE_COMPILATION_CACHE_CONTENT)
                path
            },
            discard = {},
        ),
    ),
    variables = { reservations ->
        listOf(XCODE_XCCONFIG_FILE to reservations.resources.getValue(XCODE_COMPILATION_CACHE_KEY))
    },
)
