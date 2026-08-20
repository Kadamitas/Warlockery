package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.TacticalCombatRules.Doctrine;
import com.kadamitas.warlockery.entity.TacticalCombatRules.Maneuver;
import com.kadamitas.warlockery.entity.TacticalCombatRules.Profile;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

final class TacticalCombatRulesTest {
    @Test
    void everyWarlockeryCreatureThatReachesTheGenericLayerHasAValidatedCombatDoctrine() {
        for (final CreatureKind kind : CreatureKind.values()) {
            if (!TacticalCombatRules.usesGenericTacticalLayer(kind)) {
                continue;
            }
            final Profile profile = TacticalCombatRules.profile(kind);
            assertNotNull(profile.doctrine());
            assertTrue(profile.cadenceTicks() > 0);
            assertTrue(profile.preferredDistance() > 0.0);
            assertTrue(profile.coverSearchRadius() > 0);
            assertTrue(profile.movementSpeed() > 0.0);
        }
    }

    @Test
    void familiesThatOwnTheirOwnCombatDeclareNoDoctrineTheyCannotExecute() {
        final EnumSet<CreatureKind> retired = EnumSet.of(
            CreatureKind.VAMPIRE,
            CreatureKind.BLOOD_THRALL,
            CreatureKind.WEREWOLF,
            CreatureKind.LYCAN_VILLAGER
        );
        for (final CreatureKind kind : retired) {
            assertTrue(!TacticalCombatRules.usesGenericTacticalLayer(kind),
                kind + " owns navigation through its own family runtime and never reaches "
                    + "TacticalCombatRuntime");
            assertThrows(IllegalArgumentException.class, () -> TacticalCombatRules.profile(kind),
                "the doctrine table must refuse a kind whose maneuvers can never run instead of "
                    + "handing out an unreachable " + kind + " profile");
        }
        for (final CreatureKind kind : CreatureKind.values()) {
            if (retired.contains(kind)) {
                continue;
            }
            assertTrue(TacticalCombatRules.usesGenericTacticalLayer(kind), kind.name());
            assertNotNull(TacticalCombatRules.profile(kind).doctrine(),
                "every kind outside the retired set must still resolve a doctrine: " + kind);
        }
    }

    @Test
    void rangedThreatsMakeCoverUsingDoctrinesHide() {
        for (final Doctrine doctrine : EnumSet.of(Doctrine.RANGED, Doctrine.SKIRMISHER, Doctrine.STALKER)) {
            final Profile profile = profile(doctrine, true, true);
            assertEquals(Maneuver.COVER, TacticalCombatRules.choose(
                profile, true, true, true, profile.preferredDistance(), 20.0F, 20.0F
            ));
        }
    }

    @Test
    void blockedMeleeOpeningsMakeEveryDoctrineDisengage() {
        for (final Doctrine doctrine : Doctrine.values()) {
            final Profile profile = profile(doctrine, doctrine != Doctrine.BRUTE, doctrine != Doctrine.GUARD);
            assertEquals(Maneuver.DISENGAGE, TacticalCombatRules.choose(
                profile, false, true, false, 2.0, 20.0F, 20.0F
            ));
        }
    }

    @Test
    void packHuntersAndStalkersFlankWhileGuardsHoldAndBrutesPress() {
        assertEquals(Maneuver.FLANK, chooseAtPreferred(Doctrine.PACK));
        assertEquals(Maneuver.FLANK, chooseAtPreferred(Doctrine.STALKER));
        assertEquals(Maneuver.HOLD, chooseAtPreferred(Doctrine.GUARD));
        assertEquals(Maneuver.PRESS, chooseAtPreferred(Doctrine.BRUTE));
        assertEquals(Maneuver.DISENGAGE, chooseAtPreferred(Doctrine.TIMID));
    }

    @Test
    void alternatingEntityIdsSplitGroupsAcrossBothFlanks() {
        assertEquals(-1, TacticalCombatRules.flankSide(12));
        assertEquals(1, TacticalCombatRules.flankSide(13));
        assertEquals(-TacticalCombatRules.flankSide(41), TacticalCombatRules.flankSide(42));
    }

    @Test
    void lowHealthUsesCoverWhenPossibleAndOtherwiseRetreats() {
        final Profile cover = profile(Doctrine.STALKER, true, true);
        final Profile exposed = profile(Doctrine.BRUTE, false, true);
        assertEquals(Maneuver.COVER, TacticalCombatRules.choose(cover, false, false, true, 4.0, 2.0F, 20.0F));
        assertEquals(Maneuver.DISENGAGE,
            TacticalCombatRules.choose(exposed, false, false, true, 4.0, 2.0F, 20.0F));
    }

    @Test
    void reevaluationCadenceIsDeterministicAndDistributedAcrossEntities() {
        final int cadence = 12;
        for (int entityId = 0; entityId < cadence; entityId++) {
            int decisions = 0;
            for (int tick = 0; tick < cadence; tick++) {
                decisions += TacticalCombatRules.shouldReconsider(tick, entityId, cadence) ? 1 : 0;
            }
            assertEquals(1, decisions);
        }
        assertThrows(IllegalArgumentException.class, () -> TacticalCombatRules.shouldReconsider(0, 0, 0));
    }

    @Test
    void invalidSensorInputsAreRejectedBeforeTheyReachNavigation() {
        final Profile profile = profile(Doctrine.PACK, false, true);
        assertThrows(IllegalArgumentException.class,
            () -> TacticalCombatRules.choose(profile, false, false, true, Double.NaN, 20.0F, 20.0F));
        assertThrows(IllegalArgumentException.class,
            () -> TacticalCombatRules.choose(profile, false, false, true, -1.0, 20.0F, 20.0F));
        assertThrows(IllegalArgumentException.class,
            () -> TacticalCombatRules.choose(profile, false, false, true, 2.0, 20.0F, 0.0F));
    }

    private static Maneuver chooseAtPreferred(final Doctrine doctrine) {
        final Profile profile = profile(doctrine, doctrine == Doctrine.STALKER, true);
        return TacticalCombatRules.choose(
            profile, false, false, true, profile.preferredDistance(), 20.0F, 20.0F
        );
    }

    private static Profile profile(final Doctrine doctrine, final boolean usesCover, final boolean flanks) {
        return new Profile(doctrine, 10, 5.0, 7, 0.25F, 1.0, usesCover, flanks);
    }
}

