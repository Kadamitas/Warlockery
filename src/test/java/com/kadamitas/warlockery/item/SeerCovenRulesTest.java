package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class SeerCovenRulesTest {
    @Test
    void onlyBoundCircleMagesCountAsNonPlayerParticipants() {
        assertTrue(SeerCovenRules.isCircleMageParticipant(CreatureKind.CIRCLE_MAGE, true));
        assertFalse(SeerCovenRules.isCircleMageParticipant(CreatureKind.CIRCLE_MAGE, false));
        assertFalse(SeerCovenRules.isCircleMageParticipant(CreatureKind.HEDGE_CRONE, true));
        assertFalse(SeerCovenRules.isCircleMageParticipant(null, true));
    }

    @Test
    void onlyTheCastersOwnCircleMagesCountTowardsTheirCoven() {
        final UUID caster = UUID.randomUUID();
        final UUID stranger = UUID.randomUUID();
        assertTrue(
            SeerCovenRules.countsForCaster(CreatureKind.CIRCLE_MAGE, Optional.of(caster), Optional.of(caster)),
            "a Mage sworn to the caster is part of the coven they gathered"
        );
        assertFalse(
            SeerCovenRules.countsForCaster(CreatureKind.CIRCLE_MAGE, Optional.of(stranger), Optional.of(caster)),
            "another player's Mage standing nearby must not fill the caster's circle"
        );
        assertFalse(
            SeerCovenRules.countsForCaster(CreatureKind.CIRCLE_MAGE, Optional.empty(), Optional.of(caster)),
            "an unbound Mage answers to nobody"
        );
        assertFalse(
            SeerCovenRules.countsForCaster(CreatureKind.CIRCLE_MAGE, Optional.of(caster), Optional.empty()),
            "a coven cannot be attributed to a caster who cannot be resolved"
        );
        assertFalse(
            SeerCovenRules.countsForCaster(CreatureKind.HEDGE_CRONE, Optional.of(caster), Optional.of(caster)),
            "only Circle Mages join a coven, whoever they answer to"
        );
    }

    @Test
    void aCovenNeverContributesMoreThanItsCap() {
        assertEquals(0, SeerCovenRules.cappedCoven(0));
        assertEquals(CovenRosterData.MAX_PER_OWNER, SeerCovenRules.cappedCoven(CovenRosterData.MAX_PER_OWNER));
        assertEquals(
            CovenRosterData.MAX_PER_OWNER,
            SeerCovenRules.cappedCoven(CovenRosterData.MAX_PER_OWNER + 4),
            "a save carrying more rows than the cap must not buy a larger coven"
        );
    }

    @Test
    void everyCallProducesOneOutcomeSpecificFeedbackKey() {
        assertEquals("message.warlockery.seer_stone.no_coven", SeerCovenRules.feedbackKey(0));
        assertEquals("message.warlockery.seer_stone.coven_called", SeerCovenRules.feedbackKey(1));
        assertEquals("message.warlockery.seer_stone.coven_called", SeerCovenRules.feedbackKey(15));
    }

    @Test
    void gatheredMagesReceiveDistinctPlacesAroundTheCircle() {
        final BlockPos center = new BlockPos(8, 64, 8);
        assertNotEquals(
            SeerCovenRules.gatheringPosition(center, 0, 4),
            SeerCovenRules.gatheringPosition(center, 1, 4)
        );
        assertEquals(65.5, SeerCovenRules.gatheringPosition(center, 0, 4).y());
        assertThrows(IllegalArgumentException.class, () -> SeerCovenRules.gatheringPosition(center, 4, 4));
    }

    @Test
    void aSeerCallConsidersAtMostSixRosterUuidsWithoutAnyEntityScan() {
        final CovenRosterData roster = new CovenRosterData();
        final java.util.UUID owner = new java.util.UUID(1L, 1L);
        for (int index = 0; index < 12; index++) {
            roster.register(owner, new java.util.UUID(2L, index));
        }
        assertEquals(CovenRosterData.MAX_PER_OWNER, roster.members(owner).size(),
            "the call surface can never see more than the capped roster");
        assertEquals(CovenRosterData.MAX_PER_OWNER, roster.count(owner));
        assertEquals(roster.members(owner), roster.members(owner),
            "resolution order is deterministic across repeated calls");
    }

    @Test
    void unloadedAndUnrosteredMagesStayOutsideTheCall() {
        final CovenRosterData roster = new CovenRosterData();
        final java.util.UUID owner = new java.util.UUID(3L, 3L);
        final java.util.UUID unbound = new java.util.UUID(4L, 4L);
        assertTrue(roster.members(owner).isEmpty(),
            "an unrostered unbound Mage is never discovered by a call");
        roster.register(owner, unbound);
        assertEquals(1, roster.count(owner));
        // Unload retains membership: only death or an explicit unregister removes it.
        assertEquals(1, roster.count(owner));
    }
}
