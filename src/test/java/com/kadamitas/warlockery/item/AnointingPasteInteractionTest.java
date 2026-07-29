package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.kadamitas.warlockery.item.AnointingPasteRules.Diagnostic;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class AnointingPasteInteractionTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");
    private static final Path LANG = Path.of(
        "src", "main", "resources", "assets", "warlockery", "lang", "en_us.json"
    );

    @TestFactory
    Stream<DynamicContainer> anointingPasteHasFailureDiagnosticAndSuccessCoverage() {
        return Stream.of(DynamicContainer.dynamicContainer("anointing_paste", Stream.of(
            DynamicTest.dynamicTest("failure", () -> {
                assertEquals(Diagnostic.NOT_ANOINTABLE, AnointingPasteRules.diagnostic(false, false));
                assertEquals(Diagnostic.ALREADY_ANOINTED, AnointingPasteRules.diagnostic(true, true));
            }),
            DynamicTest.dynamicTest("diagnostic", () -> {
                final String language = read(LANG);
                assertTrue(language.contains("message.warlockery.anointing_paste.not_anointable"));
                assertTrue(language.contains("message.warlockery.anointing_paste.already_anointed"));
                assertTrue(language.contains("message.warlockery.anointing_paste.success"));
                assertTrue(UtilityDeviceItemFactory.supports("ingredient_annointing_paste"));
            }),
            DynamicTest.dynamicTest("success", () -> {
                assertEquals(Diagnostic.READY, AnointingPasteRules.diagnostic(true, false));
                assertTagContains("block/anointable_cauldrons", "minecraft:water_cauldron");
                assertTrue(read(DATA.resolve("recipe/ingredient_annointing_paste.json"))
                    .contains("warlockery:ingredient_annointing_paste"));
            })
        )));
    }

    private static void assertTagContains(final String relative, final String expected) {
        final String json = read(DATA.resolve("tags/" + relative + ".json"));
        JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("values");
        assertTrue(json.contains(expected));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
