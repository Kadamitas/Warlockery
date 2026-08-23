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
import java.util.ArrayList;
import java.util.List;
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

    @Test
    void everyPlannedLiveFixtureNamesBothPractitionersExactlyOnce() {
        final List<String> fixtures = List.of(
            "hedge_crone_warns_intruders_and_casts_contextual_hex",
            "hedge_crone_prepares_one_ward_and_releases_safely",
            "hedge_crone_save_reload_hazard_and_lifecycle_are_bounded",
            "circle_mage_recruits_follows_and_regenerates_owner",
            "circle_mages_study_and_defend_as_a_bounded_conclave",
            "circle_mage_save_reload_seer_and_work_are_bounded"
        );
        assertEquals(6, fixtures.size());
        final String gameTests = readText(MAIN_JAVA.resolve(Path.of(
            "com", "kadamitas", "warlockery", "entity", "CovenPractitionerGameTests.java")));
        final List<String> missing = new ArrayList<>();
        fixtures.forEach(fixture -> {
            if (!gameTests.contains(camel(fixture))) {
                missing.add(fixture);
            }
        });
        assertEquals(List.of(), missing, "each approved fixture has its exact live method");

        // The descriptors are written and point at the isolated environment. Registering the six
        // methods in ModGameTests is coordinator deferred, so GameTestInstanceContractTest is
        // knowingly red on exactly these six ids until that deferred edit lands.
        fixtures.forEach(fixture -> {
            final Path descriptor = RESOURCES.resolve(Path.of(
                "data", "warlockery", "test_instance", fixture + ".json"));
            assertTrue(Files.exists(descriptor), "missing descriptor for " + fixture);
            final JsonObject json = read(descriptor);
            assertEquals("minecraft:function", json.get("type").getAsString());
            assertEquals("warlockery:" + fixture, json.get("function").getAsString());
            final JsonObject environment = json.getAsJsonObject("environment");
            assertEquals("warlockery:isolated", environment.get("type").getAsString());
            assertEquals("warlockery:coven_practitioners_isolated",
                environment.get("delegate").getAsString());
            assertEquals("warlockery:empty32x32x32", json.get("structure").getAsString());
            assertEquals(400, json.get("max_ticks").getAsInt());
        });
    }

    @Test
    void theTargetAcquisitionPathRunsNoEntityQueryItDoesNotUse() {
        // Regression: the acquisition path queried a thirty-two-cube box every twenty ticks per
        // Mage and then discarded every result. The loop touched no candidate list, cast no ray,
        // and only corrupted the two counters the live fixtures budget-assert against. A query
        // whose result is never read is exactly the ceremony this family keeps producing, so the
        // acquisition body is pinned to contain no entity query at all.
        final String body = methodBody(
            readText(MAIN_JAVA.resolve(Path.of("com", "kadamitas", "warlockery", "entity",
                "CircleMageRuntime.java"))),
            "private static void acquireThreatWhenDue");
        assertFalse(body.contains("getEntitiesOfClass"),
            "acquisition is bounded by the motive count, not by a crowd traversal");
        assertFalse(body.contains("MAX_CANDIDATES_VISITED"),
            "a traversal budget with nothing to traverse is ceremony");
        assertTrue(body.contains("freshAttacker(mage)"), "the direct motive is preseeded");
        assertTrue(body.contains("TargetSource.OWNER"), "the owner motive is preseeded");
        assertTrue(body.contains("CircleMageRules.select("),
            "the pure motive ordering decides the target");
    }

    @Test
    void everyPeerAndFormationQueryStillDeclaresItsOwnBoundedRadius() {
        // Deleting the unused acquisition query must not silently delete the real ones.
        final String runtime = readText(MAIN_JAVA.resolve(Path.of(
            "com", "kadamitas", "warlockery", "entity", "CircleMageRuntime.java")));
        assertTrue(runtime.contains("CircleMageRules.PEER_RADIUS"),
            "the peer report query keeps its sixteen-block radius");
        assertTrue(runtime.contains("CircleMageRules.FORMATION_QUERY_RADIUS"),
            "the formation query keeps its sixteen-block radius");
        assertTrue(runtime.contains("CircleMageRules.CONCLAVE_RADIUS"),
            "the conclave query keeps its twelve-block radius");
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

    @Test
    void neitherPractitionerAddsANewSpawnPlacementOrNaturalSpawn() {
        final String registry = readText(MAIN_JAVA.resolve(
            Path.of("com", "kadamitas", "warlockery", "registry", "ModEntities.java")));
        final int naturalStart = registry.indexOf("NATURAL_SPAWN_IDS = Set.of(");
        final String naturalBlock = registry.substring(naturalStart, registry.indexOf(");", naturalStart));
        assertFalse(naturalBlock.contains("hedge_crone"));
        assertFalse(naturalBlock.contains("circle_mage"));
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
