# Kitepost API (Spring Boot)

Spring Boot 4.1.1 · Java 21 · Spring Security 7 · Spring Session JDBC · Spring Data JPA (Hibernate 7) · Flyway · Jakarta Mail · PostgreSQL or MySQL.

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/kitepost   # or jdbc:mysql://localhost:3306/kitepost
export DATABASE_USER=kitepost DATABASE_PASSWORD=... COOKIE_SECURE=false
mvn spring-boot:run          # http://localhost:8080 ; health: /actuator/health
mvn verify                   # unit tests + jar in target/kitepost-backend.jar
```

The database is picked from the JDBC URL. Flyway runs `db/migration/postgresql` or `db/migration/mysql` (`{vendor}`), so both schemas stay in step. All settings are environment variables, listed in `../.env.example`.

## Layout

```
com.kitepost
├── config/       AppProperties (all settings), Json helpers
├── controller/   REST controllers (/api/...)
├── dto/          request/response records (Dtos)
├── entity/       JPA entities (@Version on each), JSON column converters, value records
├── repository/   Spring Data repositories; LeadRepository.claimForSending is the atomic send claim
├── exception/    ApiException and the error handler ({"error": "..."} everywhere)
├── security/     SecurityConfig, CSRF handler, SessionUserFilter, rate limiter, password policy, first-admin bootstrap
└── service/      campaigns, leads, replies, users, audit, stats, sample data
    ├── pipeline/ search → read site (SSRF-guarded) → classify → draft; Claude client; keyword-rule fallbacks
    ├── mail/     sending (SMTP or dry run), IMAP reply polling, reply matching and intent, simulated replies
    └── job/      schedulers (send tick, reply poll) and resuming campaigns at startup
```

## API

All endpoints are under `/api` and need a session, except `GET /api/auth/csrf`, `POST /api/auth/login` and `/actuator/health`. Every non-GET needs the `X-XSRF-TOKEN` header.

| | Viewer | Editor | Admin |
|---|:-:|:-:|:-:|
| See campaigns, leads, replies, stats | ✓ | ✓ | ✓ |
| Create and run campaigns, edit and approve emails, answer replies, add to do-not-contact list | | ✓ | ✓ |
| Team (`/api/users`), audit log (`/api/audit`), remove from do-not-contact list | | | ✓ |

## Verification status

This code was written in an environment where Maven Central was blocked, so it **has not yet been built with Maven or run against the real Spring libraries**. What was checked:

- All 74 source files compile with `javac` (Java 21) against hand-written stubs of the Spring, Jakarta and Jackson APIs they use.
- The unit tests (`PipelineRulesTest`, `SecurityRulesTest`: strategy, classifier, email extraction, HTML parsing, SSRF guard, password policy, rate limiter) pass.
- Both Flyway migrations were applied to real PostgreSQL 16 and MySQL 8 servers.
- The Next.js app was tested end to end in a browser against a stand-in server that copies this API's security behaviour.

The first `mvn verify` (locally or in CI, `.github/workflows/ci.yml`) is the real build. It may turn up small API mismatches that the stubs couldn't catch, and they should be quick to fix. The CI job then starts the jar on PostgreSQL and on MySQL and smoke-tests sign-in, CSRF, roles and the campaign flow.
