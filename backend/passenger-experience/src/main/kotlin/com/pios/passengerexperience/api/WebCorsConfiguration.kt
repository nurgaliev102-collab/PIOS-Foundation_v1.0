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
 */
@Configuration
class WebCorsConfiguration : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        val origins = listOfNotNull(FRONTEND_DEV_ORIGIN, System.getenv("PIOS_PILOT_FRONTEND_ORIGIN"))
        registry.addMapping("/v1/**")
            .allowedOrigins(*origins.toTypedArray())
            .allowedMethods("GET", "POST")
            .allowedHeaders("*")
    }

    companion object {
        const val FRONTEND_DEV_ORIGIN = "http://localhost:5173"
    }
}
