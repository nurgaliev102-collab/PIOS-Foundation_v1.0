package com.pios.dispatch.persistence

import com.pios.dispatch.application.ProposalRepository
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * The PostgreSQL-backed adapter for [ProposalRepository], mirroring
 * [PostgreSQLAssignmentRepository] exactly. Persists exactly the Proposal
 * logical entity this module owns, in `pios_dispatch` — no other
 * module's information, no shared schema. [OrderReference] and
 * [DriverReference] are persisted as plain identifiers only, exactly as
 * for Assignment.
 *
 * Uses plain JDBC via Spring's [JdbcTemplate] only — no ORM, no entity
 * annotations. [Proposal] itself carries no persistence knowledge
 * whatsoever; this class owns database mapping and storage operations
 * exclusively.
 *
 * ## Reconstruction and the private constructor
 *
 * [Proposal]'s constructor is private, reachable only through
 * [Proposal.propose] — which always starts a new proposal at
 * [ProposalStatus.OPEN] and does not accept a status parameter.
 * Reconstructing a persisted, possibly-already-resolved [Proposal]
 * therefore takes the same two steps already proven for
 * [PostgreSQLAssignmentRepository]:
 *
 * 1. Reflectively invoke the private constructor with the persisted id,
 *    order reference, driver reference, and (ADR-043) `created_at`,
 *    yielding a proposal in its initial [ProposalStatus.OPEN] state.
 * 2. If the persisted row's status is [ProposalStatus.ACCEPTED],
 *    [ProposalStatus.DECLINED], [ProposalStatus.LAPSED], or
 *    [ProposalStatus.WITHDRAWN] (ADR-053), call the aggregate's own
 *    public [Proposal.accept], [Proposal.decline], [Proposal.lapse], or
 *    [Proposal.withdraw] — each now taking the persisted `responded_at`
 *    as its `at` argument (ADR-043) — to advance it. No further
 *    reflection needed.
 *
 *    **Binding, per ADR-053's own "Architectural Prerequisite: PostgreSQL
 *    Reconstruction Update":** the `when` block below is a statement, not
 *    an expression, so Kotlin does not enforce its exhaustiveness — a
 *    future `ProposalStatus` value added without a corresponding branch
 *    here would compile and silently misreconstruct, not fail to build.
 *
 * This does not bypass any invariant: step 1's private constructor
 * performs no validation beyond what already-persisted, previously-valid
 * data satisfies by construction, and step 2 uses the aggregate's own
 * business logic unmodified.
 *
 * **ADR-043 binding constraint on this class specifically.** [Proposal]'s
 * private constructor gained a fourth parameter ([Proposal.createdAt]).
 * The reflective lookup below is widened to `(String, String, String,
 * Instant)` to match exactly — the same landmine this class's own KDoc
 * already names for [statedPrice] applies here in the other direction: an
 * un-widened lookup would throw `NoSuchMethodException` at the first real
 * database read, not at compile time.
 *
 * **ADR-066 binding constraint, added on top of the above.** [Proposal]'s
 * private constructor gained a sixth parameter,
 * [Proposal.passengerReference] (`PassengerReference?`). ADR-066 Decision 9
 * assumed a nullable inline value class boxes at the JVM level (the way it
 * would for a primitive-backed one, since a primitive cannot itself
 * represent `null`). **Verified empirically for this case (`javap` on the
 * compiled class) and found otherwise**: since [PassengerReference]'s own
 * underlying type is already `String` — a reference type that already
 * represents `null` natively — Kotlin erases `PassengerReference?` to
 * plain `java.lang.String` here too, identically to the non-null inline
 * classes above, not to a boxed `PassengerReference`. The reflective
 * lookup is widened to `(String, String, String, Instant, Boolean,
 * String)` — six parameters, the sixth still `String::class.java` — and
 * [reconstruct] passes the raw, possibly-null string straight through with
 * no `PassengerReference(...)` wrapping at the reflection call site (the
 * Kotlin-level wrapping happens on the *read* side, inside [Proposal]
 * itself, not here). Recorded here so a future reader does not "fix" this
 * back to `PassengerReference::class.java` on the assumption in ADR-066's
 * own text — that assumption is superseded by this verified fact, and this
 * is the correction record for it (`CLAUDE.md`: "Never Delete
 * Documentation" applies to a superseded technical claim the same as a
 * superseded product decision).
 *
 * ## `stated_price` (ADR-042, Stated Ride Price Minimal Model)
 *
 * The row is inserted at *propose* time — before any price exists — and
 * [Proposal.statedPrice] is only ever set later, at *accept* time. [save]'s
 * upsert therefore always takes the `ON CONFLICT ... DO UPDATE` path when
 * persisting an acceptance, so `stated_price` must appear in **both** the
 * `INSERT` column list and the `DO UPDATE SET` clause — omitting it from
 * the latter would silently drop every stated price on every accept, with
 * no symptom visible until a fresh read from the database (mirroring the
 * same discipline [PostgreSQLAssignmentRepository] already applies to its
 * own status-transition columns). [reconstruct] replays the persisted
 * value through [Proposal.accept]'s own `statedPrice` parameter in the
 * `ACCEPTED` branch, mirroring [Proposal.accept]'s own `at`-parameter
 * precedent on `Assignment`.
 *
 * ## `passenger_reference` (ADR-066, Proposal Participant Authorization)
 *
 * Fixed at *propose* time, like `created_at` -- never renegotiated as the
 * proposal resolves. It therefore appears in the `INSERT` column list and
 * `VALUES` only, deliberately **not** in the `DO UPDATE SET` clause: an
 * upsert on this row is always a later status transition ([reconstruct]'s
 * own `when` branches), never a re-proposal, so there is never a
 * legitimate new value to write on conflict, and including it there would
 * silently let a conflicting write overwrite who the proposal was created
 * for.
 */
@Repository
class PostgreSQLProposalRepository(
    private val jdbcTemplate: JdbcTemplate
) : ProposalRepository {

    private val selectColumns =
        "id, order_reference, driver_reference, status, stated_price, stated_eta_minutes, created_at, responded_at, is_test, passenger_reference"

    override fun save(proposal: Proposal) {
        jdbcTemplate.update(
            """
            INSERT INTO proposals (id, order_reference, driver_reference, status, stated_price, stated_eta_minutes, created_at, responded_at, is_test, passenger_reference)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                stated_price = EXCLUDED.stated_price,
                stated_eta_minutes = EXCLUDED.stated_eta_minutes,
                responded_at = EXCLUDED.responded_at
            """.trimIndent(),
            proposal.id.value,
            proposal.order.orderId,
            proposal.driver.driverId,
            proposal.status.name,
            proposal.statedPrice,
            proposal.statedEtaMinutes,
            proposal.createdAt?.let { Timestamp.from(it) },
            proposal.respondedAt?.let { Timestamp.from(it) },
            proposal.isTest,
            proposal.passengerReference?.passengerId
        )
    }

    override fun findById(id: ProposalId): Proposal? {
        val rows = jdbcTemplate.query(
            "SELECT $selectColumns FROM proposals WHERE id = ?",
            { rs, _ -> reconstruct(rs) },
            id.value
        )
        return rows.firstOrNull()
    }

    override fun findByOrder(order: OrderReference): List<Proposal> =
        jdbcTemplate.query(
            "SELECT $selectColumns FROM proposals WHERE order_reference = ?",
            { rs, _ -> reconstruct(rs) },
            order.orderId
        )

    override fun findByDriver(driver: DriverReference): List<Proposal> =
        jdbcTemplate.query(
            "SELECT $selectColumns FROM proposals WHERE driver_reference = ?",
            { rs, _ -> reconstruct(rs) },
            driver.driverId
        )

    override fun findOpen(): List<Proposal> =
        jdbcTemplate.query(
            "SELECT $selectColumns FROM proposals WHERE status = ?",
            { rs, _ -> reconstruct(rs) },
            ProposalStatus.OPEN.name
        )

    private fun reconstruct(rs: ResultSet): Proposal {
        // ADR-066 Decision 9 assumed `passengerReference` (`PassengerReference?`)
        // would box at the JVM level, requiring `PassengerReference::class.java`
        // here. Verified empirically (`javap` on the compiled class) to be
        // false for this case: since `PassengerReference` wraps `String` --
        // already nullable at the JVM level -- Kotlin erases the nullable
        // inline class straight to `String`, exactly like the non-null
        // `id`/`order_reference`/`driver_reference` slots above. The sixth
        // reflective type is therefore `String::class.java`, and the raw,
        // possibly-null string is passed through unwrapped -- see this
        // class's own KDoc "ADR-066 binding constraint" section for the
        // full correction record.
        val constructor = Proposal::class.java.getDeclaredConstructor(
            String::class.java,
            String::class.java,
            String::class.java,
            Instant::class.java,
            Boolean::class.java,
            String::class.java
        )
        constructor.isAccessible = true
        val proposal = constructor.newInstance(
            rs.getString("id"),
            rs.getString("order_reference"),
            rs.getString("driver_reference"),
            rs.getTimestamp("created_at")?.toInstant(),
            rs.getBoolean("is_test"),
            rs.getString("passenger_reference")
        )
        val status = ProposalStatus.valueOf(rs.getString("status"))
        val statedPrice = rs.getString("stated_price")
        val statedEtaMinutes = rs.getInt("stated_eta_minutes").let { if (rs.wasNull()) null else it }
        val respondedAt: Instant = rs.getTimestamp("responded_at")?.toInstant() ?: Instant.now()
        when (status) {
            ProposalStatus.ACCEPTED -> proposal.accept(statedPrice, statedEtaMinutes, respondedAt)
            // PRICE_PROPOSED (2026-09-05, mandatory driver-stated price):
            // the private constructor above always starts the reconstructed
            // object at OPEN, so it must be moved forward to PRICE_PROPOSED
            // the same way the ACCEPTED branch already moves it to ACCEPTED
            // -- statedPrice is non-null by the time a row can reach this
            // status (Proposal.proposePrice's own require), but a blank
            // fallback keeps this read path from throwing on a
            // pathological row rather than a domain violation.
            ProposalStatus.PRICE_PROPOSED -> proposal.proposePrice(statedPrice ?: "", statedEtaMinutes, respondedAt)
            // DECLINED: a row can reach this status via two different real
            // paths -- an outright driver decline (Proposal.decline, which
            // never sets a price) or a passenger's refusal of an already-
            // stated price (Proposal.declinePriceProposal, which leaves the
            // price as recorded). Distinguished here by whether stated_price
            // was actually persisted: replaying the two-hop
            // proposePrice-then-declinePriceProposal path when it was is
            // what correctly restores that price in memory -- a single
            // proposal.decline(respondedAt) call would silently drop it,
            // since that method never touches statedPrice by design.
            ProposalStatus.DECLINED -> if (statedPrice != null) {
                proposal.proposePrice(statedPrice, statedEtaMinutes, respondedAt)
                proposal.declinePriceProposal(respondedAt)
            } else {
                proposal.decline(respondedAt)
            }
            ProposalStatus.LAPSED -> proposal.lapse(respondedAt)
            ProposalStatus.WITHDRAWN -> {
                if (statedPrice != null) proposal.proposePrice(statedPrice, statedEtaMinutes, respondedAt)
                proposal.withdraw(respondedAt)
            }
            ProposalStatus.OPEN -> Unit
        }
        return proposal
    }
}
