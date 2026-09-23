package com.pios.identity.application

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encrypt/decrypt for the OTP plaintext stored in
 * [com.pios.identity.domain.PhoneVerificationChallenge.otpCiphertext]
 * (C-2 Decision Lock item 3, ADR-082 architecture item 3).
 *
 * The OTP is encrypted — not hashed — so the relay can recover the
 * plaintext it must send to SMS Aero without the original issuer thread
 * being alive. The PBKDF2 hash ([PasswordHasher]) is for verification
 * by the user-supplied code; AES-GCM is for relay-side decryption.
 *
 * [OtpRelayKey] holds the AES-256 key; both this cipher and the relay
 * consume it. AES-256-GCM: 96-bit nonce (12 bytes), 128-bit auth tag.
 * Each call to [encrypt] generates a fresh random nonce — never reused
 * for the same key (GCM nonce-reuse catastrophe). The nonce is returned
 * separately; both ciphertext and nonce are Base64-encoded for text-column
 * storage. Plaintext is never logged (D-03.7).
 */
@Component
class OtpCipher(private val otpRelayKey: OtpRelayKey) {

    data class CipherResult(
        /** Base64-encoded AES-256-GCM ciphertext (includes 128-bit auth tag). */
        val ciphertext: String,
        /** Base64-encoded 96-bit (12-byte) nonce. */
        val nonce: String
    )

    /**
     * Encrypts [plaintext] under the relay key. A fresh nonce is generated
     * for every call — the caller must store it alongside the ciphertext.
     */
    fun encrypt(plaintext: String): CipherResult {
        val nonceBytes = ByteArray(NONCE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, otpRelayKey.secretKey, GCMParameterSpec(TAG_LENGTH_BITS, nonceBytes))
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return CipherResult(
            ciphertext = Base64.getEncoder().encodeToString(cipherBytes),
            nonce = Base64.getEncoder().encodeToString(nonceBytes)
        )
    }

    /**
     * Decrypts [ciphertextBase64] using [nonceBase64]. Returns the OTP
     * plaintext, or throws if the ciphertext is tampered, truncated, or
     * the key is wrong. The caller must never log the return value.
     *
     * @throws javax.crypto.AEADBadTagException if authentication fails.
     * @throws IllegalArgumentException if inputs are malformed Base64.
     */
    fun decrypt(ciphertextBase64: String, nonceBase64: String): String {
        val cipherBytes = Base64.getDecoder().decode(ciphertextBase64)
        val nonceBytes = Base64.getDecoder().decode(nonceBase64)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, otpRelayKey.secretKey, GCMParameterSpec(TAG_LENGTH_BITS, nonceBytes))
        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val NONCE_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
    }
}
