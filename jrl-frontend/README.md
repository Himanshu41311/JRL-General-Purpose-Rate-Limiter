# JRL — General Rate Limiter (frontend)

Landing page plus sign-up/sign-in/profile for JRL, the Redis-backed rate
limiter proxy. No build step — open `index.html`, or serve the folder with
any static file server (e.g. `npx serve .`).

## This now needs a real backend running

Sign-up, sign-in, and profile are wired to **jrl-auth-service** — a real
Spring Boot service issuing real JWTs, not the old localStorage mock.

1. Start `jrl-auth-service` (see its own README) — it runs on
   `http://localhost:8081` by default.
2. Open this frontend. `js/config.js` points at that URL — change it there
   if you run the backend somewhere else.
3. Sign up. You'll get a real JWT back, stored in `localStorage`, sent as
   `Authorization: Bearer ...` on every subsequent request.

If the backend isn't running, sign-up/sign-in will show an error saying so
(`js/api.js`'s `request()` catches the fetch failure and surfaces it) rather
than failing silently.

## Pages

- `index.html` — landing page, live token-bucket animation as the hero.
- `pages/signup.html`, `pages/signin.html` — real auth against
  jrl-auth-service.
- `pages/dashboard.html` — verifies your session against the server
  (`requireAuth()` calls `GET /api/auth/me`), then shows a **"Routes" page
  that's honestly a coming-soon placeholder** — see below.
- `pages/profile.html` — real name/email/customerId from the server, name
  is editable via `PUT /api/auth/me`.

## Why routes/policies are "coming soon"

There's still no admin API that writes `route:*` / `route-policies:*` into
Redis — that's a separate service, not built yet. Rather than fake route
creation against localStorage (which would now be inconsistent with real
server-issued accounts/customerIds), the dashboard shows the "New route"
button with a visible "Coming soon" badge; clicking it just says so. The old
mock CRUD (`route.html`, and the old `js/api.js`'s route/policy functions)
has been removed rather than left half-working.

## What's real now vs. still mock/missing

**Real:** password hashing (BCrypt), JWT issuance/validation, CORS, a proper
401 if your token is missing/expired/invalid, server-side email uniqueness.

**Still missing:** the admin API for routes/policies (hence "coming soon"),
token revocation (signing out only forgets the token client-side — see
jrl-auth-service's README), refresh tokens, password reset, and any actual
usage/metrics dashboard reading the Prometheus data the rate limiter proxy
emits.
