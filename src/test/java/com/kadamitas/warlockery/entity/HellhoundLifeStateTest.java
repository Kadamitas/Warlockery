package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.HellhoundLifeRules.Evidence;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.EvidenceKind;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.Intent;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.Mode;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.PackOrigin;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.PackRole;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.RouteFailure;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class HellhoundLifeStateTest {
    private static final long NOW = 24_000L;
    private static final UUID SELF = new UUID(7L, 7L);
    private static final String NETHER = "minecraft:the_nether";

    @Test
    void solitaryDefaultsAreSafeAndDeterministic() {
        final HellhoundLifeState state = HellhoundLifeState.solitary(SELF, PackOrigin.SOLITARY, NOW);
        assertEquals(HellhoundLifeState.SCHEMA_VERSION, state.schemaVersion());
        assertEquals(Mode.WILD, state.mode());
        assertEquals(Intent.IDLE, state.intent());
        assertTrue(state.evidence().isEmpty());
        assertTrue(state.ownerId().isEmpty());
        assertFalse(state.retreatLatched());
        assertEquals(state.packId(),
            HellhoundLifeState.solitary(SELF, PackOrigin.SOLITARY, NOW).packId(),
            "the solitary pack identity is stable for one entity");
        assertNotEquals(state.packId(),
            HellhoundLifeState.solitary(new UUID(8L, 8L), PackOrigin.SOLITARY, NOW).packId(),
            "different entities never share a pack merely by initialization");
    }

    @Test
    void naturalGroupSharesOneExactPackAndAnchor() {
        final UUID pack = new UUID(3L, 3L);
        final BlockPos anchor = new BlockPos(10, 64, -20);
        final HellhoundLifeState first =
            HellhoundLifeState.naturalGroup(SELF, pack, NETHER, anchor, NOW);
        final HellhoundLifeState second =
            HellhoundLifeState.naturalGroup(new UUID(9L, 9L), pack, NETHER, anchor, NOW);
        assertEquals(pack, first.packId());
        assertEquals(first.packId(), second.packId());
        assertEquals(PackOrigin.NATURAL_GROUP, first.packOrigin());
        assertEquals(Optional.of(anchor), first.territoryAnchor());
        assertEquals(Optional.of(NETHER), first.territoryDimension());
    }

    @Test
    void roundTripPreservesEverySemanticField() {
        final UUID owner = new UUID(1L, 2L);
        final UUID challenger = new UUID(4L, 4L);
        HellhoundLifeState state = HellhoundLifeState
            .naturalGroup(SELF, new UUID(3L, 3L), NETHER, new BlockPos(1, 64, 1), NOW)
            .withMode(Mode.ANIMUS_BOUND, Optional.of(owner))
            .withMode(Mode.WILD, Optional.empty())
            .withChallenger(Optional.of(challenger), Optional.of(NETHER), NOW + 100L)
            .withEvidence(List.of(HellhoundLifeRules.createEvidence(
                EvidenceKind.DIRECT_ATTACK, Optional.of(challenger), Optional.of(NETHER),
                Optional.of(55L), NOW)))
            .withPackRole(Optional.of(PackRole.CUTOFF), NOW + 30L, 5L)
            .withHeatPoint(Optional.of(new BlockPos(2, 64, 2)), NOW + 200L)
            .withRetreat(true, NOW + 50L)
            .withRouteFailures(2, Optional.of(RouteFailure.STUCK), 0L)
            .withLegacyHearthReconciled()
            .withIntent(Intent.PATROL);
        final CompoundTag written = state.write();
        final HellhoundLifeState reloaded = HellhoundLifeState.read(written, SELF, NOW);
        assertEquals(state.packId(), reloaded.packId());
        assertEquals(state.packOrigin(), reloaded.packOrigin());
        assertEquals(state.territoryAnchor(), reloaded.territoryAnchor());
        assertEquals(state.territoryDimension(), reloaded.territoryDimension());
        assertEquals(Optional.of(challenger), reloaded.challengerId());
        assertEquals(1, reloaded.evidence().size());
        assertEquals(EvidenceKind.DIRECT_ATTACK, reloaded.evidence().get(0).kind());
        assertEquals(Optional.of(PackRole.CUTOFF), reloaded.packRole());
        assertEquals(Optional.of(new BlockPos(2, 64, 2)), reloaded.heatPoint());
        assertTrue(reloaded.retreatLatched());
        assertEquals(2, reloaded.routeFailures());
        assertTrue(reloaded.legacyHearthReconciled());
        assertEquals(Intent.PATROL, reloaded.intent(), "a resumable intent survives reload");
    }

    @Test
    void unknownSchemaRecoversAsSafeSolitaryState() {
        final CompoundTag alien = new CompoundTag();
        alien.putInt("Version", 999);
        alien.putString("Mode", "animus_bound");
        final HellhoundLifeState state = HellhoundLifeState.read(alien, SELF, NOW);
        assertEquals(Mode.WILD, state.mode());
        assertEquals(PackOrigin.SOLITARY, state.packOrigin());
        assertTrue(state.evidence().isEmpty());
        assertEquals(Intent.IDLE, state.intent());
    }

    @Test
    void malformedCoupledUnitsClearIndividuallyWithoutDestroyingTheRest() {
        HellhoundLifeState state = HellhoundLifeState
            .naturalGroup(SELF, new UUID(3L, 3L), NETHER, new BlockPos(1, 64, 1), NOW);
        final CompoundTag tag = state.write();
        tag.putString("Challenger", "not-a-uuid");
        tag.putLong("ChallengerExpiresAt", NOW + 100L);
        tag.putString("PackRole", "alpha");
        tag.putString("Mode", "animus_bound");
        final HellhoundLifeState reloaded = HellhoundLifeState.read(tag, SELF, NOW);
        assertTrue(reloaded.challengerId().isEmpty(), "a malformed challenger clears its unit");
        assertEquals(0L, reloaded.challengerExpiresAt());
        assertTrue(reloaded.packRole().isEmpty(), "an unknown role clears; there is no alpha");
        assertEquals(Mode.WILD, reloaded.mode(),
            "a bound mode without a stored owner cannot survive load");
        assertEquals(state.packId(), reloaded.packId(), "valid units survive");
    }

    @Test
    void extremeDeadlinesClampAndNonResumableIntentsRestartAsIdle() {
        HellhoundLifeState state = HellhoundLifeState.solitary(SELF, PackOrigin.SOLITARY, NOW)
            .withHeatPoint(Optional.of(new BlockPos(0, 64, 0)), Long.MAX_VALUE)
            .withIntent(Intent.BITE_WINDUP)
            .withBiteWindows(NOW, Long.MAX_VALUE, Long.MAX_VALUE);
        final HellhoundLifeState reloaded = HellhoundLifeState.read(state.write(), SELF, NOW);
        assertEquals(Intent.IDLE, reloaded.intent(),
            "a bite windup never resumes from disk");
        assertEquals(0L, reloaded.biteWindupStartedAt());
        assertEquals(0L, reloaded.biteCommitDeadline());
        assertTrue(reloaded.biteRecoveryUntil()
            <= NOW + HellhoundLifeRules.BITE_RECOVERY_TICKS,
            "bite recovery clamps to its exact maximum");
        assertTrue(reloaded.heatPointExpiresAt() <= NOW + HellhoundLifeRules.LOAD_DEADLINE_CLAMP_TICKS,
            "loaded deadlines clamp to six hundred ticks beyond current time");
        assertNotEquals(Long.MAX_VALUE, reloaded.heatPointExpiresAt());
    }

    @Test
    void expiredEvidenceWarningAndDestinationClearOnLoad() {
        final Evidence stale = HellhoundLifeRules.createEvidence(
            EvidenceKind.SCENT, Optional.of(new UUID(2L, 2L)), Optional.of(NETHER),
            Optional.of(9L), NOW - 500L);
        HellhoundLifeState state = HellhoundLifeState.solitary(SELF, PackOrigin.SOLITARY, NOW - 500L)
            .withEvidence(List.of(stale))
            .withWarning(Optional.of(new UUID(6L, 6L)), NOW - 500L, NOW - 480L)
            .withDestination(Optional.of(new BlockPos(3, 64, 3)), NOW - 400L);
        final HellhoundLifeState reloaded = HellhoundLifeState.read(state.write(), SELF, NOW);
        assertTrue(reloaded.evidence().isEmpty(), "offscreen time may expire evidence");
        assertTrue(reloaded.warningPlayerId().isEmpty(),
            "warning commitment never survives disk");
        assertTrue(reloaded.destination().isEmpty(), "raw destinations are rebuilt after load");
    }

    @Test
    void evidenceTruncatesDeterministicallyToFourOnLoad() {
        final List<Evidence> five = List.of(
            HellhoundLifeRules.createEvidence(EvidenceKind.SCENT,
                Optional.of(new UUID(2L, 0L)), Optional.of(NETHER), Optional.of(0L), NOW),
            HellhoundLifeRules.createEvidence(EvidenceKind.SIGHT,
                Optional.of(new UUID(2L, 1L)), Optional.of(NETHER), Optional.of(1L), NOW),
            HellhoundLifeRules.createEvidence(EvidenceKind.SIGHT,
                Optional.of(new UUID(2L, 2L)), Optional.of(NETHER), Optional.of(2L), NOW),
            HellhoundLifeRules.createEvidence(EvidenceKind.DIRECT_ATTACK,
                Optional.of(new UUID(2L, 3L)), Optional.of(NETHER), Optional.of(3L), NOW),
            HellhoundLifeRules.createEvidence(EvidenceKind.TERRITORY_INTRUSION,
                Optional.of(new UUID(2L, 4L)), Optional.of(NETHER), Optional.of(4L), NOW)
        );
        final HellhoundLifeState state = HellhoundLifeState.solitary(SELF, PackOrigin.SOLITARY, NOW)
            .withEvidence(five);
        assertEquals(HellhoundLifeRules.MAX_EVIDENCE_RECORDS, state.evidence().size(),
            "the constructor enforces the durable cap");
        final HellhoundLifeState reloaded = HellhoundLifeState.read(state.write(), SELF, NOW);
        assertTrue(reloaded.evidence().size() <= HellhoundLifeRules.MAX_EVIDENCE_RECORDS);
        assertEquals(EvidenceKind.DIRECT_ATTACK, reloaded.evidence().get(0).kind(),
            "stronger evidence survives deterministic truncation first");
    }

    @Test
    void bindingClearsWildClaimsAndRetainsHistoricalPackForCleanupOnly() {
        final UUID owner = new UUID(1L, 1L);
        final HellhoundLifeState wild = HellhoundLifeState
            .naturalGroup(SELF, new UUID(3L, 3L), NETHER, new BlockPos(0, 64, 0), NOW)
            .withChallenger(Optional.of(new UUID(5L, 5L)), Optional.of(NETHER), NOW + 100L)
            .withPackRole(Optional.of(PackRole.LEFT), NOW + 30L, 2L)
            .withWarning(Optional.of(new UUID(6L, 6L)), NOW, NOW + 20L);
        final HellhoundLifeState bound = wild.withMode(Mode.ANIMUS_BOUND, Optional.of(owner));
        assertTrue(bound.bound());
        assertTrue(bound.challengerId().isEmpty(), "binding clears the active wild challenger");
        assertTrue(bound.packRole().isEmpty(), "binding clears the wild pack role");
        assertTrue(bound.warningPlayerId().isEmpty(), "binding clears the wild warning unit");
        assertEquals(0L, bound.warningStartedAt());
        assertEquals(0L, bound.warningCommitDeadline());
        assertEquals(Optional.of(wild.packId()), bound.historicalPackId(),
            "a private historical pack id remains only for cleanup evidence");
    }

    @Test
    void releasedClearsEveryActiveClaimButKeepsIdentity() {
        final HellhoundLifeState busy = HellhoundLifeState
            .naturalGroup(SELF, new UUID(3L, 3L), NETHER, new BlockPos(0, 64, 0), NOW)
            .withChallenger(Optional.of(new UUID(5L, 5L)), Optional.of(NETHER), NOW + 100L)
            .withWarning(Optional.of(new UUID(6L, 6L)), NOW, NOW + 20L)
            .withPackRole(Optional.of(PackRole.RIGHT), NOW + 30L, 2L)
            .withDestination(Optional.of(new BlockPos(4, 64, 4)), NOW + 40L)
            .withHeatPoint(Optional.of(new BlockPos(5, 64, 5)), NOW + 200L)
            .withBiteWindows(NOW, NOW + 8L, NOW + 20L)
            .withRetreat(true, NOW + 50L)
            .withRouteFailures(3, Optional.of(RouteFailure.NO_ROUTE), NOW + 100L);
        final HellhoundLifeState released = busy.released();
        assertTrue(released.challengerId().isEmpty());
        assertTrue(released.warningPlayerId().isEmpty());
        assertTrue(released.packRole().isEmpty());
        assertTrue(released.destination().isEmpty());
        assertTrue(released.heatPoint().isEmpty());
        assertTrue(released.evidence().isEmpty());
        assertEquals(0L, released.biteCommitDeadline());
        assertFalse(released.retreatLatched());
        assertEquals(0, released.routeFailures());
        assertEquals(Intent.IDLE, released.intent());
        assertEquals(busy.packId(), released.packId(), "identity survives release");
        assertEquals(busy.territoryAnchor(), released.territoryAnchor());
    }

    @Test
    void missingStateMigratesAsLegacySolitaryWithoutGuessingFamily() {
        final HellhoundLifeState migrated =
            HellhoundLifeState.solitary(SELF, PackOrigin.LEGACY_SOLITARY, NOW);
        assertEquals(PackOrigin.LEGACY_SOLITARY, migrated.packOrigin());
        assertNotEquals(
            HellhoundLifeState.solitary(new UUID(11L, 11L), PackOrigin.LEGACY_SOLITARY, NOW).packId(),
            migrated.packId(),
            "a 1.4 save never guesses that nearby same-type entities are family");
        assertFalse(migrated.legacyHearthReconciled(),
            "the migration cleanup flag starts unreconciled");
    }

    @Test
    void routeFailureUnitClearsAsOne() {
        final HellhoundLifeState failed = HellhoundLifeState.solitary(SELF, PackOrigin.SOLITARY, NOW)
            .withRouteFailures(3, Optional.of(RouteFailure.BUDGET_EXHAUSTED), NOW + 100L);
        assertEquals(Optional.of(RouteFailure.BUDGET_EXHAUSTED), failed.lastRouteFailure());
        final HellhoundLifeState cleared = failed.withRouteFailures(0, Optional.empty(), 5L);
        assertTrue(cleared.lastRouteFailure().isEmpty());
        assertEquals(0L, cleared.routeRetryAfter(), "clearing failures clears the retry gate");
    }
}
