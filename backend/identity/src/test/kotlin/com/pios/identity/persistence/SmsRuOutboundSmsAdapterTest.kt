package com.pios.identity.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.identity.application.NoOpOutboundSmsPort
import com.pios.identity.application.OutboundSmsDeliveryException
import com.pios.identity.application.OutboundSmsPort
import com.pios.identity.application.SmsSubmissionFailure
import com.pios.identity.application.SmsSubmissionState
import com.pios.identity.domain.Phone
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.springframework.boot.web.client.ClientHttpRequestFactories
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmsRuOutboundSmsAdapterTest {
    private val apiId = "test-api-id-secret"
    private val phone = Phone("+79990000001")
    private val code = "123456"

    @Test
    fun `posts a single SMS RU form request and accepts the recipient sms id`() {
        FakeSmsRuServer(200, accepted()).use { server ->
            adapter(server.baseUrl).sendVerificationCode(phone, code)

            val request = server.requests.remove()
            assertEquals("POST", request.method)
            assertEquals("/sms/send", request.path)
            assertTrue(request.contentType.startsWith("application/x-www-form-urlencoded"))
            assertEquals(
                mapOf(
                    "api_id" to apiId,
                    "to" to "79990000001",
                    "msg" to "123456 — код подтверждения телефона в PIOS",
                    "from" to "PIOS",
                    "json" to "1"
                ),
                request.form
            )
            assertTrue(server.requests.isEmpty()) // no automatic retry
        }
    }

    @Test
    fun `HTTP rejection and server error have safe distinct outcomes`() {
        for (status in listOf(400, 401, 429, 500)) {
            FakeSmsRuServer(status, "sensitive response $apiId $code ${phone.value}").use { server ->
                val failure = assertFailsWith<OutboundSmsDeliveryException> {
                    adapter(server.baseUrl).sendVerificationCode(phone, code)
                }
                assertEquals(if (status < 500) SmsSubmissionState.FAILED else SmsSubmissionState.UNKNOWN,
                    failure.state)
                assertEquals(if (status < 500) SmsSubmissionFailure.HTTP_REJECTED else SmsSubmissionFailure.SERVER_ERROR,
                    failure.failure)
                assertEquals(status, failure.httpStatus)
                assertSafe(failure)
                assertEquals(1, server.requests.size)
            }
        }
    }

    @Test
    fun `provider-level and recipient-level errors are rejected without exposing body`() {
        val responses = listOf(
            """{"status":"ERROR","status_code":200,"status_text":"$apiId $code"}""",
            """{"status":"OK","status_code":100,"sms":{"79990000001":{"status":"ERROR","status_code":201,"status_text":"${phone.value} $code"}}}"""
        )
        for (body in responses) {
            FakeSmsRuServer(200, body).use { server ->
                val failure = assertFailsWith<OutboundSmsDeliveryException> {
                    adapter(server.baseUrl).sendVerificationCode(phone, code)
                }
                assertEquals(SmsSubmissionState.FAILED, failure.state)
                assertEquals(SmsSubmissionFailure.PROVIDER_REJECTED, failure.failure)
                assertSafe(failure)
            }
        }
    }

    @Test
    fun `malformed and incomplete success responses are unknown`() {
        for (body in listOf("not JSON $apiId $code", "{}", "null",
            """{"status":"OK","status_code":100,"sms":{"79990000001":{"status":"OK","status_code":100}}}""")) {
            FakeSmsRuServer(200, body).use { server ->
                val failure = assertFailsWith<OutboundSmsDeliveryException> {
                    adapter(server.baseUrl).sendVerificationCode(phone, code)
                }
                assertEquals(SmsSubmissionState.UNKNOWN, failure.state)
                assertEquals(SmsSubmissionFailure.MALFORMED_RESPONSE, failure.failure)
                assertSafe(failure)
            }
        }
    }

    @Test
    fun `connect timeout is unknown and is never retried`() {
        val factory = org.springframework.http.client.ClientHttpRequestFactory { _, _ ->
            throw SocketTimeoutException("connect timed out $apiId $code")
        }
        val client = RestClient.builder().baseUrl("https://sms.ru").requestFactory(factory).build()
        val failure = assertFailsWith<OutboundSmsDeliveryException> {
            SmsRuOutboundSmsAdapter(client, ObjectMapper(), apiId, "PIOS").sendVerificationCode(phone, code)
        }
        assertEquals(SmsSubmissionState.UNKNOWN, failure.state)
        assertEquals(SmsSubmissionFailure.TRANSPORT_TIMEOUT, failure.failure)
        assertSafe(failure)
    }

    @Test
    fun `read timeout after a possible send remains unknown`() {
        ServerSocket(0).use { listeningSocket ->
            val failure = assertFailsWith<OutboundSmsDeliveryException> {
                adapter("http://127.0.0.1:${listeningSocket.localPort}", readTimeout = Duration.ofMillis(150))
                    .sendVerificationCode(phone, code)
            }
            assertEquals(SmsSubmissionState.UNKNOWN, failure.state)
            assertEquals(SmsSubmissionFailure.TRANSPORT_TIMEOUT, failure.failure)
            assertSafe(failure)
        }
    }

    @Test
    fun `connection refusal remains unknown and is never retried`() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val failure = assertFailsWith<OutboundSmsDeliveryException> {
            adapter("http://127.0.0.1:$closedPort").sendVerificationCode(phone, code)
        }
        assertEquals(SmsSubmissionState.UNKNOWN, failure.state)
        assertEquals(SmsSubmissionFailure.TRANSPORT_FAILURE, failure.failure)
        assertSafe(failure)
    }

    @Test
    fun `production wiring selects the SMS adapter and rejects missing credentials`() {
        val configuration = SmsRuConfiguration(apiId, "PIOS", "https://sms.ru", 2000, 5000)
        assertIs<SmsRuOutboundSmsAdapter>(configuration.outboundSmsPort(ObjectMapper()))
        assertTrue(SmsRuConfiguration::class.java.getMethod("outboundSmsPort", ObjectMapper::class.java)
            .isAnnotationPresent(Bean::class.java))
        assertFalse(NoOpOutboundSmsPort::class.java.isAnnotationPresent(Component::class.java))
        assertFailsWith<IllegalArgumentException> {
            SmsRuConfiguration("", "PIOS", "https://sms.ru", 2000, 5000).outboundSmsPort(ObjectMapper())
        }
        assertFailsWith<IllegalArgumentException> {
            SmsRuConfiguration(apiId, "", "https://sms.ru", 2000, 5000).outboundSmsPort(ObjectMapper())
        }
        assertFailsWith<IllegalArgumentException> {
            SmsRuConfiguration(apiId, "PIOS", "http://sms.ru", 2000, 5000).outboundSmsPort(ObjectMapper())
        }

        AnnotationConfigApplicationContext().use { context ->
            context.environment.propertySources.addFirst(MapPropertySource("sms-test", mapOf(
                "PIOS_SMS_API_ID" to apiId,
                "PIOS_SMS_SENDER" to "PIOS"
            )))
            context.beanFactory.registerSingleton("objectMapper", ObjectMapper())
            context.register(SmsRuConfiguration::class.java)
            context.refresh()
            val ports = context.getBeansOfType(OutboundSmsPort::class.java)
            assertEquals(1, ports.size)
            assertIs<SmsRuOutboundSmsAdapter>(ports.values.single())
        }
    }

    private fun accepted() =
        """{"status":"OK","status_code":100,"sms":{"79990000001":{"status":"OK","status_code":100,"sms_id":"000000-10000000"}}}"""

    private fun adapter(baseUrl: String, readTimeout: Duration = Duration.ofSeconds(5)): SmsRuOutboundSmsAdapter {
        val requestFactory = ClientHttpRequestFactories.get(
            ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(readTimeout)
        )
        return SmsRuOutboundSmsAdapter(
            RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build(),
            ObjectMapper(), apiId, "PIOS"
        )
    }

    private fun assertSafe(failure: OutboundSmsDeliveryException) {
        val text = failure.toString()
        assertFalse(text.contains(apiId))
        assertFalse(text.contains(code))
        assertFalse(text.contains(phone.value))
        assertNull(failure.cause)
    }
}

private data class CapturedSmsRequest(
    val method: String,
    val path: String,
    val contentType: String,
    val form: Map<String, String>
)

private class FakeSmsRuServer(private val status: Int, private val response: String) : AutoCloseable {
    val requests = LinkedBlockingQueue<CapturedSmsRequest>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/sms/send") { exchange -> handle(exchange) }
        start()
    }
    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
        val form = body.split('&').associate { item ->
            val parts = item.split('=', limit = 2)
            URLDecoder.decode(parts[0], Charsets.UTF_8) to URLDecoder.decode(parts.getOrElse(1) { "" }, Charsets.UTF_8)
        }
        requests.add(CapturedSmsRequest(exchange.requestMethod, exchange.requestURI.path,
            exchange.requestHeaders.getFirst("Content-Type") ?: "", form))
        val bytes = response.toByteArray(Charsets.UTF_8)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    override fun close() = server.stop(0)
}
