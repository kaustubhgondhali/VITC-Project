package com.vitc.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Admin payload for saving the Email / SMTP configuration.
 *
 * <p>{@code password} is write-only: it is encrypted immediately and never
 * returned by any endpoint. Leaving it blank on an update keeps the
 * previously stored credential ("Replace Credential" simply sends a new one).</p>
 *
 * <p>{@code fromEmail} is the SMTP email address and {@code fromName} the sender name.
 * {@code provider}, {@code security}, {@code replyToEmail}, {@code sendingDomain},
 * {@code dkimSelector} and {@code enabled} are optional so older callers that only send
 * the original six fields keep working unchanged.</p>
 *
 * <p>Every header-bound value rejects CR/LF so it can never inject extra mail headers.</p>
 */
public record SmtpSettingsRequest(
        @NotBlank(message = "SMTP host is required") @Size(max = 200, message = "SMTP host is too long")
        @Pattern(regexp = "^[A-Za-z0-9.-]+$", message = "SMTP host must be a host name such as smtp.gmail.com")
        String host,

        @NotNull(message = "SMTP port is required")
        @Min(value = 1, message = "SMTP port must be between 1 and 65535")
        @Max(value = 65535, message = "SMTP port must be between 1 and 65535") Integer port,

        @NotBlank(message = "SMTP username is required")
        @Size(max = 200, message = "SMTP username is too long")
        @Pattern(regexp = "^[^\\r\\n]*$", message = "SMTP username must be on a single line")
        String username,

        @Size(max = 200, message = "Credential is too long")
        @Pattern(regexp = "^[^\\r\\n]*$", message = "Credential must be on a single line")
        String password,

        @NotBlank(message = "SMTP email address is required")
        @Email(message = "SMTP email address must be a valid email address")
        @Pattern(regexp = "^\\S+$", message = "SMTP email address must not contain spaces")
        @Size(max = 200, message = "SMTP email address is too long") String fromEmail,

        /* Optional: blank uses the installation's default sender name (app.mail.from-name). */
        @Size(max = 150, message = "Sender name is too long")
        @Pattern(regexp = "^[^\\r\\n<>]*$", message = "Sender name must be a single line without < or >")
        String fromName,

        @Size(max = 40, message = "Provider is too long")
        @Pattern(regexp = "^[A-Za-z_]*$", message = "Unknown email provider")
        String provider,

        @Pattern(regexp = "(?i)^(STARTTLS|SSL_TLS)?$", message = "Security must be STARTTLS or SSL_TLS")
        String security,

        @Email(message = "Reply-To must be a valid email address")
        @Pattern(regexp = "^\\S*$", message = "Reply-To must not contain spaces")
        @Size(max = 200, message = "Reply-To email is too long") String replyToEmail,

        @Size(max = 190, message = "Sending domain is too long")
        @Pattern(regexp = "^$|^([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$",
                message = "Verified sending domain must be a domain name such as vitc.in")
        String sendingDomain,

        @Size(max = 100, message = "DKIM selector is too long")
        @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9._-]*$",
                message = "DKIM selector may contain letters, digits, dot, underscore and hyphen only")
        String dkimSelector,

        Boolean enabled) {
}
