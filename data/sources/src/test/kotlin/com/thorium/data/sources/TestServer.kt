package com.thorium.data.sources

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/** A tiny local web server for tests, built on the JDK so no extra dependency is needed. */
class TestServer : AutoCloseable {

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val requests = mutableListOf<Request>()

    class Request(val path: String, val query: String?, val headers: Map<String, String>)

    val url: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.start()
    }

    /** Serves [body] at [path]. */
    fun text(path: String, body: String, contentType: String = "text/html", requireAuth: String? = null) {
        handle(path) { ex ->
            if (requireAuth != null && ex.requestHeaders.getFirst("Authorization") != requireAuth) {
                ex.sendResponseHeaders(401, -1)
            } else {
                val bytes = body.toByteArray()
                ex.responseHeaders.add("Content-Type", contentType)
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
        }
    }

    /** Serves [data] at [path], honouring `Range` requests only when [supportRange] is true. */
    fun file(path: String, data: ByteArray, supportRange: Boolean = true) {
        handle(path) { ex ->
            val range = ex.requestHeaders.getFirst("Range")
            if (supportRange && range != null) {
                val start = range.removePrefix("bytes=").substringBefore('-').toInt()
                val part = data.copyOfRange(start, data.size)
                ex.responseHeaders.add("Content-Range", "bytes $start-${data.size - 1}/${data.size}")
                ex.sendResponseHeaders(206, part.size.toLong())
                ex.responseBody.use { it.write(part) }
            } else {
                ex.sendResponseHeaders(200, data.size.toLong())
                ex.responseBody.use { it.write(data) }
            }
        }
    }

    fun handle(path: String, block: (HttpExchange) -> Unit) {
        server.createContext(path) { ex ->
            requests += Request(
                ex.requestURI.rawPath, ex.requestURI.rawQuery,
                ex.requestHeaders.entries.associate { it.key to it.value.first() },
            )
            block(ex)
        }
    }

    override fun close() = server.stop(0)
}
