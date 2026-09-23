package com.pios.networkmanagement.api

/** Request-scoped subject installed only after signature and expiry verification. */
internal object NetworkSecurityContext {
    private val identity = ThreadLocal<String>()

    fun setIdentityId(identityId: String) { identity.set(identityId) }
    fun identityId(): String? = identity.get()
    fun clear() { identity.remove() }
}
