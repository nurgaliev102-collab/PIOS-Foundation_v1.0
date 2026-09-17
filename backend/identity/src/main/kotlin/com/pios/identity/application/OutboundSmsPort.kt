package com.pios.identity.application

import com.pios.identity.domain.Phone
import org.springframework.stereotype.Component

/**
 * ADR-082 Part 5/§10 (D-03.5, D-03.8) — the seam a real SMS provider
 * attaches to, directly reusing `ADR-074` Part 3's already-proven "a seam,
 * not a provider SDK" shape: no provider-specific type, exception, or
 * configuration key crosses into `domain/` or into any application
 * service beyond this one interface. [PhoneVerificationChallengeIssuer] is
 * this port's only caller.
 *
 * No provider is chosen or implemented by ADR-082 (D-03.5/D-03.8 — that is
 * a separate technical/procurement decision). Today's only implementation
 * ([NoOpOutboundSmsPort]) sends nothing and is wired wherever no real
 * provider is configured, the same fail-closed-by-absence posture this
 * codebase already uses for `OwnerCredentialGate.isConfigured()` and
 * `SessionTokenIssuer`'s unset-secret check.
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
 * The default adapter until a real provider is chosen (D-03.5/D-03.8) —
 * intentionally sends nothing. Never logs [code], consistent with every
 * real implementation's own obligation.
 */
@Component
object NoOpOutboundSmsPort : OutboundSmsPort {
    override fun sendVerificationCode(phone: Phone, code: String) {
        // Deliberately does nothing. A real provider is a separate,
        // later-chosen adapter implementing the same interface — see this
        // interface's own KDoc.
    }
}
