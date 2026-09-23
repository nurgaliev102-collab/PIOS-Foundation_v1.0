package com.pios.identity.application

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

/**
 * The AES-256 key used by [OtpCipher] and [SmsOutboxRelay] to encrypt and
 * decrypt OTP plaintexts stored in
 * [com.pios.identity.domain.PhoneVerificationChallenge.otpCiphertext]
 * (C-2 Decision Lock architecture item 3).
 *
 * Key source: the environment variable `PIOS_OTP_RELAY_KEY`, expected to
 * be exactly 32 raw bytes encoded as Base64 (44 characters with padding).
 * A missing, blank, or wrong-length key causes startup to fail with a
 * clear [IllegalArgumentException] before any request is served — the same
 * fail-closed posture `pios.owner.*` and `pios.session.secret` already
 * establish for this module.
 *
 * Key generation command (produce once; store in the deployment secret store):
 *   `openssl rand -base64 32`
 *
 * SECURITY: the raw key bytes are held in a [SecretKeySpec] and never
 * logged. Do not expose [secretKey] to any code path that could serialize
 * or log it.
 */
@Component
class OtpRelayKey(
    @Value("\${PIOS_OTP_RELAY_KEY:}") private val keyBase64: String
) {
    val secretKey: javax.crypto.SecretKey = run {
        require(keyBase64.isNotBlank()) {
            "PIOS_OTP_RELAY_KEY is required — generate with: openssl rand -base64 32"
        }
        val keyBytes = try {
            Base64.getDecoder().decode(keyBase64)
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("PIOS_OTP_RELAY_KEY must be valid Base64", ex)
        }
        require(keyBytes.size == 32) {
            "PIOS_OTP_RELAY_KEY must be exactly 32 bytes (256 bits) when decoded; got ${keyBytes.size}"
        }
        SecretKeySpec(keyBytes, "AES")
    }
}
