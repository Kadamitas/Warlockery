package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.NamiLifeRules.Activity;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class NamiLifeStateTest {
    @Test
    void semanticStateRoundTripsWithoutPathsOrObjectReferences() {
        final UUID visitor = UUID.randomUUID();
        final UUID aggressor = UUID.randomUUID();
        final UUID ward = UUID.randomUUID();
        final UUID activityTarget = UUID.randomUUID();
        final NamiLifeState state = NamiLifeState.empty()
            .withHome("minecraft:overworld", new BlockPos(3, 64, -7))
            .begin(Activity.SOCIAL_VISIT, 500L, Optional.empty(), Optional.of(activityTarget))
            .rememberVisitor(visitor, 900L, 250L)
            .rememberAggressor(aggressor, 700L)
            .chargeWard(ward, 320L, 400L)
            .withRouteFailure(2, 350L)
            .withSchedule(240L, 600L, 200L);

        assertEquals(state, NamiLifeState.read(state.write(), 200L));
    }

    @Test
    void visitorAggressorAndWardMemoriesStayBoundedToOneIdentityEach() {
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        final NamiLifeState state = NamiLifeState.empty()
            .rememberVisitor(first, 100L, 50L)
            .rememberVisitor(second, 200L, 75L)
            .rememberAggressor(first, 300L)
            .rememberAggressor(second, 400L)
            .chargeWard(first, 500L, 600L)
            .chargeWard(second, 700L, 800L);

        assertEquals(Optional.of(second), state.welcomedVisitor());
        assertEquals(Optional.of(second), state.recentAggressor());
        assertEquals(Optional.of(second), state.wardTarget());
        assertEquals(75L, state.greetingReadyAt());
    }

    @Test
    void expiryReconciliationDropsMissingTacticalStateWithoutReplayingTicks() {
        final NamiLifeState expired = NamiLifeState.empty()
            .begin(Activity.WARD, 100L, Optional.empty(), Optional.of(UUID.randomUUID()))
            .rememberVisitor(UUID.randomUUID(), 80L, 20L)
            .rememberAggressor(UUID.randomUUID(), 90L)
            .chargeWard(UUID.randomUUID(), 70L, 75L)
            .withSchedule(40L, 50L, 10L)
            .reconcileAfterLoad(101L);

        assertEquals(Activity.IDLE, expired.activity());
        assertTrue(expired.activityEntity().isEmpty());
        assertTrue(expired.welcomedVisitor().isEmpty());
        assertTrue(expired.recentAggressor().isEmpty());
        assertTrue(expired.wardTarget().isEmpty());
        assertEquals(101L, expired.nextDecisionAt());
        assertEquals(101L, expired.nextDiscoveryAt());
        assertEquals(101L, expired.lastNavigationAt());
    }

    @Test
    void ordinaryExpiryReconciliationDoesNotSuppressFutureNavigation() {
        final NamiLifeState reconciled = NamiLifeState.empty()
            .withSchedule(40L, 50L, 10L)
            .reconcile(101L);

        assertEquals(40L, reconciled.nextDecisionAt());
        assertEquals(50L, reconciled.nextDiscoveryAt());
        assertEquals(10L, reconciled.lastNavigationAt());
    }

    @Test
    void unknownVersionAndInvalidOptionalFieldsDefaultToIdleButRetainValidHome() {
        final CompoundTag tag = NamiLifeState.empty()
            .withHome("minecraft:overworld", new BlockPos(4, 70, 9))
            .write();
        tag.putInt("SchemaVersion", 99);
        tag.putString("Activity", "not_an_activity");
        tag.putString("WelcomedVisitor", "not-a-uuid");
        tag.putString("RecentAggressor", "also-not-a-uuid");
        tag.putString("WardTarget", "still-not-a-uuid");

        final NamiLifeState recovered = NamiLifeState.read(tag, 1_000L);
        assertEquals(Activity.IDLE, recovered.activity());
        assertEquals(Optional.of("minecraft:overworld"), recovered.homeDimension());
        assertEquals(Optional.of(new BlockPos(4, 70, 9)), recovered.home());
        assertTrue(recovered.welcomedVisitor().isEmpty());
        assertTrue(recovered.recentAggressor().isEmpty());
        assertTrue(recovered.wardTarget().isEmpty());
    }

    @Test
    void missingFieldsAndExcessRouteFailuresUseSafeDefaults() {
        assertEquals(NamiLifeState.empty(), NamiLifeState.read(new CompoundTag(), 0L));
        final CompoundTag tag = new CompoundTag();
        tag.putInt("SchemaVersion", NamiLifeState.SCHEMA_VERSION);
        tag.putInt("RouteFailures", 50);
        final NamiLifeState bounded = NamiLifeState.read(tag, 0L);
        assertEquals(NamiLifeRules.MAX_ROUTE_FAILURES, bounded.routeFailures());
        assertFalse(bounded.home().isPresent());
    }
}
