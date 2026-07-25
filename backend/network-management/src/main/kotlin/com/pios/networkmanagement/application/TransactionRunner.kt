package com.pios.networkmanagement.application

/**
 * The transactional boundary an application service uses around a
 * check-then-write sequence (for example, Create Invitation's own
 * uniqueness check before insert), mirroring every other module's own
 * [TransactionRunner] abstraction exactly (see
 * `com.pios.drivermanagement.application.TransactionRunner`'s own KDoc).
 * No storage technology is named here.
 */
interface TransactionRunner {
    fun <T> run(action: () -> T): T
}
