package com.testhub.agent.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipSafetyTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsZipSlipEntry() throws IOException {
        Path zip = tempDir.resolve("evil.zip");
        writeZip(zip, "../escaped.txt", "pwned");

        Path extractDir = tempDir.resolve("extract");
        ZipExtractor.ZipSlipException error = assertThrows(ZipExtractor.ZipSlipException.class,
                () -> ZipExtractor.extract(zip, extractDir));
        assertTrue(error.getMessage().contains("escapes target directory"));
        assertFalse(Files.exists(tempDir.resolve("escaped.txt")));
    }

    @Test
    void rejectsNestedZipSlipEntry() throws IOException {
        Path zip = tempDir.resolve("nested-evil.zip");
        writeZip(zip, "sub/../../escaped2.txt", "pwned");

        Path extractDir = tempDir.resolve("extract2");
        assertThrows(ZipExtractor.ZipSlipException.class,
                () -> ZipExtractor.extract(zip, extractDir));
        assertFalse(Files.exists(tempDir.resolve("escaped2.txt")));
    }

    @Test
    void extractsValidBundle() throws IOException {
        Path zip = tempDir.resolve("valid.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            addEntry(out, "run.sh", "echo hello\n");
            addEntry(out, "sub/TEST-login.xml", "<testsuite/>");
        }

        Path extractDir = tempDir.resolve("valid-extract");
        ZipExtractor.extract(zip, extractDir);

        assertTrue(Files.isRegularFile(extractDir.resolve("run.sh")));
        assertEquals("echo hello\n",
                Files.readString(extractDir.resolve("run.sh"), StandardCharsets.UTF_8));
        assertTrue(Files.isRegularFile(extractDir.resolve("sub/TEST-login.xml")));
    }

    private void writeZip(Path zip, String entryName, String content) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            addEntry(out, entryName, content);
        }
    }

    private void addEntry(ZipOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
