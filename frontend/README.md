# Kitepost web app (Next.js)

Next.js 16 · React 19 · TypeScript. No database access and no secrets here: the browser and this server only talk to the Spring Boot API.

```bash
npm install
BACKEND_URL=http://localhost:8080 npm run dev     # http://localhost:3000
npm run typecheck && npm run build                 # production build (output: standalone)
```

`next.config.ts` forwards `/api/*` to `BACKEND_URL`, so the session and CSRF cookies are same-origin and no CORS is needed. The forwarding target is fixed at build time, so pass `BACKEND_URL` as a build argument in Docker. It also sets the security headers (CSP, frame denial, no-referrer, HSTS in production).

```
src/app/        pages: login, account, campaigns, campaigns/[id], campaigns/new, review, inbox, settings
src/components/ AppContext (session, roles, polling), Shell (navigation), LeadDrawer, shared UI
src/lib/api.ts  API client: CSRF header, retry once on an expired token, sign-out and password-change events
src/lib/types.ts response types, matching the API's records
```

Roles change what is shown (Viewer sees everything read-only, and only Admins see Team and Audit log), but the API enforces every rule itself.
