package com.yourname.picross.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

// ---------------------------------------------------------------------------
// Wire model — mirrors what picross-gen emits.
// ---------------------------------------------------------------------------

@Serializable
data class PackMeta(
    val id: String,
    val name: String,
    val version: Int,
    val count: Int,
    val url: String,
)

@Serializable
data class PuzzleDto(
    val id: String,
    val w: Int,
    val h: Int,
    val bits: String,
    val difficulty: Int,
    val passes: Int = 0,
    val source: String? = null,
)

@Serializable
data class PackDto(
    val id: String,
    val name: String,
    val version: Int,
    val license: String,
    val puzzles: List<PuzzleDto>,
)

/**
 * Local pack storage, backed by plain files in the tool's private `filesDir`.
 *
 * Room is on the Light SDK allow-list and is the right call if you later want
 * to query across packs, but for this shape of data it is overkill: a pack of
 * 150 puzzles is ~15 KB of JSON, and the only access patterns are "list packs"
 * and "load one pack". Files keep the dependency count — and the review
 * surface — smaller.
 *
 * Player progress is the part that genuinely wants a database or DataStore,
 * since it is written constantly; keep it separate from the read-only packs.
 */
class PackStore(private val filesDir: File) {

    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File(filesDir, "packs").apply { mkdirs() }

    fun installed(): List<PackDto> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString<PackDto>(it.readText()) }.getOrNull() }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun versionOf(packId: String): Int? =
        runCatching {
            json.decodeFromString<PackDto>(File(dir, "$packId.json").readText()).version
        }.getOrNull()

    fun save(pack: PackDto) {
        // Write to a temp file then rename: a sync interrupted mid-write must
        // not leave a truncated pack that fails to parse on next launch.
        val tmp = File(dir, "${pack.id}.json.tmp")
        tmp.writeText(json.encodeToString(PackDto.serializer(), pack))
        tmp.renameTo(File(dir, "${pack.id}.json"))
    }

    fun load(packId: String): PackDto? =
        runCatching {
            json.decodeFromString<PackDto>(File(dir, "$packId.json").readText())
        }.getOrNull()

    /** Seed from the APK's bundled pack on first launch so the app works offline. */
    fun installBundledIfEmpty(bundled: () -> String) {
        if (dir.listFiles()?.isNotEmpty() == true) return
        runCatching { save(json.decodeFromString<PackDto>(bundled())) }
    }
}

/**
 * Fetches only packs whose remote version differs from the local one.
 *
 * Call this from a [LightWork] job rather than on screen entry — the SDK runs
 * background jobs when the device is idle or on wifi, which is exactly the
 * right policy for content that is never urgent.
 */
class PackSync(
    private val client: HttpClient,
    private val store: PackStore,
    private val baseUrl: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sync(): SyncOutcome {
        val indexText = client.get("$baseUrl/index.json").bodyAsText()
        val remote = json.decodeFromString<List<PackMeta>>(indexText)

        var updated = 0
        var failed = 0
        for (meta in remote) {
            if (store.versionOf(meta.id) == meta.version) continue
            val ok = runCatching {
                val body = client.get(meta.url).bodyAsText()
                store.save(json.decodeFromString<PackDto>(body))
            }.isSuccess
            if (ok) updated++ else failed++
        }
        return SyncOutcome(updated, failed, remote.size)
    }

    data class SyncOutcome(val updated: Int, val failed: Int, val available: Int)
}

/*
Wire it up in your tool module:

    // build.gradle.kts  (all of these are on the Light SDK allow-list)
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-okhttp:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    @LightJob("sync-packs")
    val syncPacks: LightJobHandler = { ctx, _ ->
        val sync = PackSync(HttpClient(), PackStore(ctx.filesDir), BASE_URL)
        runCatching { sync.sync() }.fold(
            onSuccess = { LightJobResult.Success(mapOf("updated" to it.updated.toString())) },
            onFailure = { LightJobResult.Retry },   // flaky network: back off and retry
        )
    }

    // once, from your @EntryPoint
    LightWork.enqueuePeriodic(lightContext, "sync-packs", repeatInterval = 24.hours)
*/
