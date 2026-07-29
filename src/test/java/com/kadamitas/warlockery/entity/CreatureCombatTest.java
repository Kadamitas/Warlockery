package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import org.junit.jupiter.api.Test;

final class CreatureCombatTest {
    @Test
    void supernaturalCreaturesResistOrdinaryDamage() {
        assertEquals(1.5F, CreatureCombat.adjustedDamage(CreatureKind.WEREWOLF, 10.0F, false, false, false, false));
    }

    @Test
    void silverBypassesResistanceAndDealsBonusDamage() {
        assertEquals(20.0F, CreatureCombat.adjustedDamage(CreatureKind.WEREWOLF, 10.0F, true, false, false, false));
        assertEquals(20.0F, CreatureCombat.adjustedDamage(CreatureKind.LYCAN_VILLAGER, 10.0F, true, false, false, false));
        assertEquals(1.5F, CreatureCombat.adjustedDamage(CreatureKind.VAMPIRE, 10.0F, true, false, false, false));
    }

    @Test
    void stakesAndHolyBoltsPunishVampires() {
        assertEquals(50.0F, CreatureCombat.adjustedDamage(CreatureKind.VAMPIRE, 10.0F, false, true, true, false));
        assertTrue(CreatureKind.CRIMSON_MATRIARCH.isSupernatural());
        assertTrue(CreatureKind.BLOOD_THRALL.isVampiric());
        assertFalse(CreatureKind.WEREWOLF_HUNTER.isSupernatural());
    }
}
