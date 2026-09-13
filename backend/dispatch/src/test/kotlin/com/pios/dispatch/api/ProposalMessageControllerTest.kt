package com.pios.dispatch.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.ArriveAssignmentCommand
import com.pios.dispatch.application.CompleteAssignmentCommand
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.application.ProposalAssignmentOrchestrationService
import com.pios.dispatch.application.ProposalMessagingApplicationService
import com.pios.dispatch.application.StartAssignmentCommand
import com.pios.dispatch.domain.AssignmentStatus
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.persistence.InMemoryAssignmentRepository
import com.pios.dispatch.persistence.InMemoryProposalMessageRepository
import com.pios.dispatch.persistence.InMemoryProposalRepository
import com.pios.dispatch.persistence.InMemoryTripRepository
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Constructs [ProposalMessageController] directly, with a real
 * [ProposalMessagingApplicationService]/[InMemoryProposalRepository]/
 * [InMemoryAssignmentRepository]/[InMemoryProposalMessageRepository], no
 * Spring MVC context -- mirrors [ProposalControllerTest]'s own
 * constructor-based testing convention and token-minting helpers exactly
 * (same secret shape, same [issueToken]/[bearer]/[basicHeader] pattern).
 *
 * Covers, end to end, every scenario Minimal In-Ride Messaging (Product
 * Cycle) names explicitly: passenger send, driver receive, driver reply,
 * passenger receive, correct-order/proposal scoping, cross-proposal
 * isolation, behavior at every real lifecycle status (OPEN through
 * COMPLETED), and error/retry handling.
 */
class ProposalMessageControllerTest {

    private val proposalRepository = InMemoryProposalRepository()
    private val proposalService = ProposalApplicationService(proposalRepository)
    private val assignmentRepository = InMemoryAssignmentRepository()
    private val tripRepository = InMemoryTripRepository()
    private val assignmentService = DispatchAssignmentApplicationService(assignmentRepository, tripRepository = tripRepository)
    private val orchestrationService = ProposalAssignmentOrchestrationService(proposalRepository, proposalService, assignmentService)
    private val messageRepository = InMemoryProposalMessageRepository()
    private val messagingService = ProposalMessagingApplicationService(proposalRepository, assignmentRepository, tripRepository, messageRepository)

    private val secret = Base64.getEncoder().encodeToString("proposal-message-controller-test-secret".toByteArray())
    private val sessionTokenVerifier = SessionTokenVerifier(secretBase64 = secret)
    private val ownerSalt = "proposal-message-controller-owner-salt".toByteArray()
    private val ownerIterations = 1000
    private val ownerPassword = "owner-password"
    private val ownerCredentialGate = OwnerCredentialGate(
        configuredUsername = "owner",
        configuredPasswordHash = Base64.getEncoder().encodeToString(deriveKey(ownerPassword, ownerSalt, ownerIterations)),
        configuredPasswordSalt = Base64.getEncoder().encodeToString(ownerSalt),
        iterations = ownerIterations,
        failureDelayMillis = 0,
        maxFailuresPerWindow = 1000,
        windowMillis = 900_000
    )
    private val controller = ProposalMessageController(messagingService, proposalRepository, sessionTokenVerifier, ownerCredentialGate)
    private val proposalController = ProposalController(proposalService, orchestrationService, proposalRepository, sessionTokenVerifier, ownerCredentialGate)

    // --- Token minting test helper (mirrors ProposalControllerTest's own) ---

    private val objectMapper = ObjectMapper()

    private fun issueToken(sub: String, drv: String? = null, ttlSeconds: Long = 3600): String {
        val payloadNode = objectMapper.createObjectNode()
        payloadNode.put("sub", sub)
        if (drv == null) payloadNode.putNull("drv") else payloadNode.put("drv", drv)
        payloadNode.put("exp", Instant.now().plusSeconds(ttlSeconds).epochSecond)
        val encodedPayload = base64UrlEncode(objectMapper.writeValueAsBytes(payloadNode))
        val secretBytes = Base64.getDecoder().decode(secret)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        val encodedSignature = base64UrlEncode(mac.doFinal(encodedPayload.toByteArray(Charsets.UTF_8)))
        return "$encodedPayload.$encodedSignature"
    }

    private fun base64UrlEncode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun bearer(token: String): String = "Bearer $token"

    private fun basicHeader(username: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int, keyLengthBits: Int = 256): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun driverToken(driverId: String): String = bearer(issueToken(sub = "$driverId-identity", drv = driverId))
    private fun passengerToken(passengerId: String = "passenger-test"): String = bearer(issueToken(sub = passengerId))
    private fun ownerAuth(): String = basicHeader("owner", ownerPassword)

    /** Creates a real, OPEN proposal via [ProposalController] itself -- the same real path a message thread is scoped to. */
    private fun createProposal(orderId: String, driverId: String, passengerId: String = "passenger-test"): String =
        assertNotNull(
            proposalController.createProposal(
                ProposeDriverRequest(orderId, driverId, false, passengerId),
                authorization = passengerToken(passengerId)
            ).body
        ).proposalId

    /**
     * Drives a proposal all the way to a COMPLETED ride via the real
     * [DispatchAssignmentApplicationService] -- `arriveAssignment`/
     * `startAssignment`/`completeAssignment`, the same real production
     * path `AssignmentController`'s own REST endpoints call. Deliberately
     * **not** `assignment.arrive()/start()/complete()` + a manual save:
     * since ADR-063/Task 12 ("Ride-progress convergence onto Trip"), that
     * shortcut mutates [Assignment] directly, which the real service no
     * longer does at all -- it transitions the connected
     * [com.pios.dispatch.domain.Trip] instead. A live HTTP E2E check
     * (2026-09-13) proved the two are not equivalent: the shortcut made
     * every "closes after COMPLETED" test here pass while the real HTTP
     * path stayed open indefinitely. Going through the real service is
     * what [ProposalMessagingApplicationService.isMessagingOpen] actually
     * observes now.
     */
    private fun completeRide(proposalId: String, driverId: String, orderId: String) {
        proposalController.acceptProposal(proposalId, authorization = driverToken(driverId))
        val assignment = assertNotNull(assignmentRepository.findByOrder(OrderReference(orderId)).firstOrNull())
        assignmentService.arriveAssignment(ArriveAssignmentCommand(assignment.id))
        assignmentService.startAssignment(StartAssignmentCommand(assignment.id))
        assignmentService.completeAssignment(CompleteAssignmentCommand(assignment.id))
    }

    // --- Passenger sends, driver receives ---

    @Test
    fun `a passenger can send a message on their own proposal`() {
        val proposalId = createProposal("order-1", "driver-1")

        val response = controller.sendMessage(proposalId, SendProposalMessageRequest("Буду у подъезда"), authorization = passengerToken())

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals("PASSENGER", body.senderRole)
        assertEquals("Буду у подъезда", body.body)
        assertTrue(body.id.isNotBlank())
        assertNotNull(body.sentAt)
    }

    @Test
    fun `the driver of that same proposal sees the passenger's message`() {
        val proposalId = createProposal("order-2", "driver-2")
        controller.sendMessage(proposalId, SendProposalMessageRequest("Второй подъезд, код 1234"), authorization = passengerToken())

        val response = controller.listMessages(proposalId, authorization = driverToken("driver-2"))

        assertEquals(HttpStatus.OK, response.statusCode)
        val messages = assertNotNull(response.body)
        assertEquals(1, messages.size)
        assertEquals("PASSENGER", messages[0].senderRole)
        assertEquals("Второй подъезд, код 1234", messages[0].body)
    }

    // --- Driver replies, passenger receives ---

    @Test
    fun `the driver can reply, and the passenger sees the reply`() {
        val proposalId = createProposal("order-3", "driver-3")
        controller.sendMessage(proposalId, SendProposalMessageRequest("Где вас найти?"), authorization = passengerToken())

        val replyResponse = controller.sendMessage(proposalId, SendProposalMessageRequest("Уже еду, буду через 5 минут"), authorization = driverToken("driver-3"))
        assertEquals(HttpStatus.CREATED, replyResponse.statusCode)
        assertEquals("DRIVER", assertNotNull(replyResponse.body).senderRole)

        val passengerView = assertNotNull(controller.listMessages(proposalId, authorization = passengerToken()).body)
        assertEquals(2, passengerView.size)
        assertEquals("PASSENGER", passengerView[0].senderRole)
        assertEquals("DRIVER", passengerView[1].senderRole)
        assertEquals("Уже еду, буду через 5 минут", passengerView[1].body)
    }

    @Test
    fun `messages come back oldest first, in the order they were sent`() {
        val proposalId = createProposal("order-order", "driver-order")
        controller.sendMessage(proposalId, SendProposalMessageRequest("Первое"), authorization = passengerToken())
        controller.sendMessage(proposalId, SendProposalMessageRequest("Второе"), authorization = driverToken("driver-order"))
        controller.sendMessage(proposalId, SendProposalMessageRequest("Третье"), authorization = passengerToken())

        val messages = assertNotNull(controller.listMessages(proposalId, authorization = passengerToken()).body)

        assertEquals(listOf("Первое", "Второе", "Третье"), messages.map { it.body })
    }

    // --- Correct order/proposal scoping; cross-proposal isolation ---

    @Test
    fun `messages from a different proposal never appear in this one's own thread`() {
        val proposalA = createProposal("order-a", "driver-a", passengerId = "passenger-a")
        val proposalB = createProposal("order-b", "driver-b", passengerId = "passenger-b")
        controller.sendMessage(proposalA, SendProposalMessageRequest("Сообщение по заказу A"), authorization = passengerToken("passenger-a"))
        controller.sendMessage(proposalB, SendProposalMessageRequest("Сообщение по заказу B"), authorization = passengerToken("passenger-b"))

        val threadA = assertNotNull(controller.listMessages(proposalA, authorization = passengerToken("passenger-a")).body)
        val threadB = assertNotNull(controller.listMessages(proposalB, authorization = passengerToken("passenger-b")).body)

        assertEquals(listOf("Сообщение по заказу A"), threadA.map { it.body })
        assertEquals(listOf("Сообщение по заказу B"), threadB.map { it.body })
    }

    @Test
    fun `a declined-then-reproposed order's two proposals never share a thread`() {
        // The same order, declined by one driver, then proposed to a second --
        // exactly the scenario ProposalMessage.kt's own KDoc names: a later,
        // different proposal for the same order must never inherit an
        // earlier, unrelated conversation.
        val firstProposal = createProposal("order-reproposed", "driver-declines")
        controller.sendMessage(firstProposal, SendProposalMessageRequest("Пожелание для первого водителя"), authorization = passengerToken())
        proposalController.declineProposal(firstProposal, authorization = driverToken("driver-declines"))

        val secondProposal = createProposal("order-reproposed", "driver-accepts")

        val secondThread = assertNotNull(controller.listMessages(secondProposal, authorization = driverToken("driver-accepts")).body)
        assertEquals(emptyList(), secondThread)
    }

    // --- Lifecycle: OPEN through COMPLETED ---

    @Test
    fun `a passenger can message before the driver has named a price -- OPEN`() {
        val proposalId = createProposal("order-open", "driver-open")

        val response = controller.sendMessage(proposalId, SendProposalMessageRequest("Ждите, пожалуйста, у входа"), authorization = passengerToken())

        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun `messaging stays open through ACCEPTED, ARRIVED, and IN_PROGRESS`() {
        val proposalId = createProposal("order-progress", "driver-progress")
        proposalController.acceptProposal(proposalId, authorization = driverToken("driver-progress"))
        val assignment = assertNotNull(assignmentRepository.findByOrder(OrderReference("order-progress")).firstOrNull())

        assertEquals(HttpStatus.CREATED, controller.sendMessage(proposalId, SendProposalMessageRequest("Принято"), authorization = passengerToken()).statusCode)

        // Real service, real Trip -- not a manual assignment.arrive()/save()
        // (see [completeRide]'s own KDoc for why that shortcut no longer
        // reflects what the real HTTP path actually does).
        assignmentService.arriveAssignment(ArriveAssignmentCommand(assignment.id))
        assertEquals(HttpStatus.CREATED, controller.sendMessage(proposalId, SendProposalMessageRequest("Уже прибыл"), authorization = driverToken("driver-progress")).statusCode)

        assignmentService.startAssignment(StartAssignmentCommand(assignment.id))
        assertEquals(HttpStatus.CREATED, controller.sendMessage(proposalId, SendProposalMessageRequest("Едем"), authorization = passengerToken()).statusCode)
    }

    @Test
    fun `sending a message after COMPLETED is rejected with 409`() {
        val proposalId = createProposal("order-completed", "driver-completed")
        completeRide(proposalId, "driver-completed", "order-completed")

        val response = controller.sendMessage(proposalId, SendProposalMessageRequest("Спасибо!"), authorization = passengerToken())

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `existing messages remain fully readable after COMPLETED -- history is never hidden`() {
        val proposalId = createProposal("order-history", "driver-history")
        controller.sendMessage(proposalId, SendProposalMessageRequest("До поездки"), authorization = passengerToken())
        completeRide(proposalId, "driver-history", "order-history")

        val messages = assertNotNull(controller.listMessages(proposalId, authorization = passengerToken()).body)

        assertEquals(listOf("До поездки"), messages.map { it.body })
    }

    @Test
    fun `sending a message on a declined proposal is rejected with 409`() {
        val proposalId = createProposal("order-declined", "driver-declined")
        proposalController.declineProposal(proposalId, authorization = driverToken("driver-declined"))

        val response = controller.sendMessage(proposalId, SendProposalMessageRequest("Ещё здесь?"), authorization = passengerToken())

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `sending a message on a lapsed proposal is rejected with 409`() {
        val proposalId = createProposal("order-lapsed", "driver-lapsed")
        proposalController.lapseProposal(proposalId, authorization = ownerAuth())

        val response = controller.sendMessage(proposalId, SendProposalMessageRequest("Всё ещё ждём"), authorization = passengerToken())

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    // --- Errors ---

    @Test
    fun `sending without a valid credential returns 401`() {
        val proposalId = createProposal("order-401", "driver-401")

        val response = controller.sendMessage(proposalId, SendProposalMessageRequest("Привет"), authorization = null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `sending on an unknown proposal returns 404`() {
        val response = controller.sendMessage("does-not-exist", SendProposalMessageRequest("Привет"), authorization = passengerToken())

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `sending as neither this proposal's passenger nor its driver returns 403`() {
        val proposalId = createProposal("order-403", "driver-403")

        val response = controller.sendMessage(proposalId, SendProposalMessageRequest("Я тут ни при чём"), authorization = passengerToken("someone-else"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `a blank message body is rejected with 400`() {
        val proposalId = createProposal("order-blank", "driver-blank")

        val response = controller.sendMessage(proposalId, SendProposalMessageRequest("   "), authorization = passengerToken())

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a message longer than the length limit is rejected with 400`() {
        val proposalId = createProposal("order-too-long", "driver-too-long")

        val response = controller.sendMessage(proposalId, SendProposalMessageRequest("a".repeat(301)), authorization = passengerToken())

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `listing messages without a valid credential returns 401`() {
        val proposalId = createProposal("order-list-401", "driver-list-401")

        val response = controller.listMessages(proposalId, authorization = null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `listing messages as neither participant returns 403`() {
        val proposalId = createProposal("order-list-403", "driver-list-403")

        val response = controller.listMessages(proposalId, authorization = passengerToken("someone-else"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `the owner credential can read any proposal's own messages`() {
        val proposalId = createProposal("order-owner-read", "driver-owner-read")
        controller.sendMessage(proposalId, SendProposalMessageRequest("Для истории"), authorization = passengerToken())

        val response = controller.listMessages(proposalId, authorization = ownerAuth())

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1, assertNotNull(response.body).size)
    }

    // --- Retry / resubmission ---

    @Test
    fun `sending the same text twice after a failed attempt creates two independent messages, not a silent no-op`() {
        val proposalId = createProposal("order-retry", "driver-retry")

        val first = controller.sendMessage(proposalId, SendProposalMessageRequest("Уточните, пожалуйста"), authorization = passengerToken())
        val second = controller.sendMessage(proposalId, SendProposalMessageRequest("Уточните, пожалуйста"), authorization = passengerToken())

        assertEquals(HttpStatus.CREATED, first.statusCode)
        assertEquals(HttpStatus.CREATED, second.statusCode)
        assertTrue(assertNotNull(first.body).id != assertNotNull(second.body).id)
        val messages = assertNotNull(controller.listMessages(proposalId, authorization = passengerToken()).body)
        assertEquals(2, messages.size)
    }

    @Test
    fun `retrying after messaging closes still fails -- no stuck submission ever silently succeeds`() {
        val proposalId = createProposal("order-retry-closed", "driver-retry-closed")
        completeRide(proposalId, "driver-retry-closed", "order-retry-closed")

        val attempt1 = controller.sendMessage(proposalId, SendProposalMessageRequest("Ещё раз"), authorization = passengerToken())
        val attempt2 = controller.sendMessage(proposalId, SendProposalMessageRequest("Ещё раз"), authorization = passengerToken())

        assertEquals(HttpStatus.CONFLICT, attempt1.statusCode)
        assertEquals(HttpStatus.CONFLICT, attempt2.statusCode)
    }
}
