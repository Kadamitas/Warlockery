package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile.Feature;
import com.kadamitas.warlockery.entity.CreatureVisualProfile.Archetype;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import net.minecraft.world.entity.monster.Vex;
import org.junit.jupiter.api.Test;

final class CustomCreatureIdentityTest {
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
    void lycanVillagersTradeOnlyWithWerewolfPlayers() {
        assertTrue(LycanVillagerEntity.canTrade(SupernaturalForm.WEREWOLF));
        assertFalse(LycanVillagerEntity.canTrade(SupernaturalForm.NONE));
        assertFalse(LycanVillagerEntity.canTrade(SupernaturalForm.VAMPIRE));
    }
}
