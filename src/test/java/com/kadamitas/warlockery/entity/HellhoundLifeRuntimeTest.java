package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.HellhoundLifeRules.PackRole;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class HellhoundLifeRuntimeTest {
    private static final Path MAIN = Path.of("src", "main", "java", "com", "kadamitas", "warlockery");

    @Test
    void candidateRetentionPreseedsAuthorityBeforeGenericCandidates() {
        final UUID ownerThreat = new UUID(0L, 1L);
        final UUID attacker = new UUID(0L, 2L);
        final UUID warned = new UUID(0L, 3L);
        final UUID challenger = new UUID(0L, 4L);
        final List<UUID> generic = IntStream.range(100, 140)
            .mapToObj(index -> new UUID(1L, index))
            .toList();
        final List<UUID> retained = HellhoundLifeRuntime.retainCandidates(
            Optional.of(ownerThreat), Optional.of(attacker), Optional.of(warned),
            Optional.of(challenger), generic
        );
        assertEquals(HellhoundLifeRules.MAX_RETAINED_CANDIDATES, retained.size());
        assertEquals(ownerThreat, retained.get(0), "the owner threat is retained first");
        assertEquals(attacker, retained.get(1), "the direct attacker is retained second");
        assertEquals(warned, retained.get(2), "the current warning target is retained third");
        assertEquals(challenger, retained.get(3), "the stable challenger is retained fourth");
        assertTrue(retained.containsAll(List.of(ownerThreat, attacker, warned, challenger)),
            "required facts cannot be evicted because generic entities iterated first");
    }

    @Test
    void candidateRetentionDeduplicatesAndHandlesEmptySeeds() {
        final UUID shared = new UUID(0L, 5L);
        final List<UUID> retained = HellhoundLifeRuntime.retainCandidates(
            Optional.of(shared), Optional.of(shared), Optional.empty(), Optional.empty(),
            List.of(shared, new UUID(0L, 6L))
        );
        assertEquals(List.of(shared, new UUID(0L, 6L)), retained);
        assertTrue(HellhoundLifeRuntime.retainCandidates(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of()
        ).isEmpty());
    }

    @Test
    void sectorOffsetsStayInsideTheThreeToFiveBlockRing() {
        final BlockPos origin = new BlockPos(0, 64, 0);
        for (int x = -20; x <= 20; x += 7) {
            for (int z = -20; z <= 20; z += 7) {
                final BlockPos target = new BlockPos(x, 64, z);
                for (final PackRole role : PackRole.values()) {
                    final BlockPos sector = HellhoundLifeRuntime.sectorOffset(role, target, origin);
                    final double distance = Math.sqrt(sector.distSqr(target));
                    if (role == PackRole.PRESSURE) {
                        assertEquals(target, sector, "pressure closes directly");
                    } else if (x != 0 || z != 0) {
                        assertTrue(distance >= HellhoundLifeRules.SECTOR_MIN_RADIUS - 0.001D
                                && distance <= HellhoundLifeRules.SECTOR_MAX_RADIUS * Math.sqrt(2.0D) + 0.001D,
                            role + " sector distance " + distance + " for target " + target);
                    }
                    assertEquals(sector, HellhoundLifeRuntime.sectorOffset(role, target, origin),
                        "sector derivation is deterministic");
                }
            }
        }
    }

    @Test
    void sectorRolesNeverCollideForOneTarget() {
        final BlockPos origin = new BlockPos(0, 64, 0);
        final BlockPos target = new BlockPos(9, 64, 3);
        final List<BlockPos> sectors = List.of(
            HellhoundLifeRuntime.sectorOffset(PackRole.PRESSURE, target, origin),
            HellhoundLifeRuntime.sectorOffset(PackRole.LEFT, target, origin),
            HellhoundLifeRuntime.sectorOffset(PackRole.RIGHT, target, origin),
            HellhoundLifeRuntime.sectorOffset(PackRole.CUTOFF, target, origin)
        );
        assertEquals(4, sectors.stream().distinct().count(),
            "each temporary role prefers a distinct approach");
    }

    /**
     * Coordinator-required regression guard: the generic-runtime invariant holds by absence.
     * {@code CreatureBehaviorRuntime} must have no HELLHOUND pulse case, and the legacy
     * {@code WarlockeryCreatureOwner} companion key must remain distinct from the
     * {@code WarlockeryInfernalOwner} pact key that F09 consumes.
     */
    @Test
    void creatureBehaviorRuntimeHasNoHellhoundCaseAndOwnerKeysStayDistinct() {
        final String behaviorRuntime = read(MAIN.resolve("entity/CreatureBehaviorRuntime.java"));
        final int tickSwitch = behaviorRuntime.indexOf("switch (profile.kind())");
        assertTrue(tickSwitch >= 0, "the pulse dispatch switch must exist");
        final int tickSwitchEnd = behaviorRuntime.indexOf("public static InteractionResult interact", tickSwitch);
        final String pulseCases = behaviorRuntime.substring(tickSwitch, tickSwitchEnd);
        assertFalse(pulseCases.contains("HELLHOUND"),
            "the generic pulse switch must not gain a HELLHOUND case; F09 runs only through "
                + "HellhoundLifeRuntime");
        final String behaviorState = read(MAIN.resolve("entity/CreatureBehaviorState.java"));
        assertTrue(behaviorState.contains("WarlockeryCreatureOwner"),
            "the shared companion owner key must remain WarlockeryCreatureOwner");
        final String pact = read(MAIN.resolve("item/InfernalPactEffects.java"));
        assertTrue(pact.contains("\"WarlockeryInfernalOwner\""),
            "the pact owner key must remain WarlockeryInfernalOwner");
        assertFalse(behaviorState.contains("WarlockeryInfernalOwner"),
            "the companion owner model must not absorb the pact key");
        final String lifeRuntime = read(MAIN.resolve("entity/HellhoundLifeRuntime.java"));
        assertTrue(lifeRuntime.contains("InfernalPactEffects.OWNER_KEY"),
            "F09 authority reads only the exact pact key");
        assertFalse(lifeRuntime.contains("WarlockeryCreatureOwner"),
            "F09 never treats the companion owner key as Hellhound authority");
    }

    @Test
    void hellhoundEntityNeverInvokesTheGenericTacticalOrAmbientRuntimes() {
        final String entity = read(MAIN.resolve("entity/HellhoundEntity.java"));
        assertFalse(entity.contains("TacticalCombatRuntime.tick"),
            "the specialized seam must fully replace the generic tactical layer");
        assertFalse(entity.contains("AmbientActivityRuntime.tick"),
            "the specialized seam must fully replace the generic ambient layer");
        assertTrue(entity.contains("HellhoundLifeRuntime.tick(this, level)"),
            "the dedicated runtime must be the one specialized activity");
        assertTrue(entity.contains("HellhoundLifeRuntime.recordDirectAttack"),
            "damage attribution must feed the dedicated runtime");
    }

    @Test
    void everyPublicRuntimeMethodHasAProductionCallerReachableFromTheEntityTick() {
        final String entity = read(MAIN.resolve("entity/HellhoundEntity.java"));
        final String pact = read(MAIN.resolve("item/InfernalPactEffects.java"));
        final String cure = read(MAIN.resolve("entity/HellhoundCureRuntime.java"));
        final String runtime = read(MAIN.resolve("entity/HellhoundLifeRuntime.java"));
        assertTrue(entity.contains("HellhoundLifeRuntime.tick("), "tick is wired");
        assertTrue(entity.contains("HellhoundLifeRuntime.recordDirectAttack("),
            "recordDirectAttack is wired");
        assertTrue(entity.contains("HellhoundLifeRuntime.eligibleTarget("),
            "eligibleTarget gates canAttack");
        assertTrue(entity.contains("HellhoundLifeRuntime.releaseAll("),
            "terminal cleanup is wired to removal");
        assertTrue(pact.contains("HellhoundLifeRuntime.deliverOwnerCommand("),
            "the owner command seam is wired to the pact ticker");
        assertTrue(cure.contains("HellhoundLifeRuntime.releaseAll("),
            "cure completion releases through the runtime");
        assertTrue(runtime.contains("retainCandidates("), "retention is used by the scan");
        assertTrue(runtime.contains("sectorOffset("), "sectors are used by engagement");
        assertTrue(runtime.contains("effectiveOwner("), "authority resolution is used");
    }

    /**
     * GATE-FAIL regression pins. The hazard branch delegates to the project-sanctioned generic
     * {@code HazardEscapeRuntime} (the committed F04 {@code LycanPackRuntime} pattern) and must
     * never stop the escape navigation it just issued; idle warning detection resolves already
     * scanned evidence by UUID instead of running its own spatial query; STALK has a live
     * writer; and the attribution freshness bound has a production consumer.
     */
    @Test
    void hazardEscapeNeverStopsItsOwnEscapePathAndBudgetsHold() {
        final String runtime = read(MAIN.resolve("entity/HellhoundLifeRuntime.java"));
        final int hazardStart = runtime.indexOf("HazardEscapeRuntime.tick");
        assertTrue(hazardStart >= 0, "the hazard delegation must exist");
        final int hazardEnd = runtime.indexOf("nextDecisionAt", hazardStart);
        final String hazardBranch = runtime.substring(hazardStart, hazardEnd);
        assertFalse(hazardBranch.contains("getNavigation().stop"),
            "the hazard branch must never stop the escape path the generic runtime just issued");
        assertTrue(hazardBranch.contains("Intent.HAZARD_ESCAPE"),
            "the hazard branch claims the hazard intent");
        assertTrue(runtime.contains("Intent.STALK"),
            "the WARN -> SNIFF/STALK ladder must have a STALK writer");
        assertTrue(runtime.contains("ATTRIBUTION_FRESHNESS_TICKS"),
            "the forty-tick attribution freshness bound must have a production consumer");
        final int warningStart = runtime.indexOf("private static HellhoundLifeState tickWarning");
        final int warningEnd = runtime.indexOf("private static HellhoundLifeState tickHeat");
        assertTrue(warningStart >= 0 && warningEnd > warningStart);
        final String warning = runtime.substring(warningStart, warningEnd);
        assertFalse(warning.contains("getEntitiesOfClass"),
            "idle warning detection must resolve scanned evidence, not run a fresh spatial query");
        assertTrue(runtime.contains("PACK_CALL_RADIUS"),
            "the pack call radius bound must gate the one-hop broadcast recipients");
        assertTrue(runtime.contains("engageableEvidence"),
            "combat target resolution must require attributed evidence kinds");
    }

    @Test
    void cureDelegationReplacedOnlyTheBody() {
        final String behaviorRuntime = read(MAIN.resolve("entity/CreatureBehaviorRuntime.java"));
        assertTrue(behaviorRuntime.contains("case HELLHOUND -> cureHellhound(creature, level, player, held);"),
            "the interaction dispatch must remain exact");
        assertTrue(behaviorRuntime.contains("return HellhoundCureRuntime.cure(creature, level, player, held);"),
            "the private cure body must delegate to the transactional runtime");
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
