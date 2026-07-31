package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile.Feature;
import org.junit.jupiter.api.Test;

final class FamiliarDeliveryRulesTest {
    @Test
    void owlDeliveryReportsEveryMissingCondition() {
        assertEquals(FamiliarDeliveryRules.Diagnostic.OWNER_REQUIRED,
            FamiliarDeliveryRules.diagnose(false, true, true, true));
        assertEquals(FamiliarDeliveryRules.Diagnostic.MISSING_DESTINATION,
            FamiliarDeliveryRules.diagnose(true, false, true, true));
        assertEquals(FamiliarDeliveryRules.Diagnostic.MISSING_CARGO,
            FamiliarDeliveryRules.diagnose(true, true, false, true));
        assertEquals(FamiliarDeliveryRules.Diagnostic.DESTINATION_UNAVAILABLE,
            FamiliarDeliveryRules.diagnose(true, true, true, false));
    }

    @Test
    void aBoundOwlCanDeliverToWaystonesAndTaglocks() {
        assertEquals(FamiliarDeliveryRules.Diagnostic.READY,
            FamiliarDeliveryRules.diagnose(true, true, true, true));
        assertTrue(CreatureBehaviorProfile.find(CreatureKind.OWL).orElseThrow().has(Feature.ITEM_DELIVERY));
    }
}
