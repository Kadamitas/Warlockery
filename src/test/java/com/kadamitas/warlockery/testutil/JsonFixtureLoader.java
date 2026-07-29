package com.kadamitas.warlockery.testutil;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class JsonFixtureLoader {
    private JsonFixtureLoader() {
    }

    public static <T> List<Fixture<T>> load(final Path directory, final Codec<T> codec) {
        try (var paths = Files.list(directory)) {
            return paths
                .filter(path -> path.toString().endsWith(".json"))
                .sorted(Comparator.comparing(Path::toString))
                .map(path -> new Fixture<>(id(path), path, read(path, codec)))
                .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to list " + directory, exception);
        }
    }

    private static String id(final Path path) {
        return path.getFileName().toString().replaceFirst("\\.json$", "");
    }

    private static <T> T read(final Path path, final Codec<T> codec) {
        try {
            return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(path)))
                .getOrThrow(message -> new IllegalArgumentException(path + ": " + message));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read " + path, exception);
        }
    }

    public record Fixture<T>(String id, Path path, T value) {
    }
}
