package app.luxion.shogunai.domain.model

enum class Stack { GRADLE, ANDROID, XCODE, MAVEN, DOCKER_COMPOSE, FLUTTER }

data class DetectedStack(val stack: Stack, val evidence: List<String>)

enum class SuggestedProvider(val id: String) {
    GRADLE("gradle"),
    EMULATOR("emulator"),
    IOS_SIMULATOR("ios-simulator"),
    XCODE("xcode"),
    MAVEN("maven"),
    DOCKER_COMPOSE("docker-compose"),
    FLUTTER("flutter"),
}

data class ProviderSuggestion(val provider: SuggestedProvider, val stack: Stack, val evidence: List<String>)

data class SuggestionReport(val suggestions: List<ProviderSuggestion>, val declared: List<ProviderSuggestion>)

private val PROVIDERS_OF_STACK = mapOf(
    Stack.GRADLE to listOf(SuggestedProvider.GRADLE),
    Stack.ANDROID to listOf(SuggestedProvider.EMULATOR),
    Stack.XCODE to listOf(SuggestedProvider.IOS_SIMULATOR, SuggestedProvider.XCODE),
    Stack.MAVEN to listOf(SuggestedProvider.MAVEN),
    Stack.DOCKER_COMPOSE to listOf(SuggestedProvider.DOCKER_COMPOSE),
    Stack.FLUTTER to listOf(SuggestedProvider.FLUTTER),
)

private val MAC_ONLY_PROVIDERS = setOf(SuggestedProvider.IOS_SIMULATOR, SuggestedProvider.XCODE)

fun RepositoryProviders.declares(provider: SuggestedProvider): Boolean = when (provider) {
    SuggestedProvider.GRADLE -> gradle != null
    SuggestedProvider.EMULATOR -> emulator != null
    SuggestedProvider.IOS_SIMULATOR -> iosSimulator != null
    SuggestedProvider.XCODE -> xcode != null
    SuggestedProvider.MAVEN -> maven != null
    SuggestedProvider.DOCKER_COMPOSE -> dockerCompose != null
    SuggestedProvider.FLUTTER -> flutter != null
}

fun suggestProviders(stacks: List<DetectedStack>, declared: RepositoryProviders, isMac: Boolean): SuggestionReport {
    val all = stacks.flatMap { detected ->
        PROVIDERS_OF_STACK.getValue(detected.stack)
            .filter { isMac || it !in MAC_ONLY_PROVIDERS }
            .map { ProviderSuggestion(it, detected.stack, detected.evidence) }
    }
    val (alreadyDeclared, suggestions) = all.partition { declared.declares(it.provider) }
    return SuggestionReport(suggestions, alreadyDeclared)
}
