package com.pios.identity.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.identity.application.OutboundSmsDeliveryException
import com.pios.identity.application.OutboundSmsPort
import com.pios.identity.application.SmsSubmissionFailure
import com.pios.identity.application.SmsSubmissionState
import com.pios.identity.domain.Phone
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

/** SMS Aero normal send. A successful response means accepted, not delivered. */
class SmsAeroOutboundSmsAdapter private constructor(
    private val client: RestClient,
    private val objectMapper: ObjectMapper,
    private val sender: String,
    private val diagnosticSink: ((SmsAeroResponseDiagnostic) -> Unit)? = null
) : OutboundSmsPort {
    constructor(client: RestClient, objectMapper: ObjectMapper, sender: String) :
        this(client, objectMapper, sender, null)

    override fun sendVerificationCode(phone: Phone, code: String): Long {
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

        val inspection = inspect(response, recipient)
        emitDiagnostic(inspection.diagnostic)
        val message = inspection.message

        if (inspection.diagnostic.reason == SmsAeroResponseDiagnosticReason.SUCCESS_FALSE) {
            throw OutboundSmsDeliveryException(SmsSubmissionState.FAILED, SmsSubmissionFailure.PROVIDER_REJECTED)
        }
        if (inspection.diagnostic.reason != SmsAeroResponseDiagnosticReason.RESPONSE_VALID || message == null) {
            throw OutboundSmsDeliveryException(SmsSubmissionState.UNKNOWN, SmsSubmissionFailure.MALFORMED_RESPONSE)
        }

        val providerMessageId = message.path("id").longValue()
        when (message.path("status").intValue()) {
            0, 1, 3, 4, 8 -> return providerMessageId // queued, delivered, sent, waiting, or moderation
            2, 6 -> throw OutboundSmsDeliveryException(SmsSubmissionState.FAILED, SmsSubmissionFailure.PROVIDER_REJECTED)
            else -> throw OutboundSmsDeliveryException(SmsSubmissionState.UNKNOWN, SmsSubmissionFailure.MALFORMED_RESPONSE)
        }
    }

    /**
     * Inspects only response metadata and JSON structure. It deliberately never retains JSON values:
     * an SMS body can contain the OTP, and provider errors can echo request data.
     */
    private fun inspect(response: ResponseEntity<String>, recipient: String): SmsAeroResponseInspection {
        val body = response.body
        val base = SmsAeroResponseDiagnostic(
            httpStatus = response.statusCode.value(),
            contentType = response.headers.contentType?.toString()?.take(MAX_CONTENT_TYPE_LENGTH),
            bodyLength = body?.toByteArray(StandardCharsets.UTF_8)?.size ?: 0
        )
        val root = try {
            objectMapper.readTree(body)
        } catch (ex: Exception) {
            return SmsAeroResponseInspection(base.withReason(SmsAeroResponseDiagnosticReason.NON_JSON_BODY), null, null)
        }
        val parsedBase = base.copy(rootType = root?.let(::jsonType))
        if (root == null || !root.isObject) {
            return SmsAeroResponseInspection(parsedBase.withReason(SmsAeroResponseDiagnosticReason.ROOT_NOT_OBJECT), root, null)
        }

        val objectBase = parsedBase.copy(rootKeys = fieldNames(root))
        val success = root.get("success")
            ?: return SmsAeroResponseInspection(objectBase.withReason(SmsAeroResponseDiagnosticReason.SUCCESS_MISSING), root, null)
        val successBase = objectBase.copy(successType = jsonType(success))
        if (!success.isBoolean) {
            return SmsAeroResponseInspection(successBase.withReason(SmsAeroResponseDiagnosticReason.SUCCESS_NOT_BOOLEAN), root, null)
        }
        if (!success.booleanValue()) {
            return SmsAeroResponseInspection(successBase.withReason(SmsAeroResponseDiagnosticReason.SUCCESS_FALSE), root, null)
        }

        val data = root.get("data")
            ?: return SmsAeroResponseInspection(successBase.withReason(SmsAeroResponseDiagnosticReason.DATA_MISSING), root, null)
        val dataBase = successBase.copy(dataType = jsonType(data))
        // The controlled live response for this endpoint has a message object
        // directly in `data`. Do not accept an array or a collection wrapper:
        // this call submits exactly one recipient and requires one correlated
        // provider result before the outbox can become SENT.
        if (!data.isObject) {
            return SmsAeroResponseInspection(dataBase.withReason(SmsAeroResponseDiagnosticReason.DATA_NOT_OBJECT), root, null)
        }
        val message = data
        val messageBase = dataBase.copy(itemKeys = fieldNames(message))
        val id = message.get("id")
            ?: return SmsAeroResponseInspection(messageBase.withReason(SmsAeroResponseDiagnosticReason.MESSAGE_ID_MISSING), root, message)
        if (id.isNull) {
            return SmsAeroResponseInspection(messageBase.withReason(SmsAeroResponseDiagnosticReason.MESSAGE_ID_MISSING), root, message)
        }
        val idBase = messageBase.copy(idType = jsonType(id))
        if (!id.isIntegralNumber || !id.canConvertToLong() || id.longValue() <= 0) {
            return SmsAeroResponseInspection(idBase.withReason(SmsAeroResponseDiagnosticReason.MESSAGE_ID_INVALID), root, message)
        }

        val number = message.get("number")
            ?: return SmsAeroResponseInspection(idBase.withReason(SmsAeroResponseDiagnosticReason.NUMBER_MISSING), root, message)
        if (number.isNull) {
            return SmsAeroResponseInspection(idBase.copy(numberPresent = false).withReason(SmsAeroResponseDiagnosticReason.NUMBER_MISSING), root, message)
        }
        val numberBase = idBase.copy(numberPresent = !number.isNull)
        if ((!number.isTextual && !number.isIntegralNumber) || canonicalPhone(number.asText()) != canonicalPhone(recipient)) {
            return SmsAeroResponseInspection(numberBase.withReason(SmsAeroResponseDiagnosticReason.NUMBER_MISMATCH), root, message)
        }

        val status = message.get("status")
            ?: return SmsAeroResponseInspection(numberBase.withReason(SmsAeroResponseDiagnosticReason.STATUS_MISSING), root, message)
        if (status.isNull) {
            return SmsAeroResponseInspection(numberBase.withReason(SmsAeroResponseDiagnosticReason.STATUS_MISSING), root, message)
        }
        val statusBase = numberBase.copy(statusType = jsonType(status))
        if (!status.isIntegralNumber || !status.canConvertToInt() || status.intValue() !in ALLOWED_STATUS_VALUES) {
            return SmsAeroResponseInspection(statusBase.withReason(SmsAeroResponseDiagnosticReason.STATUS_INVALID), root, message)
        }
        return SmsAeroResponseInspection(
            statusBase.copy(statusValueAllowed = true).withReason(SmsAeroResponseDiagnosticReason.RESPONSE_VALID),
            root,
            message
        )
    }

    private fun emitDiagnostic(diagnostic: SmsAeroResponseDiagnostic) {
        diagnosticSink?.invoke(diagnostic) ?: logger.info(
            "SmsAero response diagnostic httpStatus={} contentType={} bodyLength={} reason={} rootType={} rootKeys={} " +
                "successType={} dataType={} dataSize={} itemKeys={} idType={} numberPresent={} statusType={} statusValueAllowed={}",
            diagnostic.httpStatus, diagnostic.contentType, diagnostic.bodyLength, diagnostic.reason,
            diagnostic.rootType, diagnostic.rootKeys, diagnostic.successType, diagnostic.dataType,
            diagnostic.dataSize, diagnostic.itemKeys, diagnostic.idType, diagnostic.numberPresent,
            diagnostic.statusType, diagnostic.statusValueAllowed
        )
    }

    private fun jsonType(node: com.fasterxml.jackson.databind.JsonNode): String = when {
        node.isObject -> "OBJECT"
        node.isArray -> "ARRAY"
        node.isBoolean -> "BOOLEAN"
        node.isIntegralNumber -> "INTEGER"
        node.isNumber -> "NUMBER"
        node.isTextual -> "STRING"
        node.isNull -> "NULL"
        else -> "OTHER"
    }

    private fun fieldNames(node: com.fasterxml.jackson.databind.JsonNode): List<String> =
        node.fieldNames().asSequence().take(MAX_REPORTED_KEYS).toList().sorted()

    /**
     * SMS Aero may echo the same Russian MSISDN as `+7…`, `7…`, or `8…`.
     * Accept only those narrow digit-only representations; anything else
     * remains a mismatch rather than weakening recipient correlation.
     */
    private fun canonicalPhone(value: String): String? {
        if (!SMS_AERO_PHONE_PATTERN.matches(value)) return null
        val digits = value.removePrefix("+")
        return if (digits.length == RUSSIAN_MSISDN_LENGTH && digits.startsWith('8')) {
            "7" + digits.drop(1)
        } else {
            digits
        }
    }

    companion object {
        /** Test-only factory. Production construction always uses the three-argument constructor. */
        internal fun withDiagnosticSink(
            client: RestClient,
            objectMapper: ObjectMapper,
            sender: String,
            diagnosticSink: (SmsAeroResponseDiagnostic) -> Unit
        ): SmsAeroOutboundSmsAdapter = SmsAeroOutboundSmsAdapter(client, objectMapper, sender, diagnosticSink)

        private val logger = LoggerFactory.getLogger(SmsAeroOutboundSmsAdapter::class.java)
        private const val MAX_CONTENT_TYPE_LENGTH = 128
        private const val MAX_REPORTED_KEYS = 16
        private const val RUSSIAN_MSISDN_LENGTH = 11
        private val SMS_AERO_PHONE_PATTERN = Regex("^\\+?[1-9][0-9]{6,14}$")
        private val ALLOWED_STATUS_VALUES = setOf(0, 1, 2, 3, 4, 6, 8)
    }
}

/** Safe metadata only: no response values, phone numbers, OTPs, credentials, or message body. */
internal data class SmsAeroResponseDiagnostic(
    val httpStatus: Int,
    val contentType: String?,
    val bodyLength: Int,
    val reason: SmsAeroResponseDiagnosticReason = SmsAeroResponseDiagnosticReason.NON_JSON_BODY,
    val rootType: String? = null,
    val rootKeys: List<String> = emptyList(),
    val successType: String? = null,
    val dataType: String? = null,
    val dataSize: Int? = null,
    val itemKeys: List<String> = emptyList(),
    val idType: String? = null,
    val numberPresent: Boolean? = null,
    val statusType: String? = null,
    val statusValueAllowed: Boolean? = null
) {
    fun withReason(reason: SmsAeroResponseDiagnosticReason): SmsAeroResponseDiagnostic = copy(reason = reason)
}

internal enum class SmsAeroResponseDiagnosticReason {
    NON_JSON_BODY,
    ROOT_NOT_OBJECT,
    SUCCESS_MISSING,
    SUCCESS_NOT_BOOLEAN,
    SUCCESS_FALSE,
    DATA_MISSING,
    DATA_NOT_OBJECT,
    MESSAGE_ID_MISSING,
    MESSAGE_ID_INVALID,
    NUMBER_MISSING,
    NUMBER_MISMATCH,
    STATUS_MISSING,
    STATUS_INVALID,
    RESPONSE_VALID
}

private data class SmsAeroResponseInspection(
    val diagnostic: SmsAeroResponseDiagnostic,
    val root: com.fasterxml.jackson.databind.JsonNode?,
    val message: com.fasterxml.jackson.databind.JsonNode?
)
