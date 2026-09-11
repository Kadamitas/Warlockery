package com.kadamitas.warlockery.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;

record ManualReadingPosition(String section, int page) {
    static ManualReadingPosition load(final Path file, final String manual, final List<String> sections) {
        final Properties preferences = read(file);
        final String section = preferences.getProperty(manual + ".section", sections.getFirst());
        if (!sections.contains(section)) {
            return new ManualReadingPosition(sections.getFirst(), 0);
        }
        int page = 0;
        try {
            page = Math.max(0, Integer.parseInt(preferences.getProperty(manual + ".page", "0")));
        } catch (NumberFormatException ignored) {
        }
        return new ManualReadingPosition(section, page);
    }

    static void save(final Path file, final String manual, final String section, final int page) {
        final Properties preferences = read(file);
        preferences.setProperty(manual + ".section", section);
        preferences.setProperty(manual + ".page", Integer.toString(Math.max(0, page)));
        Path temporary = null;
        try {
            final Path target = file.toAbsolutePath();
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), "manual-reading-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                preferences.store(writer, "Warlockery manual reading positions");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            System.getLogger(ManualReadingPosition.class.getName()).log(System.Logger.Level.WARNING,
                "Unable to save manual reading position", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static Properties read(final Path file) {
        final Properties preferences = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                preferences.load(reader);
            } catch (IOException | IllegalArgumentException ignored) {
                preferences.clear();
            }
        }
        return preferences;
    }
}
