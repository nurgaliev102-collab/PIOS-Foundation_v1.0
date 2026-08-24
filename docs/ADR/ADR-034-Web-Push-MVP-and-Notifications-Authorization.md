# ADR-034: Web Push MVP and Notifications Authorization

## Status
Accepted

## Context

ADR-033 leaves two implementation gates unresolved: the concrete participant-facing notification need and the database technology for Notifications. The Product Owner has now explicitly authorized Web Push for the first PIOS pilot. The product decision is recorded in `PRODUCT_DECISION_WEB_PUSH_MVP.md`.

The pilot requirement is narrow: a driver must be informed about a new request without keeping PIOS in the foreground; a passenger should be informed when the driver accepts and arrives. Existing three-second polling remains the source-of-truth fallback.

## Decision

### 1. Authorized participant notifications

Notifications is authorized to consume only these participant-facing facts:

- `order.assigned` (or the equivalent dispatch event that establishes a new driver-facing assignment/proposal) -> notify the assigned driver.
- `assignment.accepted` -> notify the passenger.
- `assignment.arrived` -> notify the passenger.

The implementation must use the actual event contracts present in the repository. If the current event does not contain enough information to identify the recipient, the producer must expose the minimum required recipient reference through the approved event contract; do not infer recipients from request authentication because the current mutation endpoints are not consistently authenticated.

### 2. Notifications database technology

For the pilot implementation, Notifications uses PostgreSQL as its own dedicated database instance. This is a deliberate extension of the per-domain PostgreSQL operational model already used by the running PIOS services. It does not permit shared schemas or direct access to another domain's database.

This decision resolves the Notifications-specific technology gate left open by ADR-025 for the pilot scope. Analytics remains outside this decision and retains its independent technology evaluation.

### 3. Push subscription ownership

The push subscription belongs to Identity because Identity owns authenticated identities and is already the common authentication boundary for driver and passenger sessions.

Identity stores only subscriptions associated with the authenticated token subject. A client may never supply an arbitrary identityId for subscription ownership.

Minimum record shape:

- id
- identity_id
- endpoint (unique)
- p256dh_key
- auth_key
- user_agent (optional)
- created_at
- last_seen_at

A push endpoint returning HTTP 404/410 is treated as an expired subscription and may be removed during delivery.

### 4. Frontend delivery

The existing PWA service worker must be converted from `generateSW` to `injectManifest` so a controlled worker can implement:

- `push` event handling;
- notification display;
- `notificationclick` navigation to the relevant PIOS screen.

The existing precache behavior must be preserved. Push permission is requested only as part of an explicit user interaction/onboarding flow, never on an arbitrary page load.

### 5. External delivery

Web Push uses VAPID credentials. The private VAPID key is deployment-only secret material and follows the existing WinSW `<env>` secret injection pattern. It must never enter git, templates, logs, browser storage, or notification payloads. The public VAPID key may be exposed to the frontend through a non-secret configuration mechanism.

### 6. Payload policy

Push payloads contain only non-sensitive routing data: notification type and the relevant order/assignment identifier. No passenger name, phone number, address, price, owner credential, or API key is placed in the push payload.

Opening the notification takes the user into PIOS, where authenticated REST calls retrieve current state.

### 7. Reliability and idempotency

Push is best-effort delivery. It does not alter domain state and does not replace polling. Notifications consumers must be idempotent and use the queue/retry/DLQ principles established by ADR-031.

### 8. Explicit non-goals

This ADR does not authorize:

- SMS, Telegram, email, or native mobile push;
- notification preferences or scheduling;
- a general multi-channel notification platform;
- broad wildcard RabbitMQ bindings;
- direct cross-module database access;
- push-driven business mutations.

## Consequences

### Positive

The first pilot can test the actual operational problem of missed requests while preserving the existing polling fallback. The architecture has explicit ownership, a bounded event set, isolated persistence, and a clear security boundary.

### Negative

The PWA service-worker strategy changes and therefore requires dedicated regression testing. A new Notifications database and service increase operational surface. Browser/OS permission and platform support can prevent delivery, so the product must remain fully usable without push.

## Implementation gate

Implementation may proceed on branch `feature/web-push-mvp` only after the developer verifies the actual event schemas and existing RabbitMQ routing names. No production deployment, migration, or secret generation is authorized by this ADR alone.

## Related decisions

- ADR-025: Database Technology Decision
- ADR-031: Event Infrastructure Foundation Architecture
- ADR-033: Notifications Module and Event Consumption Boundary
- `PRODUCT_DECISION_WEB_PUSH_MVP.md`
