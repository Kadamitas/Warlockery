package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.HazardEscapeRules.Hazard;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class HazardEscapeRulesTest {
    @Test
    void demonsAndNaamahDoNotFleeFireOrLava() {
        EnumSet.allOf(CreatureKind.class).stream()
            .filter(kind -> kind.isDemonic() || kind == CreatureKind.NAAMAH)
            .forEach(kind -> {
                assertTrue(HazardEscapeRules.isFireResistant(kind));
                assertFalse(HazardEscapeRules.shouldEscape(kind, Hazard.FIRE));
                assertFalse(HazardEscapeRules.shouldEscape(kind, Hazard.LAVA));
            });
    }

    @Test
    void vulnerableMobsEscapeEveryEnvironmentalHazard() {
        for (final Hazard hazard : Hazard.values()) {
            assertTrue(HazardEscapeRules.shouldEscape(CreatureKind.WEREWOLF, hazard));
            assertTrue(HazardEscapeRules.shouldEscape(CreatureKind.HOBGOBLIN, hazard));
            assertTrue(HazardEscapeRules.shouldEscape(CreatureKind.VAMPIRE, hazard));
        }
    }

    @Test
    void fireResistanceDoesNotDisableDrowningOrContactAvoidance() {
        assertTrue(HazardEscapeRules.shouldEscape(CreatureKind.DEMON, Hazard.DROWNING));
        assertTrue(HazardEscapeRules.shouldEscape(CreatureKind.DEMON, Hazard.CONTACT));
        assertTrue(HazardEscapeRules.shouldEscape(CreatureKind.NAAMAH, Hazard.DROWNING));
        assertTrue(HazardEscapeRules.shouldEscape(CreatureKind.NAAMAH, Hazard.CONTACT));
    }

    @Test
    void urgentHazardsAreReconsideredFaster() {
        assertTrue(HazardEscapeRules.reconsiderationTicks(Hazard.LAVA)
            < HazardEscapeRules.reconsiderationTicks(Hazard.FIRE));
        assertTrue(HazardEscapeRules.reconsiderationTicks(Hazard.DROWNING)
            < HazardEscapeRules.reconsiderationTicks(Hazard.CONTACT));
        assertTrue(HazardEscapeRules.movementSpeed(Hazard.LAVA)
            > HazardEscapeRules.movementSpeed(Hazard.CONTACT));
    }
}
