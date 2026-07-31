package com.kadamitas.warlockery.transformation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.transformation.SupernaturalAbilityRules.BatCommandTarget;
import com.kadamitas.warlockery.transformation.VampireProgressionRules.BloodPower;
import com.kadamitas.warlockery.transformation.VampireProgressionRules.ChargeIngredient;
import com.kadamitas.warlockery.transformation.VampireProgressionRules.ChargedBloodPower;
import com.kadamitas.warlockery.transformation.VampireProgressionRules.Diagnostic;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SupernaturalAbilityRulesTest {
    @Test
    void knockbackRequiresLevelThreeSneakingAndADirectMeleeAttack() {
        assertFalse(SupernaturalAbilityRules.vampireKnockbackActive(2, true, true));
        assertFalse(SupernaturalAbilityRules.vampireKnockbackActive(3, false, true));
        assertFalse(SupernaturalAbilityRules.vampireKnockbackActive(3, true, false));
        assertTrue(SupernaturalAbilityRules.vampireKnockbackActive(3, true, true));
    }

    @Test
    void speedStacksFiveUsesAndHighLevelBloodRushReachesTenfoldStrength() {
        assertEquals(-1, SupernaturalAbilityRules.nextBloodRushAmplifier(3, -1));
        assertEquals(4, repeatBloodRush(4, 5));
        assertEquals(9, repeatBloodRush(8, 5));
        assertEquals(9, SupernaturalAbilityRules.nextBloodRushAmplifier(10, 9));
    }

    @Test
    void resistSunUnlocksAtFiveAndScalesFromTwentyToOneHundredSeconds() {
        assertFalse(SupernaturalAbilityRules.resistsSun(4));
        assertEquals(0, SupernaturalAbilityRules.sunToleranceTicks(4));
        assertTrue(SupernaturalAbilityRules.resistsSun(5));
        assertEquals(20 * 20, SupernaturalAbilityRules.sunToleranceTicks(5));
        assertEquals(100 * 20, SupernaturalAbilityRules.sunToleranceTicks(10));
        assertEquals(
            175,
            SupernaturalAbilityRules.sunlightBloodCost(5, VampireProgressionRules.bloodCapacityAt(5))
        );
        assertEquals(
            70,
            SupernaturalAbilityRules.sunlightBloodCost(10, VampireProgressionRules.bloodCapacityAt(10))
        );
    }

    @Test
    void batswarmFormNeedsLevelSevenSlowsFeedingAndExpiresDeterministically() {
        assertFalse(SupernaturalAbilityRules.batSwarmFormActive(6, 200, 100));
        assertTrue(SupernaturalAbilityRules.batSwarmFormActive(7, 200, 100));
        assertFalse(SupernaturalAbilityRules.batSwarmFormActive(7, 100, 100));
        assertEquals(3, SupernaturalAbilityRules.BATSWARM_VISUAL_COUNT);
        assertEquals(3, SupernaturalAbilityRules.bloodSipAmount(10, true));
        assertEquals(1, SupernaturalAbilityRules.bloodSipAmount(2, true));
        assertEquals(10, SupernaturalAbilityRules.bloodSipAmount(10, false));
    }

    @Test
    void bloodPowerChargeReplacesThePreviousFiveChargePower() {
        final var runtimeCharge = SupernaturalAbilityRules.replaceBloodPower(
            Map.of(
                SupernaturalPower.CALL_STORM, 3,
                SupernaturalPower.TELEPORT, 0,
                SupernaturalPower.SUMMON_BATS, 0
            ),
            SupernaturalPower.TELEPORT
        );
        final var charged = VampireProgressionRules.chargeBloodPower(
            10,
            ChargedBloodPower.empty(),
            BloodPower.CALL_STORM,
            ChargeIngredient.WATER_ARTICHOKE_GLOBE,
            true
        );
        final var replaced = VampireProgressionRules.chargeBloodPower(
            10,
            charged.after(),
            BloodPower.TELEPORT,
            ChargeIngredient.BONE,
            true
        );

        assertTrue(runtimeCharge.replaced());
        assertEquals(0, runtimeCharge.charges().get(SupernaturalPower.CALL_STORM));
        assertEquals(5, runtimeCharge.charges().get(SupernaturalPower.TELEPORT));
        assertEquals(0, runtimeCharge.charges().get(SupernaturalPower.SUMMON_BATS));
        assertEquals(Diagnostic.BLOOD_POWER_REPLACED, replaced.diagnostic());
        assertEquals(BloodPower.TELEPORT, replaced.after().power().orElseThrow());
        assertEquals(SupernaturalAbilityRules.BLOOD_POWER_CHARGES, replaced.after().charges());
    }

    @Test
    void batSwarmUsesFifteenAttackersAndGazeOverridesRetaliation() {
        assertEquals(15, SupernaturalAbilityRules.ATTACKING_BAT_COUNT);
        assertEquals(BatCommandTarget.GAZE, SupernaturalAbilityRules.batCommandTarget(true, true));
        assertEquals(BatCommandTarget.RETALIATION, SupernaturalAbilityRules.batCommandTarget(false, true));
        assertEquals(BatCommandTarget.NONE, SupernaturalAbilityRules.batCommandTarget(false, false));
    }

    @Test
    void sprintingDamageRequiresLevelSixAChangedShapeAndDirectContact() {
        assertEquals(0.0F, SupernaturalAbilityRules.sprintingDamageBonus(5, WerewolfShape.WOLF, true, true));
        assertEquals(0.0F, SupernaturalAbilityRules.sprintingDamageBonus(6, WerewolfShape.HUMAN, true, true));
        assertEquals(0.0F, SupernaturalAbilityRules.sprintingDamageBonus(6, WerewolfShape.WOLF, false, true));
        assertEquals(4.1F, SupernaturalAbilityRules.sprintingDamageBonus(6, WerewolfShape.WOLF, true, true));
    }

    @Test
    void armorRendingBelongsOnlyToLevelNineWolfmanForm() {
        assertFalse(SupernaturalAbilityRules.armorRendingActive(8, WerewolfShape.WOLFMAN));
        assertFalse(SupernaturalAbilityRules.armorRendingActive(9, WerewolfShape.HUMAN));
        assertFalse(SupernaturalAbilityRules.armorRendingActive(9, WerewolfShape.WOLF));
        assertTrue(SupernaturalAbilityRules.armorRendingActive(9, WerewolfShape.WOLFMAN));
        final double piercingInput = SupernaturalAbilityRules.armorPiercingInputDamage(
            10.0,
            input -> input * 0.2
        );
        assertEquals(10.0, piercingInput * 0.2, 0.001);
    }

    @Test
    void spreadCurseRequiresLevelTenNearFatalContactAndUnprotectedPrey() {
        assertTrue(SupernaturalAbilityRules.canSpreadWerewolfCurse(
            10,
            WerewolfShape.WOLFMAN,
            true,
            true,
            true,
            false,
            true
        ));
        assertFalse(SupernaturalAbilityRules.canSpreadWerewolfCurse(
            10,
            WerewolfShape.WOLFMAN,
            true,
            true,
            true,
            true,
            true
        ));
        assertFalse(SupernaturalAbilityRules.canSpreadWerewolfCurse(
            10,
            WerewolfShape.WOLFMAN,
            true,
            false,
            true,
            false,
            true
        ));
    }

    @Test
    void paralysedWolfTrapConditionExcludesHumanFormWerewolves() {
        assertFalse(SupernaturalAbilityRules.wolfTrapParalyzes(
            SupernaturalForm.WEREWOLF,
            WerewolfShape.HUMAN,
            true
        ));
        assertTrue(SupernaturalAbilityRules.wolfTrapParalyzes(
            SupernaturalForm.WEREWOLF,
            WerewolfShape.WOLF,
            true
        ));
        assertTrue(SupernaturalAbilityRules.wolfTrapParalyzes(
            SupernaturalForm.WEREWOLF,
            WerewolfShape.WOLFMAN,
            true
        ));
        assertFalse(SupernaturalAbilityRules.wolfTrapParalyzes(
            SupernaturalForm.WEREWOLF,
            WerewolfShape.WOLF,
            false
        ));
    }

    private static int repeatBloodRush(final int level, final int uses) {
        int amplifier = -1;
        for (int use = 0; use < uses; use++) {
            amplifier = SupernaturalAbilityRules.nextBloodRushAmplifier(level, amplifier);
        }
        return amplifier;
    }
}
