package com.paarth.zeitreise.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The account the fake server starts with. */
internal const val ADA = "ada@example.com"
internal const val ADA_PASSWORD = "analytical-engine"

/** Supabase's shortest allowed password. */
private const val MIN_PASSWORD_LENGTH = 6

/** What Ktor's browser engine throws when a fetch fails: an [Error], not an IOException. */
private class FailedFetch : Error("Fail to fetch")

private val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")

/**
 * A pretend Supabase project for tests: it has accounts with passwords, keeps one progress row and
 * records every request.
 */
internal class FakeSupabase {
    /** The accounts, by email, with their passwords. */
    val accounts = mutableMapOf(ADA to ADA_PASSWORD)

    /** The progress row saved in the cloud, if any. */
    var progress: SyncedProgress? = null

    /** When set, refreshing a session fails with this status, as when it was ended elsewhere. */
    var refreshStatus: HttpStatusCode? = null

    /** When set, signing in fails with this status, such as 429 after too many tries. */
    var signInStatus: HttpStatusCode? = null

    /** When set, every request fails the way a browser reports a server it can't reach. */
    var unreachable = false

    val requests = mutableListOf<HttpRequestData>()

    private var sessions = 0
    private val json = Json { ignoreUnknownKeys = true }

    /** A client for this server. Its requests run on [dispatcher], so virtual time in tests covers them. */
    fun api(dispatcher: CoroutineDispatcher): SupabaseApi {
        val client = HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { answer(it) }
            }
        }
        return SupabaseApi(client, url = PROJECT_URL, key = PROJECT_KEY)
    }

    private fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
        requests += request
        if (unreachable) throw FailedFetch()
        val body = (request.body as? TextContent)?.text.orEmpty()
        return when (request.method to request.url.encodedPath) {
            HttpMethod.Post to "/auth/v1/signup" -> signUp(body)
            HttpMethod.Post to "/auth/v1/token" -> when (request.url.parameters["grant_type"]) {
                "password" -> signIn(body)
                else -> refreshStatus?.let { respond("{}", it) } ?: ok(session(ADA))
            }
            HttpMethod.Post to "/auth/v1/logout" -> respond("", HttpStatusCode.NoContent)
            HttpMethod.Post to "/rest/v1/rpc/delete_account" -> {
                accounts.remove(ADA)
                progress = null
                respond("", HttpStatusCode.NoContent)
            }
            HttpMethod.Get to "/rest/v1/progress" -> ok(rows())
            HttpMethod.Post to "/rest/v1/progress" -> {
                val data = json.parseToJsonElement(body).jsonObject.getValue("data")
                progress = json.decodeFromJsonElement(SyncedProgress.serializer(), data)
                respond("", HttpStatusCode.Created)
            }
            else -> respond("{}", HttpStatusCode.NotFound)
        }
    }

    private fun MockRequestHandleScope.signUp(body: String): HttpResponseData {
        val (email, password) = credentials(body)
        return when {
            '@' !in email -> error(HttpStatusCode.BadRequest, "validation_failed")
            email in accounts -> error(HttpStatusCode.UnprocessableEntity, "user_already_exists")
            password.length < MIN_PASSWORD_LENGTH -> error(HttpStatusCode.UnprocessableEntity, "weak_password")
            else -> {
                accounts[email] = password
                ok(session(email))
            }
        }
    }

    private fun MockRequestHandleScope.signIn(body: String): HttpResponseData {
        signInStatus?.let { return respond("{}", it) }
        val (email, password) = credentials(body)
        return if (accounts[email] == password) {
            ok(session(email))
        } else {
            error(HttpStatusCode.BadRequest, "invalid_credentials")
        }
    }

    private fun credentials(body: String): Pair<String, String> {
        val fields = json.parseToJsonElement(body).jsonObject
        return fields.getValue("email").jsonPrimitive.content to fields.getValue("password").jsonPrimitive.content
    }

    private fun MockRequestHandleScope.error(status: HttpStatusCode, code: String) =
        respond("""{"code":${status.value},"error_code":"$code","msg":"$code"}""", status, JSON_HEADERS)

    private fun rows(): String =
        progress?.let { "[{\"data\":${json.encodeToString(SyncedProgress.serializer(), it)}}]" } ?: "[]"

    private fun MockRequestHandleScope.ok(text: String) = respond(text, HttpStatusCode.OK, JSON_HEADERS)

    private fun session(email: String): String {
        sessions++
        return """
            { "access_token": "access-$sessions", "refresh_token": "refresh-$sessions", "expires_in": 3600,
              "user": { "id": "user-1", "email": "$email" } }
        """.trimIndent()
    }

    companion object {
        const val PROJECT_URL = "https://project.supabase.co"
        const val PROJECT_KEY = "public-key"
    }
}
