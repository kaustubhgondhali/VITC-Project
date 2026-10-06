package com.vitc.security.upload;

import java.util.Locale;
import java.util.Set;

/**
 * PART 2B-2/7 — FILE UPLOAD SECURITY.
 *
 * <p>Hard deny-list of server-executable / script file types. This is a second gate on top of
 * the per-category allow-lists: an allow-list already rejects these, but the deny-list also
 * catches them inside double extensions (e.g. {@code invoice.php.pdf} or {@code shell.jsp;.png})
 * so a mis-configured allow-list can never make an executable upload reachable.</p>
 */
public final class DangerousFileTypes {

    private static final Set<String> BLOCKED = Set.of(
            // native / OS executables and scripts
            "exe", "dll", "com", "scr", "msi", "msp", "bat", "cmd", "ps1", "psm1", "vbs", "vbe",
            "js", "mjs", "cjs", "jse", "wsf", "wsh", "sh", "bash", "zsh", "ksh", "csh", "run",
            "bin", "app", "dmg", "pkg", "deb", "rpm", "so", "dylib", "elf", "reg", "lnk", "scf",
            // server-side executable code
            "php", "php3", "php4", "php5", "php7", "phtml", "phar", "jsp", "jspx", "jspf", "jsw",
            "jsv", "asp", "aspx", "ashx", "asmx", "cshtml", "cgi", "pl", "pm", "py", "pyc", "rb",
            "jar", "war", "ear", "class", "java", "cfm", "erb", "hbs", "twig", "ejs", "tpl",
            // markup that can execute in the browser from our own origin
            "html", "htm", "xhtml", "shtml", "xht", "svgz", "swf", "xml", "xsl", "xslt",
            // config / server control files
            "htaccess", "htpasswd", "ini", "conf", "config", "env", "cnf", "yml", "yaml");

    private DangerousFileTypes() {
    }

    public static boolean isBlockedExtension(String extension) {
        return extension != null && BLOCKED.contains(extension.toLowerCase(Locale.ROOT));
    }

    /**
     * True when ANY dot-separated segment of the file name is a blocked type — this is what stops
     * {@code payload.php.png}, {@code x.jsp.jpg} and friends.
     */
    public static boolean containsBlockedSegment(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String segment : lower.split("\\.")) {
            String cleaned = segment.trim();
            if (!cleaned.isEmpty() && BLOCKED.contains(cleaned)) {
                return true;
            }
        }
        return false;
    }
}
