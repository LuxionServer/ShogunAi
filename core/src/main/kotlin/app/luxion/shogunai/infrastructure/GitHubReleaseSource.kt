package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.ReleaseSource
import app.luxion.shogunai.domain.model.AppVersion
import app.luxion.shogunai.domain.model.LatestRelease
import app.luxion.shogunai.domain.model.ReleaseAsset
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class GitHubReleaseSource(
    private val appVersion: String,
    private val endpoint: String = "https://api.github.com/repos/LuxionServer/ShogunAi/releases/latest",
    private val timeout: Duration = Duration.ofSeconds(10),
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(timeout).build(),
) : ReleaseSource {

    override suspend fun latest(): Result<LatestRelease> = withContext(Dispatchers.IO) {
        runCatching {
            val request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(timeout)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ShogunAi/$appVersion")
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) throw WorktreeError.UpdateCheckFailed("GitHub answered ${response.statusCode()}")
            parse(response.body())
        }.recoverCatching { error ->
            throw if (error is WorktreeError) error else WorktreeError.UpdateCheckFailed(error.message ?: error::class.simpleName)
        }
    }

    private fun parse(body: String): LatestRelease {
        val release = runCatching { Json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw WorktreeError.UpdateCheckFailed("The answer isn't a release") }
        val tag = release.text("tag_name")
        val version = tag?.let(AppVersion::parse)
            ?: throw WorktreeError.UpdateCheckFailed("Unrecognised version: ${tag.orEmpty()}")
        val assets = (release["assets"] as? JsonArray).orEmpty().mapNotNull { element ->
            val asset = element as? JsonObject ?: return@mapNotNull null
            ReleaseAsset(asset.text("name") ?: return@mapNotNull null, asset.text("browser_download_url") ?: return@mapNotNull null)
        }
        return LatestRelease(version, release.text("html_url") ?: DefaultReleasePage, assets)
    }

    private fun JsonObject.text(key: String): String? = runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

    private companion object {
        const val DefaultReleasePage = "https://github.com/LuxionServer/ShogunAi/releases/latest"
    }
}
