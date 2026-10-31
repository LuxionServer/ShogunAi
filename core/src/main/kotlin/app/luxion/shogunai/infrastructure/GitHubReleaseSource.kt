package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.ReleaseSource
import app.luxion.shogunai.domain.model.AppVersion
import app.luxion.shogunai.domain.model.PublishedRelease
import app.luxion.shogunai.domain.model.ReleaseAsset
import app.luxion.shogunai.domain.model.ReleaseNotes
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
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
    private val endpoint: String = "https://api.github.com/repos/LuxionServer/ShogunAi/releases?per_page=30",
    private val timeout: Duration = Duration.ofSeconds(10),
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(timeout).build(),
) : ReleaseSource {

    override suspend fun published(): Result<List<PublishedRelease>> = withContext(Dispatchers.IO) {
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

    private fun parse(body: String): List<PublishedRelease> {
        val releases = runCatching { Json.parseToJsonElement(body).jsonArray }
            .getOrElse { throw WorktreeError.UpdateCheckFailed("The answer isn't a list of releases") }
        return releases.mapNotNull { (it as? JsonObject)?.toPublishedRelease() }
    }

    private fun JsonObject.toPublishedRelease(): PublishedRelease? {
        if (flag("draft") || flag("prerelease")) return null
        val version = text("tag_name")?.let(AppVersion::parse) ?: return null
        val assets = (this["assets"] as? JsonArray).orEmpty().mapNotNull { element ->
            val asset = element as? JsonObject ?: return@mapNotNull null
            ReleaseAsset(asset.text("name") ?: return@mapNotNull null, asset.text("browser_download_url") ?: return@mapNotNull null)
        }
        return PublishedRelease(version, text("html_url") ?: DefaultReleasePage, assets, ReleaseNotes.parse(text("body")))
    }

    private fun JsonObject.text(key: String): String? = runCatching { this[key]?.let { it.jsonPrimitive.contentOrNull } }.getOrNull()

    private fun JsonObject.flag(key: String): Boolean = runCatching { this[key]?.let { it.jsonPrimitive.booleanOrNull } }.getOrNull() == true

    private companion object {
        const val DefaultReleasePage = "https://github.com/LuxionServer/ShogunAi/releases/latest"
    }
}
