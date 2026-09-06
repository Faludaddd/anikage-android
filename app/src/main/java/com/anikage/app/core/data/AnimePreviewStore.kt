package com.anikage.app.core.data

import com.anikage.app.core.data.model.Anime

/**
 * Process-wide in-memory store of `Anime` previews.
 *
 * Every screen that shows a list of anime (home rails, browse grid, search
 * results, schedule entries) ALREADY holds full objects — title, poster,
 * banner, description, genres, score, and (since the site's payloads carry
 * it) the Anikage catalogue `slug`. Navigation only passes the id, so that
 * data was being thrown away and re-fetched from scratch on the details
 * screen.
 *
 * Click handlers seed the store right before navigating:
 *
 * ```
 * AnimePreviewStore.put(anime)
 * navController.navigate(Routes.details(anime.id))
 * ```
 *
 * The details / schedule-detail ViewModels then:
 *  - render instantly from the preview (no black screen, no skeleton-only
 *    state when data is already in memory);
 *  - learn the `slug`, so the Anikage info endpoint (the website's own
 *    data source) can be queried directly — no AniList round-trip needed
 *    to "re-discover" data the app already had.
 *
 * Deliberately in-memory only: it is a navigation hand-off, not a cache —
 * durable caching stays in Room (DetailCacheEntity) and the repository's
 * single-flight/TTL layer. Bounded LRU so a long browsing session can't
 * grow it without limit.
 */
object AnimePreviewStore {

    private const val MAX_ENTRIES = 128

    private val lock = Any()

    /** id -> preview, insertion-ordered (eldest first) for LRU eviction. */
    private val byIdMap = LinkedHashMap<Int, Anime>(16, 0.75f, true)

    /** slug -> preview (for slug-keyed lookups from relations etc.). */
    private val bySlugMap = LinkedHashMap<String, Anime>(16, 0.75f, true)

    /** Seed the store with a list item right before navigating to it. */
    fun put(anime: Anime) {
        synchronized(lock) {
            byIdMap.put(anime.id, anime)
            anime.slug?.let { bySlugMap.put(it, anime) }
            while (byIdMap.size > MAX_ENTRIES) {
                val eldest = byIdMap.keys.first()
                byIdMap.remove(eldest)?.slug?.let { bySlugMap.remove(it) }
            }
            while (bySlugMap.size > MAX_ENTRIES) {
                bySlugMap.remove(bySlugMap.keys.first())
            }
        }
    }

    /** Preview for an AniList id, or null. Does NOT count as an LRU access. */
    fun byId(id: Int): Anime? = synchronized(lock) { byIdMap[id] }

    /** Preview for an Anikage slug, or null. */
    fun bySlug(slug: String): Anime? = synchronized(lock) { bySlugMap[slug] }

    /** The catalogue slug for an AniList id if a preview was seeded. */
    fun slugFor(id: Int): String? = synchronized(lock) { byIdMap[id]?.slug }

    fun clear() = synchronized(lock) {
        byIdMap.clear()
        bySlugMap.clear()
    }
}
