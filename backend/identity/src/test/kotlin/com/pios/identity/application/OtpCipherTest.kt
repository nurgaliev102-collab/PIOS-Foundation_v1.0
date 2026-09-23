package com.pios.identity.application

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class OtpCipherTest {
    private fun cipher(seed: Byte): OtpCipher =
        OtpCipher(OtpRelayKey(Base64.getEncoder().encodeToString(ByteArray(32) { seed })))

    @Test
    fun `AES GCM encrypts with a fresh nonce and decrypts only with the relay key`() {
        val cipher = cipher(1)
        val first = cipher.encrypt("123456")
        val second = cipher.encrypt("123456")

        assertNotEquals(first.nonce, second.nonce)
        assertNotEquals(first.ciphertext, second.ciphertext)
        assertEquals("123456", cipher.decrypt(first.ciphertext, first.nonce))
    }

    @Test
    fun `wrong relay key and tampered ciphertext are rejected`() {
        val encrypted = cipher(1).encrypt("123456")

        assertFailsWith<Exception> { cipher(2).decrypt(encrypted.ciphertext, encrypted.nonce) }
        val tampered = encrypted.ciphertext.dropLast(1) + if (encrypted.ciphertext.last() == 'A') "B" else "A"
        assertFailsWith<Exception> { cipher(1).decrypt(tampered, encrypted.nonce) }
    }

    @Test
    fun `relay key is required valid Base64 and exactly 256 bits`() {
        assertFailsWith<IllegalArgumentException> { OtpRelayKey("") }
        assertFailsWith<IllegalArgumentException> { OtpRelayKey("not base64!") }
        assertFailsWith<IllegalArgumentException> { OtpRelayKey(Base64.getEncoder().encodeToString(ByteArray(31))) }
    }
}
