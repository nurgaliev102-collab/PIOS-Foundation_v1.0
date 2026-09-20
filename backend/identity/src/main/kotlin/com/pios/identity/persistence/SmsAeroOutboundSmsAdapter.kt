package com.pios.identity.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.identity.application.OutboundSmsDeliveryException
import com.pios.identity.application.OutboundSmsPort
import com.pios.identity.application.SmsSubmissionFailure
import com.pios.identity.application.SmsSubmissionState
import com.pios.identity.domain.Phone
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.SocketTimeoutException

/** SMS Aero normal send. A successful response means accepted, not delivered. */
class SmsAeroOutboundSmsAdapter(
    private val client: RestClient,
    private val objectMapper: ObjectMapper,
    private val sender: String
) : OutboundSmsPort {
    override fun sendVerificationCode(phone: Phone, code: String) {
        val recipient = phone.value.removePrefix("+")
        val form = LinkedMultiValueMap<String, String>().apply {
            add("number", recipient)
            add("sign", sender)
            add("text", "$code — код подтверждения телефона в PIOS")
        }

        val response = try {
            client.post()
                .uri("/v2/sms/send")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .toEntity(String::class.java)
        } catch (ex: RestClientResponseException) {
            val status = ex.statusCode.value()
            throw if (status in 400..499) {
                OutboundSmsDeliveryException(SmsSubmissionState.FAILED, SmsSubmissionFailure.HTTP_REJECTED, status)
            } else {
                OutboundSmsDeliveryException(SmsSubmissionState.UNKNOWN, SmsSubmissionFailure.SERVER_ERROR, status)
            }
        } catch (ex: Exception) {
            val timeout = generateSequence<Throwable>(ex) { it.cause }.any { it is SocketTimeoutException }
            throw OutboundSmsDeliveryException(
                SmsSubmissionState.UNKNOWN,
                if (timeout) SmsSubmissionFailure.TRANSPORT_TIMEOUT else SmsSubmissionFailure.TRANSPORT_FAILURE
            )
        }

        if (!response.statusCode.is2xxSuccessful) {
            throw OutboundSmsDeliveryException(
                SmsSubmissionState.UNKNOWN, SmsSubmissionFailure.SERVER_ERROR, response.statusCode.value()
            )
        }

        val root = try {
            objectMapper.readTree(response.body)
        } catch (ex: Exception) {
            throw OutboundSmsDeliveryException(SmsSubmissionState.UNKNOWN, SmsSubmissionFailure.MALFORMED_RESPONSE)
        }
        if (root == null || !root.isObject || !root.path("success").isBoolean) {
            throw OutboundSmsDeliveryException(SmsSubmissionState.UNKNOWN, SmsSubmissionFailure.MALFORMED_RESPONSE)
        }
        if (!root.path("success").booleanValue()) {
            throw OutboundSmsDeliveryException(SmsSubmissionState.FAILED, SmsSubmissionFailure.PROVIDER_REJECTED)
        }

        // The normal send endpoint returns an array, even for one recipient.
        // Require exactly that recipient and a provider message id before
        // treating the submission as accepted. Never log the echoed data.
        val data = root.path("data")
        if (!data.isArray || data.size() != 1) {
            throw OutboundSmsDeliveryException(SmsSubmissionState.UNKNOWN, SmsSubmissionFailure.MALFORMED_RESPONSE)
        }
        val message = data[0]
        if (message.path("id").asLong(-1) <= 0 || message.path("number").asText() != recipient ||
            !message.path("status").isIntegralNumber
        ) {
            throw OutboundSmsDeliveryException(SmsSubmissionState.UNKNOWN, SmsSubmissionFailure.MALFORMED_RESPONSE)
        }
        when (message.path("status").intValue()) {
            0, 1, 3, 4, 8 -> return // queued, delivered, sent, waiting, or moderation
            2, 6 -> throw OutboundSmsDeliveryException(SmsSubmissionState.FAILED, SmsSubmissionFailure.PROVIDER_REJECTED)
            else -> throw OutboundSmsDeliveryException(SmsSubmissionState.UNKNOWN, SmsSubmissionFailure.MALFORMED_RESPONSE)
        }
    }
}
