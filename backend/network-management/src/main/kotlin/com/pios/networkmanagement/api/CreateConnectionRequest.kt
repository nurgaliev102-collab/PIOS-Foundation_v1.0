package com.pios.networkmanagement.api

data class CreateConnectionRequest(val fromPersonId: String, val toPersonId: String, val type: String)
