package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.CombatRole;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.Intent;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.Period;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.RelationEvent;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.RelationFact;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.Responder;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.RoleAssignment;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.RouteFailure;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.TargetClass;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.WorkAvailability;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Complete truth tables for the pure F10 Goblin decision surface. No Minecraft world is touched. */
final class GoblinEnclaveRulesTest {
    private static final UUID LOW = new UUID(0L, 1L);
    private static final UUID MID = new UUID(0L, 2L);
    private static final UUID HIGH = new UUID(0L, 3L);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        // GoblinProfession names real workstation Blocks, so the enum needs the registries.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---------------------------------------------------------------- identity and cadence

    @Test
    void exactlyOneCreatureKindIsOwned() {
        assertTrue(GoblinEnclaveRules.isExactGoblin(CreatureKind.GOBLIN));
        assertFalse(GoblinEnclaveRules.isExactGoblin(CreatureKind.HOBGOBLIN));
        assertFalse(GoblinEnclaveRules.isExactGoblin(CreatureKind.STONEBROKER));
        assertFalse(GoblinEnclaveRules.isExactGoblin(CreatureKind.FORGEWARDEN));
        assertSame(CreatureKind.GOBLIN, GoblinEnclaveRules.KIND);
    }

    @Test
    void stableOffsetIsNonNegativeBoundedAndDeterministic() {
        final UUID id = UUID.fromString("00000000-0000-0000-8000-000000000000");
        final int first = GoblinEnclaveRules.stableOffset(id, 40);
        assertEquals(first, GoblinEnclaveRules.stableOffset(id, 40));
        assertTrue(first >= 0 && first < 40);
        assertEquals(0, GoblinEnclaveRules.stableOffset(null, 40));
        assertEquals(0, GoblinEnclaveRules.stableOffset(id, 0));
        assertEquals(0, GoblinEnclaveRules.stableOffset(id, -5));
    }

    @Test
    void zeroAndNegativeSentinelsBothReadAsDue() {
        assertTrue(GoblinEnclaveRules.isDue(0));
        assertTrue(GoblinEnclaveRules.isDue(-1));
        assertFalse(GoblinEnclaveRules.isDue(1));
        assertEquals(0, GoblinEnclaveRules.clampRemaining(-7, 100));
        assertEquals(100, GoblinEnclaveRules.clampRemaining(500, 100));
        assertEquals(0, GoblinEnclaveRules.clampRemaining(5, -1));
    }

    @Test
    void farFutureCadenceSentinelStaysBoundedWellBelowLongMax() {
        assertEquals(20_000L, GoblinEnclaveRules.FAR_FUTURE_TICKS);
        assertTrue(GoblinEnclaveRules.FAR_FUTURE_TICKS < Long.MAX_VALUE / 4L);
    }

    @Test
    void scheduleCoversEveryDayTimeAndNormalizesOverflowAndOffsets() {
        assertEquals(Period.DAY, GoblinEnclaveRules.period(0L, 0));
        assertEquals(Period.DAY, GoblinEnclaveRules.period(10_999L, 0));
        assertEquals(Period.DUSK, GoblinEnclaveRules.period(11_000L, 0));
        assertEquals(Period.DUSK, GoblinEnclaveRules.period(12_999L, 0));
        assertEquals(Period.NIGHT, GoblinEnclaveRules.period(13_000L, 0));
        assertEquals(Period.NIGHT, GoblinEnclaveRules.period(21_999L, 0));
        assertEquals(Period.DAWN, GoblinEnclaveRules.period(22_000L, 0));
        assertEquals(Period.DAWN, GoblinEnclaveRules.period(23_999L, 0));
        // A clock that has run for many days and a negative clock both normalize.
        assertEquals(Period.NIGHT, GoblinEnclaveRules.period(24_000L * 91L + 15_000L, 0));
        assertEquals(Period.NIGHT, GoblinEnclaveRules.period(-9_000L, 0));
        // The offset is clamped to the declared maximum and can only shift a boundary case.
        assertEquals(Period.DUSK, GoblinEnclaveRules.period(10_999L, 39));
        assertEquals(Period.DUSK, GoblinEnclaveRules.period(10_999L, 10_000));
    }

    // ---------------------------------------------------------------- spawning and persistence

    @Test
    void naturalSpawnRequiresNightLowLightVillageDistanceAndLocalHeadroom() {
        assertTrue(GoblinEnclaveRules.canSpawnNaturally(true, 7, 48, 7));
        assertFalse(GoblinEnclaveRules.canSpawnNaturally(false, 7, 48, 0));
        assertFalse(GoblinEnclaveRules.canSpawnNaturally(true, 8, 48, 0));
        assertFalse(GoblinEnclaveRules.canSpawnNaturally(true, 7, 47, 0));
        assertFalse(GoblinEnclaveRules.canSpawnNaturally(true, 7, 48, 8));
    }

    @Test
    void persistenceReasonsUseOneFixedPrecedenceAndOtherwiseAllowDespawn() {
        assertEquals(GoblinEnclaveRules.PersistenceReason.ASSAULT_MEMBER,
            GoblinEnclaveRules.persistenceReason(true, true, true).orElseThrow());
        assertEquals(GoblinEnclaveRules.PersistenceReason.CONTRACTED,
            GoblinEnclaveRules.persistenceReason(true, true, false).orElseThrow());
        assertEquals(GoblinEnclaveRules.PersistenceReason.ANCHORED_RESIDENT,
            GoblinEnclaveRules.persistenceReason(true, false, false).orElseThrow());
        assertTrue(GoblinEnclaveRules.persistenceReason(false, false, false).isEmpty());
        assertTrue(GoblinEnclaveRules.mayDespawn(false, false, false));
        assertFalse(GoblinEnclaveRules.mayDespawn(true, false, false));
        assertFalse(GoblinEnclaveRules.mayDespawn(false, true, false));
        assertFalse(GoblinEnclaveRules.mayDespawn(false, false, true));
    }

    @Test
    void enclaveKeysGroupByRegionAndSeparateByKindIncludingNegativeCoordinates() {
        assertEquals(
            GoblinEnclaveRules.enclaveKey(0, 0, CreatureKind.GOBLIN),
            GoblinEnclaveRules.enclaveKey(127, 127, CreatureKind.GOBLIN)
        );
        assertNotEquals(
            GoblinEnclaveRules.enclaveKey(0, 0, CreatureKind.GOBLIN),
            GoblinEnclaveRules.enclaveKey(128, 0, CreatureKind.GOBLIN)
        );
        assertNotEquals(
            GoblinEnclaveRules.enclaveKey(0, 0, CreatureKind.GOBLIN),
            GoblinEnclaveRules.enclaveKey(0, 0, CreatureKind.HOBGOBLIN)
        );
        assertEquals(
            GoblinEnclaveRules.enclaveKey(-1, -1, CreatureKind.GOBLIN),
            GoblinEnclaveRules.enclaveKey(-128, -128, CreatureKind.GOBLIN)
        );
    }

    // ---------------------------------------------------------------- priority and intent

    @Test
    void hazardOutranksAssaultAlarmTradeAndSchedule() {
        assertEquals(Intent.SEEK_SHELTER, GoblinEnclaveRules.selectIntent(false, true, true,
            CombatRole.PRESS, true, Period.NIGHT, false, GoblinProfession.MINER, everything()));
        assertEquals(Intent.ASSAULT, GoblinEnclaveRules.selectIntent(false, false, true,
            CombatRole.PRESS, true, Period.NIGHT, false, GoblinProfession.MINER, everything()));
        assertEquals(Intent.ALARM_PRESS, GoblinEnclaveRules.selectIntent(false, false, false,
            CombatRole.PRESS, true, Period.NIGHT, false, GoblinProfession.MINER, everything()));
        assertEquals(Intent.TRADE_HOLD, GoblinEnclaveRules.selectIntent(false, false, false,
            CombatRole.NONE, true, Period.NIGHT, false, GoblinProfession.MINER, everything()));
    }

    @Test
    void dayAndDawnSendAnExposedAdultToShelterAndLetAShelteredOneRest() {
        assertEquals(Intent.SEEK_SHELTER, GoblinEnclaveRules.selectIntent(false, false, false,
            CombatRole.NONE, false, Period.DAY, false, GoblinProfession.MINER, everything()));
        assertEquals(Intent.IDLE, GoblinEnclaveRules.selectIntent(false, false, false,
            CombatRole.NONE, false, Period.DAY, true, GoblinProfession.MINER, everything()));
        assertEquals(Intent.SEEK_SHELTER, GoblinEnclaveRules.selectIntent(false, false, false,
            CombatRole.NONE, false, Period.DAWN, false, GoblinProfession.MINER, everything()));
        assertEquals(Intent.PATROL, GoblinEnclaveRules.selectIntent(false, false, false,
            CombatRole.NONE, false, Period.DUSK, true, GoblinProfession.MINER, everything()));
    }

    @Test
    void nightWorkFollowsProfessionPreferenceThenTheFixedFallbackChain() {
        assertEquals(Intent.MINE, night(GoblinProfession.MINER, everything()));
        assertEquals(Intent.BUILD_HUT, night(GoblinProfession.SMITH, everything()));
        assertEquals(Intent.FAMILY, night(GoblinProfession.SHAMAN, everything()));
        assertEquals(Intent.GATHER_LOOSE, night(GoblinProfession.PROSPECTOR, everything()));
        // A Miner with no ore falls through its own preference order, then the shared chain.
        final WorkAvailability noOre = new WorkAvailability(false, true, false, false, false,
            false, false, false, false, false);
        assertEquals(Intent.DEPOSIT, night(GoblinProfession.MINER, noOre));
        // With nothing at all available, patrol is always the last non-idle option.
        assertEquals(Intent.PATROL, night(GoblinProfession.MINER, WorkAvailability.none()));
    }

    @Test
    void childrenNeverReceiveAnAdultOrCombatIntent() {
        final Set<Intent> childIntents = Arrays.stream(Period.values())
            .map(period -> GoblinEnclaveRules.selectIntent(true, false, false, CombatRole.PRESS,
                true, period, false, GoblinProfession.MINER, everything()))
            .collect(Collectors.toUnmodifiableSet());
        assertTrue(childIntents.stream()
            .allMatch(intent -> intent.isChildIntent() || intent == Intent.SEEK_SHELTER));
        assertEquals(Intent.SEEK_SHELTER, GoblinEnclaveRules.selectIntent(true, false, true,
            CombatRole.NONE, false, Period.NIGHT, true, GoblinProfession.MINER, everything()));
        assertEquals(Intent.SEEK_SHELTER, GoblinEnclaveRules.selectIntent(true, true, false,
            CombatRole.NONE, false, Period.NIGHT, true, GoblinProfession.MINER, everything()));
    }

    @Test
    void childNightPriorityIsGiftThenDanceThenFlowerThenIdle() {
        assertEquals(Intent.CHILD_GIFT, child(everything()));
        assertEquals(Intent.CHILD_DANCE, child(new WorkAvailability(false, false, false, false,
            false, false, false, true, true, false)));
        assertEquals(Intent.CHILD_FLOWER, child(new WorkAvailability(false, false, false, false,
            false, false, false, true, false, false)));
        assertEquals(Intent.IDLE, child(WorkAvailability.none()));
    }

    @Test
    void commitmentOnlyYieldsToAStrictlyMoreUrgentIntent() {
        assertTrue(GoblinEnclaveRules.interrupts(Intent.MINE, Intent.SEEK_SHELTER));
        assertTrue(GoblinEnclaveRules.interrupts(Intent.TRADE_HOLD, Intent.ALARM_WARD));
        assertTrue(GoblinEnclaveRules.interrupts(Intent.ALARM_WARD, Intent.ASSAULT));
        assertFalse(GoblinEnclaveRules.interrupts(Intent.MINE, Intent.DEPOSIT));
        assertFalse(GoblinEnclaveRules.interrupts(Intent.SEEK_SHELTER, Intent.ASSAULT));
        assertFalse(GoblinEnclaveRules.interrupts(Intent.MINE, Intent.IDLE));
        assertTrue(GoblinEnclaveRules.interrupts(Intent.IDLE, Intent.PATROL));
    }

    @Test
    void nullWorkAndNullProfessionResolveSafelyInsteadOfThrowing() {
        assertEquals(Intent.PATROL, GoblinEnclaveRules.selectIntent(false, false, false, null,
            false, Period.NIGHT, false, null, null));
        assertEquals(GoblinEnclaveRules.preferredJobs(GoblinProfession.PROSPECTOR),
            GoblinEnclaveRules.preferredJobs(null));
    }

    // ---------------------------------------------------------------- hostility

    @Test
    void onlyFiveCandidateClassesAreEverEligibleTargets() {
        final Set<TargetClass> eligible = Arrays.stream(TargetClass.values())
            .filter(candidate -> GoblinEnclaveRules.canTarget(true, candidate, true, true, true,
                false, false))
            .collect(Collectors.toUnmodifiableSet());
        assertEquals(Set.of(TargetClass.HUMAN_VILLAGER, TargetClass.DIRECT_ATTACKER,
            TargetClass.CHILD_ATTACKER, TargetClass.PATRON_ATTACKER, TargetClass.ALARM_COPY),
            eligible);
    }

    @Test
    void everyExclusionRejectsEvenTheMostEligibleCandidateClass() {
        assertFalse(GoblinEnclaveRules.canTarget(false, TargetClass.HUMAN_VILLAGER, true, true,
            true, false, false));
        assertFalse(GoblinEnclaveRules.canTarget(true, TargetClass.HUMAN_VILLAGER, false, true,
            true, false, false));
        assertFalse(GoblinEnclaveRules.canTarget(true, TargetClass.HUMAN_VILLAGER, true, false,
            true, false, false));
        assertFalse(GoblinEnclaveRules.canTarget(true, TargetClass.HUMAN_VILLAGER, true, true,
            false, false, false));
        assertFalse(GoblinEnclaveRules.canTarget(true, TargetClass.HUMAN_VILLAGER, true, true,
            true, true, false));
        assertFalse(GoblinEnclaveRules.canTarget(true, TargetClass.HUMAN_VILLAGER, true, true,
            true, false, true));
        assertFalse(GoblinEnclaveRules.canTarget(true, null, true, true, true, false, false));
    }

    @Test
    void hurtAttributionIsFreshForExactlyFortyTicks() {
        assertTrue(GoblinEnclaveRules.isFreshAttribution(0));
        assertTrue(GoblinEnclaveRules.isFreshAttribution(40));
        assertFalse(GoblinEnclaveRules.isFreshAttribution(41));
        assertFalse(GoblinEnclaveRules.isFreshAttribution(-1));
        assertFalse(GoblinEnclaveRules.isFreshAttribution(Integer.MAX_VALUE));
    }

    // ---------------------------------------------------------------- alarm and roles

    @Test
    void rolesAreUniqueCappedAtFourAndProfessionPreferred() {
        final List<RoleAssignment> assignments = GoblinEnclaveRules.assignRoles(List.of(
            new Responder(LOW, GoblinProfession.MINER, 2, 1.0D),
            new Responder(MID, GoblinProfession.SMITH, 2, 2.0D),
            new Responder(HIGH, GoblinProfession.PROSPECTOR, 2, 3.0D),
            new Responder(new UUID(0L, 4L), GoblinProfession.SHAMAN, 2, 4.0D),
            new Responder(new UUID(0L, 5L), GoblinProfession.MINER, 2, 5.0D)
        ));
        assertEquals(5, assignments.size());
        assertEquals(CombatRole.PRESS, assignments.get(0).role());
        assertEquals(CombatRole.WARDER, assignments.get(1).role());
        assertEquals(CombatRole.HARRIER, assignments.get(2).role());
        assertEquals(CombatRole.RESERVE, assignments.get(3).role());
        assertEquals(CombatRole.NONE, assignments.get(4).role());
        assertEquals(4L, assignments.stream()
            .map(RoleAssignment::role)
            .filter(role -> role != CombatRole.NONE)
            .distinct()
            .count());
    }

    @Test
    void roleAssignmentTiesBreakDeterministicallyOnUrgencyDistanceThenUuid() {
        final List<Responder> responders = List.of(
            new Responder(HIGH, GoblinProfession.MINER, 1, 4.0D),
            new Responder(LOW, GoblinProfession.MINER, 1, 4.0D),
            new Responder(MID, GoblinProfession.MINER, 3, 9.0D)
        );
        final List<RoleAssignment> first = GoblinEnclaveRules.assignRoles(responders);
        assertEquals(first, GoblinEnclaveRules.assignRoles(responders));
        // Highest urgency wins outright, then the lower UUID breaks the equal-distance tie.
        assertEquals(MID, first.get(0).id());
        assertEquals(LOW, first.get(1).id());
        assertEquals(HIGH, first.get(2).id());
        assertTrue(GoblinEnclaveRules.assignRoles(List.of()).isEmpty());
        assertTrue(GoblinEnclaveRules.assignRoles(null).isEmpty());
    }

    @Test
    void everyProfessionHasOneDistinctPreferredRole() {
        assertEquals(CombatRole.PRESS, GoblinEnclaveRules.preferredRole(GoblinProfession.MINER));
        assertEquals(CombatRole.WARDER, GoblinEnclaveRules.preferredRole(GoblinProfession.SMITH));
        assertEquals(CombatRole.HARRIER, GoblinEnclaveRules.preferredRole(GoblinProfession.PROSPECTOR));
        assertEquals(CombatRole.RESERVE, GoblinEnclaveRules.preferredRole(GoblinProfession.SHAMAN));
        assertEquals(4L, Arrays.stream(GoblinProfession.values())
            .map(GoblinEnclaveRules::preferredRole)
            .distinct()
            .count());
    }

    @Test
    void alarmsAreDepthOneCappedAtFourRespondersAndNeverRebroadcast() {
        assertTrue(GoblinEnclaveRules.canRelayAlarm(0));
        assertFalse(GoblinEnclaveRules.canRelayAlarm(1));
        assertFalse(GoblinEnclaveRules.canRelayAlarm(2));
        assertEquals(4, GoblinEnclaveRules.recruitCap(9));
        assertEquals(3, GoblinEnclaveRules.recruitCap(3));
        assertEquals(0, GoblinEnclaveRules.recruitCap(-1));
    }

    @Test
    void retreatUsesHysteresisAndAnImmediateAttackerSuppressesIt() {
        assertTrue(GoblinEnclaveRules.shouldRetreat(false, 0.30F, false, true));
        assertFalse(GoblinEnclaveRules.shouldRetreat(false, 0.31F, false, true));
        assertFalse(GoblinEnclaveRules.shouldRetreat(false, 0.10F, true, true));
        // Once retreating, it holds until half health or the alarm ends.
        assertTrue(GoblinEnclaveRules.shouldRetreat(true, 0.49F, false, true));
        assertFalse(GoblinEnclaveRules.shouldRetreat(true, 0.50F, false, true));
        assertFalse(GoblinEnclaveRules.shouldRetreat(true, 0.10F, false, false));
    }

    @Test
    void reliefReplacesOnlyWoundedAggressiveRoles() {
        assertTrue(GoblinEnclaveRules.needsRelief(0.30F, CombatRole.PRESS));
        assertTrue(GoblinEnclaveRules.needsRelief(0.30F, CombatRole.WARDER));
        assertTrue(GoblinEnclaveRules.needsRelief(0.30F, CombatRole.HARRIER));
        assertFalse(GoblinEnclaveRules.needsRelief(0.30F, CombatRole.RESERVE));
        assertFalse(GoblinEnclaveRules.needsRelief(0.30F, CombatRole.NONE));
        assertFalse(GoblinEnclaveRules.needsRelief(0.31F, CombatRole.PRESS));
    }

    @Test
    void everyCombatRoleMapsToItsOwnAlarmIntent() {
        assertEquals(Intent.ALARM_WARD, CombatRole.WARDER.intent());
        assertEquals(Intent.ALARM_HARRY, CombatRole.HARRIER.intent());
        assertEquals(Intent.ALARM_PRESS, CombatRole.PRESS.intent());
        assertEquals(Intent.ALARM_RESERVE, CombatRole.RESERVE.intent());
        assertEquals(Intent.IDLE, CombatRole.NONE.intent());
        assertTrue(Arrays.stream(Intent.values()).filter(Intent::isAlarmIntent).count() == 4L);
    }

    // ---------------------------------------------------------------- navigation and claims

    @Test
    void threeClassifiedRouteFailuresBackOffAndAnySuccessResetsTheCount() {
        int failures = 0;
        failures = GoblinEnclaveRules.nextRouteFailure(failures, RouteFailure.NO_PATH);
        failures = GoblinEnclaveRules.nextRouteFailure(failures, RouteFailure.UNREACHABLE);
        assertFalse(GoblinEnclaveRules.shouldBackOff(failures));
        assertEquals(0, GoblinEnclaveRules.backoffTicks(failures));
        failures = GoblinEnclaveRules.nextRouteFailure(failures, RouteFailure.STUCK);
        assertTrue(GoblinEnclaveRules.shouldBackOff(failures));
        assertEquals(100, GoblinEnclaveRules.backoffTicks(failures));
        // The counter saturates instead of growing without bound.
        assertEquals(3, GoblinEnclaveRules.nextRouteFailure(failures, RouteFailure.STUCK));
        assertEquals(0, GoblinEnclaveRules.nextRouteFailure(failures, RouteFailure.NONE));
        assertEquals(0, GoblinEnclaveRules.nextRouteFailure(failures, null));
    }

    @Test
    void oneClaimPerGoblinPerWorksiteAndAtMostEightPerEnclave() {
        assertTrue(GoblinEnclaveRules.canGrantClaim(7, false, false));
        assertFalse(GoblinEnclaveRules.canGrantClaim(8, false, false));
        assertFalse(GoblinEnclaveRules.canGrantClaim(0, true, false));
        assertFalse(GoblinEnclaveRules.canGrantClaim(0, false, true));
        assertEquals(200, GoblinEnclaveRules.leaseTicks());
        assertTrue(GoblinEnclaveRules.claimExpired(0));
        assertFalse(GoblinEnclaveRules.claimExpired(1));
    }

    // ---------------------------------------------------------------- construction

    @Test
    void hutCostMatchesTheExactRetainedEighteenDirtAndThreeLogRecipe() {
        assertTrue(GoblinEnclaveRules.canAffordHut(18, 3, 0));
        assertFalse(GoblinEnclaveRules.canAffordHut(17, 3, 0));
        assertFalse(GoblinEnclaveRules.canAffordHut(18, 2, 0));
        // Three logs are worth twelve planks exactly; two logs plus four loose planks are not.
        assertFalse(GoblinEnclaveRules.canAffordHut(18, 2, 4));
        assertTrue(GoblinEnclaveRules.canAffordHut(64, 3, 12));
        assertFalse(GoblinEnclaveRules.canAffordHut(-1, -1, -1));
    }

    @Test
    void structureCapsAndTheSharedEditBudgetHoldTogether() {
        assertTrue(GoblinEnclaveRules.canReserveHut(2, 96));
        assertFalse(GoblinEnclaveRules.canReserveHut(3, 0));
        assertFalse(GoblinEnclaveRules.canReserveHut(0, 97));
        assertTrue(GoblinEnclaveRules.canReserveTunnel(0, 118, 10));
        assertFalse(GoblinEnclaveRules.canReserveTunnel(1, 0, 10));
        assertFalse(GoblinEnclaveRules.canReserveTunnel(0, 0, 3));
        assertFalse(GoblinEnclaveRules.canReserveTunnel(0, 0, 11));
        assertFalse(GoblinEnclaveRules.canReserveTunnel(0, 119, 10));
        assertTrue(GoblinEnclaveRules.canRecordEdit(127, 1));
        assertFalse(GoblinEnclaveRules.canRecordEdit(128, 1));
    }

    @Test
    void aBlockIsEditableOnlyWhenEveryGuardPassesAtOnce() {
        assertTrue(GoblinEnclaveRules.canEditBlock(true, true, true, true, false, false, 1.5F));
        assertFalse(GoblinEnclaveRules.canEditBlock(false, true, true, true, false, false, 1.5F));
        assertFalse(GoblinEnclaveRules.canEditBlock(true, false, true, true, false, false, 1.5F));
        assertFalse(GoblinEnclaveRules.canEditBlock(true, true, false, true, false, false, 1.5F));
        assertFalse(GoblinEnclaveRules.canEditBlock(true, true, true, false, false, false, 1.5F));
        assertFalse(GoblinEnclaveRules.canEditBlock(true, true, true, true, true, false, 1.5F));
        assertFalse(GoblinEnclaveRules.canEditBlock(true, true, true, true, false, true, 1.5F));
        assertFalse(GoblinEnclaveRules.canEditBlock(true, true, true, true, false, false, -1.0F));
    }

    // ---------------------------------------------------------------- family

    @Test
    void conceptionRequiresEveryDeclaredConditionSimultaneously() {
        assertTrue(GoblinEnclaveRules.canConceive(true, true, true, 7, true, 12, 12, true));
        assertFalse(GoblinEnclaveRules.canConceive(false, true, true, 2, true, 24, 24, true));
        assertFalse(GoblinEnclaveRules.canConceive(true, false, true, 2, true, 24, 24, true));
        assertFalse(GoblinEnclaveRules.canConceive(true, true, false, 2, true, 24, 24, true));
        assertFalse(GoblinEnclaveRules.canConceive(true, true, true, 8, true, 24, 24, true));
        assertFalse(GoblinEnclaveRules.canConceive(true, true, true, 2, false, 24, 24, true));
        assertFalse(GoblinEnclaveRules.canConceive(true, true, true, 2, true, 11, 24, true));
        assertFalse(GoblinEnclaveRules.canConceive(true, true, true, 2, true, 24, 11, true));
        assertFalse(GoblinEnclaveRules.canConceive(true, true, true, 2, true, 24, 24, false));
    }

    @Test
    void childProfessionIsDeterministicFromTheLowerParentUuid() {
        assertEquals(GoblinProfession.MINER, GoblinEnclaveRules.childProfession(
            LOW, GoblinProfession.MINER, HIGH, GoblinProfession.SHAMAN));
        assertEquals(GoblinProfession.SHAMAN, GoblinEnclaveRules.childProfession(
            HIGH, GoblinProfession.MINER, LOW, GoblinProfession.SHAMAN));
        // Order of the arguments cannot change the outcome for the same pair.
        assertEquals(
            GoblinEnclaveRules.childProfession(LOW, GoblinProfession.MINER, HIGH, GoblinProfession.SHAMAN),
            GoblinEnclaveRules.childProfession(HIGH, GoblinProfession.SHAMAN, LOW, GoblinProfession.MINER)
        );
        assertEquals(GoblinProfession.SMITH, GoblinEnclaveRules.childProfession(
            null, GoblinProfession.SMITH, LOW, GoblinProfession.MINER));
        assertEquals(GoblinProfession.PROSPECTOR, GoblinEnclaveRules.childProfession(
            LOW, null, HIGH, null));
    }

    @Test
    void foodPointsClampAndGiftAndDanceObeyTheirDeclaredBounds() {
        assertEquals(0, GoblinEnclaveRules.clampFoodPoints(-5));
        assertEquals(24, GoblinEnclaveRules.clampFoodPoints(99));
        assertFalse(GoblinEnclaveRules.canDance(1));
        assertTrue(GoblinEnclaveRules.canDance(2));
        assertTrue(GoblinEnclaveRules.canDance(4));
        assertFalse(GoblinEnclaveRules.canDance(5));
        assertTrue(GoblinEnclaveRules.giftReady(true, 0));
        assertFalse(GoblinEnclaveRules.giftReady(true, 1));
        assertFalse(GoblinEnclaveRules.giftReady(false, 0));
    }

    // ---------------------------------------------------------------- relations

    @Test
    void relationDeltasAreExplicitClampedAndSaturating() {
        assertEquals(5, GoblinEnclaveRules.relationDelta(RelationEvent.TRADE_COMPLETED));
        assertEquals(10, GoblinEnclaveRules.relationDelta(RelationEvent.CONTRACT_ACCEPTED));
        assertEquals(3, GoblinEnclaveRules.relationDelta(RelationEvent.GIFT_RECEIVED));
        assertEquals(-20, GoblinEnclaveRules.relationDelta(RelationEvent.DIRECT_ATTACK));
        assertEquals(-40, GoblinEnclaveRules.relationDelta(RelationEvent.MEMBER_KILLED));
        assertEquals(100, GoblinEnclaveRules.applyRelation(95, RelationEvent.CONTRACT_ACCEPTED));
        assertEquals(-100, GoblinEnclaveRules.applyRelation(-95, RelationEvent.MEMBER_KILLED));
        assertEquals(100, GoblinEnclaveRules.clampRelation(1_000));
        assertEquals(-100, GoblinEnclaveRules.clampRelation(-1_000));
    }

    @Test
    void relationEvictionIsOldestThenLeastExtremeThenLowestUuid() {
        assertTrue(GoblinEnclaveRules.relationToEvict(null).isEmpty());
        assertTrue(GoblinEnclaveRules.relationToEvict(List.of()).isEmpty());
        final List<RelationFact> full = List.of(
            new RelationFact(LOW, 90, 10, 100),
            new RelationFact(MID, 5, 500, 100),
            new RelationFact(HIGH, 80, 500, 100),
            new RelationFact(new UUID(0L, 4L), 1, 20, 100),
            new RelationFact(new UUID(0L, 5L), 2, 20, 100),
            new RelationFact(new UUID(0L, 6L), 3, 20, 100),
            new RelationFact(new UUID(0L, 7L), 4, 20, 100),
            new RelationFact(new UUID(0L, 8L), 6, 20, 100)
        );
        // Both MID and HIGH are the oldest; the least extreme score decides.
        assertEquals(MID, GoblinEnclaveRules.relationToEvict(full).orElseThrow());
        assertTrue(GoblinEnclaveRules.relationToEvict(full.subList(0, 7)).isEmpty());
    }

    @Test
    void relationFactsNormalizeTheirOwnFieldsAndReportExpiry() {
        final RelationFact fact = new RelationFact(null, 500, -20, -3);
        assertEquals(new UUID(0L, 0L), fact.id());
        assertEquals(100, fact.score());
        assertEquals(0, fact.lastInteractionAgeTicks());
        assertEquals(0, fact.remainingTicks());
        assertTrue(fact.expired());
        assertFalse(new RelationFact(LOW, 0, 0, 5).expired());
    }

    // ---------------------------------------------------------------- merchant

    @Test
    void merchantLevelsFollowTheDeclaredExperienceThresholds() {
        assertEquals(1, GoblinEnclaveRules.levelForXp(0));
        assertEquals(1, GoblinEnclaveRules.levelForXp(9));
        assertEquals(2, GoblinEnclaveRules.levelForXp(10));
        assertEquals(3, GoblinEnclaveRules.levelForXp(70));
        assertEquals(4, GoblinEnclaveRules.levelForXp(150));
        assertEquals(5, GoblinEnclaveRules.levelForXp(250));
        assertEquals(5, GoblinEnclaveRules.levelForXp(999_999));
        assertEquals(1, GoblinEnclaveRules.levelForXp(-500));
        assertEquals(1, GoblinEnclaveRules.clampMerchantLevel(0));
        assertEquals(5, GoblinEnclaveRules.clampMerchantLevel(9));
    }

    @Test
    void restockIsCappedAtTwoPerDayAndSpacedAfterTheFirst() {
        assertTrue(GoblinEnclaveRules.canRestock(0, 2_400, true, true));
        assertFalse(GoblinEnclaveRules.canRestock(1, 1, true, true));
        assertTrue(GoblinEnclaveRules.canRestock(1, 0, true, true));
        assertFalse(GoblinEnclaveRules.canRestock(2, 0, true, true));
        assertFalse(GoblinEnclaveRules.canRestock(0, 0, false, true));
        assertFalse(GoblinEnclaveRules.canRestock(0, 0, true, false));
    }

    @Test
    void offerSeedsAreStablePerGoblinProfessionAndLevel() {
        final long seed = GoblinEnclaveRules.offerSeed(LOW, GoblinProfession.MINER, 1);
        assertEquals(seed, GoblinEnclaveRules.offerSeed(LOW, GoblinProfession.MINER, 1));
        assertNotEquals(seed, GoblinEnclaveRules.offerSeed(MID, GoblinProfession.MINER, 1));
        assertNotEquals(seed, GoblinEnclaveRules.offerSeed(LOW, GoblinProfession.SMITH, 1));
        assertNotEquals(seed, GoblinEnclaveRules.offerSeed(LOW, GoblinProfession.MINER, 2));
        // A clamped level and a null profession both resolve rather than throwing.
        assertEquals(seed, GoblinEnclaveRules.offerSeed(LOW, GoblinProfession.MINER, 0));
        assertEquals(GoblinEnclaveRules.offerSeed(LOW, GoblinProfession.PROSPECTOR, 1),
            GoblinEnclaveRules.offerSeed(LOW, null, 1));
    }

    // ---------------------------------------------------------------- assault

    @Test
    void assaultSuspendsEveryEnclaveInitiatedJobAndNothingElse() {
        final Set<Intent> suspended = Arrays.stream(Intent.values())
            .filter(GoblinEnclaveRules::assaultSuspends)
            .collect(Collectors.toUnmodifiableSet());
        assertEquals(Set.of(Intent.FAMILY, Intent.GATHER_LOOSE, Intent.GATHER_LOG, Intent.BUILD_HUT,
            Intent.DIG_TUNNEL, Intent.MINE, Intent.DEPOSIT, Intent.TRADE_HOLD, Intent.CHILD_FLOWER,
            Intent.CHILD_DANCE, Intent.CHILD_GIFT), suspended);
        assertFalse(GoblinEnclaveRules.assaultSuspends(Intent.ASSAULT));
        assertFalse(GoblinEnclaveRules.assaultSuspends(Intent.SEEK_SHELTER));
    }

    @Test
    void exactlyTheDeclaredIntentsEditTheWorld() {
        assertEquals(Set.of(Intent.BUILD_HUT, Intent.DIG_TUNNEL, Intent.MINE, Intent.GATHER_LOG,
            Intent.CHILD_FLOWER),
            Arrays.stream(Intent.values()).filter(Intent::editsWorld)
                .collect(Collectors.toUnmodifiableSet()));
    }

    // ---------------------------------------------------------------- helpers

    private static WorkAvailability everything() {
        return new WorkAvailability(true, true, true, true, true, true, true, true, true, true);
    }

    private static Intent night(final GoblinProfession profession, final WorkAvailability work) {
        return GoblinEnclaveRules.selectIntent(false, false, false, CombatRole.NONE, false,
            Period.NIGHT, false, profession, work);
    }

    private static Intent child(final WorkAvailability work) {
        return GoblinEnclaveRules.selectIntent(true, false, false, CombatRole.NONE, false,
            Period.NIGHT, false, GoblinProfession.MINER, work);
    }
}
