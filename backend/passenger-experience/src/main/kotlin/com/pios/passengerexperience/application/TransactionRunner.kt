package com.pios.passengerexperience.application

/**
 * The transactional boundary [CreateConnectionApplicationService] uses
 * around its find-or-create sequence, mirroring every other module's own
 * [TransactionRunner] abstraction exactly (see
 * `com.pios.drivermanagement.application.TransactionRunner`'s own KDoc).
 */
interface TransactionRunner {
    fun <T> run(action: () -> T): T
}
