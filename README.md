# URL Shortener

A URL shortener built for the AI-Proficient Software Engineer take-home. Shorten a
URL, get redirected when you hit the short link, see basic click stats.

Full reasoning behind the design choices is in [SYSTEM_DESIGN.md](SYSTEM_DESIGN.md)
(how this would scale — gateway, instances, sharding) and
[APPLICATION_DESIGN.md](APPLICATION_DESIGN.md) (what's actually built — controllers,
services, cache, db). Scenarios, testing, and summary are in
[ARCHITECTURE.md](ARCHITECTURE.md). Request + metrics captures from a Docker smoke
run are in [DEMO.md](DEMO.md).

![System design](images/system-design.png)

![Application design](images/application-design.png)

## Stack

Java 21, Spring Boot, Postgres, Maven.

## Running it

### Full stack (app + Postgres) via Docker

```
cd urlshortener
docker compose up --build -d
```

That builds the app image, starts Postgres, waits for it to be healthy, then starts
the app. Both containers come up together; the app talks to Postgres over the
compose network (`jdbc:postgresql://postgres:5432/urlshortener`).

App: `http://localhost:8080` — health at `/actuator/health`, API docs at
`/swagger-ui.html`, Prometheus metrics at `/actuator/prometheus`.

Stop and remove containers (keeps the Postgres volume):

```
docker compose down
```

### Local app against Dockerized Postgres only

```
cd urlshortener
docker compose up -d postgres
./mvnw spring-boot:run
```

Defaults in `application.properties` already point at `localhost:5432` with the
same credentials compose uses, so no extra config is needed for local runs.

## API

| Method | Path | What it does |
|---|---|---|
| POST | `/api/v1/urls` | Shorten a URL |
| GET | `/{code}` | Redirect to the original URL |
| GET | `/api/v1/urls/{code}` | Look up the metadata for a code |
| GET | `/api/v1/urls/{code}/stats` | Click count for a code |
| DELETE | `/api/v1/urls/{code}` | Disable a short link |

`POST` and `DELETE` need an `X-API-Key` header. `GET` doesn't.

## Tests

```
./mvnw test
```

Runs against an embedded H2 database, so nothing extra needs to be running.
