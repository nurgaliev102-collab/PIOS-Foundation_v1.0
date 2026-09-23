package com.pios.networkmanagement.api

/**
 * Public representation of a network person.
 *
 * `Person.phone` is relationship data owned by this module, but ADR-059
 * prohibits disclosing any participant phone number through an endpoint.
 */
data class PersonResponse(val id: String, val name: String)
