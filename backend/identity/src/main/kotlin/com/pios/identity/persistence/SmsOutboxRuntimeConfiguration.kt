package com.pios.identity.persistence

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class SmsOutboxRuntimeConfiguration {
    @Bean
    fun smsOutboxClock(): Clock = Clock.systemUTC()
}
