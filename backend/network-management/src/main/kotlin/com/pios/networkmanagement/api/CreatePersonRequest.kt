package com.pios.networkmanagement.api

data class CreatePersonRequest(val name: String, val phone: String? = null, val identityId: String? = null)
