package gr.uoa.di.ted.events;

import gr.uoa.di.ted.events.dto.booking.BookingCreateRequest;
import gr.uoa.di.ted.events.dto.event.EventCreateRequest;
import gr.uoa.di.ted.events.dto.event.TicketTypeRequest;
import gr.uoa.di.ted.events.dto.export.ExportEventsWrapper;
import gr.uoa.di.ted.events.entity.User;
import gr.uoa.di.ted.events.enums.Role;
import gr.uoa.di.ted.events.enums.UserStatus;
import gr.uoa.di.ted.events.repository.*;
import gr.uoa.di.ted.events.service.BookingService;
import gr.uoa.di.ted.events.service.EventService;
import gr.uoa.di.ted.events.service.ExportService;
import gr.uoa.di.ted.events.service.xml.ExportXmlWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the XML from ExportService + ExportXmlWriter validates against events.dtd
 * with zero errors.
 *
 * Seeds one PUBLISHED event (category, ticket type, geo-location) and one confirmed
 * booking so every optional element appears, generates the XML through the same path
 * as the endpoint, parses it with DocumentBuilderFactory.setValidating(true), and
 * asserts no SAXParseExceptions. An EntityResolver loads events.dtd from the
 * classpath so the test doesn't need a running server.
 *
 * DocumentBuilderFactory rather than javax.xml.validation.Validator because DTD
 * validation in Java happens in the SAX/DOM parser, not that API (which is for XML
 * Schema/RelaxNG); setValidating(true) applies the DTD named in the DOCTYPE.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
class ExportDtdValidationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired ExportService     exportService;
    @Autowired EventService      eventService;
    @Autowired BookingService    bookingService;
    @Autowired BookingRepository bookingRepository;
    @Autowired EventRepository   eventRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UserRepository    userRepository;
    @Autowired PasswordEncoder   passwordEncoder;

    private Long eventId;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getRole() != Role.ADMIN)
                .forEach(userRepository::delete);

        User organizer   = createUser("exp_org",  "exp_org@test.com",  Role.ORGANIZER);
        User participant = createUser("exp_part", "exp_part@test.com", Role.PARTICIPANT);

        Long catId = categoryRepository.findAll().get(0).getId();

        var req = new EventCreateRequest(
                "Export Test Concert",
                "A concert for the export test",
                LocalDateTime.now().plusDays(14),
                LocalDateTime.now().plusDays(14).plusHours(3),
                100,
                "Test Arena", "123 Main St", "Athens", "Greece",
                new BigDecimal("37.97945000"),
                new BigDecimal("23.71622000"),
                List.of(catId),
                List.of(new TicketTypeRequest(null, "General Admission", new BigDecimal("25.00"), 100))
        );

        var detail = eventService.createEvent(req, organizer.getUsername());
        eventService.publishEvent(detail.id(), organizer.getUsername());
        eventId = detail.id();

        Long ttId = detail.ticketTypes().get(0).id();
        bookingService.createBooking(eventId,
                new BookingCreateRequest(ttId, 2), participant.getUsername());
    }

    @Test
    void generatedXml_validatesAgainstDtd_withNoErrors() throws Exception {
        // 1. Build the export DTO from the live database
        ExportEventsWrapper wrapper = exportService.buildExport();
        assertThat(wrapper.events()).isNotEmpty();

        // 2. Serialize to XML bytes using the same StAX writer as the endpoint
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ExportXmlWriter.write(baos, wrapper);
        byte[] xmlBytes = baos.toByteArray();

        // Sanity check: XML should reference the DOCTYPE
        String xmlString = new String(xmlBytes);
        assertThat(xmlString).contains("<!DOCTYPE Events SYSTEM \"events.dtd\">");
        assertThat(xmlString).contains("<GeoLocation");   // geo-location is present
        assertThat(xmlString).contains("<Booking ");       // at least one booking

        // 3. Validate against the DTD with the JDK SAX parser
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(true);   // activates DTD validation

        DocumentBuilder builder = factory.newDocumentBuilder();

        // collect all validation errors instead of throwing on the first
        List<SAXParseException> errors = new ArrayList<>();
        builder.setErrorHandler(new ErrorHandler() {
            @Override public void warning(SAXParseException e)  { /* ignore warnings */ }
            @Override public void error(SAXParseException e)    { errors.add(e); }
            @Override public void fatalError(SAXParseException e) throws org.xml.sax.SAXException { throw e; }
        });

        // resolve "events.dtd" from the classpath so the test needs no running
        // server. In production Spring Boot serves it from resources/static/; here
        // we read the same file off the classpath.
        builder.setEntityResolver((publicId, systemId) -> {
            if (systemId != null && systemId.endsWith("events.dtd")) {
                InputStream dtd = getClass().getResourceAsStream("/static/events.dtd");
                return new InputSource(dtd);
            }
            return null;
        });

        // 4. parse; the DTD validator fires during parsing
        builder.parse(new ByteArrayInputStream(xmlBytes));

        // 5. Assert zero validation errors
        assertThat(errors)
                .as("XML should validate against events.dtd with no errors; found: %s", errors)
                .isEmpty();
    }

    private User createUser(String username, String email, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Test@1234"));
        u.setFirstName("Test");
        u.setLastName("User");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        if (role == Role.ORGANIZER) u.setTaxId("094259216");
        return userRepository.save(u);
    }
}
