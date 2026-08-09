package com.pios.dispatch.api

import org.springframework.web.servlet.config.annotation.CorsRegistry
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies the dev-origin allowlist directly against Spring's own
 * [CorsRegistry], without booting an `ApplicationContext` — mirrors
 * `com.pios.identity.api.WebCorsConfigurationTest` exactly.
 * [CorsRegistry.getCorsConfigurations] is `protected`, so [ExposedCorsRegistry]
 * widens it to read back what [WebCorsConfiguration] actually configured.
 */
class WebCorsConfigurationTest {

    private class ExposedCorsRegistry : CorsRegistry() {
        public override fun getCorsConfigurations() = super.getCorsConfigurations()
    }

    @Test
    fun `allows both the localhost and 127-0-0-1 dev origins`() {
        val registry = ExposedCorsRegistry()

        WebCorsConfiguration().addCorsMappings(registry)

        val allowedOrigins = registry.getCorsConfigurations()["/v1/**"]?.allowedOrigins.orEmpty()
        assertTrue(WebCorsConfiguration.FRONTEND_DEV_ORIGIN in allowedOrigins)
        assertTrue(WebCorsConfiguration.FRONTEND_DEV_ORIGIN_LOOPBACK_IP in allowedOrigins)
    }
}
