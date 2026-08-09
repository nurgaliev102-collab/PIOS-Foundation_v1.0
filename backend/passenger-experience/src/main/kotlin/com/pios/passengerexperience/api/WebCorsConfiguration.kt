package com.pios.passengerexperience.api

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Allows this module's REST API to be called directly from the
 * frontend's own local dev server (Sprint 7B: Personal Network Flow MVP
 * -- this module's first real REST endpoint the frontend calls directly),
 * mirroring every other module's own identical `WebCorsConfiguration`
 * (for example, `com.pios.drivermanagement.api.WebCorsConfiguration`'s own
 * KDoc).
 *
 * Sprint 8.5 (Pilot Deployment Preparation): an additional origin read
 * from `PIOS_PILOT_FRONTEND_ORIGIN`, when set, is allowed alongside the
 * local dev origin — see that class's own KDoc for the reasoning.
 *
 * Bug found and fixed during the "Final Pre-Pilot Sprint" security review:
 * `DELETE` was missing here even though `ConnectionController`'s own
 * `DELETE /v1/connections/{connectionId}` (ADR-054, Circle of Trust) has
 * existed since the previous Sprint. `DELETE` is never a CORS "simple"
 * method, so a browser always preflights it — with `DELETE` absent from
 * `allowedMethods`, that preflight failed and the browser blocked the real
 * request before it ever reached this server. Backend unit/integration
 * tests never exercise CORS at all (it is purely a browser-enforced
 * mechanism), which is why this shipped unnoticed; it would only have
 * surfaced the first time a real browser tried "Удалить" from the circle
 * of trust.
 *
 * Browsers treat `http://localhost:5173` and `http://127.0.0.1:5173` as two
 * distinct origins even though Vite's dev server answers both, so both are
 * listed explicitly (mirrors the identical fix in `identity` and
 * `driver-management`) — a `127.0.0.1` origin without this entry fails CORS
 * preflight with 403.
 */
@Configuration
class WebCorsConfiguration : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        val origins = listOfNotNull(FRONTEND_DEV_ORIGIN, FRONTEND_DEV_ORIGIN_LOOPBACK_IP, System.getenv("PIOS_PILOT_FRONTEND_ORIGIN"))
        registry.addMapping("/v1/**")
            .allowedOrigins(*origins.toTypedArray())
            .allowedMethods("GET", "POST", "DELETE")
            .allowedHeaders("*")
    }

    companion object {
        const val FRONTEND_DEV_ORIGIN = "http://localhost:5173"
        const val FRONTEND_DEV_ORIGIN_LOOPBACK_IP = "http://127.0.0.1:5173"
    }
}
