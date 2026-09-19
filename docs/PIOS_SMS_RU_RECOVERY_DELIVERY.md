# SMS.RU delivery for identity recovery

SMS.RU is the Product Owner's first production SMS provider for PIOS. Identity uses
`OutboundSmsPort` -> `SmsRuOutboundSmsAdapter` -> the [SMS.RU HTTPS send API](https://sms.ru/docs/api/api_group_sms/send).
The adapter submits a single OTP SMS and checks both the overall response and the
recipient result. SMS.RU's `sms_id` means accepted for delivery, not delivered.
There is no automatic retry or callback in this implementation.

## Identity service configuration

| Variable | Required | Source | Purpose |
| --- | --- | --- | --- |
| `PIOS_SMS_API_ID` | Yes | Machine-scope environment secret | SMS.RU `api_id`; never commit or log |
| `PIOS_SMS_SENDER` | Yes | Machine-scope environment | Approved sender name used as `from` |
| `PIOS_SMS_API_BASE_URL` | No | Identity WinSW template or environment | HTTPS origin; default `https://sms.ru` |
| `PIOS_SMS_CONNECT_TIMEOUT_MS` | No | Identity WinSW template or environment | Default 2000 ms |
| `PIOS_SMS_READ_TIMEOUT_MS` | No | Identity WinSW template or environment | Default 5000 ms |

The identity service fails startup when the API ID or sender is absent. The
`windows-services/generate-service-xml.ps1` script reads the two required
values from Machine-scope environment and renders them only into the local,
gitignored `windows-services/identity/pios-identity.xml`. This follows the
existing WinSW owner-credential mechanism. The separate
`PIOS_SESSION_SECRET` still uses its existing per-service SCM Environment
mechanism documented in `PIOS_DEPLOYMENT_SECRETS.md`; it is not managed by
this generator. Restrict access to the generated XML because it contains
secrets. Do not print the API ID, the generated XML, or the SMS request body.

Register `PIOS_SMS_SENDER` for the recipient operators before production use.
The exact message is `<six-digit code> — код подтверждения телефона в PIOS`.
Submit the corresponding authorization template `%d — код подтверждения телефона в PIOS`
for operator approval under the same sender. SMS.RU [requires the code,
purpose and application name in an authorization template](https://sms.ru/docs/templates).
The provider may reject an unapproved sender or charge a different tariff
when text does not match an approved template. Keep the account funded.

## Failure semantics

An HTTP 4xx or explicit SMS.RU `ERROR` is `FAILED`. HTTP 5xx, malformed or
incomplete response, and transport failure or timeout are `UNKNOWN` because
the provider may have accepted the request. Both are logged internally as
classification only; the existing OTP remains in its bounded, single-use
challenge after the database transaction commits. A new user request
supersedes it. There is no blind retry or second OTP generated for a
provider failure. The recovery API still returns `202 {}`.

The current request path performs the HTTPS call only for an eligible
account. Its latency may therefore reveal account eligibility to a
determined caller even though HTTP status and body are generic. Eliminating
that timing channel requires an independently designed asynchronous
delivery path with protected OTP handling; it is not claimed closed here.

## Controlled real-SMS verification (separate Product Owner authorization)

No automated test sends to SMS.RU. SMS.RU `test=1` is a simulation and
cannot prove handset receipt. After explicit approval for one controlled
SMS test:

1. Use a dedicated, consenting Russian test number and identity. Obtain a
   dedicated SMS.RU API ID through the secret channel; do not place either
   value in a command history, ticket, repository file, or test fixture.
2. Confirm the sender and authorization template are approved for the test
   number's operator and the account has sufficient balance. Provision
   `PIOS_SMS_API_ID` and `PIOS_SMS_SENDER` in Machine-scope environment.
   Regenerate local WinSW XML and restart only `pios-identity` under the
   separately authorized deployment procedure.
3. Ensure the test identity's on-file phone is verified. If it is a legacy
   identity, use its existing Bearer session with
   `POST /v1/identities/me/phone/verify/request`, receive the SMS, then
   `POST /v1/identities/me/phone/verify/confirm` with the code. This step
   also requires authorization for the real SMS.
4. Send `POST /v1/identities/recovery/request` with JSON `{ "phone":
   "<approved test number>" }`. Expect HTTP `202` with `{}` and no OTP in
   the response. Independently confirm handset receipt of exactly one SMS.
5. Send `POST /v1/identities/recovery/confirm` with the same phone, received
   `code`, and a new test password. Expect HTTP `200` with a new session.
   Verify the prior password is rejected and identity's protected endpoints
   reject the prior session.
6. Review only sanitized delivery classifications and API status; never
   copy the OTP, API ID, full phone or provider response to logs/reports.
   Retire the test account under the established data procedure and rotate
   or revoke the dedicated test API ID when the controlled test is over.

The [SMS.RU status endpoint](https://sms.ru/docs/api/api_group_sms/status)
can distinguish later delivery from initial API acceptance. This adapter
does not poll status or install a callback.
