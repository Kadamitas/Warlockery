package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.brew.BrewKind;
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
    private static final List<String> LANGUAGES = List.of(
        "de_de", "en_us", "es_es", "fr_fr", "ja_jp", "ko_kr", "pl_pl", "pt_br", "ru_ru", "tr_tr",
        "zh_cn", "zh_tw"
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
        assertTrue(ids(ManualProfile.search("circle magic", resolver)).contains("ingredient_book_circle_magic"));
        assertEquals(List.of("ingredient_book_burning"), ids(ManualProfile.search("bound fetishes", resolver)));
        assertEquals(List.of("ingredient_book_herbology"), ids(ManualProfile.search("safe harvest", resolver)));
        assertEquals(List.of("vampirebook"), ids(ManualProfile.search("blood sense", resolver)));
        assertTrue(ManualProfile.search("no such manual text", resolver).isEmpty());
    }

    @Test
    void chapterNavigationCyclesInBothDirections() {
        final ManualProfile profile = ManualProfile.find("cauldronbook").orElseThrow();
        assertEquals("antidotes", profile.adjacentSection("custom_brews", 1));
        assertEquals("preamble", profile.adjacentSection("custom_brews", -1));
        assertEquals("brew_entry_heal", profile.adjacentSection("diagnostics", 1));
    }

    @Test
    void everyManualAndChapterHasReadableTranslations() {
        final JsonObject translations = translations();
        ManualProfile.profiles().forEach(profile -> {
            assertTrue(translations.has(profile.translatedTitleKey()), profile.translatedTitleKey());
            profile.chapters().forEach(chapter ->
                assertTrue(translations.has(chapter.titleKey()), chapter.titleKey()));
            profile.sections().forEach(section -> {
                final String sectionTitleKey = profile.translatedSectionTitleKey(section);
                assertTrue(
                    section.startsWith("biome_entry_")
                        ? sectionTitleKey.startsWith("biome.minecraft.") || translations.has(sectionTitleKey)
                        : translations.has(sectionTitleKey),
                    sectionTitleKey
                );
                assertTrue(translations.has(profile.translatedSectionKey(section)),
                    profile.translatedSectionKey(section));
                assertFalse(translations.get(profile.translatedSectionKey(section)).getAsString().isBlank());
            });
        });
    }

    @Test
    void everyPhysicalManualStartsWithALocalizedSummaryPreamble() {
        final List<ManualProfile> manuals = ManualProfile.profiles().stream()
            .filter(profile -> !profile.id().equals("ingredient_vbook_page"))
            .toList();

        manuals.forEach(profile -> {
            assertEquals("preamble", profile.sections().getFirst(), profile.id());
            assertEquals("preamble", profile.chapters().getFirst().sections().getFirst(), profile.id());
        });

        final JsonObject english = translations();
        assertEquals(manuals.size(), manuals.stream()
            .map(profile -> english.get(profile.translatedSectionKey("preamble")).getAsString())
            .distinct()
            .count());

        LANGUAGES.stream().map(ManualLibraryTest::translations).forEach(translations -> manuals.forEach(profile -> {
            final String bodyKey = profile.translatedSectionKey("preamble");
            final String titleKey = profile.translatedSectionTitleKey("preamble");
            assertTrue(translations.has(bodyKey), bodyKey);
            assertTrue(translations.has(titleKey), titleKey);
            assertFalse(translations.get(bodyKey).getAsString().isBlank(), bodyKey);
            assertFalse(translations.get(titleKey).getAsString().isBlank(), titleKey);
        }));
    }

    @Test
    void immortalPreambleExplainsTheWholeTornPageProgression() {
        final String preamble = translations().get("manual.warlockery.immortal.preamble").getAsString();
        assertTrue(preamble.contains("guide to becoming an immortal vampire"));
        assertTrue(preamble.contains("this same book"));
        assertTrue(preamble.contains("one at a time"));
        assertTrue(preamble.contains("Nine Torn Pages"));
    }

    @Test
    void biomeLessonsExplainTheBoundBookAndBothChunkRadiusPaths() {
        final JsonObject translations = translations();
        final String binding = translations.get("manual.warlockery.biomes_extended.biome_notes").getAsString();
        final String shifting = translations.get("manual.warlockery.biomes_extended.shifting_rite").getAsString();
        assertTrue(binding.contains("Crouch and use this book"));
        assertTrue(binding.contains("adds its name"));
        assertFalse(binding.contains("paper note"));
        assertTrue(shifting.contains("1, 3, 5, or 7"));
        assertTrue(shifting.contains("3, 5, 7, or 9"));
        assertTrue(shifting.contains("book is kept"));
        assertTrue(shifting.contains("Nether Star is consumed"));
    }

    @Test
    void circleMagicIndexesEveryDataDrivenRitual() throws IOException {
        final ManualProfile circles = ManualProfile.find("ingredient_book_circle_magic").orElseThrow();
        final long indexedRituals = circles.sections().stream().filter(section -> section.startsWith("rite_")).count();
        final long packagedRituals;
        try (var paths = Files.list(Path.of("src/main/resources/data/warlockery/ritual"))) {
            packagedRituals = paths.filter(path -> path.toString().endsWith(".json")).count();
        }
        assertEquals(packagedRituals, indexedRituals);
        assertEquals(108, indexedRituals);
    }

    @Test
    void cauldronCodexIndexesEveryBuiltInBrew() {
        final ManualProfile codex = ManualProfile.find("cauldronbook").orElseThrow();
        final long indexedBrews = codex.sections().stream().filter(section -> section.startsWith("brew_entry_")).count();
        assertEquals(BrewKind.builtIns().size(), indexedBrews);
        assertEquals(128, indexedBrews);
    }

    @Test
    void supernaturalProgressionsPublishOrderedInitiationAndLevelSubchapters() {
        final ManualProfile observations = ManualProfile.find("vampirebook").orElseThrow();
        assertEquals(
            java.util.stream.Stream.concat(
                java.util.stream.Stream.of("preamble", "nami", "blood_audience"),
                java.util.stream.IntStream.rangeClosed(1, 10).mapToObj(level -> "vampire_level_" + level)
            ).toList(),
            observations.sections()
        );
        assertEquals(List.of("introduction", "vampire_awakening", "vampire_trials", "vampire_ascendance"),
            observations.chapters().stream().map(ManualProfile.Chapter::id).toList());

        final ManualProfile circles = ManualProfile.find("ingredient_book_circle_magic").orElseThrow();
        final ManualProfile.Chapter lycanthropy = circles.chapters().stream()
            .filter(chapter -> chapter.id().equals("lycanthropy_trials"))
            .findFirst()
            .orElseThrow();
        assertEquals(
            java.util.stream.IntStream.rangeClosed(1, 10).mapToObj(level -> "werewolf_level_" + level).toList(),
            lycanthropy.sections()
        );
    }

    @Test
    void supernaturalSubchaptersUseInWorldProseAndStateEveryBloodLimit() {
        final JsonObject translations = translations();
        final List<String> bloodCaps = List.of(
            "750", "1,000", "1,250", "1,500", "1,750",
            "2,000", "2,250", "2,500", "2,750", "3,000"
        );
        for (int level = 1; level <= 10; level++) {
            final String vampire = translations.get(
                "manual.warlockery.immortal.vampire_level_" + level
            ).getAsString();
            assertFalse(vampire.startsWith("Trial:"), "Vampire level " + level);
            assertFalse(vampire.contains("Reward:"), "Vampire level " + level);
            assertTrue(vampire.contains(bloodCaps.get(level - 1)), "Vampire level " + level);

            final String werewolf = translations.get(
                "manual.warlockery.circles.werewolf_level_" + level
            ).getAsString();
            assertTrue(werewolf.startsWith("Trial:"), "Werewolf level " + level);
            assertTrue(werewolf.contains("Reward:"), "Werewolf level " + level);
        }
        assertTrue(translations.get("manual.warlockery.immortal.vampire_level_4").getAsString()
            .contains("first three Torn Pages"));
        assertTrue(translations.get("manual.warlockery.immortal.vampire_level_8").getAsString()
            .contains("four distinct villages"));
        final String finalTrial = translations.get("manual.warlockery.immortal.vampire_level_10").getAsString();
        assertTrue(finalTrial.contains("all nine Torn Pages"));
        assertTrue(finalTrial.contains("goblet"));
        assertTrue(finalTrial.contains("coffin"));
        assertTrue(translations.get("manual.warlockery.immortal.nami").getAsString().contains("marriage"));
        assertTrue(translations.get("manual.warlockery.immortal.blood_audience").getAsString()
            .contains("Naamah"));
    }

    private static List<String> ids(final List<ManualProfile> profiles) {
        return profiles.stream().map(ManualProfile::id).toList();
    }

    private static JsonObject translations() {
        return translations("en_us");
    }

    private static JsonObject translations(final String language) {
        try {
            return JsonParser.parseString(Files.readString(LANGUAGE.resolveSibling(language + ".json"))).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
