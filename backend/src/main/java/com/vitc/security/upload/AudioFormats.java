package com.vitc.security.upload;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Single source of truth for accepted audio extensions, MIME types and magic bytes. */
public final class AudioFormats {
    private static final Map<String, Set<String>> MIME = new LinkedHashMap<>();
    private static final Map<String, String> CANONICAL = new LinkedHashMap<>();

    static {
        put("mp3", "audio/mpeg", "audio/mpeg", "audio/mp3", "application/octet-stream");
        put("wav", "audio/wav", "audio/wav", "audio/x-wav", "audio/wave", "application/octet-stream");
        put("aac", "audio/aac", "audio/aac", "audio/x-aac", "application/octet-stream");
        put("m4a", "audio/mp4", "audio/mp4", "audio/x-m4a", "application/octet-stream");
        put("ogg", "audio/ogg", "audio/ogg", "application/ogg", "application/octet-stream");
        put("flac", "audio/flac", "audio/flac", "audio/x-flac", "application/octet-stream");
        put("wma", "audio/x-ms-wma", "audio/x-ms-wma", "audio/wma", "application/octet-stream");
        put("opus", "audio/opus", "audio/opus", "application/ogg", "audio/ogg", "application/octet-stream");
    }

    private AudioFormats() {}

    private static void put(String ext, String canonical, String... types) {
        CANONICAL.put(ext, canonical);
        MIME.put(ext, new LinkedHashSet<>(java.util.List.of(types)));
    }

    public static Set<String> allowedExtensions() { return MIME.keySet(); }

    public static boolean isAudioExtension(String extension) {
        return extension != null && MIME.containsKey(extension.toLowerCase(Locale.ROOT));
    }

    public static Set<String> allowedContentTypes() {
        Set<String> all = new LinkedHashSet<>();
        MIME.values().forEach(all::addAll);
        all.add("binary/octet-stream");
        return all;
    }

    public static boolean isAcceptableDeclaredType(String declared) {
        if (declared == null || declared.isBlank()) return true;
        String value = declared.toLowerCase(Locale.ROOT).trim();
        int semi = value.indexOf(';');
        if (semi >= 0) value = value.substring(0, semi).trim();
        return value.startsWith("audio/") || allowedContentTypes().contains(value);
    }

    public static String contentTypeFor(String extension) {
        return CANONICAL.getOrDefault(extension == null ? "" : extension.toLowerCase(Locale.ROOT), "audio/mpeg");
    }

    public static String allowedHint() {
        return "MP3, WAV, AAC, M4A, OGG, FLAC, WMA or OPUS";
    }

    public static boolean matchesSignature(String extension, byte[] head) {
        if (head == null || head.length < 4) return false;
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "mp3" -> starts(head, 0x49, 0x44, 0x33) || starts(head, 0xFF, 0xFB)
                    || starts(head, 0xFF, 0xF3) || starts(head, 0xFF, 0xF2);
            case "wav" -> ascii(head, 0, "RIFF") && ascii(head, 8, "WAVE");
            case "m4a" -> ascii(head, 4, "ftyp");
            case "ogg", "opus" -> ascii(head, 0, "OggS");
            case "flac" -> ascii(head, 0, "fLaC");
            case "wma" -> starts(head, 0x30, 0x26, 0xB2, 0x75, 0x8E, 0x66, 0xCF, 0x11);
            case "aac" -> starts(head, 0xFF, 0xF1) || starts(head, 0xFF, 0xF9);
            default -> false;
        };
    }

    private static boolean starts(byte[] data, int... signature) {
        if (data.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if ((data[i] & 0xFF) != signature[i]) return false;
        return true;
    }

    private static boolean ascii(byte[] data, int offset, String expected) {
        byte[] bytes = expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (data.length < offset + bytes.length) return false;
        for (int i = 0; i < bytes.length; i++) if (data[offset + i] != bytes[i]) return false;
        return true;
    }
}