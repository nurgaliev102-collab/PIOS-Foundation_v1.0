package com.pios.identity.application

import com.pios.identity.persistence.InMemoryPhoneVerificationChallengeRepository
import com.pios.identity.persistence.InMemorySmsOutboxRepository
import java.util.Base64

internal fun testOtpCipher(): OtpCipher = OtpCipher(
    OtpRelayKey(Base64.getEncoder().encodeToString(ByteArray(32) { 7 }))
)

internal fun testSmsOutbox(repository: InMemoryPhoneVerificationChallengeRepository): InMemorySmsOutboxRepository =
    InMemorySmsOutboxRepository(repository)
