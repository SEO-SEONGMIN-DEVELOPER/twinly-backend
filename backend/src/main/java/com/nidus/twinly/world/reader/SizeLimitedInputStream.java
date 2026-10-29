package com.nidus.twinly.world.reader;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

final class SizeLimitedInputStream extends FilterInputStream {

    private final long maxBytes;
    private long readBytes;

    SizeLimitedInputStream(InputStream in, long maxBytes) {
        super(in);
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int value = super.read();
        if (value != -1) {
            count(1);
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = super.read(buffer, offset, length);
        if (read > 0) {
            count(read);
        }
        return read;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(n);
        count(skipped);
        return skipped;
    }

    private void count(long bytes) throws IOException {
        readBytes += bytes;
        if (readBytes > maxBytes) {
            throw new SizeLimitExceededException(maxBytes);
        }
    }
}
