# Test / Production RabbitMQ Isolation

Status: Operational convention, established 2026-09-02 in direct response to a real incident, the same day and immediately after [docs/TEST_DATABASE_ISOLATION.md](TEST_DATABASE_ISOLATION.md). Not a Product Decision, not an ADR — this document records a testing-infrastructure convention, not a change to any domain, capability, business rule, or event contract. See [PIOS_REALITY_AUDIT.md](../PIOS_REALITY_AUDIT.md) Section 19 for the PostgreSQL incident this one is a direct sequel to.

## What happened

Three backend modules use RabbitMQ (`driver-management`, `order-management`, `dispatch`; `identity`, `passenger-experience`, and `network-management` have no RabbitMQ dependency at all). Each of the three had exactly one test-only connection factory, `internal object RabbitMQTestConnection` (`src/test/kotlin/.../persistence/RabbitMQTestConnection.kt`), and that factory's host, port, username, and password were byte-for-byte identical to production's own `application.yml` (`127.0.0.1`, `5672`, `guest`/`guest`) — with no vhost set at all, meaning both production and every test run implicitly shared RabbitMQ's own default vhost, `/`. Every test's own topology-declaration code (`RabbitMQTestObserverQueue`, `*TestListenerHarness`, `*MessagePublisher`) imported and reused production's own exchange/queue/routing-key constants directly (`RabbitMQEventPublisher.EXCHANGE_NAME`, `RabbitMQTopologyConfiguration.PRODUCER_EXCHANGE_NAME`, etc.), so tests were not merely *able* to reach production's topology by accident — they were written to deliberately publish to, bind to, and in some harnesses (`AssignmentCompletedTestListenerHarness`, `DispatchTestListenerHarness`) `purgeQueue()` the exact same queue names production's own live consumers are bound to and draining in real time.

While verifying [docs/TEST_DATABASE_ISOLATION.md](TEST_DATABASE_ISOLATION.md)'s own fix on 2026-09-02, `order-management`'s full test suite (`./gradlew :order-management:test`) produced 9 real failures, all in `AssignmentCompletedConsumerIntegrationTest`/`AssignmentAcceptedConsumerIntegrationTest`-shaped tests: each published a real `AssignmentCompleted`/`AssignmentAccepted`-shaped message onto the real, shared `dispatch.events` exchange, and the test's own private listener harness lost the race for it against production's own live `order-management` consumer (confirmed still running throughout, on its own stable PID). The test's own `awaitUntilNotNull` timed out waiting for a message production had already consumed. Verified at the time that this did not write any bad data to production (`pios_order_management.orders` row count and newest `created_at` unchanged immediately before/after) — but the mechanism itself (a fully shared broker, vhost, and topology, with zero environment separation) was real, reproducible, and a second, independent incident risk from the PostgreSQL one, which is why `dispatch`'s and `network-management`'s test suites were deliberately not run at that time.

A read-only inspection of the shared broker's default vhost (`/`) on 2026-09-02, purely administrative (`rabbitmqctl list_queues/list_exchanges/list_permissions`, no message published, consumed, or purged), confirmed the exposure was live, not theoretical: `dispatch.from-driver-management` held 15 unconsumed messages with 0 registered consumers at the time of inspection, `order-management.from-dispatch` and `order-management.from-dispatch.assignment-completed` each had a live production consumer attached, and every exchange a test module's `RabbitMQEventPublisherTest`/consumer-integration-test suite touches (`dispatch.events`, `driver-management.events`, `order-management.events`) was confirmed to be the literal, single, shared production exchange — not a test-only copy. (The 15-message backlog with no attached consumer on `dispatch.from-driver-management` is itself worth separate investigation — noted here as an observation, deliberately not investigated or fixed as part of this task, per this task's own explicit scope boundary.)

## The fix

RabbitMQ (unlike PostgreSQL) has a built-in, broker-enforced namespace primitive for exactly this: **virtual hosts**. A vhost is not a naming convention — a client connection authenticates to exactly one vhost, and every operation on that connection (declaring an exchange/queue, binding, publishing, consuming, purging, deleting) is scoped to that vhost alone. A user's permissions are granted per-vhost; a user with no permission grant on a vhost cannot open a connection to it at all, regardless of what exchange/queue name it asks for. This makes a dedicated vhost a *stronger* isolation boundary than the `_test`-suffixed-database-name convention `docs/TEST_DATABASE_ISOLATION.md` had to invent for PostgreSQL (which has no per-connection namespace of that kind) — and it means test topology can keep using the exact same exchange/queue/routing-key names as production (deliberately preserved, so tests still exercise the real production topology shape, per ADR-031) without any risk of collision, because the vhost boundary makes them different objects entirely.

Two new, additive RabbitMQ objects were provisioned on the same broker instance — nothing belonging to production (`/`, `guest`, or any exchange/queue/binding on `/`) was modified, and the broker was not restarted:

| | Production (untouched) | Test (new) |
| --- | --- | --- |
| Virtual host | `/` (RabbitMQ's own default) | `pios-test` |
| User | `guest` (`administrator` tag, unchanged) | `pios_test` (no admin tag; least-privilege) |
| Permissions | `guest` has `.*`/`.*`/`.*` on `/` only, as before | `pios_test` has `.*`/`.*`/`.*` on `pios-test` **only** — no grant on `/` exists, so a connection as `pios_test` to `/` is refused by the broker itself |
| Host : Port | `127.0.0.1:5672` (same broker) | `127.0.0.1:5672` (same broker — only the vhost and credentials differ) |

`RabbitMQTestConnection.kt` (one file per RabbitMQ-using module: `driver-management`, `dispatch`, `order-management`) now builds its `CachingConnectionFactory` with `virtualHost = "pios-test"`, `username = "pios_test"`, and the generated `pios_test` password, instead of production's own `guest`/`guest` on the implicit default vhost. **All of it is hardcoded, exactly as `PostgreSQLTestDatabase`'s own fix is — deliberately not configurable through an environment variable, system property, or `application-test.yml`.** A configuration knob would only reintroduce a way to point tests at production by misconfiguration, the exact failure mode this fix removes.

### Fail-closed guard

Each `RabbitMQTestConnection.connectionFactory` initializer opens with:

```kotlin
check(TEST_VIRTUAL_HOST.isNotBlank() && TEST_VIRTUAL_HOST != "/") { ... }
```

This is the code-level half of "no silent fallback to production" — it only ever fires if a future edit changes the hardcoded vhost constant back to `/` (or blanks it) by mistake, e.g. someone "just trying to get tests passing." The broker-level half is already absolute regardless of this check: `pios_test` has no permission grant on `/` at all, so even a hypothetical connection attempt against `/` is refused by RabbitMQ itself (`ACCESS_REFUSED`), not silently accepted. There is no code path — today, or introduced by a future edit to this one file — that resolves a test run onto production's own vhost without both of these independently failing first.

## Setting up a new clone / new machine

Before running any RabbitMQ-touching backend module's tests (`driver-management`, `dispatch`, `order-management`), provision the same two objects on your local broker (idempotent — safe to re-run; `add_vhost`/`add_user` no-op if they already exist, `set_permissions` simply re-asserts the grant):

```powershell
$rmq = "C:\Program Files\RabbitMQ Server\rabbitmq_server-<version>\sbin\rabbitmqctl.bat"
& $rmq add_vhost pios-test
& $rmq add_user pios_test "u2cZAscL4EP4dCwAFHSeDeP2elucyROf"
& $rmq set_permissions -p pios-test pios_test ".*" ".*" ".*"
```

(Password above matches what is hardcoded in all three `RabbitMQTestConnection.kt` files today — this is a local-only development credential, scoped to a vhost with nothing of value in it, never a production secret; change it in all three files together if you ever want a different one for your own machine.)

A module's RabbitMQ-touching tests fail at connection time (a clear AMQP `ACCESS_REFUSED`/authentication error, not a silent fallback) if `pios-test`/`pios_test` do not exist yet on your broker — this is intentional, mirroring `PostgreSQLTestDatabase`'s own "no default that would let a test run succeed by accident against something else."

## Convention for any future RabbitMQ-using module

Any new backend module that needs a real-broker RabbitMQ integration test must follow the same shape: its own `RabbitMQTestConnection.kt`, connecting to the `pios-test` vhost with the `pios_test` user. Test topology (exchange/queue/routing-key names) may keep reusing production's own constants exactly as today — the vhost boundary, not a naming convention, is what makes this safe.

### `core` (PIOS Core — Slice 01, ADR-067) — fail-closed configurable variant

`core` keeps the `pios-test` vhost / `pios_test` user as its **default** namespace, but reaches it through `backend/core/src/test/kotlin/com/pios/core/qa/CoreQaEndpoints`, which runs `CoreQaSafetyGate` before building the connection factory. ADR-067 § QA / Production Gate item 4 (amended 2026-09-11) requires *"QA execution MUST fail closed if the test process can address a production RabbitMQ namespace"* — so the Gate rejects a resolved vhost of `/` (or blank, or `%2F`), the `guest` user, the pilot VPS host, and an absent/invalid host or port. The vhost/user/host/port are overridable per `-Dpios.core.qa.rabbitmq.*` / `PIOS_CORE_QA_RABBITMQ_*` (for a fully separate QA broker), but **no override can select a production value** — the Gate makes those invalid. Every FAIL branch has a unit test (`CoreQaSafetyGateTest`). This is stricter than a hard-coded `RabbitMQTestConnection.kt`: a hard-coded constant is still `/` one careless edit away, whereas the Gate refuses it.

## What this fix does not cover

- **This does not undo or interact with** the 76 rows already written to production PostgreSQL on 2026-09-02, nor the `dispatch.from-driver-management` queue's own pre-existing 15-message, 0-consumer backlog observed while writing this document — both are separate, out-of-scope matters, recorded here only as things this fix does not touch.
- **This does not add a CI environment.** No CI exists in this repository today; if one is added later, it must provision `pios-test`/`pios_test` on whatever broker it runs against (or, per this task's own instruction, choose a different isolation mechanism deliberately at that time) — this convention does not decide that in advance.
- **This does not change any event contract, exchange name, queue name, or routing key.** Production's topology (`driver-management.events`, `order-management.events`, `dispatch.events` and every queue/binding declared against them) is completely unmodified; this fix is entirely about *which connection* test code uses, never *what* it publishes or where.
