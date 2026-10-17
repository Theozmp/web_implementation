package gr.uoa.di.ted.events.config;

import gr.uoa.di.ted.events.entity.*;
import gr.uoa.di.ted.events.enums.BookingStatus;
import gr.uoa.di.ted.events.enums.EventStatus;
import gr.uoa.di.ted.events.enums.Role;
import gr.uoa.di.ted.events.enums.UserStatus;
import gr.uoa.di.ted.events.recommender.RecommendationBootstrap;
import gr.uoa.di.ted.events.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds a realistic end-to-end test dataset, active only under the {@code test-data}
 * Spring profile ({@code -Dspring.profiles.active=dev,test-data}). It never runs in
 * prod, so production data stays clean.
 *
 * <p>Runs as a {@link CommandLineRunner}, which Spring invokes before the
 * {@code ApplicationReadyEvent} that trains the recommender, so the seeded
 * bookings/views are committed first. It then calls
 * {@link RecommendationBootstrap#retrain()} so the model learns from the seeded data
 * even if an older saved model exists.
 *
 * <p>Idempotent: if {@code alice.k} already exists, seeding is skipped (the
 * credentials table is just re-printed), so restarts are safe.
 *
 * <p>Seeds 8 users (6 ACTIVE, 2 PENDING), 15 events (2 DRAFT, 10 PUBLISHED,
 * 1 CANCELLED, 2 COMPLETED) across 4 cities, 30 bookings (27 CONFIRMED, 3 CANCELLED)
 * including a fully-booked and a near-capacity event, 20 views, and 10 messages with
 * mixed read/unread state.
 */
@Slf4j
@Component
@Profile("test-data")
@RequiredArgsConstructor
public class TestDataSeeder implements CommandLineRunner {

    /** Shared dev password for every seeded account. */
    private static final String PWD = "Test@Pass123!";

    // city coordinates (real lat/lng) reused across events.
    private static final double[] ATHENS       = {37.9838, 23.7275};
    private static final double[] THESSALONIKI = {40.6401, 22.9444};
    private static final double[] PATRAS       = {38.2466, 21.7346};
    private static final double[] HERAKLION    = {35.3387, 25.1442};

    private final UserRepository       users;
    private final EventRepository      events;
    private final CategoryRepository   categories;
    private final BookingRepository    bookings;
    private final EventViewRepository  views;
    private final MessageRepository    messages;
    private final TicketTypeRepository ticketTypes;
    private final PasswordEncoder      encoder;
    private final RecommendationBootstrap recommender;
    private final PlatformTransactionManager txManager;

    // lookups populated during seeding.
    private final Map<String, Category> catByName = new HashMap<>();
    private final Map<String, User>     userByName = new HashMap<>();
    private final Map<String, Event>    eventByKey = new HashMap<>();

    @Override
    public void run(String... args) {
        if (users.existsByUsername("alice.k")) {
            log.info("[test-data] Seed data already present (alice.k exists) — skipping insert.");
            printCredentials();
            return;
        }

        log.info("[test-data] Seeding realistic end-to-end test dataset…");
        new TransactionTemplate(txManager).executeWithoutResult(status -> populate());
        log.info("[test-data] Insert complete. Retraining recommender on the augmented data…");
        recommender.retrain();
        printCredentials();
    }

    // population

    private void populate() {
        categories.findAll().forEach(c -> catByName.put(c.getName(), c));

        seedUsers();
        seedEvents();
        seedBookings();
        seedViews();
        seedMessages();

        log.info("[test-data] Inserted {} users, {} events, {} bookings, {} views, {} messages.",
                userByName.size(), eventByKey.size(), bookings.count(), views.count(), messages.count());
    }

    private void seedUsers() {
        // 6 ACTIVE (4 organizers, 2 participants), 2 PENDING (1 participant, 1 organizer).
        user("alice.k",   "Alice",   "Konstantinou", Role.ORGANIZER,   UserStatus.ACTIVE,  "Athens",       ATHENS,       "094259216");
        user("nikos.p",   "Nikos",   "Papadopoulos", Role.ORGANIZER,   UserStatus.ACTIVE,  "Thessaloniki", THESSALONIKI, "123456783");
        user("maria.g",   "Maria",   "Georgiou",     Role.ORGANIZER,   UserStatus.ACTIVE,  "Patras",       PATRAS,       "234567897");
        user("yannis.d",  "Yannis",  "Dimitriou",    Role.ORGANIZER,   UserStatus.ACTIVE,  "Heraklion",    HERAKLION,    "456789010");
        user("eleni.v",   "Eleni",   "Vasiliou",     Role.PARTICIPANT, UserStatus.ACTIVE,  "Athens",       ATHENS,       "876543210");
        user("giorgos.a", "Giorgos", "Antoniou",     Role.PARTICIPANT, UserStatus.ACTIVE,  "Thessaloniki", THESSALONIKI, "192837460");
        user("sofia.m",   "Sofia",   "Makri",        Role.PARTICIPANT, UserStatus.PENDING, "Patras",       PATRAS,       "564738294");
        user("kostas.t",  "Kostas",  "Triantafyllou",Role.ORGANIZER,   UserStatus.PENDING, "Heraklion",    HERAKLION,    "918273648");
    }

    private void seedEvents() {
        LocalDateTime now = LocalDateTime.now();

        // 10 PUBLISHED (future)
        event("e1", "Athens Summer Jazz Festival",
                "Three open-air stages of contemporary and classic jazz under the Athenian sky, with local and international acts.",
                "alice.k", EventStatus.PUBLISHED, future(now, 10, 3), "Athens", ATHENS, 300,
                List.of("Music", "Festival"),
                tt("General Admission", 25, 180), tt("VIP", 60, 60), tt("Student", 15, 40));

        event("e2", "TechSummit Greece 2026",
                "The country's largest software and cloud conference: keynotes, hands-on labs, and a startup expo floor.",
                "nikos.p", EventStatus.PUBLISHED, future(now, 20, 8), "Thessaloniki", THESSALONIKI, 500,
                List.of("Conference"),
                tt("Standard", 80, 300), tt("Early Bird", 50, 100));

        event("e3", "Acropolis Open-Air Theatre: Antigone",
                "Sophocles' tragedy staged at dusk in a historic open-air amphitheatre with a full orchestral accompaniment.",
                "maria.g", EventStatus.PUBLISHED, future(now, 15, 2), "Athens", ATHENS, 200,
                List.of("Theatre"),
                tt("Orchestra", 40, 80), tt("Balcony", 25, 120));

        event("e4", "Patras Carnival Workshop: Mask Making",
                "A hands-on afternoon learning traditional Patras Carnival mask techniques with master craftspeople.",
                "yannis.d", EventStatus.PUBLISHED, future(now, 25, 4), "Patras", PATRAS, 50,
                List.of("Workshop"),
                tt("Participation", 30, 50));

        event("e5", "Heraklion Wine & Food Festival",
                "A weekend celebrating Cretan cuisine: tastings from dozens of local wineries and producers, plus live music.",
                "alice.k", EventStatus.PUBLISHED, future(now, 30, 9), "Heraklion", HERAKLION, 400,
                List.of("Festival", "Family"),
                tt("General Admission", 20, 300), tt("Family Pass", 50, 100));

        event("e6", "Indie Rock Night: The Coastliners",
                "An intimate club gig from the breakout indie quartet, with two supporting local bands.",
                "nikos.p", EventStatus.PUBLISHED, future(now, 12, 4), "Thessaloniki", THESSALONIKI, 120,
                List.of("Music"),
                tt("General Admission", 18, 12), tt("VIP", 45, 8)); // small tiers, fully booked below

        event("e7", "AI & Robotics Conference",
                "Researchers and industry leaders explore the frontier of machine learning, autonomy, and ethics.",
                "maria.g", EventStatus.PUBLISHED, future(now, 40, 8), "Athens", ATHENS, 350,
                List.of("Conference", "Workshop"),
                tt("Standard", 70, 250), tt("Student", 35, 100));

        event("e8", "Stand-up Comedy Gala",
                "An evening of sharp, fast-paced stand-up from Greece's best touring comedians.",
                "yannis.d", EventStatus.PUBLISHED, future(now, 18, 3), "Patras", PATRAS, 180,
                List.of("Comedy"),
                tt("General Admission", 22, 14), tt("Front Row", 40, 6)); // near capacity, see below

        event("e9", "Classical Evenings: Mozart by Candlelight",
                "A chamber ensemble performs Mozart favourites in a candlelit hall — an intimate acoustic experience.",
                "alice.k", EventStatus.PUBLISHED, future(now, 50, 2), "Heraklion", HERAKLION, 150,
                List.of("Music"),
                tt("General Admission", 30, 100), tt("Premium", 55, 50));

        event("e10", "Mediterranean Food Festival",
                "Street food, chef demos, and family activities celebrating the flavours of the Mediterranean.",
                "nikos.p", EventStatus.PUBLISHED, future(now, 60, 10), "Athens", ATHENS, 500,
                List.of("Festival"),
                tt("General Admission", 15, 400), tt("Tasting Pass", 45, 100));

        // 2 COMPLETED (past)
        event("e11", "Spring Coding Bootcamp 2026",
                "An intensive two-day bootcamp covering modern web development, from APIs to deployment.",
                "maria.g", EventStatus.COMPLETED, past(now, 40, 30), "Thessaloniki", THESSALONIKI, 120,
                List.of("Workshop", "Conference"),
                tt("Full Pass", 60, 120));

        event("e12", "Winter Theatre Series: Hamlet",
                "A modern-dress production of Shakespeare's Hamlet from an acclaimed touring company.",
                "yannis.d", EventStatus.COMPLETED, past(now, 20, 3), "Athens", ATHENS, 220,
                List.of("Theatre"),
                tt("Orchestra", 35, 150), tt("Balcony", 20, 70));

        // 1 CANCELLED
        event("e13", "Open-Air Cinema: Cult Classics",
                "A summer season of cult film screenings under the stars — cancelled due to venue refurbishment.",
                "alice.k", EventStatus.CANCELLED, future(now, 35, 3), "Patras", PATRAS, 300,
                List.of("Family", "Music"),
                tt("General Admission", 10, 300));

        // 2 DRAFT
        event("e14", "Autumn Art Exhibition: Modern Greece",
                "A curated exhibition of contemporary Greek painters and sculptors (still being finalised).",
                "nikos.p", EventStatus.DRAFT, future(now, 75, 6), "Athens", ATHENS, 200,
                List.of("Exhibition"),
                tt("General Admission", 12, 150), tt("Guided Tour", 25, 50));

        event("e15", "Kids Science Discovery Day",
                "Interactive science experiments and shows for children and families (programme in progress).",
                "maria.g", EventStatus.DRAFT, future(now, 80, 6), "Heraklion", HERAKLION, 250,
                List.of("Family", "Workshop"),
                tt("Child", 8, 150), tt("Adult", 15, 100));
    }

    private void seedBookings() {
        // strong-signal participant eleni.v: many bookings across several categories.
        book("eleni.v",  "e1",  "General Admission", 2, BookingStatus.CONFIRMED);
        book("eleni.v",  "e2",  "Early Bird",        1, BookingStatus.CONFIRMED);
        book("eleni.v",  "e3",  "Balcony",           2, BookingStatus.CONFIRMED);
        book("eleni.v",  "e4",  "Participation",     1, BookingStatus.CONFIRMED);
        book("eleni.v",  "e5",  "General Admission", 2, BookingStatus.CONFIRMED);
        book("eleni.v",  "e7",  "Student",           1, BookingStatus.CONFIRMED);
        book("eleni.v",  "e8",  "General Admission", 2, BookingStatus.CONFIRMED);
        book("eleni.v",  "e12", "Orchestra",         1, BookingStatus.CONFIRMED);
        // eleni deliberately doesn't book e9 or e10, so they remain live candidates
        // and her /recommendations returns a non-empty, model-ranked list.

        // e6 fully booked: GA 12/12 and VIP 8/8, so 0 seats left on every tier.
        book("alice.k",  "e6",  "General Admission", 4, BookingStatus.CONFIRMED);
        book("maria.g",  "e6",  "General Admission", 4, BookingStatus.CONFIRMED);
        book("yannis.d", "e6",  "General Admission", 4, BookingStatus.CONFIRMED);
        book("eleni.v",  "e6",  "VIP",               4, BookingStatus.CONFIRMED);
        book("alice.k",  "e6",  "VIP",               4, BookingStatus.CONFIRMED);

        // e8 near capacity: GA 10/14 (4 left) + Front Row 4/6 (2 left).
        book("alice.k",  "e8",  "General Admission", 4, BookingStatus.CONFIRMED);
        book("nikos.p",  "e8",  "General Admission", 4, BookingStatus.CONFIRMED);
        book("maria.g",  "e8",  "Front Row",         4, BookingStatus.CONFIRMED);

        // Spread across organizers (attending other organizers' events) and events.
        book("alice.k",  "e2",  "Standard",          2, BookingStatus.CONFIRMED);
        book("nikos.p",  "e1",  "General Admission", 2, BookingStatus.CONFIRMED);
        book("maria.g",  "e1",  "VIP",               1, BookingStatus.CONFIRMED);
        book("yannis.d", "e1",  "Student",           2, BookingStatus.CONFIRMED);
        book("nikos.p",  "e3",  "Orchestra",         2, BookingStatus.CONFIRMED);
        book("maria.g",  "e5",  "Family Pass",       1, BookingStatus.CONFIRMED);
        book("yannis.d", "e7",  "Standard",          1, BookingStatus.CONFIRMED);
        book("nikos.p",  "e11", "Full Pass",         2, BookingStatus.CONFIRMED);
        book("nikos.p",  "e12", "Balcony",           2, BookingStatus.CONFIRMED);
        book("nikos.p",  "e9",  "General Admission", 1, BookingStatus.CONFIRMED); // keeps e9 in the model
        book("yannis.d", "e10", "General Admission", 2, BookingStatus.CONFIRMED); // keeps e10 in the model

        // 3 CANCELLED bookings; these don't consume seats or count as sold.
        book("alice.k",  "e10", "Tasting Pass",      2, BookingStatus.CANCELLED);
        book("maria.g",  "e9",  "Premium",           1, BookingStatus.CANCELLED);
        book("yannis.d", "e2",  "Standard",          1, BookingStatus.CANCELLED);
    }

    private void seedViews() {
        // giorgos.a: ACTIVE, 0 bookings but 5 views, exercising the views-only recommender signal.
        view("giorgos.a", "e1",  null);
        view("giorgos.a", "e2",  null);
        view("giorgos.a", "e6",  null);
        view("giorgos.a", "e8",  null);
        view("giorgos.a", "e10", null);
        // browsing before booking, plus interest from other users.
        view("eleni.v",   "e6",  null);
        view("eleni.v",   "e11", null);
        view("eleni.v",   "e13", null);
        view("alice.k",   "e2",  null);
        view("alice.k",   "e7",  null);
        view("nikos.p",   "e1",  null);
        view("nikos.p",   "e8",  null);
        view("maria.g",   "e1",  null);
        view("maria.g",   "e10", null);
        view("yannis.d",  "e3",  null);
        view("yannis.d",  "e9",  null);
        // 4 anonymous (not logged-in) views.
        view(null, "e1",  "203.0.113.10");
        view(null, "e2",  "203.0.113.11");
        view(null, "e5",  "198.51.100.7");
        view(null, "e10", "198.51.100.8");
    }

    private void seedMessages() {
        // 6 tied to an event, 4 general, mixed read/unread.
        msg("eleni.v",  "alice.k",  "e1", "Question about VIP tickets for the Jazz Festival",
                "Hi Alice — do the VIP tickets include backstage access? Thanks!", false);
        msg("alice.k",  "eleni.v",  "e1", "Re: VIP tickets for the Jazz Festival",
                "Hi Eleni, yes — VIP includes the backstage lounge and a welcome drink. See you there!", true);
        msg("giorgos.a","nikos.p",  "e6", "Is the Indie Rock Night sold out?",
                "Hey, I noticed tickets are gone — any chance of a second date?", false);
        msg("nikos.p",  "giorgos.a","e6", "Re: Indie Rock Night availability",
                "We're fully booked, but we're looking at adding a matinee. I'll keep you posted!", true);
        msg("maria.g",  "yannis.d", null, "Collaboration on a joint workshop?",
                "Yannis, would you be interested in co-hosting a stagecraft workshop next season?", true);
        msg("yannis.d", "maria.g",  null, "Re: Collaboration on a joint workshop?",
                "Love the idea — let's find a date. I can bring lighting and sound.", false);
        msg("eleni.v",  "maria.g",  "e7", "Accessibility at the AI & Robotics Conference",
                "Is the venue wheelchair accessible, and will talks have live captions?", false);
        msg("giorgos.a","maria.g",  null, "Group booking discount?",
                "Do you offer group rates for 10+ attendees across your events?", true);
        msg("alice.k",  "nikos.p",  null, "Cross-promotion for summer events",
                "Want to cross-promote the Jazz Festival and your Food Festival in one newsletter?", false);
        msg("eleni.v",  "yannis.d", "e12","Loved the Hamlet production!",
                "Just wanted to say the modern staging was fantastic — bravo to the whole cast.", true);
    }

    // builders

    private void user(String username, String first, String last, Role role, UserStatus status,
                      String city, double[] coords, String taxId) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username.replace('.', '_') + "@example.com");
        u.setPasswordHash(encoder.encode(PWD));
        u.setFirstName(first);
        u.setLastName(last);
        u.setRole(role);
        u.setStatus(status);
        u.setMustChangePassword(false);
        u.setCity(city);
        u.setCountry("Greece");
        u.setLatitude(BigDecimal.valueOf(coords[0]));
        u.setLongitude(BigDecimal.valueOf(coords[1]));
        u.setTaxId(taxId);
        userByName.put(username, users.save(u));
    }

    /** Inline ticket-type spec used by {@link #event}. */
    private record TtSpec(String name, double price, int quantity) {}

    private TtSpec tt(String name, double price, int quantity) {
        return new TtSpec(name, price, quantity);
    }

    private void event(String key, String title, String description, String organizerUsername,
                       EventStatus status, LocalDateTime[] span, String city, double[] coords,
                       int capacity, List<String> categoryNames, TtSpec... tts) {
        Event e = new Event();
        e.setTitle(title);
        e.setDescription(description);
        e.setOrganizer(userByName.get(organizerUsername));
        e.setStatus(status);
        e.setStartDatetime(span[0]);
        e.setEndDatetime(span[1]);
        e.setVenueName(city + " Main Venue");
        e.setAddress("1 Central Square");
        e.setCity(city);
        e.setCountry("Greece");
        e.setLatitude(BigDecimal.valueOf(coords[0]));
        e.setLongitude(BigDecimal.valueOf(coords[1]));
        e.setCapacity(capacity);
        categoryNames.forEach(n -> e.getCategories().add(catByName.get(n)));
        for (TtSpec spec : tts) {
            TicketType t = new TicketType();
            t.setName(spec.name());
            t.setPrice(BigDecimal.valueOf(spec.price()));
            t.setQuantity(spec.quantity());
            e.addTicketType(t);
        }
        eventByKey.put(key, events.save(e));
    }

    private void book(String username, String eventKey, String ticketName, int qty, BookingStatus status) {
        Event e = eventByKey.get(eventKey);
        User u = userByName.get(username);
        TicketType t = e.getTicketTypes().stream()
                .filter(x -> x.getName().equals(ticketName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No ticket type '" + ticketName + "' on event " + eventKey));

        Booking b = new Booking();
        b.setEvent(e);
        b.setAttendee(u);
        b.setTicketType(t);
        b.setQuantity(qty);
        b.setTotalPrice(t.getPrice().multiply(BigDecimal.valueOf(qty)));
        b.setStatus(status);
        bookings.save(b);

        // Only CONFIRMED bookings consume seats (sold = SUM of CONFIRMED quantities).
        if (status == BookingStatus.CONFIRMED) {
            t.setQuantity(t.getQuantity() - qty);
            ticketTypes.save(t);
        }
    }

    private void view(String username, String eventKey, String ip) {
        EventView v = new EventView();
        v.setEvent(eventByKey.get(eventKey));
        v.setViewer(username == null ? null : userByName.get(username));
        v.setIpAddress(ip);
        views.save(v);
    }

    private void msg(String sender, String recipient, String eventKey,
                     String subject, String body, boolean read) {
        Message m = new Message();
        m.setSender(userByName.get(sender));
        m.setRecipient(userByName.get(recipient));
        m.setEvent(eventKey == null ? null : eventByKey.get(eventKey));
        m.setSubject(subject);
        m.setBody(body);
        if (read) {
            m.setReadAt(LocalDateTime.now());
        }
        messages.save(m);
    }

    /** Future event window: starts in {@code days} days at 19:00, lasts {@code hours}. */
    private static LocalDateTime[] future(LocalDateTime now, int days, int hours) {
        LocalDateTime start = now.plusDays(days).withHour(19).withMinute(0).withSecond(0).withNano(0);
        return new LocalDateTime[]{start, start.plusHours(hours)};
    }

    /** Past (COMPLETED) event window: ended {@code days} ago. */
    private static LocalDateTime[] past(LocalDateTime now, int days, int hours) {
        LocalDateTime start = now.minusDays(days).withHour(19).withMinute(0).withSecond(0).withNano(0);
        return new LocalDateTime[]{start, start.plusHours(hours)};
    }

    // credentials table

    private void printCredentials() {
        String pwd = PWD;
        String table = """

                ╔══════════════════════════════════════════════════════════════════════════════════════════════╗
                ║                          TEST-DATA PROFILE — SEEDED ACCOUNTS                                   ║
                ║                          (all passwords below: %-37s)║
                ╠═══════════════╦══════════════╦══════════╦══════════════════════════════════════════════════════╣
                ║ username      ║ role         ║ status   ║ what they have / what to test                          ║
                ╠═══════════════╬══════════════╬══════════╬══════════════════════════════════════════════════════╣
                ║ admin         ║ ADMIN        ║ ACTIVE   ║ see ADMIN_INITIAL_PASSWORD (default Admin@123456),     ║
                ║               ║              ║          ║ must change on first login; approves pending users     ║
                ║ alice.k       ║ ORGANIZER    ║ ACTIVE   ║ organizes 4 events (incl. CANCELLED e13); 5 bookings   ║
                ║ nikos.p       ║ ORGANIZER    ║ ACTIVE   ║ organizes 4 events (incl. DRAFT e14); 7 bookings       ║
                ║ maria.g       ║ ORGANIZER    ║ ACTIVE   ║ organizes 4 events (e3,e7,e11,e15); 5 bookings         ║
                ║ yannis.d      ║ ORGANIZER    ║ ACTIVE   ║ organizes 3 events (incl. COMPLETED e12); 5 bookings   ║
                ║ eleni.v       ║ PARTICIPANT  ║ ACTIVE   ║ 9 bookings across 6 categories → STRONG recommender    ║
                ║               ║              ║          ║ signal (expect personalised, varied recommendations)  ║
                ║ giorgos.a     ║ PARTICIPANT  ║ ACTIVE   ║ 0 bookings but 5 event VIEWS → weak views-only signal  ║
                ║ sofia.m       ║ PARTICIPANT  ║ PENDING  ║ approve me to test the admin-approval flow; 0 activity ║
                ║               ║              ║          ║ → PURE cold-start (popularity fallback) once approved  ║
                ║ kostas.t      ║ ORGANIZER    ║ PENDING  ║ approve me, then I can create events                   ║
                ╚═══════════════╩══════════════╩══════════╩══════════════════════════════════════════════════════╝
                  Event states: e1–e10 PUBLISHED · e11,e12 COMPLETED · e13 CANCELLED · e14,e15 DRAFT
                  Booking edge cases: e6 "Indie Rock Night" = FULLY BOOKED (0 seats) · e8 "Stand-up Comedy Gala" = NEAR CAPACITY (6 left)
                """.formatted(pwd);
        log.info(table);
    }
}
