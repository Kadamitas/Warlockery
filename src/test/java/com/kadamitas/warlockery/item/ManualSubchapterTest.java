package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.registry.ContentCatalog;
import java.util.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import com.google.gson.JsonParser;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class ManualSubchapterTest {
    @Test
    void everyManualSectionBelongsToExactlyOneOrderedChapter() {
        ManualProfile.profiles().forEach(profile -> {
            final List<String> grouped = profile.chapters().stream()
                .flatMap(chapter -> chapter.sections().stream())
                .toList();
            assertEquals(profile.sections(), grouped, profile.id());
            profile.sections().forEach(section ->
                assertTrue(profile.chapterFor(section).sections().contains(section), section));
        });
    }

    @Test
    void boundFetishesGiveEveryItemItsOwnSubchapter() {
        final ManualProfile profile = ManualProfile.find("ingredient_book_burning").orElseThrow();
        final ManualProfile.Chapter chapter = profile.chapters().stream()
            .filter(candidate -> candidate.id().equals("bound_fetishes"))
            .findFirst()
            .orElseThrow();

        assertEquals(13, chapter.sections().size());
        assertTrue(chapter.sections().stream().allMatch(section -> section.startsWith("fetish_")));
        assertEquals(5, chapter.sections().stream()
            .filter(section -> section.startsWith("fetish_dream_weaver_"))
            .count());
        assertTrue(chapter.sections().stream()
            .filter(section -> section.startsWith("fetish_dream_weaver_"))
            .map(section -> section.substring("fetish_".length()))
            .allMatch(ContentCatalog.ITEMS::contains));
        assertFalse(chapter.sections().contains("fetish_dream_weaver_restoration"));
        assertEquals(chapter.sections(), profile.sectionsInChapter(chapter.id(), profile.sections()));
        assertEquals(
            List.of("sympathetic_vials", "beast_speech"),
            profile.chapters().stream()
                .filter(candidate -> candidate.id().equals("binding_tools"))
                .findFirst()
                .orElseThrow()
                .sections()
        );
    }

    @Test
    void herbologyGivesEveryCultivatedAndWildPlantItsOwnSubchapter() {
        final ManualProfile profile = ManualProfile.find("ingredient_book_herbology").orElseThrow();
        final List<String> plantSections = profile.chapters().stream()
            .filter(chapter -> chapter.id().equals("cultivated_herbs") || chapter.id().equals("wild_plants"))
            .flatMap(chapter -> chapter.sections().stream())
            .toList();

        assertEquals(19, plantSections.size());
        assertTrue(plantSections.stream().allMatch(section -> section.startsWith("plant_")));
    }

    @Test
    void ritualAndBrewIndexesAreSplitIntoManageableChapters() {
        final ManualProfile rituals = ManualProfile.find("ingredient_book_circle_magic").orElseThrow();
        final ManualProfile brews = ManualProfile.find("cauldronbook").orElseThrow();

        assertTrue(rituals.chapters().size() >= 8);
        assertTrue(brews.chapters().size() >= 7);
        assertTrue(rituals.chapters().stream().allMatch(chapter -> chapter.sections().size() < 40));
        assertTrue(brews.chapters().stream().allMatch(chapter -> chapter.sections().size() < 50));
    }

    @Test
    void everyCompleteBookHasMultipleSearchableChapters() {
        ManualProfile.profiles().stream()
            .filter(profile -> !profile.id().equals("ingredient_vbook_page"))
            .forEach(profile -> assertTrue(profile.chapters().size() >= 2, profile.id()));
    }

    @Test
    void bothBiomeBooksIndexEveryBuiltInBiome() {
        for (final String id : List.of("ingredient_book_biomes", "bookbiomes2")) {
            final ManualProfile profile = ManualProfile.find(id).orElseThrow();
            final List<String> biomes = profile.sections().stream()
                .filter(section -> section.startsWith("biome_entry_"))
                .toList();
            assertEquals(66, biomes.size(), id);
            assertEquals(biomes.size(), Set.copyOf(biomes).size(), id);
            assertTrue(biomes.contains("biome_entry_pale_garden"), id);
            assertTrue(biomes.contains("biome_entry_sulfur_caves"), id);
            assertTrue(biomes.contains("biome_entry_end_barrens"), id);
        }
    }

    @Test
    void machineBooksIndexEveryPackagedRecipeInTheirSubject() throws IOException {
        assertEquals(
            recipes("oven_"),
            recipeSections(ManualProfile.find("ingredient_book_oven").orElseThrow())
        );
        assertEquals(
            recipes("distill_"),
            recipeSections(ManualProfile.find("ingredient_book_distilling").orElseThrow())
        );
    }

    @Test
    void subjectBooksLinkToTheirDetailedRitualRecords() {
        final ManualProfile conjuration = ManualProfile.find("ingredient_book_burning").orElseThrow();
        final ManualProfile infusions = ManualProfile.find("ingredient_book_infusions").orElseThrow();

        assertTrue(conjuration.sections().stream().filter(section -> section.startsWith("rite_summon_")).count() >= 15);
        assertTrue(conjuration.sections().contains("rite_blood_audience"));
        assertEquals(13, infusions.sections().stream().filter(section -> section.startsWith("rite_")).count());
    }

    @Test
    void conjurationBookIndexesAndSearchesTheSpiritWorldLessons() throws IOException {
        final ManualProfile conjuration = ManualProfile.find("ingredient_book_burning").orElseThrow();
        final ManualProfile.Chapter spiritWorld = conjuration.chapters().stream()
            .filter(chapter -> chapter.id().equals("spirit_world"))
            .findFirst()
            .orElseThrow();
        assertEquals(List.of(
            "spirit_world_entry",
            "spirit_world_laws",
            "spirit_world_harvest",
            "spirit_world_nightmares"
        ), spiritWorld.sections());

        final var translations = JsonParser.parseString(Files.readString(
            Path.of("src/main/resources/assets/warlockery/lang/en_us.json")
        )).getAsJsonObject();
        assertTrue(ManualProfile.search("sleeping body", key -> translations.has(key)
            ? translations.get(key).getAsString()
            : key).contains(conjuration));
    }

    private static Set<String> recipes(final String prefix) throws IOException {
        try (var paths = Files.list(Path.of("src/main/resources/data/warlockery/warlockery_machine"))) {
            return paths
                .map(path -> path.getFileName().toString())
                .filter(name -> name.startsWith(prefix) && name.endsWith(".json"))
                .map(name -> name.substring(0, name.length() - ".json".length()))
                .collect(Collectors.toSet());
        }
    }

    private static Set<String> recipeSections(final ManualProfile profile) {
        return profile.sections().stream()
            .filter(section -> section.startsWith("machine_recipe_"))
            .map(section -> section.substring("machine_recipe_".length()))
            .collect(Collectors.toSet());
    }
}
