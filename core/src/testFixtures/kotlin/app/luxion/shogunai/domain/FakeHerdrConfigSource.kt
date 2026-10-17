package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.HerdrConfigSource

class FakeHerdrConfigSource(private val root: String = "/home/dev/.herdr/worktrees") : HerdrConfigSource {

    override fun worktreesRoot(): String = root
}
