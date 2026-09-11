package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.*;
import com.kadamitas.warlockery.item.ManualProfile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ManualOnboardingTest {
    @BeforeAll
    static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void starterBooksContainTheirOwnMachineAndAltarConstruction() {
        assertTrue(ManualProfile.find("ingredient_book_oven").orElseThrow().sections().containsAll(
            java.util.List.of("crafting_alchemical_oven", "crafting_ingredient_soft_clay_jar", "crafting_ingredient_clay_jar_from_smelting")));
        assertTrue(ManualProfile.find("ingredient_book_distilling").orElseThrow().sections().containsAll(
            java.util.List.of("crafting_distilleryidle", "crafting_altar", "power")));
    }

    @Test
    void everyRitualPicturesTheToolThatActuallyOpensIt() {
        var circles = ManualProfile.find("ingredient_book_circle_magic").orElseThrow();
        circles.sections().stream().filter(id -> id.startsWith("rite_")).forEach(id ->
            assertEquals("warlockery:arcane_focus", ManualArticleCatalog.article(circles, id).pictograms().getFirst().itemId(), id));
    }

    @Test
    void goldenChalkCarriesARealBookLinkToTheGypsumWorking() {
        var circles = ManualProfile.find("ingredient_book_circle_magic").orElseThrow();
        var body = ManualArticleCatalog.article(circles, "golden_chalk").body();
        var serialized = net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(
            com.mojang.serialization.JsonOps.INSTANCE, body).getOrThrow().toString();
        assertTrue(serialized.contains("warlockery:manual/ingredient_book_distilling/machine_recipe_distill_vitriol"));
    }

    @Test
    void workshopMachinesHaveObtainableRecipes() {
        assertNotNull(getClass().getResource("/data/warlockery/recipe/brazier.json"));
        assertNotNull(getClass().getResource("/data/warlockery/recipe/silvervat.json"));
    }

    @Test
    void ritualPagesExplainTheCastingTimeAndActualOutput() {
        var circles = ManualProfile.find("ingredient_book_circle_magic").orElseThrow();
        var body = ManualArticleCatalog.article(circles, "rite_infuse_brew_grave").body().getString();
        assertTrue(body.contains("manual.warlockery.ritual.cast_time"));
        assertTrue(body.contains("manual.warlockery.ritual.item_result"));
    }

    @Test
    void everyIndexedMachineRecipeLinksToItsSetupInstructions() {
        for (var profile : ManualProfile.profiles()) for (var section : profile.sections()) {
            if (!section.startsWith("machine_recipe_")) continue;
            var references = ManualBookLinks.references(profile, section);
            assertFalse(references.isEmpty(), profile.id() + "/" + section);
            var serialized = net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(
                com.mojang.serialization.JsonOps.INSTANCE,
                ManualArticleCatalog.article(profile, section).body()).getOrThrow().toString();
            for (var reference : references) assertTrue(serialized.contains(reference.id().toString()), section);
        }
    }

    @Test
    void brazierRecipeStatesHowToIgniteTheLoadedMachine() {
        var profile = ManualProfile.find("ingredient_book_burning").orElseThrow();
        var body = ManualArticleCatalog.article(profile, "machine_recipe_brazier_summon_spectre").body().getString();
        assertTrue(body.contains("manual.warlockery.machine_recipe.ignite"));
    }
}
