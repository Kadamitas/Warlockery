package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CreatureCombatTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

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
    void woodenAndBoneBoltsUseTheirOwnCreatureWeaknesses() {
        assertEquals(15.0F, CreatureCombat.adjustedDamage(CreatureKind.VAMPIRE, 10.0F, false, true, true, false));
        assertEquals(20.0F, CreatureCombat.adjustedDamage(CreatureKind.ENT, 10.0F, false, true, false, false));
        assertEquals(15.0F, CreatureCombat.adjustedDamage(CreatureKind.DEMON, 10.0F, false, false, true, false));
        assertTrue(CreatureKind.NAAMAH.isSupernatural());
        assertTrue(CreatureKind.BLOOD_THRALL.isVampiric());
        assertFalse(CreatureKind.WEREWOLF_HUNTER.isSupernatural());
    }

    @Test
    void poisonAndWitherPersistThroughNullification() {
        assertTrue(CreatureCombat.persistsThroughNullification(net.minecraft.world.effect.MobEffects.POISON));
        assertTrue(CreatureCombat.persistsThroughNullification(net.minecraft.world.effect.MobEffects.WITHER));
        assertFalse(CreatureCombat.persistsThroughNullification(net.minecraft.world.effect.MobEffects.STRENGTH));
    }

    @Test
    void deathNeverLosesMoreThanFifteenHealthToOneHit() {
        assertEquals(15.0F, CreatureCombat.adjustedDamage(CreatureKind.DEATH, 80.0F, false, false, false, false));
        assertEquals(8.0F, CreatureCombat.adjustedDamage(CreatureKind.DEATH, 8.0F, false, false, false, false));
    }
}
