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
    void hexBatKeepsExactHolyWeaknessWithoutNewSilverWoodOrSupernaturalBehavior() {
        // Holy Bolt against the exact Hex Bat kind stays a 1.5x consecrated weakness.
        // The registered entity is dispatched with the spirit flag today; the deferred
        // CreatureCombat exact-kind rule preserves the same observable result once
        // HexBatEntity stops being a SpiritMob.
        assertEquals(15.0F, CreatureCombat.adjustedDamage(CreatureKind.HEX_BAT, 10.0F, false, false, true, true));
        // Ordinary damage: no supernatural reduction now or after the change.
        assertFalse(CreatureKind.HEX_BAT.isSupernatural());
        assertEquals(10.0F, CreatureCombat.adjustedDamage(CreatureKind.HEX_BAT, 10.0F, false, false, false, true));
        // Silver and wooden bolts gain no new Hex Bat weakness.
        assertEquals(10.0F, CreatureCombat.adjustedDamage(CreatureKind.HEX_BAT, 10.0F, true, false, false, true));
        assertFalse(CreatureKind.HEX_BAT.isWoodenVulnerable());
        assertEquals(10.0F, CreatureCombat.adjustedDamage(CreatureKind.HEX_BAT, 10.0F, false, true, false, true));
        // Not undead, demonic, or a classic familiar.
        assertFalse(CreatureKind.HEX_BAT.isUndead());
        assertFalse(CreatureKind.HEX_BAT.isDemonic());
        assertFalse(FamiliarBondRules.isClassicFamiliar(CreatureKind.HEX_BAT));
        // Non-Hex parity: an unrelated spirit kind keeps the identical matrix.
        assertEquals(15.0F, CreatureCombat.adjustedDamage(CreatureKind.BANSHEE, 10.0F, false, false, true, true));
        assertEquals(1.5F, CreatureCombat.adjustedDamage(CreatureKind.WEREWOLF, 10.0F, false, false, false, false));
    }

    /**
     * DELIBERATE DEFERRED-WIRING RED. Once the deferred ModEntities edit
     * routes hex_bat through HexBatEntity, the spirit flag is false and ONLY
     * the deferred CreatureCombat exact-kind clause preserves the observable
     * 1.5x Holy Bolt result. This exact case fails until coordinator deferred
     * edit 4 lands, so omitting that edit can never pass the suite silently.
     */
    @Test
    void hexBatHolyWeaknessSurvivesLeavingTheSpiritClass() {
        assertEquals(15.0F, CreatureCombat.adjustedDamage(
            CreatureKind.HEX_BAT, 10.0F, false, false, true, false
        ));
        // The exact-kind clause must not leak to any other non-spirit kind.
        assertEquals(1.5F, CreatureCombat.adjustedDamage(
            CreatureKind.WEREWOLF, 10.0F, false, false, true, false
        ));
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
    void typedAntiWerewolfDamageBypassesOnlyGenericResistanceWithoutSilverDoubling() {
        assertEquals(10.0F, CreatureCombat.adjustedDamage(
            CreatureKind.WEREWOLF, 10.0F, false, false, false, false, true, true
        ));
        assertEquals(10.0F, CreatureCombat.adjustedDamage(
            CreatureKind.LYCAN_VILLAGER, 10.0F, false, false, false, false, true, true
        ));
        assertEquals(20.0F, CreatureCombat.adjustedDamage(
            CreatureKind.WEREWOLF, 10.0F, true, false, false, false, true, true
        ), "silver keeps its own doubling even when the typed source is also present");
        assertEquals(1.5F, CreatureCombat.adjustedDamage(
            CreatureKind.VAMPIRE, 10.0F, false, false, false, false, false, true
        ), "the typed source must not weaken non-werewolf supernatural targets");
        assertEquals(10.0F, CreatureCombat.adjustedDamage(
            CreatureKind.DEMON, 10.0F, false, false, false, false, false, true
        ), "non-supernatural kinds keep their ordinary full damage with or without the typed source");
        assertEquals(1.5F, CreatureCombat.adjustedDamage(
            CreatureKind.WEREWOLF, 10.0F, false, false, false, false, true, false
        ), "without the typed source ordinary magic keeps the generic reduction");
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
