package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.MultirepoDeclaration

interface MultirepoSource {
    fun read(folder: String): MultirepoDeclaration?
    fun write(folder: String, declaration: MultirepoDeclaration)
    fun delete(folder: String)
    fun knownFolders(): List<String>
    fun remember(folder: String)
    fun forget(folder: String)
}
