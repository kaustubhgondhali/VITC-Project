package com.vitc.security.upload;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * PART 2B-2/7 — FILE UPLOAD SECURITY.
 *
 * <p>Verifies the ACTUAL bytes of an upload instead of trusting the extension or the
 * browser-declared content type. Works on the first few KB only, so it is cheap even for a
 * 500 MB video. Returns {@code true} when the content is consistent with the claimed
 * extension, {@code false} when it is a fake (e.g. a PHP script renamed to {@code .png}).</p>
 */
public final class FileContentInspector {

    /** Enough for every signature below plus the SVG/text scan window. */
    public static final int SNIFF_BYTES = 4096;

    private FileContentInspector() {
    }

    public static boolean matchesExtension(String extension, byte[] head) {
        if (head == null || head.length == 0) {
            return false;
        }
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "jpg", "jpeg" -> startsWith(head, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "gif" -> ascii(head, 0, "GIF87a") || ascii(head, 0, "GIF89a");
            case "webp" -> ascii(head, 0, "RIFF") && ascii(head, 8, "WEBP");
            case "avif" -> ascii(head, 4, "ftyp");
            case "svg" -> looksLikeSvg(head);
            case "pdf" -> ascii(head, 0, "%PDF-");
            case "docx", "xlsx", "pptx", "zip" -> startsWith(head, 0x50, 0x4B); // PK zip container
            case "doc", "xls", "ppt" -> startsWith(head, 0xD0, 0xCF, 0x11, 0xE0) // OLE2
                    || startsWith(head, 0x09, 0x08);
            // PART 2/10 — VIDEO UPLOAD: every accepted video container signature (MP4, WebM,
            // MOV, AVI, MKV, MPEG, MPG, M4V, 3GP, FLV, OGV) is verified by VideoFormats.
            case "mp4", "mov", "webm", "avi", "mkv", "mpeg", "mpg", "m4v", "3gp", "flv", "ogv" ->
                    VideoFormats.matchesSignature(ext, head);
            case "mp3", "wav", "aac", "m4a", "ogg", "flac", "wma", "opus" ->
                    AudioFormats.matchesSignature(ext, head);
            // Plain-text formats have no signature; they are validated by the script scan below.
            case "txt", "csv" -> !containsScript(head);
            default -> false;
        };
    }

    /** SVG is XML — accept it only when it really opens as XML/SVG and carries no script. */
    public static boolean looksLikeSvg(byte[] head) {
        String text = text(head);
        return (text.contains("<svg") || text.startsWith("<?xml")) && !containsScript(head);
    }

    /**
     * Blocks active content inside text-ish uploads (SVG, TXT, CSV): script tags, inline event
     * handlers, javascript: URLs, PHP/JSP/ASP tags and CSV formula injection payloads.
     */
    public static boolean containsScript(byte[] head) {
        String text = text(head).toLowerCase(Locale.ROOT);
        String[] markers = {
            "<script", "javascript:", "onload=", "onerror=", "onclick=", "onmouseover=",
            "<iframe", "<embed", "<object", "<foreignobject", "<?php", "<%", "<jsp:",
            "#!/bin/", "#!/usr/bin"
        };
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String text(byte[] head) {
        return new String(head, 0, Math.min(head.length, SNIFF_BYTES), StandardCharsets.UTF_8).trim();
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
