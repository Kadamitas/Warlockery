package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.item.ManualProfile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ManualArticleCatalogTest {
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
}
