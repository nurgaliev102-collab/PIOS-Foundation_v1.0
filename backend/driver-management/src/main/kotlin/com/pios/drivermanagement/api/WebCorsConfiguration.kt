package com.pios.drivermanagement.api

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Allows this module's REST API to be called directly from the
 * frontend's own local dev server (`http://localhost:5173`, Vite's
 * default port) — a different origin. Without this, every browser-side
 * `fetch` from `Coordinator`/`DriverHome` is blocked by the browser's own
 * CORS policy before this module's controllers ever see the request.
 *
 * Discovered during Sprint VALIDATION-001's end-to-end audit: no CORS
 * configuration existed anywhere in any backend module, even though the
 * frontend has called this module directly (`DriverController`, since
 * Sprint 5) for several sprints. Fixed identically in `order-management`
 * and `dispatch`, the other two modules the frontend calls directly. No
 * ADR or Product Decision governs cross-origin policy, and none is
 * introduced here beyond allowing the one origin this project's own
 * frontend actually runs on locally — not an architectural decision, a
 * minimal operational necessity for the already-built frontend to reach
 * the already-built backend at all.
 *
 * Sprint 8.5 (Pilot Deployment Preparation): the local dev origin above
 * is always allowed, unchanged; an additional origin read from the
 * `PIOS_PILOT_FRONTEND_ORIGIN` environment variable is allowed alongside
 * it when set, so a temporary pilot deployment (for example, a
 * Cloudflare Tunnel URL) can reach this API without changing the default
 * for anyone still running the frontend locally.
 *
 * Browsers treat `http://localhost:5173` and `http://127.0.0.1:5173` as two
 * distinct origins even though Vite's dev server answers both, so both are
 * listed explicitly (mirrors the identical fix in
 * `com.pios.identity.api.WebCorsConfiguration`) — a `127.0.0.1` origin
 * without this entry fails CORS preflight with 403.
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
