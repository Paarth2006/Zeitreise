package com.paarth.zeitreise.data

import kotlinx.io.IOException

/**
 * Runs [request] and reports a server that can't be reached as an [IOException] on every platform.
 * Ktor's browser engine throws an [Error] instead (`Fail to fetch`), which the callers that fall
 * back to cached or bundled data would otherwise miss, so the web app would stop where Android and
 * iOS carry on.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> reaching(request: suspend () -> T): T = try {
    request()
} catch (unreachable: Error) {
    throw IOException("Could not reach the server", unreachable)
}
