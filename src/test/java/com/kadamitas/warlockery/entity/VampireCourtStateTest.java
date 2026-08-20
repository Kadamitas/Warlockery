package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.VampireCourtRules.AssaultRole;
import com.kadamitas.warlockery.entity.VampireCourtRules.Intent;
import com.kadamitas.warlockery.entity.VampireCourtRules.ReportOutcome;
import com.kadamitas.warlockery.entity.VampireCourtRules.VictimReport;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class VampireCourtStateTest {
    @Test
    void defaultsKeepVampireAndThrallNeedsSeparate() {
        final VampireCourtState vampire = VampireCourtState.empty(CreatureKind.VAMPIRE, 100L);
        final VampireCourtState thrall = VampireCourtState.empty(CreatureKind.BLOOD_THRALL, 100L);
        assertEquals(350, vampire.pressure());
        assertEquals(Intent.ROOST, vampire.intent());
        assertEquals(0, thrall.pressure());
        assertEquals(Intent.UNBOUND, thrall.intent());
        assertTrue(thrall.reports().isEmpty());
        assertTrue(thrall.masterId().isEmpty());
    }

    @Test
    void boundedSemanticStateRoundTripsWithoutPathsOrEntities() {
        final UUID prey = UUID.randomUUID();
        final UUID attacker = UUID.randomUUID();
        final VictimReport report = new VictimReport(prey, 4, 70, -2, 200L, ReportOutcome.FED, 8);
        final VampireCourtState state = VampireCourtState.empty(CreatureKind.VAMPIRE, 100L)
            .withPressure(720, 200L)
            .withIntent(Intent.STALK, 500L)
            .withShelter("minecraft:overworld", new BlockPos(5, 70, -2), 550L)
            .withTarget(prey, 520L)
            .rememberAttacker(attacker, 510L)
            .rememberVictim(report, 200L)
            .withCadence(240L, 260L, 280L, 300L, 220L)
            .withRouteRetry(2, 400L);
        assertEquals(state, VampireCourtState.read(state.write(), CreatureKind.VAMPIRE, 210L));
    }

    @Test
    void thrallLoadDropsVampireOnlyPressureAndReports() {
        final UUID victim = UUID.randomUUID();
        final CompoundTag tag = VampireCourtState.empty(CreatureKind.VAMPIRE, 0L)
            .withPressure(900, 100L)
            .rememberVictim(new VictimReport(victim, 0, 64, 0, 100L, ReportOutcome.FED, 10), 100L)
            .write();
        tag.putString("Kind", CreatureKind.BLOOD_THRALL.name());

        final VampireCourtState thrall = VampireCourtState.read(tag, CreatureKind.BLOOD_THRALL, 120L);
        assertEquals(0, thrall.pressure());
        assertTrue(thrall.reports().isEmpty());
        assertEquals(Intent.UNBOUND, thrall.intent());
    }

    @Test
    void elapsedLoadCatchupIsArithmeticOnlyAndExpiryClearsActions() {
        final UUID target = UUID.randomUUID();
        final VampireCourtState loaded = VampireCourtState.read(
            VampireCourtState.empty(CreatureKind.VAMPIRE, 0L)
                .withPressure(350, 0L)
                .withIntent(Intent.STALK, 50L)
                .withTarget(target, 50L)
                .withShelter("minecraft:overworld", new BlockPos(1, 64, 1), 50L)
                .write(),
            CreatureKind.VAMPIRE,
            200L
        );
        assertEquals(360, loaded.pressure());
        assertEquals(Intent.RECOVER, loaded.intent());
        assertTrue(loaded.targetId().isEmpty());
        assertTrue(loaded.shelter().isEmpty());
        assertEquals(200L, loaded.nextDecisionAt());
        assertEquals(200L, loaded.nextEntityScanAt());
        assertEquals(200L, loaded.nextShelterScanAt());
    }

    @Test
    void unknownSchemaMalformedIdentityAndExtremeFieldsFallBackSafely() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("SchemaVersion", 99);
        tag.putString("Kind", "bad_kind");
        tag.putString("Intent", "bad_intent");
        tag.putString("Target", "not-a-uuid");
        tag.putString("Master", "also-bad");
        tag.putInt("Pressure", Integer.MAX_VALUE);
        tag.putInt("ReportCount", 999);
        tag.putInt("RouteFailures", Integer.MAX_VALUE);
        tag.putLong("RetryAfter", Long.MAX_VALUE);

        final VampireCourtState safe = VampireCourtState.read(tag, CreatureKind.VAMPIRE, 500L);
        assertEquals(VampireCourtState.empty(CreatureKind.VAMPIRE, 500L), safe);
    }

    @Test
    void feedingMasterLossAndRouteFailureHaveSingleBoundedTransitions() {
        final UUID master = UUID.randomUUID();
        VampireCourtState vampire = VampireCourtState.empty(CreatureKind.VAMPIRE, 0L).withPressure(700, 0L);
        assertEquals(460, vampire.afterOrdinaryFeed(10L).pressure());
        assertEquals(250, vampire.withPressure(900, 10L).afterAssaultFeed(20L).pressure());

        VampireCourtState thrall = VampireCourtState.empty(CreatureKind.BLOOD_THRALL, 0L)
            .withMaster(master, AssaultRole.BOUND_GUARD)
            .withTarget(UUID.randomUUID(), 500L)
            .loseMaster(100L);
        assertEquals(Intent.WAVERING, thrall.intent());
        assertEquals(300L, thrall.waveringUntil());
        assertTrue(thrall.masterId().isEmpty());
        assertTrue(thrall.targetId().isEmpty());
        final VampireCourtState repeatedLoss = thrall.loseMaster(250L);
        assertEquals(300L, repeatedLoss.waveringUntil(),
            "re-observing the same missing master must not restart the two-hundred-tick deadline");
        final VampireCourtState deadlineLoaded = VampireCourtState.read(
            repeatedLoss.write(), CreatureKind.BLOOD_THRALL, 300L
        );
        assertEquals(Intent.WAVERING, deadlineLoaded.intent(),
            "load reconciliation must preserve the expired WAVERING fact for assault-aware runtime resolution");
        assertEquals(300L, deadlineLoaded.waveringUntil());
        final VampireCourtState rebound = repeatedLoss.withMaster(master, AssaultRole.BOUND_GUARD);
        assertEquals(0L, rebound.waveringUntil(), "a valid authored rebind must reset the old loss deadline");
        assertEquals(master, rebound.masterId().orElseThrow());

        thrall = thrall.withShelter("minecraft:overworld", new BlockPos(2, 64, 2), 1_000L);
        thrall = thrall.recordRouteResult(false, 100L).recordRouteResult(false, 120L).recordRouteResult(false, 140L);
        assertEquals(3, thrall.routeFailures());
        assertEquals(240L, thrall.retryAfter());
        assertTrue(thrall.shelter().isEmpty());
    }
}
