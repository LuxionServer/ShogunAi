package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.MultirepoSource
import app.luxion.shogunai.domain.model.MultirepoDeclaration

class FakeMultirepoSource(
    declarations: Map<String, MultirepoDeclaration> = emptyMap(),
    known: List<String> = declarations.keys.toList(),
) : MultirepoSource {

    val declarations = declarations.toMutableMap()
    val known = known.toMutableList()
    val unreadable = mutableMapOf<String, Exception>()

    override fun read(folder: String): MultirepoDeclaration? {
        unreadable[folder]?.let { throw it }
        return declarations[folder]
    }

    override fun write(folder: String, declaration: MultirepoDeclaration) {
        declarations[folder] = declaration
    }

    override fun delete(folder: String) {
        declarations.remove(folder)
    }

    override fun knownFolders(): List<String> = known.toList()

    override fun remember(folder: String) {
        if (folder !in known) known += folder
    }

    override fun forget(folder: String) {
        known.remove(folder)
    }
}
