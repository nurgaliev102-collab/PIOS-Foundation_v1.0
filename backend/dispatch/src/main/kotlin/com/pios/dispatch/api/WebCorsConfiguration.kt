package com.pios.dispatch.api

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
 * frontend has called this module directly (`AssignmentController`,
 * since Sprint FR-004; `ProposalController`, since Sprint
 * IMPLEMENTATION-003) for several sprints. Fixed identically in
 * `driver-management` and `order-management`, the other two modules the
 * frontend calls directly. No ADR or Product Decision governs
 * cross-origin policy, and none is introduced here beyond allowing the
 * one origin this project's own frontend actually runs on locally — not
 * an architectural decision, a minimal operational necessity for the
 * already-built frontend to reach the already-built backend at all.
 */
@Configuration
class WebCorsConfiguration : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/v1/**")
            .allowedOrigins(FRONTEND_DEV_ORIGIN)
            .allowedMethods("GET", "POST")
            .allowedHeaders("*")
    }

    companion object {
        const val FRONTEND_DEV_ORIGIN = "http://localhost:5173"
    }
}
