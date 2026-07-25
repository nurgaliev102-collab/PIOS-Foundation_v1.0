package com.pios.networkmanagement.api

/**
 * The shape `GET /v1/persons/{id}/connections` returns, per the founder's
 * own specification example (`{"person":"Регина","type":"CONNECTED"}`) —
 * [person] is the connected-to person's display name (falling back to
 * their raw id if, for some reason, their [com.pios.networkmanagement.domain.Person]
 * record cannot be found), not their id.
 */
data class PersonConnectionResponse(val person: String, val type: String)
