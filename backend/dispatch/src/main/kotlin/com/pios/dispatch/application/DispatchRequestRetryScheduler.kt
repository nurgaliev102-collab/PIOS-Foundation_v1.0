package com.pios.dispatch.application

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DispatchRequestRetryScheduler(private val service: DispatchRequestApplicationService) {
    @Scheduled(fixedDelayString = "\${pios.dispatch.retry.fixed-delay-ms:5000}")
    fun retryDue() {
        service.retryDue()
    }
}
