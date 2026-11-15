package com.paarth.zeitreise.dataprep

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.math.pow
import kotlin.math.roundToLong

/** Bytes in a kibibyte, for the sizes the generators report. */
internal const val BYTES_PER_KIB = 1024

/** Seconds to wait for the connection and for the whole download. */
private const val CONNECT_TIMEOUT_SECONDS = 30L
private const val REQUEST_TIMEOUT_SECONDS = 300L

/** The only HTTP status a download accepts. */
private const val HTTP_OK = 200

/** Downloads [url] as text, failing loudly on anything but HTTP 200. */
internal fun download(url: String): String {
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS)).build().use { client ->
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "Zeitreise-dataprep/1.0 (https://github.com/Paarth2006/Zeitreise)")
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == HTTP_OK) { "GET $url returned HTTP ${response.statusCode()}" }
        return response.body()
    }
}

/** The text of property [key], or an empty string when it is missing or null. */
internal fun JsonObject.text(key: String): String {
    val value = this[key]
    return if (value == null || value is JsonNull) "" else value.jsonPrimitive.content
}

/** Rounds [value] to [decimals] decimal places. */
internal fun round(value: Double, decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    return (value * factor).roundToLong() / factor
}
