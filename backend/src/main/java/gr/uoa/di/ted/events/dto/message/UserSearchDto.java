package gr.uoa.di.ted.events.dto.message;

/** Minimal user projection returned by the recipient autocomplete endpoint. */
public record UserSearchDto(Long id, String username) {}
