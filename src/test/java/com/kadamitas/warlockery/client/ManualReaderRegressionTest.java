package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ManualReaderRegressionTest {
    @TempDir Path directory;

    @Test
    void readingPositionSurvivesReloadAndStaysSeparateForEachBook() throws Exception {
        final Class<?> type = helper("ManualReadingPosition");
        final var save = type.getDeclaredMethod("save", Path.class, String.class, String.class, int.class);
        final var load = type.getDeclaredMethod("load", Path.class, String.class, List.class);
        final Path file = directory.resolve("manual.properties");
        save.invoke(null, file, "codex", "rituals", 3);
        save.invoke(null, file, "herbalist", "plants", 1);
        final Object position = load.invoke(null, file, "codex", List.of("intro", "rituals"));
        assertEquals("rituals", position.getClass().getDeclaredMethod("section").invoke(position));
        assertEquals(3, position.getClass().getDeclaredMethod("page").invoke(position));
        final Object fallback = load.invoke(null, file, "codex", List.of("intro"));
        assertEquals("intro", fallback.getClass().getDeclaredMethod("section").invoke(fallback));
        assertEquals(0, fallback.getClass().getDeclaredMethod("page").invoke(fallback));
    }

    @Test
    void damagedReadingPreferencesFallBackToTheFirstPage() throws Exception {
        final Class<?> type = helper("ManualReadingPosition");
        final Path file = directory.resolve("damaged.properties");
        Files.writeString(file, "codex.section=rituals\ncodex.page=broken\n");
        final Object position = type.getDeclaredMethod("load", Path.class, String.class, List.class)
            .invoke(null, file, "codex", List.of("intro", "rituals"));
        assertEquals(0, position.getClass().getDeclaredMethod("page").invoke(position));
    }

    @Test
    void paginationReservesTheVisualPageAndKeepsEveryTextLine() throws Exception {
        final Class<?> type = helper("ManualPagination");
        final var paginate = type.getDeclaredMethod("pages", List.class, int.class, int.class, Predicate.class);
        final Predicate<String> blank = String::isBlank;
        final Object result = paginate.invoke(null, List.of("A", "", "B", "C", "", "D"), 0, 3, blank);
        assertEquals(List.of(List.of(), List.of("A", "", "B"), List.of("C", "", "D")), result);
        assertEquals(List.of(List.of("A", "B"), List.of("C")),
            paginate.invoke(null, List.of("", "A", "B", "", "C", ""), 2, 2, blank));
    }

    private static Class<?> helper(final String name) {
        return assertDoesNotThrow(() -> Class.forName("com.kadamitas.warlockery.client." + name),
            "The manual reader needs " + name);
    }
}
