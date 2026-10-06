package com.vitc.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

/** Internal MultipartFile adapter for ffmpeg output; never exposed to clients. */
final class TempMultipartFile implements MultipartFile {
    private final String name, original, contentType;
    private final Path path;

    TempMultipartFile(String name, String original, String contentType, Path path) {
        this.name = name; this.original = original; this.contentType = contentType; this.path = path;
    }
    public String getName() { return name; }
    public String getOriginalFilename() { return original; }
    public String getContentType() { return contentType; }
    public boolean isEmpty() { try { return Files.size(path) == 0; } catch (IOException e) { return true; } }
    public long getSize() { try { return Files.size(path); } catch (IOException e) { return 0; } }
    public byte[] getBytes() throws IOException { return Files.readAllBytes(path); }
    public InputStream getInputStream() throws IOException { return Files.newInputStream(path); }
    public void transferTo(File destination) throws IOException { Files.copy(path, destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
}