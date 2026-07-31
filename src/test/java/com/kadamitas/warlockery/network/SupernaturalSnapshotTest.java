package com.kadamitas.warlockery.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SupernaturalSnapshotTest {
    @Test
    void normalizesUntrustedNetworkValues() {
        final ModNetwork.SupernaturalSnapshot snapshot = new ModNetwork.SupernaturalSnapshot(
            null,
            14,
            900,
            500,
            null,
            null,
            null,
            null
        );

        assertEquals("", snapshot.identity());
        assertEquals(10, snapshot.level());
        assertEquals(500, snapshot.resource());
        assertEquals(500, snapshot.maxResource());
        assertEquals("", snapshot.selectedPower());
        assertFalse(snapshot.active());
    }

    @Test
    void clampsNegativeResourceCapacity() {
        final ModNetwork.SupernaturalSnapshot snapshot = new ModNetwork.SupernaturalSnapshot(
            "transformation.warlockery.vampire",
            -4,
            -20,
            -1,
            "power.warlockery.transfix",
            "shape.warlockery.human",
            "quest.warlockery.vampire.first_night",
            "0 / 1"
        );

        assertEquals(0, snapshot.level());
        assertEquals(0, snapshot.resource());
        assertEquals(0, snapshot.maxResource());
        assertEquals(0.0F, snapshot.resourceFraction());
        assertTrue(snapshot.active());
    }

    @Test
    void calculatesResourceFraction() {
        final ModNetwork.SupernaturalSnapshot snapshot = new ModNetwork.SupernaturalSnapshot(
            "transformation.warlockery.werewolf",
            7,
            35,
            100,
            "power.warlockery.stun_howl",
            "shape.warlockery.wolfman",
            "quest.warlockery.werewolf.call_the_pack",
            "2 / 6"
        );

        assertEquals(0.35F, snapshot.resourceFraction(), 0.0001F);
        assertTrue(snapshot.active());
    }

    @Test
    void hidesExplicitNoneIdentities() {
        assertFalse(snapshot("none").active());
        assertFalse(snapshot("transformation.warlockery.none").active());
        assertTrue(snapshot("transformation.warlockery.vampire").active());
    }

    private static ModNetwork.SupernaturalSnapshot snapshot(final String identity) {
        return new ModNetwork.SupernaturalSnapshot(identity, 0, 0, 0, "", "", "", "");
    }
}
