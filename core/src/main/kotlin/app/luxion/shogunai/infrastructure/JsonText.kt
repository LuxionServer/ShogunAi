package app.luxion.shogunai.infrastructure

import kotlinx.serialization.json.JsonPrimitive

internal fun JsonPrimitive.stringOrNull(): String? = if (isString) content else null
