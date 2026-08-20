package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/** Guards the exact F04 resource, identity, and protected-contract surfaces without editing them. */
final class LycanPackResourceTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path DATA = RESOURCES.resolve(Path.of("data", "warlockery"));
    private static final Path ASSETS = RESOURCES.resolve(Path.of("assets", "warlockery"));
    private static final List<String> F04_FIXTURES = List.of(
        "lycan_variants_keep_identity_and_drop_zombie_lifecycle",
        "werewolf_hunt_assigns_roles_and_replaces_coordinator",
        "feral_lycan_tracks_prey_warns_bonds_and_avoids_settlement",
        "lycan_schedules_hazards_and_silver_counters_remain_distinct",
        "lycan_family_targets_respect_kin_players_and_other_families",
        "werewolf_trap_hunt_assault_and_infection_contracts_remain_exact",
        "lycan_actions_cancel_across_failure_save_and_reload",
        "lycan_population_work_stays_within_declared_caps"
    );

    @Test
    void harmWerewolvesDamageTypeIsRegisteredWithTheExactSemanticContract() {
        final JsonObject damageType = readJson(DATA.resolve(Path.of("damage_type", "harm_werewolves.json")));
        assertEquals("magic", damageType.get("message_id").getAsString(),
            "the typed source must keep the magic death message");
        assertEquals("when_caused_by_living_non_player", damageType.get("scaling").getAsString());
        assertEquals(0.0D, damageType.get("exhaustion").getAsDouble());
        assertEquals(3, damageType.entrySet().size(),
            "the typed source must not declare invulnerability, armor, or ward bypass fields");
    }

    @Test
    void magicalDamageTagAddsOnlyTheTypedSourceAndPreservesEveryExistingMember() {
        final JsonObject tag = readJson(DATA.resolve(Path.of("tags", "damage_type", "magical_damage.json")));
        assertFalse(tag.get("replace").getAsBoolean(), "the magical tag must never replace vanilla members");
        final Set<String> values = strings(tag.getAsJsonArray("values"));
        assertTrue(values.contains("warlockery:harm_werewolves"),
            "the typed source must remain magical for Absorb Magic compatibility");
        assertTrue(values.containsAll(Set.of(
            "minecraft:dragon_breath", "minecraft:indirect_magic", "minecraft:magic",
            "minecraft:sonic_boom", "minecraft:thorns", "minecraft:wither", "minecraft:wither_skull"
        )), "every pre-F04 magical member must be preserved");
        assertEquals(8, values.size(), "the sole F04 tag edit is the exact typed-source addition");
    }

    @Test
    void werewolfFamilyEntityTagAndLootRemainExact() {
        final JsonObject tag = readJson(DATA.resolve(Path.of("tags", "entity_type", "werewolves.json")));
        assertFalse(tag.get("replace").getAsBoolean());
        assertEquals(Set.of("warlockery:werewolf", "warlockery:feral_lycan", "warlockery:lycan_villager"),
            strings(tag.getAsJsonArray("values")),
            "the lycan family tag must keep its exact three members");
        assertTrue(Files.exists(DATA.resolve(Path.of("loot_table", "entities", "werewolf.json"))),
            "the existing Werewolf loot table must remain in place");
    }

    @Test
    void publicIdentityNamesTexturesAndSilverContractsRemainRegistered() throws IOException {
        final JsonObject lang = readJson(ASSETS.resolve(Path.of("lang", "en_us.json")));
        assertEquals("Werewolf", lang.get("entity.warlockery.werewolf").getAsString(),
            "the public Werewolf display name is immutable");
        assertEquals("Feral Lycan", lang.get("entity.warlockery.feral_lycan").getAsString(),
            "the public Feral Lycan display name is immutable");
        assertTrue(lang.has("item.warlockery.werewolf_spawn_egg")
                || lang.entrySet().stream().anyMatch(entry -> entry.getKey().contains("werewolf_spawn_egg")),
            "the Werewolf spawn egg locale entry must remain");
        try (var textures = Files.walk(ASSETS.resolve("textures"))) {
            assertTrue(textures.anyMatch(path -> path.getFileName().toString().equals("werewolf.png")),
                "the existing Werewolf texture bytes must remain in place");
        }
        final JsonObject silver = readJson(DATA.resolve(Path.of("tags", "item", "silver_weapons.json")));
        assertTrue(strings(silver.getAsJsonArray("values")).contains("warlockery:silversword"),
            "the silver weakness item contract must remain intact");
        final JsonObject projectiles = readJson(DATA.resolve(Path.of("tags", "item", "silver_projectiles.json")));
        assertFalse(strings(projectiles.getAsJsonArray("values")).isEmpty(),
            "the silver projectile contract used by armed Pillagers must remain intact");
    }

    private static Set<String> strings(final JsonArray array) {
        return StreamSupport.stream(array.spliterator(), false)
            .map(element -> element.getAsString())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static JsonObject readJson(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
