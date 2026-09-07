package com.anikage.app.core.download

import android.content.Context
import com.anikage.app.Config
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.db.AnikageDatabase
import com.anikage.app.core.data.db.DownloadedEpisodeEntity
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import com.anikage.app.core.settings.SettingsState
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * IN-APP EPISODE DOWNLOAD ENGINE.
 *
 * The site's Download dialog links out to provider pages; this engine instead
 * downloads the SAME HLS stream the player plays (the token -> proxy URL),
 * segment by segment, into app-scoped storage:
 *
 *   1. resolve sources (slug/provider/lang/episode) -> best m3u8 token URL
 *   2. fetch the MASTER playlist -> parse renditions -> pick requested height
 *   3. fetch the VARIANT playlist -> segment list (must be VOD: ENDLIST)
 *   4. download every segment (Origin/Referer headers, same as playback) and
 *      append it to one output file — concatenated MPEG-TS, which ExoPlayer
 *      plays natively
 *   5. save the default softsub VTT beside it
 *   6. persist a [DownloadedEpisodeEntity] row — the Downloads screen and
 *      offline playback read from there
 *
 * Pause = cancel the running job after the current segment (completed
 * segments stay on disk + a sidecar index records the count, so resume —
 * even after process death — continues exactly where it stopped).
 * Cancel deletes partials; Retry re-resolves a FRESH token.
 */
object EpisodeDownloadEngine {

    enum class Status { QUEUED, RESOLVING, DOWNLOADING, PAUSED, COMPLETED, FAILED }

    data class DownloadRequest(
        val animeId: Int,
        val slug: String,
        val episode: Int,
        val provider: String,
        val lang: String,
        /** Preferred rendition height; 0 = follow the stream-quality setting. */
        val height: Int = 0,
        val titleRomaji: String? = null,
        val titleEnglish: String? = null,
        val episodeTitle: String? = null,
        val posterUrl: String? = null,
    )

    data class DownloadState(
        val key: String,
        val animeId: Int,
        val episode: Int,
        val slug: String? = null,
        val titleRomaji: String? = null,
        val titleEnglish: String? = null,
        val episodeTitle: String? = null,
        val posterUrl: String? = null,
        val qualityLabel: String = "",
        val height: Int = 0,
        val status: Status = Status.QUEUED,
        val progress: Float = 0f,
        val segmentsDone: Int = 0,
        val segmentsTotal: Int = 0,
        val bytesDone: Long = 0L,
        val bytesTotal: Long = 0L,
        val bytesPerSec: Long = 0L,
        val error: String? = null,
        val filePath: String? = null,
        val subtitlePath: String? = null,
    )

    @Serializable
    private data class ResumeInfo(
        val segmentsDone: Int = 0,
        val bytesDone: Long = 0L,
        val segmentsTotal: Int = 0,
        val height: Int = 0,
        val qualityLabel: String = "",
        val masterUrl: String = "",
        /** The chosen VARIANT playlist — resuming MUST reuse it exactly. */
        val variantUrl: String = "",
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val states = MutableStateFlow<List<DownloadState>>(emptyList())
    val statesFlow: StateFlow<List<DownloadState>> = states.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()
    private val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Config.Network.CONNECT_TIMEOUT.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(Config.Network.READ_TIMEOUT.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun keyFor(animeId: Int, episode: Int, height: Int): String = "$animeId-ep$episode-${height}p"

    fun downloadsDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "AnikageDownloads").apply { mkdirs() }

    private fun outputFile(context: Context, key: String): File = File(downloadsDir(context), "$key.ts")
    private fun resumeFile(context: Context, key: String): File = File(downloadsDir(context), "$key.progress")
    private fun partFile(context: Context, key: String): File = File(downloadsDir(context), "$key.part")

    // ── public controls ───────────────────────────────────────────────────

    fun enqueue(context: Context, request: DownloadRequest) {
        val key = keyFor(request.animeId, request.episode, request.height)
        if (jobs[key]?.isActive == true) return
        states.value = states.value.filterNot { it.key == key } + DownloadState(
            key = key,
            animeId = request.animeId,
            episode = request.episode,
            slug = request.slug,
            titleRomaji = request.titleRomaji,
            titleEnglish = request.titleEnglish,
            episodeTitle = request.episodeTitle,
            posterUrl = request.posterUrl,
            status = Status.RESOLVING,
        )
        start(context.applicationContext, key, request)
    }

    fun pause(key: String) {
        jobs[key]?.cancel()
        updateState(key) { it.copy(status = Status.PAUSED, bytesPerSec = 0L, error = null) }
    }

    fun resume(context: Context, key: String) {
        val current = states.value.firstOrNull { it.key == key } ?: return
        if (jobs[key]?.isActive == true) return
        if (current.status != Status.PAUSED) return
        val ctx = context.applicationContext
        scope.launch {
            val row = runCatching { AnikageDatabase.get(ctx).downloadedEpisodeDao().get(key) }.getOrNull()
            val resume = readResume(ctx, key)
            val request = DownloadRequest(
                animeId = current.animeId,
                slug = row?.slug ?: "",
                episode = current.episode,
                provider = row?.provider ?: Config.DEFAULT_STREAM_PROVIDER,
                lang = row?.lang ?: SettingsState.streamLang,
                height = current.height,
                titleRomaji = current.titleRomaji,
                titleEnglish = current.titleEnglish,
                episodeTitle = current.episodeTitle,
                posterUrl = current.posterUrl,
            )
            if (request.slug.isBlank() && resume?.masterUrl.isNullOrBlank()) {
                updateState(key) { it.copy(status = Status.FAILED, error = "Missing episode info — restart the download from the watch page.") }
                return@launch
            }
            updateState(key) { it.copy(status = Status.DOWNLOADING, error = null) }
            start(ctx, key, request)
        }
    }

    fun retry(context: Context, key: String) {
        val current = states.value.firstOrNull { it.key == key } ?: return
        if (jobs[key]?.isActive == true) return
        val ctx = context.applicationContext
        resumeFile(ctx, key).delete()
        outputFile(ctx, key).delete()
        partFile(ctx, key).delete()
        updateState(key) {
            it.copy(
                status = Status.RESOLVING, error = null, progress = 0f,
                segmentsDone = 0, bytesDone = 0, bytesTotal = 0, bytesPerSec = 0,
            )
        }
        scope.launch {
            val row = runCatching { AnikageDatabase.get(ctx).downloadedEpisodeDao().get(key) }.getOrNull()
            val request = DownloadRequest(
                animeId = current.animeId,
                slug = row?.slug ?: "",
                episode = current.episode,
                provider = row?.provider ?: Config.DEFAULT_STREAM_PROVIDER,
                lang = row?.lang ?: SettingsState.streamLang,
                height = current.height,
                titleRomaji = current.titleRomaji,
                titleEnglish = current.titleEnglish,
                episodeTitle = current.episodeTitle,
                posterUrl = current.posterUrl,
            )
            if (request.slug.isBlank()) {
                updateState(key) { it.copy(status = Status.FAILED, error = "Restart the download from the watch page.") }
                return@launch
            }
            start(ctx, key, request)
        }
    }

    fun cancel(context: Context, key: String) {
        jobs[key]?.cancel()
        jobs.remove(key)
        val ctx = context.applicationContext
        scope.launch {
            runCatching { AnikageDatabase.get(ctx).downloadedEpisodeDao().delete(key) }
        }
        partFile(ctx, key).delete()
        resumeFile(ctx, key).delete()
        outputFile(ctx, key).delete()
        File(downloadsDir(ctx), "$key.vtt").delete()
        states.value = states.value.filterNot { it.key == key }
    }

    /** Delete a COMPLETED download (row + files). */
    fun deleteDownloaded(context: Context, key: String) = cancel(context, key)

    // ── engine core ───────────────────────────────────────────────────────

    private fun start(context: Context, key: String, request: DownloadRequest) {
        jobs[key]?.cancel()
        jobs[key] = scope.launch { runDownload(context, key, request) }
    }

    private suspend fun runDownload(context: Context, key: String, request: DownloadRequest) {
        try {
            download(context, key, request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(LogCategory.DOWNLOAD, "Download failed: $key — ${e.message}", e)
            updateState(key) { it.copy(status = Status.FAILED, error = friendly(e), bytesPerSec = 0L) }
        } finally {
            jobs.remove(key)
        }
    }

    private suspend fun download(context: Context, key: String, request: DownloadRequest) {
        if (SettingsState.downloadsWifiOnly && isNetworkMetered(context)) {
            updateState(key) { it.copy(status = Status.FAILED, error = "Wi-Fi only is on and this network is metered.") }
            return
        }

        val repo = AnikageRepository.get(context)
        val out = outputFile(context, key)
        val resume = readResume(context, key)

        // 1 ── resolve the stream (fresh token) unless resuming a known URL.
        var masterUrl = resume?.masterUrl.orEmpty()
        var chosenHeight = resume?.height ?: 0
        var chosenLabel = resume?.qualityLabel.orEmpty()

        if (masterUrl.isBlank() && resume?.variantUrl.orEmpty().isNotBlank()) {
            // Resuming with a known variant but expired master: keep the
            // persisted variant identity (height/label) so the re-resolved
            // master picks the SAME rendition.
            masterUrl = ""
            chosenHeight = resume?.height ?: 0
            chosenLabel = resume?.qualityLabel.orEmpty()
        }
        if (masterUrl.isBlank()) {
            if (request.slug.isBlank()) {
                updateState(key) { it.copy(status = Status.FAILED, error = "No Anikage slug — can't resolve the stream.") }
                return
            }
            updateState(key) { it.copy(status = Status.RESOLVING) }
            val response = repo.anikageSources(
                request.slug, request.episode, request.provider.lowercase(), request.lang, refresh = false,
            ).getOrElse { e ->
                updateState(key) { it.copy(status = Status.FAILED, error = "Couldn't resolve the stream: ${e.message}") }
                return
            }
            val best = response.sources.firstOrNull { it.isM3U8 }
                ?: response.sources.firstOrNull()
            val tokenUrl = best?.url?.let { t ->
                if (t.startsWith("http")) t
                else "${Config.ANIKAGE_STREAM_PROXY_BASE_URL ?: "https://og.bakayaro.live"}/m3u8/$t"
            }
            if (tokenUrl == null) {
                updateState(key) { it.copy(status = Status.FAILED, error = "No downloadable stream on ${request.provider}.") }
                return
            }
            masterUrl = tokenUrl
        }

        // 2 ── master playlist -> renditions
        val masterText = fetchText(masterUrl)
        val variants = parseMaster(masterText, masterUrl)
        val wantedHeight = if (request.height > 0) request.height else capHeightFromQualitySetting()

        // Resuming MUST reuse the persisted variant — a different rendition
        // would corrupt the already-appended segments.
        val variantUrl: String = when {
            !resume?.variantUrl.isNullOrBlank() && resume?.masterUrl == masterUrl -> {
                resume!!.variantUrl
            }
            variants.isNotEmpty() -> {
                val pick = pickVariant(variants, if (chosenHeight > 0) chosenHeight else wantedHeight)
                chosenHeight = pick.height
                chosenLabel = if (pick.height > 0) "${pick.height}p" else "Auto"
                pick.url
            }
            else -> {
                if (chosenLabel.isBlank()) chosenLabel = "Auto"
                masterUrl
            }
        }

        // 3 ── variant playlist -> segments (VOD + encryption checks)
        val mediaText = fetchText(variantUrl)
        val header = parseMediaPlaylist(mediaText)
        if (!header.isVod) {
            updateState(key) { it.copy(status = Status.FAILED, error = "This stream is still live — wait for the episode to finish airing.") }
            return
        }
        if (header.hasEncryption) {
            updateState(key) { it.copy(status = Status.FAILED, error = "This provider encrypts its stream. Try a different server for downloads.") }
            return
        }
        val segments = header.segments
        if (segments.isEmpty()) {
            updateState(key) { it.copy(status = Status.FAILED, error = "Empty segment list.") }
            return
        }

        val segmentsDone = (resume?.segmentsDone ?: 0).coerceIn(0, segments.size)
        var bytesDone = if (resume?.segmentsTotal == segments.size) resume.bytesDone else 0L
        if (segmentsDone == 0) out.delete() // fresh start — never mix partials

        writeResume(context, key, ResumeInfo(segmentsDone, bytesDone, segments.size, chosenHeight, chosenLabel, masterUrl, variantUrl))

        updateState(key) {
            it.copy(
                status = Status.DOWNLOADING,
                qualityLabel = chosenLabel,
                height = chosenHeight,
                segmentsTotal = segments.size,
                segmentsDone = segmentsDone,
                bytesDone = bytesDone,
                progress = segmentsDone.toFloat() / segments.size,
                error = null,
            )
        }

        // 4 ── download segments, appending to the single output file
        val part = partFile(context, key)
        var speedWindowStart = System.currentTimeMillis()
        var speedWindowBytes = 0L
        for (i in segmentsDone until segments.size) {
            coroutineContext.ensureActive()
            val seg = segments[i]
            downloadToFile(seg, part)
            appendToFile(part, out)
            part.delete()
            val segBytes = seg.length ?: out.length() - bytesDone
            bytesDone += segBytes.coerceAtLeast(0L)
            speedWindowBytes += segBytes.coerceAtLeast(0L)

            val now = System.currentTimeMillis()
            val elapsed = (now - speedWindowStart).coerceAtLeast(1)
            val bps = speedWindowBytes * 1000L / elapsed
            if (now - speedWindowStart > 1000L) {
                speedWindowStart = now
                speedWindowBytes = 0L
            }

            writeResume(context, key, ResumeInfo(i + 1, bytesDone, segments.size, chosenHeight, chosenLabel, masterUrl, variantUrl))
            updateState(key) {
                it.copy(
                    segmentsDone = i + 1,
                    bytesDone = bytesDone,
                    bytesPerSec = bps,
                    progress = (i + 1).toFloat() / segments.size,
                )
            }
        }

        // 5 ── softsub VTT alongside (best effort)
        var subPath: String? = null
        if (request.slug.isNotBlank()) {
            runCatching {
                val response = repo.anikageSources(
                    request.slug, request.episode, request.provider.lowercase(), request.lang, refresh = false,
                ).getOrNull()
                val sub = response?.subtitles?.firstOrNull { s ->
                    s.default || (s.label ?: "").contains(SettingsState.defaultSubtitleLang, ignoreCase = true)
                } ?: response?.subtitles?.firstOrNull()
                val fileToken = sub?.file
                if (fileToken != null) {
                    val url = if (fileToken.startsWith("http")) fileToken
                    else "${Config.ANIKAGE_STREAM_PROXY_BASE_URL ?: "https://og.bakayaro.live"}/stream/$fileToken"
                    val vttBody = fetchTextOrNull(url)
                    if (!vttBody.isNullOrBlank()) {
                        val vttFile = File(downloadsDir(context), "$key.vtt")
                        vttFile.writeText(vttBody)
                        subPath = vttFile.absolutePath
                    }
                }
            }
        }

        // 6 ── persist the record
        val size = out.length()
        val db = AnikageDatabase.get(context)
        db.downloadedEpisodeDao().upsert(
            DownloadedEpisodeEntity(
                downloadKey = key,
                animeId = request.animeId,
                slug = request.slug,
                episode = request.episode,
                quality = chosenLabel,
                height = chosenHeight,
                filePath = out.absolutePath,
                subtitlePath = subPath,
                titleRomaji = request.titleRomaji,
                titleEnglish = request.titleEnglish,
                episodeTitle = request.episodeTitle,
                posterUrl = request.posterUrl,
                sizeBytes = size,
                provider = request.provider,
                lang = request.lang,
            ),
        )
        resumeFile(context, key).delete()
        updateState(key) {
            it.copy(
                status = Status.COMPLETED,
                progress = 1f,
                bytesPerSec = 0L,
                bytesTotal = size,
                bytesDone = size,
                filePath = out.absolutePath,
                subtitlePath = subPath,
                slug = request.slug,
            )
        }
        AppLogger.i(
            LogCategory.DOWNLOAD,
            "Download complete: ${request.titleRomaji ?: request.titleEnglish ?: request.slug} ep${request.episode} " +
                "[$chosenLabel, ${size / 1_000_000}MB, ${segments.size} segments, sub=${subPath != null}]",
        )
    }

    // ── playlist parsing ──────────────────────────────────────────────────

    /** A rendition choice for the download dialog. */
    data class VariantInfo(val height: Int, val label: String)

    /**
     * Resolve the downloadable renditions for an episode (the download
     * dialog's quality list): sources -> token URL -> master playlist ->
     * variant list. Single-variant proxies report one "Auto" entry.
     */
    suspend fun resolveVariantList(
        context: Context,
        slug: String,
        episode: Int,
        provider: String,
        lang: String,
    ): Result<List<VariantInfo>> = runCatching {
        val repo = AnikageRepository.get(context)
        val response = repo.anikageSources(slug, episode, provider.lowercase(), lang, refresh = false)
            .getOrElse { e -> throw IOException("Sources unavailable: ${e.message}") }
        val best = response.sources.firstOrNull { it.isM3U8 } ?: response.sources.firstOrNull()
        val tokenUrl = best?.url?.let { t ->
            if (t.startsWith("http")) t
            else "${Config.ANIKAGE_STREAM_PROXY_BASE_URL ?: "https://og.bakayaro.live"}/m3u8/$t"
        } ?: throw IOException("No downloadable stream on this server.")
        val masterText = fetchText(tokenUrl)
        val variants = parseMaster(masterText, tokenUrl)
        if (variants.isEmpty()) {
            listOf(VariantInfo(0, "Auto"))
        } else {
            variants.map { VariantInfo(it.height, if (it.height > 0) "${it.height}p" else "Auto") }
                .sortedByDescending { it.height }
        }
    }

    private data class Variant(val height: Int, val url: String)

    private data class Segment(val url: String, val offset: Long?, val length: Long?)

    private data class MediaHeader(
        val segments: List<Segment>,
        val isVod: Boolean,
        val hasEncryption: Boolean,
    )

    private fun parseMaster(text: String, baseUrl: String): List<Variant> {
        val out = mutableListOf<Variant>()
        var pendingHeight = 0
        for (line in text.lines()) {
            val t = line.trim()
            if (t.startsWith("#EXT-X-STREAM-INF")) {
                val res = Regex("RESOLUTION=(\\d+)x(\\d+)").find(t)
                pendingHeight = res?.groupValues?.get(2)?.toIntOrNull() ?: 0
            } else if (t.isNotEmpty() && !t.startsWith("#")) {
                out += Variant(pendingHeight, resolveUrl(baseUrl, t))
                pendingHeight = 0
            }
        }
        return out
    }

    private fun parseMediaPlaylist(text: String): MediaHeader {
        val segments = mutableListOf<Segment>()
        var isVod = text.contains("#EXT-X-ENDLIST")
        var hasEncryption = false
        var pendingOffset: Long? = null
        var pendingLength: Long? = null
        var runningOffset: Long? = null
        var lastUri: String? = null
        for (line in text.lines()) {
            val t = line.trim()
            when {
                t.startsWith("#EXT-X-KEY") -> {
                    val method = Regex("METHOD=([A-Za-z0-9-]+)").find(t)?.groupValues?.get(1) ?: "NONE"
                    if (method != "NONE") hasEncryption = true
                }
                t.startsWith("#EXT-X-BYTERANGE") -> {
                    val m = Regex("([0-9]+)(?:@([0-9]+))?").find(t.removePrefix("#EXT-X-BYTERANGE:"))
                    pendingLength = m?.groupValues?.get(1)?.toLongOrNull()
                    pendingOffset = m?.groupValues?.get(2)?.toLongOrNull()
                }
                t.isNotEmpty() && !t.startsWith("#") -> {
                    val effectiveOffset = pendingOffset
                        ?: if (lastUri == t) runningOffset else null
                    segments += Segment(t, effectiveOffset, pendingLength)
                    runningOffset = if (effectiveOffset != null && pendingLength != null) {
                        effectiveOffset + pendingLength
                    } else null
                    lastUri = t
                    pendingOffset = null
                    pendingLength = null
                }
            }
        }
        return MediaHeader(segments, isVod, hasEncryption)
    }

    private fun resolveUrl(base: String, ref: String): String = runCatching {
        URI(base).resolve(ref).toString()
    }.getOrDefault(ref)

    private fun pickVariant(variants: List<Variant>, wantedHeight: Int): Variant {
        if (wantedHeight > 0) {
            variants.firstOrNull { it.height == wantedHeight }?.let { return it }
            variants.filter { it.height in 1 until wantedHeight }.maxByOrNull { it.height }?.let { return it }
        }
        return variants.maxByOrNull { it.height } ?: variants.first()
    }

    private fun capHeightFromQualitySetting(): Int = when (SettingsState.streamQuality) {
        "low" -> 480
        "standard" -> 720
        "full" -> 1080
        else -> 0
    }

    // ── HTTP plumbing ─────────────────────────────────────────────────────

    private fun request(url: String, range: Pair<Long, Long>? = null): Request {
        val b = Request.Builder()
            .url(url)
            .header("User-Agent", Config.Network.USER_AGENT)
            .header("Referer", "${Config.ANIKAGE_SITE_ORIGIN}/")
            .header("Origin", Config.ANIKAGE_SITE_ORIGIN)
        if (range != null) b.header("Range", "bytes=${range.first}-${range.second}")
        return b.build()
    }

    private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(request(url)).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for ${url.takeLast(60)}")
            resp.body?.string() ?: throw IOException("Empty body")
        }
    }

    private suspend fun fetchTextOrNull(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(request(url)).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull()
    }

    private suspend fun downloadToFile(seg: Segment, dest: File) = withContext(Dispatchers.IO) {
        val range = if (seg.length != null && seg.offset != null) seg.offset to (seg.offset + seg.length - 1) else null
        client.newCall(request(seg.url, range)).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Segment HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Segment body empty")
            dest.outputStream().use { out -> body.byteStream().copyTo(out, 64 * 1024) }
        }
    }

    private fun appendToFile(part: File, out: File) {
        java.io.FileOutputStream(out, true).use { fout ->
            java.io.FileInputStream(part).use { fin -> fin.copyTo(fout, 64 * 1024) }
        }
    }

    // ── persistence helpers ───────────────────────────────────────────────

    private fun readResume(context: Context, key: String): ResumeInfo? = runCatching {
        val f = resumeFile(context, key)
        if (!f.exists()) null else json.decodeFromString<ResumeInfo>(f.readText())
    }.getOrNull()

    private fun writeResume(context: Context, key: String, info: ResumeInfo) {
        runCatching { resumeFile(context, key).writeText(json.encodeToString(info)) }
    }

    private fun updateState(key: String, block: (DownloadState) -> DownloadState) {
        val list = states.value
        states.value = if (list.any { it.key == key }) {
            list.map { if (it.key == key) block(it) else it }
        } else {
            list + block(DownloadState(key = key, animeId = -1, episode = -1))
        }
    }

    private fun isNetworkMetered(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.isActiveNetworkMetered
    }.getOrDefault(false)

    private fun friendly(e: Exception): String = when {
        e.message?.contains("HTTP 403") == true ->
            "The stream host blocked this download (403). Try again or pick another server."
        e.message?.contains("HTTP 429") == true ->
            "Rate limited by the stream host. Wait a few seconds and retry."
        else -> e.message ?: "Download failed."
    }

    /** Reconcile in-memory states with DB rows on app start. */
    fun restoreCompleted(context: Context) {
        scope.launch {
            runCatching {
                val ctx = context.applicationContext
                val db = AnikageDatabase.get(ctx)
                val rows = db.downloadedEpisodeDao().all()
                val persisted = rows.map { row ->
                    DownloadState(
                        key = row.downloadKey,
                        animeId = row.animeId,
                        episode = row.episode,
                        slug = row.slug,
                        titleRomaji = row.titleRomaji,
                        titleEnglish = row.titleEnglish,
                        episodeTitle = row.episodeTitle,
                        posterUrl = row.posterUrl,
                        qualityLabel = row.quality,
                        height = row.height,
                        status = Status.COMPLETED,
                        progress = 1f,
                        bytesDone = row.sizeBytes,
                        bytesTotal = row.sizeBytes,
                        filePath = row.filePath,
                        subtitlePath = row.subtitlePath,
                    )
                }
                // Partial downloads left over from a killed process -> PAUSED.
                val dir = downloadsDir(ctx)
                val partials = dir.listFiles { f -> f.name.endsWith(".progress") }?.toList().orEmpty()
                val paused = partials.mapNotNull { pf ->
                    val key = pf.name.removeSuffix(".progress")
                    val info = runCatching { json.decodeFromString<ResumeInfo>(pf.readText()) }.getOrNull()
                        ?: return@mapNotNull null
                    val animeId = key.substringBefore("-ep").toIntOrNull() ?: return@mapNotNull null
                    val ep = key.substringAfter("-ep").substringBefore("-").toIntOrNull() ?: return@mapNotNull null
                    DownloadState(
                        key = key,
                        animeId = animeId,
                        episode = ep,
                        qualityLabel = info.qualityLabel,
                        height = info.height,
                        status = Status.PAUSED,
                        progress = if (info.segmentsTotal > 0) info.segmentsDone.toFloat() / info.segmentsTotal else 0f,
                        segmentsDone = info.segmentsDone,
                        segmentsTotal = info.segmentsTotal,
                        bytesDone = info.bytesDone,
                    ).takeIf { info.segmentsDone > 0 }
                }
                states.value = (persisted + paused).distinctBy { it.key }
            }
            Unit
        }
    }
}
