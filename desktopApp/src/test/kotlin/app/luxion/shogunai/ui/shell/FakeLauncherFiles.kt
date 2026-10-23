package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.domain.io.LauncherFiles

class FakeLauncherFiles : LauncherFiles {
    val texts = mutableMapOf<String, String>()
    val links = mutableMapOf<String, String>()
    val others = mutableSetOf<String>()
    val writable = mutableSetOf<String>()

    override fun exists(path: String) = path in texts || path in others
    override fun isWritableFolder(folder: String) = folder in writable
    override fun writeText(path: String, text: String) {
        texts[path] = text
    }
    override fun readText(path: String) = texts[path]
    override fun linkTarget(link: String) = links[link]
    override fun createLink(target: String, link: String) {
        links[link] = target
    }
    override fun delete(path: String) {
        texts.remove(path)
        links.remove(path)
        others.remove(path)
    }
}
