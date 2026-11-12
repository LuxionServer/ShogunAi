package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

internal class JsonSchema(
    val migrations: List<(JsonElement) -> JsonElement> = emptyList(),
    val normalize: (JsonElement) -> JsonElement = { it },
) {
    val version: Int get() = migrations.size + 1
}

internal class VersionedJsonFile<T>(
    private val path: Path,
    private val serializer: KSerializer<T>,
    private val json: Json,
    private val empty: T,
    private val schema: JsonSchema = JsonSchema(),
    private val privateFile: Boolean = false,
    private val decode: (JsonElement) -> T = { json.decodeFromJsonElement(serializer, it) },
) {

    fun read(): T = LocalFileLock.withLock(path) {
        val stored = stored()
        if (stored == null) empty else decoded(stored, upgraded(stored))
    }

    fun update(transform: (T) -> T): WorktreeError.LocalFileUnreadable? = LocalFileLock.withLock(path) { updateLocked(transform) }

    private fun updateLocked(transform: (T) -> T): WorktreeError.LocalFileUnreadable? {
        val stored = try {
            stored()
        } catch (problem: WorktreeError.LocalFileUnreadable) {
            return replaceUnreadable(problem, transform)
        } ?: return applied(empty, null, transform)
        if (stored.version > schema.version) {
            throw WorktreeError.LocalFileFromNewerVersion(path.toString(), stored.version, schema.version)
        }
        val original = upgraded(stored)
        val current = try {
            decoded(stored, original)
        } catch (problem: WorktreeError.LocalFileUnreadable) {
            return replaceUnreadable(problem, transform)
        }
        return applied(current, original, transform)
    }

    private fun applied(current: T, original: JsonElement?, transform: (T) -> T): WorktreeError.LocalFileUnreadable? {
        val updated = transform(current)
        if (updated != current) write(updated, original)
        return null
    }

    private fun replaceUnreadable(
        problem: WorktreeError.LocalFileUnreadable,
        transform: (T) -> T,
    ): WorktreeError.LocalFileUnreadable {
        keepUnreadableCopy(path)
        write(transform(empty), null)
        return problem
    }

    private fun stored(): Stored? {
        if (!path.exists()) return null
        val text = try {
            path.readText()
        } catch (error: IOException) {
            throw unreadable(error.message)
        }
        if (text.isBlank()) return null
        val element = try {
            json.parseToJsonElement(text)
        } catch (error: IllegalArgumentException) {
            throw unreadable(firstLineOf(error))
        }
        if (element !is JsonObject || VERSION_KEY !in element) return Stored(element, FIRST_VERSION)
        return Stored(JsonObject(element - VERSION_KEY), versionOf(element.getValue(VERSION_KEY)))
    }

    private fun versionOf(field: JsonElement): Int {
        val version = (field as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        if (version == null || version < FIRST_VERSION) throw unreadable("$VERSION_KEY must be a positive whole number, not $field")
        return version
    }

    private fun upgraded(stored: Stored): JsonElement =
        schema.normalize(schema.migrations.drop(stored.version - FIRST_VERSION).fold(stored.element) { element, migration -> migration(element) })

    private fun decoded(stored: Stored, element: JsonElement): T = try {
        decode(element)
    } catch (error: IllegalArgumentException) {
        val newer = if (stored.version > schema.version) newerVersionNote(stored.version) else ""
        throw unreadable(newer + firstLineOf(error))
    }

    private fun newerVersionNote(version: Int): String =
        "written by a newer version of ShogunAi (schema $version; this version supports up to ${schema.version}): "

    private fun write(value: T, original: JsonElement?) {
        val written = UnknownFields.kept(json.encodeToJsonElement(serializer, value), original, serializer.descriptor)
        val versioned = if (written is JsonObject) JsonObject(mapOf(VERSION_KEY to JsonPrimitive(schema.version)) + written) else written
        writeAtomically(path, json.encodeToString(JsonElement.serializer(), versioned), privateFile)
    }

    private fun unreadable(reason: String?) = WorktreeError.LocalFileUnreadable(path.toString(), reason)

    private class Stored(val element: JsonElement, val version: Int)

    private companion object {
        const val VERSION_KEY = "schemaVersion"
        const val FIRST_VERSION = 1
    }
}
