package gr.uoa.di.ted.events.service.xml;

import gr.uoa.di.ted.events.dto.export.*;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.OutputStream;
import java.util.List;

/**
 * Streams an Events XML document using StAX (XMLStreamWriter).
 *
 * StAX rather than JAXB or DOM because both of those build the whole document in
 * memory before writing a byte, so peak heap equals the entire payload. StAX
 * writes tokens straight to the OutputStream, so peak memory is roughly one
 * ExportEventDto and an admin can export tens of thousands of events without a
 * GC pause or OOM.
 *
 * The DOCTYPE line points a validating parser at events.dtd (a relative URI;
 * Spring Boot serves it from resources/static/ at /events.dtd). The DTD content
 * model is ordered, so this writer emits elements in the exact declared sequence.
 */
public final class ExportXmlWriter {

    private ExportXmlWriter() {}

    public static void write(OutputStream out, ExportEventsWrapper wrapper)
            throws XMLStreamException {

        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLStreamWriter  writer  = factory.createXMLStreamWriter(out, "UTF-8");

        writer.writeStartDocument("UTF-8", "1.0");
        // writeDTD emits the string verbatim after the XML declaration
        writer.writeDTD("<!DOCTYPE Events SYSTEM \"events.dtd\">");
        writer.writeStartElement("Events");

        for (ExportEventDto ev : wrapper.events()) {
            writeEvent(writer, ev);
        }

        writer.writeEndElement(); // </Events>
        writer.writeEndDocument();
        writer.flush();
        writer.close();
    }

    // event

    private static void writeEvent(XMLStreamWriter w, ExportEventDto ev)
            throws XMLStreamException {

        w.writeStartElement("Event");
        w.writeAttribute("EventID", String.valueOf(ev.eventId()));

        // DTD order: Title, Category+, EventType, Venue, Address, City, Country,
        //            GeoLocation?, StartDateTime, EndDateTime, Capacity,
        //            TicketTypes, Bookings, Organizer, Status, Description, Media?

        writeText(w, "Title", ev.title());

        for (String cat : ev.categories()) {
            writeText(w, "Category", cat);
        }

        writeText(w, "EventType", ev.eventType());
        writeText(w, "Venue",     safe(ev.venue()));
        writeText(w, "Address",   safe(ev.address()));
        writeText(w, "City",      safe(ev.city()));
        writeText(w, "Country",   safe(ev.country()));

        // GeoLocation is optional; omit it when coordinates aren't recorded
        if (ev.latitude() != null && ev.longitude() != null) {
            w.writeEmptyElement("GeoLocation");
            w.writeAttribute("Latitude",  ev.latitude());
            w.writeAttribute("Longitude", ev.longitude());
        }

        writeText(w, "StartDateTime", ev.startDateTime());
        writeText(w, "EndDateTime",   ev.endDateTime());
        writeText(w, "Capacity",      String.valueOf(ev.capacity()));

        writeTicketTypes(w, ev.ticketTypes());
        writeBookings(w, ev.bookings());

        // Organizer is EMPTY with a required UserID attribute
        w.writeEmptyElement("Organizer");
        w.writeAttribute("UserID", String.valueOf(ev.organizerId()));

        writeText(w, "Status",      ev.status());
        writeText(w, "Description", safe(ev.description()));

        // Media is optional; omit it when the event has no photos
        List<String> photos = ev.photoUrls();
        if (photos != null && !photos.isEmpty()) {
            w.writeStartElement("Media");
            for (String url : photos) {
                writeText(w, "Photo", url);
            }
            w.writeEndElement(); // </Media>
        }

        w.writeEndElement(); // </Event>
    }

    // ticket types

    private static void writeTicketTypes(XMLStreamWriter w, List<ExportTicketTypeDto> types)
            throws XMLStreamException {

        w.writeStartElement("TicketTypes");
        for (ExportTicketTypeDto tt : types) {
            w.writeStartElement("TicketType");
            w.writeAttribute("TicketTypeID", String.valueOf(tt.ticketTypeId()));
            writeText(w, "Name",      tt.name());
            writeText(w, "Price",     tt.price());
            writeText(w, "Quantity",  String.valueOf(tt.quantity()));
            writeText(w, "Available", String.valueOf(tt.available()));
            w.writeEndElement(); // </TicketType>
        }
        w.writeEndElement(); // </TicketTypes>
    }

    // bookings

    private static void writeBookings(XMLStreamWriter w, List<ExportBookingDto> bookings)
            throws XMLStreamException {

        w.writeStartElement("Bookings");
        for (ExportBookingDto b : bookings) {
            w.writeStartElement("Booking");
            w.writeAttribute("BookingID", String.valueOf(b.bookingId()));

            // Attendee is EMPTY with a required UserID attribute
            w.writeEmptyElement("Attendee");
            w.writeAttribute("UserID", String.valueOf(b.attendeeUserId()));

            writeText(w, "Time",            b.time());
            writeText(w, "TicketTypeRef",   String.valueOf(b.ticketTypeRef()));
            writeText(w, "NumberOfTickets", String.valueOf(b.numberOfTickets()));
            writeText(w, "TotalCost",       b.totalCost());
            writeText(w, "BookingStatus",   b.bookingStatus());
            w.writeEndElement(); // </Booking>
        }
        w.writeEndElement(); // </Bookings>
    }

    // helpers

    /** Writes <name>value</name>. StAX escapes &amp; &lt; etc. automatically. */
    private static void writeText(XMLStreamWriter w, String name, String value)
            throws XMLStreamException {
        w.writeStartElement(name);
        if (value != null && !value.isEmpty()) {
            w.writeCharacters(value);
        }
        w.writeEndElement();
    }

    /** Returns empty string for null, keeping required DTD elements valid. */
    private static String safe(String s) {
        return s != null ? s : "";
    }
}
