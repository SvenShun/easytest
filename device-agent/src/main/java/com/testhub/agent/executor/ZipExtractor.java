package com.testhub.agent.executor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Extracts a script bundle with zip-slip protection. */
public final class ZipExtractor {

    private static final int MAX_ENTRIES = 20_000;

    private ZipExtractor() {
    }

    /**
     * Extract {@code zipFile} below {@code targetDir}.
     *
     * @throws ZipSlipException when an entry would escape {@code targetDir}
     */
    public static void extract(Path zipFile, Path targetDir) throws IOException {
        Path targetRoot = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(targetRoot);

        try (InputStream fileIn = Files.newInputStream(zipFile);
                ZipInputStream zip = new ZipInputStream(fileIn)) {
            ZipEntry entry;
            int entries = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IOException("zip contains too many entries (> " + MAX_ENTRIES + ")");
                }
                String name = entry.getName().replace('\\', '/');
                Path resolved = targetRoot.resolve(name).normalize();
                if (!resolved.startsWith(targetRoot)) {
                    throw new ZipSlipException("zip entry escapes target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                    continue;
                }
                Path parent = resolved.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(zip, resolved, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** Raised when a zip entry escapes the extraction root (zip slip). */
    public static class ZipSlipException extends IOException {

        public ZipSlipException(String message) {
            super(message);
        }
    }
}
