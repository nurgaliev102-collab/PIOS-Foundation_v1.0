package com.pios.networkmanagement.api

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/** Authenticate before MVC deserializes an untrusted path or body. */
@Component
class NetworkSessionFilter(private val verifier: SessionTokenVerifier) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/v1/") || request.method == "OPTIONS"

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        NetworkSecurityContext.clear()
        val identityId = verifier.verify(request.getHeader("Authorization"))
        if (identityId == null) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return
        }
        try {
            NetworkSecurityContext.setIdentityId(identityId)
            chain.doFilter(request, response)
        } finally {
            NetworkSecurityContext.clear()
        }
    }
}
