package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.ConnectionType
import com.pios.networkmanagement.domain.PersonId

data class CreateConnectionCommand(val fromPersonId: PersonId, val toPersonId: PersonId, val type: ConnectionType)
