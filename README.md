# Kitepost

Lead generation with a human in the loop. Describe your offer, and Kitepost finds companies in the **United States, India** and other countries. It reads their websites and sorts each one as a **customer, vendor, partner or competitor**, and only customers get an email draft. You review and approve every email before it is sent, and replies are tracked in one inbox.

```
kitepost/
├── demo/        Browser-only demo (HTML, CSS, JS, JSON). No server. For presentations and user testing.
├── backend/     Spring Boot 4.1 API (Java 21). Security, pipeline, sending, reply tracking. PostgreSQL or MySQL.
├── frontend/    Next.js 16 web app. Talks to the API through /api on the same origin.
├── docker-compose.yml
├── .env.example
└── .github/workflows/ci.yml   Builds and smoke-tests the API on PostgreSQL and MySQL, builds the web app
```

## Quick start (Docker)

```bash
cp .env.example .env              # set DATABASE_PASSWORD; ADMIN_EMAIL/ADMIN_PASSWORD optional
docker compose --profile postgres up --build
# or MySQL:
DATABASE_URL=jdbc:mysql://mysql:3306/kitepost docker compose --profile mysql up --build
```

Open http://localhost:3000. If you left `ADMIN_PASSWORD` blank, the first admin is `admin@kitepost.local` and its temporary password is printed once in the backend log (`docker compose logs backend-postgres`). You must choose a new password at first sign-in.

Set `SEED_SAMPLE_DATA=true` to start with the 42-company sample campaign (US + India).

## Without Docker

1. Create a database, either PostgreSQL 14+ or MySQL 8+. The tables are created automatically by Flyway on the first start.
2. Backend: `cd backend && mvn spring-boot:run` (with the variables from `.env.example` exported; `COOKIE_SECURE=false` for http://localhost).
3. Web app: `cd frontend && npm install && npm run dev`, then open http://localhost:3000.

## How it is secured

| Area | What it does |
|---|---|
| Sign-in | Server-side sessions (Spring Session JDBC), cookie `HttpOnly`, `SameSite=Lax`, `Secure` in production, 8 h timeout. Session id rotated on sign-in and password change. |
| Passwords | bcrypt (cost 12), 12–128 characters, common passwords refused, forced change for temporary passwords. |
| Brute force | Lockout after 5 failures for 15 minutes; rate limits per IP and per email; same response time for unknown emails. |
| CSRF | Token in a `XSRF-TOKEN` cookie, echoed in the `X-XSRF-TOKEN` header on every change (Spring Security SPA pattern). |
| Roles | Admin, Editor, Viewer, enforced on the server with `@PreAuthorize`. The UI only hides what the server refuses anyway. |
| Accounts | Disabling a user or resetting a password signs them out on every device. Role changes apply on the next request. The last admin can't be removed. |
| Audit log | Sign-ins (and failures), approvals, sends, category changes, team changes, with user and IP. |
| Website reading | SSRF guard blocks private, loopback, link-local and metadata addresses on every redirect; size and time limits; robots.txt respected. |
| AI | Website text is passed to Claude as untrusted data, with instructions not to follow anything inside it. |
| Email | Dry run by default; daily cap; do-not-contact list checked right before each send; atomic claim so an email is never sent twice, even with several API instances; header-injection-safe; `List-Unsubscribe` and a postal-address footer (CAN-SPAM). |
| Data | Optimistic locking on every record; database credentials and API keys only on the server, never sent to the browser. |
| HTTP | CSP, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, Permissions-Policy, HSTS in production; error messages never include stack traces. |
| Containers | Non-root users, read-only filesystems, `no-new-privileges`; the API isn't published, only the web app. |

## Running in production

- Serve the web app over HTTPS behind a proxy, keep `COOKIE_SECURE=true`, and set `DRY_RUN=false` only after you have sent test emails to yourself.
- Use a sending domain with SPF, DKIM and DMARC set up, and keep `DAILY_SEND_LIMIT` modest while the domain warms up.
- Cold email rules differ by country (CAN-SPAM in the US, the DPDP Act in India, GDPR/PECR in Europe). Kitepost gives you the controls; checking that your use is lawful is up to you.
