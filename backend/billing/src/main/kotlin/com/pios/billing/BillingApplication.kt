package com.pios.billing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Structural boundary for the Billing module (ADR-074: Subscription /
 * Billing Bounded Context Foundation). Its own, independently buildable
 * and deployable unit, with its own PostgreSQL database (`pios_billing`),
 * depending on no other module.
 *
 * Exposes `POST /v1/subscriptions/{driverId}/periods` (owner-gated) and
 * `GET /v1/subscriptions/{driverId}` (session-token-gated to that driver)
 * on `com.pios.billing.api.SubscriptionController`, and
 * `GET /v1/health/billing` (owner-gated) on
 * `com.pios.billing.api.HealthController` -- nothing else.
 *
 * No `@EnableScheduling`: unlike `driver-management`/`passenger-experience`/
 * `order-management`/`dispatch`, this module has no outbox relay to
 * trigger. ADR-074's own "no RabbitMQ topology, no outbox, no consumer, no
 * event listener anywhere in this module" constraint means there is
 * nothing periodic here to run.
 */
@SpringBootApplication
class BillingApplication

fun main(args: Array<String>) {
    runApplication<BillingApplication>(*args)
}
