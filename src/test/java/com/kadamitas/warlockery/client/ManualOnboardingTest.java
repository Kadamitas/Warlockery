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
    void exceptionalMachineRequirementsAppearInBothRecipeAndBrewGuides() {
        var brew = ManualProfile.find("cauldronbook").orElseThrow();
        for (String id : java.util.List.of("bodega", "cursed_leaping", "frogs_tongue")) {
            assertTrue(ManualArticleCatalog.article(brew, "brew_entry_" + id).body().getString()
                .contains("manual.warlockery.machine_setup." + id), id);
            assertTrue(ManualArticleCatalog.article(brew, "machine_recipe_kettle_brew_" + id).body().getString()
                .contains("manual.warlockery.machine_setup." + id), id);
        }
        assertTrue(ManualArticleCatalog.article(brew, "machine_recipe_cauldron_flowing_spirit").body().getString()
            .contains("manual.warlockery.machine_setup.spirit_world"));
    }

    @Test
    void brewRecipesNameTheirActualKettleWorkstation() {
        var brew = ManualProfile.find("cauldronbook").orElseThrow();
        assertTrue(ManualArticleCatalog.article(brew, "brew_entry_bodega").body().getString()
            .contains("manual.warlockery.brew.kettle_recipe"));
    }

    @Test
    void hellRiftGuideExplainsItsOngoingPowerDrain() {
        var circles = ManualProfile.find("ingredient_book_circle_magic").orElseThrow();
        assertTrue(ManualArticleCatalog.article(circles, "rite_hell_on_earth").body().getString()
            .contains("manual.warlockery.ritual.setup.hell_on_earth"));
    }

    @Test
    void everyBrewKeepsItsRealMachinePreparationAndSetupLink() {
        var book = ManualProfile.find("cauldronbook").orElseThrow();
        for (String section : book.sections()) {
            if (!section.startsWith("brew_entry_")) continue;
            var article = ManualArticleCatalog.article(book, section);
            var body = article.body().getString();
            for (String key : java.util.List.of("manual.warlockery.machine_recipe.heat",
                "manual.warlockery.machine_recipe.produces", "manual.warlockery.machine_recipe.processing_time",
                "manual.warlockery.machine_recipe.fluid")) assertTrue(body.contains(key), section + ": " + key);
            var serialized = net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(
                com.mojang.serialization.JsonOps.INSTANCE, article.body()).getOrThrow().toString();
            assertTrue(serialized.contains("warlockery:manual/cauldronbook/machines"), section);
        }
    }

    @Test
    void exceptionalPreparationLinksToActualBindingAndDreamInstructions() {
        var book = ManualProfile.find("cauldronbook").orElseThrow();
        for (var entry : java.util.Map.of("brew_entry_bodega", "ingredient_book_circle_magic/rite_bind_familiar",
            "machine_recipe_cauldron_flowing_spirit", "ingredient_book_burning/spirit_world_entry").entrySet()) {
            var serialized = net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(
                com.mojang.serialization.JsonOps.INSTANCE, ManualArticleCatalog.article(book, entry.getKey()).body())
                .getOrThrow().toString();
            assertTrue(serialized.contains("warlockery:manual/" + entry.getValue()), entry.getKey());
        }
        var burning = ManualProfile.find("ingredient_book_burning").orElseThrow();
        assertTrue(ManualArticleCatalog.article(burning, "machine_recipe_brazier_drain_growth").body().getString()
            .contains("manual.warlockery.machine_setup.drain_growth"));
    }

    @Test
    void customBrewGuideExampleComposesFromItsPackagedComponents() throws Exception {
        var components = new java.util.ArrayList<com.kadamitas.warlockery.brew.custom.CustomBrewComposer.Ingredient>();
        for (String id : java.util.List.of("capacity_diamond", "effect/regeneration", "container_bottle")) {
            var definition = com.kadamitas.warlockery.brew.custom.CustomBrewComponentDefinition.CODEC.parse(
                com.mojang.serialization.JsonOps.INSTANCE, json("/data/warlockery/custom_brew_component/" + id + ".json"))
                .getOrThrow();
            components.add(com.kadamitas.warlockery.brew.custom.CustomBrewComposer.Ingredient.of("warlockery:" + id, definition));
        }
        var state = com.kadamitas.warlockery.brew.custom.CustomBrewComposer.compose(components,
            com.kadamitas.warlockery.brew.custom.CustomBrewComposer.Conditions.ready(1000));
        assertTrue(state.ready(), state.toString());
        var formula = state.formula().orElseThrow();
        assertEquals(2, formula.capacity());
        assertEquals(2, formula.capacityCost());
        assertEquals(1000, formula.altarPower());
        assertEquals(com.kadamitas.warlockery.brew.custom.CustomBrewDelivery.DRINKABLE, formula.delivery());
        assertFalse(formula.effects().isEmpty());
        assertFalse(com.kadamitas.warlockery.brew.custom.CustomBrewComposer.compose(components,
            com.kadamitas.warlockery.brew.custom.CustomBrewComposer.Conditions.ready(999)).ready());
        var guide = json("/assets/warlockery/lang/en_us.json").get("manual.warlockery.codex.custom_brews").getAsString();
        for (String phrase : java.util.List.of("Diamond", "Brew of Regeneration", "Glass Bottle", "1,000", "250 mB"))
            assertTrue(guide.contains(phrase), phrase);
        var order = guide.substring(guide.indexOf("Order:"), guide.indexOf("\n\n", guide.indexOf("Order:")));
        int previous = -1;
        for (String role : java.util.List.of("capacity", "power", "duration", "modifiers", "extent", "lingering", "delivery", "effects", "container")) {
            int position = order.indexOf(role);
            assertTrue(position > previous, role);
            previous = position;
        }
    }

    @Test
    void extendedBurnAndRiftUpkeepAreNotPresentedAsFixedTotalCosts() throws Exception {
        var english = json("/assets/warlockery/lang/en_us.json");
        var rift = english.get("manual.warlockery.ritual.setup.hell_on_earth").getAsString();
        assertEquals(5000, json("/data/warlockery/ritual/hell_on_earth.json").get("power").getAsInt());
        assertTrue(rift.contains("5,000"));
        assertTrue(rift.contains(com.kadamitas.warlockery.ritual.HellRiftRules.POWER_PER_SECOND + " altar power per second"));
        var drain = english.get("manual.warlockery.machine_setup.drain_growth").getAsString();
        for (String phrase : java.util.List.of("partly grown", "wounded undead", "40 seconds", "60 seconds"))
            assertTrue(drain.contains(phrase), phrase);
    }

    @Test
    void enchantedEquipmentHasNamedSearchableUsagePagesAndItemPictures() throws Exception {
        var english = json("/assets/warlockery/lang/en_us.json");
        var profile = ManualProfile.find("ingredient_book_infusions").orElseThrow();
        java.util.function.Function<String, String> translate = key ->
            english.has(key) ? english.get(key).getAsString() : key;
        for (String id : java.util.List.of("earmuffs", "seepingshoes", "barkbelt", "twisting_band", "iceslippers",
            "forgewardens_girdle", "stonebrokers_quiver", "deathscowl", "deathsrobe", "deathsfeet", "deathshand")) {
            assertEquals(1, profile.sections().stream().filter(id::equals).count(), id);
            assertTrue(english.has(profile.translatedSectionTitleKey(id)), id);
            assertTrue(english.has(profile.translatedSectionKey(id)), id);
            var body = english.get(profile.translatedSectionKey(id)).getAsString();
            assertTrue(body.contains("Wear ") || body.contains("Hold "), id + " must explain the control");
            assertTrue(body.contains("\n\n"), id + " must divide use from its conditions");
            assertEquals("warlockery:" + id, ManualArticleCatalog.article(profile, id).pictograms().getFirst().itemId(), id);
            var title = english.get(profile.translatedSectionTitleKey(id)).getAsString();
            assertTrue(ManualProfile.search(title, translate).stream().anyMatch(found -> found.id().equals(profile.id())), id);
        }
    }

    @Test
    void deathEquipmentSeparatesOrdinaryFootwearFromTheCompleteDisguiseAction() throws Exception {
        var english = json("/assets/warlockery/lang/en_us.json");
        var profile = ManualProfile.find("ingredient_book_infusions").orElseThrow();
        for (String id : java.util.List.of("deathscowl", "deathsrobe", "deathsfeet", "deathshand"))
            assertEquals("death_disguise", profile.chapterFor(id).id(), id);
        var hand = english.get(profile.translatedSectionKey("deathshand")).getAsString();
        for (String phrase : java.util.List.of("Death's Hood", "Death's Robe", "Death's Feet", "use", "hunger", "15%"))
            assertTrue(hand.contains(phrase), phrase);
        var feet = english.get(profile.translatedSectionKey("deathsfeet")).getAsString();
        assertTrue(feet.contains("Frost Walker II"));
        assertTrue(feet.contains("Fire Protection IV"));
        assertTrue(feet.contains("Hand of Death"));
        assertFalse(feet.contains("activate the boots"));
    }

    @Test
    void banishmentInstructionsWarnAboutLiveDemonsAndFriendlyTargets() throws Exception {
        var english = json("/assets/warlockery/lang/en_us.json");
        for (String id : java.util.List.of("banish_demon", "banish_demon_portable")) {
            var body = english.get("ritual.warlockery." + id + ".description").getAsString();
            for (String phrase : java.util.List.of("Contain hostile demons", "do not pause the world",
                "Potion of Fire Resistance", "friendly or bound demons"))
                assertTrue(body.contains(phrase), id + ": " + phrase);
        }
    }

    @Test
    void infusedSoaringInstructionsExplainDrinkingAndBroomSteering() throws Exception {
        var body = json("/assets/warlockery/lang/en_us.json")
            .get("ritual.warlockery.infuse_brew_soaring.description").getAsString();
        for (String phrase : java.util.List.of("Drink", "enchanted broom", "steering", "two hours",
            "normal controls", "empty glass bottle"))
            assertTrue(body.contains(phrase), phrase);
        assertFalse(body.contains("maximum speed"));
        assertFalse(body.contains("free flight"));
    }

    private static com.google.gson.JsonObject json(String path) throws Exception {
        try (var stream = ManualOnboardingTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            try (var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    @Test
    void everyCraftingSectionLoadsItsPackagedRecipe() {
        for (var profile : ManualProfile.profiles()) for (var section : profile.sections()) {
            if (section.startsWith("crafting_")) {
                assertDoesNotThrow(() -> ManualArticleCatalog.article(profile, section), profile.id() + "/" + section);
            }
        }
    }

    @Test
    void specialtyUtilityItemsHaveSearchableNamedInstructionsAndPictures() throws Exception {
        var english = json("/assets/warlockery/lang/en_us.json");
        var sectionsByBook = java.util.Map.of(
            "ingredient_book_circle_magic", java.util.List.of(
                "ingredient_waystone", "ingredient_waystone_bound", "ingredient_waystone_creature_bound"),
            "ingredient_book_burning", java.util.List.of(
                "doll", "earth_guard_doll", "water_guard_doll", "hunger_guard_doll", "fire_guard_doll",
                "tool_mending_doll", "death_guard_doll", "hex_guard_doll", "hexing_doll", "blood_link_doll",
                "doll_guard", "armor_mending_doll", "ingredient_bat_ball", "spectralstone",
                "ingredient_subdued_spirit", "ingredient_subdued_spirit_village", "hornofthehunt",
                "ingredient_fool_skull", "ingredient_necro_stone"),
            "ingredient_book_herbology", java.util.List.of(
                "boline", "ingredient_icy_needle", "ingredient_wolfsbane")
        );
        for (var entry : sectionsByBook.entrySet()) {
            var profile = ManualProfile.find(entry.getKey()).orElseThrow();
            for (var section : entry.getValue()) {
                assertTrue(profile.sections().contains(section), entry.getKey() + "/" + section);
                assertTrue(english.has(profile.translatedSectionKey(section)), profile.translatedSectionKey(section));
                assertTrue(english.has(profile.translatedSectionTitleKey(section)), profile.translatedSectionTitleKey(section));
                var article = ManualArticleCatalog.article(profile, section);
                assertEquals("warlockery:" + section, article.pictograms().getFirst().itemId(), section);
                assertFalse(english.get(profile.translatedSectionKey(section)).getAsString().isBlank(), section);
            }
        }
        java.util.function.Function<String, String> translate = key ->
            english.has(key) ? english.get(key).getAsString() : key;
        for (var expected : java.util.Map.of(
            "Hexing Doll", "ingredient_book_burning",
            "Blooded Waystone", "ingredient_book_circle_magic",
            "Icy Needle", "ingredient_book_herbology"
        ).entrySet()) {
            assertTrue(ManualProfile.search(expected.getKey(), translate).stream()
                .anyMatch(profile -> profile.id().equals(expected.getValue())), expected.toString());
        }
    }

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
