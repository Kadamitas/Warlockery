package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class HexBehaviorsTest {
    @Test
    void everyNamedHexIsRegisteredWithTheFactory() {
        Set.of("misfortune", "insanity", "sinking", "overheating", "nightmare", "corrupt_doll", "wolf")
            .forEach(target -> assertTrue(HexBehaviors.supports(target)));
    }

    @Test
    void unknownTargetsDoNotBecomeNamedHexes() {
        assertFalse(HexBehaviors.supports("unknown"));
    }
}
