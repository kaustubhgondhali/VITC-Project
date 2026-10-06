package com.vitc.mail;

import com.vitc.entity.enums.SmtpSecurityMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The one place that knows what each email provider's SMTP service looks like. The admin
 * provider dropdown, the "Load Provider Settings" detection and the save-time validation all
 * read from here, so adding a provider later means adding one entry to {@link #PROVIDERS}.
 *
 * <p>Detection is conservative: a provider is reported from its public mailbox domains or from
 * the MX records of a custom domain (Google Workspace); anything else is CUSTOM with an empty
 * host/port rather than a guess.</p>
 */
public final class MailProviderRegistry {

    /** Pseudo-code used by the dropdown for "detect from the email address". */
    public static final String AUTO = "AUTO";
    public static final String GMAIL = "GMAIL";
    public static final String CUSTOM = "CUSTOM";

    /** How the SMTP username is derived for a provider. */
    public enum UsernameRule {
        /** The full SMTP email address (Gmail, most mailbox providers). */
        FULL_EMAIL,
        /** Credentials issued by the provider that are not the mailbox address. */
        PROVIDER_ISSUED
    }

    public record MailProvider(
            String code,
            String label,
            /** Public mailbox domains that identify the provider directly. */
            List<String> domains,
            /** Substrings found in the MX hostnames of custom domains hosted with this provider. */
            List<String> mxPatterns,
            String smtpHost,
            Integer smtpPort,
            SmtpSecurityMode security,
            UsernameRule usernameRule,
            /** What the secret field is called for this provider. */
            String credentialLabel,
            /** Status text shown once a credential is stored (the credential itself never is). */
            String credentialStatusLabel,
            /** Exact length of the credential once whitespace is removed, or null when free-form. */
            Integer credentialLength,
            /** Short, actionable setup notes shown next to the credential field. */
            List<String> instructions,
            /** DKIM selectors this provider publishes, checked against real DNS - never assumed. */
            List<String> dkimSelectors,
            /** The provider's own page where the admin creates the credential, or null. */
            String credentialSetupUrl) {

        public boolean isCustom() {
            return CUSTOM.equals(code);
        }
    }

    private static final String NEVER_GENERATED =
            "For security reasons VITC cannot generate or retrieve this credential - you enter it, "
                    + "it is stored encrypted on the server and it is never shown again.";

    private static final List<MailProvider> PROVIDERS = List.of(
            new MailProvider(GMAIL, "Gmail / Google Workspace",
                    List.of("gmail.com", "googlemail.com"),
                    List.of("google.com", "googlemail.com"),
                    "smtp.gmail.com", 587, SmtpSecurityMode.STARTTLS, UsernameRule.FULL_EMAIL,
                    "Google App Password", "App Password already configured", 16,
                    List.of("Gmail SMTP does not accept your normal Google password. Turn on 2-Step Verification, "
                                    + "then create an App Password: Google Account -> Security -> 2-Step Verification -> App passwords.",
                            "Copy the 16-character App Password and paste it here (spaces are ignored).",
                            "Google Workspace addresses use the same steps; your Workspace administrator must allow App Passwords.",
                            NEVER_GENERATED),
                    // "google" is the Google Workspace default; the dated ones are what gmail.com publishes.
                    List.of("google", "20230601", "20221208", "20210112", "20161025"),
                    "https://myaccount.google.com/apppasswords"),
            new MailProvider(CUSTOM, "Custom SMTP server",
                    List.of(), List.of(),
                    null, null, SmtpSecurityMode.STARTTLS, UsernameRule.FULL_EMAIL,
                    "SMTP Password", "SMTP password already configured", null,
                    List.of("Enter the SMTP host, port and security exactly as given by your email host "
                                    + "(usually port 587 with STARTTLS, or 465 with SSL/TLS).",
                            "The SMTP username is usually the full email address.",
                            NEVER_GENERATED),
                    List.of("default", "selector1", "selector2", "s1", "s2", "k1", "dkim", "mail"),
                    null)
    );

    private MailProviderRegistry() {
    }

    public static List<MailProvider> all() {
        return PROVIDERS;
    }

    public static Optional<MailProvider> byCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String c = code.trim().toUpperCase(Locale.ROOT);
        return PROVIDERS.stream().filter(p -> p.code().equals(c)).findFirst();
    }

    public static MailProvider custom() {
        return byCode(CUSTOM).orElseThrow();
    }

    /** Direct match on a public mailbox domain, e.g. "gmail.com" -> GMAIL. */
    public static Optional<MailProvider> byDomain(String domain) {
        if (domain == null) {
            return Optional.empty();
        }
        String d = domain.trim().toLowerCase(Locale.ROOT);
        return PROVIDERS.stream().filter(p -> p.domains().contains(d)).findFirst();
    }

    /** Match on the MX hostnames of a custom domain, e.g. "aspmx.l.google.com" -> GMAIL (Workspace). */
    public static Optional<MailProvider> byMx(List<String> mxHosts) {
        if (mxHosts == null) {
            return Optional.empty();
        }
        for (String mx : mxHosts) {
            String h = mx.toLowerCase(Locale.ROOT);
            for (MailProvider p : PROVIDERS) {
                if (p.mxPatterns().stream().anyMatch(h::contains)) {
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    /** Provider whose standard SMTP host this is, e.g. "smtp.gmail.com" -> GMAIL. */
    public static Optional<MailProvider> byHost(String host) {
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        String h = host.trim().toLowerCase(Locale.ROOT);
        return PROVIDERS.stream().filter(p -> p.smtpHost() != null && p.smtpHost().equals(h)).findFirst();
    }

    /** Domain part of an email address, lower-cased; empty string when there is none. */
    public static String domainOf(String email) {
        if (email == null) {
            return "";
        }
        int at = email.lastIndexOf('@');
        return at < 0 ? "" : email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    }
}
