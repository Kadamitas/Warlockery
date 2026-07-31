package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ParasyticLouseRulesTest {
    @Test
    void beneficialPotionsProtectTheWearer() {
        assertEquals(
            ParasyticLouseRules.InjectionTarget.WEARER,
            ParasyticLouseRules.target(true, true, true)
        );
    }

    @Test
    void harmfulPotionsBiteTheAttacker() {
        assertEquals(
            ParasyticLouseRules.InjectionTarget.ATTACKER,
            ParasyticLouseRules.target(true, false, true)
        );
    }

    @Test
    void anEmptyLouseOrMissingAttackerCannotInject() {
        assertEquals(
            ParasyticLouseRules.InjectionTarget.NONE,
            ParasyticLouseRules.target(false, true, true)
        );
        assertEquals(
            ParasyticLouseRules.InjectionTarget.NONE,
            ParasyticLouseRules.target(true, false, false)
        );
    }
}
