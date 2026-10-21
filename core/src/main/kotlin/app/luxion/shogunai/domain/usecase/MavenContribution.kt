package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.MavenProvider

const val MAVEN_OPTS = "MAVEN_OPTS"

fun mavenContribution(settings: MavenProvider): EnvironmentContribution? =
    settings.mavenMaxHeap?.let { heap ->
        EnvironmentContribution(variables = { listOf(MAVEN_OPTS to "-Xmx$heap") }, names = setOf(MAVEN_OPTS))
    }
