package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.GoblinEnclaveRules.CombatRole;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.Intent;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.RouteFailure;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Defaults, coupling, round trip, corruption, unknown schema, and 1.4 migration for F10 state. */
final class GoblinEnclaveStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final UUID PATRON = new UUID(7L, 11L);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void anEmptyStateIsASolitaryUnclaimedProspector() {
        final GoblinEnclaveState state = GoblinEnclaveState.empty();
        assertEquals(GoblinEnclaveState.SCHEMA_VERSION, state.schemaVersion());
        assertEquals(GoblinProfession.PROSPECTOR, state.profession());
        assertEquals(1, state.merchant().level());
        assertEquals(0, state.merchant().xp());
        assertFalse(state.anchor().present());
        assertFalse(state.patron().bound());
        assertEquals(Intent.IDLE, state.action().intent());
        assertFalse(state.action().holdsClaim());
        assertEquals(CombatRole.NONE, state.combat().role());
        assertEquals(RouteFailure.NONE, state.cadence().lastFailure());
        assertEquals(0, state.foodPoints());
        assertEquals(0, state.childGiftCooldownTicks());
    }

    @Test
    void theSchemaVersionIsAlwaysNormalizedToTheCurrentOne() {
        final GoblinEnclaveState forged = new GoblinEnclaveState(99, GoblinProfession.MINER,
            GoblinEnclaveState.Merchant.initial(), GoblinEnclaveState.Anchor.none(),
            GoblinEnclaveState.Patron.none(), GoblinEnclaveState.Action.idle(),
            GoblinEnclaveState.Combat.none(), GoblinEnclaveState.Cadence.none(), 0, 0);
        assertEquals(GoblinEnclaveState.SCHEMA_VERSION, forged.schemaVersion());
    }

    @Test
    void everyBoundedFieldClampsInsteadOfOverflowing() {
        final GoblinEnclaveState state = GoblinEnclaveState.empty()
            .withFoodPoints(999)
            .withChildGiftCooldown(999_999);
        assertEquals(GoblinEnclaveRules.MAX_FOOD_POINTS, state.foodPoints());
        assertEquals(GoblinEnclaveRules.CHILD_GIFT_COOLDOWN_TICKS, state.childGiftCooldownTicks());
        assertEquals(0, GoblinEnclaveState.empty().withFoodPoints(-40).foodPoints());
        final GoblinEnclaveState.Merchant merchant =
            new GoblinEnclaveState.Merchant(99, -5, 99, 999_999);
        assertEquals(GoblinEnclaveRules.MAX_MERCHANT_LEVEL, merchant.level());
        assertEquals(0, merchant.xp());
        assertEquals(GoblinEnclaveRules.MAX_RESTOCKS_PER_DAY, merchant.restocksToday());
        assertEquals(GoblinEnclaveRules.RESTOCK_SPACING_TICKS, merchant.restockSpacingTicks());
    }

    @Test
    void merchantExperienceDrivesTheLevelAndARestockSpacesTheNextOne() {
        final GoblinEnclaveState.Merchant leveled = GoblinEnclaveState.Merchant.initial().withXp(150);
        assertEquals(4, leveled.level());
        final GoblinEnclaveState.Merchant restocked = leveled.afterRestock();
        assertEquals(1, restocked.restocksToday());
        assertEquals(GoblinEnclaveRules.RESTOCK_SPACING_TICKS, restocked.restockSpacingTicks());
        final GoblinEnclaveState.Merchant nextDay = restocked.onNewDay();
        assertEquals(0, nextDay.restocksToday());
        assertEquals(0, nextDay.restockSpacingTicks());
        assertEquals(4, nextDay.level());
    }

    @Test
    void anAnchorIsAllOrNothingAcrossKeyPositionAndDimension() {
        assertFalse(new GoblinEnclaveState.Anchor(Optional.of(5L), Optional.of(BlockPos.ZERO),
            Optional.empty()).present());
        assertFalse(new GoblinEnclaveState.Anchor(Optional.empty(), Optional.of(BlockPos.ZERO),
            Optional.of(OVERWORLD)).present());
        assertFalse(new GoblinEnclaveState.Anchor(Optional.of(5L), Optional.of(BlockPos.ZERO),
            Optional.of("   ")).present());
        assertTrue(GoblinEnclaveState.Anchor.at(5L, BlockPos.ZERO, OVERWORLD).present());
    }

    @Test
    void anExpiredPatronPreferenceIsDroppedButTheBoundPatronSurvives() {
        final GoblinEnclaveState.Patron expired = new GoblinEnclaveState.Patron(
            Optional.of(PATRON), Optional.of(new BlockPos(1, 2, 3)), Optional.of(OVERWORLD), 0
        );
        assertTrue(expired.bound());
        assertTrue(expired.depositPreference().isEmpty());
        final GoblinEnclaveState.Patron active = new GoblinEnclaveState.Patron(
            Optional.of(PATRON), Optional.of(new BlockPos(1, 2, 3)), Optional.of(OVERWORLD), 200
        );
        assertTrue(active.depositPreference().isPresent());
        assertTrue(active.expirePreference().bound());
        assertTrue(active.expirePreference().depositPreference().isEmpty());
        // A preference without a patron is meaningless and is discarded outright.
        assertTrue(new GoblinEnclaveState.Patron(Optional.empty(),
            Optional.of(BlockPos.ZERO), Optional.of(OVERWORLD), 200).depositPreference().isEmpty());
    }

    @Test
    void anExpiredLeaseIsReportedNotSilentlyEndedByTheConstructor() {
        final UUID claim = UUID.randomUUID();
        final GoblinEnclaveState.Action expired = new GoblinEnclaveState.Action(
            Intent.BUILD_HUT, Optional.of(claim), Optional.of(PATRON),
            Optional.of(BlockPos.ZERO), Optional.of(OVERWORLD), 0, 0, 18, 3, 12
        );
        // The canonical constructor must NOT end what a tick branch owns. If it cleared the claim
        // here, the intent would survive with holdsClaim() false, the runtime's execute guard would
        // be skipped, and two Goblins past their lease could mutate the same worksite.
        assertEquals(claim, expired.claimId().orElseThrow());
        assertTrue(expired.holdsClaim());
        assertTrue(expired.leaseExpired(), "an expired lease is reported for the tick to act on");
        assertEquals(18, expired.reservedDirt());
        assertEquals(3, expired.reservedLogs());
        assertEquals(12, expired.reservedPlanks());

        final GoblinEnclaveState.Action live = new GoblinEnclaveState.Action(
            Intent.BUILD_HUT, Optional.of(claim), Optional.empty(),
            Optional.of(BlockPos.ZERO), Optional.of(OVERWORLD), 200, 0, 18, 3, 12
        );
        assertTrue(live.holdsClaim());
        assertFalse(live.leaseExpired());
        // An action with no claim can never report an expired lease.
        assertFalse(GoblinEnclaveState.Action.idle().leaseExpired());
        assertFalse(GoblinEnclaveState.Action.idle().holdsClaim());
    }

    @Test
    void materialReservationsAreRealFieldsThatSurviveTheirTransactionAndRoundTrip() {
        final GoblinEnclaveState reserved = GoblinEnclaveState.empty()
            .withAction(new GoblinEnclaveState.Action(Intent.BUILD_HUT,
                Optional.of(UUID.randomUUID()), Optional.empty(), Optional.empty(),
                Optional.empty(), 200, 0,
                GoblinEnclaveRules.HUT_DIRT_COST, GoblinEnclaveRules.HUT_LOG_COST,
                GoblinEnclaveRules.HUT_MIN_PLANKS));
        assertEquals(GoblinEnclaveRules.HUT_DIRT_COST, reserved.action().reservedDirt());
        assertEquals(GoblinEnclaveRules.HUT_LOG_COST, reserved.action().reservedLogs());
        assertEquals(GoblinEnclaveRules.HUT_MIN_PLANKS, reserved.action().reservedPlanks());
        // Over-reservation clamps to the exact declared hut cost rather than growing.
        final GoblinEnclaveState.Action greedy = new GoblinEnclaveState.Action(Intent.BUILD_HUT,
            Optional.of(UUID.randomUUID()), Optional.empty(), Optional.empty(), Optional.empty(),
            200, 0, 999, 999, 999);
        assertEquals(GoblinEnclaveRules.HUT_DIRT_COST, greedy.reservedDirt());
        assertEquals(GoblinEnclaveRules.HUT_LOG_COST, greedy.reservedLogs());
        assertEquals(GoblinEnclaveRules.HUT_MAX_EDITS, greedy.reservedPlanks());
        // Reload deliberately drops the in-tick reservation with the rest of the transaction.
        assertEquals(0, GoblinEnclaveState.read(reserved.write(), OVERWORLD).action().reservedDirt());
    }

    @Test
    void aDestinationWithoutADimensionIsDiscarded() {
        final GoblinEnclaveState.Action action = new GoblinEnclaveState.Action(
            Intent.MINE, Optional.empty(), Optional.empty(), Optional.of(BlockPos.ZERO),
            Optional.empty(), 100, 0, 0, 0, 0
        );
        assertTrue(action.destination().isEmpty());
        assertTrue(action.destinationDimension().isEmpty());
    }

    @Test
    void clearingTheCombatRoleAlsoClearsRetreatAndZeroFailuresClearTheirClassification() {
        final GoblinEnclaveState.Combat combat = new GoblinEnclaveState.Combat(
            CombatRole.NONE, 12L, true, Optional.of(BlockPos.ZERO), Optional.of(BlockPos.ZERO)
        );
        assertFalse(combat.retreating());
        final GoblinEnclaveState.Cadence cadence =
            new GoblinEnclaveState.Cadence(RouteFailure.STUCK, 0, 50, true);
        assertEquals(RouteFailure.NONE, cadence.lastFailure());
        assertFalse(cadence.stuck());
        assertEquals(50, cadence.retryRemainingTicks());
    }

    @Test
    void releaseActionKeepsIdentityProgressionAnchorAndPatron() {
        final GoblinEnclaveState state = GoblinEnclaveState.empty()
            .withProfession(GoblinProfession.SMITH)
            .withMerchant(GoblinEnclaveState.Merchant.initial().withXp(80))
            .withAnchor(GoblinEnclaveState.Anchor.at(9L, new BlockPos(4, 5, 6), OVERWORLD))
            .withPatron(GoblinEnclaveState.Patron.bound(PATRON))
            .withAction(new GoblinEnclaveState.Action(Intent.MINE, Optional.of(UUID.randomUUID()),
                Optional.of(PATRON), Optional.of(BlockPos.ZERO), Optional.of(OVERWORLD),
                200, 0, 0, 0, 0));
        final GoblinEnclaveState released = state.releaseAction();
        assertEquals(Intent.IDLE, released.action().intent());
        assertTrue(released.action().claimId().isEmpty());
        assertEquals(GoblinProfession.SMITH, released.profession());
        assertEquals(3, released.merchant().level());
        assertTrue(released.anchor().present());
        assertTrue(released.patron().bound());
    }

    @Test
    void aPopulatedStateRoundTripsAndDeliberatelyDropsTheTransactionalAction() {
        final GoblinEnclaveState original = GoblinEnclaveState.empty()
            .withProfession(GoblinProfession.SHAMAN)
            .withMerchant(new GoblinEnclaveState.Merchant(3, 90, 1, 1_200))
            .withAnchor(GoblinEnclaveState.Anchor.at(42L, new BlockPos(10, 64, -20), OVERWORLD))
            .withPatron(new GoblinEnclaveState.Patron(Optional.of(PATRON),
                Optional.of(new BlockPos(1, 2, 3)), Optional.of(OVERWORLD), 500))
            .withAction(new GoblinEnclaveState.Action(Intent.BUILD_HUT,
                Optional.of(UUID.randomUUID()), Optional.of(PATRON), Optional.of(BlockPos.ZERO),
                Optional.of(OVERWORLD), 150, 0, 18, 3, 12))
            .withCombat(new GoblinEnclaveState.Combat(CombatRole.WARDER, 900L, true,
                Optional.of(new BlockPos(3, 64, 3)), Optional.of(new BlockPos(4, 64, 4))))
            .withCadence(new GoblinEnclaveState.Cadence(RouteFailure.NO_PATH, 2, 40, false))
            .withFoodPoints(18)
            .withChildGiftCooldown(600);

        final GoblinEnclaveState reloaded = GoblinEnclaveState.read(original.write(), OVERWORLD);
        assertEquals(GoblinProfession.SHAMAN, reloaded.profession());
        assertEquals(3, reloaded.merchant().level());
        assertEquals(90, reloaded.merchant().xp());
        assertEquals(1, reloaded.merchant().restocksToday());
        assertEquals(1_200, reloaded.merchant().restockSpacingTicks());
        assertEquals(original.anchor(), reloaded.anchor());
        assertEquals(PATRON, reloaded.patron().id().orElseThrow());
        assertEquals(new BlockPos(1, 2, 3), reloaded.patron().depositPreference().orElseThrow());
        assertEquals(CombatRole.WARDER, reloaded.combat().role());
        assertTrue(reloaded.combat().retreating());
        assertEquals(900L, reloaded.combat().alarmEpoch());
        assertEquals(RouteFailure.NO_PATH, reloaded.cadence().lastFailure());
        assertEquals(2, reloaded.cadence().routeFailures());
        assertEquals(18, reloaded.foodPoints());
        assertEquals(600, reloaded.childGiftCooldownTicks());
        // The in-tick transaction is never resumed after a reload.
        assertEquals(Intent.IDLE, reloaded.action().intent());
        assertTrue(reloaded.action().claimId().isEmpty());
        assertEquals(0, reloaded.action().reservedDirt());
    }

    @Test
    void aCrossDimensionAnchorPatronPreferenceAndCombatPointsAreDropped() {
        final CompoundTag tag = GoblinEnclaveState.empty()
            .withAnchor(GoblinEnclaveState.Anchor.at(42L, new BlockPos(1, 2, 3), OVERWORLD))
            .withPatron(new GoblinEnclaveState.Patron(Optional.of(PATRON),
                Optional.of(new BlockPos(1, 2, 3)), Optional.of(OVERWORLD), 500))
            .write();
        final GoblinEnclaveState reloaded = GoblinEnclaveState.read(tag, NETHER);
        assertFalse(reloaded.anchor().present());
        assertTrue(reloaded.patron().bound());
        assertTrue(reloaded.patron().depositPreference().isEmpty());
    }

    @Test
    void aMissingMalformedOrUnknownFutureSchemaResetsToASafeSolitaryState() {
        assertEquals(GoblinEnclaveState.empty(), GoblinEnclaveState.read(null, OVERWORLD));
        assertEquals(GoblinEnclaveState.empty(),
            GoblinEnclaveState.read(new CompoundTag(), OVERWORLD));
        final CompoundTag future = GoblinEnclaveState.empty().write();
        future.putInt("Version", GoblinEnclaveState.SCHEMA_VERSION + 1);
        assertEquals(GoblinEnclaveState.empty(), GoblinEnclaveState.read(future, OVERWORLD));
    }

    @Test
    void corruptFieldsFallBackWithoutThrowing() {
        final CompoundTag corrupt = GoblinEnclaveState.empty().write();
        corrupt.putString("Profession", "warlord");
        corrupt.putString("Role", "generalissimo");
        corrupt.putString("RouteFail", "unknown-class");
        corrupt.putString("PatronId", "not-a-uuid");
        corrupt.putString("AnchorDim", "");
        final GoblinEnclaveState reloaded = GoblinEnclaveState.read(corrupt, OVERWORLD);
        assertEquals(GoblinProfession.PROSPECTOR, reloaded.profession());
        assertEquals(CombatRole.NONE, reloaded.combat().role());
        assertEquals(RouteFailure.NONE, reloaded.cadence().lastFailure());
        assertFalse(reloaded.patron().bound());
        assertFalse(reloaded.anchor().present());
    }

    @Test
    void aLegacyGoblinMigratesItsProfessionExperienceGiftDeadlineAndOwnerOnly() {
        final GoblinEnclaveState migrated = GoblinEnclaveState.migrateLegacy(
            "smith", 75, 13_000L, 1_000L, Optional.of(PATRON)
        );
        assertEquals(GoblinProfession.SMITH, migrated.profession());
        assertEquals(75, migrated.merchant().xp());
        assertEquals(3, migrated.merchant().level());
        assertEquals(PATRON, migrated.patron().id().orElseThrow());
        assertEquals(GoblinEnclaveRules.CHILD_GIFT_COOLDOWN_TICKS,
            migrated.childGiftCooldownTicks());
        // Nothing is invented: an old Goblin without enclave state starts solitary and idle.
        assertFalse(migrated.anchor().present());
        assertEquals(Intent.IDLE, migrated.action().intent());
        assertEquals(0, migrated.foodPoints());
        assertEquals(CombatRole.NONE, migrated.combat().role());
    }

    @Test
    void aLegacyGiftDeadlineAlreadyInThePastMigratesAsImmediatelyDue() {
        assertEquals(0, GoblinEnclaveState.migrateLegacy("miner", 0, 500L, 900L,
            Optional.empty()).childGiftCooldownTicks());
        assertEquals(300, GoblinEnclaveState.migrateLegacy("miner", 0, 1_200L, 900L,
            Optional.empty()).childGiftCooldownTicks());
    }

    @Test
    void anUnknownLegacyProfessionAndNegativeExperienceFallBackSafely() {
        final GoblinEnclaveState migrated = GoblinEnclaveState.migrateLegacy(
            null, -400, 0L, 0L, Optional.empty()
        );
        assertEquals(GoblinProfession.PROSPECTOR, migrated.profession());
        assertEquals(0, migrated.merchant().xp());
        assertEquals(1, migrated.merchant().level());
        assertFalse(migrated.patron().bound());
    }

    @Test
    void aFullyPopulatedStateEncodesBelowTheDeclaredByteCeiling() {
        final GoblinEnclaveState populated = GoblinEnclaveState.empty()
            .withProfession(GoblinProfession.PROSPECTOR)
            .withMerchant(new GoblinEnclaveState.Merchant(5, 400, 2, 2_400))
            .withAnchor(GoblinEnclaveState.Anchor.at(Long.MAX_VALUE / 2L,
                new BlockPos(30_000_000, 300, -30_000_000), OVERWORLD))
            .withPatron(new GoblinEnclaveState.Patron(Optional.of(UUID.randomUUID()),
                Optional.of(new BlockPos(-1, 2, -3)), Optional.of(OVERWORLD), 20_000))
            .withCombat(new GoblinEnclaveState.Combat(CombatRole.RESERVE, Long.MAX_VALUE / 2L,
                true, Optional.of(BlockPos.ZERO), Optional.of(BlockPos.ZERO)))
            .withCadence(new GoblinEnclaveState.Cadence(RouteFailure.UNREACHABLE, 3, 100, true))
            .withFoodPoints(24)
            .withChildGiftCooldown(12_000);
        assertTrue(encode(populated.write()).length < GoblinEnclaveRules.MAX_STATE_BYTES);
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
