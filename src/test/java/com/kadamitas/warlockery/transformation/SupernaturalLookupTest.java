package com.kadamitas.warlockery.transformation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SupernaturalLookupTest {
    @Test
    void formParsingAndPathIndexesPreserveStoredIdentityCompatibility() {
        assertEquals(SupernaturalForm.VAMPIRE, SupernaturalForm.parse("vampire"));
        assertEquals(SupernaturalForm.WEREWOLF, SupernaturalForm.parse("WEREWOLF"));
        assertEquals(SupernaturalForm.NONE, SupernaturalForm.parse("unknown"));
        assertEquals(
            SupernaturalProgression.Path.VAMPIRE,
            SupernaturalProgression.Path.find("VAMPIRE").orElseThrow()
        );
        assertEquals(
            SupernaturalProgression.Path.WEREWOLF,
            SupernaturalProgression.Path.forForm(SupernaturalForm.WEREWOLF).orElseThrow()
        );
        assertTrue(SupernaturalProgression.Path.forForm(SupernaturalForm.NONE).isEmpty());
    }

    @Test
    void powerIndexesKeepDeclarationOrderAndReturnImmutableUnlockViews() {
        assertEquals(SupernaturalPower.TELEPORT, SupernaturalPower.find("teleport").orElseThrow());
        assertTrue(SupernaturalPower.find("missing").isEmpty());
        final List<SupernaturalPower> levelTenVampirePowers = SupernaturalPower.unlocked(
            SupernaturalProgression.Path.VAMPIRE,
            10
        );

        assertEquals(SupernaturalPower.TRANSFIX, levelTenVampirePowers.getFirst());
        assertEquals(SupernaturalPower.SUMMON_BATS, levelTenVampirePowers.getLast());
        assertThrows(UnsupportedOperationException.class, levelTenVampirePowers::clear);
    }

    @Test
    void formCombatProfilesRetainTheEstablishedReserveCosts() {
        assertEquals(8.0, SupernaturalForm.VAMPIRE.damageReserveMultiplier());
        assertEquals(0.55F, SupernaturalForm.VAMPIRE.maximumDamageReduction());
        assertEquals(125, SupernaturalForm.VAMPIRE.deathWardCost());
        assertEquals(4.0, SupernaturalForm.WEREWOLF.damageReserveMultiplier());
        assertEquals(0.7F, SupernaturalForm.WEREWOLF.maximumDamageReduction());
        assertEquals(100, SupernaturalForm.WEREWOLF.deathWardCost());
    }
}
