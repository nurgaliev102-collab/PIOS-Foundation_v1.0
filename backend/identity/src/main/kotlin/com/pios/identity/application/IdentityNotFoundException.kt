package com.pios.identity.application

import com.pios.identity.domain.IdentityId

class IdentityNotFoundException(id: IdentityId) : RuntimeException("Identity ${id.value} not found")
