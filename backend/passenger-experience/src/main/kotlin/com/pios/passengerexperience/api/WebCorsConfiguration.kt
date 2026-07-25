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
