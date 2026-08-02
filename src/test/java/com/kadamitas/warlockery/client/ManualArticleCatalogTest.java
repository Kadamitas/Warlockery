package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.item.ManualProfile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ManualArticleCatalogTest {
    private static final java.util.Set<String> BIOME_ARTICLE_CATEGORIES = java.util.Set.of(
        "manual.warlockery.biome.entry.green_lands",
        "manual.warlockery.biome.entry.waters_and_shores",
        "manual.warlockery.biome.entry.cold_lands_and_peaks",
        "manual.warlockery.biome.entry.dry_and_windswept_lands",
        "manual.warlockery.biome.entry.caves_and_strange_places",
        "manual.warlockery.biome.entry.nether_biomes",
        "manual.warlockery.biome.entry.end_biomes"
    );

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyRitualArticleLoadsItsEffectIngredientsConditionsAndDiagram() {
        final ManualProfile circles = ManualProfile.find("ingredient_book_circle_magic").orElseThrow();
        circles.sections().stream().filter(section -> section.startsWith("rite_")).forEach(section -> {
            final ManualArticleCatalog.Article article = ManualArticleCatalog.article(circles, section);
            assertFalse(article.body().getString().isBlank(), section);
            assertTrue(article.hasDiagram(), section);
            assertTrue(article.hasPictograms(), section);
            assertFalse(article.body().getString().contains("manual.warlockery.entry.glyphs"), section);
        });
    }

    @Test
    void everyBrewArticleLoadsItsEffectAndOrderedRecipe() {
        final ManualProfile codex = ManualProfile.find("cauldronbook").orElseThrow();
        codex.sections().stream().filter(section -> section.startsWith("brew_entry_")).forEach(section -> {
            final ManualArticleCatalog.Article article = ManualArticleCatalog.article(codex, section);
            final String text = article.body().getString();
            assertTrue(text.contains("manual.warlockery.entry.ingredients"), section);
            assertTrue(text.contains("manual.warlockery.entry.workings"), section);
            assertTrue(article.hasPictograms(), section);
        });
    }

    @Test
    void everyFetishAndPlantSubchapterHasAUsefulArticle() {
        final ManualProfile conjuration = ManualProfile.find("ingredient_book_burning").orElseThrow();
        final ManualProfile herbology = ManualProfile.find("ingredient_book_herbology").orElseThrow();

        java.util.stream.Stream.concat(
            conjuration.sections().stream().filter(section -> section.startsWith("fetish_")),
            herbology.sections().stream().filter(section -> section.startsWith("plant_"))
        ).forEach(section -> {
            final ManualProfile profile = section.startsWith("fetish_") ? conjuration : herbology;
            final ManualArticleCatalog.Article article = ManualArticleCatalog.article(profile, section);
            assertFalse(article.body().getString().isBlank(), section);
            assertTrue(article.hasPictograms(), section);
        });
    }

    @Test
    void immortalProgressionShowsEveryRequiredActionAsPictograms() {
        final ManualProfile observations = ManualProfile.find("vampirebook").orElseThrow();
        observations.sections().forEach(section ->
            assertTrue(ManualArticleCatalog.article(observations, section).hasPictograms(), section));
    }

    @Test
    void everyManualPreambleShowsItsBookAndImmortalAlsoShowsATornPage() {
        ManualProfile.profiles().stream()
            .filter(profile -> !profile.id().equals("ingredient_vbook_page"))
            .forEach(profile -> {
                final java.util.Set<String> pictures = ManualArticleCatalog.article(profile, "preamble")
                    .pictograms().stream()
                    .map(ManualArticleCatalog.Pictogram::itemId)
                    .collect(java.util.stream.Collectors.toSet());
                assertTrue(pictures.contains("warlockery:" + profile.id()), profile.id());
                if (profile.id().equals("vampirebook")) {
                    assertTrue(pictures.contains("warlockery:ingredient_vbook_page"));
                }
            });
    }

    @Test
    void beastSpeechLessonPicturesAPlayerCharmAndSheep() {
        final ManualProfile conjuration = ManualProfile.find("ingredient_book_burning").orElseThrow();
        assertEquals(
            java.util.Set.of(
                "minecraft:player_head",
                "warlockery:beast_speech_charm",
                "minecraft:sheep_spawn_egg"
            ),
            ManualArticleCatalog.article(conjuration, "beast_speech").pictograms().stream()
                .map(ManualArticleCatalog.Pictogram::itemId)
                .collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void entityRequirementsUseRecognizableCreaturePictograms() {
        final ManualProfile circles = ManualProfile.find("ingredient_book_circle_magic").orElseThrow();
        final java.util.Set<String> deathBinding = ManualArticleCatalog.article(circles, "rite_bind_death")
            .pictograms().stream()
            .map(ManualArticleCatalog.Pictogram::itemId)
            .collect(java.util.stream.Collectors.toSet());
        assertTrue(deathBinding.contains("warlockery:banshee_spawn_egg"));
        assertTrue(deathBinding.contains("warlockery:poltergeist_spawn_egg"));
        assertTrue(deathBinding.contains("warlockery:spectre_spawn_egg"));

        final java.util.Set<String> audience = ManualArticleCatalog.article(circles, "rite_blood_audience")
            .pictograms().stream()
            .map(ManualArticleCatalog.Pictogram::itemId)
            .collect(java.util.stream.Collectors.toSet());
        assertTrue(audience.contains("warlockery:nami_spawn_egg"));
    }

    @Test
    void climateRiteExplainsAndPicturesEveryRangeControl() {
        final ManualProfile circles = ManualProfile.find("ingredient_book_circle_magic").orElseThrow();
        final ManualArticleCatalog.Article climate = ManualArticleCatalog.article(circles, "rite_climate_change");
        final java.util.Map<String, Integer> pictures = climate.pictograms().stream()
            .collect(java.util.stream.Collectors.toMap(
                ManualArticleCatalog.Pictogram::itemId,
                ManualArticleCatalog.Pictogram::count
            ));

        assertTrue(climate.body().getString().contains("manual.warlockery.ritual.climate_change.guide"));
        assertEquals(1, pictures.get("warlockery:ingredient_book_biomes"));
        assertEquals(1, pictures.get("warlockery:ingredient_seer_stone"));
        assertEquals(5, pictures.get("minecraft:player_head"));
        assertEquals(3, pictures.get("minecraft:nether_star"));
    }

    @Test
    void generatedRitualAndMachineTextContainsNoEmbeddedEnglishInstructions() {
        ManualProfile.profiles().forEach(profile -> profile.sections().stream()
            .filter(section -> section.startsWith("rite_") || section.startsWith("machine_recipe_"))
            .map(section -> ManualArticleCatalog.article(profile, section).body().getString())
            .forEach(text -> java.util.List.of(
                "Produces ", "Tends for ", "Requires fuel", " (kept)", " participants"
            ).forEach(fragment -> assertFalse(text.contains(fragment), fragment + " in " + text))));
    }

    @Test
    void everyBiomeAndMachineRecipeHasAGeneratedArticle() {
        ManualProfile.profiles().forEach(profile -> profile.sections().stream()
            .filter(section -> section.startsWith("biome_entry_") || section.startsWith("machine_recipe_"))
            .forEach(section -> {
                final String text = ManualArticleCatalog.article(profile, section).body().getString();
                assertFalse(text.isBlank(), section);
                if (section.startsWith("machine_recipe_")) {
                    assertTrue(text.contains("manual.warlockery.entry.ingredients"), section);
                    assertTrue(text.contains("manual.warlockery.entry.workings"), section);
                }
            }));
    }

    @Test
    void everyListedBiomeUsesASpecificLandGuideWhileUnknownBiomesUseTheFallback() {
        final ManualProfile biomes = ManualProfile.find("bookbiomes2").orElseThrow();
        biomes.sections().stream()
            .filter(section -> section.startsWith("biome_entry_"))
            .forEach(section -> {
                final String key = biomes.translatedSectionKey(section);
                assertTrue(BIOME_ARTICLE_CATEGORIES.contains(key), section + " -> " + key);
                assertTrue(ManualArticleCatalog.article(biomes, section).body().getString().contains(key), section);
            });
        assertEquals(
            "manual.warlockery.biome.entry",
            ManualProfile.translatedBiomeEntryKey("another_mod_moon_garden")
        );
        assertEquals("biome.warlockery.sulfur_caves", biomes.translatedSectionTitleKey("biome_entry_sulfur_caves"));
        assertEquals(
            "manual.warlockery.biome.entry.green_lands",
            ManualProfile.translatedBiomeEntryKey("cherry_grove")
        );
        assertEquals(
            "manual.warlockery.biome.entry.waters_and_shores",
            ManualProfile.translatedBiomeEntryKey("mangrove_swamp")
        );
        for (final String coldWater : java.util.List.of("frozen_ocean", "frozen_river", "snowy_beach")) {
            assertEquals(
                "manual.warlockery.biome.entry.cold_lands_and_peaks",
                ManualProfile.translatedBiomeEntryKey(coldWater),
                coldWater
            );
        }
    }
}
