# JRL — General Rate Limiter (frontend)

Landing page plus sign-up/sign-in/profile/route/policy management for JRL,
the Redis-backed rate limiter proxy. No build step — open `index.html`, or
serve the folder with any static file server (e.g. `npx serve .`).

## Backend

`js/config.js` points at the deployed backend:

```
https://jrl-general-purpose-rate-limiter-production.up.railway.app
```

Everything — sign-up, sign-in, profile, routes, policies — is a real HTTP
call to that URL. Change `apiBaseUrl` there if you're running the backend
somewhere else (e.g. locally on `http://localhost:8081`).

## Pages

- `index.html` — landing page, live token-bucket animation as the hero.
- `pages/signup.html`, `pages/signin.html` — real auth.
- `pages/dashboard.html` — lists your routes, each with:
  - a **green/red dot** — a live Redis check (`GET /api/routes/{id}/status`),
    polled every 8s. This can legitimately disagree with the "active" pill
    next to it: `active` is what Postgres has stored, the dot is whether the
    proxy would actually find the route in Redis *right now*. If they
    disagree, the dual-write from the backend silently failed — see that
    project's README.
  - create / delete actions.
- `pages/route.html?id=...` — edit a route's name/URL/active state, force a
  fresh live-status check, and manage its policies (add/edit/delete).
- `pages/profile.html` — name/email/customerId from the server.

## Editing a policy is deliberately restricted

Creating a policy lets you set scope, identity source, algorithm, and
limits. **Editing one only lets you change the algorithm, its limit/window,
and whether it's active** — the scope and identity-source fields are shown
disabled with a note explaining why (they're baked into the Redis counter
key the rate limiter proxy uses; changing them in place would orphan an
existing counter). This mirrors the backend's `PolicyUpdateRequest` exactly
— the UI isn't just hiding fields cosmetically, the API itself rejects them
on update.

## What's real vs. still missing

**Real:** every button on every page above calls the actual backend. No
localStorage mock remains anywhere in this project.

**Still missing:** any usage/metrics dashboard reading the Prometheus data
the rate limiter proxy itself emits (separate from this admin API entirely),
and this frontend doesn't handle a 401 mid-session on the routes/policies
pages by redirecting to sign-in — only the initial page load is guarded via
`requireAuth()`. If your token expires while you're sitting on a page, the
next action will show an error message rather than bouncing you to sign-in.
