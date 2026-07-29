package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

final class ManualLibraryTest {
    private static final Path LANGUAGE = Path.of(
        "src/main/resources/assets/warlockery/lang/en_us.json"
    );

    @Test
    void libraryPublishesEveryUniqueProfileAsAnImmutableList() {
        assertEquals(12, ManualProfile.profiles().size());
        assertEquals(12, ManualProfile.ids().size());
        assertThrows(UnsupportedOperationException.class, () -> ManualProfile.profiles().clear());
    }

    @Test
    void blankSearchReturnsTheCompleteLibrary() {
        assertEquals(ManualProfile.profiles(), ManualProfile.search("  ", Function.identity()));
    }

    @Test
    void searchMatchesIdentifiersTitlesChapterTitlesAndBodyText() {
        final JsonObject translations = translations();
        final Function<String, String> resolver = key -> translations.has(key)
            ? translations.get(key).getAsString()
            : key;
        assertEquals(List.of("ingredient_book_circle_magic"), ids(ManualProfile.search("circle magic", resolver)));
        assertEquals(List.of("ingredient_book_herbology"), ids(ManualProfile.search("safe harvest", resolver)));
        assertEquals(List.of("vampirebook"), ids(ManualProfile.search("holy bolts", resolver)));
        assertTrue(ManualProfile.search("no such manual text", resolver).isEmpty());
    }

    @Test
    void chapterNavigationCyclesInBothDirections() {
        final ManualProfile profile = ManualProfile.find("cauldronbook").orElseThrow();
        assertEquals("delivery", profile.adjacentSection("custom_brews", 1));
        assertEquals("diagnostics", profile.adjacentSection("custom_brews", -1));
        assertEquals("custom_brews", profile.adjacentSection("diagnostics", 1));
    }

    @Test
    void everyManualAndChapterHasReadableTranslations() {
        final JsonObject translations = translations();
        ManualProfile.profiles().forEach(profile -> {
            assertTrue(translations.has(profile.translatedTitleKey()), profile.translatedTitleKey());
            profile.sections().forEach(section -> {
                assertTrue(translations.has(profile.translatedSectionTitleKey(section)),
                    profile.translatedSectionTitleKey(section));
                assertTrue(translations.has(profile.translatedSectionKey(section)),
                    profile.translatedSectionKey(section));
                assertFalse(translations.get(profile.translatedSectionKey(section)).getAsString().isBlank());
            });
        });
    }

    private static List<String> ids(final List<ManualProfile> profiles) {
        return profiles.stream().map(ManualProfile::id).toList();
    }

    private static JsonObject translations() {
        try {
            return JsonParser.parseString(Files.readString(LANGUAGE)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
