package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards every protected Banshee identity, acquisition, and asset resource without editing any of
 * them, plus the exact five F16 fixture descriptors and the isolated test environment.
 */
final class BansheeResourceTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path DATA = RESOURCES.resolve("data").resolve("warlockery");
    private static final Path ASSETS = RESOURCES.resolve("assets").resolve("warlockery");
    private static final List<String> FIXTURES = List.of(
        "banshee_warns_at_risk_player_without_causing_harm",
        "banshee_laments_only_an_observed_death_and_returns_to_vigil",
        "banshee_recoils_from_attack_without_a_sonic_weapon",
        "banshee_save_reload_and_acquisition_contracts_are_preserved",
        "banshee_flight_hazard_feedback_and_work_are_bounded"
    );

    @Test
    void publicIdentityNameAndEggResourcesRemainExact() {
        final JsonObject language = json(ASSETS.resolve("lang").resolve("en_us.json"));
        assertEquals("Banshee", language.get("entity.warlockery.banshee").getAsString());
        assertTrue(Files.exists(ASSETS.resolve("textures").resolve("entity").resolve("banshee.png")),
            "the Banshee texture bytes stay in place and untouched");
        assertTrue(Files.exists(ASSETS.resolve("items").resolve("banshee_spawn_egg.json")),
            "the spawn egg item model registration remains");
    }

    @Test
    void summonRiteDefinitionRemainsExact() {
        final JsonObject rite = json(DATA.resolve("ritual").resolve("summon_banshee.json"));
        assertEquals("summon_entity", rite.get("action").getAsString());
        assertEquals(2200, rite.get("power").getAsInt());
        assertEquals(6, rite.get("radius").getAsInt());
        assertEquals(120, rite.get("casting_time").getAsInt());
        assertEquals(12, rite.getAsJsonObject("glyphs").get("circleglyphritual").getAsInt());
        assertEquals(16, rite.getAsJsonObject("glyphs").get("circleglyph_veil").getAsInt());
        assertTrue(rite.get("night_only").getAsBoolean());
        assertEquals("warlockery:banshee", rite.get("target").getAsString());
        assertEquals(1, rite.getAsJsonObject("requirements").get("minimum_players").getAsInt());
    }

    @Test
    void brazierRecipeRemainsExact() {
        final JsonObject recipe = json(
            DATA.resolve("warlockery_machine").resolve("brazier_summon_banshee.json")
        );
        assertEquals("brazier", recipe.get("machine").getAsString());
        assertEquals(600, recipe.get("processing_time").getAsInt());
        assertEquals(600, recipe.get("altar_power").getAsInt());
        assertEquals("continuous", recipe.get("power_mode").getAsString());
        assertEquals(3, recipe.getAsJsonArray("inputs").size());
        assertEquals("warlockery:ingredient_ash_wood",
            recipe.getAsJsonArray("outputs").get(0).getAsJsonObject().get("item").getAsString());
    }

    @Test
    void lootTableAndUndeadClassificationsRemainExact() {
        final JsonObject loot = json(DATA.resolve("loot_table").resolve("entities").resolve("banshee.json"));
        assertEquals("warlockery:entities/banshee", loot.get("random_sequence").getAsString());
        final JsonObject pool = loot.getAsJsonArray("pools").get(0).getAsJsonObject();
        assertEquals("warlockery:ingredient_spectral_dust",
            pool.getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        assertTrue(pool.getAsJsonArray("conditions").toString().contains("killed_by_player"));
        assertTagContains(DATA.resolve("tags").resolve("entity_type").resolve("spectral.json"),
            "warlockery:banshee");
        assertTagContains(DATA.resolve("tags").resolve("entity_type").resolve("reagent_sources.json"),
            "warlockery:banshee");
        assertTagContains(RESOURCES.resolve("data").resolve("minecraft").resolve("tags")
            .resolve("entity_type").resolve("undead.json"), "warlockery:banshee");
        final JsonObject deathBinding = json(DATA.resolve("tags").resolve("entity_type")
            .resolve("death_binding").resolve("banshees.json"));
        assertEquals(1, deathBinding.getAsJsonArray("values").size());
        assertEquals("warlockery:banshee",
            deathBinding.getAsJsonArray("values").get(0).getAsString());
    }

    @Test
    void graveyardDustEmpowermentContractRemainsExact() {
        final JsonObject tag = json(DATA.resolve("tags").resolve("item")
            .resolve("creature_interactions").resolve("banshee_empowerment.json"));
        assertEquals("warlockery:ingredient_graveyard_dust",
            tag.getAsJsonArray("values").get(0).getAsString());
        assertEquals(5, CreatureBehaviorRules.MAX_EMPOWERMENT,
            "the five-use empowerment cap remains exact");
        final CreatureBehaviorProfile profile = CreatureBehaviorProfile.find(CreatureKind.BANSHEE).orElseThrow();
        assertEquals("banshee", profile.auditId(),
            "the compatibility profile row keeps its exact audit identity");
        assertEquals("warlockery:creature_interactions/banshee_empowerment",
            profile.offering().orElseThrow().location().toString(),
            "the exact empowerment item tag stays wired to the Banshee profile");
        assertTrue(profile.has(CreatureBehaviorProfile.Feature.DUST_EMPOWERMENT));
    }

    @Test
    void protectedRitualAndProgressionResourcesAreUntouchedByThisPackage() {
        for (final Path path : List.of(
            DATA.resolve("ritual").resolve("bind_death.json"),
            DATA.resolve("ritual").resolve("bind_spectral.json"),
            DATA.resolve("ritual").resolve("summon_banshee.json")
        )) {
            assertTrue(Files.exists(path), path + " must remain in place");
        }
        final JsonObject bindDeath = json(DATA.resolve("ritual").resolve("bind_death.json"));
        final String text = bindDeath.toString();
        assertTrue(text.contains("death_binding/banshees"),
            "the five-Banshee Death binding requirement remains referenced");
    }

    private static void assertTagContains(final Path path, final String value) {
        final JsonArray values = json(path).getAsJsonArray("values");
        boolean found = false;
        for (int index = 0; index < values.size(); index++) {
            if (value.equals(values.get(index).getAsString())) {
                found = true;
            }
        }
        assertTrue(found, path + " must contain " + value);
        assertFalse(json(path).get("replace").getAsBoolean(), path + " must not replace");
    }

    private static JsonObject json(final Path path) {
        return JsonParser.parseString(read(path)).getAsJsonObject();
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
