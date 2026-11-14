package vn.utbridora.auth.files;

import java.io.*;
import java.nio.channels.SeekableByteChannel;

/** Private binary storage port; callers never receive an object URL or filesystem path. */
public interface FileStorage {
    interface Staged extends AutoCloseable {
        long size();
        String sha256();
        byte[] prefix(int limit) throws IOException;
        InputStream open() throws IOException;
        @Override void close();
    }
    Staged stage(InputStream input,long limit);
    String promote(Staged staged);
    SeekableByteChannel open(String key,long size,String sha256);
    void delete(String key);
}
