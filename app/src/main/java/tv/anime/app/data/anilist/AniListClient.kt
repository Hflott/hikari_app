package tv.anime.app.data.anilist

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tv.anime.app.net.SharedOkHttp

class AniListClient {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val http = HttpClient(OkHttp) {
        engine {
            // Share TLS configuration with the rest of the app (Coil, Media3).
            preconfigured = SharedOkHttp.client
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun post(
        query: String,
        variables: JsonObject = buildJsonObject { }
    ): GraphQLResponse {
        val requestBody = GraphQLRequest(query = query, variables = variables)

        return http.post {
            url("https://graphql.anilist.co")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.body()
    }

    companion object {
        /**
         * Helper to build a GraphQL variables JsonObject from primitive Kotlin types.
         * Nulls are omitted (common GraphQL convention).
         */
        fun variablesOf(pairs: Map<String, Any?>): JsonObject = buildJsonObject {
            for ((k, v) in pairs) {
                when (v) {
                    null -> {
                        // omit nulls
                    }
                    is Int -> put(k, JsonPrimitive(v))
                    is Long -> put(k, JsonPrimitive(v))
                    is Double -> put(k, JsonPrimitive(v))
                    is Float -> put(k, JsonPrimitive(v.toDouble()))
                    is Boolean -> put(k, JsonPrimitive(v))
                    is String -> put(k, JsonPrimitive(v))
                    else -> put(k, JsonPrimitive(v.toString()))
                }
            }
        }
    }
}

@Serializable
data class GraphQLRequest(
    val query: String,
    val variables: JsonObject = buildJsonObject { }
)

@Serializable
data class GraphQLResponse(
    val data: JsonObject? = null,
    val errors: List<GraphQLError>? = null
)

@Serializable
data class GraphQLError(val message: String)
