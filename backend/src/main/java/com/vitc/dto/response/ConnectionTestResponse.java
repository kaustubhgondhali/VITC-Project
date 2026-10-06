package com.vitc.dto.response;

/** Result of "Test connection" — never carries credentials. */
public record ConnectionTestResponse(boolean success, String message) {
}
