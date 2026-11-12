package app.luxion.shogunai.infrastructure

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal object UnknownFields {

    private const val ID = "id"

    fun kept(written: JsonElement, original: JsonElement?, descriptor: SerialDescriptor): JsonElement = when {
        written is JsonObject && original is JsonObject -> keptInObject(written, original, descriptor)
        written is JsonArray && original is JsonArray -> keptInList(written, original, descriptor)
        else -> written
    }

    private fun keptInObject(written: JsonObject, original: JsonObject, descriptor: SerialDescriptor): JsonElement = when (descriptor.kind) {
        StructureKind.CLASS -> keptInClass(written, original, descriptor)
        StructureKind.MAP -> JsonObject(written.mapValues { (key, value) -> kept(value, original[key], descriptor.getElementDescriptor(1)) })
        else -> written
    }

    private fun keptInClass(written: JsonObject, original: JsonObject, descriptor: SerialDescriptor): JsonObject {
        val known = (0 until descriptor.elementsCount).associateBy(descriptor::getElementName)
        val updated = written.mapValues { (key, value) -> kept(value, original[key], descriptor.getElementDescriptor(known.getValue(key))) }
        return JsonObject(updated + original.filterKeys { it !in known })
    }

    private fun keptInList(written: JsonArray, original: JsonArray, descriptor: SerialDescriptor): JsonElement {
        if (descriptor.kind != StructureKind.LIST) return written
        val item = descriptor.getElementDescriptor(0)
        val byId = original.filterIsInstance<JsonObject>().filter { ID in it }.associateBy { it.getValue(ID) }
        return JsonArray(written.map { element -> kept(element, (element as? JsonObject)?.get(ID)?.let(byId::get), item) })
    }
}
