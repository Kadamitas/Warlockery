package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class HunterArmorRulesTest {
    @TestFactory
    Stream<DynamicContainer> everyHunterTierHasFailureAndSuccessCoverage() {
        return Stream.of(
            new Scenario("base", HunterArmorRules.resolve(false, false, true, true, false, false), 0.5F),
            new Scenario("silvered", HunterArmorRules.resolve(false, true, false, false, true, false), 0.4F),
            new Scenario("dawn_werewolf", HunterArmorRules.resolve(true, false, false, false, true, false), 0.25F),
            new Scenario("dawn_vampire", HunterArmorRules.resolve(true, false, false, false, false, true), 0.25F)
        ).map(scenario -> DynamicContainer.dynamicContainer(scenario.id(), List.of(
            DynamicTest.dynamicTest("failure", () -> assertEquals(
                HunterArmorRules.Resolution.NONE,
                HunterArmorRules.resolve(false, false, false, true, true, true)
            )),
            DynamicTest.dynamicTest("diagnostic state", () -> assertTrue(scenario.resolution().protectedDamage())),
            DynamicTest.dynamicTest("success", () -> assertEquals(
                scenario.expectedMultiplier(),
                scenario.resolution().damageMultiplier()
            ))
        )));
    }

    @Test
    void completeBaseSetBlocksRemoteHexes() {
        assertTrue(HunterArmorRules.blocksHex(true));
        assertFalse(HunterArmorRules.blocksHex(false));
    }

    @Test
    void invalidMultiplierIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HunterArmorRules.Resolution(1.1F, false));
    }

    private record Scenario(String id, HunterArmorRules.Resolution resolution, float expectedMultiplier) {
    }
}
