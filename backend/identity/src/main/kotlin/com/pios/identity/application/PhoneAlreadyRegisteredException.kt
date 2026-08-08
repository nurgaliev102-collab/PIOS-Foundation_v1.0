package com.pios.identity.application

import com.pios.identity.domain.Phone

/**
 * Thrown by [RegisterIdentityApplicationService] when [Phone] already
 * anchors another [com.pios.identity.domain.Identity] (ADR-055 Decision
 * 3: `POST /v1/identities/register` returns 409). Mirrors
 * [IdentityNotFoundException]'s own convention: a domain-meaningful
 * exception the API layer maps to a transport-level status, rather than a
 * boolean or a null past this layer.
 */
class PhoneAlreadyRegisteredException(phone: Phone) : RuntimeException("Phone ${phone.value} already registered")
