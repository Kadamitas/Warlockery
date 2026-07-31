package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class BrewingGarbRulesParityTest {
    @Test
    void hedgeCroneHatCanAwardSecondAndThirdBrewOutputsIndependently() {
        assertEquals(2, BrewingGarbRules.additionalCopies(List.of(25, 25), List.of(0, 24)));
        assertEquals(1, BrewingGarbRules.additionalCopies(List.of(25, 25), List.of(24, 25)));
        assertEquals(0, BrewingGarbRules.additionalCopies(List.of(25, 25), List.of(25, 99)));
    }

    @Test
    void witchAndNecromancerGarmentsUseTheirLegacyYieldChances() {
        assertEquals(2, BrewingGarbRules.additionalCopies(List.of(35, 35), List.of(34, 0)));
        assertEquals(1, BrewingGarbRules.additionalCopies(List.of(45), List.of(44)));
        assertEquals(0, BrewingGarbRules.additionalCopies(List.of(45), List.of(45)));
    }
}
