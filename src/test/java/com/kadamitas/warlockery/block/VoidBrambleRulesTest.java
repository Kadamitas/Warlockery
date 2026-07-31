package com.kadamitas.warlockery.block;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class VoidBrambleRulesTest {
    @Test
    void suppressesCircleMagicInsideThirtyTwoBlocks() {
        assertTrue(VoidBrambleRules.suppressesMagic(0.0));
        assertTrue(VoidBrambleRules.suppressesMagic(32.0 * 32.0));
        assertFalse(VoidBrambleRules.suppressesMagic(32.0 * 32.0 + 1.0));
    }

    @Test
    void teleportOffsetsReachButNeverExceedFiveHundredBlocks() {
        assertEquals(-480, VoidBrambleRules.targetCoordinate(20, -500));
        assertEquals(520, VoidBrambleRules.targetCoordinate(20, 500));
        assertThrows(IllegalArgumentException.class, () -> VoidBrambleRules.targetCoordinate(20, 501));
    }

    @Test
    void cooldownPreventsContactFromTeleportingEveryTick() {
        assertFalse(VoidBrambleRules.teleportReady(99L, 100L));
        assertTrue(VoidBrambleRules.teleportReady(100L, 100L));
    }

    @Test
    void onlyTheOwnerOrCreativePlayersCanBreakClaimedBramble() {
        final UUID owner = UUID.randomUUID();
        final UUID stranger = UUID.randomUUID();
        assertTrue(VoidBrambleRules.canBreak(owner, owner, false));
        assertFalse(VoidBrambleRules.canBreak(owner, stranger, false));
        assertTrue(VoidBrambleRules.canBreak(owner, stranger, true));
        assertTrue(VoidBrambleRules.canBreak(null, stranger, false));
    }
}
