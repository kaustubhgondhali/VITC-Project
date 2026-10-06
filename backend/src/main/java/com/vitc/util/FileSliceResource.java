package com.vitc.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.AbstractResource;

/** A lazily opened, bounded view of a file for constant-memory HTTP range responses. */
public final class FileSliceResource extends AbstractResource {

    private final Path path;
    private final long position;
    private final long length;

    public FileSliceResource(Path path, long position, long length) {
        this.path = path;
        this.position = position;
        this.length = length;
    }

    @Override
    public String getDescription() {
        return "File slice [" + path + ", " + position + ", " + length + "]";
    }

    @Override
    public String getFilename() {
        return path.getFileName().toString();
    }

    @Override
    public long contentLength() {
        return length;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            input.skipNBytes(position);
            return new BoundedInputStream(input, length);
        } catch (IOException exception) {
            input.close();
            throw exception;
        }
    }

    private static final class BoundedInputStream extends FilterInputStream {
        private long remaining;

        private BoundedInputStream(InputStream input, long remaining) {
            super(input);
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) {
                return -1;
            }
            int value = super.read();
            if (value != -1) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int requestedLength) throws IOException {
            if (remaining == 0) {
                return -1;
            }
            int allowedLength = (int) Math.min(requestedLength, remaining);
            int count = super.read(buffer, offset, allowedLength);
            if (count != -1) {
                remaining -= count;
            }
            return count;
        }
    }
}