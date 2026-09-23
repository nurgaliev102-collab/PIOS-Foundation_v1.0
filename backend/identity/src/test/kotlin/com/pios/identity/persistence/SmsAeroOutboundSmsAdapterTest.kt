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
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.core.env.MapPropertySource
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmsAeroOutboundSmsAdapterTest {
    private val login = "fixture@example.invalid"
    private val fakeKey = "fixture-only-key"
    private val sender = "FixtureSign"
    private val phone = Phone("+79990000001")
    private val code = "123456"

    @Test
    fun `posts one normal send with Basic Auth and accepts provider message id`() {
        FakeSmsAeroServer(200, accepted()).use { server ->
            assertEquals(1L, adapter(server.baseUrl).sendVerificationCode(phone, code))

            val request = server.requests.remove()
            assertEquals("POST", request.method)
            assertEquals("/v2/sms/send", request.path)
            assertTrue(request.contentType.startsWith("application/x-www-form-urlencoded"))
            assertTrue(request.accept.contains("application/json"))
            assertEquals(
                "Basic " + Base64.getEncoder().encodeToString("$login:$fakeKey".toByteArray(StandardCharsets.UTF_8)),
                request.authorization
            )
            assertEquals(mapOf(
                "number" to "79990000001",
                "sign" to sender,
                "text" to "123456 — код подтверждения телефона в PIOS"
            ), request.form)
            assertTrue(server.requests.isEmpty()) // no automatic retry
        }
    }

    @Test
    fun `HTTP 4xx rejection and 5xx uncertainty do not expose provider response`() {
        for (status in listOf(400, 401, 429, 500)) {
            FakeSmsAeroServer(status, "sensitive response $fakeKey $code ${phone.value}").use { server ->
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
    fun `explicit API failure and rejected message status are failed`() {
        val responses = listOf(
            """{"success":false,"data":null,"message":"$fakeKey $code ${phone.value}"}""",
            """{"success":true,"data":{"id":1,"number":"79990000001","status":2}}""",
            """{"success":true,"data":{"id":1,"number":"79990000001","status":6}}"""
        )
        for (body in responses) {
            FakeSmsAeroServer(200, body).use { server ->
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
    fun `moderation and queued statuses mean accepted for processing`() {
        for (status in listOf(0, 1, 3, 4, 8)) {
            FakeSmsAeroServer(200, accepted(status)).use { server ->
                adapter(server.baseUrl).sendVerificationCode(phone, code)
                assertEquals(1, server.requests.size)
            }
        }
    }

    @Test
    fun `malformed or incomplete success remains unknown`() {
        val responses = listOf(
            "not JSON $fakeKey $code", "{}", "null",
            "", """{"success":true,"data":[]}""", """{"success":true,"data":"unexpected"}""",
            """{"success":true,"data":null}""", """{"success":true,"data":{}}""",
            """{"success":true,"data":{"number":"79990000001","status":0}}""",
            """{"success":true,"data":{"id":"1","number":"79990000001","status":0}}""",
            """{"success":true,"data":{"id":1,"number":"79990000002","status":0}}""",
            """{"success":true,"data":{"id":1,"number":"79990000001","status":99}}"""
        )
        for (body in responses) {
            FakeSmsAeroServer(200, body).use { server ->
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
    fun `response diagnostics classify every response validation branch without response values`() {
        val cases = listOf(
            DiagnosticCase("not JSON $fakeKey $code", SmsAeroResponseDiagnosticReason.NON_JSON_BODY),
            DiagnosticCase("[]", SmsAeroResponseDiagnosticReason.ROOT_NOT_OBJECT),
            DiagnosticCase("{}", SmsAeroResponseDiagnosticReason.SUCCESS_MISSING),
            DiagnosticCase("""{"success":"true"}""", SmsAeroResponseDiagnosticReason.SUCCESS_NOT_BOOLEAN),
            DiagnosticCase("""{"success":false}""", SmsAeroResponseDiagnosticReason.SUCCESS_FALSE, SmsSubmissionState.FAILED),
            DiagnosticCase("""{"success":true}""", SmsAeroResponseDiagnosticReason.DATA_MISSING),
            DiagnosticCase("""{"success":true,"data":[]}""", SmsAeroResponseDiagnosticReason.DATA_NOT_OBJECT),
            DiagnosticCase("""{"success":true,"data":"unexpected"}""", SmsAeroResponseDiagnosticReason.DATA_NOT_OBJECT),
            DiagnosticCase("""{"success":true,"data":null}""", SmsAeroResponseDiagnosticReason.DATA_NOT_OBJECT),
            DiagnosticCase("""{"success":true,"data":{}}""", SmsAeroResponseDiagnosticReason.MESSAGE_ID_MISSING),
            DiagnosticCase("""{"success":true,"data":{"id":0}}""", SmsAeroResponseDiagnosticReason.MESSAGE_ID_INVALID),
            DiagnosticCase("""{"success":true,"data":{"id":"1"}}""", SmsAeroResponseDiagnosticReason.MESSAGE_ID_INVALID),
            DiagnosticCase("""{"success":true,"data":{"id":9223372036854775808}}""", SmsAeroResponseDiagnosticReason.MESSAGE_ID_INVALID),
            DiagnosticCase("""{"success":true,"data":{"id":1}}""", SmsAeroResponseDiagnosticReason.NUMBER_MISSING),
            DiagnosticCase("""{"success":true,"data":{"id":1,"number":null}}""", SmsAeroResponseDiagnosticReason.NUMBER_MISSING),
            DiagnosticCase("""{"success":true,"data":{"id":1,"number":"79990000002"}}""", SmsAeroResponseDiagnosticReason.NUMBER_MISMATCH),
            DiagnosticCase("""{"success":true,"data":{"id":1,"number":"79990000001"}}""", SmsAeroResponseDiagnosticReason.STATUS_MISSING),
            DiagnosticCase("""{"success":true,"data":{"id":1,"number":"79990000001","status":null}}""", SmsAeroResponseDiagnosticReason.STATUS_MISSING),
            DiagnosticCase("""{"success":true,"data":{"id":1,"number":"79990000001","status":"0"}}""", SmsAeroResponseDiagnosticReason.STATUS_INVALID),
            DiagnosticCase("""{"success":true,"data":{"id":1,"number":"79990000001","status":4294967296}}""", SmsAeroResponseDiagnosticReason.STATUS_INVALID),
            DiagnosticCase("""{"success":true,"data":{"id":1,"number":"79990000001","status":99}}""", SmsAeroResponseDiagnosticReason.STATUS_INVALID)
        )

        for (case in cases) {
            FakeSmsAeroServer(200, case.body).use { server ->
                val diagnostics = mutableListOf<SmsAeroResponseDiagnostic>()
                val failure = assertFailsWith<OutboundSmsDeliveryException> {
                    adapter(server.baseUrl, diagnosticSink = diagnostics::add).sendVerificationCode(phone, code)
                }
                assertEquals(case.state, failure.state, case.reason.name)
                val diagnostic = diagnostics.single()
                assertEquals(200, diagnostic.httpStatus)
                assertEquals(case.reason, diagnostic.reason)
                assertEquals(case.body.toByteArray(StandardCharsets.UTF_8).size, diagnostic.bodyLength)
                assertEquals("application/json", diagnostic.contentType)
                assertSafe(diagnostic.toString())
            }
        }
    }

    @Test
    fun `controlled live SMS Aero data object fixture is accepted and exposes only structural diagnostics`() {
        FakeSmsAeroServer(200, actualDataObjectResponse()).use { server ->
            val diagnostics = mutableListOf<SmsAeroResponseDiagnostic>()
            val providerMessageId = adapter(server.baseUrl, diagnosticSink = diagnostics::add)
                .sendVerificationCode(phone, code)

            assertEquals(123456789L, providerMessageId)
            assertEquals(1, server.requests.size) // Fake server only; no real SMS Aero call.
            val diagnostic = diagnostics.single()
            assertEquals(SmsAeroResponseDiagnosticReason.RESPONSE_VALID, diagnostic.reason)
            assertEquals("application/json", diagnostic.contentType)
            assertEquals(listOf("data", "message", "success"), diagnostic.rootKeys)
            assertEquals(listOf("extendStatus", "from", "id", "number", "status", "text"), diagnostic.itemKeys)
            assertEquals("INTEGER", diagnostic.idType)
            assertTrue(diagnostic.numberPresent == true)
            assertEquals("INTEGER", diagnostic.statusType)
            assertTrue(diagnostic.statusValueAllowed == true)
            assertSafe(diagnostic.toString())
        }
    }

    @Test
    fun `Russian eight-prefix recipient echo is correlated without accepting a different number`() {
        val body = """{"success":true,"data":{"id":77,"number":"89990000001","status":0},"message":null}"""
        FakeSmsAeroServer(200, body).use { server ->
            assertEquals(77L, adapter(server.baseUrl).sendVerificationCode(phone, code))
            assertEquals(1, server.requests.size)
        }
    }

    @Test
    fun `connect timeout remains unknown and is not retried`() {
        val attempts = AtomicInteger()
        val factory = org.springframework.http.client.ClientHttpRequestFactory { _, _ ->
            attempts.incrementAndGet()
            throw SocketTimeoutException("connect timed out $fakeKey $code")
        }
        val client = RestClient.builder().baseUrl("https://gate.smsaero.ru")
            .requestFactory(factory).build()
        val failure = assertFailsWith<OutboundSmsDeliveryException> {
            SmsAeroOutboundSmsAdapter(client, ObjectMapper(), sender).sendVerificationCode(phone, code)
        }
        assertEquals(SmsSubmissionState.UNKNOWN, failure.state)
        assertEquals(SmsSubmissionFailure.TRANSPORT_TIMEOUT, failure.failure)
        assertEquals(1, attempts.get())
        assertSafe(failure)
    }

    @Test
    fun `read timeout after a possible send remains unknown`() {
        ServerSocket(0).use { listeningSocket ->
            val failure = assertFailsWith<OutboundSmsDeliveryException> {
                adapter("http://127.0.0.1:${listeningSocket.localPort}", Duration.ofMillis(150))
                    .sendVerificationCode(phone, code)
            }
            assertEquals(SmsSubmissionState.UNKNOWN, failure.state)
            assertEquals(SmsSubmissionFailure.TRANSPORT_TIMEOUT, failure.failure)
            assertSafe(failure)
        }
    }

    @Test
    fun `connection refusal remains unknown`() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val failure = assertFailsWith<OutboundSmsDeliveryException> {
            adapter("http://127.0.0.1:$closedPort").sendVerificationCode(phone, code)
        }
        assertEquals(SmsSubmissionState.UNKNOWN, failure.state)
        assertEquals(SmsSubmissionFailure.TRANSPORT_FAILURE, failure.failure)
        assertSafe(failure)
    }

    @Test
    fun `production wiring is SMS Aero only and fails safely without credentials`() {
        val configuration = SmsAeroConfiguration(login, fakeKey, sender, "https://gate.smsaero.ru", 2000, 5000)
        assertIs<SmsAeroOutboundSmsAdapter>(configuration.outboundSmsPort(ObjectMapper()))
        assertTrue(SmsAeroConfiguration::class.java.getMethod("outboundSmsPort", ObjectMapper::class.java)
            .isAnnotationPresent(Bean::class.java))
        assertFalse(NoOpOutboundSmsPort::class.java.isAnnotationPresent(Component::class.java))
        for (missing in listOf(
            SmsAeroConfiguration("", fakeKey, sender, "https://gate.smsaero.ru", 2000, 5000),
            SmsAeroConfiguration(login, "", sender, "https://gate.smsaero.ru", 2000, 5000),
            SmsAeroConfiguration(login, fakeKey, "", "https://gate.smsaero.ru", 2000, 5000),
            SmsAeroConfiguration(login, fakeKey, sender, "https://sms.ru", 2000, 5000),
            SmsAeroConfiguration(login, fakeKey, sender, "http://gate.smsaero.ru", 2000, 5000)
        )) {
            val error = assertFailsWith<IllegalArgumentException> { missing.outboundSmsPort(ObjectMapper()) }
            assertFalse(error.toString().contains(fakeKey))
        }

        AnnotationConfigApplicationContext().use { context ->
            context.environment.propertySources.addFirst(MapPropertySource("sms-test", mapOf(
                "PIOS_SMS_LOGIN" to login,
                "PIOS_SMS_API_KEY" to fakeKey,
                "PIOS_SMS_SENDER" to sender
            )))
            context.beanFactory.registerSingleton("objectMapper", ObjectMapper())
            context.register(SmsAeroConfiguration::class.java)
            context.refresh()
            val ports = context.getBeansOfType(OutboundSmsPort::class.java)
            assertEquals(1, ports.size)
            assertIs<SmsAeroOutboundSmsAdapter>(ports.values.single())
        }
    }

    @Test
    fun `Spring context fails closed when login is absent`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.propertySources.addFirst(MapPropertySource("sms-missing-login", mapOf(
                "PIOS_SMS_LOGIN" to "",
                "PIOS_SMS_API_KEY" to fakeKey,
                "PIOS_SMS_SENDER" to sender
            )))
            context.beanFactory.registerSingleton("objectMapper", ObjectMapper())
            context.register(SmsAeroConfiguration::class.java)
            val error = assertFailsWith<Exception> { context.refresh() }
            assertTrue(error.toString().contains("PIOS_SMS_LOGIN"))
            assertFalse(error.toString().contains(fakeKey))
        }
    }

    private fun accepted(status: Int = 0) =
        """{"success":true,"data":{"id":1,"number":"79990000001","status":$status},"message":null}"""

    private fun actualDataObjectResponse(): String = requireNotNull(
        javaClass.getResource("/sms-aero/send-success-data-object.json")
    ).readText().replace("TEST_NUMBER", phone.value.removePrefix("+"))

    private fun adapter(
        baseUrl: String,
        readTimeout: Duration = Duration.ofSeconds(5),
        diagnosticSink: ((SmsAeroResponseDiagnostic) -> Unit)? = null
    ): SmsAeroOutboundSmsAdapter {
        val requestFactory = ClientHttpRequestFactories.get(
            ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(readTimeout)
        )
        val client = RestClient.builder().baseUrl(baseUrl)
            .defaultHeaders { it.setBasicAuth(login, fakeKey, StandardCharsets.UTF_8) }
            .requestFactory(requestFactory).build()
        return if (diagnosticSink == null) {
            SmsAeroOutboundSmsAdapter(client, ObjectMapper(), sender)
        } else {
            SmsAeroOutboundSmsAdapter.withDiagnosticSink(client, ObjectMapper(), sender, diagnosticSink)
        }
    }

    private fun assertSafe(failure: OutboundSmsDeliveryException) {
        assertSafe(failure.toString())
        assertNull(failure.cause)
    }

    private fun assertSafe(text: String) {
        val authorization = "Basic " + Base64.getEncoder()
            .encodeToString("$login:$fakeKey".toByteArray(StandardCharsets.UTF_8))
        assertFalse(text.contains(fakeKey))
        assertFalse(text.contains(authorization))
        assertFalse(text.contains(code))
        assertFalse(text.contains(phone.value))
    }

    private data class DiagnosticCase(
        val body: String,
        val reason: SmsAeroResponseDiagnosticReason,
        val state: SmsSubmissionState = SmsSubmissionState.UNKNOWN
    )
}

private data class CapturedSmsRequest(
    val method: String,
    val path: String,
    val contentType: String,
    val accept: String,
    val authorization: String,
    val form: Map<String, String>
)

private class FakeSmsAeroServer(
    private val status: Int,
    private val response: String,
    private val responseContentType: String = "application/json"
) : AutoCloseable {
    val requests = LinkedBlockingQueue<CapturedSmsRequest>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/v2/sms/send") { exchange -> handle(exchange) }
        start()
    }
    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
        val form = body.split('&').associate { item ->
            val parts = item.split('=', limit = 2)
            URLDecoder.decode(parts[0], Charsets.UTF_8) to URLDecoder.decode(parts.getOrElse(1) { "" }, Charsets.UTF_8)
        }
        requests.add(CapturedSmsRequest(
            exchange.requestMethod, exchange.requestURI.path,
            exchange.requestHeaders.getFirst("Content-Type") ?: "",
            exchange.requestHeaders.getFirst("Accept") ?: "",
            exchange.requestHeaders.getFirst("Authorization") ?: "", form
        ))
        val bytes = response.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", responseContentType)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    override fun close() = server.stop(0)
}
