package com.pios.identity.api

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Allows this module's REST API to be called directly from the frontend's
 * own local dev server, and from a temporary pilot deployment when
 * `PIOS_PILOT_FRONTEND_ORIGIN` is set — mirrors every other directly-called
 * module's own identical `WebCorsConfiguration`
 * (e.g. `com.pios.drivermanagement.api.WebCorsConfiguration`'s own KDoc).
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
