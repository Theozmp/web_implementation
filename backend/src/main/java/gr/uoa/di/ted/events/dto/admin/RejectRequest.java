package gr.uoa.di.ted.events.dto.admin;

/** Optional body for the reject endpoint; reason may be null or blank. */
public record RejectRequest(String reason) {}
