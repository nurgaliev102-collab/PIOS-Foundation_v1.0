# SMS Aero delivery for identity recovery

SMS Aero is the current production provider for identity recovery SMS.
`OutboundSmsPort` remains provider-independent:
`PhoneVerificationChallengeIssuer` -> durable Identity SMS outbox ->
`SmsOutboxRelay` -> `OutboundSmsPort` -> `SmsAeroOutboundSmsAdapter` -> the
[SMS Aero normal send API](https://smsaero.ru/integration/documentation/api/).
PIOS generates its own six-digit OTP; the provider's built-in OTP and test-send
endpoints are not used. A successful API result means accepted for processing,
not received by the handset. There is no automatic retry, callback, or status
polling in this adapter.

## Identity service configuration

| Variable | Required | Source | Purpose |
| --- | --- | --- | --- |
| `PIOS_SMS_LOGIN` | Yes | Machine-scope environment | SMS Aero Basic Auth username |
| `PIOS_SMS_API_KEY` | Yes | Machine-scope environment secret | SMS Aero Basic Auth password; never commit or log |
| `PIOS_SMS_SENDER` | Yes | Machine-scope environment | Registered SMS Aero `sign` for normal send |
| `PIOS_OTP_RELAY_KEY` | Yes | Machine-scope environment secret | Base64-encoded AES-256-GCM relay key; exactly 32 decoded bytes; never commit or log |
| `PIOS_SMS_API_BASE_URL` | No | Machine-scope environment or default | Default `https://gate.smsaero.ru`; only that HTTPS host is allowed to prevent a stale SMS.RU URL receiving the new credential |
| `PIOS_SMS_CONNECT_TIMEOUT_MS` | No | Machine-scope environment or default | Default 2000 ms |
| `PIOS_SMS_READ_TIMEOUT_MS` | No | Machine-scope environment or default | Default 5000 ms |

Identity fails startup with a variable-name-only error if any required value
is absent or the relay key is malformed. `windows-services/generate-service-xml.ps1` reads these values from
Machine-scope environment and renders them into the local, gitignored
`windows-services/identity/pios-identity.xml`. Restrict access to this file:
it contains the API key. The separate `PIOS_SESSION_SECRET` still uses its
existing per-service SCM Environment mechanism documented in
`PIOS_DEPLOYMENT_SECRETS.md`. Do not print the generated XML, Basic Auth
header, API key, request body, or provider response.

The exact SMS text is `<six-digit code> — код подтверждения телефона в PIOS`.
The sender name must be registered and active for the recipient's operator;
do not substitute a guessed value. Confirm that this text is permitted for
the sign. [SMS Aero's API documentation](https://smsaero.ru/integration/documentation/api/)
notes that messages may undergo manual moderation for 5–10 minutes. That can
consume the entire 600-second PIOS OTP TTL, so the Product Owner must confirm
the account's moderation and delivery arrangements before a real recovery
test. A positive send response does not prove timely delivery.

## Submission and failure semantics

The adapter performs one form-encoded HTTPS `POST /v2/sms/send` with Basic
Auth and `number`, `sign`, and `text`, requesting JSON. It accepts a response
only when `success=true` and exactly one matching recipient has a positive
message `id` and a recognized non-failure status. `success=false`, a rejected
message status, or HTTP 4xx is `FAILED`. HTTP 5xx, malformed/incomplete
response, connection failure, and timeout are `UNKNOWN`: the provider may
already have accepted the SMS. Neither outcome triggers an automatic retry.

The OTP challenge and its PENDING outbox row are committed atomically before
the relay makes an outbound call. UNKNOWN outcomes use the C-2 durable retry
policy; explicit provider rejection does not retry. Only safe failure
classifications are logged. The anonymous recovery endpoint still responds
with `202 {}` for unknown phones, accepted requests, and provider failures.

The request path never waits for SMS Aero. It applies C-2's provisional timing
floor after recovery handling, while the separately scheduled relay submits
only durable, authorized outbox rows.

## Controlled real-SMS verification (separate Product Owner authorization)

Automated tests use a local fake HTTP endpoint and send no real SMS. After
explicit approval for one controlled SMS test:

1. Choose a consenting, already phone-verified test identity with a phone
   controlled by the Product Owner. Avoid legacy enrolment in this test
   because it requires an additional SMS.
2. Confirm the registered sender/sign works for that phone's operator, the
   message text is permitted, and any moderation delay will fit the OTP TTL.
   Confirm account balance. Provision `PIOS_SMS_LOGIN`, `PIOS_SMS_API_KEY`,
   and `PIOS_SMS_SENDER` through the Machine Environment secret procedure;
   never paste the key or number into a command history, ticket, or repo file.
3. Under a separately authorized local pilot deployment, regenerate the
   gitignored WinSW XML, update the identity JAR and restart only
   `pios-identity`. Verify startup without printing its environment or XML.
   Do not use piosapp.ru.
4. Send exactly one `POST /v1/identities/recovery/request` for the test
   identity. Expect HTTP `202` with `{}` and no OTP in the response. Confirm
   actual handset receipt independently and within 600 seconds. If the
   request times out, do not retry: SMS Aero might already have accepted it.
5. Send `POST /v1/identities/recovery/confirm` with the received code and a
   new test password. Expect HTTP `200` with a new session. Verify the prior
   password is rejected and identity's protected endpoints reject the prior
   session; verify the new session works.
6. Review only sanitized failure classifications and API status. Do not copy
   the OTP, API key, Basic Auth header, full phone, or provider response into
   logs or reports. Retire the test identity and rotate/revoke the dedicated
   test key under the established operational procedures.

The [SMS Aero status API](https://smsaero.ru/integration/documentation/api/)
can distinguish later delivery from initial acceptance. This adapter does
not poll status or install a callback.
