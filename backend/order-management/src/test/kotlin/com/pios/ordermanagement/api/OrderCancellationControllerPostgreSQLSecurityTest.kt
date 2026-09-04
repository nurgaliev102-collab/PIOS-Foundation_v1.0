package com.pios.ordermanagement.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.domain.Order
import com.pios.ordermanagement.domain.OrderOrigin
import com.pios.ordermanagement.domain.OrderStatus
import com.pios.ordermanagement.persistence.PostgreSQLOrderRepository
import com.pios.ordermanagement.persistence.PostgreSQLTestDatabase
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 25 (Orders Cancellation & Driver Availability Security
 * Remediation): proves, against the real, isolated `pios_order_management_test`
 * PostgreSQL database -- not the in-memory repository
 * [OrderCancellationControllerTest] otherwise uses -- that a cancellation
 * attempt by a passenger other than the order's own persisted
 * [Order.origin] is rejected and never mutates the real, persisted row,
 * and that the owning passenger's own token still cancels it. Mirrors
 * `com.pios.dispatch.api.AssignmentControllerPostgreSQLSecurityTest`'s own
 * shape exactly (Task 23).
 *
 * Every identifier here is randomized ([UUID.randomUUID]), not a fixed
 * literal -- this repository's own known, repeated residual-test-data
 * collision pattern (`postgres-vertical-*`, `postgres-lifecycle-*`) is a
 * consequence of exactly the opposite choice in older tests; this file is
 * new, so it is written correctly from the start.
 */
class OrderCancellationControllerPostgreSQLSecurityTest {

    private val repository = PostgreSQLOrderRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val service = OrderLifecycleApplicationService(repository)
    private val secret = Base64.getEncoder().encodeToString("order-cancel-postgres-security-test-secret".toByteArray())
    private val controller = OrderCancellationController(service, repository, SessionTokenVerifier(secretBase64 = secret))

    private val objectMapper = ObjectMapper()

    private fun issueToken(sub: String, ttlSeconds: Long = 3600): String {
        val payloadNode = objectMapper.createObjectNode()
        payloadNode.put("sub", sub)
        payloadNode.putNull("drv")
        payloadNode.put("exp", Instant.now().plusSeconds(ttlSeconds).epochSecond)
        val encodedPayload = base64UrlEncode(objectMapper.writeValueAsBytes(payloadNode))
        val secretBytes = Base64.getDecoder().decode(secret)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        val encodedSignature = base64UrlEncode(mac.doFinal(encodedPayload.toByteArray(Charsets.UTF_8)))
        return "$encodedPayload.$encodedSignature"
    }

    private fun base64UrlEncode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun passengerToken(passengerReference: String): String = "Bearer " + issueToken(sub = passengerReference)

    @Test
    fun `cancelling through REST as a different passenger, backed by PostgreSQL, is rejected and never mutates the real order`() {
        val owner = "order-sec-passenger-owner-${UUID.randomUUID()}"
        val submitted = Order.submit(OrderOrigin(owner))
        repository.save(submitted.order)

        val response = controller.cancelOrder(
            submitted.order.id.value,
            authorization = passengerToken("order-sec-passenger-attacker-${UUID.randomUUID()}")
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(OrderStatus.SUBMITTED, repository.findById(submitted.order.id)?.status)
    }

    @Test
    fun `cancelling through REST with no Authorization header, backed by PostgreSQL, does not mutate the real order`() {
        val owner = "order-sec-passenger-${UUID.randomUUID()}"
        val submitted = Order.submit(OrderOrigin(owner))
        repository.save(submitted.order)

        val response = controller.cancelOrder(submitted.order.id.value)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(OrderStatus.SUBMITTED, repository.findById(submitted.order.id)?.status)
    }

    @Test
    fun `the owning passenger's own token still cancels the real order in PostgreSQL`() {
        val owner = "order-sec-passenger-${UUID.randomUUID()}"
        val submitted = Order.submit(OrderOrigin(owner))
        repository.save(submitted.order)

        val response = controller.cancelOrder(submitted.order.id.value, authorization = passengerToken(owner))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(OrderStatus.CANCELLED, repository.findById(submitted.order.id)?.status)
    }
}
