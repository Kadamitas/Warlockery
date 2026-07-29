package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class EveryDollInteractionTest {
    @TestFactory
    Stream<DynamicContainer> oneSuitePerDoll() {
        return Stream.of(DollKind.values()).map(kind -> DynamicContainer.dynamicContainer(kind.id(), List.of(
            DynamicTest.dynamicTest("invalid state does not activate", () -> invalidState(kind)),
            DynamicTest.dynamicTest("side panel has stable labels", () -> panelContract(kind)),
            DynamicTest.dynamicTest("successful activation has defined behavior", () -> successContract(kind))
        )));
    }

    private static void invalidState(final DollKind kind) {
        assertTrue(DollKind.find("missing_" + kind.id()).isEmpty());
        if (kind.definition().ability() instanceof DollAbility.Mending) {
            assertFalse(DollRules.needsRepair(0, 100));
        }
    }

    private static void panelContract(final DollKind kind) {
        assertEquals(kind, DollKind.find(kind.id()).orElseThrow());
        assertEquals("tooltip.warlockery.doll." + kind.id(), kind.descriptionKey());
        assertFalse(kind.descriptionKey().isBlank());
        assertTrue(DollFactory.supports(kind.id()));
    }

    private static void successContract(final DollKind kind) {
        switch (kind.definition().ability()) {
            case DollAbility.None ignored -> assertFalse(kind.consumedOnActivation());
            case DollAbility.Mending ignored -> {
                assertTrue(DollRules.needsRepair(10, 100));
                assertEquals(8, DollRules.repairedDamage(10));
                assertEquals(0, DollRules.repairedDamage(1));
                assertFalse(kind.consumedOnActivation());
                assertEquals(128, kind.definition().durability());
            }
            case DollAbility.LethalProtection ignored -> {
                assertTrue(DollRules.isLethal(5.0F, 5.0F));
                assertTrue(kind.consumedOnActivation());
            }
            case DollAbility.ActiveHex ignored -> {
                assertEquals(DollHexAction.SHOVE, DollHexAction.PRICK.next());
                assertEquals(DollHexAction.IGNITE, DollHexAction.SHOVE.next());
                assertEquals(DollHexAction.DROWN, DollHexAction.IGNITE.next());
                assertEquals(DollHexAction.PRICK, DollHexAction.DROWN.next());
                assertFalse(kind.consumedOnActivation());
            }
            case DollAbility.HexGuard ignored -> assertFalse(kind.consumedOnActivation());
            case DollAbility.DamageLink ignored -> assertFalse(kind.consumedOnActivation());
            case DollAbility.DollGuard ignored -> assertFalse(kind.consumedOnActivation());
        }
    }
}
