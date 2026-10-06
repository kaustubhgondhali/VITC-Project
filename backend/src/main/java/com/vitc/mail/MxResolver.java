package com.vitc.mail;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Small read-only DNS helper for the Email Configuration screen.
 *
 * <p>{@link #lookup} returns a domain's MX records so a custom domain (e.g. admin@yourschool.in
 * hosted on Google Workspace) can be matched to the provider actually hosting its mail.
 * {@link #hasDkimRecord} checks whether {@code <selector>._domainkey.<domain>} publishes a DKIM
 * key, so the DKIM selector is only ever filled from a record that really exists.</p>
 *
 * <p>Uses the JDK's DNS JNDI provider - no extra library - with short timeouts so an unreachable
 * resolver cannot stall the admin screen. Any failure yields "not found".</p>
 */
@Slf4j
@Component
public class MxResolver {

    public List<String> lookup(String domain) {
        List<String> hosts = new ArrayList<>();
        for (String record : query(domain, "MX")) {
            // "10 aspmx.l.google.com." -> "aspmx.l.google.com"
            String[] parts = record.trim().split("\\s+");
            String host = parts[parts.length - 1];
            hosts.add(host.endsWith(".") ? host.substring(0, host.length() - 1) : host);
        }
        return hosts;
    }

    /** True when {@code selector._domainkey.domain} has a TXT record that is a DKIM public key. */
    public boolean hasDkimRecord(String domain, String selector) {
        if (domain == null || domain.isBlank() || selector == null || !selector.matches("^[A-Za-z0-9._-]{1,63}$")) {
            return false;
        }
        for (String txt : query(selector + "._domainkey." + domain, "TXT")) {
            String t = txt.replace("\"", "").replace(" ", "").toLowerCase(Locale.ROOT);
            if (t.contains("v=dkim1") || t.contains("p=")) {
                return true;
            }
        }
        return false;
    }

    private List<String> query(String name, String type) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        try {
            InitialDirContext ctx = new InitialDirContext(env);
            try {
                Attributes attrs = ctx.getAttributes(name.trim().toLowerCase(Locale.ROOT), new String[]{type});
                Attribute attr = attrs.get(type);
                List<String> values = new ArrayList<>();
                if (attr != null) {
                    for (int i = 0; i < attr.size(); i++) {
                        values.add(String.valueOf(attr.get(i)));
                    }
                }
                return values;
            } finally {
                ctx.close();
            }
        } catch (NamingException | RuntimeException e) {
            log.debug("DNS {} lookup for {} found nothing ({})", type, name, e.getClass().getSimpleName());
            return List.of();
        }
    }
}
