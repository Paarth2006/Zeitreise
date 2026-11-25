package com.paarth.zeitreise.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** The table that holds one row of progress per user. Its setup is in `supabase/setup.sql`. */
private const val PROGRESS_TABLE = "rest/v1/progress"

/**
 * A signed-in user's session.
 *
 * @property accessToken proves who the user is on each request. It expires after about an hour.
 * @property refreshToken gets a new session later. Supabase replaces it on every refresh.
 * @property userId the user's ID, which their progress row is saved under.
 * @property email the address the user signed in with.
 */
internal data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
)

/**
 * Supabase answered a request with an error.
 *
 * @property status the HTTP status, such as 400.
 * @property errorCode Supabase's name for the error, such as `invalid_credentials`, if it gave one.
 */
internal class SupabaseException(val status: Int, val errorCode: String? = null) :
    IllegalStateException("Supabase answered HTTP $status ${errorCode.orEmpty()}")

/**
 * Talks to a Supabase project over its REST APIs: Auth for accounts with an email and password,
 * and PostgREST for the progress table. Plain Ktor calls keep this in common code on every target.
 *
 * @param url the project's address, such as `https://abcd.supabase.co`.
 * @param key the project's public (anon or publishable) key. It's safe in the app, because the
 *   table's row-level security only lets each user reach their own row.
 */
internal class SupabaseApi(
    private val client: HttpClient,
    private val url: String,
    private val key: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /**
     * Creates an account and signs in to it. The project doesn't ask new users to confirm their
     * email, so the answer is a session right away and no email is sent.
     */
    suspend fun signUp(email: String, password: String): AuthSession =
        session(post("auth/v1/signup", json.credentials(email, password)))

    /** Signs in to an existing account. */
    suspend fun signIn(email: String, password: String): AuthSession =
        session(post("auth/v1/token?grant_type=password", json.credentials(email, password)))

    /** Swaps [refreshToken] for a new session, with a new refresh token. */
    suspend fun refresh(refreshToken: String): AuthSession {
        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
        return session(post("auth/v1/token?grant_type=refresh_token", body))
    }

    /** Ends [session] on the server, so its refresh token stops working. */
    suspend fun signOut(session: AuthSession) {
        post("auth/v1/logout", body = null) { bearerAuth(session.accessToken) }
    }

    /** The user's saved progress, or `null` if nothing was saved yet. */
    suspend fun loadProgress(session: AuthSession): SyncedProgress? {
        val response = send {
            client.get("$url/$PROGRESS_TABLE?select=data") {
                header(API_KEY_HEADER, key)
                bearerAuth(session.accessToken)
            }
        }
        return json.decodeFromString(
            ListSerializer(ProgressRow.serializer()),
            response.bodyAsText(),
        ).firstOrNull()?.data
    }

    /** Saves [progress] as the user's row, replacing what was there. */
    suspend fun saveProgress(session: AuthSession, progress: SyncedProgress) {
        val row = json.encodeToString(ProgressRow.serializer(), ProgressRow(userId = session.userId, data = progress))
        post(PROGRESS_TABLE, row) {
            bearerAuth(session.accessToken)
            // An upsert: the row is created the first time and replaced after that.
            header("Prefer", "resolution=merge-duplicates,return=minimal")
        }
    }

    private suspend fun post(
        path: String,
        body: String?,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = send {
        client.post("$url/$path") {
            header(API_KEY_HEADER, key)
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            configure()
        }
    }

    /**
     * Sends a request and checks its status. Ktor's browser engine reports a server it can't reach
     * as an [Error] instead of an [IOException], so that is turned into one here, as every platform's
     * callers expect.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun send(request: suspend () -> HttpResponse): HttpResponse {
        val response = try {
            request()
        } catch (unreachable: Error) {
            throw IOException("Could not reach Supabase", unreachable)
        }
        return response.checked()
    }

    private suspend fun session(response: HttpResponse): AuthSession {
        val session = json.decodeFromString(SessionResponse.serializer(), response.bodyAsText())
        return AuthSession(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            userId = session.user.id,
            email = session.user.email.orEmpty(),
        )
    }

    private suspend fun HttpResponse.checked(): HttpResponse = also {
        if (status.isSuccess()) return@also
        // The error's name tells apart, say, a wrong password from an account that already exists.
        val text = bodyAsText()
        val errorCode = try {
            json.decodeFromString(ErrorResponse.serializer(), text).errorCode
        } catch (_: IllegalArgumentException) {
            // Not Supabase's usual error JSON, so only the status is known.
            null
        }
        throw SupabaseException(status.value, errorCode)
    }

    private companion object {
        /** Supabase reads the project key from this header on every request. */
        const val API_KEY_HEADER = "apikey"
    }
}

/** The JSON body that signs up or signs in with [email] and [password]. */
private fun Json.credentials(email: String, password: String): String =
    encodeToString(Credentials.serializer(), Credentials(email, password))

@Serializable
private data class Credentials(val email: String, val password: String)

@Serializable
private data class ErrorResponse(@SerialName("error_code") val errorCode: String? = null)

@Serializable
private data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
private data class SessionResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    val user: SessionUser,
)

@Serializable
private data class SessionUser(val id: String, val email: String? = null)

/** One row of the progress table. Reads ask only for [data]. */
@Serializable
private data class ProgressRow(@SerialName("user_id") val userId: String? = null, val data: SyncedProgress)
