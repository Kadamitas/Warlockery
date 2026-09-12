package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.brew.BrewMarkerKind;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ManualBrewBehaviorInstructionsTest {
    @Test
    void everyBuiltinBehaviorHasActionableEnglishInsteadOfItsFallbackLabel() throws Exception {
        final JsonObject english = english();
        for (var behavior : BrewKind.builtIns().stream().flatMap(kind -> kind.behaviors().stream()).distinct().toList()) {
            final String key = "manual.warlockery.brew.behavior." + behavior.id();
            assertTrue(english.has(key), key);
            final String text = english.get(key).getAsString();
            assertTrue(text.length() >= 80, key + " needs a target, action and outcome");
            assertTrue(text.startsWith("Throw "), key + " must explain how to deliver the working");
            assertFalse(text.equalsIgnoreCase(behavior.id().replace('_', ' ')), key);
            assertFalse(text.contains("#warlockery:"), key + " must use player-readable material names");
        }
    }

    @Test
    void delayedWorkingsNameTheirRealTargetAndTrigger() throws Exception {
        final JsonObject english = english();
        require(english, "apply_keep_inventory", "player", "dies", "respawn");
        require(english, "apply_keep_effects", "player", "dies", "respawn");
        require(english, "apply_reincarnate", "animal", "dies", "replacement");
        require(english, "apply_infection", "villager", "zombie villager", "stone");
        require(english, "apply_depths", "water", "air", "drowning");
        require(english, "apply_tint_skin", "glowing", "particles");
        require(english, "apply_sleeping", "Spirit World", "Icy Needle");
        require(english, "apply_cursed_leaping", "ground", "two seconds");
        require(english, "substitute_blocks", "dropped", "one", "block");
        for (String behavior : List.of("solidify_stone", "solidify_dirt", "solidify_sand", "solidify_sandstone", "solidify_erosion")) {
            require(english, behavior, "Hollow Tears");
        }
    }

    @Test
    void undeadHealingAndRegentSummoningNameTheirActualOutcomes() throws Exception {
        final JsonObject english = english();
        require(english, "buff_undead", "owned undead", "Strength II", "Resistance I", "60 seconds",
            "Undead Mending", "30 seconds", "one health point every 2.5 seconds");
        assertEquals("Undead Mending", english.get("effect.warlockery.undead_mending").getAsString());
        assertFalse(english.get("manual.warlockery.brew.behavior.buff_undead").getAsString().contains("Regeneration"));
        require(english, "summon_abyssal_regent", "Abyssal Regent", "hostile", "not bound", "Infernal Animus cannot bind");
        assertFalse(english.get("manual.warlockery.brew.behavior.summon_abyssal_regent").getAsString().contains("Emberhorn"));
    }

    @Test
    void longLivedEffectsStateTheCurrentSourceDuration() throws Exception {
        final JsonObject english = english();
        for (var marker : List.of(BrewMarkerKind.KEEP_INVENTORY, BrewMarkerKind.KEEP_EFFECTS, BrewMarkerKind.REINCARNATE,
            BrewMarkerKind.WEREWOLF_LOCK, BrewMarkerKind.VOLATILITY)) {
            require(english, "apply_" + marker.id(), marker.defaultDuration() / 1200 + " minutes");
        }
        require(english, "apply_absorb_magic", BrewMarkerKind.ABSORB_MAGIC.defaultDuration() / 1200 + " minutes");
        require(english, "apply_gas_immunity", BrewMarkerKind.BREW_GAS_IMMUNITY.defaultDuration() / 1200 + " minutes");
    }

    private static void require(final JsonObject english, final String behavior, final String... phrases) {
        final String key = "manual.warlockery.brew.behavior." + behavior;
        assertTrue(english.has(key), key);
        final String text = english.get(key).getAsString().toLowerCase(java.util.Locale.ROOT);
        for (String phrase : phrases) assertTrue(text.contains(phrase.toLowerCase(java.util.Locale.ROOT)), key + ": " + phrase);
    }

    private static JsonObject english() throws Exception {
        try (var stream = ManualBrewBehaviorInstructionsTest.class.getResourceAsStream("/assets/warlockery/lang/en_us.json")) {
            assertNotNull(stream);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
