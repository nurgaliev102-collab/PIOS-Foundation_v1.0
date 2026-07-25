package com.pios.networkmanagement.api

/**
 * The shape `POST /v1/connections` returns, per the founder's own
 * specification example (`{"id":"uuid","status":"CONNECTED"}`) — the
 * field is named `status` here, matching that example literally, even
 * though the underlying domain field is [com.pios.networkmanagement.domain.Connection.type].
 */
data class ConnectionResponse(val id: String, val status: String)
