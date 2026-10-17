package gr.uoa.di.ted.events.dto.export;

import java.util.List;

/** Top-level wrapper: serialises to { "events": [...] } in JSON and <Events>...</Events> in XML. */
public record ExportEventsWrapper(List<ExportEventDto> events) {}
