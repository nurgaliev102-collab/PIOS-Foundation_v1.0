package com.pios.networkmanagement.domain

/**
 * A Connection's own state (Sprint 7A: PIOS Network Foundation). `INVITED`
 * is available for a caller creating a Connection directly
 * (`POST /v1/connections`) to represent a pending relationship; a
 * Connection created by [com.pios.networkmanagement.application.AcceptInvitationApplicationService]
 * is always [CONNECTED] — accepting an [Invitation] is itself the
 * connecting event, so there is no separate pending state to pass through
 * on that path.
 */
enum class ConnectionType {
    INVITED,
    CONNECTED
}
