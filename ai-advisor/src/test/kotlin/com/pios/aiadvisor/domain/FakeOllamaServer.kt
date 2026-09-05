package com.pios.aiadvisor.domain

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue

/**
 * A minimal, dependency-free fake Ollama server for [OllamaProviderTest] —
 * built on the JDK's own `com.sun.net.httpserver.HttpServer`, mirroring
 * [FakeDeepSeekServer] exactly (same shape, same reasoning: no new test
 * dependency, no real external service called). **Never calls a real Ollama
 * instance** — this task's own explicit requirement — every test points
 * [OllamaProvider] at [baseUrl] instead of `http://localhost:11434`.
 *
 * One canned `(status, body)` response is queued per expected request via
 * [enqueue]; [lastAuthorizationHeader] and [lastRequestBody] let a test
 * assert what [OllamaProvider] actually sent, without this class ever
 * needing to understand Ollama's own request shape itself.
 */
class FakeOllamaServer {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val responses = ArrayBlockingQueue<Pair<Int, String>>(16)

    var lastRequestBody: String? = null
        private set
    var lastAuthorizationHeader: String? = null
        private set
    var requestCount = 0
        private set

    /** Set before [enqueue]ing a response to simulate a slow/hanging model (for timeout tests). */
    var delayMillis: Long = 0

    init {
        server.createContext("/") { exchange ->
            requestCount++
            lastAuthorizationHeader = exchange.requestHeaders.getFirst("Authorization")
            lastRequestBody = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            if (delayMillis > 0) {
                Thread.sleep(delayMillis)
            }
            val (status, body) = responses.poll() ?: (500 to "{}")
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    fun baseUrl(): String = "http://127.0.0.1:${server.address.port}"

    fun enqueue(status: Int, body: String) {
        responses.put(status to body)
    }

    fun stop() {
        server.stop(0)
    }
}
