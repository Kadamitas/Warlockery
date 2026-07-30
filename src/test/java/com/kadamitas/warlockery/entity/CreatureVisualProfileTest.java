package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureVisualProfile.Archetype;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class CreatureVisualProfileTest {
    @Test
    void everyCreatureKindHasBoundedPositiveDimensions() {
        Arrays.stream(CreatureKind.values())
            .map(CreatureVisualProfile::forKind)
            .forEach(profile -> {
                assertTrue(Float.isFinite(profile.width()));
                assertTrue(Float.isFinite(profile.height()));
                assertTrue(profile.width() >= 0.4F && profile.width() <= 1.5F);
                assertTrue(profile.height() >= 0.3F && profile.height() <= 3.0F);
            });
    }

    @Test
    void familiarFamiliesUseDistinctSmallSilhouettes() {
        final CreatureVisualProfile cat = CreatureVisualProfile.forKind(CreatureKind.CAT);
        final CreatureVisualProfile owl = CreatureVisualProfile.forKind(CreatureKind.OWL);
        final CreatureVisualProfile toad = CreatureVisualProfile.forKind(CreatureKind.TOAD);
        assertEquals(Archetype.FELINE, cat.archetype());
        assertEquals(Archetype.AVIAN, owl.archetype());
        assertEquals(Archetype.AMPHIBIAN, toad.archetype());
        assertTrue(cat.height() < 1.0F && owl.height() < 1.0F && toad.height() < 1.0F);
        assertNotEquals(cat.height(), toad.height());
    }

    @Test
    void largeFamiliesUsePurposeBuiltArchetypes() {
        assertEquals(Archetype.MOUNT, CreatureVisualProfile.forKind(CreatureKind.NIGHTMARE).archetype());
        assertEquals(Archetype.CANINE, CreatureVisualProfile.forKind(CreatureKind.HELLHOUND).archetype());
        assertEquals(Archetype.PLANT_BRUTE, CreatureVisualProfile.forKind(CreatureKind.ENT).archetype());
        assertEquals(Archetype.PLANT_BRUTE, CreatureVisualProfile.forKind(CreatureKind.BRAMBLE_COLOSSUS).archetype());
        assertEquals(Archetype.BOSS, CreatureVisualProfile.forKind(CreatureKind.DEMON).archetype());
        assertEquals(Archetype.LYCAN, CreatureVisualProfile.forKind(CreatureKind.WEREWOLF).archetype());
    }

    @Test
    void lostSoulUsesTheSpiritEntityAndRendererFamily() {
        assertEquals(Archetype.SPIRIT, CreatureVisualProfile.forKind(CreatureKind.LOST_SOUL).archetype());
    }

    @Test
    void impAndStormSimianNoLongerUseTheVexShapedSpiritFamily() {
        assertEquals(Archetype.IMP, CreatureVisualProfile.forKind(CreatureKind.IMP).archetype());
        assertEquals(Archetype.SIMIAN, CreatureVisualProfile.forKind(CreatureKind.STORM_SIMIAN).archetype());
        assertNotEquals(Archetype.SPIRIT, CreatureVisualProfile.forKind(CreatureKind.IMP).archetype());
        assertNotEquals(Archetype.SPIRIT, CreatureVisualProfile.forKind(CreatureKind.STORM_SIMIAN).archetype());
    }
}
