package com.pios.networkmanagement.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Local verifier for Identity's ADR-055 session format. Identity remains the sole issuer. */
@Component
class SessionTokenVerifier(@Value("\${pios.session.secret:}") private val secretBase64: String) {
    private val objectMapper = ObjectMapper()

    fun verify(authorizationHeader: String?): String? {
        if (secretBase64.isBlank() || authorizationHeader?.startsWith("Bearer ") != true) return null
        val token = authorizationHeader.removePrefix("Bearer ").trim()
        val parts = token.split(".")
        if (parts.size != 2) return null
        val signature = decode(parts[1]) ?: return null
        val expected = try {
            Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(Base64.getDecoder().decode(secretBase64), "HmacSHA256"))
            }.doFinal(parts[0].toByteArray(Charsets.UTF_8))
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (!MessageDigest.isEqual(signature, expected)) return null
        val payload = try { objectMapper.readTree(decode(parts[0]) ?: return null) } catch (_: Exception) { return null }
        val sub = payload.get("sub")?.takeIf { it.isTextual }?.asText()
        val exp = payload.get("exp")?.takeIf { it.isNumber }?.asLong()
        return sub?.takeIf { it.isNotBlank() && exp != null && exp > Instant.now().epochSecond }
    }

    private fun decode(value: String): ByteArray? = try {
        Base64.getUrlDecoder().decode(value)
    } catch (_: IllegalArgumentException) {
        null
    }
}
