package gr.uoa.di.ted.events.controller;

import gr.uoa.di.ted.events.dto.export.ExportEventsWrapper;
import gr.uoa.di.ted.events.service.ExportService;
import gr.uoa.di.ted.events.service.xml.ExportXmlWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.xml.stream.XMLStreamException;

/**
 * Admin-only export endpoints.
 *
 * GET /api/v1/admin/export/events.xml returns application/xml (StAX-streamed,
 * DTD-referenced); events.json returns application/json (Jackson).
 *
 * Both load all events via ExportService (one @Transactional read) and then
 * write the DTOs. Loading is kept separate from streaming so the JPA transaction
 * commits before the response body is written, instead of holding a DB connection
 * open for the whole download.
 */
@RestController
@RequestMapping("/api/v1/admin/export")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminExportController {

    private final ExportService exportSvc;

    /**
     * XML export via StAX StreamingResponseBody. Spring's async executor writes
     * the stream incrementally, so the browser can start downloading while later
     * events are still being serialised, and peak server memory stays at one
     * ExportEventDto rather than the whole document.
     */
    @GetMapping(value = "/events.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<StreamingResponseBody> exportXml() {
        ExportEventsWrapper wrapper = exportSvc.buildExport();

        StreamingResponseBody body = out -> {
            try {
                ExportXmlWriter.write(out, wrapper);
            } catch (XMLStreamException e) {
                throw new RuntimeException("XML serialisation failed", e);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"events.xml\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(body);
    }

    /**
     * JSON export: Jackson serialises ExportEventsWrapper directly. Structure
     * mirrors the XML: a top-level "events" array, each item carrying
     * "categories", "ticketTypes", "bookings", etc.
     */
    @GetMapping(value = "/events.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ExportEventsWrapper> exportJson() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"events.json\"")
                .body(exportSvc.buildExport());
    }
}
