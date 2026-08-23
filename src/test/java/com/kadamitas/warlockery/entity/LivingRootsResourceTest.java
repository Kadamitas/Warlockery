package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class LivingRootsResourceTest {
    private static final Path ROOT = Path.of("src", "main");
    private static final List<String> FIXTURES = List.of(
        "mandrake_extraction_wail_and_resettle_are_bounded",
        "mandrake_disturbance_requires_fresh_attribution_and_sight",
        "dreamroot_threshold_dream_requires_rooted_ground",
        "dreamroot_bulb_population_and_mutation_stay_capped",
        "living_roots_hazard_escape_and_cancellation_are_deterministic",
        "living_roots_save_reload_and_zombie_lifecycle_are_replaced"
    );

    @Test void exactDedicatedClassesAndPublicVisualContractsStayFixed() throws IOException {
        assertEquals(net.minecraft.world.entity.monster.Monster.class, MandrakeEntity.class.getSuperclass());
        assertEquals(net.minecraft.world.entity.monster.Monster.class, DreamrootEntity.class.getSuperclass());
        assertEquals(0.55F, CreatureVisualProfile.forKind(ArcaneCreature.CreatureKind.MANDRAKE).width());
        assertEquals(0.81F, CreatureVisualProfile.forKind(ArcaneCreature.CreatureKind.MANDRAKE).height());
        assertEquals(0.9F, CreatureVisualProfile.forKind(ArcaneCreature.CreatureKind.DREAMROOT).width());
        assertEquals(1.62F, CreatureVisualProfile.forKind(ArcaneCreature.CreatureKind.DREAMROOT).height());
        for (String entity : List.of("MandrakeEntity.java", "DreamrootEntity.java")) {
            assertTrue(read(ROOT.resolve("java/com/kadamitas/warlockery/entity/" + entity))
                .contains("Attributes.SPAWN_REINFORCEMENTS_CHANCE,0"));
        }
    }

    @Test void acquisitionMutationAndProgressionFilesRemainPresentAndUnalteredInMeaning() throws IOException {
        final String harvest = read(ROOT.resolve("java/com/kadamitas/warlockery/block/MandrakeHarvestRules.java"));
        assertTrue(harvest.contains("DAY_AWAKENING_CHANCE = 0.75F"));
        assertTrue(harvest.contains("NIGHT_AWAKENING_CHANCE = 0.25F"));
        final String crop = read(ROOT.resolve("java/com/kadamitas/warlockery/block/WarlockeryCropBlock.java"));
        assertTrue(crop.contains("causeFoodExhaustion(0.005F)"));
        assertTrue(crop.contains("serverLevel.isDarkOutside()"));
        assertTrue(Files.exists(ROOT.resolve("resources/data/warlockery/ritual/summon_witch.json")));
        assertTrue(Files.exists(ROOT.resolve("resources/data/warlockery/loot_table/entities/mandrake.json")));
        assertTrue(Files.exists(ROOT.resolve("resources/data/warlockery/loot_table/entities/dreamroot.json")));
        assertTrue(Files.exists(ROOT.resolve("resources/data/warlockery/tags/entity_type/mutation/minedrake/living_mandrakes.json")));
        assertTrue(Files.exists(ROOT.resolve("resources/data/warlockery/tags/block/mutation/minedrake/mandrake_crops.json")));
    }

    @Test void sourceHasNoHistoricalCombinedPlantConstructSubstituteOrForbiddenSignatureEffects() throws IOException {
        final String mandrake = read(ROOT.resolve("java/com/kadamitas/warlockery/entity/MandrakeRuntime.java"));
        final String dreamroot = read(ROOT.resolve("java/com/kadamitas/warlockery/entity/DreamrootRuntime.java"));
        assertFalse(mandrake.contains("PlantConstruct"));
        assertFalse(dreamroot.contains("PlantConstruct"));
        assertFalse(mandrake.contains("MobEffects.POISON"));
        assertFalse(mandrake.contains("MobEffects.WEAKNESS"));
        assertFalse(dreamroot.contains("MobEffects.POISON"));
        assertFalse(dreamroot.contains("MobEffects.WEAKNESS"));
        assertFalse(dreamroot.contains("explode("));
        assertTrue(mandrake.contains("quota.entities(list.size())"), "every returned Mandrake visit must charge the level quota");
        assertTrue(dreamroot.contains("q.entities(list.size())"), "every returned Dreamroot visit must charge the level quota");
        final String mandrakeEntity = read(ROOT.resolve("java/com/kadamitas/warlockery/entity/MandrakeEntity.java"));
        final String dreamrootEntity = read(ROOT.resolve("java/com/kadamitas/warlockery/entity/DreamrootEntity.java"));
        for (final String entity : List.of(mandrakeEntity, dreamrootEntity)) {
            assertFalse(entity.contains("LookAtPlayerGoal"),
                "the dedicated controller may not be preceded by an independent player-perception goal");
            assertFalse(entity.contains("NearestAttackableTargetGoal"));
            assertFalse(entity.contains("HurtByTargetGoal"));
            assertFalse(entity.contains("targetSelector.addGoal"),
                "living roots have no independently ticking target goals");
            assertTrue(entity.contains("new LookOnly(this)"),
                "non-perceptive random look presentation remains allowed");
        }
        assertTrue(mandrakeEntity.contains("healthBefore"), "Mandrake attribution must require effective positive damage");
        assertTrue(dreamrootEntity.contains("healthBefore"), "Dreamroot attribution must require effective positive damage");
        assertTrue(mandrakeEntity.contains("getAbsorptionAmount()"));
        assertTrue(dreamrootEntity.contains("getAbsorptionAmount()"));
        assertTrue(mandrake.contains("subjectDimension"));
        assertTrue(mandrake.contains("resettleRouteActive"),
            "RESETTLE must own an accepted route until arrival or an observed early finish");
        assertTrue(dreamroot.contains("subjectDimension"));
        assertTrue(dreamroot.contains("attackerDimension"));
        assertTrue(dreamroot.contains("attackerTick"));
        assertTrue(mandrake.contains("CancellationReason"));
        assertTrue(dreamroot.contains("CancellationReason"));
        assertTrue(mandrake.contains("feedback("));
        assertTrue(dreamroot.contains("feedback("));
        for (final String runtime : List.of(mandrake, dreamroot)) {
            assertTrue(runtime.contains("createPath("), "runtime must use a strict path object");
            assertTrue(runtime.contains("canReach()"), "partial paths must be rejected");
            assertTrue(runtime.contains(".path()"), "every new path request must charge the shared quota");
            assertFalse(runtime.contains("level.noCollision("), "hazard search may not use the convenience collision query");
            assertFalse(runtime.contains("level.getBlockCollisions("), "collision reads must use the bounded halo cache");
            assertFalse(runtime.contains("level.getBlockState("), "block reads must use the bounded halo cache");
            assertFalse(runtime.contains("level.getFluidState("), "fluid reads must use the bounded halo cache");
            assertFalse(runtime.contains("getEntities().get("), "raw entity discovery must use the capped always-accept overload");
            assertTrue(runtime.contains("escapeDestination"), "escape must retain its accepted destination");
            assertTrue(runtime.contains("hazardEscapeSuccesses++"), "validated escape arrival must be observable");
        }
        final String shared = read(ROOT.resolve("java/com/kadamitas/warlockery/entity/LivingRootsRules.java"));
        assertTrue(shared.contains("class HaloReadCache"));
        assertTrue(shared.contains("implements BlockGetter"));
        assertTrue(shared.contains("actualReads()"));
        assertTrue(shared.contains("haloLoaded()"));
        final String bulb = read(ROOT.resolve("java/com/kadamitas/warlockery/item/MinedrakeBulbItem.java"));
        assertFalse(bulb.contains("getNearestPlayer"));
        assertFalse(bulb.contains("setTarget("));
    }

    @Test void runtimeDiagnosticsExposeRequiredBoundedWorkAndForbiddenActionCounters() {
        final Set<String> mandrake = java.util.Arrays.stream(MandrakeRuntime.Counters.class.getDeclaredFields())
            .map(java.lang.reflect.Field::getName).collect(Collectors.toSet());
        final Set<String> dreamroot = java.util.Arrays.stream(DreamrootRuntime.Counters.class.getDeclaredFields())
            .map(java.lang.reflect.Field::getName).collect(Collectors.toSet());
        assertTrue(mandrake.containsAll(Set.of("episodeStarts", "episodeCancelsByReason", "pathRequests",
            "pathFailures", "pathBackoffs", "hazardObservationReads", "safeReads", "feedbackSuppressed",
            "genericBehaviorDispatches", "poisonApplications", "explosions", "chunkLoadRequests", "transientReplays")));
        assertTrue(dreamroot.containsAll(Set.of("thresholdChecks", "stirStarts", "dreamBlockedUprooted",
            "sustainBlockReads", "attackerAttributions", "meleeAttempts", "hazardObservationReads",
            "bulbCreations", "nearestPlayerQueries", "genericBehaviorDispatches", "weaknessApplications",
            "blockEdits", "transientReplays")));
    }

    private static String read(final Path path) throws IOException {
        return Files.readString(path).replace("\r", "").replace("\n", "");
    }
}
