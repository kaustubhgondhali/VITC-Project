package com.vitc.security.upload;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * PART 2/10 — VIDEO UPLOAD.
 *
 * <p>Single source of truth for every video container VITC accepts on the Teacher Admin lesson
 * video upload (and the Success Story video upload, which shares the same validator):</p>
 *
 * <pre>MP4, WebM, MOV, AVI, MKV, MPEG, MPG, M4V, 3GP, FLV, OGV</pre>
 *
 * <p>Three independent facts are kept per extension:</p>
 * <ul>
 *   <li>the extension itself (used only as a hint / for the stored file name),</li>
 *   <li>the MIME types browsers are known to declare for it (advisory only — browsers are
 *       inconsistent and often send {@code application/octet-stream} or nothing at all for
 *       MKV/FLV/OGV/3GP, so a declared type is never the reason a valid video is rejected),</li>
 *   <li>the real container signature ("magic bytes"), which is the authoritative check.</li>
 * </ul>
 *
 * <p>Nothing here trusts the file name: {@link #matchesSignature(String, byte[])} inspects the
 * actual bytes, and a file whose bytes are not a recognised video container is rejected even when
 * its extension and declared MIME type look perfect.</p>
 */
public final class VideoFormats {

    /** Extension -> browser-declared MIME types commonly seen for that container. */
    private static final Map<String, Set<String>> MIME_BY_EXTENSION = new LinkedHashMap<>();

    /** Extension -> canonical content type used when serving the file back. */
    private static final Map<String, String> CANONICAL_MIME = new LinkedHashMap<>();

    static {
        put("mp4", "video/mp4", List.of("video/mp4", "application/mp4", "video/x-mp4"));
        put("webm", "video/webm", List.of("video/webm", "audio/webm"));
        put("mov", "video/quicktime", List.of("video/quicktime", "video/mov", "video/x-quicktime"));
        put("avi", "video/x-msvideo", List.of("video/x-msvideo", "video/avi", "video/msvideo", "video/x-avi"));
        put("mkv", "video/x-matroska", List.of("video/x-matroska", "video/matroska", "application/x-matroska"));
        put("mpeg", "video/mpeg", List.of("video/mpeg", "video/x-mpeg", "video/mpg"));
        put("mpg", "video/mpeg", List.of("video/mpeg", "video/x-mpeg", "video/mpg"));
        put("m4v", "video/x-m4v", List.of("video/x-m4v", "video/mp4", "video/m4v"));
        put("3gp", "video/3gpp", List.of("video/3gpp", "video/3gp", "audio/3gpp"));
        put("flv", "video/x-flv", List.of("video/x-flv", "video/flv", "application/x-flv"));
        put("ogv", "video/ogg", List.of("video/ogg", "application/ogg", "video/x-ogg"));
    }

    private VideoFormats() {
    }

    private static void put(String ext, String canonical, List<String> mimes) {
        CANONICAL_MIME.put(ext, canonical);
        MIME_BY_EXTENSION.put(ext, new LinkedHashSet<>(mimes));
    }

    /** All accepted video extensions, lower-case, without a leading dot. */
    public static Set<String> allowedExtensions() {
        return MIME_BY_EXTENSION.keySet();
    }

    /**
     * Declared content types tolerated for a video upload. Deliberately wide: it includes the
     * generic types browsers fall back to, because the authoritative check is the container
     * signature — a valid MKV must never be rejected just because Chrome sent
     * {@code application/octet-stream}. Anything not video-ish at all is still rejected here,
     * and everything is rejected again by the signature check if the bytes are not a video.
     */
    public static Set<String> allowedContentTypes() {
        Set<String> all = new LinkedHashSet<>();
        MIME_BY_EXTENSION.values().forEach(all::addAll);
        all.add("application/octet-stream");
        all.add("binary/octet-stream");
        return all;
    }

    /** {@code true} when the declared MIME type is acceptable for a video upload. */
    public static boolean isAcceptableDeclaredType(String declared) {
        if (declared == null || declared.isBlank()) {
            // Browsers legitimately send no type for MKV/FLV/OGV/3GP — signature decides.
            return true;
        }
        String d = declared.toLowerCase(Locale.ROOT).trim();
        int semi = d.indexOf(';');
        if (semi > -1) {
            d = d.substring(0, semi).trim();
        }
        return d.startsWith("video/") || allowedContentTypes().contains(d);
    }

    /** Canonical content type to store/serve for an accepted extension. */
    public static String contentTypeFor(String extension) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return CANONICAL_MIME.getOrDefault(ext, "video/mp4");
    }

    /** Canonical content type from a file name (used by the student streaming endpoint). */
    public static String contentTypeForFileName(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return contentTypeFor(dot < 0 ? "" : name.substring(dot + 1));
    }

    /** Human-readable hint for rejection messages. Never leaks server internals. */
    public static String allowedHint() {
        return "MP4, WebM, MOV, AVI, MKV, MPEG, MPG, M4V, 3GP, FLV or OGV";
    }

    /**
     * Authoritative content check: do the first bytes really look like the claimed container?
     * A generic "is this any known video container" fallback is intentionally NOT used — the
     * extension must agree with the bytes, so a WebM renamed to {@code .mp4} is rejected.
     */
    public static boolean matchesSignature(String extension, byte[] head) {
        if (head == null || head.length < 8) {
            return false;
        }
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "mp4", "m4v", "3gp" -> isIsoBmff(head);
            case "mov" -> isIsoBmff(head) || isQuickTimeAtom(head);
            case "webm", "mkv" -> startsWith(head, 0x1A, 0x45, 0xDF, 0xA3);
            case "avi" -> ascii(head, 0, "RIFF") && ascii(head, 8, "AVI ");
            case "mpeg", "mpg" -> isMpegProgramOrTransportStream(head);
            case "flv" -> ascii(head, 0, "FLV") && (head[3] & 0xFF) <= 0x05;
            case "ogv" -> ascii(head, 0, "OggS");
            default -> false;
        };
    }

    /** {@code true} when the extension is one of the accepted video containers. */
    public static boolean isVideoExtension(String extension) {
        return extension != null && MIME_BY_EXTENSION.containsKey(extension.toLowerCase(Locale.ROOT));
    }

    /* ------------------------------------------------------------------ signatures */

    /** ISO Base Media File Format (MP4 / M4V / 3GP / modern MOV): "ftyp" box at offset 4. */
    private static boolean isIsoBmff(byte[] head) {
        return ascii(head, 4, "ftyp");
    }

    /** Legacy QuickTime files can start with another top-level atom instead of "ftyp". */
    private static boolean isQuickTimeAtom(byte[] head) {
        for (String atom : List.of("moov", "mdat", "wide", "free", "skip", "pnot")) {
            if (ascii(head, 4, atom)) {
                return true;
            }
        }
        return false;
    }

    /** MPEG-1/2 program stream, video sequence header, or MPEG-TS (0x47 sync bytes). */
    private static boolean isMpegProgramOrTransportStream(byte[] head) {
        if (startsWith(head, 0x00, 0x00, 0x01, 0xBA) || startsWith(head, 0x00, 0x00, 0x01, 0xB3)
                || startsWith(head, 0x00, 0x00, 0x01, 0xB0) || startsWith(head, 0x00, 0x00, 0x01, 0xE0)) {
            return true;
        }
        // MPEG-TS: 188-byte packets each starting with 0x47.
        return (head[0] & 0xFF) == 0x47 && head.length > 188 && (head[188] & 0xFF) == 0x47;
    }

    private static boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] data, int offset, String expected) {
        byte[] want = expected.getBytes(StandardCharsets.US_ASCII);
        if (data.length < offset + want.length) {
            return false;
        }
        for (int i = 0; i < want.length; i++) {
            if (data[offset + i] != want[i]) {
                return false;
            }
        }
        return true;
    }
}
