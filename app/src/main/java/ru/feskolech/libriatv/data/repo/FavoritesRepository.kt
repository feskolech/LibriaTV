package ru.feskolech.libriatv.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.feskolech.libriatv.data.api.AniLibriaApi
import ru.feskolech.libriatv.data.api.FavoriteUpdateDto
import ru.feskolech.libriatv.domain.FilterOption
import ru.feskolech.libriatv.domain.ReleasePage

@Singleton
class FavoritesRepository private constructor(
    private val api: AniLibriaApi,
    private val isAuthorized: suspend () -> Boolean,
) {
    @Inject constructor(api: AniLibriaApi, tokens: TokenStore) : this(api, { !tokens.get().isNullOrBlank() })
    internal constructor(api: AniLibriaApi) : this(api, { true })
    private val mutex = Mutex()
    private val _ids = MutableStateFlow<Set<Int>>(emptySet())
    val ids: StateFlow<Set<Int>> = _ids

    suspend fun refreshIds(): ApiResult<Set<Int>> = mutex.withLock {
        request {
            if (!isAuthorized()) {
                _ids.value = emptySet()
                emptySet()
            } else api.favoriteIds().toSet().also { _ids.value = it }
        }
    }

    suspend fun releases(page: Int = 1, sorting: String? = null): ApiResult<ReleasePage> = request {
        val response = api.favoriteReleases(page, 30, sorting)
        val pagination = response.meta?.pagination
        ReleasePage(
            response.data.mapNotNull { it.toDomain() },
            pagination?.currentPage ?: page,
            pagination?.totalPages ?: page,
        )
    }

    /** The startup comparison needs every favorite, including pages beyond the home row. */
    suspend fun allReleases(): ApiResult<List<ru.feskolech.libriatv.domain.Release>> = request {
        val result = mutableListOf<ru.feskolech.libriatv.domain.Release>()
        var page = 1
        do {
            val response = api.favoriteReleases(page, 30)
            result += response.data.mapNotNull { it.toDomain() }
            val lastPage = response.meta?.pagination?.totalPages ?: page
            page++
        } while (page <= lastPage)
        result.distinctBy { it.id }
    }

    suspend fun sorting(): ApiResult<List<FilterOption>> = request {
        api.favoriteSorting().mapNotNull { option ->
            option.value?.let { FilterOption(it, option.label ?: option.description ?: it) }
        }
    }

    suspend fun toggle(id: Int): ApiResult<Set<Int>> = mutex.withLock {
        if (!isAuthorized()) return@withLock ApiResult.Failure(401, "Требуется вход")
        val before = _ids.value
        val add = id !in before
        _ids.value = if (add) before + id else before - id
        when (val result = request {
            (if (add) api.addFavorites(listOf(FavoriteUpdateDto(id)))
                else api.deleteFavorites(listOf(FavoriteUpdateDto(id)))).toSet()
        }) {
            is ApiResult.Success -> { _ids.value = result.value; result }
            is ApiResult.Failure -> { _ids.value = before; result }
        }
    }

    private suspend fun <T> request(block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: retrofit2.HttpException) {
        ApiResult.Failure(error.code(), error.message())
    } catch (error: Exception) {
        ApiResult.Failure(null, error.message ?: "Ошибка сети")
    }
}
