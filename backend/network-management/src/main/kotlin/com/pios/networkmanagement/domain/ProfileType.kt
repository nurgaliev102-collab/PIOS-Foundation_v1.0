package com.pios.networkmanagement.domain

/**
 * The role a [PersonProfile] represents for its [Person] (Sprint 7A: PIOS
 * Network Foundation). A person may hold more than one — each is a
 * separate [PersonProfile] row, never a set or list field on [Person]
 * itself.
 */
enum class ProfileType {
    DRIVER,
    PASSENGER,
    NETWORK_MEMBER
}
