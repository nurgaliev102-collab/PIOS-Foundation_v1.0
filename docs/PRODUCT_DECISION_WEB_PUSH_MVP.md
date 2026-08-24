# Product Decision: Web Push MVP for the First PIOS Pilot

## Status
Accepted by Product Owner

## Purpose

The first real PIOS pilot must not require a driver to keep the PIOS page open and repeatedly check it in order to discover a new customer request. Web Push is therefore part of the pilot-ready product surface, not a post-pilot enhancement.

## Participant-facing needs

### Driver
When a customer creates a request that is assigned/proposed to the driver, PIOS should notify the driver even when the PIOS page is in the background or the phone is locked, subject to browser/OS permission and support.

### Passenger
When the driver accepts the passenger's request, and when the driver arrives, PIOS should notify the passenger so the passenger does not have to keep the page in the foreground.

## MVP notification events

Only these participant-facing notifications are authorized for the MVP:

1. `order.assigned` / new driver proposal — notify the driver that a new request is waiting.
2. `assignment.accepted` — notify the passenger that the named driver accepted the request.
3. `assignment.arrived` — notify the passenger that the driver has arrived.

No other event is authorized by this decision.

## Product constraints

- Polling remains enabled and is the fallback. Push must never become the only source of truth for order state.
- Notifications are informational only. A notification never accepts, declines, assigns, starts, completes, or cancels an order.
- Notification payloads contain routing data only; participant details are re-fetched through authenticated REST after opening PIOS.
- The user must be able to deny browser notification permission without losing access to PIOS.
- No SMS, Telegram, email, native mobile application, notification preferences, or multi-channel orchestration is part of this MVP.
- The first pilot will measure whether participants notice and act on notifications; this is product evidence, not assumed success.

## Pilot success signal

During the first real pilot, record whether the driver can receive and act on a new-request notification without manually keeping PIOS open. Record passenger reactions to acceptance/arrival notifications. Do not infer product-market fit from delivery alone.

## Architecture consequence

This decision provides the missing product/business requirement referenced by ADR-033. The implementation must still obey the architecture decision for persistence technology, event consumption, data ownership, idempotency, and external delivery boundaries.
