package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.LycanPackRules.ActionKind;
import com.kadamitas.warlockery.entity.LycanPackRules.HuntPhase;
import com.kadamitas.warlockery.entity.LycanPackRules.HuntRole;
import com.kadamitas.warlockery.entity.LycanPackRules.PlayerRelation;
import com.kadamitas.warlockery.entity.LycanPackRules.Relation;
import com.kadamitas.warlockery.entity.LycanPackRules.TrailClass;
import com.kadamitas.warlockery.entity.LycanPackRules.Variant;
import com.kadamitas.warlockery.entity.LycanPackState.TrailFact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class LycanPackStateTest {
    private static final UUID MEMBER = new UUID(3L, 1L);
    private static final UUID OTHER = new UUID(3L, 2L);

    private static LycanPackState populatedWerewolf(final long now) {
        return LycanPackState.empty(Variant.WEREWOLF, now)
            .withNeeds(720, 40, now)
            .withRefuge(new BlockPos(10, 64, -4), now + 12_000L, now + 200L)
            .withRelationships(List.of(
                new PlayerRelation(MEMBER, Relation.GRIEVANCE, 2, now, now + 12_000L)
            ))
            .withTrails(List.of(new TrailFact(
                Optional.of(OTHER), TrailClass.PREY, new BlockPos(1, 64, 1), 80, now, now + 2_400L
            )))
            .withHunt(new LycanPackState.Hunt(
                Optional.of(new UUID(4L, 4L)), Optional.of(MEMBER), List.of(MEMBER, OTHER),
                Optional.of(HuntRole.ROUTE_SETTER), Optional.of(HuntPhase.RALLY),
                Optional.of(new UUID(5L, 5L)), Optional.of(new BlockPos(2, 64, 2)),
                now + 2_400L, now + 200L, 1, Optional.of(new BlockPos(0, 64, 0))
            ))
            .beginAction(ActionKind.POUNCE, now + 10L, now + 30L, now + 110L)
            .withClaim("prey", new UUID(5L, 5L).toString(), now + 200L);
    }

    @Test
    void variantDefaultsFollowTheApprovedNumbers() {
        final LycanPackState werewolf = LycanPackState.empty(Variant.WEREWOLF, 100L);
        assertEquals(LycanPackState.SCHEMA_VERSION, werewolf.schemaVersion());
        assertEquals(1, LycanPackState.SCHEMA_VERSION);
        assertEquals(Variant.WEREWOLF, werewolf.variant());
        assertEquals(300, werewolf.needs().hunger());
        assertEquals(0, werewolf.needs().fear());
        assertEquals(350, LycanPackState.empty(Variant.FERAL_LYCAN, 100L).needs().hunger());
        assertTrue(werewolf.refuge().position().isEmpty());
        assertTrue(werewolf.relationships().isEmpty());
        assertTrue(werewolf.trails().isEmpty());
        assertTrue(werewolf.hunt().episodeId().isEmpty());
        assertTrue(werewolf.cohort().familiarity().isEmpty());
        assertEquals(ActionKind.NONE, werewolf.action().kind());
    }

    @Test
    void versionOneRoundTripPreservesEverySemanticField() {
        final long now = 1_000L;
        final LycanPackState state = populatedWerewolf(now);
        final LycanPackState loaded = LycanPackState.read(state.write(), Variant.WEREWOLF, now);
        assertEquals(state.needs().hunger(), loaded.needs().hunger());
        assertEquals(state.needs().fear(), loaded.needs().fear());
        assertEquals(state.refuge().position(), loaded.refuge().position());
        assertEquals(state.relationships(), loaded.relationships());
        assertEquals(state.trails(), loaded.trails());
        assertEquals(state.hunt().episodeId(), loaded.hunt().episodeId());
        assertEquals(state.hunt().memberIds(), loaded.hunt().memberIds());
        assertEquals(state.hunt().role(), loaded.hunt().role());
        assertEquals(state.hunt().phase(), loaded.hunt().phase());
        assertEquals(state.hunt().targetId(), loaded.hunt().targetId());
        assertEquals(state.hunt().targetPosition(), loaded.hunt().targetPosition());
        assertEquals(state.hunt().targetChanges(), loaded.hunt().targetChanges());
        assertEquals(state.action().kind(), loaded.action().kind());
        assertEquals(state.action().claimPurpose(), loaded.action().claimPurpose());
        assertEquals(state.action().claimKey(), loaded.action().claimKey());
    }

    @Test
    void capsAreEnforcedAtConstructionAndVariantsExcludeForeignLedgers() {
        final long now = 0L;
        final List<UUID> members = List.of(
            new UUID(6L, 1L), new UUID(6L, 2L), new UUID(6L, 3L), new UUID(6L, 4L),
            new UUID(6L, 5L), new UUID(6L, 6L), new UUID(6L, 7L), new UUID(6L, 8L)
        );
        final LycanPackState oversized = LycanPackState.empty(Variant.WEREWOLF, now)
            .withHunt(new LycanPackState.Hunt(
                Optional.of(new UUID(7L, 7L)), Optional.empty(), members,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                now + 2_400L, now + 200L, 9, Optional.empty()
            ));
        assertEquals(6, oversized.hunt().memberIds().size(), "hunt membership caps at six");
        assertTrue(oversized.hunt().targetChanges() <= 3);
        final LycanPackState feral = LycanPackState.empty(Variant.FERAL_LYCAN, now)
            .withHunt(oversized.hunt());
        assertTrue(feral.hunt().episodeId().isEmpty(),
            "a Feral never carries Werewolf hunt membership");
        final LycanPackState werewolfCohort = LycanPackState.empty(Variant.WEREWOLF, now)
            .withCohort(new LycanPackState.Cohort(
                List.of(new LycanPackState.Familiarity(OTHER, 6, now)),
                Optional.of(new UUID(8L, 8L)), List.of(OTHER), now + 2_400L, now + 200L, now
            ));
        assertTrue(werewolfCohort.cohort().familiarity().isEmpty(),
            "a Werewolf never carries the Feral familiarity ledger");
        final LycanPackState duplicateMembers = LycanPackState.empty(Variant.WEREWOLF, now)
            .withHunt(new LycanPackState.Hunt(
                Optional.of(new UUID(7L, 7L)), Optional.empty(), List.of(MEMBER, MEMBER, OTHER),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                now + 2_400L, now + 200L, 0, Optional.empty()
            ));
        assertEquals(2, duplicateMembers.hunt().memberIds().size(), "duplicate members collapse");
    }

    @Test
    void malformedUnknownMismatchedAndLaterVersionStateDegradesToSafeDefaults() {
        final long now = 500L;
        final LycanPackState state = populatedWerewolf(now);
        final CompoundTag laterVersion = state.write();
        laterVersion.putInt("SchemaVersion", LycanPackState.SCHEMA_VERSION + 1);
        final LycanPackState fromLater = LycanPackState.read(laterVersion, Variant.WEREWOLF, now);
        assertEquals(300, fromLater.needs().hunger(), "a later schema degrades to safe defaults");
        assertTrue(fromLater.hunt().episodeId().isEmpty());

        final LycanPackState mismatched = LycanPackState.read(state.write(), Variant.FERAL_LYCAN, now);
        assertEquals(Variant.FERAL_LYCAN, mismatched.variant());
        assertEquals(350, mismatched.needs().hunger(), "variant mismatch degrades to that variant's defaults");

        final CompoundTag corruptEnum = state.write();
        corruptEnum.putString("ActionKind", "NOT_A_REAL_ACTION");
        final LycanPackState fromCorrupt = LycanPackState.read(corruptEnum, Variant.WEREWOLF, now);
        assertEquals(ActionKind.NONE, fromCorrupt.action().kind(), "unknown enums degrade to rest");

        final CompoundTag corruptUuid = state.write();
        corruptUuid.putString("HuntTarget", "not-a-uuid");
        final LycanPackState fromBadUuid = LycanPackState.read(corruptUuid, Variant.WEREWOLF, now);
        assertTrue(fromBadUuid.hunt().targetId().isEmpty(), "malformed UUIDs are dropped");

        final LycanPackState legacy = LycanPackState.read(new CompoundTag(), Variant.WEREWOLF, now);
        assertEquals(300, legacy.needs().hunger(),
            "a 1.4.0 entity without F04 state migrates to safe defaults");
        assertEquals(ActionKind.NONE, legacy.action().kind());
    }

    @Test
    void encodedStateStoresNoLiveObjectsPathsQueuesOrTransientCarrion() {
        final CompoundTag tag = populatedWerewolf(2_000L).write();
        for (final String key : tag.keySet()) {
            assertFalse(key.toLowerCase(java.util.Locale.ROOT).contains("path"), key);
            assertFalse(key.toLowerCase(java.util.Locale.ROOT).contains("node"), key);
            assertFalse(key.toLowerCase(java.util.Locale.ROOT).contains("queue"), key);
            assertFalse(key.toLowerCase(java.util.Locale.ROOT).contains("carrion"), key);
        }
        assertEquals(2, Relation.values().length,
            "only THREAT and GRIEVANCE are persistable relationship values");
    }

    @Test
    void reconciliationAppliesElapsedArithmeticExpiryAndActionCleanupInOnePass() {
        final long start = 0L;
        final LycanPackState state = populatedWerewolf(start);
        final long later = 30_000L;
        final LycanPackState reconciled = state.reconcile(later);
        assertEquals(1_000, reconciled.needs().hunger(),
            "hunger reconciliation caps at twenty-four thousand elapsed ticks");
        assertEquals(0, reconciled.needs().fear());
        assertTrue(reconciled.relationships().isEmpty(), "expired relationships clear in the same pass");
        assertTrue(reconciled.trails().isEmpty(), "expired trails clear in the same pass");
        assertTrue(reconciled.hunt().episodeId().isEmpty(), "expired hunt membership clears");
        assertTrue(reconciled.refuge().position().isEmpty(), "expired refuge clears");
        assertEquals(ActionKind.NONE, reconciled.action().kind(), "expired action windups clear");
        assertTrue(reconciled.action().claimPurpose().isEmpty(), "expired claims clear");

        final LycanPackState fresh = populatedWerewolf(start).reconcile(5L);
        assertEquals(ActionKind.POUNCE, fresh.action().kind(),
            "an unexpired action survives reconciliation");
    }

    @Test
    void actionCancellationAndDimensionChangeClearExecutionStateWithoutTouchingNeeds() {
        final long now = 100L;
        final LycanPackState state = populatedWerewolf(now);
        final LycanPackState cancelled = state.cancelAction("target_invalid", now + 20L);
        assertEquals(ActionKind.NONE, cancelled.action().kind());
        assertEquals(Optional.of("target_invalid"), cancelled.action().lastCancellation());
        assertEquals(now + 20L, cancelled.action().recoveryUntil());
        assertTrue(cancelled.action().claimPurpose().isEmpty(), "cancellation releases the claim");

        final LycanPackState crossed = state.afterDimensionChange(now);
        assertTrue(crossed.refuge().position().isEmpty());
        assertTrue(crossed.hunt().episodeId().isEmpty());
        assertTrue(crossed.hunt().targetId().isEmpty());
        assertEquals(ActionKind.NONE, crossed.action().kind());
        assertTrue(crossed.action().claimPurpose().isEmpty());
        assertEquals(state.needs().hunger(), crossed.needs().hunger(),
            "portable hunger survives dimension change");
        assertEquals(state.relationships(), crossed.relationships(),
            "bounded expiring relationships survive dimension change");
    }

    @Test
    void routeResultsFeedTheBoundedFailureBackoff() {
        final long now = 1_000L;
        LycanPackState state = LycanPackState.empty(Variant.FERAL_LYCAN, now);
        state = state.recordRouteResult(false, now);
        state = state.recordRouteResult(false, now + 20L);
        assertEquals(2, state.cadence().routeFailures());
        assertEquals(0L, state.cadence().retryAfter());
        state = state.recordRouteResult(false, now + 40L);
        assertEquals(3, state.cadence().routeFailures());
        assertEquals(now + 40L + 100L, state.cadence().retryAfter(),
            "the third failure imposes one hundred ticks of backoff");
        state = state.recordRouteResult(true, now + 200L);
        assertEquals(0, state.cadence().routeFailures());
        assertEquals(0L, state.cadence().retryAfter());
    }
}
