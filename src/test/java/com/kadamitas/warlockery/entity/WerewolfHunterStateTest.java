package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.WerewolfHunterRules.EvidenceType;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.Intent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class WerewolfHunterStateTest {
    private static final UUID HUNTER_ID = new UUID(11L, 47L);
    private static final long NOW = 24_000L;

    @Test
    void emptyStateStartsIdleWithStableStaggeredDecision() {
        final WerewolfHunterState state = WerewolfHunterState.empty(HUNTER_ID, NOW);
        assertEquals(WerewolfHunterState.SCHEMA_VERSION, state.schemaVersion());
        assertTrue(state.evidence().isEmpty());
        assertTrue(state.quarryId().isEmpty());
        assertTrue(state.huntId().isEmpty());
        assertEquals(Intent.IDLE, state.intent());
        assertEquals(0, state.routeFailures());
        final long offset = WerewolfHunterRules.stableOffset(
            HUNTER_ID, WerewolfHunterRules.DECISION_INTERVAL_TICKS
        );
        assertEquals(NOW + offset, state.cadence().nextDecisionAt());
        assertEquals(WerewolfHunterState.empty(HUNTER_ID, NOW).cadence().nextDecisionAt(),
            state.cadence().nextDecisionAt(), "staggering is deterministic per identity");
    }

    @Test
    void roundTripPreservesSemanticFactsOnly() {
        final UUID attacker = new UUID(3L, 9L);
        final UUID quarry = new UUID(4L, 12L);
        final UUID hunt = new UUID(5L, 15L);
        final BlockPos anchor = new BlockPos(10, 64, -20);
        WerewolfHunterState state = WerewolfHunterState.empty(HUNTER_ID, NOW)
            .withEvidence(List.of(
                WerewolfHunterRules.createEvidence(
                    EvidenceType.DIRECT_ATTACK, Optional.of(attacker), Optional.empty(),
                    Optional.of(anchor.asLong()), Optional.of("minecraft:overworld"), NOW),
                WerewolfHunterRules.createEvidence(
                    EvidenceType.EVENT_QUARRY, Optional.empty(), Optional.of(quarry), NOW)
            ))
            .withQuarry(Optional.of(quarry))
            .withHunt(Optional.of(hunt), NOW + 5_000L)
            .withAnchors(new WerewolfHunterState.Anchors(
                Optional.of(anchor), Optional.of(anchor.offset(3, 0, 0)),
                Optional.of(anchor.offset(1, 0, 0)), Optional.of(anchor.offset(0, 0, 1)),
                Optional.of(anchor)
            ))
            .withIntent(Intent.PATROL)
            .withRouteFailures(2);
        final CompoundTag written = state.write();
        final WerewolfHunterState loaded = WerewolfHunterState.read(written, HUNTER_ID, NOW + 10L);
        assertEquals(2, loaded.evidence().size());
        assertTrue(loaded.evidence().stream().anyMatch(entry ->
            entry.type() == EvidenceType.DIRECT_ATTACK
                && entry.sourceId().map(attacker::equals).orElse(false)
                && entry.packedPosition().equals(Optional.of(anchor.asLong()))
                && entry.dimension().equals(Optional.of("minecraft:overworld"))),
            "the observed locus and dimension round trip with the evidence record");
        assertEquals(Optional.of(quarry), loaded.quarryId(), "a quarry with live evidence survives");
        assertEquals(Optional.of(hunt), loaded.huntId());
        assertEquals(Optional.of(anchor), loaded.anchors().settlement());
        assertEquals(Optional.of(anchor.offset(3, 0, 0)), loaded.anchors().event());
        assertEquals(Optional.of(anchor), loaded.anchors().returnPoint());
        assertTrue(loaded.anchors().lane().isEmpty(), "lane claims never persist");
        assertTrue(loaded.anchors().search().isEmpty(), "search claims never persist");
        assertEquals(Intent.PATROL, loaded.intent());
        assertEquals(2, loaded.routeFailures());
    }

    @Test
    void unknownSchemaFallsBackToSafeDefaults() {
        final CompoundTag malformed = new CompoundTag();
        malformed.putInt("Version", 99);
        malformed.putString("Quarry", "not-a-uuid");
        final WerewolfHunterState state = WerewolfHunterState.read(malformed, HUNTER_ID, NOW);
        assertEquals(Intent.IDLE, state.intent());
        assertTrue(state.evidence().isEmpty());
        assertTrue(state.quarryId().isEmpty());
    }

    @Test
    void malformedCouplingsResetOnlyTheUnsafeBoundedState() {
        final CompoundTag tag = WerewolfHunterState.empty(HUNTER_ID, NOW)
            .withAnchors(new WerewolfHunterState.Anchors(
                Optional.of(new BlockPos(1, 64, 1)), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty()
            )).write();
        tag.putString("Quarry", new UUID(9L, 9L).toString());
        tag.putString("Hunt", new UUID(8L, 8L).toString());
        tag.putLong("HuntExpiresAt", NOW - 1L);
        tag.putString("Intent", "engage");
        tag.putInt("RouteFailures", 99);
        final WerewolfHunterState state = WerewolfHunterState.read(tag, HUNTER_ID, NOW);
        assertTrue(state.quarryId().isEmpty(), "a quarry without supporting evidence is cleared");
        assertTrue(state.huntId().isEmpty(), "an expired hunt coupling is cleared");
        assertEquals(0L, state.huntExpiresAt());
        assertEquals(Intent.IDLE, state.intent(), "combat intents never resume from disk");
        assertEquals(WerewolfHunterRules.MAX_ROUTE_FAILURES, state.routeFailures(),
            "route failures clamp to the bounded maximum");
        assertEquals(Optional.of(new BlockPos(1, 64, 1)), state.anchors().settlement(),
            "the valid settlement anchor is untouched by the reset");
    }

    @Test
    void extremeDeadlinesClampToTheirSemanticHorizons() {
        final CompoundTag tag = WerewolfHunterState.empty(HUNTER_ID, NOW).write();
        tag.putLong("EngageUntil", Long.MAX_VALUE);
        tag.putLong("SearchUntil", Long.MAX_VALUE);
        tag.putLong("RetreatUntil", NOW - 50L);
        tag.putLong("ActionBackoffUntil", Long.MAX_VALUE);
        tag.putLong("WarnedAt", NOW - 5L);
        final WerewolfHunterState state = WerewolfHunterState.read(tag, HUNTER_ID, NOW);
        assertEquals(NOW + WerewolfHunterRules.ENGAGE_TICKS, state.deadlines().engageUntil());
        assertEquals(NOW + WerewolfHunterRules.SEARCH_TICKS, state.deadlines().searchUntil());
        assertEquals(0L, state.deadlines().retreatUntil(), "an elapsed retreat deadline clears on load");
        assertEquals(NOW + WerewolfHunterRules.ROUTE_BACKOFF_TICKS,
            state.deadlines().actionBackoffUntil());
        assertEquals(0L, state.deadlines().warnedAt(), "warning progress never resumes from disk");
    }

    @Test
    void expiredEvidenceIsDroppedDuringLoad() {
        final WerewolfHunterState state = WerewolfHunterState.empty(HUNTER_ID, NOW)
            .withEvidence(List.of(WerewolfHunterRules.createEvidence(
                EvidenceType.WITNESSED_ATTACK, Optional.of(new UUID(2L, 2L)), Optional.empty(), NOW
            )));
        final WerewolfHunterState loaded = WerewolfHunterState.read(
            state.write(), HUNTER_ID, NOW + WerewolfHunterRules.WITNESSED_ATTACK_TICKS + 1L
        );
        assertTrue(loaded.evidence().isEmpty());
    }

    @Test
    void intentGenerationOnlyMovesForward() {
        WerewolfHunterState state = WerewolfHunterState.empty(HUNTER_ID, NOW);
        final long generation = state.intentGeneration();
        state = state.withIntent(Intent.PATROL);
        assertEquals(generation + 1L, state.intentGeneration());
        state = state.withIntent(Intent.PATROL);
        assertEquals(generation + 1L, state.intentGeneration(), "an unchanged intent keeps its generation");
        state = state.nextGeneration();
        assertEquals(generation + 2L, state.intentGeneration());
        assertFalse(state.nextGeneration().intentGeneration() < state.intentGeneration());
    }
}
