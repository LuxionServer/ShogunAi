package app.luxion.shogunai

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform