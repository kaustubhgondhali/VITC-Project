package com.vitc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One-time credentials produced when a paid order provisions a student
 * account. {@code temporaryPassword} is populated only for the internal
 * hand-off from {@code StudentAccountService} to
 * {@code StudentCredentialEmailService} (which mails it and never persists
 * or logs it) - the checkout confirmation API strips it before the response
 * ever reaches the browser (see {@code CheckoutServiceImpl.redactPassword}).
 * No endpoint returns a plaintext password to a client.
 */
@Schema(name = "StudentCredentialsResponse",
        description = "One-time student credentials returned immediately after a verified payment")
public record StudentCredentialsResponse(
        String studentLoginId,
        String temporaryPassword,
        boolean newAccount,
        String message) {
}
