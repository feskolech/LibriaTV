package ru.feskolech.libriatv.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import ru.feskolech.libriatv.data.api.AniLibriaApi
import ru.feskolech.libriatv.data.api.CollectionUpdateDto
import ru.feskolech.libriatv.data.api.FavoriteUpdateDto
import ru.feskolech.libriatv.data.api.RatingRequestDto
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.domain.ReleasePage
import ru.feskolech.libriatv.domain.UserList

/** Seasons/franchise, user lists ("collections") and the user's own rating. */
@Singleton
class LibraryRepository @Inject constructor(private val api: AniLibriaApi) {

    private suspend fun <T> safe(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    /**
     * Other seasons of [release], oldest first, including [release] itself; empty when there are none.
     * The API franchise list lags behind new seasons (season 3 of a show may be missing from season 2's
     * franchise and have none itself), so it is merged with a title search for the same base name.
     */
    suspend fun seasons(release: Release): List<Release> {
        val fromFranchise = safe { api.franchises(release.id) }.orEmpty()
            .flatMap { franchise -> franchise.releases.sortedBy { it.sortOrder ?: Int.MAX_VALUE } }
            .mapNotNull { it.release?.toDomain() }
        val base = seasonBaseTitle(release.title)
        val fromSearch = if (base.length < 3) emptyList() else
            safe { api.search(base) }.orEmpty().mapNotNull { it.toDomain() }
                .filter { seasonBaseTitle(it.title).equals(base, ignoreCase = true) }
        val all = (fromFranchise + fromSearch + release).distinctBy { it.id }
        if (all.size < 2) return emptyList()
        val order = fromFranchise.map { it.id }
        return all.sortedWith(compareBy<Release>(
            { order.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } },
            { it.year ?: Int.MAX_VALUE },
            { seasonNumber(it.title) },
        ))
    }

    /** Current list of [releaseId], or null if it is in none. */
    suspend fun listOf(releaseId: Int): UserList? = safe { api.collectionIds() }.orEmpty()
        .firstOrNull { it.getOrNull(0)?.jsonPrimitive?.intOrNull == releaseId }
        ?.getOrNull(1)?.jsonPrimitive?.contentOrNull?.let(UserList::of)

    /** Moves the release to [list] (null removes it from all lists). */
    suspend fun setList(releaseId: Int, list: UserList?): Boolean = safe {
        if (list == null) api.removeFromCollection(listOf(FavoriteUpdateDto(releaseId)))
        else api.addToCollection(listOf(CollectionUpdateDto(releaseId, list.apiValue)))
        true
    } ?: false

    suspend fun listReleases(list: UserList, page: Int): ReleasePage? = safe {
        val response = api.collectionReleases(list.apiValue, page)
        val pagination = response.meta?.pagination
        ReleasePage(response.data.mapNotNull { it.toDomain() }, pagination?.currentPage ?: page, pagination?.totalPages ?: page)
    }

    suspend fun ownRating(releaseId: Int): Int? = safe { api.ownRating(releaseId).score?.toInt() }

    /** Sets the score 1..10, or clears it for null. Returns false on failure. */
    suspend fun rate(releaseId: Int, score: Int?): Boolean = safe {
        if (score == null) api.unrate(releaseId) else api.rate(releaseId, RatingRequestDto(score))
        true
    } ?: false
}

private val SEASON_SUFFIX = Regex("""(\s*[:\-–—]?\s*(\d+|[IVX]+|сезон\s*\d+|\d+\s*сезон|season\s*\d+|\d+(st|nd|rd|th)\s*season))$""", RegexOption.IGNORE_CASE)

/** "Перерождение в аристократа ... анализа 3" -> "Перерождение в аристократа ... анализа". */
internal fun seasonBaseTitle(title: String): String = title.trim().replace(SEASON_SUFFIX, "").trim()

internal fun seasonNumber(title: String): Int =
    Regex("""(\d+)\D*$""").find(title.trim())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { title.trim() != seasonBaseTitle(title) } ?: 1
