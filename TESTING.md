# Manual End-to-End Test Checklist

A click-by-click walkthrough that exercises every functional requirement of the
assignment against the seeded `test-data` dataset. Do it by hand: each step lists the
action, the expected outcome, and a checklist of what to verify.

> Tip: keep the backend console visible while testing. The recommender training logs
> and the seeded credentials table print there on startup.

---

## 0. Start the app (test-data profile)

**Backend** (seeds data and retrains the recommender on startup):

```bash
# from repo root, start MySQL (host port 3307)
docker compose up -d mysql

# build + run the backend with the seed profile
cd backend
MVN=/home/mrphoenix/.local/share/JetBrains/Toolbox/apps/intellij-idea/plugins/maven/lib/maven3/bin/mvn   # ./mvnw is broken
"$MVN" -DskipTests package
SPRING_PROFILES_ACTIVE=dev,test-data \
DB_URL="jdbc:mysql://localhost:3307/events_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
DB_USER=events_user DB_PASSWORD=events_pass ADMIN_INITIAL_PASSWORD=Admin@123456 \
java -jar target/events-0.0.1-SNAPSHOT.jar
```

**Frontend** (separate terminal):

```bash
cd frontend
npm install      # first run only
npm start        # ng serve, http://localhost:4200 (calls the backend on :8080)
```

- [ ] Backend log shows `The following 2 profiles are active: "dev", "test-data"`.
- [ ] Backend log shows `[test-data] Inserted 8 users, 15 events, 30 bookings, 20 views, 10 messages.`
- [ ] Backend log prints the **TEST-DATA PROFILE - SEEDED ACCOUNTS** table.
- [ ] Backend log shows `BMF training: ... BMF training complete: best validation RMSE = ...` with a **decreasing** per-epoch RMSE and no `NaN`/`Exception`.
- [ ] Browser opens `http://localhost:4200` and redirects to **/welcome**.

> To reset between full runs: `docker compose down -v && rm -f backend/data/bmf-model.json`, then start again (seeding is skipped if `alice.k` already exists).

### Seeded test accounts (password for all = `Test@Pass123!`)

| Username    | Role        | Status      | Use it to test                                                               |
| ----------- | ----------- | ----------- | ---------------------------------------------------------------------------- |
| `admin`     | ADMIN       | ACTIVE      | Password = `Admin@123456` (forced change on first login); approvals; exports |
| `alice.k`   | ORGANIZER   | ACTIVE      | Organizes e1/e5/e9 + **CANCELLED** e13; cancellation broadcast               |
| `nikos.p`   | ORGANIZER   | ACTIVE      | Organizes e2/e6/e10 + **DRAFT** e14                                          |
| `maria.g`   | ORGANIZER   | ACTIVE      | Organizes e3/e7 + **COMPLETED** e11 + **DRAFT** e15                          |
| `yannis.d`  | ORGANIZER   | ACTIVE      | Organizes e4/e8 + **COMPLETED** e12                                          |
| `eleni.v`   | PARTICIPANT | ACTIVE      | **Strong** recommender signal (9 bookings, 6 categories)                     |
| `giorgos.a` | PARTICIPANT | ACTIVE      | **Views-only** recommender signal (0 bookings, 5 views)                      |
| `sofia.m`   | PARTICIPANT | **PENDING** | Admin **approval** flow; **cold-start** recommender after approval           |
| `kostas.t`  | ORGANIZER   | **PENDING** | Admin approval, then can create events                                       |

**Key events:** `e1` Athens Summer Jazz Festival, `e6` Indie Rock Night = **FULLY BOOKED (0 seats)**, `e8` Stand-up Comedy Gala = **NEAR CAPACITY (6 left)**, `e13` Open-Air Cinema = **CANCELLED**, `e14`/`e15` = **DRAFT** (hidden from public), `e11`/`e12` = **COMPLETED** (past).

---

## 1. Welcome / landing page (public)

**Action:** Open `http://localhost:4200/` (redirects to `/welcome`). Without logging in, browse to the public events page (`/events`).

- [ ] Welcome page renders with calls to action to **Login** and **Register**.
- [ ] Public event listing shows **only PUBLISHED** events: you should see the 10 published events (e1 to e10) but **NOT** the DRAFT (e14, e15), CANCELLED (e13), or COMPLETED (e11, e12) ones.
- [ ] Each card shows title, city, date, and price/availability.
- [ ] Opening an event detail (`/events/{id}`, e.g. e1) shows description, ticket tiers, and a **Leaflet/OpenStreetMap** map pinned at the venue lat/lng (Athens for e1).

---

## 2. Registration

**Action:** Click **Register**. Create a brand-new participant:

- username `testuser1`, email `testuser1@example.com`, password `Test@Pass123!` (+ confirm), first/last name, role **PARTICIPANT**, **AFM (taxId)** e.g. `094259216`.
  Repeat to create an organizer `testorg1` with role **ORGANIZER**.

- [ ] Registration succeeds and tells you the account is **pending admin approval**.
- [ ] Try to register again with the **same username/email**; it's rejected (duplicate).
- [ ] Submit with a **missing AFM or mismatched password confirmation**; you get an inline validation error (AFM is required for all roles).
- [ ] Attempt to **log in** as `testuser1` now; it's blocked (account not yet ACTIVE).

---

## 3. Admin first login and forced password change

**Action:** Log in as `admin` / `Admin@123456`.

- [ ] You are **forced** to the **change-password** screen before anything else is reachable.
- [ ] Change to a new password (e.g. `Admin@NewPass1!`); the confirm field must match.
- [ ] After the change you are logged out / asked to log in again; log in with the **new** password.
- [ ] Admin landing/menu now exposes **Users** and **Export** (admin-only).

---

## 4. Admin approval workflow

**Action:** Go to **/admin/users**. Filter by status **PENDING**.

- [ ] Pending list contains `sofia.m`, `kostas.t`, and your new `testuser1`, `testorg1`.
- [ ] **Approve** `kostas.t`, `testorg1` (organizers) and `sofia.m`, `testuser1` (participants).
- [ ] Optionally **Reject** one and confirm it moves to REJECTED and cannot log in.
- [ ] Approved users disappear from the PENDING filter and show as **ACTIVE**.
- [ ] A non-admin token cannot reach `/admin/users` (try logging in as `eleni.v` and navigating there; you get blocked / 403).

---

## 5. Login per role

**Action:** Log in (separate browser/incognito windows help) as `kostas.t`, `eleni.v`, and `admin`.

- [ ] ORGANIZER (`kostas.t`) sees **My Events** / create-event options.
- [ ] PARTICIPANT (`eleni.v`) sees **My Bookings**, **Messages**, recommendations, but **no** create-event/admin options.
- [ ] Each session's menu matches its role (role-based UI).

---

## 6. Event creation (organizer) into DRAFT

**Action:** As `kostas.t` (or `testorg1`), go to **My Events, then New** (`/my-events/new`). Fill:

- title, description, **start/end datetime** (start in the future), capacity (50 to 500),
- venue + city + **pick a location on the map** (lat/lng),
- one or more **categories**,
- **2 to 3 ticket types** with name/price/quantity.

- [ ] Form validates: end after start, capacity at least 1, at least one ticket type, prices at least 0.
- [ ] On save, the event is created as **DRAFT** and appears under **My Events**.
- [ ] The new DRAFT event is **NOT** visible on the public `/events` listing.
- [ ] An organizer cannot see/edit **another** organizer's events (try opening one of alice.k's event-edit URLs).

---

## 7. Publish

**Action:** Open your DRAFT event under **My Events** and click **Publish**.

- [ ] Status changes from DRAFT to **PUBLISHED**.
- [ ] The event now appears on the public `/events` listing and is bookable.
- [ ] (Edge) A DRAFT cannot be booked; only PUBLISHED events accept bookings.

---

## 8. Search & browse

**Action:** On `/events`, use the search/filter controls.

- [ ] **Keyword**: search `Festival` returns _Athens Summer Jazz Festival_, _Heraklion Wine & Food Festival_, _Mediterranean Food Festival_.
- [ ] **Keyword**: `Hamlet` returns nothing on the public list (e12 is COMPLETED), which confirms completed events are excluded from public browse.
- [ ] **Category** filter (e.g. _Theatre_) returns _Acropolis Open-Air Theatre: Antigone_ (e3).
- [ ] **City** filter (_Thessaloniki_) returns e2 / e6 (published, Thessaloniki).
- [ ] **Date range** spanning the next month returns the soon-starting events and excludes the far-future ones; broadening the range includes more (start dates are spread over ~3 months).
- [ ] Event detail shows the **map** (Leaflet/OSM) at the correct city coordinates.

---

## 9. Booking (incl. "no seats" and near-capacity paths)

**Action:** As `eleni.v`, open an event you have **not** booked (e.g. **e9 Mozart by Candlelight** or **e10 Mediterranean Food Festival**) and book 1 to 2 tickets.

- [ ] Booking succeeds; a confirmation with **total price = quantity x tier price** is shown.
- [ ] **My Bookings** (`/my-bookings`) lists the new booking as **CONFIRMED**.
- [ ] The event detail's remaining quantity for that tier **decreases** by the amount booked.

**"No seats" path, e6 Indie Rock Night (FULLY BOOKED):**

- [ ] Open **e6**. Both tiers (General Admission, VIP) show **0 remaining / sold out**.
- [ ] Attempting to book e6 is blocked with a clear **"no seats / sold out"** message.

**Near-capacity, e8 Stand-up Comedy Gala:**

- [ ] Open **e8**: General Admission shows **4 left**, Front Row shows **2 left** (6 total).
- [ ] Booking the **last** Front Row seats reduces remaining to 0; one more attempt is rejected.

**Cancellation of a booking:**

- [ ] In **My Bookings**, cancel one booking; status becomes **CANCELLED** and the seats are returned to the tier's availability.

---

## 10. Messaging

**Action:** As `eleni.v`, open **Messages** (`/messages`).

- [ ] Inbox shows seeded messages with **unread** ones visually distinct (5 unread / 5 read total across users).
- [ ] Open the unread _"Re: VIP tickets for the Jazz Festival"_ thread from `alice.k` (tied to **e1**); it marks as **read**.
- [ ] **Compose** a new message to `alice.k`, optionally **linked to an event** (event-tied messages show the event reference).
- [ ] Log in as `alice.k`; the new message appears in her inbox as **unread**; reply to it.
- [ ] A message tied to an event shows/links the event context.

---

## 11. Cancellation broadcast (organizer cancels an event, attendees notified)

**Action:** As `nikos.p`, open **e6 Indie Rock Night** (it has 5 confirmed bookings) under **My Events** and **Cancel** the event.

- [ ] Event status becomes **CANCELLED**; it disappears from the public listing.
- [ ] Each **attendee** of e6 (alice.k, maria.g, yannis.d, eleni.v) receives a **notification message** about the cancellation in their inbox.
- [ ] Log in as `eleni.v`; a cancellation notification for e6 is present (unread).
- [ ] The attendees' e6 bookings are reflected as cancelled / refundable per the app's rule.

---

## 12. XML export + DTD validation (admin)

**Action:** As `admin`, go to **Export** (`/admin/export`) and export **events as XML** (or `GET /api/v1/admin/export/events.xml`).

- [ ] An XML document downloads containing the events, organizers, ticket types and sold counts.
- [ ] Validate against the DTD:

```bash
curl -s -o events.xml -H "Authorization: Bearer $ADMIN_TOKEN" \
  "http://localhost:8080/api/v1/admin/export/events.xml"
xmllint --dtdvalid http://localhost:8080/events.dtd --noout events.xml && echo "DTD VALID"
```

- [ ] `xmllint` exits **0** (valid against `events.dtd`).

---

## 13. JSON export (admin)

**Action:** As `admin`, export **events as JSON** (`GET /api/v1/admin/export/events.json`).

- [ ] A well-formed JSON document downloads with the same event/booking data as the XML.
- [ ] Field values are consistent with the UI (e.g. e6 shows tiers fully sold; e8 near capacity).

---

## 14. Recommender

The model trains on startup from the e-class `event_interest.csv` seed **plus** the seeded
bookings/views. Recommendations exclude events the user already booked.

**Strong signal, `eleni.v`:**

- [ ] Log in as `eleni.v`, open the recommendations view (home) or `GET /api/v1/recommendations?limit=10`.
- [ ] Returns a **non-empty** list, specifically **e9 (Mozart)** and **e10 (Mediterranean Food Festival)**, the two published events she has _not_ booked, ranked by predicted score (BMF model path, not popularity).

**Views-only signal, `giorgos.a`:**

- [ ] Log in as `giorgos.a`; returns up to 10 recommendations (he is in the model via his 5 views, with 0 bookings).

**Cold-start, a freshly-approved user (`sofia.m` or your `testuser1`):**

- [ ] Log in as `sofia.m` (after approval in step 4). With 0 bookings and 0 views she is **not** in the model, so recommendations fall back to **popularity** (most-booked upcoming events: e1/e8/e5... by confirmed sales).
- [ ] Confirm the cold-start list is the **same popularity ranking** for any brand-new user (expected, not a bug), while `eleni.v`'s list is **personalised/different**.

**Sanity:**

- [ ] Recommendations never include an event the user already booked.
- [ ] Recommendations never include DRAFT/CANCELLED/COMPLETED events (PUBLISHED upcoming only).

---

## Coverage map (assignment requirement to section)

| Requirement                                    | Section |
| ---------------------------------------------- | ------- |
| Welcome / public landing & browse              | 1       |
| Registration (+ AFM validation)                | 2       |
| Forced password change                         | 3       |
| Admin approval workflow                        | 4       |
| Role-based login & access                      | 5, 4    |
| Event creation (map, categories, tiers)        | 6       |
| Publish lifecycle                              | 7       |
| Search (keyword/category/city/date-range)      | 8       |
| Booking + "no seats" + cancellation            | 9       |
| Messaging (read/unread, event-tied)            | 10      |
| Cancellation broadcast to attendees            | 11      |
| XML export + DTD validation                    | 12      |
| JSON export                                    | 13      |
| Recommender (strong / views-only / cold-start) | 14      |
