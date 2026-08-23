package com.kadamitas.warlockery.transformation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WerewolfPreyDriveRulesTest {
    @Test
    void driveOnlyAppliesToPredatoryShapesAndOnePercentRoll() {
        assertFalse(WerewolfPreyDriveRules.shapeEligible(WerewolfShape.HUMAN));
        assertTrue(WerewolfPreyDriveRules.shapeEligible(WerewolfShape.WOLF));
        assertTrue(WerewolfPreyDriveRules.shapeEligible(WerewolfShape.WOLFMAN));
        assertTrue(WerewolfPreyDriveRules.triggered(0));
        assertFalse(WerewolfPreyDriveRules.triggered(1));
        assertFalse(WerewolfPreyDriveRules.triggered(99));
        assertEquals(3_600, WerewolfPreyDriveRules.COOLDOWN_TICKS);
    }

    @Test
    void candidateMustPassEverySafetyAndAuthorityGate() {
        final WerewolfPreyDriveRules.Candidate valid = candidate(false, false, false, false, false, false, false, false);
        assertTrue(WerewolfPreyDriveRules.eligible(valid));
        assertFalse(WerewolfPreyDriveRules.eligible(candidate(true, false, false, false, false, false, false, false)));
        assertFalse(WerewolfPreyDriveRules.eligible(candidate(false, true, false, false, false, false, false, false)));
        assertFalse(WerewolfPreyDriveRules.eligible(candidate(false, false, true, false, false, false, false, false)));
        assertFalse(WerewolfPreyDriveRules.eligible(candidate(false, false, false, true, false, false, false, false)));
        assertFalse(WerewolfPreyDriveRules.eligible(candidate(false, false, false, false, true, false, false, false)));
        assertFalse(WerewolfPreyDriveRules.eligible(candidate(false, false, false, false, false, true, false, false)));
        assertFalse(WerewolfPreyDriveRules.eligible(candidate(false, false, false, false, false, false, true, false)));
        assertFalse(WerewolfPreyDriveRules.eligible(candidate(false, false, false, false, false, false, false, true)));
    }

    @Test
    void selectionIsStableByDistanceThenUuid() {
        final UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        final UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertEquals(first, WerewolfPreyDriveRules.select(List.of(
            new WerewolfPreyDriveRules.Target(second, 9.0),
            new WerewolfPreyDriveRules.Target(first, 9.0),
            new WerewolfPreyDriveRules.Target(UUID.randomUUID(), 16.0)
        )).orElseThrow().id());

        final java.util.ArrayList<WerewolfPreyDriveRules.Target> dense = new java.util.ArrayList<>();
        for (int index = 0; index < 80; index++) {
            dense.add(new WerewolfPreyDriveRules.Target(
                new UUID(0L, 100L + index), 100.0 + index
            ));
        }
        dense.addLast(new WerewolfPreyDriveRules.Target(first, 1.0));
        assertEquals(first, WerewolfPreyDriveRules.select(dense).orElseThrow().id());
    }

    @Test
    void serverPursuitAcceleratesTowardPreyAndNeverChangesVerticalMotion() {
        final WerewolfPreyDriveRules.PursuitMotion first = WerewolfPreyDriveRules.pursuitMotion(
            0.0, -0.04, 8.0, 0.0, -0.31
        );
        assertTrue(first.x() > 0.0);
        assertTrue(Math.hypot(first.x(), first.z()) <= WerewolfPreyDriveRules.MAX_PURSUIT_SPEED);
        assertEquals(-0.31, first.vertical(), 0.000_001);

        WerewolfPreyDriveRules.PursuitMotion bounded = first;
        for (int tick = 0; tick < 40; tick++) {
            bounded = WerewolfPreyDriveRules.pursuitMotion(
                bounded.x(), bounded.z(), 8.0, 0.0, bounded.vertical()
            );
        }
        assertEquals(WerewolfPreyDriveRules.MAX_PURSUIT_SPEED,
            Math.hypot(bounded.x(), bounded.z()), 0.000_001);
        assertEquals(-0.31, bounded.vertical(), 0.000_001);
    }

    @Test
    void flightAndImmediateContactHazardsCancelBeforeOrDuringAnEpisode() {
        assertFalse(WerewolfPreyDriveRules.cancelsEpisode(condition()));
        assertTrue(WerewolfPreyDriveRules.cancelsEpisode(condition(true, false, false, false, false, false, false)));
        assertTrue(WerewolfPreyDriveRules.cancelsEpisode(condition(false, true, false, false, false, false, false)));
        assertTrue(WerewolfPreyDriveRules.cancelsEpisode(condition(false, false, true, false, false, false, false)));
        assertTrue(WerewolfPreyDriveRules.cancelsEpisode(condition(false, false, false, true, false, false, false)));
        assertTrue(WerewolfPreyDriveRules.cancelsEpisode(condition(false, false, false, false, true, false, false)));
        assertTrue(WerewolfPreyDriveRules.cancelsEpisode(condition(false, false, false, false, false, true, false)));
        assertTrue(WerewolfPreyDriveRules.cancelsEpisode(condition(false, false, false, false, false, false, true)));
    }

    private static WerewolfPreyDriveRules.Candidate candidate(
        boolean baby, boolean protectedIdentity, boolean arcane, boolean riding,
        boolean invalidWorld, boolean protectedAssaultTarget, boolean outOfRange, boolean noLineOfSight
    ) {
        return new WerewolfPreyDriveRules.Candidate(
            true, true, baby, protectedIdentity, arcane, riding, invalidWorld,
            protectedAssaultTarget, outOfRange, noLineOfSight
        );
    }

    private static WerewolfPreyDriveRules.PlayerCondition condition() {
        return condition(false, false, false, false, false, false, false);
    }

    private static WerewolfPreyDriveRules.PlayerCondition condition(
        boolean flying, boolean fallFlying, boolean inWater, boolean drowning,
        boolean inLavaOrFire, boolean inPowderSnow, boolean freezing
    ) {
        return new WerewolfPreyDriveRules.PlayerCondition(
            false, flying, fallFlying, inWater, drowning, inLavaOrFire, inPowderSnow, freezing
        );
    }
}
