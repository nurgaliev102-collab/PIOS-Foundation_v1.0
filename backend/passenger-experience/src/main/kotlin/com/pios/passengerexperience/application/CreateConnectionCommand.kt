package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.DriverReference
import com.pios.passengerexperience.domain.PassengerReference

data class CreateConnectionCommand(val driverId: DriverReference, val passengerReference: PassengerReference)
