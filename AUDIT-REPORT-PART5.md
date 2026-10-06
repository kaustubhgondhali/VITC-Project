# AUDIT REPORT — PART 5/12: Main Admin Test Email

## Implemented

The existing **Main Admin → Email / SMTP Settings** page now includes:

- `Test Email Address`
- `Send Test Email`
- inline success and failure feedback

The existing Save Settings flow and the existing **Students → Credential
Emails** flow were left in place.

## Backend flow

The new admin-only endpoint is:

```text
POST /api/v1/admin/smtp-settings/test
Body: { "to": "owner@example.com" }
```

It uses the existing admin session headers and the existing encrypted
`smtp_settings` database row. The password is decrypted only in memory while a
short-lived SMTP sender is created. The sender calls `JavaMailSender.send()`;
the API reports success only after that call returns. SMTP connection,
authentication, TLS, timeout, and mail-server rejection failures are caught
and returned as useful safe messages.

The browser never receives or displays:

- SMTP password
- Razorpay secrets
- JWT/session secrets
- student passwords
- Java stack traces

## Configuration

1. Start MySQL and create/import the VITC database using the SQL files in
   `database/`.
2. From `backend/`, copy `.env.example` to `.env`.
3. Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
4. In the Main Admin panel, open **Email / SMTP Settings**.
5. Enter the SMTP host, port, username, password/app password, From Email,
   and From Name, then click **Save settings**.
6. Enter a real recipient address and click **Send Test Email**.

For Gmail, use a Google App Password rather than the normal account password.
The test requires a reachable SMTP server and valid credentials; it is not a
simulation.

## Run

Backend:

```bash
cd backend
mvn spring-boot:run
```

Static frontend, from the project root in another terminal:

```bash
python -m http.server 5500
```

The frontend API base is `/api/v1`, so the backend must be reachable at
`http://localhost:8080`.

## Verification

The Spring Boot test suite passes, including the new assertion that the test
endpoint rejects unauthenticated callers. A live delivery test requires the
owner's real SMTP credentials and a real recipient, so it should be performed
from the Main Admin page after configuration.