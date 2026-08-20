package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.CampPhase;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.ContractEnd;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.ContractKind;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.Mode;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.RelationFact;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.RouteFailure;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Defaults, coupled invariants, the reconcile-shape contract, round trip, and 1.4 migration. */
final class HobgoblinJourneyStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final UUID CONTRACTOR = new UUID(7L, 11L);
    private static final UUID AGGRESSOR = new UUID(13L, 17L);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---------------------------------------------------------------- defaults

    @Test
    void anEmptyStateIsASafeSolitaryTraveler() {
        final HobgoblinJourneyState state = HobgoblinJourneyState.empty();
        assertEquals(HobgoblinJourneyState.SCHEMA_VERSION, state.schemaVersion());
        assertEquals(GoblinProfession.FALLBACK, state.profession());
        assertEquals(Mode.IDLE, state.mode());
        assertFalse(state.contract().active());
        assertFalse(state.caravan().present());
        assertFalse(state.camp().present());
        assertFalse(state.job().holdsClaim());
        assertFalse(state.combat().remembersAggressor());
        assertTrue(state.relations().isEmpty());
        assertEquals(HobgoblinJourneyRules.MIN_MERCHANT_LEVEL, state.merchant().level());
    }

    @Test
    void theSchemaVersionIsAlwaysNormalizedAndEveryComponentIsRequired() {
        final HobgoblinJourneyState state = new HobgoblinJourneyState(
            99, null, HobgoblinJourneyState.Merchant.initial(), null,
            HobgoblinJourneyState.Contract.none(), HobgoblinJourneyState.Caravan.none(),
            HobgoblinJourneyState.Camp.none(), HobgoblinJourneyState.Job.none(),
            HobgoblinJourneyState.Combat.none(), null, HobgoblinJourneyState.Cadence.none(),
            -100, 10_000_000
        );
        assertEquals(HobgoblinJourneyState.SCHEMA_VERSION, state.schemaVersion());
        assertEquals(GoblinProfession.FALLBACK, state.profession());
        assertEquals(Mode.IDLE, state.mode());
        assertEquals(0, state.childGiftCooldownTicks());
        assertEquals(HobgoblinJourneyRules.BIRTH_COOLDOWN_TICKS, state.birthCooldownTicks());
    }

    @Test
    void aPopulatedStateEncodesBelowTheDeclaredByteCeiling() {
        HobgoblinJourneyState state = HobgoblinJourneyState.empty()
            .withProfession(GoblinProfession.SMITH)
            .withMerchant(HobgoblinJourneyState.Merchant.initial().withXp(250))
            .withMode(Mode.CAMP_BUILD)
            .withContract(HobgoblinJourneyState.Contract.accepted(
                CONTRACTOR, ContractKind.MINING, Optional.of(new BlockPos(9, 60, -9))))
            .withCaravan(HobgoblinJourneyState.Caravan.none()
                .withKey(1234L)
                .withLeader(Optional.of(CONTRACTOR))
                .withWaypoint(new BlockPos(20, 64, 20), OVERWORLD, 400)
                .withRegroup(200))
            .withCamp(HobgoblinJourneyState.Camp.at(4321L, CampPhase.ACTIVE))
            .withJob(new HobgoblinJourneyState.Job(
                Optional.of(AGGRESSOR), Optional.of(new BlockPos(1, 2, 3)), Optional.of(OVERWORLD), 200))
            .withCombat(HobgoblinJourneyState.Combat.aggressor(AGGRESSOR))
            .withCadence(new HobgoblinJourneyState.Cadence(RouteFailure.STUCK, 3, 100, true, 2))
            .withChildGiftCooldown(12_000)
            .withBirthCooldown(70_000);
        for (int index = 0; index < HobgoblinJourneyRules.MAX_RELATION_FACTS; index++) {
            state = state.withRelation(new UUID(index, index), RelationFact.values()[index]);
        }
        assertEquals(HobgoblinJourneyRules.MAX_RELATION_FACTS, state.relations().size());
        assertTrue(encode(state.write()).length <= HobgoblinJourneyRules.MAX_STATE_BYTES,
            "state grew past its declared ceiling: " + encode(state.write()).length);
    }

    // ---------------------------------------------------------------- coupled invariants

    @Test
    void anAgreementWithoutAContractorHasNoKindTargetClockOrUnits() {
        final HobgoblinJourneyState.Contract contract = new HobgoblinJourneyState.Contract(
            Optional.empty(), ContractKind.MINING, Optional.of(BlockPos.ZERO), 5_000, 4, ContractEnd.ACTIVE
        );
        assertEquals(ContractKind.NONE, contract.kind());
        assertTrue(contract.target().isEmpty());
        assertEquals(0, contract.remainingTicks());
        assertEquals(0, contract.completedUnits());
        assertFalse(contract.active());
    }

    @Test
    void aRouteClockIsMeaninglessWithoutItsWaypointAndALeaderWithoutItsCaravan() {
        final HobgoblinJourneyState.Caravan noWaypoint = new HobgoblinJourneyState.Caravan(
            Optional.of(9L), Optional.of(CONTRACTOR), Optional.empty(), Optional.of(OVERWORLD), 400, 100
        );
        assertTrue(noWaypoint.waypoint().isEmpty());
        assertTrue(noWaypoint.dimension().isEmpty());
        assertEquals(0, noWaypoint.routeRemainingTicks());
        final HobgoblinJourneyState.Caravan noCaravan = new HobgoblinJourneyState.Caravan(
            Optional.empty(), Optional.of(CONTRACTOR), Optional.of(BlockPos.ZERO),
            Optional.of(OVERWORLD), 400, 100
        );
        assertTrue(noCaravan.leader().isEmpty());
        assertEquals(0, noCaravan.regroupRemainingTicks());
        assertFalse(noCaravan.present());
    }

    @Test
    void aPhaseWithoutACampKeyNamesNothing() {
        final HobgoblinJourneyState.Camp camp = new HobgoblinJourneyState.Camp(
            Optional.empty(), CampPhase.ACTIVE
        );
        assertEquals(CampPhase.NONE, camp.phase());
        assertFalse(camp.present());
        assertTrue(HobgoblinJourneyState.Camp.at(5L, CampPhase.COMMIT).present());
    }

    // ---------------------------------------------------------------- reconcile shape

    @Test
    void anExhaustedJobLeaseIsReportedNotSilentlyEndedByTheConstructor() {
        final HobgoblinJourneyState.Job job = new HobgoblinJourneyState.Job(
            Optional.of(AGGRESSOR), Optional.of(BlockPos.ZERO), Optional.of(OVERWORLD), 0
        );
        assertTrue(job.holdsClaim(),
            "the constructor must not release the claim a tick branch owns releasing");
        assertTrue(job.leaseExpired());
        assertFalse(new HobgoblinJourneyState.Job(
            Optional.of(AGGRESSOR), Optional.of(BlockPos.ZERO), Optional.of(OVERWORLD), 5
        ).leaseExpired());
    }

    @Test
    void anExpiredAgreementIsReportedNotSilentlyEndedByTheConstructor() {
        final HobgoblinJourneyState.Contract contract = new HobgoblinJourneyState.Contract(
            Optional.of(CONTRACTOR), ContractKind.GATHER, Optional.empty(), 0, 2, ContractEnd.ACTIVE
        );
        assertTrue(contract.contractor().isPresent(),
            "the constructor must not drop the contractor a tick branch owns releasing");
        assertEquals(ContractEnd.ACTIVE, contract.end());
        assertTrue(contract.expired());
        assertEquals(2, contract.completedUnits());
    }

    @Test
    void aLapsedAggressorIsReportedNotSilentlyForgottenByTheConstructor() {
        final HobgoblinJourneyState.Combat combat = new HobgoblinJourneyState.Combat(
            Optional.of(AGGRESSOR), 0, true
        );
        assertTrue(combat.aggressor().isPresent(),
            "the constructor must not clear the target a tick branch owns clearing");
        assertTrue(combat.aggressorLapsed());
        assertFalse(combat.remembersAggressor());
        assertTrue(HobgoblinJourneyState.Combat.aggressor(AGGRESSOR).remembersAggressor());
    }

    @Test
    void unitExhaustionAndTerminationAreExplicitOperations() {
        HobgoblinJourneyState.Contract contract =
            HobgoblinJourneyState.Contract.accepted(CONTRACTOR, ContractKind.GATHER, Optional.empty());
        for (int index = 0; index < HobgoblinJourneyRules.MAX_CONTRACT_UNITS; index++) {
            assertFalse(contract.unitsExhausted());
            contract = contract.withUnit();
        }
        assertTrue(contract.unitsExhausted());
        final HobgoblinJourneyState.Contract ended = contract.ended(ContractEnd.COMPLETED);
        assertEquals(ContractEnd.COMPLETED, ended.end());
        assertTrue(ended.contractor().isEmpty());
        assertFalse(ended.active());
        assertEquals(ContractEnd.INVALID, contract.ended(null).end());
    }

    // ---------------------------------------------------------------- relations

    @Test
    void relationsCollapseByPlayerAndKindAndNeverExceedTheCap() {
        HobgoblinJourneyState state = HobgoblinJourneyState.empty();
        state = state.withRelation(CONTRACTOR, RelationFact.FAIR_TRADE);
        state = state.withRelation(CONTRACTOR, RelationFact.FAIR_TRADE);
        assertEquals(1, state.relations().size(), "the same fact refreshes rather than duplicating");
        for (int index = 0; index < 20; index++) {
            state = state.withRelation(new UUID(index, index), RelationFact.values()[index % 8]);
        }
        assertTrue(state.relations().size() <= HobgoblinJourneyRules.MAX_RELATION_FACTS);
        assertEquals(state, state.withRelation(null, RelationFact.AID));
        assertEquals(state, state.withRelation(CONTRACTOR, null));
    }

    @Test
    void theScoreCountsOnlyLiveFactsForThatExactPlayer() {
        final HobgoblinJourneyState state = HobgoblinJourneyState.empty()
            .withRelation(CONTRACTOR, RelationFact.WORK_COMPLETED)
            .withRelation(AGGRESSOR, RelationFact.ATTACK);
        assertEquals(2, state.relationScore(CONTRACTOR));
        assertEquals(-3, state.relationScore(AGGRESSOR));
        assertEquals(0, state.relationScore(new UUID(99L, 99L)));
        assertEquals(0, state.relationScore(null));
        final HobgoblinJourneyState lapsed = state.withRelations(List.of(
            new HobgoblinJourneyState.Relation(CONTRACTOR, RelationFact.WORK_COMPLETED, 0)
        ));
        assertEquals(0, lapsed.relationScore(CONTRACTOR),
            "an expired fact stops counting even before the tick branch drops it");
    }

    // ---------------------------------------------------------------- persistence

    @Test
    void aFullStateSurvivesAnExactRoundTrip() {
        final HobgoblinJourneyState original = HobgoblinJourneyState.empty()
            .withProfession(GoblinProfession.SHAMAN)
            .withMerchant(HobgoblinJourneyState.Merchant.initial().withXp(80))
            .withMode(Mode.CAMP_REST)
            .withContract(HobgoblinJourneyState.Contract.accepted(
                CONTRACTOR, ContractKind.MINING, Optional.of(new BlockPos(3, 4, 5))))
            .withCaravan(HobgoblinJourneyState.Caravan.none()
                .withKey(77L)
                .withLeader(Optional.of(CONTRACTOR))
                .withWaypoint(new BlockPos(8, 9, 10), OVERWORLD, 300))
            .withCamp(HobgoblinJourneyState.Camp.at(88L, CampPhase.ACTIVE))
            .withCombat(HobgoblinJourneyState.Combat.aggressor(AGGRESSOR))
            .withRelation(CONTRACTOR, RelationFact.FAIR_TRADE)
            .withCadence(new HobgoblinJourneyState.Cadence(RouteFailure.NO_PATH, 2, 40, false, 1))
            .withChildGiftCooldown(600)
            .withBirthCooldown(9_000);
        final HobgoblinJourneyState restored = HobgoblinJourneyState.read(original.write(), OVERWORLD);
        assertEquals(GoblinProfession.SHAMAN, restored.profession());
        assertEquals(original.merchant(), restored.merchant());
        assertEquals(CONTRACTOR, restored.contract().contractor().orElseThrow());
        assertEquals(ContractKind.MINING, restored.contract().kind());
        assertEquals(new BlockPos(3, 4, 5), restored.contract().target().orElseThrow());
        assertEquals(77L, restored.caravan().key().orElseThrow());
        assertEquals(new BlockPos(8, 9, 10), restored.caravan().waypoint().orElseThrow());
        assertEquals(88L, restored.camp().key().orElseThrow());
        assertEquals(CampPhase.ACTIVE, restored.camp().phase());
        assertEquals(AGGRESSOR, restored.combat().aggressor().orElseThrow());
        assertEquals(1, restored.relations().size());
        assertEquals(RouteFailure.NO_PATH, restored.cadence().lastFailure());
        assertEquals(1, restored.cadence().blockedExits());
        assertEquals(600, restored.childGiftCooldownTicks());
        assertEquals(9_000, restored.birthCooldownTicks());
    }

    @Test
    void aCommittedTransactionAndModeNeverSurviveAnUnload() {
        final HobgoblinJourneyState original = HobgoblinJourneyState.empty()
            .withMode(Mode.CAMP_BUILD)
            .withJob(new HobgoblinJourneyState.Job(
                Optional.of(AGGRESSOR), Optional.of(BlockPos.ZERO), Optional.of(OVERWORLD), 200));
        final HobgoblinJourneyState restored = HobgoblinJourneyState.read(original.write(), OVERWORLD);
        assertEquals(Mode.IDLE, restored.mode());
        assertFalse(restored.job().holdsClaim());
    }

    @Test
    void aCrossDimensionWaypointIsDroppedWhileTheCaravanIdentitySurvives() {
        final HobgoblinJourneyState original = HobgoblinJourneyState.empty()
            .withCaravan(HobgoblinJourneyState.Caravan.none()
                .withKey(42L)
                .withWaypoint(new BlockPos(5, 6, 7), OVERWORLD, 200));
        final HobgoblinJourneyState restored = HobgoblinJourneyState.read(original.write(), NETHER);
        assertEquals(42L, restored.caravan().key().orElseThrow());
        assertTrue(restored.caravan().waypoint().isEmpty());
    }

    @Test
    void aMissingCorruptOrUnknownSchemaResetsToASafeSolitaryTraveler() {
        assertEquals(HobgoblinJourneyState.empty(), HobgoblinJourneyState.read(null, OVERWORLD));
        assertEquals(HobgoblinJourneyState.empty(), HobgoblinJourneyState.read(new CompoundTag(), OVERWORLD));
        final CompoundTag future = HobgoblinJourneyState.empty().write();
        future.putInt("Version", HobgoblinJourneyState.SCHEMA_VERSION + 7);
        assertEquals(HobgoblinJourneyState.empty(), HobgoblinJourneyState.read(future, OVERWORLD));
    }

    @Test
    void hostileFieldValuesAreNormalizedRatherThanTrusted() {
        final CompoundTag hostile = HobgoblinJourneyState.empty().write();
        hostile.putString("Profession", "not-a-profession");
        hostile.putString("ContractKind", "sabotage");
        hostile.putString("CampPhase", "nonsense");
        hostile.putString("RouteFail", "nonsense");
        hostile.putString("Contractor", "not-a-uuid");
        hostile.putString("Aggressor", "");
        hostile.putInt("ContractLeft", Integer.MAX_VALUE);
        hostile.putInt("GiftCooldown", Integer.MIN_VALUE);
        hostile.putInt("BlockedExits", 9_999);
        final HobgoblinJourneyState restored = HobgoblinJourneyState.read(hostile, OVERWORLD);
        assertEquals(GoblinProfession.FALLBACK, restored.profession());
        assertEquals(ContractKind.NONE, restored.contract().kind());
        assertEquals(CampPhase.NONE, restored.camp().phase());
        assertEquals(RouteFailure.NONE, restored.cadence().lastFailure());
        assertTrue(restored.contract().contractor().isEmpty());
        assertTrue(restored.combat().aggressor().isEmpty());
        assertEquals(0, restored.childGiftCooldownTicks());
        assertEquals(HobgoblinJourneyRules.MAX_BLOCKED_EXITS, restored.cadence().blockedExits());
    }

    // ---------------------------------------------------------------- migration

    @Test
    void aLegacyOwnerBecomesABoundedAgreementRatherThanPermanentOwnership() {
        final HobgoblinJourneyState migrated = HobgoblinJourneyState.migrateLegacy(
            "smith", 75, 6_000L, 0L, Optional.of(CONTRACTOR)
        );
        assertEquals(GoblinProfession.SMITH, migrated.profession());
        assertEquals(3, migrated.merchant().level());
        assertEquals(CONTRACTOR, migrated.contract().contractor().orElseThrow());
        assertEquals(ContractKind.LEGACY_WORK, migrated.contract().kind());
        assertEquals(HobgoblinJourneyRules.CONTRACT_DURATION_TICKS, migrated.contract().remainingTicks());
        assertEquals(6_000, migrated.childGiftCooldownTicks());
        assertFalse(migrated.caravan().present());
        assertFalse(migrated.camp().present());
    }

    @Test
    void anUnresolvableLegacyOwnerSimplyProducesNoAgreement() {
        final HobgoblinJourneyState migrated = HobgoblinJourneyState.migrateLegacy(
            null, -50, 0L, 0L, Optional.empty()
        );
        assertEquals(GoblinProfession.FALLBACK, migrated.profession());
        assertEquals(HobgoblinJourneyRules.MIN_MERCHANT_LEVEL, migrated.merchant().level());
        assertFalse(migrated.contract().active());
        assertEquals(0, migrated.childGiftCooldownTicks());
    }

    @Test
    void anExtremeLegacyGiftDeadlineClampsToABoundedHorizon() {
        final HobgoblinJourneyState migrated = HobgoblinJourneyState.migrateLegacy(
            "miner", 0, Long.MAX_VALUE, 0L, Optional.empty()
        );
        assertEquals(HobgoblinJourneyRules.CHILD_GIFT_COOLDOWN_TICKS, migrated.childGiftCooldownTicks());
        final HobgoblinJourneyState past = HobgoblinJourneyState.migrateLegacy(
            "miner", 0, 10L, 20_000L, Optional.empty()
        );
        assertEquals(0, past.childGiftCooldownTicks());
    }

    // ---------------------------------------------------------------- merchant

    @Test
    void merchantProgressionAndTheDailyQuotaAreBothExplicitOperations() {
        HobgoblinJourneyState.Merchant merchant = HobgoblinJourneyState.Merchant.initial();
        assertEquals(1, merchant.level());
        merchant = merchant.withXp(150);
        assertEquals(4, merchant.level());
        merchant = merchant.afterRestock();
        assertEquals(1, merchant.restocksToday());
        assertEquals(HobgoblinJourneyRules.RESTOCK_SPACING_TICKS, merchant.restockSpacingTicks());
        merchant = merchant.afterRestock();
        assertEquals(HobgoblinJourneyRules.MAX_RESTOCKS_PER_DAY, merchant.restocksToday());
        merchant = merchant.afterRestock();
        assertEquals(HobgoblinJourneyRules.MAX_RESTOCKS_PER_DAY, merchant.restocksToday(),
            "the daily quota is clamped rather than growing");
        final HobgoblinJourneyState.Merchant rolled = merchant.onNewDay();
        assertEquals(0, rolled.restocksToday(), "the day roll is what makes restocking possible again");
        assertEquals(0, rolled.restockSpacingTicks());
        assertEquals(merchant.level(), rolled.level());
        assertEquals(merchant.xp(), rolled.xp());
    }

    @Test
    void releaseJobDropsOnlyTheClaimAndModeAndNeverTheAgreementOrCaravan() {
        final HobgoblinJourneyState state = HobgoblinJourneyState.empty()
            .withContract(HobgoblinJourneyState.Contract.accepted(
                CONTRACTOR, ContractKind.GATHER, Optional.empty()))
            .withCaravan(HobgoblinJourneyState.Caravan.none().withKey(5L))
            .withMode(Mode.WORK_COMMIT)
            .withJob(new HobgoblinJourneyState.Job(
                Optional.of(AGGRESSOR), Optional.of(BlockPos.ZERO), Optional.of(OVERWORLD), 200));
        final HobgoblinJourneyState released = state.releaseJob();
        assertFalse(released.job().holdsClaim());
        assertEquals(Mode.IDLE, released.mode());
        assertTrue(released.contract().active());
        assertTrue(released.caravan().present());
    }

    private static byte[] encode(final CompoundTag tag) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            NbtIo.write(tag, new DataOutputStream(bytes));
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return bytes.toByteArray();
    }
}
