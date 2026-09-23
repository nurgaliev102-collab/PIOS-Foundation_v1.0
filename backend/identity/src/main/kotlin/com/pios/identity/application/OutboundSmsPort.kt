package com.pios.identity.application

import com.pios.identity.domain.Phone

/**
 * ADR-082 Part 5/§10 (D-03.5, D-03.8) — the seam a real SMS provider
 * attaches to, directly reusing `ADR-074` Part 3's already-proven "a seam,
 * not a provider SDK" shape: no provider-specific type, exception, or
 * configuration key crosses into `domain/` or into any application
 * service beyond this one interface. [SmsOutboxRelay] is this port's only
 * production caller (C-2: the relay, not the issuer, drives SMS delivery).
 *
 * The production implementation is supplied by identity's SMS Aero adapter.
 * This interface remains provider-independent.
 */
fun interface OutboundSmsPort {
    /**
     * Sends [code] to [phone] as a verification code and returns the
     * provider's accepted message ID. [code] is the plain numeric OTP —
     * implementations must not log it (D-03.7).
     *
     * Return value semantic (Decision Lock I.3): "SMS Aero accepted the
     * message for routing" — NOT "delivered" and NOT "carrier-accepted".
     *
     * @throws [OutboundSmsDeliveryException] on rejection (FAILED) or
     *         uncertain outcome (UNKNOWN). Never on success.
     */
    fun sendVerificationCode(phone: Phone, code: String): Long
}

/**
 * Test/dev-only adapter. It is deliberately not a Spring component: a
 * production process cannot silently accept recovery requests without SMS.
 * Returns 0L as the sentinel provider message ID.
 */
object NoOpOutboundSmsPort : OutboundSmsPort {
    override fun sendVerificationCode(phone: Phone, code: String): Long = 0L
}
