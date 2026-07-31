package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ManifestationRuntimeTest {
    @Test
    void ghostWalkingOnlyExtendsAManifestationAndNeverShortensIt() {
        assertEquals(240, ManifestationRuntime.sustainedExpiration(200, 240));
        assertEquals(300, ManifestationRuntime.sustainedExpiration(300, 240));
    }

    @Test
    void covenMembersExtendManifestationByTwentyFiveSecondsEach() {
        assertEquals(3000, ManifestationRules.durationTicks(3000, 1));
        assertEquals(3500, ManifestationRules.durationTicks(3000, 2));
        assertEquals(5500, ManifestationRules.durationTicks(3000, 6));
    }
}
