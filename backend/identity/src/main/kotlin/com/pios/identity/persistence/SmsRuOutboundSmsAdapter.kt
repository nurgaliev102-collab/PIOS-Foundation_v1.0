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

/** SMS.RU transport. A successful API response means accepted for delivery, not delivered. */
class SmsRuOutboundSmsAdapter(
    private val client: RestClient,
    private val objectMapper: ObjectMapper,
    private val apiId: String,
    private val sender: String
) : OutboundSmsPort {
    override fun sendVerificationCode(phone: Phone, code: String) {
        val recipient = phone.value.removePrefix("+")
        val form = LinkedMultiValueMap<String, String>().apply {
            add("api_id", apiId)
            add("to", recipient)
            add("msg", "$code — код подтверждения телефона в PIOS")
            add("from", sender)
            add("json", "1")
        }

        val response = try {
            client.post()
                .uri("/sms/send")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
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
            throw OutboundSmsDeliveryException(SmsSubmissionState.UNKNOWN, SmsSubmissionFailure.SERVER_ERROR,
                response.statusCode.value())
        }

        val root = try {
            objectMapper.readTree(response.body)
        } catch (ex: Exception) {
            throw OutboundSmsDeliveryException(SmsSubmissionState.UNKNOWN, SmsSubmissionFailure.MALFORMED_RESPONSE)
        }
        if (root == null || !root.isObject) {
            throw OutboundSmsDeliveryException(SmsSubmissionState.UNKNOWN, SmsSubmissionFailure.MALFORMED_RESPONSE)
        }
        if (root.path("status").asText() == "ERROR") {
            throw OutboundSmsDeliveryException(SmsSubmissionState.FAILED, SmsSubmissionFailure.PROVIDER_REJECTED)
        }
        val addressed = root.path("sms").path(recipient)
        if (addressed.path("status").asText() == "ERROR") {
            throw OutboundSmsDeliveryException(SmsSubmissionState.FAILED, SmsSubmissionFailure.PROVIDER_REJECTED)
        }
        if (root.path("status").asText() != "OK" || root.path("status_code").asInt(-1) != 100 ||
            addressed.path("status").asText() != "OK" || addressed.path("status_code").asInt(-1) != 100 ||
            addressed.path("sms_id").asText().isBlank()
        ) {
            throw OutboundSmsDeliveryException(SmsSubmissionState.UNKNOWN, SmsSubmissionFailure.MALFORMED_RESPONSE)
        }
    }
}
