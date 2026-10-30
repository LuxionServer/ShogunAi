package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.DesktopTurn
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.ResourceKind
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

object TextSamples {

    private const val MAX_COMBINATIONS = 5_000

    private val holders = PoolHolderState.entries.map { state ->
        PoolHolder(ResourceKind.EMULATOR, "/worktrees/TASK-${state.ordinal}", "/projects/main-repo/.git/worktrees/TASK-${state.ordinal}", state, false, null)
    } + listOf(
        PoolHolder(ResourceKind.EMULATOR, "/worktrees/TASK-90", "/projects/main-repo/.git/worktrees/TASK-90", PoolHolderState.RUNNING, false, null, agentState = AgentState.WORKING),
        PoolHolder(ResourceKind.EMULATOR, "/worktrees/TASK-91", "/projects/main-repo/.git/worktrees/TASK-91", PoolHolderState.RUNNING, false, null, leaseExpiresAt = Instant.EPOCH),
    )

    private val fixed: Map<KClass<*>, List<Any>> = mapOf(
        Int::class to listOf(0, 1, 2),
        Long::class to listOf(0L, 1L, 2L),
        Boolean::class to listOf(true, false),
        Duration::class to listOf(Duration.ofMinutes(30)),
        Throwable::class to listOf(IOException("‹cause›"), IOException()),
        DesktopTurn::class to listOf(DesktopTurn("/projects/main-repo/.git/worktrees/TASK-1", "/worktrees/TASK-1", 0L, 60_000L)),
    )

    fun of(type: KType, label: String = "value"): List<Any?>? {
        val values = nonNullOf(type, label) ?: return null
        return if (type.isMarkedNullable) values + null else values
    }

    fun combinations(types: List<KType>, label: String = "value"): List<List<Any?>> {
        val samples = types.mapIndexed { position, type -> of(type, "$label $position") ?: error("No samples for $type") }
        check(samples.fold(1L) { total, values -> total * values.size } <= MAX_COMBINATIONS) { "Too many combinations for $types" }
        return samples.fold(listOf(emptyList())) { prefixes, values -> prefixes.flatMap { prefix -> values.map { prefix + it } } }
    }

    fun shownValues(arguments: List<Any?>): List<String> =
        arguments.flatMap { if (it is List<*>) it else listOf(it) }.filterIsInstance<String>()

    private fun nonNullOf(type: KType, label: String): List<Any?>? {
        val classifier = type.classifier as KClass<*>
        return when {
            classifier == String::class -> listOf("‹$label›")
            classifier in fixed -> fixed.getValue(classifier)
            classifier == List::class -> listsOf(type.arguments.single().type!!, label)
            classifier.java.isEnum -> classifier.java.enumConstants.toList()
            classifier.isSealed -> classifier.sealedSubclasses.flatMap { instancesOf(it, label) ?: return null }
            classifier.isData -> instancesOf(classifier, label)
            else -> null
        }
    }

    private fun listsOf(element: KType, label: String): List<List<Any?>>? {
        if (element.classifier == PoolHolder::class) return listOf(emptyList(), holders)
        if (element.classifier == String::class) return listOf(emptyList(), listOf("‹$label first›"), listOf("‹$label first›", "‹$label second›"))
        val elements = of(element, label) ?: return null
        return listOf(emptyList<Any?>()) + elements.map { listOf(it) } + listOf(elements)
    }

    private fun instancesOf(type: KClass<*>, label: String): List<Any>? {
        type.objectInstance?.let { return listOf(it) }
        val constructor = type.primaryConstructor ?: return null
        val types = constructor.parameters.map { it.type }
        if (types.any { of(it) == null }) return null
        return combinations(types, label).map { constructor.call(*it.toTypedArray()) }
    }
}
