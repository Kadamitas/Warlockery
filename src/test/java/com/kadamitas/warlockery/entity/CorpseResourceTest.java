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
import java.util.Set;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.junit.jupiter.api.Test;

final class CorpseResourceTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");
    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "warlockery");
    private static final List<String> DESCRIPTORS = List.of(
        "corpse_raise_dead_identity_owner_and_acquisition_are_preserved",
        "corpse_scavenges_feeds_and_enters_dormancy_safely",
        "corpse_clutch_reacts_without_horde_or_conversion",
        "corpse_dual_owner_grave_command_and_loyalty_are_deterministic",
        "corpse_relationships_and_zombie_lifecycle_are_replaced",
        "corpse_save_reload_hazards_and_work_are_bounded"
    );

    @Test
    void corpseIsADedicatedFinalMonsterShellWithExactIdentity() {
        assertEquals(Monster.class, CorpseEntity.class.getSuperclass());
        assertTrue(java.lang.reflect.Modifier.isFinal(CorpseEntity.class.getModifiers()));
        assertFalse(Zombie.class.isAssignableFrom(CorpseEntity.class));
        assertFalse(ArcaneMob.class.isAssignableFrom(CorpseEntity.class));
        assertTrue(ArcaneCreature.class.isAssignableFrom(CorpseEntity.class));
        assertTrue(CreatureKind.CORPSE.isUndead());
        final CreatureVisualProfile visual = CreatureVisualProfile.forKind(CreatureKind.CORPSE);
        assertEquals(0.6F, visual.width());
        assertEquals(1.95F, visual.height());
        assertEquals(CreatureVisualProfile.Archetype.HUMANOID, visual.archetype());
    }

    @Test
    void corpseDeclaresTheExactBaseAttributesAndReinforcementZero() {
        assertEquals(20.0D, CorpseEntity.BASE_MAX_HEALTH);
        assertEquals(35.0D, CorpseEntity.BASE_FOLLOW_RANGE);
        assertEquals(0.23D, CorpseEntity.BASE_MOVEMENT_SPEED);
        assertEquals(3.0D, CorpseEntity.BASE_ATTACK_DAMAGE);
        assertEquals(2.0D, CorpseEntity.BASE_ARMOR);
        assertEquals(0.0D, CorpseEntity.BASE_REINFORCEMENT_CHANCE);
        assertEquals(Set.of(
            "minecraft:baby",
            "minecraft:random_spawn_bonus",
            "minecraft:zombie_random_spawn_bonus",
            "minecraft:leader_zombie_bonus",
            "minecraft:reinforcement_caller_charge",
            "minecraft:reinforcement_callee_charge"
        ), CorpseRules.LEGACY_MODIFIER_IDS);
    }

    @Test
    void corpseIsAbsentFromAlluringSkullTargetsWhileEveryOtherTargetRemains() {
        final JsonObject tag = JsonParser.parseString(
            read(DATA.resolve("tags/entity_type/alluring_skull_targets.json"))
        ).getAsJsonObject();
        assertFalse(tag.get("replace").getAsBoolean());
        final JsonArray values = tag.getAsJsonArray("values");
        final List<String> members = values.asList().stream()
            .map(value -> value.getAsString()).toList();
        assertFalse(members.contains("warlockery:corpse"));
        assertEquals(13, members.size());
        List.of("minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager",
            "minecraft:zombified_piglin", "minecraft:skeleton", "minecraft:stray", "minecraft:bogged",
            "minecraft:wither_skeleton", "minecraft:phantom", "minecraft:zoglin", "minecraft:wither",
            "#warlockery:spectral"
        ).forEach(member -> assertTrue(members.contains(member), member + " must remain a target"));
    }

    @Test
    void corpseLeavesSharedGraveScavengeAndF31RetiredTheRowOutright() {
        assertTrue(AmbientActivityProfile.forKind(CreatureKind.CORPSE).isEmpty(),
            "the Corpse is delegated to its dedicated runtime");
        assertTrue(AmbientActivityProfile.forKind(CreatureKind.LOUSE).isEmpty(),
            "F31 delegated the Parasytic Louse, which was GRAVE_SCAVENGE's last kind, so the row "
                + "and its ActivityType are gone rather than empty");
    }

    @Test
    void corpseIsolatedEnvironmentIsOneEmptyAllOfDefinition() {
        final JsonObject environment = JsonParser.parseString(
            read(DATA.resolve("test_environment/corpse_isolated.json"))
        ).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertEquals(0, environment.getAsJsonArray("definitions").size());
    }

    @Test
    void exactlySixCorpseDescriptorsUseTheIsolatedEnvironment() throws IOException {
        for (final String descriptor : DESCRIPTORS) {
            final JsonObject instance = JsonParser.parseString(
                read(DATA.resolve("test_instance/" + descriptor + ".json"))
            ).getAsJsonObject();
            assertEquals("minecraft:function", instance.get("type").getAsString());
            assertEquals("warlockery:" + descriptor, instance.get("function").getAsString());
            assertEquals("warlockery:corpse_isolated", instance.get("environment").getAsString());
        }
        try (var files = Files.list(DATA.resolve("test_instance"))) {
            final long corpseDescriptors = files
                .map(path -> path.getFileName().toString())
                .filter(name -> name.startsWith("corpse_"))
                .count();
            assertEquals(DESCRIPTORS.size(), corpseDescriptors,
                "exactly the six approved F17 descriptors exist");
        }
    }

    @Test
    void protectedCorpseNameLootAndAcquisitionResourcesRemainExact() {
        final String language = read(ASSETS.resolve("lang/en_us.json"));
        assertTrue(language.contains("\"entity.warlockery.corpse\": \"Body\""));
        assertTrue(language.contains("\"item.warlockery.corpse_spawn_egg\": \"Body Spawn Egg\""));
        final JsonObject loot = JsonParser.parseString(
            read(DATA.resolve("loot_table/entities/corpse.json"))
        ).getAsJsonObject();
        assertEquals(0, loot.getAsJsonArray("pools").size(), "species loot stays empty");
        assertEquals("warlockery:entities/corpse", loot.get("random_sequence").getAsString());
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
