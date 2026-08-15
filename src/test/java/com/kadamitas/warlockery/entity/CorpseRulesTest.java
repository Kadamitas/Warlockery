package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.CorpseRules.Hazard;
import com.kadamitas.warlockery.entity.CorpseRules.ItemCandidate;
import com.kadamitas.warlockery.entity.CorpseRules.ItemToken;
import com.kadamitas.warlockery.entity.CorpseRules.OwnerFacts;
import com.kadamitas.warlockery.entity.CorpseRules.Release;
import com.kadamitas.warlockery.entity.CorpseRules.Route;
import com.kadamitas.warlockery.entity.CorpseRules.TargetLegality;
import com.kadamitas.warlockery.entity.CorpseRules.TargetSource;
import com.kadamitas.warlockery.entity.CorpseRules.Work;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class CorpseRulesTest {
    private static final UUID RAISE_OWNER = new UUID(1L, 1L);
    private static final UUID GRAVE_OWNER = new UUID(2L, 2L);
    private static final UUID STRANGER = new UUID(3L, 3L);
    private static final String FLESH = "minecraft:rotten_flesh";

    @Test
    void exactDeclaredConstantsRemainFrozen() {
        assertEquals(1, CorpseRules.SCHEMA_VERSION);
        assertEquals(1_200, CorpseRules.MAX_COHESION);
        assertEquals(20, CorpseRules.COHESION_INTERVAL_TICKS);
        assertEquals(19, CorpseRules.MAX_DECAY_REMAINDER);
        assertEquals(900, CorpseRules.SCAVENGE_COHESION_THRESHOLD);
        assertEquals(300, CorpseRules.FOOD_COHESION_RESTORE);
        assertEquals(2.0F, CorpseRules.FOOD_DIRECT_HEAL);
        assertEquals(60, CorpseRules.WAKE_COHESION_FLOOR);
        assertEquals(4_800, CorpseRules.GROUND_MEAL_COOLDOWN_TICKS);
        assertEquals(100, CorpseRules.ITEM_SCAN_INTERVAL_TICKS);
        assertEquals(6.0D, CorpseRules.ACTIVE_SCAN_RADIUS);
        assertEquals(2.0D, CorpseRules.DORMANT_SCAN_RADIUS);
        assertEquals(12, CorpseRules.MAX_ITEM_CANDIDATES);
        assertEquals(4.0D, CorpseRules.FINAL_ARRIVAL_DISTANCE_SQR);
        assertEquals(60, CorpseRules.ITEM_RELEASE_BACKOFF_TICKS);
        assertEquals(120, CorpseRules.DIRECT_ATTACKER_TICKS);
        assertEquals(100, CorpseRules.GRAVE_TIMESTAMP_MAX_AGE);
        assertEquals(80, CorpseRules.RAISE_TIMESTAMP_MAX_AGE);
        assertEquals(24.0D, CorpseRules.TARGET_RETENTION_DISTANCE);
        assertEquals(10, CorpseRules.TARGET_LOS_INTERVAL_TICKS);
        assertEquals(40, CorpseRules.TARGET_LOST_SIGHT_TICKS);
        assertEquals(10, CorpseRules.CLUTCH_WINDUP_TICKS);
        assertEquals(40, CorpseRules.CLUTCH_RECOVERY_TICKS);
        assertEquals(40, CorpseRules.SLOWNESS_DURATION_TICKS);
        assertEquals(0, CorpseRules.SLOWNESS_AMPLIFIER);
        assertEquals(20, CorpseRules.PATH_INTERVAL_TICKS);
        assertEquals(3, CorpseRules.MAX_ROUTE_FAILURES);
        assertEquals(100, CorpseRules.ROUTE_BACKOFF_TICKS);
        assertEquals(8.0D, CorpseRules.FOLLOW_START_DISTANCE);
        assertEquals(4.0D, CorpseRules.FOLLOW_STOP_DISTANCE);
        assertEquals(32.0D, CorpseRules.OWNER_ENVELOPE);
        assertEquals(16.0D, CorpseRules.OWNER_DEFENSE_RANGE);
        assertEquals(20, CorpseRules.OWNER_RESOLVE_INTERVAL_TICKS);
        assertEquals(20, CorpseRules.HAZARD_INTERVAL_TICKS);
        assertEquals(18, CorpseRules.HAZARD_OBSERVATION_READS);
        assertEquals(16, CorpseRules.SAFE_CANDIDATES);
        assertEquals(128, CorpseRules.SAFE_STATE_READS);
        assertEquals(8, CorpseRules.SAFE_ENTITY_VISITS_PER_CANDIDATE);
        assertEquals(32, CorpseRules.SAFE_ENTITY_VISITS_PER_SEARCH);
        assertEquals(144_000L, CorpseRules.GRAVE_DURATION_TICKS);
        assertEquals(1.1D, CorpseRules.GRAVE_COMMAND_SPEED);
        assertEquals(1.0D, CorpseRules.COMBAT_SPEED);
        assertEquals(FLESH, CorpseRules.FOOD_ITEM_ID);
    }

    @Test
    void perLevelQuotasAreExact() {
        assertEquals(16, CorpseRules.quota(Work.EXPENSIVE));
        assertEquals(8, CorpseRules.quota(Work.PATH));
        assertEquals(192, CorpseRules.quota(Work.ITEM_VISIT));
        assertEquals(512, CorpseRules.quota(Work.CHARGED_READ));
        assertEquals(128, CorpseRules.quota(Work.SAFE_ENTITY_VISIT));
        assertEquals(4, CorpseRules.quota(Work.ITEM_MUTATION));
        assertEquals(8, CorpseRules.quota(Work.CLUTCH));
        assertEquals(8, CorpseRules.quota(Work.FEEDBACK));
        assertEquals(2, CorpseRules.quota(Work.GRAVE_SCAN));
        assertEquals(64, CorpseRules.quota(Work.GRAVE_DIRECTIVE));
        assertEquals(64, CorpseRules.GRAVE_RAW_BODIES_PER_SCAN);
    }

    @Test
    void quotaGrantsStopExactlyAtTheDeclaredCeiling() {
        assertTrue(CorpseRules.mayCharge(0, 1, Work.PATH));
        assertTrue(CorpseRules.mayCharge(7, 1, Work.PATH));
        assertFalse(CorpseRules.mayCharge(8, 1, Work.PATH));
        assertFalse(CorpseRules.mayCharge(7, 2, Work.PATH));
        assertFalse(CorpseRules.mayCharge(0, 0, Work.PATH), "a zero charge is not real work");
        assertTrue(CorpseRules.mayCharge(0, 512, Work.CHARGED_READ));
        assertFalse(CorpseRules.mayCharge(1, 512, Work.CHARGED_READ));
    }

    @Test
    void quotaIdentityFollowsServerTickCountAndNeverWorldGameTime() {
        assertTrue(CorpseRules.quotaExpired(4L, 5L));
        assertFalse(CorpseRules.quotaExpired(5L, 5L));
        assertTrue(CorpseRules.quotaExpired(6L, 5L), "a rewound server tick still opens a new budget");
    }

    @Test
    void cohesionFallsExactlyOncePerTwentyLoadedTicks() {
        CorpseRules.Decay step = CorpseRules.decay(1_200, 0);
        assertEquals(1_200, step.cohesion());
        assertEquals(1, step.remainder());
        assertFalse(step.decremented());

        step = CorpseRules.decay(1_200, 19);
        assertEquals(1_199, step.cohesion());
        assertEquals(0, step.remainder());
        assertTrue(step.decremented());

        int cohesion = 1_200;
        int remainder = 0;
        for (int tick = 0; tick < 20; tick++) {
            final CorpseRules.Decay next = CorpseRules.decay(cohesion, remainder);
            cohesion = next.cohesion();
            remainder = next.remainder();
        }
        assertEquals(1_199, cohesion);
        assertEquals(0, remainder);
    }

    @Test
    void cohesionNeverFallsBelowZeroAndDormancyIsDerived() {
        final CorpseRules.Decay step = CorpseRules.decay(0, 19);
        assertEquals(0, step.cohesion());
        assertFalse(step.decremented(), "a dormant Body has nothing left to lose");
        assertTrue(CorpseRules.dormant(0));
        assertFalse(CorpseRules.dormant(1));
        assertFalse(CorpseRules.dormant(CorpseRules.MAX_COHESION));
    }

    @Test
    void feedingAndWakingClampToTheDeclaredBounds() {
        assertEquals(300, CorpseRules.fed(0));
        assertEquals(1_200, CorpseRules.fed(901));
        assertEquals(1_200, CorpseRules.fed(1_200));
        assertEquals(60, CorpseRules.woken(0));
        assertEquals(60, CorpseRules.woken(59));
        assertEquals(60, CorpseRules.woken(60));
        assertEquals(61, CorpseRules.woken(61));
        assertEquals(1_200, CorpseRules.woken(1_200));
    }

    @Test
    void groundMealCooldownDecrementsOnceAndStopsAtZero() {
        assertEquals(4_799, CorpseRules.cooldownTick(4_800));
        assertEquals(0, CorpseRules.cooldownTick(1));
        assertEquals(0, CorpseRules.cooldownTick(0));
    }

    @Test
    void legacyAmbientCooldownMigratesToBoundedRemainingLoadedTicks() {
        assertEquals(0, CorpseRules.migrateLegacyCooldown(0L, 5_000L));
        assertEquals(0, CorpseRules.migrateLegacyCooldown(4_000L, 5_000L));
        assertEquals(1_000, CorpseRules.migrateLegacyCooldown(6_000L, 5_000L));
        assertEquals(4_800, CorpseRules.migrateLegacyCooldown(5_000_000L, 5_000L));
        assertEquals(4_800, CorpseRules.migrateLegacyCooldown(Long.MAX_VALUE, 0L));
        assertEquals(0, CorpseRules.migrateLegacyCooldown(Long.MIN_VALUE, 0L));
    }

    @Test
    void scavengingRequiresLowCohesionZeroCooldownAndNoHigherActivity() {
        assertTrue(CorpseRules.mayScavenge(900, 0, false));
        assertTrue(CorpseRules.mayScavenge(1, 0, false));
        assertFalse(CorpseRules.mayScavenge(901, 0, false));
        assertFalse(CorpseRules.mayScavenge(900, 1, false));
        assertFalse(CorpseRules.mayScavenge(900, 0, true));
        assertTrue(CorpseRules.mayScavenge(0, 0, false), "a dormant Body may still take an arrival meal");
        assertEquals(6.0D, CorpseRules.scanRadius(false));
        assertEquals(2.0D, CorpseRules.scanRadius(true));
    }

    @Test
    void itemSelectionRanksTheBoundedRawSnapshotByDistanceThenUuid() {
        final ItemCandidate far = new ItemCandidate(new UUID(0L, 9L), FLESH, 1, 25.0D);
        final ItemCandidate nearHighUuid = new ItemCandidate(new UUID(0L, 8L), FLESH, 1, 4.0D);
        final ItemCandidate nearLowUuid = new ItemCandidate(new UUID(0L, 7L), FLESH, 3, 4.0D);
        final ItemCandidate wrongItem = new ItemCandidate(new UUID(0L, 1L), "minecraft:bone", 4, 1.0D);
        final ItemCandidate emptyStack = new ItemCandidate(new UUID(0L, 2L), FLESH, 0, 1.0D);

        assertEquals(Optional.of(nearLowUuid), CorpseRules.selectItem(
            List.of(far, nearHighUuid, nearLowUuid, wrongItem, emptyStack)
        ));
        assertEquals(Optional.empty(), CorpseRules.selectItem(List.of(wrongItem, emptyStack)));
        assertEquals(Optional.empty(), CorpseRules.selectItem(List.of()));
    }

    @Test
    void itemSelectionNeverInspectsMoreThanTheApiCandidateCap() {
        final java.util.ArrayList<ItemCandidate> raw = new java.util.ArrayList<>();
        for (int index = 0; index < 12; index++) {
            raw.add(new ItemCandidate(new UUID(0L, index), "minecraft:bone", 1, index));
        }
        raw.add(new ItemCandidate(new UUID(0L, 99L), FLESH, 1, 0.5D));
        assertEquals(Optional.empty(), CorpseRules.selectItem(raw),
            "a late eligible item hidden behind the raw cap is not reached this cadence");
        assertEquals(12, CorpseRules.MAX_ITEM_CANDIDATES);
    }

    @Test
    void mutationTokenRevalidatesUuidItemIdCountAndFinalDistance() {
        final ItemToken token = new ItemToken(new UUID(0L, 5L), FLESH, 3);
        assertTrue(CorpseRules.tokenValid(token,
            new ItemCandidate(new UUID(0L, 5L), FLESH, 3, 4.0D), true, true));
        assertTrue(CorpseRules.tokenValid(token,
            new ItemCandidate(new UUID(0L, 5L), FLESH, 3, 0.0D), true, true),
            "movement inside the final envelope is allowed");
        assertFalse(CorpseRules.tokenValid(token,
            new ItemCandidate(new UUID(0L, 5L), FLESH, 3, 4.01D), true, true));
        assertFalse(CorpseRules.tokenValid(token,
            new ItemCandidate(new UUID(0L, 6L), FLESH, 3, 1.0D), true, true));
        assertFalse(CorpseRules.tokenValid(token,
            new ItemCandidate(new UUID(0L, 5L), "minecraft:bone", 3, 1.0D), true, true));
        assertFalse(CorpseRules.tokenValid(token,
            new ItemCandidate(new UUID(0L, 5L), FLESH, 2, 1.0D), true, true), "a merge or split releases");
        assertFalse(CorpseRules.tokenValid(token,
            new ItemCandidate(new UUID(0L, 5L), FLESH, 4, 1.0D), true, true));
        assertFalse(CorpseRules.tokenValid(token,
            new ItemCandidate(new UUID(0L, 5L), FLESH, 3, 1.0D), false, true));
        assertFalse(CorpseRules.tokenValid(token,
            new ItemCandidate(new UUID(0L, 5L), FLESH, 3, 1.0D), true, false));
    }

    @Test
    void graveAuthorityRequiresUnexpiredKeysLoadedOwnerAndThePath() {
        final OwnerFacts held = new OwnerFacts(
            Optional.of(RAISE_OWNER), Optional.of(GRAVE_OWNER), 10_000L, true, true
        );
        assertTrue(CorpseRules.graveAuthorityActive(held, 9_999L));
        assertFalse(CorpseRules.graveAuthorityActive(held, 10_000L), "expiry is absolute and inclusive");
        assertFalse(CorpseRules.graveAuthorityActive(held, 10_001L));
        assertFalse(CorpseRules.graveAuthorityActive(new OwnerFacts(
            Optional.of(RAISE_OWNER), Optional.of(GRAVE_OWNER), 10_000L, false, true), 0L),
            "an offline controller stays protected but inactive");
        assertFalse(CorpseRules.graveAuthorityActive(new OwnerFacts(
            Optional.of(RAISE_OWNER), Optional.of(GRAVE_OWNER), 10_000L, true, false), 0L));
        assertFalse(CorpseRules.graveAuthorityActive(new OwnerFacts(
            Optional.of(RAISE_OWNER), Optional.empty(), 0L, true, true), 0L));
    }

    @Test
    void bothOwnerIdentitiesAreAbsoluteTargetExclusions() {
        final OwnerFacts facts = new OwnerFacts(
            Optional.of(RAISE_OWNER), Optional.of(GRAVE_OWNER), 10_000L, false, false
        );
        assertTrue(CorpseRules.protectedIdentity(facts, RAISE_OWNER));
        assertTrue(CorpseRules.protectedIdentity(facts, GRAVE_OWNER),
            "an unresolved Grave UUID is still protected");
        assertFalse(CorpseRules.protectedIdentity(facts, STRANGER));
        final OwnerFacts collapsed = new OwnerFacts(
            Optional.of(RAISE_OWNER), Optional.of(RAISE_OWNER), 10_000L, true, true
        );
        assertTrue(CorpseRules.protectedIdentity(collapsed, RAISE_OWNER));
        assertEquals(1, CorpseRules.protectedIdentities(collapsed).size(),
            "one person in both roles is one protected identity");
        assertEquals(2, CorpseRules.protectedIdentities(facts).size());
    }

    @Test
    void manualFeedingAcceptsOnlyTheRaiseOwnerOrAValidGraveController() {
        final OwnerFacts facts = new OwnerFacts(
            Optional.of(RAISE_OWNER), Optional.of(GRAVE_OWNER), 10_000L, true, true
        );
        assertTrue(CorpseRules.manualFeedAccepted(facts, RAISE_OWNER, 0L, true, 1_200));
        assertTrue(CorpseRules.manualFeedAccepted(facts, RAISE_OWNER, 0L, false, 900));
        assertTrue(CorpseRules.manualFeedAccepted(facts, GRAVE_OWNER, 0L, false, 900));
        assertFalse(CorpseRules.manualFeedAccepted(facts, GRAVE_OWNER, 10_000L, false, 900),
            "an expired controller cannot feed");
        assertFalse(CorpseRules.manualFeedAccepted(facts, STRANGER, 0L, true, 0));
        assertFalse(CorpseRules.manualFeedAccepted(facts, RAISE_OWNER, 0L, false, 1_200),
            "nothing can improve, so the interaction passes");
    }

    @Test
    void ownerTimestampAgesAreInclusiveAndRejectNegativeOrStaleReferences() {
        assertTrue(CorpseRules.timestampFresh(100, 100, CorpseRules.GRAVE_TIMESTAMP_MAX_AGE));
        assertTrue(CorpseRules.timestampFresh(200, 100, CorpseRules.GRAVE_TIMESTAMP_MAX_AGE));
        assertFalse(CorpseRules.timestampFresh(201, 100, CorpseRules.GRAVE_TIMESTAMP_MAX_AGE));
        assertFalse(CorpseRules.timestampFresh(99, 100, CorpseRules.GRAVE_TIMESTAMP_MAX_AGE),
            "a negative age is stale, not fresh");
        assertTrue(CorpseRules.timestampFresh(180, 100, CorpseRules.RAISE_TIMESTAMP_MAX_AGE));
        assertFalse(CorpseRules.timestampFresh(181, 100, CorpseRules.RAISE_TIMESTAMP_MAX_AGE));
        assertFalse(CorpseRules.timestampFresh(0, 0, CorpseRules.GRAVE_TIMESTAMP_MAX_AGE),
            "a zero sentinel never reads as due");
    }

    @Test
    void absoluteTargetExclusionsRejectEveryProtectedRelation() {
        final TargetLegality legal = TargetLegality.of(true);
        assertTrue(CorpseRules.targetLegal(legal));
        assertFalse(CorpseRules.targetLegal(legal.withSelf(true)));
        assertFalse(CorpseRules.targetLegal(legal.withDead(true)));
        assertFalse(CorpseRules.targetLegal(legal.withInvulnerable(true)));
        assertFalse(CorpseRules.targetLegal(legal.withCrossLevel(true)));
        assertFalse(CorpseRules.targetLegal(legal.withProtectedOwner(true)));
        assertFalse(CorpseRules.targetLegal(legal.withCorpse(true)));
        assertFalse(CorpseRules.targetLegal(legal.withCreativeOrSpectator(true)));
        assertFalse(CorpseRules.targetLegal(legal.withGarbed(true)));
        assertFalse(CorpseRules.targetLegal(TargetLegality.of(false)));
    }

    @Test
    void targetSourcePriorityFollowsTheFrozenOrder() {
        assertEquals(Optional.of(TargetSource.DIRECT_ATTACKER),
            CorpseRules.targetSource(true, true, true, true));
        assertEquals(Optional.empty(), CorpseRules.targetSource(false, true, true, true),
            "an active Grave position command suppresses combat selection");
        assertEquals(Optional.of(TargetSource.EXPLICIT),
            CorpseRules.targetSource(false, false, true, true));
        assertEquals(Optional.of(TargetSource.GRAVE_CONTROLLER),
            CorpseRules.targetSourceWithoutExplicit(false, false, true, true));
        assertEquals(Optional.of(TargetSource.RAISE_OWNER),
            CorpseRules.targetSourceWithoutExplicit(false, false, false, true));
        assertEquals(Optional.empty(), CorpseRules.targetSourceWithoutExplicit(false, false, false, false));
    }

    @Test
    void targetRetentionReleasesOnRangeInvalidityAndLostSightGrace() {
        assertSame(Release.NONE, CorpseRules.retention(true, true, true, 24.0D, 40));
        assertSame(Release.RANGE, CorpseRules.retention(true, true, true, 24.01D, 0));
        assertSame(Release.LOST_SIGHT, CorpseRules.retention(true, true, true, 4.0D, 41));
        assertSame(Release.MISSING, CorpseRules.retention(false, true, true, 4.0D, 0));
        assertSame(Release.MISSING, CorpseRules.retention(true, false, true, 4.0D, 0));
        assertSame(Release.ILLEGAL, CorpseRules.retention(true, true, false, 4.0D, 0));
    }

    @Test
    void lineOfSightIsCheckedAtMostOncePerTenLoadedTicks() {
        assertTrue(CorpseRules.lineOfSightDue(0L, 10L));
        assertFalse(CorpseRules.lineOfSightDue(0L, 9L));
        assertTrue(CorpseRules.lineOfSightDue(100L, 110L));
        assertFalse(CorpseRules.lineOfSightDue(100L, 100L));
    }

    @Test
    void onlyEffectivePositiveDamageIsAnAcceptedHit() {
        assertTrue(CorpseRules.effectiveDamage(true, 20.0F, 19.5F));
        assertFalse(CorpseRules.effectiveDamage(false, 20.0F, 19.5F));
        assertFalse(CorpseRules.effectiveDamage(true, 20.0F, 20.0F), "a zeroed hit is not damage");
        assertFalse(CorpseRules.effectiveDamage(true, 20.0F, 20.5F));
        assertFalse(CorpseRules.effectiveDamage(true, 8.0F, 8.0F), "full absorption is not damage");
    }

    @Test
    void clutchAppliesSlownessOnlyAfterAnAcceptedContact() {
        assertTrue(CorpseRules.clutchComplete(10));
        assertFalse(CorpseRules.clutchComplete(9));
        assertTrue(CorpseRules.applySlowness(true, 20.0F, 17.0F));
        assertFalse(CorpseRules.applySlowness(false, 20.0F, 17.0F));
        assertFalse(CorpseRules.applySlowness(true, 20.0F, 20.0F));
        assertEquals(40, CorpseRules.CLUTCH_RECOVERY_TICKS);
    }

    @Test
    void pathCadenceIsOneRequestPerBodyPerTwentyLoadedTicks() {
        assertTrue(CorpseRules.pathDue(0L, 20L));
        assertFalse(CorpseRules.pathDue(0L, 19L));
        assertTrue(CorpseRules.pathDue(-1L, 0L), "a fresh Body may path immediately");
        assertFalse(CorpseRules.pathDue(100L, 100L));
    }

    @Test
    void thirdConsecutiveRouteFailureClearsAndBacksOff() {
        Route route = CorpseRules.routeFailed(Route.fresh(), 1_000L);
        assertEquals(1, route.failures());
        assertEquals(0L, route.backoffUntil());
        assertFalse(route.released());

        route = CorpseRules.routeFailed(route, 1_000L);
        assertEquals(2, route.failures());
        assertFalse(route.released());

        route = CorpseRules.routeFailed(route, 1_000L);
        assertEquals(3, route.failures());
        assertTrue(route.released());
        assertEquals(1_100L, route.backoffUntil());
        assertFalse(CorpseRules.routeAllowed(route, 1_099L));
        assertTrue(CorpseRules.routeAllowed(route, 1_100L));

        assertEquals(Route.fresh(), CorpseRules.routeSucceeded());
    }

    @Test
    void followStartsBeyondEightAndStopsWithinFourInsideTheOwnerEnvelope() {
        assertTrue(CorpseRules.followShouldStart(8.01D));
        assertFalse(CorpseRules.followShouldStart(8.0D));
        assertFalse(CorpseRules.followShouldStart(33.0D), "an owner outside the envelope is not followed");
        assertTrue(CorpseRules.followShouldStop(4.0D));
        assertFalse(CorpseRules.followShouldStop(4.01D));
        assertTrue(CorpseRules.ownerDefenseInRange(16.0D));
        assertFalse(CorpseRules.ownerDefenseInRange(16.01D));
    }

    @Test
    void followContinuesInsideTheHysteresisBandUntilTheFourBlockStop() {
        assertTrue(CorpseRules.followShouldContinue(4.01D),
            "an already-following Body keeps closing below the 8-block start threshold");
        assertTrue(CorpseRules.followShouldContinue(8.0D));
        assertTrue(CorpseRules.followShouldContinue(32.0D));
        assertFalse(CorpseRules.followShouldContinue(4.0D),
            "the designed stop distance ends the follow");
        assertFalse(CorpseRules.followShouldContinue(32.01D),
            "leaving the owner envelope ends the follow");
    }

    @Test
    void raiseDefenseIsSuspendedNotErasedUnderActiveGraveAuthority() {
        assertFalse(CorpseRules.raiseDefenseAvailable(true),
            "active Grave authority suspends Raise-owner defense");
        assertTrue(CorpseRules.raiseDefenseAvailable(false),
            "Grave expiry or invalidity restores Raise-owner defense");
        assertEquals(Optional.empty(),
            CorpseRules.targetSourceWithoutExplicit(false, false, false,
                CorpseRules.raiseDefenseAvailable(true)),
            "a suspended Raise defense signal never selects the owner's attacker");
        assertEquals(Optional.of(TargetSource.RAISE_OWNER),
            CorpseRules.targetSourceWithoutExplicit(false, false, false,
                CorpseRules.raiseDefenseAvailable(false)),
            "the restored Raise defense signal resumes normally");
    }

    @Test
    void hazardObservationCoversOnlyFireLavaAndContact() {
        assertSame(Hazard.LAVA, CorpseRules.hazard(true, true, true));
        assertSame(Hazard.FIRE, CorpseRules.hazard(true, false, true));
        assertSame(Hazard.CONTACT, CorpseRules.hazard(false, false, true));
        assertSame(Hazard.NONE, CorpseRules.hazard(false, false, false));
        assertTrue(CorpseRules.hazardObservationDue(0L, 20L));
        assertFalse(CorpseRules.hazardObservationDue(0L, 19L));
    }

    @Test
    void safeDestinationBudgetsRejectRatherThanReadPartially() {
        assertTrue(CorpseRules.safeSearchAffordable(0, 0, 40, 8));
        assertFalse(CorpseRules.safeSearchAffordable(0, 0, 129, 8),
            "a candidate that cannot be fully charged is rejected before the first read");
        assertFalse(CorpseRules.safeSearchAffordable(120, 0, 16, 8));
        assertFalse(CorpseRules.safeSearchAffordable(0, 32, 16, 1));
        assertFalse(CorpseRules.safeSearchAffordable(0, 0, 16, 9),
            "no candidate may visit more than eight raw entities");
    }

    @Test
    void staggerIsDeterministicAndInsideTheDeclaredPeriod() {
        final UUID body = new UUID(7L, 13L);
        assertEquals(CorpseRules.stagger(body, 100), CorpseRules.stagger(body, 100));
        assertTrue(CorpseRules.stagger(body, 100) >= 0);
        assertTrue(CorpseRules.stagger(body, 100) < 100);
        assertEquals(0, CorpseRules.stagger(body, 1));
        assertEquals(0, CorpseRules.stagger(body, 0));
    }
}
