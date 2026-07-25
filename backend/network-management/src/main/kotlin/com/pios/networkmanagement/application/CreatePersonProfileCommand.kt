package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.PersonId
import com.pios.networkmanagement.domain.ProfileType

data class CreatePersonProfileCommand(val personId: PersonId, val type: ProfileType)
