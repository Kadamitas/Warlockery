package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.brew.CauldronChalkCircles;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ArchfiendBrewingRiskTest {
    @Test
    void noShadeLeavesCauldronBrewingSafeAndOrdinaryRange() {
        final ArchfiendBrewingRisk.RiskProfile profile = ArchfiendBrewingRisk.profile(0);

        assertEquals(12, profile.effectRange());
        assertEquals(0.0F, profile.chance());
        assertFalse(ArchfiendBrewingRisk.shouldBackfire(profile, 0.0F));
    }

    @Test
    void oneShadeExtendsRangeAndIntroducesBoundedRisk() {
        final ArchfiendBrewingRisk.RiskProfile profile = ArchfiendBrewingRisk.profile(1);

        assertEquals(20, profile.effectRange());
        assertEquals(0.125F, profile.chance());
        assertTrue(ArchfiendBrewingRisk.shouldBackfire(profile, 0.124F));
        assertFalse(ArchfiendBrewingRisk.shouldBackfire(profile, 0.125F));
    }

    @Test
    void multipleShadesAreCapped() {
        assertEquals(ArchfiendBrewingRisk.profile(2), ArchfiendBrewingRisk.profile(20));
        assertEquals(28, ArchfiendBrewingRisk.profile(2).effectRange());
        assertEquals(0.25F, ArchfiendBrewingRisk.profile(2).chance());
    }

    @Test
    void invalidRandomRollIsRejected() {
        final ArchfiendBrewingRisk.RiskProfile profile = ArchfiendBrewingRisk.profile(1);

        assertThrows(IllegalArgumentException.class, () -> ArchfiendBrewingRisk.shouldBackfire(profile, -0.01F));
        assertThrows(IllegalArgumentException.class, () -> ArchfiendBrewingRisk.shouldBackfire(profile, 1.0F));
    }

    @Test
    void ritualAndInfernalCirclesAdjustRiskWithoutChangingTheArchfiendsRange() {
        final CauldronChalkCircles.State ritual = CauldronChalkCircles.evaluate(
            Set.copyOf(CauldronChalkCircles.offsets(CauldronChalkCircles.Size.MEDIUM)),
            Set.of(),
            Set.of()
        );
        final CauldronChalkCircles.State infernal = CauldronChalkCircles.evaluate(
            Set.of(),
            Set.copyOf(CauldronChalkCircles.offsets(CauldronChalkCircles.Size.MEDIUM)),
            Set.of()
        );

        assertEquals(0.05F, ArchfiendBrewingRisk.profile(2, ritual).chance(), 0.0001F);
        assertEquals(0.45F, ArchfiendBrewingRisk.profile(2, infernal).chance(), 0.0001F);
        assertEquals(28, ArchfiendBrewingRisk.profile(2, ritual).effectRange());
        assertEquals(28, ArchfiendBrewingRisk.profile(2, infernal).effectRange());
        assertEquals(0.0F, ArchfiendBrewingRisk.profile(0, ritual).chance(), 0.0001F);
        assertEquals(0.2F, ArchfiendBrewingRisk.profile(0, infernal).chance(), 0.0001F);
        assertTrue(ArchfiendBrewingRisk.shouldBackfire(ArchfiendBrewingRisk.profile(0, infernal), 0.1F));
    }
}
