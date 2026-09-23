package com.pios.networkmanagement.api

data class CreateConnectionRequest(val fromPersonId: String? = null, val toPersonId: String, val type: String)
