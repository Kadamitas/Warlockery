package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(0.45F, visual.height());
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
        assertEquals(1.8F, CreatureVisualProfile.forKind(CreatureKind.BANSHEE).height());
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
        assertEquals(1.1F, visual.height());
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
        assertEquals(1.95F, CreatureVisualProfile.forKind(CreatureKind.CORPSE).height());
        assertTrue(CreatureKind.CORPSE.isUndead(), "Smite and Holy classification remains");
    }

    @Test
    void lycanVillagersTradeOnlyWithWerewolfPlayers() {
        assertTrue(LycanVillagerEntity.canTrade(SupernaturalForm.WEREWOLF));
        assertFalse(LycanVillagerEntity.canTrade(SupernaturalForm.NONE));
        assertFalse(LycanVillagerEntity.canTrade(SupernaturalForm.VAMPIRE));
    }
}
