package com.vitc.entity.enums;

/**
 * Transport security of the outgoing-mail (SMTP) connection.
 *
 * <p>The two modes are deliberately kept apart: STARTTLS upgrades a plain connection
 * (normally port 587), SSL_TLS is TLS from the first byte (normally port 465). Mixing
 * them (e.g. STARTTLS on 465) never works, so the settings service rejects that.</p>
 */
public enum SmtpSecurityMode {
    STARTTLS,
    SSL_TLS;

    /** Legacy rows saved before this column existed: 465 meant implicit TLS, anything else STARTTLS. */
    public static SmtpSecurityMode derivedFromPort(Integer port) {
        return port != null && port == 465 ? SSL_TLS : STARTTLS;
    }
}
