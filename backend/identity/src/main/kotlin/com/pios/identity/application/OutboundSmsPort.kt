package com.pios.identity.application

import com.pios.identity.domain.Phone

/**
 * ADR-082 Part 5/§10 (D-03.5, D-03.8) — the seam a real SMS provider
 * attaches to, directly reusing `ADR-074` Part 3's already-proven "a seam,
 * not a provider SDK" shape: no provider-specific type, exception, or
 * configuration key crosses into `domain/` or into any application
 * service beyond this one interface. [PhoneVerificationChallengeIssuer] is
 * this port's only caller.
 *
 * The production implementation is supplied by identity's SMS.RU adapter.
 * This interface remains provider-independent.
 */
fun interface OutboundSmsPort {
    /**
     * Sends [code] to [phone] as a verification code. [code] is the plain
     * numeric OTP the caller must type back — this is the one place in the
     * whole flow it exists outside a hash; implementations must not log it
     * (D-03.7).
     */
    fun sendVerificationCode(phone: Phone, code: String)
}

/**
 * Test/dev-only adapter. It is deliberately not a Spring component: a
 * production process cannot silently accept recovery requests without SMS.
 */
object NoOpOutboundSmsPort : OutboundSmsPort {
    override fun sendVerificationCode(phone: Phone, code: String) {
        // Deliberately does nothing.
    }
}
