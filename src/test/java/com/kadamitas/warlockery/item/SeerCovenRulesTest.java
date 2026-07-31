package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
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
}
