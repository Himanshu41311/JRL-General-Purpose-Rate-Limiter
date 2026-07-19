# jrl-auth-service

A Spring Boot service that owns user identity **and** route/policy
registration — it's the "admin service" the rate limiter proxy was always
waiting on. Postgres (Supabase) is the source of truth for everything;
routes and policies are additionally dual-written to Redis, in the exact
shape the proxy reads at request time, so the proxy never has to touch
Postgres.

## Setup

### 1. Supabase Postgres

Create a project (or use an existing one), then get your connection details
from **Project Settings → Database → Connection string**. Set these as
environment variables before running:

```
export SUPABASE_DB_URL="jdbc:postgresql://db.<your-project-ref>.supabase.co:5432/postgres"
export SUPABASE_DB_USER="postgres"
export SUPABASE_DB_PASSWORD="<your-db-password>"
```

`spring.jpa.hibernate.ddl-auto=update` will create the `users`, `routes`,
and `rate_limit_policies` tables automatically on first run — fine for an
academic project, not how you'd manage schema on something real (use
Flyway/Liquibase there instead).

### 2. Redis

This needs to be **the same Redis instance the rate limiter proxy reads
from** — that's the whole point of the dual-write. `application.properties`
defaults to the same Upstash instance already configured in that project;
override via `REDIS_HOST` / `REDIS_PORT` / `REDIS_USERNAME` / `REDIS_PASSWORD`
/ `REDIS_SSL` if yours differs.

### 3. Run it

```
mvn spring-boot:run
```

Starts on `http://localhost:8081`.

## Endpoints

| Method | Path                                  | Auth | Body |
|--------|---------------------------------------|------|------|
| POST   | `/api/auth/signup`                    | No   | `{name, email, password}` |
| POST   | `/api/auth/signin`                    | No   | `{email, password}` |
| GET    | `/api/auth/me`                        | Yes  | — |
| PUT    | `/api/auth/me`                        | Yes  | `{name}` |
| GET    | `/api/routes`                         | Yes  | — (lists your routes, each with a live `live` status) |
| POST   | `/api/routes`                         | Yes  | `{name, targetUrl}` |
| GET    | `/api/routes/{routeId}`               | Yes  | — |
| GET    | `/api/routes/{routeId}/status`        | Yes  | — (lightweight: just `{routeId, live, checkedAt}`) |
| PUT    | `/api/routes/{routeId}`               | Yes  | `{name, targetUrl, active}` |
| DELETE | `/api/routes/{routeId}`               | Yes  | — |
| GET    | `/api/routes/{routeId}/policies`      | Yes  | — |
| POST   | `/api/routes/{routeId}/policies`      | Yes  | see "creating a policy" below |
| PUT    | `/api/routes/{routeId}/policies/{id}` | Yes  | see "updating a policy" below |
| DELETE | `/api/routes/{routeId}/policies/{id}` | Yes  | — |

### The green/red dot: `live`

Every `RouteResponse` (from `GET /api/routes`, `GET /api/routes/{id}`,
and the create/update responses) includes:

```json
{ "active": true, "live": true, ... }
```

`active` is what's stored in Postgres — "should this route be enabled".
`live` is a **live Redis read**, done fresh on every request via
`RouteHealthService` — it's true only if `route:{routeId}` actually exists
in Redis right now with `active: true` inside it. These two can disagree:
`active=true, live=false` means Postgres thinks the route should work but
the dual-write to Redis never landed (or failed) — exactly the gap called
out in "the dual-write, and its real limitation" below. That mismatch *is*
the signal a dashboard's red dot should show.

`GET /api/routes/{routeId}/status` returns just `{routeId, live, checkedAt}`
if a dashboard wants to poll the dot on a timer without re-fetching the
whole route.

### Creating a policy

```json
{
  "scope": "API_KEY",
  "identifierSource": "HEADER",
  "identifierValue": "X-Api-Key",
  "algorithm": "TOKEN_BUCKET",
  "algorithmConfig": "{\"limit\":100,\"windowSize\":1,\"windowUnit\":\"MINUTE\"}"
}
```
`identifierSource`/`identifierValue` are only required when `scope` is
`USER` or `API_KEY`.

### Updating a policy — deliberately narrower

```json
{
  "algorithm": "TOKEN_BUCKET",
  "algorithmConfig": "{\"limit\":200,\"windowSize\":1,\"windowUnit\":\"MINUTE\"}",
  "active": false
}
```
`PUT .../policies/{id}` only accepts `algorithm`, `algorithmConfig`, and
`active` — `scope`, `identifierSource`, and `identifierValue` cannot be
changed after creation. That's not an oversight: those three fields are
baked into the Redis counter key
(`rl:{routeId}:{policyId}:{scope}:{identifier}`), so changing them in place
would silently orphan whatever count was accruing under the old key. If who
a policy applies to needs to change, delete it and create a new one —
editing an existing policy is only ever safe for the algorithm, its numeric
config, and whether it's active.

`algorithmConfig` must be a JSON **string** containing
an object with either `limit` or `capacity` — validated before it's ever
written to Postgres or Redis, so a typo here is a 400, not a silently
broken policy the proxy has to skip later.

All routes/policies are scoped to the signed-in user's `customerId` — you
can only see, edit, or delete your own. Trying to touch someone else's
returns a 404, not a 403 (doesn't reveal whether it exists at all).

## The dual-write, and its real limitation

Every create/update/delete writes to Postgres first, then Redis — in
`RouteService`/`RateLimitPolicyService`, via `RedisCacheService`. This is a
**plain dual-write, not a transaction**: if the Redis call fails after
Postgres already committed, the two stores disagree until someone notices.
There's no outbox pattern, no retry queue, no reconciliation job here — a
failed Redis write is logged loudly (`log.error`) and otherwise silently
accepted. For an academic project this is a reasonable and clearly-labeled
simplification; for anything real, add a periodic job that diffs Postgres
against Redis and re-syncs, or move to an outbox/CDC pattern.

## Auth model (unchanged from before)

BCrypt password hashing, HMAC-signed JWTs (`sub`=userId, `email`,
`customerId`), 24h expiration, stateless (no revocation — see the JWT
section below). **Rotate `jrl.jwt.secret`** via the `JRL_JWT_SECRET`
environment variable before this touches anything real; the value in
`application.properties` is a placeholder.

## Not implemented

Refresh tokens, password reset, email verification, rate limiting on the
auth endpoints themselves, DB migrations (Flyway/Liquibase), and the
Postgres↔Redis reconciliation job mentioned above.
