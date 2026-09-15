package com.pios.billing.api

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Allows this module's REST API to be called directly from the frontend's
 * own local dev server (`http://localhost:5173`, Vite's default port) — a
 * different origin. Replicated identically from
 * `com.pios.drivermanagement.api.WebCorsConfiguration` (see that class's
 * own KDoc for the incident this fixes). This module exposes no endpoint
 * any frontend screen is required to call (ADR-074 Part 4: no frontend UI
 * is authorized beyond, at most, a minimal, ungated confirmation) — this
 * configuration exists so the one read endpoint
 * (`GET /v1/subscriptions/{driverId}`) is reachable from a browser if and
 * when a caller adds that minimal confirmation, mirroring every other
 * module's identical CORS posture rather than leaving this module the one
 * inconsistent exception.
 */
@Configuration
class WebCorsConfiguration : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        val origins = listOfNotNull(FRONTEND_DEV_ORIGIN, FRONTEND_DEV_ORIGIN_LOOPBACK_IP, System.getenv("PIOS_PILOT_FRONTEND_ORIGIN"))
        registry.addMapping("/v1/**")
            .allowedOrigins(*origins.toTypedArray())
            .allowedMethods("GET", "POST")
            .allowedHeaders("*")
    }

    companion object {
        const val FRONTEND_DEV_ORIGIN = "http://localhost:5173"
        const val FRONTEND_DEV_ORIGIN_LOOPBACK_IP = "http://127.0.0.1:5173"
    }
}
