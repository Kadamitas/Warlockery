package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.EldritchWatcherRules.ActionType;
import com.kadamitas.warlockery.entity.EldritchWatcherRules.EvidenceType;
import com.kadamitas.warlockery.entity.EldritchWatcherRules.Mode;
import com.kadamitas.warlockery.entity.EldritchWatcherState.Site;
import com.kadamitas.warlockery.entity.EldritchWatcherState.TimedSite;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class EldritchWatcherStateTest {
    private static final UUID WATCHER_ID = new UUID(31L, 77L);
    private static final String OVERWORLD = "minecraft:overworld";
    private static final long NOW = 12_000L;

    private static EldritchWatcherState populated() {
        final UUID subject = new UUID(1L, 2L);
        final UUID threat = new UUID(3L, 4L);
        return EldritchWatcherState.empty(WATCHER_ID, NOW)
            .withAnchor(Optional.of(new Site(OVERWORLD, new BlockPos(10, 64, -20))))
            .withFocus(Optional.of(new TimedSite(OVERWORLD, new BlockPos(12, 64, -18), NOW + 200L)))
            .withMode(Mode.OBSERVING)
            .withSubject(Optional.of(subject), Optional.of(EvidenceType.RECIPROCAL_GAZE), NOW + 90L, 1)
            .withLastSeen(Optional.of(new TimedSite(OVERWORLD, new BlockPos(14, 65, -21), NOW + 80L)))
            .withThreat(Optional.of(threat), NOW + 70L, NOW + 30L)
            .withAction(ActionType.REVELATION, Optional.of(subject), Optional.of(OVERWORLD), NOW + 15L, 0L)
            .withLure(Optional.of(new TimedSite(OVERWORLD, new BlockPos(8, 64, -22), NOW + 35L)))
            .withRouteFailures(2, NOW + 50L)
            .withWithdrawUntil(NOW + 60L);
    }

    @Test
    void emptyStateStartsInQuietVigilWithStableStaggeredCadence() {
        final EldritchWatcherState state = EldritchWatcherState.empty(WATCHER_ID, NOW);
        assertEquals(EldritchWatcherState.SCHEMA_VERSION, state.schemaVersion());
        assertEquals(Mode.QUIET_VIGIL, state.mode());
        assertTrue(state.anchor().isEmpty());
        assertTrue(state.subjectId().isEmpty());
        assertEquals(ActionType.NONE, state.action());
        assertEquals(0, state.routeFailures());
        assertEquals(
            EldritchWatcherState.empty(WATCHER_ID, NOW).cadence().nextPerceptionAt(),
            state.cadence().nextPerceptionAt(),
            "staggering is deterministic per identity"
        );
        assertTrue(state.cadence().nextPerceptionAt()
            < NOW + EldritchWatcherRules.PERCEPTION_INTERVAL_TICKS);
    }

    @Test
    void completeRoundTripPreservesEverySemanticFact() {
        final EldritchWatcherState original = populated();
        final CompoundTag tag = original.write();
        final EldritchWatcherState loaded = EldritchWatcherState.read(tag, WATCHER_ID, OVERWORLD, NOW);
        assertEquals(original.anchor(), loaded.anchor());
        assertEquals(original.focus(), loaded.focus());
        assertEquals(Mode.OBSERVING, loaded.mode());
        assertEquals(original.subjectId(), loaded.subjectId());
        assertEquals(original.evidenceType(), loaded.evidenceType());
        assertEquals(original.evidenceExpiresAt(), loaded.evidenceExpiresAt());
        assertEquals(original.attentionSamples(), loaded.attentionSamples());
        assertEquals(original.lastSeen(), loaded.lastSeen());
        assertEquals(original.threatId(), loaded.threatId());
        assertEquals(ActionType.REVELATION, loaded.action());
        assertEquals(original.actionTargetId(), loaded.actionTargetId());
        assertEquals(original.actionDimension(), loaded.actionDimension());
        assertEquals(original.lure(), loaded.lure());
        assertEquals(original.routeFailures(), loaded.routeFailures());
        assertEquals(original.withdrawUntil(), loaded.withdrawUntil());
        assertTrue(loaded.destination().isEmpty(),
            "flight destinations are never persisted");
    }

    @Test
    void unknownSchemaRetainsOnlyIndependentlyValidAnchorData() {
        final CompoundTag tag = populated().write();
        tag.putInt("Version", 99);
        final EldritchWatcherState loaded = EldritchWatcherState.read(tag, WATCHER_ID, OVERWORLD, NOW);
        assertEquals(populated().anchor(), loaded.anchor());
        assertEquals(Mode.QUIET_VIGIL, loaded.mode());
        assertTrue(loaded.subjectId().isEmpty());
        assertEquals(ActionType.NONE, loaded.action());
        assertTrue(loaded.focus().isEmpty());
        assertTrue(loaded.lure().isEmpty());
    }

    @Test
    void malformedEnumsAndUuidsFallBackToQuietVigil() {
        final CompoundTag tag = populated().write();
        tag.putString("Mode", "not_a_mode");
        tag.putString("Subject", "not-a-uuid");
        tag.putString("Evidence", "sinister");
        tag.putString("Action", "laser");
        final EldritchWatcherState loaded = EldritchWatcherState.read(tag, WATCHER_ID, OVERWORLD, NOW);
        assertEquals(Mode.QUIET_VIGIL, loaded.mode());
        assertTrue(loaded.subjectId().isEmpty());
        assertTrue(loaded.evidenceType().isEmpty());
        assertEquals(ActionType.NONE, loaded.action());
        assertTrue(loaded.actionTargetId().isEmpty());
    }

    @Test
    void nonNoneActionRequiresImmutableTargetAndDimension() {
        final CompoundTag tag = populated().write();
        tag.remove("ActionTarget");
        final EldritchWatcherState missingTarget = EldritchWatcherState.read(tag, WATCHER_ID, OVERWORLD, NOW);
        assertEquals(ActionType.NONE, missingTarget.action(),
            "a revelation without an immutable target cancels on load");
        assertTrue(missingTarget.actionRecoverUntil() > NOW,
            "the cancelled action enters recovery without replaying damage");

        final CompoundTag wrongDimension = populated().write();
        wrongDimension.putString("ActionDimension", "minecraft:the_nether");
        final EldritchWatcherState mismatch =
            EldritchWatcherState.read(wrongDimension, WATCHER_ID, OVERWORLD, NOW);
        assertEquals(ActionType.NONE, mismatch.action(),
            "a dimension mismatch cancels the action safely");
    }

    @Test
    void constructorNormalizesInconsistentActionIdentity() {
        final EldritchWatcherState state = EldritchWatcherState.empty(WATCHER_ID, NOW)
            .withAction(ActionType.REVELATION, Optional.empty(), Optional.empty(), NOW + 20L, 0L);
        assertEquals(ActionType.NONE, state.action(),
            "an action without target and dimension can never exist in memory");
    }

    @Test
    void extremeFutureDeadlinesAreCappedOnLoad() {
        final CompoundTag tag = populated().write();
        tag.putLong("EvidenceExpiresAt", Long.MAX_VALUE);
        tag.putLong("WithdrawUntil", Long.MAX_VALUE);
        tag.putLong("RetryAfter", Long.MAX_VALUE);
        final EldritchWatcherState loaded = EldritchWatcherState.read(tag, WATCHER_ID, OVERWORLD, NOW);
        assertTrue(loaded.evidenceExpiresAt() <= NOW + EldritchWatcherRules.SEEN_EVIDENCE_TICKS);
        assertTrue(loaded.withdrawUntil() <= NOW + EldritchWatcherRules.WITHDRAW_TICKS);
        assertTrue(loaded.retryAfter() <= NOW + EldritchWatcherRules.ROUTE_BACKOFF_TICKS);
    }

    @Test
    void expiredEvidenceFocusAndLureAreDroppedOnLoad() {
        final CompoundTag tag = populated().write();
        final EldritchWatcherState later = EldritchWatcherState.read(
            tag, WATCHER_ID, OVERWORLD, NOW + 1_000L
        );
        assertTrue(later.subjectId().isEmpty());
        assertTrue(later.focus().isEmpty());
        assertTrue(later.lure().isEmpty());
        assertTrue(later.lastSeen().isEmpty());
        assertTrue(later.threatId().isEmpty());
        assertEquals(ActionType.NONE, later.action());
    }

    @Test
    void sitesInAnotherDimensionAreClearedOnLoad() {
        final CompoundTag tag = populated().write();
        final EldritchWatcherState nether = EldritchWatcherState.read(
            tag, WATCHER_ID, "minecraft:the_nether", NOW
        );
        assertTrue(nether.focus().isEmpty(), "focus in another dimension is suspended");
        assertTrue(nether.lure().isEmpty(), "lure in another dimension is cleared");
        assertEquals(ActionType.NONE, nether.action(),
            "a cross-dimension action cancels without damage");
        assertEquals(populated().anchor(), nether.anchor(),
            "the independently valid original anchor is retained for later return");
    }

    @Test
    void dimensionChangeCleanupClearsVolatileStateOnly() {
        final EldritchWatcherState cleared = populated().clearedForDimensionChange();
        assertTrue(cleared.subjectId().isEmpty());
        assertTrue(cleared.focus().isEmpty());
        assertTrue(cleared.lure().isEmpty());
        assertTrue(cleared.threatId().isEmpty());
        assertTrue(cleared.destination().isEmpty());
        assertEquals(ActionType.NONE, cleared.action());
        assertEquals(Mode.QUIET_VIGIL, cleared.mode());
        assertEquals(populated().anchor(), cleared.anchor());
    }

    @Test
    void persistedCompoundContainsNoUnboundedCollections() {
        final CompoundTag tag = populated().write();
        for (final String key : tag.keySet()) {
            assertFalse(tag.getCompound(key).isPresent() || tag.getList(key).isPresent(),
                "the Watcher compound stores only scalar semantic facts: " + key);
        }
        assertFalse(tag.keySet().stream().anyMatch(key -> key.startsWith("Path")
                || key.contains("Recipient") || key.contains("History")),
            "no path, recipient collection, or per-tick history is persisted");
    }

    @Test
    void transientCombatModesNeverSurviveReload() {
        final CompoundTag tag = populated().withMode(Mode.INTERCEPTING).write();
        assertEquals(Mode.QUIET_VIGIL,
            EldritchWatcherState.read(tag, WATCHER_ID, OVERWORLD, NOW).mode());
        final CompoundTag lure = populated().withMode(Mode.EXTERNAL_LURE).write();
        assertEquals(Mode.QUIET_VIGIL,
            EldritchWatcherState.read(lure, WATCHER_ID, OVERWORLD, NOW).mode());
    }
}
