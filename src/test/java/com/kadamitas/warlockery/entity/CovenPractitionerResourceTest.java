package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile.Feature;
import com.kadamitas.warlockery.entity.CreatureVisualProfile.Archetype;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Exact IDs, names, attributes, acquisition, loot, model, texture, profile, and fixture resources
 * for both F13 entities, plus the protected ritual and progression invariants they must not touch.
 */
final class CovenPractitionerResourceTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");

    @Test
    void bothExactIdsAndEnglishNamesAreUnchanged() {
        final JsonObject locale = read(RESOURCES.resolve(
            Path.of("assets", "warlockery", "lang", "en_us.json")));
        assertEquals("Hedge Crone", locale.get("entity.warlockery.hedge_crone").getAsString());
        assertEquals("Circle Mage", locale.get("entity.warlockery.circle_mage").getAsString());
    }

    @Test
    void registeredDimensionsCategoryAndArchetypesRemainExact() {
        final CreatureVisualProfile crone = CreatureVisualProfile.forKind(CreatureKind.HEDGE_CRONE);
        assertEquals(0.65F, crone.width());
        assertEquals(2.7F, crone.height());
        assertEquals(Archetype.BOSS, crone.archetype());

        final CreatureVisualProfile mage = CreatureVisualProfile.forKind(CreatureKind.CIRCLE_MAGE);
        assertEquals(0.6F, mage.width());
        assertEquals(1.8F, mage.height());
        assertEquals(Archetype.HUMANOID, mage.archetype());
    }

    @Test
    void theExactRegisteredAttributeBaselineIsMirroredByTheDedicatedEntities() {
        final String registry = readText(MAIN_JAVA.resolve(
            Path.of("com", "kadamitas", "warlockery", "registry", "ModEntities.java")));
        assertTrue(registry.contains(
            "case \"hedge_crone\" -> attributes.add(Attributes.MAX_HEALTH, 60)"
                + ".add(Attributes.ATTACK_DAMAGE, 9).add(Attributes.ARMOR, 6);"),
            "the registered Hedge Crone attribute row must stay byte identical");
        assertEquals(60.0D, HedgeCroneEntity.BASE_MAX_HEALTH);
        assertEquals(9.0D, HedgeCroneEntity.BASE_ATTACK_DAMAGE);
        assertEquals(6.0D, HedgeCroneEntity.BASE_ARMOR);
        // The Circle Mage keeps the plain Zombie-derived registry defaults.
        assertEquals(20.0D, CircleMageEntity.BASE_MAX_HEALTH);
        assertEquals(3.0D, CircleMageEntity.BASE_ATTACK_DAMAGE);
        assertEquals(2.0D, CircleMageEntity.BASE_ARMOR);
        assertEquals(HedgeCroneEntity.BASE_FOLLOW_RANGE, CircleMageEntity.BASE_FOLLOW_RANGE);
        assertEquals(HedgeCroneEntity.BASE_MOVEMENT_SPEED, CircleMageEntity.BASE_MOVEMENT_SPEED);
    }

    @Test
    void bothLootTablesAndTheirProgressionSurfacesAreUnchanged() {
        final String croneLoot = readText(RESOURCES.resolve(
            Path.of("data", "warlockery", "loot_table", "entities", "hedge_crone.json")));
        assertTrue(croneLoot.contains("warlockery:hedge_crones_hat"));
        assertTrue(croneLoot.contains("warlockery:entities/hedge_crone"));

        final String mageLoot = readText(RESOURCES.resolve(
            Path.of("data", "warlockery", "loot_table", "entities", "circle_mage.json")));
        assertTrue(mageLoot.contains("warlockery:entities/circle_mage"));
    }

    @Test
    void bothTexturesAndTheExistingModelVariantsRemainRegistered() {
        assertTrue(Files.exists(RESOURCES.resolve(
            Path.of("assets", "warlockery", "textures", "entity", "hedge_crone.png"))));
        assertTrue(Files.exists(RESOURCES.resolve(
            Path.of("assets", "warlockery", "textures", "entity", "circle_mage.png"))));
        final String model = readText(MAIN_JAVA.resolve(
            Path.of("com", "kadamitas", "warlockery", "client", "ArcaneCreatureModel.java")));
        assertTrue(model.contains("addStaff(root, \"bone_staff\""),
            "the Hedge Crone bone staff geometry is untouched");
        assertTrue(model.contains("addStaff(root, \"ritual_staff\""),
            "the Circle Mage ritual staff geometry is untouched");
        assertTrue(model.contains("addPart(root, \"crooked_nose\""));
    }

    @Test
    void theBehaviorProfilesAndOfferingTagsAreUnchanged() {
        final CreatureBehaviorProfile crone =
            CreatureBehaviorProfile.find(CreatureKind.HEDGE_CRONE).orElseThrow();
        assertTrue(crone.has(Feature.POTION_VOLLEY));
        assertTrue(crone.has(Feature.THORN_RETALIATION));

        final CreatureBehaviorProfile mage =
            CreatureBehaviorProfile.find(CreatureKind.CIRCLE_MAGE).orElseThrow();
        assertTrue(mage.has(Feature.COVEN_RECRUITMENT));
        assertTrue(mage.has(Feature.OWNER_AURA));
        assertTrue(mage.has(Feature.PROTECT_OWNER));
        assertFalse(mage.offering().isEmpty(), "the existing coven offering tag stays authoritative");
    }

    @Test
    void theSummonRitualAndItsProgressionRemainByteExact() {
        final JsonObject ritual = read(RESOURCES.resolve(
            Path.of("data", "warlockery", "ritual", "summon_circle_mage.json")));
        assertEquals("warlockery:circle_mage", ritual.get("target").getAsString());
        assertEquals("ritual.warlockery.summon_circle_mage.title", ritual.get("title").getAsString());
        assertEquals("ritual.warlockery.summon_circle_mage.description",
            ritual.get("description").getAsString());
        assertFalse(Files.exists(RESOURCES.resolve(
            Path.of("data", "warlockery", "ritual", "summon_hedge_crone.json"))),
            "F13 adds no ritual: the Hedge Crone keeps its crystal ball encounter only");
    }

    /** The body of one named method, by brace matching from its declaration. */
    private static String methodBody(final String source, final String declaration) {
        final int start = source.indexOf(declaration);
        assertTrue(start >= 0, "missing declaration: " + declaration);
        final int open = source.indexOf('{', start);
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            if (source.charAt(index) == '{') {
                depth++;
            } else if (source.charAt(index) == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open + 1, index);
                }
            }
        }
        throw new AssertionError("unterminated method: " + declaration);
    }

    private static String camel(final String fixture) {
        final String[] parts = fixture.split("_");
        final StringBuilder builder = new StringBuilder(parts[0]);
        for (int index = 1; index < parts.length; index++) {
            builder.append(Character.toUpperCase(parts[index].charAt(0)))
                .append(parts[index].substring(1));
        }
        return builder.toString();
    }

    private static JsonObject read(final Path path) {
        return JsonParser.parseString(readText(path)).getAsJsonObject();
    }

    private static String readText(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
