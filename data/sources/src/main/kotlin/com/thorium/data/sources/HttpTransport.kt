package com.thorium.data.sources

import com.thorium.core.model.OpenedStream
import com.thorium.core.model.SourceException
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Shared HTTP behaviour: URL checks, credentials, error mapping and resumable reads. */
internal class HttpTransport(
    private val client: OkHttpClient,
    private val allowInsecure: Boolean,
    private val username: String,
    private val password: String,
    /** Credentials are only ever sent to this host. */
    private val credentialHost: String?,
    private val extraHeaders: Map<String, String> = emptyMap(),
) {

    fun parse(url: String): HttpUrl {
        val parsed = url.trim().toHttpUrlOrNull()
            ?: throw SourceException.InvalidLocation("Not a valid web address: $url")
        if (!parsed.isHttps && !allowInsecure) throw SourceException.InsecureConnection()
        return parsed
    }

    private fun request(url: HttpUrl, offset: Long = 0): Request {
        val builder = Request.Builder().url(url)
        if (username.isNotBlank() && url.host == credentialHost) {
            builder.header("Authorization", Credentials.basic(username, password))
        }
        extraHeaders.forEach { (name, value) -> if (url.host == credentialHost) builder.header(name, value) }
        if (offset > 0) builder.header("Range", "bytes=$offset-")
        return builder.build()
    }

    /** Fetches a small document (HTML or JSON) as text. */
    fun getText(url: HttpUrl): String = execute(url).use { response ->
        response.body.string()
    }

    /** Opens a (possibly huge) file as a stream starting at [offset]. The caller closes the stream. */
    fun open(url: HttpUrl, offset: Long): OpenedStream {
        val response = execute(url, offset)
        val body = response.body
        val length = body.contentLength().takeIf { it >= 0 }
        val partial = response.code == 206
        // With a partial answer the full size is after the slash of "Content-Range: bytes 10-99/100".
        val total = if (partial) {
            response.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: length?.plus(offset)
        } else {
            length
        }
        return OpenedStream(body.byteStream(), total, resumed = partial || offset == 0L)
    }

    private fun execute(url: HttpUrl, offset: Long = 0): Response {
        val response = try {
            client.newCall(request(url, offset)).execute()
        } catch (e: IOException) {
            throw SourceException.Network(e)
        }
        if (response.isSuccessful) return response
        response.close()
        throw when (response.code) {
            401, 403 -> SourceException.Unauthorized()
            404 -> SourceException.NotFound()
            else -> SourceException.BadResponse("The server answered ${response.code}")
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
