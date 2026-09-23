# C-2 recovery-request timing decision

## Status

**PROVISIONAL implementation decision, 2026-09-22.** The floor is implemented
with the provisional default below, but its value must be recalibrated using
the measurement protocol before any production rollout. This document does
not authorize a deployment, a migration, or SMS submission.

## 1. Threat

`POST /v1/identities/recovery/request` always returns `202 {}`, but an
attacker could otherwise distinguish a recovery-eligible identity from an
unknown or unverified phone by timing the request. Eligibility performs OTP
generation, PBKDF2, AES-GCM, and durable database work; ineligible paths do
not. The timing defense must not create OTPs for ineligible phones or await
SMS delivery.

## 2. Current request paths

All paths enter `IdentityController.requestRecoveryFromAddress` and derive a
client key, acquire both existing request budgets, parse the phone, and query
`IdentityRepository.findByPhone`.

- **Unknown:** the identity lookup returns no row.
- **Known, unverified:** lookup returns a row whose `phoneVerifiedAt` is null.
- **Eligible:** `PhoneVerificationChallengeIssuer.issue` generates an OTP;
  opens and commits a database transaction; locks the challenge guard;
  supersedes/cancels old work; PBKDF2-hashes and AES-GCM-encrypts the OTP;
  inserts the challenge and outbox row.

The relay is not called from this path. SMS is never awaited.

## 3. Measurement methodology

The manual test-source harness
`RecoveryRequestTimingDecisionBenchmark` uses the hard-coded,
Flyway-managed `pios_identity_test` database. It directly calls the
controller method, measures immediately around that call, uses fresh phone
and identity values per sample to avoid rate-limit reuse, and never invokes
the relay or `OutboundSmsPort`.

For calibration, run the harness in a production-like non-production
environment with:

1. the production JDK, password iteration count, JDBC driver, PostgreSQL
   version/storage, pool configuration, and C-2 schema;
2. at least 100 warm-up requests per path;
3. at least 10,000 samples per path, separately under nominal load and a
   representative normal database-contention load;
4. controller-entry to post-`handle()` timing only; exclude HTTP network and
   SMS-provider time; and
5. recorded min, P50, P95, P99, P99.9, and max without logging phones, OTPs,
   or secrets.

## 4. Measurement results

No reliable local distribution exists yet. The isolated test-database harness
was prepared, but the local Gradle process exited before compilation or test
results, so no sample count or percentile is asserted here. In particular,
this document does not manufacture P50/P95/P99 values.

## 5. Floor selection rule

The baseline population is the **eligible controller-to-post-`handle()` path**
from the calibrated environment, including normal representative database
contention and excluding SMS.

Let `E` be the larger eligible-path P99.9 measured under nominal and normal
contention runs. The promoted floor is:

```
ceil_to_10ms(E × 1.25)
```

This is intentionally not merely P99: it uses a tail percentile, includes a
contention scenario, adds a 25% scheduling/storage margin, and rounds upward
to a deterministic configuration value. Legitimate requests that already
exceed the floor are never failed, retried, or delayed further; they return
when their normal work completes and are recorded as floor overruns for
recalibration.

## 6. Proposed floor value

Until the calibration protocol succeeds, set the documented default to
**`1000 ms` — PROVISIONAL**. It is a conservative temporary boundary, not a
measured production P99. It must be replaced by the selection rule in Section
5 before production enablement.

Implemented deterministic property:

```
pios.identity.recovery.timing.floor-ms=1000
```

## 7. Scope

The floor applies to every **syntactically valid, non-rate-limited** anonymous
recovery request, whether the phone is unknown, known/unverified, or
recovery-eligible.

It does not apply to malformed phone input or to a request rejected by either
rate limiter. Neither path reaches an account-eligibility decision; padding
rate-limited traffic would add avoidable denial-of-service cost. This scope is
internal only: all cases continue to receive the same `202 {}` response.

## 8. Implementation location

At the first line of `IdentityController.requestRecoveryFromAddress`, capture
a monotonic start time. Execute `requestRecoveryApplicationService.handle` as
it exists. After that call returns, and immediately before constructing the
accepted response, sleep/park only for the remaining floor duration.

`handle()` has already completed its transaction before this point. Therefore
the padding holds no transaction or JDBC connection, makes no SMS call, and
does not change outbox behavior.

## 9. Acceptance tests

- Unit tests use injected monotonic clock/delay seams to prove valid unknown,
  unverified, and eligible paths all have the same minimum duration.
- Unit tests prove padding begins only after `handle()` returns, and that no
  `OutboundSmsPort` call occurs in the request path.
- Unit tests prove malformed and rate-limited scope exclusions.
- In the calibrated integration benchmark, use at least 10,000 samples per
  path. Compare unknown-vs-unverified and unknown-vs-eligible distributions
  with a bootstrap confidence interval against a same-path control split.
  At P50 and P95, each cross-path difference must be no greater than the
  control split's 99% upper confidence bound. Eligible floor overruns must be
  at or below 0.1% under the calibrated nominal and normal-contention loads.

## 10. Operational considerations

The floor applies only after rate limiting and only to valid requests, so it
does not intentionally amplify invalid-request traffic. Monitor floor
overruns, request volume, and configured floor value without logging phone
numbers or OTPs. Recalibrate after changes to PBKDF2 iterations, JVM,
database/storage, JDBC configuration, or contention profile.

## 11. SMS boundary

SMS is never awaited by the timing floor or by the recovery request. The
request commits a challenge and PENDING outbox row; the separately scheduled
relay performs SMS submission later.
