package com.kadamitas.warlockery.dream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SpiritManifestationRulesTest {
    @Test
    void portalRequiresADreamerWithAnActiveRiteAndDestination() {
        assertEquals(SpiritManifestationRules.Decision.NOT_IN_SPIRIT_WORLD,
            SpiritManifestationRules.enter(false, false, true, false, true));
        assertEquals(SpiritManifestationRules.Decision.MISSING_RITE,
            SpiritManifestationRules.enter(true, true, false, false, true));
        assertEquals(SpiritManifestationRules.Decision.ALREADY_MANIFESTED,
            SpiritManifestationRules.enter(true, true, true, true, true));
        assertEquals(SpiritManifestationRules.Decision.DESTINATION_UNAVAILABLE,
            SpiritManifestationRules.enter(true, true, true, false, false));
        assertTrue(SpiritManifestationRules.enter(true, true, true, false, true).ready());
    }

    @Test
    void manifestationEndsExactlyAtItsServerTickDeadline() {
        assertFalse(SpiritManifestationRules.expired(99, 100));
        assertTrue(SpiritManifestationRules.expired(100, 100));
        assertTrue(SpiritManifestationRules.expired(101, 100));
    }

    @Test
    void ghostWalkingCanOnlyExtendTheDeadline() {
        assertEquals(240L, SpiritManifestationRules.extend(200, 240));
        assertEquals(300L, SpiritManifestationRules.extend(300, 240));
    }
}
