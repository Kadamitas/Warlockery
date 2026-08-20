package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.CampPhase;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.ContractEnd;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.ContractKind;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.DefensiveResponse;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.Mode;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.Period;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.PersistenceReason;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.RelationFact;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.RouteFailure;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.TargetClass;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.WorkAvailability;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The pure F11 decision surface: priority, schedule, village policy, contracts, relations, caravan,
 * camp lifecycle, defence, navigation, merchant bounds, and the charged-scan coverage contract.
 */
final class HobgoblinJourneyRulesTest {
    private static final WorkAvailability NOTHING = WorkAvailability.none();
    private static final WorkAvailability ADULT_WORK =
        new WorkAvailability(true, false, false, false, false, false, false, false);
    private static final WorkAvailability CAMPABLE =
        new WorkAvailability(false, false, false, true, true, false, false, false);
    private static final WorkAvailability CHILD_PLAY =
        new WorkAvailability(false, false, false, false, false, true, false, false);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---------------------------------------------------------------- identity and cadence

    @Test
    void theFamilyOwnsExactlyOneKind() {
        assertTrue(HobgoblinJourneyRules.isExactHobgoblin(CreatureKind.HOBGOBLIN));
        assertFalse(HobgoblinJourneyRules.isExactHobgoblin(CreatureKind.GOBLIN));
        assertFalse(HobgoblinJourneyRules.isExactHobgoblin(CreatureKind.STONEBROKER));
        assertFalse(HobgoblinJourneyRules.isExactHobgoblin(CreatureKind.FORGEWARDEN));
    }

    @Test
    void everyFarFutureSentinelIsBoundedWellBelowOverflow() {
        assertTrue(HobgoblinJourneyRules.FAR_FUTURE_TICKS < Long.MAX_VALUE / 4);
        assertTrue(HobgoblinJourneyRules.CAMP_EXPIRY_TICKS <= HobgoblinJourneyRules.FAR_FUTURE_TICKS);
        assertTrue(HobgoblinJourneyRules.BIRTH_COOLDOWN_TICKS <= HobgoblinJourneyRules.FAR_FUTURE_TICKS);
    }

    @Test
    void zeroAndNegativeRemainingBothReadAsDue() {
        assertTrue(HobgoblinJourneyRules.isDue(0));
        assertTrue(HobgoblinJourneyRules.isDue(-500));
        assertFalse(HobgoblinJourneyRules.isDue(1));
        assertEquals(0, HobgoblinJourneyRules.clampRemaining(-9, 100));
        assertEquals(100, HobgoblinJourneyRules.clampRemaining(4_000, 100));
    }

    @Test
    void theIdentityOffsetIsStableNonNegativeAndSpreads() {
        final Set<Integer> offsets = new HashSet<>();
        for (int index = 0; index < 64; index++) {
            final UUID id = new UUID(index * 7919L, index * 104_729L);
            final int offset = HobgoblinJourneyRules.stableOffset(id, 40);
            assertTrue(offset >= 0 && offset < 40);
            assertEquals(offset, HobgoblinJourneyRules.stableOffset(id, 40));
            offsets.add(offset);
        }
        assertTrue(offsets.size() > 8, "identity offsets must actually stagger a batch");
        assertEquals(0, HobgoblinJourneyRules.stableOffset(null, 40));
        assertEquals(0, HobgoblinJourneyRules.stableOffset(new UUID(1L, 2L), 0));
    }

    @Test
    void theScheduleCoversEveryBoundaryAndNormalizesALongClock() {
        assertEquals(Period.DAY, HobgoblinJourneyRules.period(0L, 0));
        assertEquals(Period.DAY, HobgoblinJourneyRules.period(11_999L, 0));
        assertEquals(Period.DUSK, HobgoblinJourneyRules.period(12_000L, 0));
        assertEquals(Period.DUSK, HobgoblinJourneyRules.period(14_000L, 0));
        assertEquals(Period.NIGHT, HobgoblinJourneyRules.period(14_001L, 0));
        assertEquals(Period.NIGHT, HobgoblinJourneyRules.period(22_000L, 0));
        assertEquals(Period.DAWN, HobgoblinJourneyRules.period(22_001L, 0));
        assertEquals(Period.DAWN, HobgoblinJourneyRules.period(23_999L, 0));
        // A clock that has run far past one day still resolves, and the offset is clamped.
        assertEquals(Period.DAY, HobgoblinJourneyRules.period(24_000L * 900L, 0));
        assertEquals(HobgoblinJourneyRules.period(500L, HobgoblinJourneyRules.MAX_SCHEDULE_OFFSET_TICKS),
            HobgoblinJourneyRules.period(500L, 10_000));
    }

    @Test
    void onlyDangerAndMandatoryExitTightenTheDecisionCadence() {
        assertEquals(HobgoblinJourneyRules.URGENT_DECISION_INTERVAL_TICKS,
            HobgoblinJourneyRules.decisionInterval(Mode.FLEE));
        assertEquals(HobgoblinJourneyRules.URGENT_DECISION_INTERVAL_TICKS,
            HobgoblinJourneyRules.decisionInterval(Mode.DEFEND));
        assertEquals(HobgoblinJourneyRules.URGENT_DECISION_INTERVAL_TICKS,
            HobgoblinJourneyRules.decisionInterval(Mode.VILLAGE_EXIT));
        assertEquals(HobgoblinJourneyRules.DECISION_INTERVAL_TICKS,
            HobgoblinJourneyRules.decisionInterval(Mode.TRAVEL));
        assertEquals(HobgoblinJourneyRules.DECISION_INTERVAL_TICKS,
            HobgoblinJourneyRules.decisionInterval(null));
    }

    // ---------------------------------------------------------------- priority

    @Test
    void hazardAndLowHealthEscapeOutrankEverythingElse() {
        assertEquals(Mode.FLEE, HobgoblinJourneyRules.selectMode(
            false, true, DefensiveResponse.DEFEND, true, true, true,
            CampPhase.COMMIT, false, true, Period.NIGHT, true, ADULT_WORK));
        assertEquals(Mode.FLEE, HobgoblinJourneyRules.selectMode(
            false, false, DefensiveResponse.FLEE, true, true, true,
            CampPhase.COMMIT, false, true, Period.NIGHT, true, ADULT_WORK));
    }

    @Test
    void mandatoryVillageExitOutranksTradeWorkAndCamp() {
        assertEquals(Mode.VILLAGE_EXIT, HobgoblinJourneyRules.selectMode(
            false, false, DefensiveResponse.NONE, true, true, true,
            CampPhase.ACTIVE, false, true, Period.NIGHT, true, ADULT_WORK));
        // Even a child in village space exits before it plays.
        assertEquals(Mode.VILLAGE_EXIT, HobgoblinJourneyRules.selectMode(
            true, false, DefensiveResponse.NONE, true, false, false,
            CampPhase.NONE, false, false, Period.DAY, false, CHILD_PLAY));
    }

    @Test
    void aChildNeverReceivesAnAdultCampWorkOrCombatMode() {
        for (final Period period : Period.values()) {
            final Mode mode = HobgoblinJourneyRules.selectMode(
                true, false, DefensiveResponse.DEFEND, false, true, true,
                CampPhase.COMMIT, false, true, period, true, CAMPABLE);
            assertTrue(mode == Mode.IDLE || mode == Mode.CHILD_PLAY,
                "a child chose " + mode);
        }
        assertEquals(Mode.CHILD_PLAY, HobgoblinJourneyRules.selectMode(
            true, false, DefensiveResponse.NONE, false, false, false,
            CampPhase.NONE, false, false, Period.DAY, false, CHILD_PLAY));
    }

    @Test
    void aCommittedTransactionOutranksTradeAndOrdinaryWork() {
        assertEquals(Mode.WORK_COMMIT, HobgoblinJourneyRules.selectMode(
            false, false, DefensiveResponse.NONE, false, true, true,
            CampPhase.NONE, false, true, Period.NIGHT, true, ADULT_WORK));
        assertEquals(Mode.CAMP_BUILD, HobgoblinJourneyRules.selectMode(
            false, false, DefensiveResponse.NONE, false, true, true,
            CampPhase.COMMIT, false, true, Period.NIGHT, true, ADULT_WORK));
        assertEquals(Mode.CAMP_TEARDOWN, HobgoblinJourneyRules.selectMode(
            false, false, DefensiveResponse.NONE, false, true, true,
            CampPhase.TEARDOWN, false, true, Period.NIGHT, true, ADULT_WORK));
    }

    @Test
    void aLiveExternalEventHoldsTeardownButNeverBlocksDanger() {
        assertEquals(Mode.TRAVEL, HobgoblinJourneyRules.selectMode(
            false, false, DefensiveResponse.NONE, false, false, false,
            CampPhase.TEARDOWN, true, false, Period.DAY, false, NOTHING));
        assertEquals(Mode.CAMP_TEARDOWN, HobgoblinJourneyRules.selectMode(
            false, false, DefensiveResponse.NONE, false, false, false,
            CampPhase.TEARDOWN, false, false, Period.DAY, false, NOTHING));
        assertEquals(Mode.FLEE, HobgoblinJourneyRules.selectMode(
            false, true, DefensiveResponse.NONE, false, false, false,
            CampPhase.TEARDOWN, true, false, Period.DAY, false, NOTHING));
    }

    @Test
    void nightAndDuskPreferWorkWhileDayPrefersTravelAndRest() {
        assertEquals(Mode.WORK_APPROACH, HobgoblinJourneyRules.selectMode(
            false, false, DefensiveResponse.NONE, false, false, false,
            CampPhase.NONE, false, false, Period.NIGHT, false, ADULT_WORK));
        assertEquals(Mode.TRAVEL, HobgoblinJourneyRules.selectMode(
            false, false, DefensiveResponse.NONE, false, false, false,
            CampPhase.NONE, false, false, Period.DAY, false, ADULT_WORK));
        // A committed agreement makes work legal in any period.
        assertEquals(Mode.WORK_APPROACH, HobgoblinJourneyRules.selectMode(
            false, false, DefensiveResponse.NONE, false, false, false,
            CampPhase.NONE, false, false, Period.DAY, true, ADULT_WORK));
        assertEquals(Mode.CAMP_REST, HobgoblinJourneyRules.selectMode(
            false, false, DefensiveResponse.NONE, false, false, false,
            CampPhase.ACTIVE, false, false, Period.DAY, false, NOTHING));
        assertEquals(Mode.CAMP_PROPOSE, HobgoblinJourneyRules.selectMode(
            false, false, DefensiveResponse.NONE, false, false, false,
            CampPhase.NONE, false, false, Period.DUSK, false, CAMPABLE));
        assertEquals(Mode.REGROUP, HobgoblinJourneyRules.selectMode(
            false, false, DefensiveResponse.NONE, false, false, false,
            CampPhase.NONE, false, true, Period.DAY, false, NOTHING));
    }

    @Test
    void onlyAMoreUrgentModeMayPreemptAWorldEditingTransaction() {
        assertTrue(HobgoblinJourneyRules.interrupts(Mode.CAMP_BUILD, Mode.FLEE));
        assertTrue(HobgoblinJourneyRules.interrupts(Mode.WORK_COMMIT, Mode.VILLAGE_EXIT));
        assertTrue(HobgoblinJourneyRules.interrupts(Mode.CAMP_TEARDOWN, Mode.DEFEND));
        assertFalse(HobgoblinJourneyRules.interrupts(Mode.WORK_COMMIT, Mode.TRAVEL));
        assertFalse(HobgoblinJourneyRules.interrupts(Mode.CAMP_BUILD, Mode.CAMP_REST));
        assertTrue(HobgoblinJourneyRules.interrupts(Mode.TRAVEL, Mode.WORK_APPROACH));
        assertFalse(HobgoblinJourneyRules.interrupts(Mode.TRAVEL, Mode.TRAVEL));
        assertFalse(HobgoblinJourneyRules.interrupts(null, Mode.FLEE));
    }

    @Test
    void exactlyTheThreeWorldEditingModesRequireAClaim() {
        for (final Mode mode : Mode.values()) {
            final boolean edits = mode == Mode.CAMP_BUILD
                || mode == Mode.CAMP_TEARDOWN
                || mode == Mode.WORK_COMMIT;
            assertEquals(edits, mode.editsWorld(), mode.name());
            assertTrue(!mode.editsWorld() || mode.requiresClaim(), mode.name());
        }
        assertTrue(Mode.WORK_APPROACH.requiresClaim());
        assertFalse(Mode.TRAVEL.requiresClaim());
        assertFalse(Mode.CHILD_PLAY.requiresClaim());
    }

    // ---------------------------------------------------------------- village exclusion

    @Test
    void everyIndependentVillageSignalExcludesAPositionOnItsOwn() {
        assertFalse(HobgoblinJourneyRules.villageExcluded(false, false, false));
        assertTrue(HobgoblinJourneyRules.villageExcluded(true, false, false));
        assertTrue(HobgoblinJourneyRules.villageExcluded(false, true, false));
        assertTrue(HobgoblinJourneyRules.villageExcluded(false, false, true));
    }

    @Test
    void anExitCandidateMustLeaveTheVillageAndLandInTheDeclaredBand() {
        assertTrue(HobgoblinJourneyRules.exitCandidateAccepted(16.0, false, true));
        assertFalse(HobgoblinJourneyRules.exitCandidateAccepted(16.0, true, true), "still excluded");
        assertFalse(HobgoblinJourneyRules.exitCandidateAccepted(16.0, false, false), "not standable");
        assertFalse(HobgoblinJourneyRules.exitCandidateAccepted(4.0, false, true), "too close");
        assertFalse(HobgoblinJourneyRules.exitCandidateAccepted(64.0, false, true), "too far");
        assertTrue(HobgoblinJourneyRules.exitCandidateAccepted(
            HobgoblinJourneyRules.EXIT_MIN_DISTANCE, false, true));
        assertTrue(HobgoblinJourneyRules.exitCandidateAccepted(
            HobgoblinJourneyRules.EXIT_MAX_DISTANCE, false, true));
    }

    @Test
    void threeBlockedExitsArmTheBackoffInsteadOfRetryingForever() {
        assertFalse(HobgoblinJourneyRules.exitBlocked(0));
        assertFalse(HobgoblinJourneyRules.exitBlocked(2));
        assertTrue(HobgoblinJourneyRules.exitBlocked(HobgoblinJourneyRules.MAX_BLOCKED_EXITS));
        assertTrue(HobgoblinJourneyRules.exitBlocked(99));
    }

    @Test
    void naturalSpawnRefusesVillageOriginBufferAndLocalCrowding() {
        assertTrue(HobgoblinJourneyRules.canSpawnNaturally(false, 64, 0));
        assertFalse(HobgoblinJourneyRules.canSpawnNaturally(true, 64, 0), "village origin");
        assertFalse(HobgoblinJourneyRules.canSpawnNaturally(false, 4, 0), "inside the buffer");
        assertFalse(HobgoblinJourneyRules.canSpawnNaturally(
            false, 64, HobgoblinJourneyRules.LOCAL_SPAWN_CAP), "local cap");
    }

    // ---------------------------------------------------------------- persistence

    @Test
    void persistenceReasonsHaveAFixedPrecedenceAndAnEmptyDefault() {
        assertTrue(HobgoblinJourneyRules.persistenceReason(false, false, false, false).isEmpty());
        assertEquals(PersistenceReason.EVENT_RESIDENT,
            HobgoblinJourneyRules.persistenceReason(true, true, true, true).orElseThrow());
        assertEquals(PersistenceReason.CONTRACTED,
            HobgoblinJourneyRules.persistenceReason(true, true, true, false).orElseThrow());
        assertEquals(PersistenceReason.CAMP_RESIDENT,
            HobgoblinJourneyRules.persistenceReason(true, true, false, false).orElseThrow());
        assertEquals(PersistenceReason.CARAVAN_MEMBER,
            HobgoblinJourneyRules.persistenceReason(true, false, false, false).orElseThrow());
        assertTrue(HobgoblinJourneyRules.mayDespawn(false, false, false, false));
        assertFalse(HobgoblinJourneyRules.mayDespawn(true, false, false, false));
    }

    // ---------------------------------------------------------------- contracts

    @Test
    void onlyAnUnthreatenedAdultOutsideVillageSpaceMayAcceptAnAgreement() {
        assertTrue(HobgoblinJourneyRules.canAcceptContract(true, false, false, false, false, false));
        assertFalse(HobgoblinJourneyRules.canAcceptContract(false, false, false, false, false, false));
        assertFalse(HobgoblinJourneyRules.canAcceptContract(true, true, false, false, false, false));
        assertFalse(HobgoblinJourneyRules.canAcceptContract(true, false, true, false, false, false));
        assertFalse(HobgoblinJourneyRules.canAcceptContract(true, false, false, true, false, false));
        assertFalse(HobgoblinJourneyRules.canAcceptContract(true, false, false, false, true, false));
        assertFalse(HobgoblinJourneyRules.canAcceptContract(true, false, false, false, false, true),
            "an existing valid contractor is never overwritten by another player");
    }

    @Test
    void everyContractTerminatorHasItsOwnDeclaredOutcome() {
        assertEquals(ContractEnd.ACTIVE, HobgoblinJourneyRules.contractOutcome(true, 500, 0, false, false));
        assertEquals(ContractEnd.EXPIRED, HobgoblinJourneyRules.contractOutcome(true, 0, 0, false, false));
        assertEquals(ContractEnd.COMPLETED, HobgoblinJourneyRules.contractOutcome(
            true, 500, HobgoblinJourneyRules.MAX_CONTRACT_UNITS, false, false));
        assertEquals(ContractEnd.INVALID, HobgoblinJourneyRules.contractOutcome(false, 500, 0, false, false));
        assertEquals(ContractEnd.DISMISSED, HobgoblinJourneyRules.contractOutcome(true, 500, 0, true, false));
        assertEquals(ContractEnd.BETRAYED, HobgoblinJourneyRules.contractOutcome(true, 500, 0, true, true),
            "betrayal outranks every other terminator");
    }

    @Test
    void workPreferenceIsAProfessionFactNotARandomRoll() {
        assertEquals(ContractKind.MINING, HobgoblinJourneyRules.preferredWork(GoblinProfession.MINER));
        assertEquals(ContractKind.GATHER, HobgoblinJourneyRules.preferredWork(GoblinProfession.SMITH));
        assertEquals(ContractKind.GATHER, HobgoblinJourneyRules.preferredWork(GoblinProfession.SHAMAN));
        assertEquals(ContractKind.GATHER, HobgoblinJourneyRules.preferredWork(GoblinProfession.PROSPECTOR));
    }

    // ---------------------------------------------------------------- relations

    @Test
    void theRelationScoreIsClampedNoMatterHowManyFactsAgree() {
        assertEquals(0, HobgoblinJourneyRules.relationScore(List.of()));
        assertEquals(0, HobgoblinJourneyRules.relationScore(null));
        assertEquals(HobgoblinJourneyRules.MAX_RELATION_SCORE, HobgoblinJourneyRules.relationScore(List.of(
            RelationFact.WORK_COMPLETED, RelationFact.WORK_COMPLETED,
            RelationFact.FAIR_TRADE, RelationFact.AID, RelationFact.ACCEPTED_FOOD
        )));
        assertEquals(HobgoblinJourneyRules.MIN_RELATION_SCORE, HobgoblinJourneyRules.relationScore(List.of(
            RelationFact.ATTACK, RelationFact.ATTACK, RelationFact.COERCION, RelationFact.CHEATING
        )));
    }

    @Test
    void refusalAndDiscountFollowTheBoundedScoreOnly() {
        assertTrue(HobgoblinJourneyRules.tradeRefused(-1));
        assertFalse(HobgoblinJourneyRules.tradeRefused(0));
        assertFalse(HobgoblinJourneyRules.tradeRefused(4));
        assertEquals(0, HobgoblinJourneyRules.priceImprovement(-4));
        assertEquals(0, HobgoblinJourneyRules.priceImprovement(0));
        assertEquals(3, HobgoblinJourneyRules.priceImprovement(3));
        assertEquals(HobgoblinJourneyRules.MAX_RELATION_SCORE,
            HobgoblinJourneyRules.priceImprovement(99));
    }

    @Test
    void evictionTakesTheWeakestThenTheOldestAndIsFullyDeterministic() {
        final List<RelationFact> facts = List.of(
            RelationFact.WORK_COMPLETED, RelationFact.FAIR_TRADE, RelationFact.AID
        );
        // FAIR_TRADE and AID are equally weak, so the older of the two loses.
        assertEquals(2, HobgoblinJourneyRules.evictionIndex(facts, List.of(0, 10, 40)));
        assertEquals(1, HobgoblinJourneyRules.evictionIndex(facts, List.of(0, 40, 10)));
        assertEquals(-1, HobgoblinJourneyRules.evictionIndex(List.of(), List.of()));
        assertEquals(-1, HobgoblinJourneyRules.evictionIndex(null, null));
    }

    @Test
    void everyRelationFactCarriesABoundedMagnitudeAndExpiry() {
        for (final RelationFact fact : RelationFact.values()) {
            assertTrue(Math.abs(fact.magnitude()) <= HobgoblinJourneyRules.MAX_RELATION_SCORE, fact.name());
            assertTrue(fact.expiryTicks() > 0 && fact.expiryTicks() <= HobgoblinJourneyRules.FAR_FUTURE_TICKS,
                fact.name());
        }
    }

    // ---------------------------------------------------------------- caravan and family

    @Test
    void leaderElectionIsTheLowestUnsignedAdultUuidAndIsStable() {
        // Unsigned order: 0x0000... is the lowest and 0x8000... is the highest, so the low key is
        // UUID(0, 0). Signed comparison would have picked UUID(Long.MIN_VALUE, 0) instead, which is
        // exactly the ordering bug this comparator exists to avoid.
        final UUID low = new UUID(0L, 0L);
        final UUID mid = new UUID(Long.MAX_VALUE, 0L);
        final UUID high = new UUID(Long.MIN_VALUE, 0L);
        assertEquals(low, HobgoblinJourneyRules.electLeader(List.of(high, mid, low)).orElseThrow());
        assertEquals(low, HobgoblinJourneyRules.electLeader(List.of(low, high, mid)).orElseThrow());
        assertTrue(HobgoblinJourneyRules.electLeader(List.of()).isEmpty());
        assertTrue(HobgoblinJourneyRules.electLeader(null).isEmpty());
        assertTrue(HobgoblinJourneyRules.unsignedUuidOrder().compare(low, high) < 0);
        assertTrue(HobgoblinJourneyRules.unsignedUuidOrder().compare(mid, high) < 0);
        // A lone remaining adult is a valid solitary traveler, not a leaderless group.
        assertEquals(mid, HobgoblinJourneyRules.electLeader(List.of(mid)).orElseThrow());
    }

    @Test
    void regroupUsesHysteresisAndEndsInASafeDeparture() {
        assertFalse(HobgoblinJourneyRules.shouldRegroup(HobgoblinJourneyRules.REGROUP_RADIUS));
        assertTrue(HobgoblinJourneyRules.shouldRegroup(HobgoblinJourneyRules.REGROUP_RADIUS + 1));
        assertTrue(HobgoblinJourneyRules.regroupSatisfied(HobgoblinJourneyRules.COHESION_RADIUS));
        assertFalse(HobgoblinJourneyRules.regroupSatisfied(HobgoblinJourneyRules.COHESION_RADIUS + 1));
        assertTrue(HobgoblinJourneyRules.regroupAbandoned(0));
        assertFalse(HobgoblinJourneyRules.regroupAbandoned(1));
    }

    @Test
    void conceptionNeedsEveryDeclaredConditionAtOnce() {
        assertTrue(HobgoblinJourneyRules.canConceive(2, true, true, true, 0));
        assertFalse(HobgoblinJourneyRules.canConceive(2, false, true, true, 0), "no partner");
        assertFalse(HobgoblinJourneyRules.canConceive(2, true, false, true, 0), "unsafe context");
        assertFalse(HobgoblinJourneyRules.canConceive(2, true, true, false, 0), "no accepted food");
        assertFalse(HobgoblinJourneyRules.canConceive(2, true, true, true, 5), "birth cooldown");
        assertFalse(HobgoblinJourneyRules.canConceive(
            HobgoblinJourneyRules.MAX_CARAVAN_MEMBERS, true, true, true, 0), "group is full");
    }

    @Test
    void theChildProfessionIsTheDeterministicLowerParentAndNeverARoll() {
        final UUID low = new UUID(0L, 0L);
        final UUID high = new UUID(Long.MIN_VALUE, 0L);
        assertEquals(GoblinProfession.MINER, HobgoblinJourneyRules.childProfession(
            low, GoblinProfession.MINER, high, GoblinProfession.SHAMAN));
        assertEquals(GoblinProfession.MINER, HobgoblinJourneyRules.childProfession(
            high, GoblinProfession.SHAMAN, low, GoblinProfession.MINER));
        assertEquals(GoblinProfession.SMITH, HobgoblinJourneyRules.childProfession(
            null, GoblinProfession.MINER, low, GoblinProfession.SMITH));
        assertEquals(GoblinProfession.FALLBACK, HobgoblinJourneyRules.childProfession(low, null, high, null));
    }

    @Test
    void childPlayHasExactCapsAndCooldowns() {
        assertFalse(HobgoblinJourneyRules.canDance(HobgoblinJourneyRules.MIN_DANCE_CHILDREN - 1));
        assertTrue(HobgoblinJourneyRules.canDance(HobgoblinJourneyRules.MIN_DANCE_CHILDREN));
        assertTrue(HobgoblinJourneyRules.giftReady(true, 0));
        assertFalse(HobgoblinJourneyRules.giftReady(false, 0), "an empty hand is never a gift");
        assertFalse(HobgoblinJourneyRules.giftReady(true, 500), "cooldown holds");
    }

    // ---------------------------------------------------------------- camp

    @Test
    void everyCampPreconditionCanRefuseTheProposalOnItsOwn() {
        assertTrue(HobgoblinJourneyRules.campEligible(2, true, true, false, true, true, true, true, false, 0));
        assertFalse(HobgoblinJourneyRules.campEligible(1, true, true, false, true, true, true, true, false, 0));
        assertFalse(HobgoblinJourneyRules.campEligible(2, false, true, false, true, true, true, true, false, 0));
        assertFalse(HobgoblinJourneyRules.campEligible(2, true, false, false, true, true, true, true, false, 0));
        assertFalse(HobgoblinJourneyRules.campEligible(2, true, true, true, true, true, true, true, false, 0));
        assertFalse(HobgoblinJourneyRules.campEligible(2, true, true, false, false, true, true, true, false, 0));
        assertFalse(HobgoblinJourneyRules.campEligible(2, true, true, false, true, false, true, true, false, 0));
        assertFalse(HobgoblinJourneyRules.campEligible(2, true, true, false, true, true, false, true, false, 0));
        assertFalse(HobgoblinJourneyRules.campEligible(2, true, true, false, true, true, true, false, false, 0));
        assertFalse(HobgoblinJourneyRules.campEligible(2, true, true, false, true, true, true, true, true, 0),
            "one camp per caravan");
        assertFalse(HobgoblinJourneyRules.campEligible(2, true, true, false, true, true, true, true, false,
            HobgoblinJourneyRules.MAX_CAMP_RECORDS), "dimension record cap");
    }

    @Test
    void aPhysicalShelterNeedsMobGriefingAndTheDataOnlyCampIsTheFallback() {
        assertTrue(HobgoblinJourneyRules.campMayPlaceBlocks(true));
        assertFalse(HobgoblinJourneyRules.campMayPlaceBlocks(false));
    }

    @Test
    void campEditAndTeardownBudgetsAreClampedBothWays() {
        assertEquals(0, HobgoblinJourneyRules.campEditsThisTick(-5));
        assertEquals(3, HobgoblinJourneyRules.campEditsThisTick(3));
        assertEquals(HobgoblinJourneyRules.CAMP_EDITS_PER_TICK, HobgoblinJourneyRules.campEditsThisTick(999));
        assertEquals(0, HobgoblinJourneyRules.campTeardownThisTick(0));
        assertEquals(HobgoblinJourneyRules.CAMP_TEARDOWN_PER_TICK,
            HobgoblinJourneyRules.campTeardownThisTick(999));
    }

    @Test
    void aLiveMatchingEventHoldsTeardownOnlyUntilItsStaleDeadline() {
        assertFalse(HobgoblinJourneyRules.campExpired(500, true, false, 0));
        assertTrue(HobgoblinJourneyRules.campExpired(0, true, false, 0), "deadline");
        assertTrue(HobgoblinJourneyRules.campExpired(500, false, false, 0), "caravan gone");
        assertFalse(HobgoblinJourneyRules.campExpired(0, false, true, 400), "held by a live event");
        assertTrue(HobgoblinJourneyRules.campExpired(0, false, true, 0), "stale event deadline reached");
    }

    @Test
    void theCampPhaseTableNeverSkipsTeardownAndAlwaysReachesRelease() {
        assertEquals(CampPhase.PROPOSE, HobgoblinJourneyRules.nextCampPhase(
            CampPhase.NONE, true, false, false, false, true));
        assertEquals(CampPhase.RESERVE, HobgoblinJourneyRules.nextCampPhase(
            CampPhase.PROPOSE, true, false, false, false, true));
        assertEquals(CampPhase.VALIDATE, HobgoblinJourneyRules.nextCampPhase(
            CampPhase.RESERVE, true, false, false, false, true));
        assertEquals(CampPhase.COMMIT, HobgoblinJourneyRules.nextCampPhase(
            CampPhase.VALIDATE, true, false, false, false, true));
        assertEquals(CampPhase.COMMIT, HobgoblinJourneyRules.nextCampPhase(
            CampPhase.COMMIT, true, false, false, false, false));
        assertEquals(CampPhase.ACTIVE, HobgoblinJourneyRules.nextCampPhase(
            CampPhase.COMMIT, true, true, false, false, false));
        assertEquals(CampPhase.SUSPEND, HobgoblinJourneyRules.nextCampPhase(
            CampPhase.COMMIT, true, true, true, false, false));
        assertEquals(CampPhase.EXPIRE, HobgoblinJourneyRules.nextCampPhase(
            CampPhase.ACTIVE, false, true, false, true, false));
        assertEquals(CampPhase.TEARDOWN, HobgoblinJourneyRules.nextCampPhase(
            CampPhase.EXPIRE, false, true, false, true, false));
        assertEquals(CampPhase.TEARDOWN, HobgoblinJourneyRules.nextCampPhase(
            CampPhase.TEARDOWN, false, true, false, true, false));
        assertEquals(CampPhase.RELEASE, HobgoblinJourneyRules.nextCampPhase(
            CampPhase.TEARDOWN, false, true, false, true, true));
        assertEquals(CampPhase.NONE, HobgoblinJourneyRules.nextCampPhase(
            CampPhase.RELEASE, false, true, false, true, true));
        // A suspended camp can recover rather than being stranded.
        assertEquals(CampPhase.ACTIVE, HobgoblinJourneyRules.nextCampPhase(
            CampPhase.SUSPEND, true, true, false, false, false));
        assertEquals(CampPhase.NONE, HobgoblinJourneyRules.nextCampPhase(null, true, true, false, false, true));
    }

    @Test
    void aCampPlacementOnlyEverClaimsGenuinelyEmptySpace() {
        assertTrue(HobgoblinJourneyRules.canPlaceCampBlock(true, true, true, true, false, false));
        assertFalse(HobgoblinJourneyRules.canPlaceCampBlock(false, true, true, true, false, false));
        assertFalse(HobgoblinJourneyRules.canPlaceCampBlock(true, false, true, true, false, false));
        assertFalse(HobgoblinJourneyRules.canPlaceCampBlock(true, true, false, true, false, false));
        assertFalse(HobgoblinJourneyRules.canPlaceCampBlock(true, true, true, false, false, false),
            "a non-air prior state is never claimed, so teardown is exactly reversible");
        assertFalse(HobgoblinJourneyRules.canPlaceCampBlock(true, true, true, true, true, false));
        assertFalse(HobgoblinJourneyRules.canPlaceCampBlock(true, true, true, true, false, true));
    }

    @Test
    void theBlockEditGuardRefusesEveryUnsafeInput() {
        assertTrue(HobgoblinJourneyRules.canEditBlock(true, true, true, true, false, false, 1.5F));
        assertFalse(HobgoblinJourneyRules.canEditBlock(false, true, true, true, false, false, 1.5F));
        assertFalse(HobgoblinJourneyRules.canEditBlock(true, false, true, true, false, false, 1.5F));
        assertFalse(HobgoblinJourneyRules.canEditBlock(true, true, false, true, false, false, 1.5F));
        assertFalse(HobgoblinJourneyRules.canEditBlock(true, true, true, false, false, false, 1.5F));
        assertFalse(HobgoblinJourneyRules.canEditBlock(true, true, true, true, true, false, 1.5F));
        assertFalse(HobgoblinJourneyRules.canEditBlock(true, true, true, true, false, true, 1.5F));
        assertFalse(HobgoblinJourneyRules.canEditBlock(true, true, true, true, false, false, -1.0F),
            "an unbreakable block is never mined");
    }

    // ---------------------------------------------------------------- defence

    @Test
    void thereIsNoProactiveTargetAndEscapeAlwaysOutranksTheStrike() {
        assertEquals(DefensiveResponse.NONE,
            HobgoblinJourneyRules.defensiveResponse(true, false, true, true, 1.0F));
        assertEquals(DefensiveResponse.DEFEND,
            HobgoblinJourneyRules.defensiveResponse(true, true, true, true, 1.0F));
        assertEquals(DefensiveResponse.FLEE,
            HobgoblinJourneyRules.defensiveResponse(true, true, true, false, 1.0F),
            "with nobody to protect, a traveler leaves");
        assertEquals(DefensiveResponse.FLEE,
            HobgoblinJourneyRules.defensiveResponse(true, true, false, true, 1.0F),
            "an unreachable aggressor is never chased");
        assertEquals(DefensiveResponse.FLEE,
            HobgoblinJourneyRules.defensiveResponse(false, true, true, true, 1.0F),
            "a child never defends");
        assertEquals(DefensiveResponse.FLEE, HobgoblinJourneyRules.defensiveResponse(
            true, true, true, true, HobgoblinJourneyRules.ESCAPE_HEALTH_FRACTION - 0.01F));
    }

    @Test
    void onlyTheRememberedDirectAggressorIsEverTargetable() {
        assertTrue(HobgoblinJourneyRules.canTarget(
            true, TargetClass.DIRECT_AGGRESSOR, true, true, false, false));
        assertFalse(HobgoblinJourneyRules.canTarget(
            true, TargetClass.PROTECTED, true, true, false, false));
        assertFalse(HobgoblinJourneyRules.canTarget(
            true, TargetClass.NEUTRAL, true, true, false, false),
            "a merely neutral bystander is never prey");
        assertFalse(HobgoblinJourneyRules.canTarget(
            false, TargetClass.DIRECT_AGGRESSOR, true, true, false, false));
        assertFalse(HobgoblinJourneyRules.canTarget(
            true, TargetClass.DIRECT_AGGRESSOR, false, true, false, false));
        assertFalse(HobgoblinJourneyRules.canTarget(
            true, TargetClass.DIRECT_AGGRESSOR, true, false, false, false));
        assertFalse(HobgoblinJourneyRules.canTarget(
            true, TargetClass.DIRECT_AGGRESSOR, true, true, true, false));
        assertFalse(HobgoblinJourneyRules.canTarget(
            true, TargetClass.DIRECT_AGGRESSOR, true, true, false, true));
    }

    @Test
    void chaseAndAlarmAreBothHardCapped() {
        assertFalse(HobgoblinJourneyRules.shouldDisengage(HobgoblinJourneyRules.DISENGAGE_RADIUS));
        assertTrue(HobgoblinJourneyRules.shouldDisengage(HobgoblinJourneyRules.DISENGAGE_RADIUS + 1));
        assertEquals(0, HobgoblinJourneyRules.alarmRecipients(-3));
        assertEquals(2, HobgoblinJourneyRules.alarmRecipients(2));
        assertEquals(HobgoblinJourneyRules.MAX_ALARM_MEMBERS, HobgoblinJourneyRules.alarmRecipients(99));
    }

    // ---------------------------------------------------------------- navigation and claims

    @Test
    void routeFailuresAccumulateResetAndArmTheDeclaredBackoff() {
        assertEquals(0, HobgoblinJourneyRules.nextRouteFailure(2, RouteFailure.NONE));
        assertEquals(0, HobgoblinJourneyRules.nextRouteFailure(2, null));
        assertEquals(1, HobgoblinJourneyRules.nextRouteFailure(0, RouteFailure.NO_PATH));
        assertEquals(HobgoblinJourneyRules.MAX_ROUTE_FAILURES,
            HobgoblinJourneyRules.nextRouteFailure(99, RouteFailure.STUCK));
        assertEquals(HobgoblinJourneyRules.ROUTE_RETRY_TICKS, HobgoblinJourneyRules.backoffTicks(1));
        assertEquals(HobgoblinJourneyRules.ROUTE_BACKOFF_TICKS,
            HobgoblinJourneyRules.backoffTicks(HobgoblinJourneyRules.MAX_ROUTE_FAILURES));
        assertTrue(HobgoblinJourneyRules.shouldBackOff(HobgoblinJourneyRules.MAX_ROUTE_FAILURES));
        assertFalse(HobgoblinJourneyRules.shouldBackOff(0));
    }

    @Test
    void onlyBoundedLocalLegsAreEverRequested() {
        assertTrue(HobgoblinJourneyRules.withinLocalRoute(HobgoblinJourneyRules.MAX_LOCAL_ROUTE_BLOCKS));
        assertFalse(HobgoblinJourneyRules.withinLocalRoute(HobgoblinJourneyRules.MAX_LOCAL_ROUTE_BLOCKS + 1));
    }

    @Test
    void claimsRefuseCapContentionAndDoubleHolding() {
        assertTrue(HobgoblinJourneyRules.canGrantClaim(0, false, false));
        assertFalse(HobgoblinJourneyRules.canGrantClaim(
            HobgoblinJourneyRules.MAX_CLAIM_RECORDS, false, false));
        assertFalse(HobgoblinJourneyRules.canGrantClaim(0, true, false));
        assertFalse(HobgoblinJourneyRules.canGrantClaim(0, false, true));
        assertEquals(HobgoblinJourneyRules.CLAIM_LEASE_TICKS, HobgoblinJourneyRules.leaseTicks());
    }

    @Test
    void caravanAndCampKeysAreStableRegionalAndDistinct() {
        assertEquals(HobgoblinJourneyRules.caravanKey(0, 0), HobgoblinJourneyRules.caravanKey(127, 127));
        assertTrue(HobgoblinJourneyRules.caravanKey(0, 0) != HobgoblinJourneyRules.caravanKey(128, 0));
        assertTrue(HobgoblinJourneyRules.caravanKey(0, 0) != HobgoblinJourneyRules.caravanKey(-1, 0));
        final long caravan = HobgoblinJourneyRules.caravanKey(0, 0);
        assertTrue(caravan != HobgoblinJourneyRules.campKey(caravan));
        assertEquals(HobgoblinJourneyRules.campKey(caravan), HobgoblinJourneyRules.campKey(caravan));
    }

    // ---------------------------------------------------------------- merchant

    @Test
    void merchantLevelAndRestockBoundsAreExact() {
        assertEquals(HobgoblinJourneyRules.MIN_MERCHANT_LEVEL, HobgoblinJourneyRules.clampMerchantLevel(-4));
        assertEquals(HobgoblinJourneyRules.MAX_MERCHANT_LEVEL, HobgoblinJourneyRules.clampMerchantLevel(99));
        assertEquals(1, HobgoblinJourneyRules.levelForXp(0));
        assertEquals(2, HobgoblinJourneyRules.levelForXp(10));
        assertEquals(3, HobgoblinJourneyRules.levelForXp(75));
        assertEquals(5, HobgoblinJourneyRules.levelForXp(100_000));
        assertTrue(HobgoblinJourneyRules.canRestock(0, 0, true, true));
        assertFalse(HobgoblinJourneyRules.canRestock(0, 0, false, true), "nothing to restock");
        assertFalse(HobgoblinJourneyRules.canRestock(0, 0, true, false), "unsafe moment");
        assertFalse(HobgoblinJourneyRules.canRestock(0, 500, true, true), "spacing holds");
        assertFalse(HobgoblinJourneyRules.canRestock(
            HobgoblinJourneyRules.MAX_RESTOCKS_PER_DAY, 0, true, true), "daily quota");
    }

    // ---------------------------------------------------------------- charged scan coverage

    @Test
    void aScanNeverExceedsItsDeclaredReadBudget() {
        for (final ScanCase scan : SCANS) {
            final List<BlockPos> offsets = HobgoblinJourneyRuntime.envelope(scan.horizontal, scan.vertical);
            for (int cursor = 0; cursor < 40; cursor++) {
                assertTrue(
                    HobgoblinJourneyRuntime.scanWindow(offsets, scan.readCap, cursor * 13).size()
                        <= scan.readCap,
                    scan.name + " exceeded its budget"
                );
            }
        }
    }

    @Test
    void everyScanEvaluatesTheTravelerOwnLevelAndItsImmediateNeighbourhood() {
        for (final ScanCase scan : SCANS) {
            final List<BlockPos> offsets = HobgoblinJourneyRuntime.envelope(scan.horizontal, scan.vertical);
            for (int rotation = 0; rotation < 25; rotation++) {
                final Set<BlockPos> window = new HashSet<>(
                    HobgoblinJourneyRuntime.scanWindow(offsets, scan.readCap, rotation * 37)
                );
                assertTrue(window.contains(new BlockPos(0, 0, 0)), scan.name + " skipped its own block");
                assertTrue(window.contains(new BlockPos(1, 0, 0)), scan.name + " skipped +x");
                assertTrue(window.contains(new BlockPos(-1, 0, 0)), scan.name + " skipped -x");
                assertTrue(window.contains(new BlockPos(0, 0, 1)), scan.name + " skipped +z");
                assertTrue(window.contains(new BlockPos(0, 0, -1)), scan.name + " skipped -z");
            }
        }
    }

    @Test
    void theWholeEnvelopeIncludingTheFarQuadrantIsCoveredWithinABoundedNumberOfScans() {
        for (final ScanCase scan : SCANS) {
            final List<BlockPos> offsets = HobgoblinJourneyRuntime.envelope(scan.horizontal, scan.vertical);
            final int anchor = HobgoblinJourneyRuntime.anchorSize(offsets.size(), scan.readCap);
            final int page = HobgoblinJourneyRuntime.pageSize(offsets.size(), scan.readCap);
            final int tail = offsets.size() - anchor;
            final int scansToCover = page == 0 ? 1 : (tail + page - 1) / page;
            assertTrue(scansToCover <= 40, scan.name + " needs too many scans: " + scansToCover);
            final Set<BlockPos> union = new HashSet<>();
            int cursor = 17;
            for (int pass = 0; pass < scansToCover; pass++) {
                union.addAll(HobgoblinJourneyRuntime.scanWindow(offsets, scan.readCap, cursor));
                cursor = tail == 0 ? 0 : Math.floorMod(cursor + page, tail);
            }
            assertEquals(offsets.size(), union.size(), scan.name + " never covered its whole envelope");
            assertTrue(union.contains(new BlockPos(scan.horizontal, scan.vertical, scan.horizontal)),
                scan.name + " never reached its far +x+y+z corner");
            assertTrue(union.contains(new BlockPos(-scan.horizontal, -scan.vertical, -scan.horizontal)),
                scan.name + " never reached its far -x-y-z corner");
        }
    }

    @Test
    void theEnvelopeIsCentreOutAndDeterministic() {
        final List<BlockPos> offsets = HobgoblinJourneyRuntime.envelope(5, 2);
        assertEquals(new BlockPos(0, 0, 0), offsets.get(0));
        assertEquals(11 * 11 * 5, offsets.size());
        final List<BlockPos> again = HobgoblinJourneyRuntime.envelope(5, 2);
        assertEquals(offsets, again, "the cached envelope must be identical on every call");
        final List<Integer> distances = new ArrayList<>();
        offsets.forEach(offset -> distances.add(
            offset.getX() * offset.getX() + offset.getY() * offset.getY() + offset.getZ() * offset.getZ()
        ));
        for (int index = 1; index < distances.size(); index++) {
            assertTrue(distances.get(index) >= distances.get(index - 1),
                "the envelope must be ordered centre-out");
        }
    }

    /** The exact live scan shapes, mirrored from the runtime's four charged callers. */
    private record ScanCase(String name, int horizontal, int vertical, int readCap) {
    }

    private static final List<ScanCase> SCANS = List.of(
        new ScanCase("mining", 5, 2, HobgoblinJourneyRules.MAX_MINING_BLOCK_READS),
        new ScanCase("deposit", 6, 3, HobgoblinJourneyRules.MAX_WORK_BLOCK_READS),
        new ScanCase("camp", 6, 2, HobgoblinJourneyRules.MAX_CAMP_BLOCK_READS),
        new ScanCase("flower", 4, 1, HobgoblinJourneyRules.MAX_CHILD_BLOCK_READS)
    );
}
