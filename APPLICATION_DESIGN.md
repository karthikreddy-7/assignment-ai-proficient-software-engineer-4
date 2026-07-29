# Application Design

![Application design](images/application-design.png)

This is what's actually built and running — one Spring Boot instance, one Postgres
database. No gateway, no sharding (that's [SYSTEM_DESIGN.md](SYSTEM_DESIGN.md), and it's
not built).

## Why Spring Boot

Java, and a mature web framework on top of it, gets a working REST API with almost no
boilerplate — embedded server, dependency injection, JPA, validation, security, and
metrics all wired through starters instead of hand-assembled. For a service this size
that needs to be correct and easy to reason about more than it needs anything exotic,
that's the right trade. Spring Security and Spring Data JPA in particular meant the API
key check and the database layer didn't have to be written from scratch.

## Why two controllers instead of one

`UrlController` handles everything under `/api/v1/urls` — create, look up, get stats,
disable. `RedirectController` handles exactly one thing: `GET /{code}`.

They're split because they don't behave the same way. The redirect endpoint is the one
a real user's browser hits, it needs to be a plain top-level path (a short link that
isn't short defeats the point), it's the highest-traffic endpoint by far, and it doesn't
require an API key. Everything in `UrlController` is the management side — used by
whoever owns the links, always behind the API key, never on the hot path. Keeping them
apart means the redirect path stays small and fast, and doesn't carry logic that only
the management API needs.

## Request flow

A request comes in, hits the Spring Security check (validates `X-API-Key` for `POST`
and `DELETE`, lets `GET` through), and then reaches whichever controller matches the
path. The controller calls into `UrlService`, which is where the actual logic lives —
generating a code, resolving a code back to a URL, checking expiry, disabling a link.

## Why the cache

`RedirectController` → `UrlService` checks an in-memory cache (Caffeine) before it
touches the database. Redirects happen far more often than new links get created, so
the read path is the one worth optimizing. Cache-aside: read the cache first, on a miss
read the database and populate the cache, writes update the database and drop the stale
cache entry. Everything still works if the cache is empty (cold start, entry evicted) —
it's a speed-up, not a dependency.

## Why analytics is async and separate from UrlService

`AnalyticsService` records the click (count + last-clicked-at) after a redirect. It's
called from `UrlService`, but the call is async, on its own thread pool, and
`UrlService` doesn't wait for it to finish. The person who clicked a short link gets
redirected immediately — recording that it happened is bookkeeping, not something that
should ever slow down or fail the redirect itself. That's also why the arrow to
`AnalyticsService` only goes one way: it's fired and forgotten, not called for a result.

`AnalyticsService` writes through `UrlMappingRepository`, same as everything else — it
isn't a separate table or a separate database, just a separate write done off the main
request thread.

## Repository and database

One repository (`UrlMappingRepository`), one table, one database. Everything —
`UrlService` and `AnalyticsService` both — goes through the same repository. No
sharding, no read replicas, nothing split up. At this scale it doesn't need to be, and
[SYSTEM_DESIGN.md](SYSTEM_DESIGN.md) is where the "if it needed to scale" version of
this is written down instead.

## What's not shown

Things like the correlation-id filter that stamps a trace ID onto every request for
logging, and the exact error-response shape, are real but left off this diagram on
purpose — the diagram is about how a request moves through the pieces, not every filter
in the chain.
