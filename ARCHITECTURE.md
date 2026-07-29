# Architecture

How the work was approached for this take-home: what got built, how it was
broken down, how AI was used, how it was tested, and what's still out of scope.

The running design is in [APPLICATION_DESIGN.md](APPLICATION_DESIGN.md). The
"if this needed to scale" picture is in [SYSTEM_DESIGN.md](SYSTEM_DESIGN.md).
Live request + metrics captures from Docker are in [DEMO.md](DEMO.md).

## What's running

One Spring Boot app, one Postgres database, brought up together with Docker
Compose.

Request path in short: client hits the app → Spring Security checks the API key
on writes → controller → `UrlService` → cache and/or Postgres → response. On a
redirect, click counting is kicked off on a separate thread so the 302 doesn't
wait on it.

Stack: Java 21, Spring Boot, Security, JPA, Flyway, Caffeine, Micrometer /
Prometheus, springdoc, Maven, Docker. Tests use H2 so `./mvnw test` doesn't need
Postgres.

A few decisions that mattered:

- Codes come from a DB sequence, encoded base62 — short, no collision retry loop
- Cache is only on the redirect read path; writes go to the DB and drop the cache
  entry
- Click counting is async — redirect speed matters more than the counter being
  perfectly up to the millisecond
- API key on POST/DELETE only; browsers have to open short links without one
- Flyway owns the schema; Hibernate just validates it at boot

## Three scenarios

The assignment wanted greenfield, brownfield, and something ambiguous. Here's
how that mapped onto this repo.

### Greenfield — core API

Build the shortener from the empty Spring Boot project: shorten, redirect,
metadata, stats, store it in Postgres.

Order of work was roughly: table + repository → code generation and URL checks
→ service → controllers → cache → async clicks → run it locally against
compose Postgres.

AI helped with boilerplate. I owned the schema, the code format, where the cache
sits, and what stays off the request thread. Checked with unit tests, a full
create → redirect → delete integration test, and hitting the live API.

### Brownfield — harden what already worked

Once the core path worked: lock down writes with an API key, put a correlation
id on every request, add metrics that are actually useful, and package app + DB
in Docker.

Touched security filters, logging, Micrometer wiring, the Dockerfile, and
compose. Landed in smaller commits rather than one dump. One thing AI wouldn't
have caught without a live check: Caffeine's `cache_puts_total` / `cache_size`
don't mean much with a manual put — `cache_gets_total` is the one that does.
That's written up under Observability below.

Verified with 401 tests, `docker compose up --build`, and a smoke pass
(create, redirect, stats, prometheus scrape, reject missing key).

### Ambiguous — "analytics" and "production ready"

"Analytics" could mean a warehouse. I took it as click count + last clicked time
on the same row, updated after redirect, not a separate events pipeline.

"Production ready" for two or three days means: API key on mutations, input
validation, error JSON with a trace id, migrations, health + prometheus,
Docker, and a real test suite. It does **not** mean SSO, multi-region, or
shipping Grafana in this repo.

If product later wants full click streams, that becomes an append-only events
table (or outbox), not more columns on `url_mapping`.

## How AI was used

I treated AI as a sparring partner and a build accelerator, not as the architect.
Design came first from me; implementation went through AI in small phases I could
still own and roll back.

### Design first, then argue with the model

I started on system design myself — gateway vs app responsibilities, why multiple
instances, why the database has to scale out at all, what a short code even is.
Only after I had a stance did I put it in front of AI and ask it to push back:
does sharding actually belong here, what should the shard key be, why encode the
shard into the short code instead of a lookup table, what falls over if you get
that wrong. Same pattern for application design: I sketched controllers, cache
placement, async analytics, then made the model debate those choices until the
trade-offs felt honest enough to write down.

That order mattered. If I had asked AI to "design a URL shortener" first, I would
have been reviewing a generic answer. By bringing my own decisions in and fighting
for them, I kept ownership of the architecture and used AI to stress-test it.

### Build in small green commits

For the technical build-out I did use AI heavily — scaffolding, wiring, first-pass
service and controller code, tests, Docker, docs polish. Not as one giant
generation. As a sequence of slices that match the commit history:

1. Spring Boot scaffold  
2. Config, datasource, error envelope  
3. Domain, Flyway, repository  
4. Shorten / redirect / cache / async analytics as one vertical slice  
5. Cache fix when the first version was wrong  
6. Security, correlation ids, API docs  
7. Logging and metrics verification  
8. Docker + remaining docs  

Each phase had a clear "done": app still boots, relevant tests pass, and the
diff is small enough that I could actually read what was about to land. I
reviewed every commit's git diff myself before moving on. If something looked
off, I fixed it in that phase or rolled back — I did not stack untested AI output
and hope the next step would clean it up.

That is also why the history has a dedicated cache fix commit. The first cut was
not correct; tests and a real look at the call path caught it; the fix is its own
change so the mistake and the correction are both visible.

### What AI was good at vs what I kept

| AI helped with | I kept ownership of |
|---|---|
| Boilerplate and Spring wiring | System and application design choices |
| First-pass implementations per phase | Whether a phase was actually done |
| Test stubs and expanding cases | What correctness means for that slice |
| Drafting / tightening docs | Final wording and what we claim is built |
| Suggesting alternatives in design debates | The decision after the debate |

Examples of not taking AI at face value: the cache path had to be reworked after
self-invocation / wrong layering showed up; Caffeine's `cache_puts_total` and
`cache_size` look useful until you scrape a live process and see they do not
mean what you hoped — that only showed up when I verified metrics against a
running instance, not when the code was first generated.

### Traceability in practice

I did not keep a separate "AI change log" file. Traceability for this project is
the commit history plus green tests at each step: each commit is a reviewable
unit, the app was working after each one, and anything high impact (security,
schema, cache behavior, Docker networking) only stayed if it survived that gate.
AI proposed a lot of the text inside those commits; I approved what shipped.

## Testing

- Unit tests for code generation, URL safety, service rules
- Integration tests with MockMvc + H2 for the full HTTP round trip
- A concurrency test for click counting under parallel redirects
- Cache hit/miss coverage on the lookup path
- Docker smoke against real Postgres for the end-to-end path reviewers will run

Trade-offs I accepted: H2 in CI is fast and has zero setup, but it isn't
Postgres — Docker covers the real DB. In-memory cache is fine for one instance;
if there were many instances, that cache would need to move out of process (see
system design). No load test suite in the repo.

## Observability

Metrics live on `/actuator/prometheus`. Point Prometheus at that and you can
build Grafana panels without changing the app. Prometheus and Grafana themselves
are not part of this submission.

### Metrics we wrote

| Metric | Type | Where | What it tells you |
|---|---|---|---|
| `urlshortener_shorten_count_total` | Counter | `UrlController` | how many links were created |
| `urlshortener_redirect_latency_seconds{outcome}` | Timer | `RedirectController` | redirect latency by `found` / `not_found` / `gone` |
| `urlshortener_analytics_queue_size` | Gauge | `AsyncConfig` | how backed up async click recording is |
| `cache_gets_total{cache="urlMappings",result="hit"/"miss"}` | Counter | `CacheConfig` | whether the redirect cache is helping |

Limitation worth knowing: `cache_puts_total` and `cache_size` sit at `0` even
after a put. That's Caffeine + Micrometer (manual put isn't counted as a "load",
and size is updated async). `cache_gets_total` is fine and is the number I'd put
on a dashboard.

### Metrics that came free

Actuator + the Prometheus registry give you these without extra code:

- `http_server_requests_seconds_*` — latency and count per endpoint
- `hikaricp_connections_*` — pool health
- `jvm_*` — memory, GC, threads
- `spring_data_repository_invocations_seconds_*` — repository method latency
- executor metrics for the analytics thread pool

### Logs

Every request gets a `traceId` in the MDC (`CorrelationIdFilter`). Same id shows
up in the log line and in the error JSON if something fails. A redirect looks
like this end to end:

```
[traceId=e6e9...] RedirectController - Request received: GET /6
[traceId=e6e9...] UrlService          - Redirect requested for code=6
[traceId=e6e9...] UrlLookupCache      - code=6 cache=MISS, querying repository
[traceId=e6e9...] UrlLookupCache      - code=6 repository response=found
[traceId=e6e9...] UrlService          - Resolved code=6 -> https://example.com/...
[traceId=e6e9...] RedirectController  - Request completed: GET /6 -> 302 ...
[analytics-2]      AnalyticsService    - code=6 click received, recording asynchronously
[analytics-2]      AnalyticsService    - code=6 repository update count=1
```

Request in → decision → cache hit/miss → DB if needed → response out → analytics
on its own pool. Grep one id and you have the whole story.

### What I'd actually dashboard

If Prometheus + Grafana were wired up: redirect latency by outcome, cache hit
rate, analytics queue size, Hikari pool usage, and the free per-endpoint HTTP
timers. None of those dashboards are in the repo — same deliberate cut as the
gateway and sharding.

Concrete captures of create / redirect / stats against a live Docker run:
[DEMO.md](DEMO.md).

## Summary

Built a single-node shortener that actually runs: APIs, Postgres, security on
writes, async click counts, metrics, Docker. Wrote down how it would scale
instead of pretending the prototype already does.

**In the repo:** app under `urlshortener/`, design docs, diagrams in `images/`,
demo captures in `examples/` via [DEMO.md](DEMO.md), unit + integration tests.

**Trade-offs I took:**

| Choice | Downside | Why it's fine here |
|---|---|---|
| In-process Caffeine | Not shared across instances | One instance in the demo |
| Async click writes | Counter can lag; work in the queue is lost if the process dies | Redirect UX first; queue gauge shows backpressure |
| API key only | Not real identity | Enough for a take-home |
| H2 in tests | Not Postgres | Fast CI; Docker smoke uses Postgres |
| No rate limiting | Public GETs can be abused | Belongs at a gateway later |

**Assumptions:** one region, click stats mean counters not event analytics,
reviewer has Docker.

**Not built:** multi-instance compose, Prometheus/Grafana stack, load tests,
admin UI.