package tv.anime.app.data.tmdb

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tv.anime.app.net.SharedOkHttp

/**
 * Minimal TMDB v3 client used only for retrieving title-logo art.
 *
 * Docs:
 * - Search TV: /search/tv
 * - Search Movie: /search/movie
 * - TV series images (logos included): /tv/{series_id}/images
 * - TV series details: /tv/{series_id}
 * - Movie details: /movie/{movie_id}
 */
class TmdbClient(
    private val apiKey: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val http = HttpClient(OkHttp) {
        engine {
            preconfigured = SharedOkHttp.client
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun searchTv(query: String, firstAirDateYear: Int? = null): TmdbSearchResponse {
        return http.get {
            url("https://api.themoviedb.org/3/search/tv")
            parameter("api_key", apiKey)
            parameter("query", query)
            if (firstAirDateYear != null) {
                parameter("first_air_date_year", firstAirDateYear)
            }
            parameter("include_adult", false)
        }.body()
    }

    suspend fun tvImages(seriesId: Int): TmdbImagesResponse {
        return http.get {
            url("https://api.themoviedb.org/3/tv/$seriesId/images")
            parameter("api_key", apiKey)
            // Prefer english logos but allow "null" (no language) assets.
            parameter("include_image_language", "en,null")
        }.body()
    }

    suspend fun searchMovie(query: String, primaryReleaseYear: Int? = null): TmdbMovieSearchResponse {
        return http.get {
            url("https://api.themoviedb.org/3/search/movie")
            parameter("api_key", apiKey)
            parameter("query", query)
            if (primaryReleaseYear != null) {
                parameter("primary_release_year", primaryReleaseYear)
            }
            parameter("include_adult", false)
        }.body()
    }

    suspend fun tvDetails(seriesId: Int): TmdbTvDetailsResponse {
        return http.get {
            url("https://api.themoviedb.org/3/tv/$seriesId")
            parameter("api_key", apiKey)
            parameter("language", "en-US")
        }.body()
    }

    suspend fun movieDetails(movieId: Int): TmdbMovieDetailsResponse {
        return http.get {
            url("https://api.themoviedb.org/3/movie/$movieId")
            parameter("api_key", apiKey)
            parameter("language", "en-US")
        }.body()
    }
}

@Serializable
data class TmdbSearchResponse(
    val page: Int = 1,
    val results: List<TmdbSearchItem> = emptyList()
)

@Serializable
data class TmdbMovieSearchResponse(
    val page: Int = 1,
    val results: List<TmdbMovieSearchItem> = emptyList()
)

@Serializable
data class TmdbSearchItem(
    val id: Int,
    val name: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null
)

@Serializable
data class TmdbMovieSearchItem(
    val id: Int,
    val title: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null
)

@Serializable
data class TmdbImagesResponse(
    val id: Int,
    val logos: List<TmdbImage> = emptyList()
)

@Serializable
data class TmdbImage(
    @SerialName("file_path") val filePath: String,
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("iso_639_1") val language: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0
)

@Serializable
data class TmdbGenre(
    val id: Int,
    val name: String
)

@Serializable
data class TmdbTvDetailsResponse(
    val id: Int,
    val name: String? = null,
    val overview: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val status: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null
)

@Serializable
data class TmdbMovieDetailsResponse(
    val id: Int,
    val title: String? = null,
    val overview: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
    val runtime: Int? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val status: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null
)
