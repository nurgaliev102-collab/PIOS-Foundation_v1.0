package com.pios.dispatch.persistence

import com.pios.dispatch.application.OrderGuard
import com.pios.dispatch.domain.OrderReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class PostgreSQLOrderGuard(private val jdbc: JdbcTemplate) : OrderGuard {
    override fun lock(order: OrderReference) {
        jdbc.update(
            "INSERT INTO dispatch_order_guards (order_reference) VALUES (?) ON CONFLICT DO NOTHING",
            order.orderId
        )
        jdbc.queryForObject(
            "SELECT order_reference FROM dispatch_order_guards WHERE order_reference = ? FOR UPDATE",
            String::class.java,
            order.orderId
        ) ?: error("Missing order guard for ${order.orderId}")
    }
}
