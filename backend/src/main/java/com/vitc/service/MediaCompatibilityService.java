package com.vitc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.exception.BadRequestException;
import com.vitc.security.upload.UploadCategory;
import com.vitc.security.upload.AudioFormats;
import com.vitc.security.upload.VideoFormats;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.unit.DataSize;

/**
 * Probes uploads with ffprobe and normalizes media that is valid but not browser friendly.
 * Process arguments are supplied directly (no shell), and every temporary file is cleaned up.
 */
@Service
@RequiredArgsConstructor
public class MediaCompatibilityService {
    private final ObjectMapper objectMapper;

    @Value("${app.media.ffmpeg-enabled:true}")
    private boolean enabled;
    @Value("${app.media.ffmpeg-binary:ffmpeg}")
    private String ffmpegBinary;
    @Value("${app.media.ffprobe-binary:ffprobe}")
    private String ffprobeBinary;
    @Value("${app.media.transcode-timeout-seconds:900}")
    private long timeoutSeconds;
    @Value("${app.media.max-file-size:500MB}")
    private DataSize videoMaxSize;
    @Value("${app.media.audio-max-file-size:100MB}")
    private DataSize audioMaxSize;

    public NormalizedMedia normalize(MultipartFile input, UploadCategory category) {
        if (input == null || input.isEmpty()) throw new BadRequestException("Please select a media file.");
        long max = category == UploadCategory.VIDEO ? videoMaxSize.toBytes() : audioMaxSize.toBytes();
        if (input.getSize() > max) {
            throw new BadRequestException("Media file exceeds the configured upload limit. Maximum allowed size is "
                    + (max / (1024 * 1024)) + "MB.");
        }
        Path source = null;
        try {
            source = Files.createTempFile("vitc-media-", ".upload");
            try (var stream = input.getInputStream()) {
                Files.copy(stream, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            boolean wantsVideo = category == UploadCategory.VIDEO;
            if (!enabled) {
                if (isCompatibleWithoutProbe(input, category)) {
                    return new NormalizedMedia(input, null);
                }
                throw conversionUnavailable(category);
            }
            Probe probe;
            try {
                probe = probe(source);
            } catch (ExecutableUnavailableException unavailable) {
                if (isCompatibleWithoutProbe(input, category)) {
                    return new NormalizedMedia(input, null);
                }
                throw conversionUnavailable(category);
            }
            if (wantsVideo != probe.video() || (!wantsVideo && !probe.audio()) || (wantsVideo && probe.audioOnly())) {
                throw new BadRequestException(wantsVideo
                        ? "Invalid or unsupported media file."
                        : "Invalid or unsupported media file.");
            }
            String original = input.getOriginalFilename() == null ? "" : input.getOriginalFilename().toLowerCase(Locale.ROOT);
            boolean compatible = wantsVideo
                    ? probe.format().contains("mp4") && probe.videoCodec().equals("h264")
                        && (!probe.audio() || probe.audioCodec().equals("aac"))
                    : ((original.endsWith(".mp3") && probe.audioCodec().equals("mp3"))
                        || (original.endsWith(".m4a") && probe.audioCodec().equals("aac")));
            if (compatible) return new NormalizedMedia(input, null);

            if (!enabled) {
                throw conversionUnavailable(category);
            }

            Path output = Files.createTempFile("vitc-media-normalized-", wantsVideo ? ".mp4" : ".mp3");
            if (wantsVideo) {
                run(ffmpegBinary, "-y", "-i", source.toString(), "-map", "0:v:0", "-map", "0:a?",
                        "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                        "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", output.toString());
            } else {
                run(ffmpegBinary, "-y", "-i", source.toString(), "-vn", "-c:a", "libmp3lame",
                        "-b:a", "192k", output.toString());
            }
            String name = wantsVideo ? "normalized.mp4" : "normalized.mp3";
            String type = wantsVideo ? "video/mp4" : "audio/mpeg";
            return new NormalizedMedia(new TempMultipartFile("file", name, type, output), output);
        } catch (BadRequestException e) {
            throw e;
        } catch (ExecutableUnavailableException e) {
            throw conversionUnavailable(category);
        } catch (Exception e) {
            throw new BadRequestException("This media could not be converted to a browser-compatible format.");
        } finally {
            if (source != null) {
                try { Files.deleteIfExists(source); } catch (IOException ignored) { }
            }
        }
    }

    private Probe probe(Path source) throws Exception {
        String output = run(ffprobeBinary, "-v", "error", "-show_entries",
                "format=format_name:stream=codec_type,codec_name", "-of", "json", source.toString());
        var root = objectMapper.readTree(output);
        String format = root.path("format").path("format_name").asText("");
        boolean video = false, audio = false;
        String videoCodec = "", audioCodec = "";
        for (var stream : root.path("streams")) {
            String type = stream.path("codec_type").asText("");
            if ("video".equals(type)) { video = true; videoCodec = stream.path("codec_name").asText(""); }
            if ("audio".equals(type)) { audio = true; audioCodec = stream.path("codec_name").asText(""); }
        }
        return new Probe(format, video, audio, videoCodec, audioCodec);
    }

    private String run(String... args) throws Exception {
        Process process;
        try {
            process = new ProcessBuilder(args).redirectErrorStream(true).start();
        } catch (IOException unavailable) {
            throw new ExecutableUnavailableException(args.length == 0 ? "media tool" : args[0], unavailable);
        }
        boolean done = process.waitFor(Math.max(1, timeoutSeconds), java.util.concurrent.TimeUnit.SECONDS);
        if (!done) { process.destroyForcibly(); throw new IOException("media process timed out"); }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) throw new IOException(output);
        return output;
    }

    private boolean isCompatibleWithoutProbe(MultipartFile input, UploadCategory category) throws IOException {
        String original = input.getOriginalFilename() == null ? ""
                : input.getOriginalFilename().toLowerCase(Locale.ROOT);
        String extension = extensionOf(original);
        String declared = input.getContentType() == null ? "" : input.getContentType().toLowerCase(Locale.ROOT);
        int semi = declared.indexOf(';');
        if (semi >= 0) {
            declared = declared.substring(0, semi).trim();
        }
        byte[] head;
        try (var stream = input.getInputStream()) {
            head = stream.readNBytes(8192);
        }
        if (category == UploadCategory.VIDEO) {
            return (extension.equals("mp4") || extension.equals("m4v"))
                    && VideoFormats.isAcceptableDeclaredType(declared)
                    && VideoFormats.matchesSignature(extension, head);
        }
        return (extension.equals("mp3") || extension.equals("m4a"))
                && AudioFormats.isAcceptableDeclaredType(declared)
                && AudioFormats.matchesSignature(extension, head);
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).replaceAll("[^a-z0-9]", "");
    }

    private static BadRequestException conversionUnavailable(UploadCategory category) {
        return new BadRequestException("FFmpeg/FFprobe is required to convert this "
                + category.label() + " format, but the media tools are currently unavailable.");
    }

    public record NormalizedMedia(MultipartFile file, Path temporaryOutput) implements AutoCloseable {
        @Override public void close() {
            if (temporaryOutput != null) try { Files.deleteIfExists(temporaryOutput); } catch (IOException ignored) { }
        }
    }

    private record Probe(String format, boolean video, boolean audio, String videoCodec, String audioCodec) {
        boolean audioOnly() { return audio && !video; }
    }

    private static final class ExecutableUnavailableException extends IOException {
        private ExecutableUnavailableException(String executable, IOException cause) {
            super("Executable unavailable: " + executable, cause);
        }
    }
}