package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile.AuditStatus;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile.Feature;
import com.kadamitas.warlockery.ritual.HuntsmanSummoningStructure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class CreatureIntegrityClosureTest {
    private static final UUID OWNER = UUID.fromString("cdd1f4eb-6697-4943-a9d3-3d64a2e922ce");
    private static final UUID OTHER = UUID.fromString("e01acb90-30b6-43e8-bf0b-f9d4c680a68e");

    @TestFactory
    Stream<DynamicContainer> oneFailureStateAndSuccessSuitePerIntegrityPage() {
        return Stream.of(
            suite("Death", CreatureIntegrityClosureTest::deathFailure,
                CreatureIntegrityClosureTest::deathState, CreatureIntegrityClosureTest::deathSuccess),
            suite("Binky", CreatureIntegrityClosureTest::paleSteedFailure,
                CreatureIntegrityClosureTest::paleSteedState, CreatureIntegrityClosureTest::paleSteedSuccess),
            suite("Nightmare", CreatureIntegrityClosureTest::nightmareFailure,
                CreatureIntegrityClosureTest::nightmareState, CreatureIntegrityClosureTest::nightmareSuccess),
            suite("Familiar Recall", CreatureIntegrityClosureTest::familiarRecallFailure,
                CreatureIntegrityClosureTest::familiarRecallState, CreatureIntegrityClosureTest::familiarRecallSuccess),
            suite("Demon Patron", CreatureIntegrityClosureTest::demonFailure,
                CreatureIntegrityClosureTest::demonState, CreatureIntegrityClosureTest::demonSuccess),
            suite("Spirit", CreatureIntegrityClosureTest::spiritFailure,
                CreatureIntegrityClosureTest::spiritState, CreatureIntegrityClosureTest::spiritSuccess),
            suite("Hobgoblin", CreatureIntegrityClosureTest::hobgoblinFailure,
                CreatureIntegrityClosureTest::hobgoblinState, CreatureIntegrityClosureTest::hobgoblinSuccess),
            suite("Gulg", CreatureIntegrityClosureTest::forgewardenFailure,
                CreatureIntegrityClosureTest::forgewardenState, CreatureIntegrityClosureTest::forgewardenSuccess),
            suite("Mog", CreatureIntegrityClosureTest::stonebrokerFailure,
                CreatureIntegrityClosureTest::stonebrokerState, CreatureIntegrityClosureTest::stonebrokerSuccess),
            suite("Bloodied Wicker Bundle", CreatureIntegrityClosureTest::wickerFailure,
                CreatureIntegrityClosureTest::wickerState, CreatureIntegrityClosureTest::wickerSuccess),
            suite("Baba Yaga", CreatureIntegrityClosureTest::babaFailure,
                CreatureIntegrityClosureTest::babaState, CreatureIntegrityClosureTest::babaSuccess)
        );
    }

    private static DynamicContainer suite(
        final String page,
        final Runnable failure,
        final Runnable state,
        final Runnable success
    ) {
        return DynamicContainer.dynamicContainer(page, List.of(
            DynamicTest.dynamicTest("failure is bounded", failure::run),
            DynamicTest.dynamicTest("state is exposed", state::run),
            DynamicTest.dynamicTest("success behavior is wired", success::run)
        ));
    }

    private static void deathFailure() {
        assertFalse(DeathImpersonationRules.qualifies(false, true, true, true));
        assertFalse(DeathImpersonationRules.qualifies(true, false, true, true));
        assertFalse(DeathImpersonationRules.qualifies(true, true, false, true));
        assertFalse(DeathImpersonationRules.qualifies(true, true, true, false));
    }

    private static void deathState() {
        assertTrue(profile(CreatureKind.DEATH).has(Feature.DEATH_DISGUISE));
    }

    private static void deathSuccess() {
        assertTrue(DeathImpersonationRules.qualifies(true, true, true, true));
    }

    private static void paleSteedFailure() {
        assertFalse(SpectralMountRules.canControl(CreatureKind.PALE_STEED, Optional.of(OTHER), OWNER));
    }

    private static void paleSteedState() {
        assertTrue(profile(CreatureKind.PALE_STEED).has(Feature.RIDEABLE_BOND));
        assertTrue(CompanionCombatRules.requiresDedicatedMeleeGoal(CreatureKind.CAT));
        assertTrue(CompanionCombatRules.requiresDedicatedMeleeGoal(CreatureKind.OWL));
        assertTrue(CompanionCombatRules.requiresDedicatedMeleeGoal(CreatureKind.TOAD));
    }

    private static void paleSteedSuccess() {
        assertTrue(SpectralMountRules.canControl(CreatureKind.PALE_STEED, Optional.of(OWNER), OWNER));
        assertTrue(SpectralMountRules.speed(CreatureKind.PALE_STEED, 0.23) > 0.23F);
    }

    private static void nightmareFailure() {
        assertFalse(SpectralMountRules.canControl(CreatureKind.NIGHTMARE, Optional.empty(), OWNER));
    }

    private static void nightmareState() {
        assertTrue(profile(CreatureKind.NIGHTMARE).has(Feature.RIDEABLE_BOND));
    }

    private static void nightmareSuccess() {
        assertTrue(SpectralMountRules.canControl(CreatureKind.NIGHTMARE, Optional.of(OWNER), OWNER));
        assertTrue(SpectralMountRules.speed(CreatureKind.NIGHTMARE, 0.23)
            > SpectralMountRules.speed(CreatureKind.PALE_STEED, 0.23));
    }

    private static void familiarRecallFailure() {
        assertFalse(FamiliarRecallRules.eligible(true, true, false));
        assertFalse(FamiliarRecallRules.eligible(false, true, true));
    }

    private static void familiarRecallState() {
        assertTrue(Files.exists(Path.of(
            "src", "main", "resources", "data", "warlockery", "ritual", "call_familiar.json"
        )));
    }

    private static void familiarRecallSuccess() {
        assertTrue(FamiliarRecallRules.eligible(true, true, true));
    }

    private static void demonFailure() {
        assertFalse(profile(CreatureKind.DEMON).has(Feature.PASSIVE_UNTIL_HURT));
    }

    private static void demonState() {
        assertTrue(profile(CreatureKind.DEMON).has(Feature.INFERNAL_BARTER));
    }

    private static void demonSuccess() {
        assertTrue(profile(CreatureKind.DEMON).has(Feature.PROTECT_OWNER));
    }

    private static void spiritFailure() {
        assertFalse(SpiritTemperamentRules.canAttack(false, true));
        assertFalse(SpiritTemperamentRules.shouldFlee(true, true, 4.0));
    }

    private static void spiritState() {
        assertTrue(Files.exists(Path.of(
            "src", "main", "resources", "data", "warlockery", "tags", "worldgen", "biome",
            "spirit_habitats.json"
        )));
    }

    private static void spiritSuccess() {
        assertTrue(SpiritTemperamentRules.shouldFlee(false, true, 16.0));
        assertTrue(SpiritTemperamentRules.canAttack(true, true));
    }

    private static void hobgoblinFailure() {
        assertFalse(HobgoblinWorkRules.canWork(false, false, false, true));
        assertEquals(HobgoblinWorkRules.WorkAction.IDLE,
            HobgoblinWorkRules.nextAction(false, true, false));
    }

    private static void hobgoblinState() {
        assertTrue(profile(CreatureKind.HOBGOBLIN).offering().isPresent());
    }

    private static void hobgoblinSuccess() {
        assertTrue(HobgoblinWorkRules.canWork(true, false, false, true));
        assertEquals(HobgoblinWorkRules.WorkAction.COLLECT,
            HobgoblinWorkRules.nextAction(false, false, true));
        assertEquals(HobgoblinWorkRules.WorkAction.DEPOSIT,
            HobgoblinWorkRules.nextAction(true, true, true));
    }

    private static void forgewardenFailure() {
        assertFalse(KoboldBossRules.isBoss(CreatureKind.HOBGOBLIN));
    }

    private static void forgewardenState() {
        assertTrue(profile(CreatureKind.FORGEWARDEN).has(Feature.FORGE_AURA));
    }

    private static void forgewardenSuccess() {
        final KoboldBossRules.CombatProfile combat = KoboldBossRules.combatProfile(CreatureKind.FORGEWARDEN).orElseThrow();
        assertTrue(combat.health() >= 100.0);
        assertTrue(combat.attack() >= 11.0);
    }

    private static void stonebrokerFailure() {
        assertTrue(KoboldBossRules.combatProfile(CreatureKind.CAT).isEmpty());
    }

    private static void stonebrokerState() {
        assertTrue(profile(CreatureKind.STONEBROKER).has(Feature.KOBOLD_AURA));
    }

    private static void stonebrokerSuccess() {
        final KoboldBossRules.CombatProfile combat = KoboldBossRules.combatProfile(CreatureKind.STONEBROKER).orElseThrow();
        assertTrue(combat.health() >= 80.0);
        assertTrue(combat.attack() >= 9.0);
    }

    private static void wickerFailure() {
        assertFalse(HuntsmanSummoningStructure.ready(3));
    }

    private static void wickerState() {
        assertEquals(4, HuntsmanSummoningStructure.positions(net.minecraft.core.BlockPos.ZERO).size());
    }

    private static void wickerSuccess() {
        assertTrue(HuntsmanSummoningStructure.ready(4));
    }

    private static void babaFailure() {
        assertFalse(profile(CreatureKind.HEDGE_CRONE).auditStatus() == AuditStatus.PARTIAL);
    }

    private static void babaState() {
        assertEquals(AuditStatus.MODERN_EQUIVALENT, profile(CreatureKind.HEDGE_CRONE).auditStatus());
    }

    private static void babaSuccess() {
        assertTrue(profile(CreatureKind.HEDGE_CRONE).has(Feature.POTION_VOLLEY));
        assertTrue(profile(CreatureKind.HEDGE_CRONE).has(Feature.THORN_RETALIATION));
    }

    private static CreatureBehaviorProfile profile(final CreatureKind kind) {
        return CreatureBehaviorProfile.find(kind).orElseThrow();
    }
}
