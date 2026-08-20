package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class BrambleColossusStateTest {
    @Test void dimensionTransitionDropsOnlyTheOldPost() {
        var state=BrambleColossusState.empty().postedAt(new BlockPos(1,2,3)).withNerve(42).withLeg(2);
        var cleared=state.withoutPost();
        assertFalse(cleared.posted());
        assertTrue(cleared.post().isEmpty());
        assertEquals(42,cleared.nerve());
        assertEquals(2,cleared.leg());
    }
    @Test void versionOneRoundTripsItsExactFixedFields() {
        var state = BrambleColossusState.empty().postedAt(new BlockPos(1, 64, -2))
            .withNerve(17).withLeg(3).withDisplayCooldown(600).withCircuitCooldown(2400);
        assertEquals(state, BrambleColossusState.read(state.write()));
        assertEquals(9, state.write().size());
        assertTrue(state.write().contains("SchemaVersion"));
        assertFalse(state.write().contains("Version"));
        assertEquals(new BlockPos(1, 64, -2), state.post().orElseThrow());
    }

    @Test void malformedAndOverflowingFieldsClampIndependently() {
        var tag = BrambleColossusState.empty().write();
        tag.putInt("Nerve", -8);
        tag.putInt("Leg", 99);
        tag.putInt("DisplayCooldownRemaining", 900);
        tag.putInt("CircuitCooldownRemaining", -1);
        var state = BrambleColossusState.read(tag);
        assertEquals(0, state.nerve());
        assertEquals(3, state.leg());
        assertEquals(600, state.displayCooldownRemaining());
        assertEquals(0, state.circuitCooldownRemaining());
        tag.putInt("SchemaVersion", 99);
        assertEquals(BrambleColossusState.empty(), BrambleColossusState.read(tag));
    }

    @Test void zeroCoordinateNeverMeansUnposted() {
        var posted = BrambleColossusState.empty().postedAt(BlockPos.ZERO);
        assertTrue(posted.posted());
        assertEquals(BlockPos.ZERO, posted.post().orElseThrow());
        assertFalse(BrambleColossusState.empty().posted());
    }
}
