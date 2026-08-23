package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile.Feature;
import com.kadamitas.warlockery.entity.CreatureVisualProfile.Archetype;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.monster.Vex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CustomCreatureIdentityTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }


    @Test
    void entIsADedicatedPathfinderWithNoGolemOrGenericControllerIdentity() {
        assertEquals(net.minecraft.world.entity.PathfinderMob.class, EntEntity.class.getSuperclass());
        assertTrue(ArcaneCreature.class.isAssignableFrom(EntEntity.class));
        assertFalse(net.minecraft.world.entity.animal.golem.IronGolem.class.isAssignableFrom(EntEntity.class));
        assertFalse(net.minecraft.world.entity.animal.golem.AbstractGolem.class.isAssignableFrom(EntEntity.class));
        assertFalse(ArcaneMob.class.isAssignableFrom(EntEntity.class));
        assertEquals(200.0D, EntEntity.BASE_MAX_HEALTH);
        assertEquals(15.0D, EntEntity.BASE_ATTACK_DAMAGE);
        assertEquals(0.25D, EntEntity.BASE_MOVEMENT_SPEED);
        assertEquals(2.0D, EntEntity.BASE_ARMOR);
        assertEquals(16.0D, EntEntity.BASE_FOLLOW_RANGE);
        assertEquals(0, EntEntity.BASE_XP_REWARD);
    }

    @Test
    void thornedPursuerIsADedicatedAdultEmptyMonster() {
        assertEquals(net.minecraft.world.entity.monster.Monster.class,
            ThornedPursuerEntity.class.getSuperclass());
        assertTrue(java.lang.reflect.Modifier.isFinal(ThornedPursuerEntity.class.getModifiers()));
        assertTrue(ArcaneCreature.class.isAssignableFrom(ThornedPursuerEntity.class));
        assertFalse(net.minecraft.world.entity.monster.zombie.Zombie.class
            .isAssignableFrom(ThornedPursuerEntity.class));
        assertFalse(ArcaneMob.class.isAssignableFrom(ThornedPursuerEntity.class));
        assertEquals(100.0D, ThornedPursuerEntity.BASE_MAX_HEALTH);
        assertEquals(11.0D, ThornedPursuerEntity.BASE_ATTACK_DAMAGE);
        assertEquals(8.0D, ThornedPursuerEntity.BASE_ARMOR);
        assertEquals(35.0D, ThornedPursuerEntity.BASE_FOLLOW_RANGE);
        assertEquals(0.23D, ThornedPursuerEntity.BASE_MOVEMENT_SPEED);
        assertEquals(0.0D, ThornedPursuerEntity.BASE_REINFORCEMENT_CHANCE);
        assertEquals(5, ThornedPursuerEntity.BASE_XP);
        assertEquals(0, ThornedPursuerEntity.LIFECYCLE_EQUIPMENT_SLOTS);
        assertEquals(java.util.Set.of(
            "minecraft:baby", "minecraft:random_spawn_bonus", "minecraft:zombie_random_spawn_bonus",
            "minecraft:leader_zombie_bonus", "minecraft:reinforcement_caller_charge",
            "minecraft:reinforcement_callee_charge", "warlockery:thorned_pursuer_course"),
            ThornedPursuerEntity.lifecycleModifierIds());
        assertTrue(java.util.Arrays.stream(ThornedPursuerEntity.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("remove")),
            "removal must own transient and escort teardown");
        assertTrue(java.util.Arrays.stream(ThornedPursuerEntity.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("teleport")),
            "external teleport and dimension change must own full transient teardown");
    }

    @Test
    void livingRootsAreSeparateDedicatedNonZombieMonstersWithExactPublicBodies() {
        for (final Class<?> type : java.util.List.of(MandrakeEntity.class, DreamrootEntity.class)) {
            assertEquals(net.minecraft.world.entity.monster.Monster.class, type.getSuperclass());
            assertTrue(ArcaneCreature.class.isAssignableFrom(type));
            assertTrue(java.lang.reflect.Modifier.isFinal(type.getModifiers()));
            assertFalse(net.minecraft.world.entity.monster.zombie.Zombie.class.isAssignableFrom(type));
            assertFalse(ArcaneMob.class.isAssignableFrom(type));
        }
        assertEquals(20.0D, MandrakeEntity.BASE_MAX_HEALTH);
        assertEquals(35.0D, MandrakeEntity.BASE_FOLLOW_RANGE);
        assertEquals(0.23D, MandrakeEntity.BASE_MOVEMENT_SPEED);
        assertEquals(3.0D, MandrakeEntity.BASE_ATTACK_DAMAGE);
        assertEquals(2.0D, MandrakeEntity.BASE_ARMOR);
        assertEquals(5, MandrakeEntity.XP_REWARD);
        assertEquals(MandrakeEntity.BASE_MAX_HEALTH, DreamrootEntity.BASE_MAX_HEALTH);
        assertEquals(MandrakeEntity.BASE_FOLLOW_RANGE, DreamrootEntity.BASE_FOLLOW_RANGE);
        assertEquals(MandrakeEntity.BASE_MOVEMENT_SPEED, DreamrootEntity.BASE_MOVEMENT_SPEED);
        assertEquals(MandrakeEntity.BASE_ATTACK_DAMAGE, DreamrootEntity.BASE_ATTACK_DAMAGE);
        assertEquals(MandrakeEntity.BASE_ARMOR, DreamrootEntity.BASE_ARMOR);
        assertEquals(MandrakeEntity.XP_REWARD, DreamrootEntity.XP_REWARD);
        assertEquals(0.55F, CreatureVisualProfile.forKind(CreatureKind.MANDRAKE).width());
        assertEquals(0.81F, CreatureVisualProfile.forKind(CreatureKind.MANDRAKE).height());
        assertEquals(0.9F, CreatureVisualProfile.forKind(CreatureKind.DREAMROOT).width());
        assertEquals(1.62F, CreatureVisualProfile.forKind(CreatureKind.DREAMROOT).height());
    }

    @Test
    void brambleColossusIsADedicatedNonZombieMonster() {
        assertEquals(net.minecraft.world.entity.monster.Monster.class, BrambleColossusEntity.class.getSuperclass());
        assertFalse(net.minecraft.world.entity.monster.zombie.Zombie.class.isAssignableFrom(BrambleColossusEntity.class));
        assertFalse(ArcaneMob.class.isAssignableFrom(BrambleColossusEntity.class));
        assertTrue(ArcaneCreature.class.isAssignableFrom(BrambleColossusEntity.class));
        assertEquals(36.0D, BrambleColossusEntity.BASE_MAX_HEALTH);
        assertEquals(7.0D, BrambleColossusEntity.BASE_ATTACK_DAMAGE);
        assertEquals(0.3D, BrambleColossusEntity.BASE_MOVEMENT_SPEED);
        assertEquals(1.3F, CreatureVisualProfile.forKind(CreatureKind.BRAMBLE_COLOSSUS).width());
        assertEquals(2.61F, CreatureVisualProfile.forKind(CreatureKind.BRAMBLE_COLOSSUS).height());
    }
    @Test
    void hexBatIsADedicatedNonVexNonSpiritMonster() {
        assertEquals(net.minecraft.world.entity.monster.Monster.class, HexBatEntity.class.getSuperclass());
        assertFalse(Vex.class.isAssignableFrom(HexBatEntity.class),
            "the dedicated Hex Bat must not inherit Vex phasing, goals, or owner-copy behavior");
        assertFalse(SpiritMob.class.isAssignableFrom(HexBatEntity.class),
            "the dedicated Hex Bat must not carry the SpiritMob class identity");
        assertTrue(ArcaneCreature.class.isAssignableFrom(HexBatEntity.class));
        assertTrue(java.lang.reflect.Modifier.isFinal(HexBatEntity.class.getModifiers()));
        final CreatureVisualProfile visual = CreatureVisualProfile.forKind(CreatureKind.HEX_BAT);
        assertEquals(0.5F, visual.width());
        assertEquals(0.63F, visual.height());
        assertEquals(Archetype.SPIRIT, visual.archetype(),
            "registry-owned dimensions and archetype stay exact");
    }

    @Test
    void impAndStormSimianArePurposeBuiltMobsRatherThanVexCopies() {
        assertEquals(WingedArcaneMob.class, ImpEntity.class.getSuperclass());
        assertEquals(WingedArcaneMob.class, StormSimianEntity.class.getSuperclass());
        assertFalse(Vex.class.isAssignableFrom(ImpEntity.class));
        assertFalse(Vex.class.isAssignableFrom(StormSimianEntity.class));
        assertEquals(Archetype.IMP, CreatureVisualProfile.forKind(CreatureKind.IMP).archetype());
        assertEquals(Archetype.SIMIAN, CreatureVisualProfile.forKind(CreatureKind.STORM_SIMIAN).archetype());
    }

    @Test
    void stormSimianRetainsCompanionTravelAndProtectionBehavior() {
        final CreatureBehaviorProfile profile = CreatureBehaviorProfile.find(CreatureKind.STORM_SIMIAN).orElseThrow();
        assertTrue(profile.has(Feature.FAMILIAR_BOND));
        assertTrue(profile.has(Feature.WAYSTONE_TRAVEL));
        assertTrue(profile.has(Feature.PROTECT_OWNER));
    }

    @Test
    void bansheeIsADedicatedNonEnemyPathfinderRatherThanAVexCopy() {
        assertEquals(net.minecraft.world.entity.PathfinderMob.class, BansheeEntity.class.getSuperclass());
        assertTrue(java.lang.reflect.Modifier.isFinal(BansheeEntity.class.getModifiers()));
        assertTrue(ArcaneCreature.class.isAssignableFrom(BansheeEntity.class));
        assertFalse(Vex.class.isAssignableFrom(BansheeEntity.class));
        assertFalse(net.minecraft.world.entity.monster.zombie.Zombie.class.isAssignableFrom(BansheeEntity.class));
        assertFalse(SpiritMob.class.isAssignableFrom(BansheeEntity.class));
        assertFalse(WingedArcaneMob.class.isAssignableFrom(BansheeEntity.class));
        assertFalse(net.minecraft.world.entity.monster.Enemy.class.isAssignableFrom(BansheeEntity.class),
            "the Banshee must not implement Enemy: no sleep prevention and no golem auto-targeting");
        assertFalse(net.minecraft.world.entity.monster.Monster.class.isAssignableFrom(BansheeEntity.class));
        assertEquals(Archetype.SPIRIT, CreatureVisualProfile.forKind(CreatureKind.BANSHEE).archetype());
        assertEquals(0.65F, CreatureVisualProfile.forKind(CreatureKind.BANSHEE).width());
        assertEquals(1.98F, CreatureVisualProfile.forKind(CreatureKind.BANSHEE).height());
    }

    @Test
    void feralLycanIsADedicatedSubclassOfTheExtensibleWerewolfClass() {
        assertEquals(WerewolfEntity.class, FeralLycanEntity.class.getSuperclass());
        assertEquals(ArcaneMob.class, WerewolfEntity.class.getSuperclass());
        assertFalse(java.lang.reflect.Modifier.isFinal(WerewolfEntity.class.getModifiers()),
            "WerewolfEntity is extensible only for the dedicated Feral subclass");
        assertTrue(java.lang.reflect.Modifier.isFinal(FeralLycanEntity.class.getModifiers()));
    }

    @Test
    void lycanVariantsDeclareDistinctSemanticIdentities() {
        assertEquals(LycanPackRules.Variant.values().length, 2);
        assertTrue(WerewolfEntity.class.isAssignableFrom(FeralLycanEntity.class),
            "class-based hunter and Pillager systems keep recognizing the Feral by inheritance");
    }

    @Test
    void eldritchWatcherIsADedicatedVexFlightSubclassWithFixedIdentity() {
        assertEquals(Vex.class, EldritchWatcherEntity.class.getSuperclass(),
            "the dedicated Watcher deliberately keeps the Vex phasing flight chassis");
        assertTrue(ArcaneCreature.class.isAssignableFrom(EldritchWatcherEntity.class));
        assertTrue(java.lang.reflect.Modifier.isFinal(EldritchWatcherEntity.class.getModifiers()));
        final CreatureVisualProfile visual = CreatureVisualProfile.forKind(CreatureKind.ELDRITCH_WATCHER);
        assertEquals(0.8F, visual.width());
        assertEquals(1.35F, visual.height());
        assertEquals(Archetype.SPIRIT, visual.archetype());
    }

    @Test
    void corpseIsADedicatedNonZombieNonArcaneMobMonsterWithExactBases() {
        assertEquals(net.minecraft.world.entity.monster.Monster.class, CorpseEntity.class.getSuperclass());
        assertTrue(java.lang.reflect.Modifier.isFinal(CorpseEntity.class.getModifiers()));
        assertFalse(net.minecraft.world.entity.monster.zombie.Zombie.class.isAssignableFrom(CorpseEntity.class));
        assertFalse(ArcaneMob.class.isAssignableFrom(CorpseEntity.class));
        assertTrue(ArcaneCreature.class.isAssignableFrom(CorpseEntity.class));
        assertEquals(20.0D, CorpseEntity.BASE_MAX_HEALTH);
        assertEquals(35.0D, CorpseEntity.BASE_FOLLOW_RANGE);
        assertEquals(0.23D, CorpseEntity.BASE_MOVEMENT_SPEED);
        assertEquals(3.0D, CorpseEntity.BASE_ATTACK_DAMAGE);
        assertEquals(2.0D, CorpseEntity.BASE_ARMOR);
        assertEquals(0.0D, CorpseEntity.BASE_REINFORCEMENT_CHANCE);
        assertEquals(0.6F, CreatureVisualProfile.forKind(CreatureKind.CORPSE).width());
        assertEquals(1.71F, CreatureVisualProfile.forKind(CreatureKind.CORPSE).height());
        assertTrue(CreatureKind.CORPSE.isUndead(), "Smite and Holy classification remains");
    }

    @Test
    void hedgeCroneIsADedicatedNonZombieMonsterWithFixedIdentity() {
        assertEquals(net.minecraft.world.entity.monster.Monster.class,
            HedgeCroneEntity.class.getSuperclass());
        assertTrue(java.lang.reflect.Modifier.isFinal(HedgeCroneEntity.class.getModifiers()));
        assertTrue(ArcaneCreature.class.isAssignableFrom(HedgeCroneEntity.class));
        assertFalse(net.minecraft.world.entity.monster.zombie.Zombie.class
            .isAssignableFrom(HedgeCroneEntity.class),
            "no baby, jockey, equipment roll, doors, reinforcement, villager conversion, "
                + "turtle grief, or Drowned conversion may survive");
        assertFalse(ArcaneMob.class.isAssignableFrom(HedgeCroneEntity.class));
        assertFalse(Vex.class.isAssignableFrom(HedgeCroneEntity.class));
        assertFalse(SpiritMob.class.isAssignableFrom(HedgeCroneEntity.class));
        assertFalse(CircleMageEntity.class.isAssignableFrom(HedgeCroneEntity.class),
            "the two practitioners are never each other's social variant");
        assertEquals(60.0D, HedgeCroneEntity.BASE_MAX_HEALTH);
        assertEquals(9.0D, HedgeCroneEntity.BASE_ATTACK_DAMAGE);
        assertEquals(6.0D, HedgeCroneEntity.BASE_ARMOR);
        assertEquals(35.0D, HedgeCroneEntity.BASE_FOLLOW_RANGE);
        assertEquals(0.23D, HedgeCroneEntity.BASE_MOVEMENT_SPEED);
        final CreatureVisualProfile visual = CreatureVisualProfile.forKind(CreatureKind.HEDGE_CRONE);
        assertEquals(0.65F, visual.width());
        assertEquals(2.7F, visual.height());
        assertEquals(Archetype.BOSS, visual.archetype());
    }

    @Test
    void circleMageIsADedicatedNonZombieMonsterWithFixedIdentity() {
        assertEquals(net.minecraft.world.entity.monster.Monster.class,
            CircleMageEntity.class.getSuperclass());
        assertTrue(java.lang.reflect.Modifier.isFinal(CircleMageEntity.class.getModifiers()));
        assertTrue(ArcaneCreature.class.isAssignableFrom(CircleMageEntity.class));
        assertFalse(net.minecraft.world.entity.monster.zombie.Zombie.class
            .isAssignableFrom(CircleMageEntity.class));
        assertFalse(ArcaneMob.class.isAssignableFrom(CircleMageEntity.class));
        assertFalse(HedgeCroneEntity.class.isAssignableFrom(CircleMageEntity.class),
            "the two practitioners never share a controller or a class identity");
        assertEquals(20.0D, CircleMageEntity.BASE_MAX_HEALTH);
        assertEquals(3.0D, CircleMageEntity.BASE_ATTACK_DAMAGE);
        assertEquals(2.0D, CircleMageEntity.BASE_ARMOR);
        assertEquals(35.0D, CircleMageEntity.BASE_FOLLOW_RANGE);
        assertEquals(0.23D, CircleMageEntity.BASE_MOVEMENT_SPEED);
        final CreatureVisualProfile visual = CreatureVisualProfile.forKind(CreatureKind.CIRCLE_MAGE);
        assertEquals(0.6F, visual.width());
        assertEquals(1.8F, visual.height());
        assertEquals(Archetype.HUMANOID, visual.archetype());
    }

    @Test
    void theTwoCovenPractitionersKeepBehaviorallyDistinctMotives() {
        assertNotEquals(HedgeCroneRules.WITHDRAW_HEALTH_FRACTION,
            CircleMageRules.WITHDRAW_HEALTH_FRACTION);
        assertNotEquals(HedgeCroneRules.HEX_WINDUP_TICKS, CircleMageRules.BOLT_WINDUP_TICKS);
        assertNotEquals(HedgeCroneRules.CAST_RECOVERY_TICKS, CircleMageRules.BOLT_RECOVERY_TICKS);
        assertEquals(HedgeCroneRules.Mode.values().length, 6);
        assertEquals(CircleMageRules.Mode.values().length, 5);
        // The distinctness claim is about MOTIVE vocabulary, not about every helper. The two rule
        // classes deliberately do share a bounded-safety vocabulary (route retry, deadline clamps,
        // health fractions, cadence stagger, and one geometric search envelope), because the
        // approved design permits reusing common safety shapes and because duplicating that
        // enumeration is what previously produced an unreachable search envelope in both mobs.
        final java.util.Set<String> croneMethods = declaredMethodNames(HedgeCroneRules.class);
        final java.util.Set<String> mageMethods = declaredMethodNames(CircleMageRules.class);

        final java.util.Set<String> croneMotives = java.util.Set.of(
            "selectHex", "hexDurationTicks", "hexAmplifier", "wardDamage", "wardDischarges",
            "wardPreparationAllowed", "warningEscalates", "boundaryCandidate", "threatReleases",
            "anchorReturnRequired", "mayAdoptReplacementAnchor", "castEligible");
        final java.util.Set<String> mageMotives = java.util.Set.of(
            "recruitmentDecision", "auraProvider", "auraEligible", "conclaveAdmits", "acceptPeers",
            "coordinator", "sessionSlot", "sessionReleased", "mayEmitReport", "reportRecipients",
            "reportAcceptable", "formationSlot", "safeStepAllowed", "boltEligible", "boltDamage",
            "consumesFocus", "studySearchAllowed");

        assertTrue(croneMethods.containsAll(croneMotives), "the Crone owns its own motives");
        assertTrue(mageMethods.containsAll(mageMotives), "the Mage owns its own motives");
        croneMotives.forEach(motive -> assertFalse(mageMethods.contains(motive),
            "the Circle Mage must not carry the Hedge Crone motive " + motive));
        mageMotives.forEach(motive -> assertFalse(croneMethods.contains(motive),
            "the Hedge Crone must not carry the Circle Mage motive " + motive));

        // The shared safety vocabulary is declared explicitly rather than pretended away.
        final java.util.Set<String> shared = new java.util.TreeSet<>(croneMethods);
        shared.retainAll(mageMethods);
        assertEquals(java.util.Set.of(
            "clampRemaining", "decrementLoaded", "healthFraction", "mayRetarget",
            "pathRequestAllowed", "priority", "rank", "relationLegal", "routeBackoffAfter",
            "routeExhausted", "routeFailuresAfter", "safeCandidatePreference",
            "safeSearchOffsets", "select", "shouldWithdraw", "stableOffset",
            "workstationOffsets"
        ), shared, "the shared surface is exactly the bounded-safety vocabulary, nothing more");
    }

    /** Declared method names excluding compiler-synthesized lambda bodies. */
    private static java.util.Set<String> declaredMethodNames(final Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredMethods())
            .filter(method -> !method.isSynthetic())
            .map(java.lang.reflect.Method::getName)
            .filter(name -> !name.startsWith("lambda$"))
            .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void lycanVillagersTradeOnlyWithWerewolfPlayers() {
        assertTrue(LycanVillagerEntity.canTrade(SupernaturalForm.WEREWOLF));
        assertFalse(LycanVillagerEntity.canTrade(SupernaturalForm.NONE));
        assertFalse(LycanVillagerEntity.canTrade(SupernaturalForm.VAMPIRE));
    }
}
