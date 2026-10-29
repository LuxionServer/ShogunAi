package app.luxion.shogunai.domain.model

sealed interface FlutterPubGet {
    data object Resolved : FlutterPubGet

    data class Needed(val reason: FlutterPubGetFailure) : FlutterPubGet
}

sealed interface FlutterPubGetFailure {
    data class NotFound(val executable: String) : FlutterPubGetFailure

    data class Exited(val exitCode: Int, val errorLines: List<String>) : FlutterPubGetFailure

    data class Unexpected(val message: String) : FlutterPubGetFailure
}
