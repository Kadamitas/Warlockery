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
    void lycanVillagersTradeOnlyWithWerewolfPlayers() {
        assertTrue(LycanVillagerEntity.canTrade(SupernaturalForm.WEREWOLF));
        assertFalse(LycanVillagerEntity.canTrade(SupernaturalForm.NONE));
        assertFalse(LycanVillagerEntity.canTrade(SupernaturalForm.VAMPIRE));
    }
}
