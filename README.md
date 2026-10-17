# Event Reservation System

Web application for event management and online ticket booking.  
Built for Τεχνολογίες Εφαρμογών Διαδικτύου (TED) 2026,  
Department of Informatics & Telecommunications, National and Kapodistrian University of Athens.

---

## Table of Contents

1. [Architecture](#architecture)
2. [Tech Stack](#tech-stack)
3. [Prerequisites](#prerequisites)
4. [Quick Start (Docker)](#quick-start-docker)
5. [Manual Setup](#manual-setup-without-docker)
6. [HTTPS / TLS Setup](#https--tls-setup)
7. [Admin Account](#admin-account)
8. [Recommender Dataset CSV](#recommender-dataset-csv)
9. [Running Tests](#running-tests)
10. [Environment Variables](#environment-variables)
11. [End-to-End Smoke Test](#end-to-end-smoke-test)
12. [Project Structure](#project-structure)
13. [Known Limitations & Future Work](#known-limitations--future-work)

---

## Architecture

Four layers, each talking to the one below it:

- **Browser** runs the Angular 21 SPA: auth, events, bookings, messages, recommendations.
- **nginx** (port 80/443) serves the built Angular files and proxies `/api/*` and
  `/events.dtd` to the backend over the internal Docker network.
- **Spring Boot 3.5** (port 8080/8443) is the REST API under `/api/v1`. It covers
  auth/JWT, events, bookings, messages, the BMF recommender (plain-Java SGD) and the
  admin XML/JSON export (which validates against `events.dtd`), on top of Spring Data
  JPA, Flyway, Spring Security and a nightly `@Scheduled` retrain.
- **MySQL 8** (port 3306) holds the data: `users`, `events`, `bookings`,
  `ticket_types`, `messages`, `event_views`, `event_photos`, `categories` and
  `event_categories`.

A request flows browser to nginx to Spring Boot to MySQL (JDBC on TCP 3306), and the
response comes back the same way.

---

## Tech Stack

| Layer         | Technology                         | Version  |
| ------------- | ---------------------------------- | -------- |
| Frontend      | Angular                            | 21       |
| UI Components | Angular Material (M3)              | 21       |
| Backend       | Java / Spring Boot                 | 21 / 3.5 |
| Security      | Spring Security + JJWT             | 0.12.3   |
| Database      | MySQL                              | 8.0      |
| Migrations    | Flyway                             | 10.x     |
| DTO Mapping   | MapStruct                          | 1.5.5    |
| API Docs      | SpringDoc OpenAPI (Swagger UI)     | 2.5      |
| Monitoring    | Spring Boot Actuator               | 3.5      |
| Testing       | JUnit 5, Mockito, Testcontainers   | -        |
| Container     | Docker / Docker Compose            | 24+      |
| Web Server    | nginx (production)                 | alpine   |

---

## Prerequisites

| Tool             | Version | Install                      |
| ---------------- | ------- | ---------------------------- |
| Java JDK         | 21      | <https://adoptium.net>       |
| Node.js          | 22 LTS  | <https://nodejs.org>         |
| Docker + Compose | 24+     | <https://docs.docker.com>    |
| MySQL (optional) | 8.0     | only needed for manual setup |

---

## Quick Start (Docker)

```bash
# Clone the repository
git clone <repo-url>
cd event-reservation-system

# Build images and start all services
docker compose up --build
```

| Service          | URL                                     |
| ---------------- | --------------------------------------- |
| Frontend (nginx) | <https://localhost>                     |
| Backend API      | <http://localhost:8080/api/v1>          |
| Swagger UI       | <http://localhost:8080/swagger-ui.html> |
| Actuator health  | <http://localhost:8080/actuator/health> |
| MySQL            | `localhost:3306` (user: `events_user`)  |

> **First run:** MySQL initialisation takes about 30 seconds. The backend waits for the
> `healthcheck` to pass before it starts. If the backend exits before the DB is ready,
> just run `docker compose up` again; it reuses the already-initialized volume.

```bash
# Stop and keep data
docker compose down

# Stop and wipe the database volume (full reset)
docker compose down -v
```

---

## Manual Setup (without Docker)

### 1. Start MySQL

```bash
docker run -d --name events-mysql \
  -e MYSQL_ROOT_PASSWORD=rootpass \
  -e MYSQL_DATABASE=events_db \
  -e MYSQL_USER=events_user \
  -e MYSQL_PASSWORD=events_pass \
  -p 3306:3306 \
  mysql:8.0
```

### 2. Backend

```bash
cd backend
./mvnw spring-boot:run
# API: http://localhost:8080  |  Swagger: http://localhost:8080/swagger-ui.html
```

The environment variables all have defaults for local dev (see
[Environment Variables](#environment-variables)):

```bash
export DB_URL="jdbc:mysql://localhost:3306/events_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export DB_USER=events_user
export DB_PASSWORD=events_pass
export JWT_SECRET="at-least-32-random-chars-change-this"
export ADMIN_INITIAL_PASSWORD="Admin@123456"
```

### 3. Frontend (dev server with live reload)

```bash
cd frontend
npm install      # first time only
npm start        # ng serve, http://localhost:4200
```

`/api/` requests are proxied to `http://localhost:8080` by Angular's dev-server proxy
(configured in `frontend/proxy.conf.json`).

---

## HTTPS / TLS Setup

### Docker (automated, no manual steps)

`docker compose up` serves the whole app over HTTPS automatically. nginx terminates
TLS on port 443, and the frontend image generates a self-signed `localhost`
certificate at build time (see `docker/Dockerfile.frontend`), so there is nothing to
run by hand:

```bash
docker compose up --build
# open https://localhost  (plain http://localhost 301-redirects here)
```

The browser shows a one-time "untrusted certificate" warning (expected for a
self-signed cert); click _Advanced, then Proceed_. Traffic between the browser and
the server is encrypted, and nginx proxies `/api/` to the backend over the isolated
internal Docker network. To use a real CA-signed certificate, mount it over
`/etc/nginx/certs/` (`server.crt` / `server.key`) in `docker-compose.yml`.

### Self-signed certificate for localhost development (non-Docker backend)

```bash
# Run from the repo root. The -dname and -ext flags make this non-interactive
# and add a Subject Alternative Name so Chrome 58+ accepts the cert.
keytool -genkeypair \
  -alias events \
  -keyalg RSA -keysize 2048 \
  -dname "CN=localhost, OU=TED, O=UoA, L=Athens, ST=Attica, C=GR" \
  -ext "SAN=DNS:localhost,IP:127.0.0.1" \
  -storetype PKCS12 \
  -keystore backend/src/main/resources/keystore.p12 \
  -storepass changeit \
  -validity 365
```

Start the backend with the `prod` profile:

```bash
cd backend
SPRING_PROFILES_ACTIVE=prod \
SSL_KEY_STORE=classpath:keystore.p12 \
SSL_KEY_STORE_PASSWORD=changeit \
SSL_KEY_ALIAS=events \
JWT_SECRET="<minimum-32-char-secret>" \
ADMIN_INITIAL_PASSWORD="<strong-password>" \
./mvnw spring-boot:run
```

The API is then available at **<https://localhost:8443>**.

> **Browser trust warning:** browsers show an "untrusted certificate" warning for a
> self-signed cert. Click _Advanced, then Proceed_ in Chrome, or add the cert to your
> OS trust store.

```bash
# macOS
sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain \
  backend/src/main/resources/keystore.p12
# Linux (Ubuntu/Debian)
openssl pkcs12 -in backend/src/main/resources/keystore.p12 -nokeys -out localhost.crt -passin pass:changeit
sudo cp localhost.crt /usr/local/share/ca-certificates/localhost.crt
sudo update-ca-certificates
```

### Swapping in a real certificate (production)

Replace the keystore with one signed by a trusted CA:

```bash
# Convert Let's Encrypt PEM files to PKCS12
openssl pkcs12 -export \
  -in /etc/letsencrypt/live/yourdomain.com/fullchain.pem \
  -inkey /etc/letsencrypt/live/yourdomain.com/privkey.pem \
  -out keystore.p12 \
  -name events \
  -password pass:${SSL_KEY_STORE_PASSWORD}
```

Mount the keystore as a Docker secret or volume and set `SSL_KEY_STORE` to the
absolute path (e.g. `/run/secrets/keystore.p12`). The `prod` profile in
`application.yml` reads all SSL parameters from environment variables.

---

## Admin Account

Flyway migration `V2__seed_admin.sql` creates a single admin account on first boot:

| Field    | Default value                                          |
| -------- | ------------------------------------------------------ |
| Username | `admin`                                                |
| Password | value of `ADMIN_INITIAL_PASSWORD` env var              |
|          | Docker default: `Admin@123456`                         |
|          | **Change this before any internet-facing deployment.** |

On first login the app forces a password change. After changing it, log in again with
the new password to reach the admin panel (`/admin/users`).

---

## Recommender Dataset CSV

The Biased Matrix Factorization recommender is bootstrapped on first startup with a
seed dataset, so the latent space is populated before any live bookings/views
accumulate. The shipped seed is the e-class Kaggle "Event Recommendation Engine
Challenge" `event_interest.csv`, packaged in the jar at
`backend/src/main/resources/dataset/event_interest.csv` (647 KB). No env var is
needed; it works out of the box.

> The full Kaggle download (~1.5 GB across 5 CSVs) is **not** committed; only
> `event_interest.csv` is needed for matrix factorization. The raw `dataset/`
> folder is `.gitignore`d.

**Configuration** (`recommender.dataset.*` in `application.yml`):

```yaml
recommender:
  dataset:
    enabled: true # false trains on live DB data only
    path: classpath:dataset/event_interest.csv # classpath: (in jar) or file:/abs/path
```

| Key / env var                 | Default                                | Meaning                                  |
| ----------------------------- | -------------------------------------- | ---------------------------------------- |
| `RECOMMENDER_DATASET_ENABLED` | `true`                                 | Master switch for CSV bootstrap          |
| `RECOMMENDER_DATASET_PATH`    | `classpath:dataset/event_interest.csv` | Spring resource location of the seed CSV |

**Expected format** is the `event_interest.csv` schema (header auto-detected):

```csv
user,event,invited,timestamp,interested,not_interested
8949,1566551,0,2012-10-02 15:53:05.754,1,0
8949,1130067,0,2012-10-02 15:53:05.754,0,0
```

Only three fields are used: `user`, `event`, and `interested`. The rating is derived to
match the live in-app feedback scale:

| Source row          | Rating | Mirrors             |
| ------------------- | ------ | ------------------- |
| `interested = 1`    | `5.0`  | a CONFIRMED booking |
| any other shown row | `1.0`  | a page view         |

Rules:

- The header row is detected automatically (a non-numeric first field is skipped).
- Lines starting with `#` are comments; blank lines are ignored.
- Rows with an empty or non-numeric event id are counted as malformed and skipped
  (the real dataset has about 843 such rows). Startup logs `Loaded N interactions
  across U users and I items` and `Skipped K malformed rows`.
- Seed IDs are disjoint from the app's DB IDs, so the seed bootstraps the model and the
  global mean; live DB bookings (r = 5.0) and views (r = 1.0) are merged on top and win
  for matching `(user, event)` pairs.
- The recommender retrains every night at 03:00 server time. The model is persisted to
  `./data/bmf-model.json` (configurable via `RECOMMENDER_MODEL_PATH`); delete that file
  to force a from-scratch retrain on the next startup.

---

## Running Tests

### Unit tests only (fast, no Docker required)

```bash
cd backend
./mvnw test -Dtest="BiasedMfTrainerTest,RecommendationServiceTest"
```

### All tests including integration tests (requires Docker for Testcontainers)

```bash
cd backend
./mvnw verify
```

Integration tests spin up a real MySQL 8 container via Testcontainers:

| Test class                     | What it tests                                       |
| ------------------------------ | --------------------------------------------------- |
| `EventManagementTest`          | Event lifecycle: create, publish, cancel            |
| `BookingConcurrencyTest`       | Optimistic locking under concurrent booking load    |
| `DataIntegrityTest`            | FK constraints, cascade deletes, version increments |
| `CancellationNotificationTest` | Cancel event, then 3 distinct notification messages |
| `ExportDtdValidationTest`      | Generated XML validates against `events.dtd`        |
| `BiasedMfTrainerTest`          | SGD convergence, finite scores, edge cases          |
| `RecommendationServiceTest`    | Cold-start fallback, exclusion, limit enforcement   |

### Frontend type-check

```bash
cd frontend
npx ng build --configuration production   # reports type errors as build errors
```

---

## Environment Variables

| Variable                      | Default (dev)                               | Required in prod  |
| ----------------------------- | ------------------------------------------- | ----------------- |
| `SPRING_PROFILES_ACTIVE`      | `dev,test-data`                             | `prod`            |
| `DB_URL`                      | `jdbc:mysql://localhost:3306/events_db?...` | yes               |
| `DB_USER`                     | `events_user`                               | yes               |
| `DB_PASSWORD`                 | `events_pass`                               | yes               |
| `JWT_SECRET`                  | `DEV-ONLY-SECRET-...` (insecure)            | yes (min 32 chars)|
| `JWT_EXPIRATION_MS`           | `3600000` (1 hour)                          |                   |
| `ADMIN_INITIAL_PASSWORD`      | `Admin@123456`                              | yes               |
| `SERVER_PORT`                 | `8080` (dev) / `8443` (prod)                |                   |
| `SSL_KEY_STORE`               | `classpath:keystore.p12`                    | yes (prod)        |
| `SSL_KEY_STORE_PASSWORD`      | (none)                                      | yes (prod)        |
| `SSL_KEY_ALIAS`               | `events`                                    |                   |
| `UPLOAD_DIR`                  | `./uploads`                                 |                   |
| `RECOMMENDER_K`               | `20`                                        |                   |
| `RECOMMENDER_LR`              | `0.005`                                     |                   |
| `RECOMMENDER_LAMBDA`          | `0.02`                                      |                   |
| `RECOMMENDER_EPOCHS`          | `30`                                        |                   |
| `RECOMMENDER_MODEL_PATH`      | `./data/bmf-model.json`                     |                   |
| `RECOMMENDER_DATASET_ENABLED` | `true`                                      |                   |
| `RECOMMENDER_DATASET_PATH`    | `classpath:dataset/event_interest.csv`      |                   |

---

## End-to-End Smoke Test

This script walks through every major feature. Run it against a fresh Docker
deployment (`docker compose up --build`).

### Preconditions

```
BASE=http://localhost:8080/api/v1
```

---

### Step 1: Admin logs in and approves a user

```bash
# 1a. Log in as admin (password is the ADMIN_INITIAL_PASSWORD value)
TOKEN=$(curl -s -X POST $BASE/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123456"}' | jq -r '.token')

# If admin still has mustChangePassword=true, change it first:
curl -s -X POST $BASE/auth/change-password \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"currentPassword":"Admin@123456","newPassword":"Admin@NewPass1!","confirmPassword":"Admin@NewPass1!"}'
TOKEN=$(curl -s -X POST $BASE/auth/login \
  -d '{"username":"admin","password":"Admin@NewPass1!"}' \
  -H "Content-Type: application/json" | jq -r '.token')

# 1b. Register an organizer
curl -s -X POST $BASE/auth/register -H "Content-Type: application/json" -d '{
  "username":"organizer1","email":"org1@example.com",
  "password":"Organizer@1","confirmPassword":"Organizer@1",
  "firstName":"Alice","lastName":"Smith","role":"ORGANIZER","taxId":"094259216"
}'

# 1c. Register a participant
curl -s -X POST $BASE/auth/register -H "Content-Type: application/json" -d '{
  "username":"participant1","email":"part1@example.com",
  "password":"Participant@1","confirmPassword":"Participant@1",
  "firstName":"Bob","lastName":"Jones","role":"PARTICIPANT"
}'

# 1d. Admin fetches pending users and approves them
USERS=$(curl -s "$BASE/admin/users?status=PENDING" -H "Authorization: Bearer $TOKEN")
ORG_ID=$(echo $USERS | jq '.content[] | select(.username=="organizer1") | .id')
PART_ID=$(echo $USERS | jq '.content[] | select(.username=="participant1") | .id')

curl -s -X POST "$BASE/admin/users/$ORG_ID/approve"  -H "Authorization: Bearer $TOKEN"
curl -s -X POST "$BASE/admin/users/$PART_ID/approve" -H "Authorization: Bearer $TOKEN"
```

---

### Step 2: Organizer creates and publishes an event

```bash
# 2a. Log in as organizer
ORG_TOKEN=$(curl -s -X POST $BASE/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"organizer1","password":"Organizer@1"}' | jq -r '.token')

# 2b. Get a category ID
CAT_ID=$(curl -s $BASE/categories | jq '.[0].id')

# 2c. Create event with two ticket types
EVENT=$(curl -s -X POST $BASE/events -H "Authorization: Bearer $ORG_TOKEN" \
  -H "Content-Type: application/json" -d "{
    \"title\": \"Rock Concert 2026\",
    \"description\": \"A great show.\",
    \"startDatetime\": \"$(date -u -d '+7 days' +%Y-%m-%dT20:00:00)\",
    \"endDatetime\":   \"$(date -u -d '+7 days' +%Y-%m-%dT23:00:00)\",
    \"capacity\": 200,
    \"venueName\": \"Athens Arena\", \"address\": \"1 Main St\",
    \"city\": \"Athens\", \"country\": \"Greece\",
    \"latitude\": 37.979, \"longitude\": 23.716,
    \"categoryIds\": [$CAT_ID],
    \"ticketTypes\": [
      {\"name\":\"General\",\"price\":25.00,\"quantity\":150},
      {\"name\":\"VIP\",    \"price\":75.00,\"quantity\":50}
    ]
  }")
EVENT_ID=$(echo $EVENT | jq '.id')

# 2d. Publish
curl -s -X POST "$BASE/events/$EVENT_ID/publish" -H "Authorization: Bearer $ORG_TOKEN"
echo "Event $EVENT_ID published"
```

---

### Step 3: Participant searches, finds, and books

```bash
# 3a. Log in as participant
PART_TOKEN=$(curl -s -X POST $BASE/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"participant1","password":"Participant@1"}' | jq -r '.token')

# 3b. Search (public, no token needed)
curl -s "$BASE/events/search?q=Rock+Concert" | jq '.content[].title'

# 3c. View event detail (records a view for the recommender)
curl -s "$BASE/events/$EVENT_ID" -H "Authorization: Bearer $PART_TOKEN" | jq '.title'

# 3d. Book 2 General tickets
TT_ID=$(curl -s "$BASE/events/$EVENT_ID" | jq '.ticketTypes[] | select(.name=="General") | .id')
BOOKING=$(curl -s -X POST "$BASE/events/$EVENT_ID/bookings" \
  -H "Authorization: Bearer $PART_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"ticketTypeId\":$TT_ID,\"quantity\":2}")
BOOKING_ID=$(echo $BOOKING | jq '.id')
echo "Booking $BOOKING_ID confirmed"
```

---

### Step 4: Organizer sees the booking

```bash
curl -s "$BASE/events/$EVENT_ID/bookings" \
  -H "Authorization: Bearer $ORG_TOKEN" | jq '.content[].attendeeUsername'
```

---

### Step 5: Participant messages the organizer

```bash
ORG_USER_ID=$(curl -s "$BASE/events/$EVENT_ID" | jq '.organizerId')
curl -s -X POST $BASE/messages \
  -H "Authorization: Bearer $PART_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"recipientId\":$ORG_USER_ID,\"eventId\":$EVENT_ID,
       \"subject\":\"Question about VIP tickets\",
       \"body\":\"Are VIP tickets still available?\"}"
echo "Message sent"
```

---

### Step 6: Organizer cancels; participant receives notification

```bash
curl -s -X POST "$BASE/events/$EVENT_ID/cancel" \
  -H "Authorization: Bearer $ORG_TOKEN"

# Participant's inbox should contain the cancellation notification
curl -s "$BASE/messages/inbox" \
  -H "Authorization: Bearer $PART_TOKEN" | jq '.[].subject'
```

---

### Step 7: Admin exports XML and validates DTD

```bash
curl -s -o events.xml \
  -H "Authorization: Bearer $TOKEN" \
  "$BASE/admin/export/events.xml"

xmllint --dtdvalid http://localhost:8080/events.dtd --noout events.xml
echo $?   # 0 means valid
```

---

### Step 8: Recommender appears on participant's home

```bash
# After the booking, the BMF model will include participant1 on the next retrain.
# Trigger an immediate retrain by restarting the backend, or wait for 03:00.
# Then check recommendations:
curl -s "$BASE/recommendations?limit=5" \
  -H "Authorization: Bearer $PART_TOKEN" | jq '.[].title'
```

---

## Project Structure

```text
event-reservation-system/
├── backend/                         Spring Boot 3.5 / Java 21
│   ├── src/main/java/gr/uoa/di/ted/events/
│   │   ├── config/                  SecurityConfig, OpenApiConfig, JpaAuditingConfig
│   │   ├── controller/              REST controllers (one per feature)
│   │   ├── dto/                     Request / response DTOs (records)
│   │   ├── entity/                  JPA entities (Event, User, Booking, ...)
│   │   ├── enums/                   Role, EventStatus, BookingStatus, UserStatus
│   │   ├── exception/               AppException hierarchy + GlobalExceptionHandler
│   │   ├── recommender/             BMF model, trainer, service, bootstrap
│   │   ├── repository/              Spring Data JPA repositories
│   │   ├── security/                JWT filter, UserDetails impl
│   │   ├── service/                 Business logic (EventService, BookingService, ...)
│   │   └── validation/              Custom @GreekTaxId, @ValidPassword annotations
│   └── src/main/resources/
│       ├── application.yml          dev + prod profiles
│       ├── static/events.dtd        XML schema for admin export
│       └── db/migration/            V1 to V6 Flyway SQL scripts
│
├── frontend/                        Angular 21
│   └── src/app/
│       ├── core/                    guards/, interceptors/, services/, models/
│       ├── features/                auth/, events/, my-events/, my-bookings/,
│       │                            messages/, admin/, recommendations/
│       └── environments/            environment.ts (prod), environment.development.ts
│
├── docker/
│   ├── Dockerfile.backend           Multi-stage: Maven build, then JRE runtime
│   ├── Dockerfile.frontend          Multi-stage: Node build, then nginx serve
│   └── nginx.conf                   SPA routing, /api/ proxy, gzip, cache headers
│
├── docs/                            Project report PDF
└── docker-compose.yml               MySQL + backend + frontend (dev mode)
```

---

## Known Limitations & Future Work

| Area               | Limitation / Future improvement                                                                                              |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------- |
| **Authentication** | No "Forgot password" flow. Users must contact an admin to reset.                                                             |
| **Email**          | No email notifications. All notifications are in-app messages only.                                                          |
| **Payments**       | Bookings are confirmed immediately without payment processing.                                                               |
| **Photo storage**  | Event photos are stored on the local filesystem. A production system would use S3 / MinIO.                                   |
| **Recommender**    | Model retrains only at 03:00 nightly, so new users stay cold-start until then. k-NN or ALS would scale better.              |
| **Rate limiting**  | No per-IP or per-user rate limiting on API endpoints.                                                                        |
| **Observability**  | No distributed tracing (OpenTelemetry). Logs only.                                                                          |
| **Search**         | Full-text search uses MySQL's MATCH ... AGAINST (fine for MySQL; Elasticsearch would scale better).                          |
| **Accessibility**  | Angular Material gives a baseline; no screen-reader testing was done.                                                        |
| **i18n**           | English only. Angular i18n is not wired up.                                                                                  |
