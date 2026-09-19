package com.pios.identity.application

/** The provider rejected the request, or its eventual acceptance is uncertain. */
enum class SmsSubmissionState { FAILED, UNKNOWN }

enum class SmsSubmissionFailure {
    HTTP_REJECTED,
    PROVIDER_REJECTED,
    SERVER_ERROR,
    MALFORMED_RESPONSE,
    TRANSPORT_TIMEOUT,
    TRANSPORT_FAILURE
}

/** Contains only safe classifications: never a provider body, phone, code, or credential. */
class OutboundSmsDeliveryException(
    val state: SmsSubmissionState,
    val failure: SmsSubmissionFailure,
    val httpStatus: Int? = null
) : RuntimeException("SMS submission $state ($failure)")
