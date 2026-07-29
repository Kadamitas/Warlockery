package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.brew.BrewTargeting.Facts;
import com.kadamitas.warlockery.brew.BrewTargeting.Target;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class BrewTargetingTest {
    @Test
    void dataTagsTakePriorityForModdedCreatures() {
        final Facts tagged = new Facts(true, SupernaturalForm.NONE, Optional.empty());
        assertTrue(BrewTargeting.matches(Target.DEMON, tagged));
        assertTrue(BrewTargeting.matches(Target.VAMPIRE, tagged));
        assertTrue(BrewTargeting.matches(Target.WEREWOLF, tagged));
    }

    @Test
    void playerTransformationStateMatchesSupernaturalTargets() {
        assertTrue(BrewTargeting.matches(Target.VAMPIRE, facts(SupernaturalForm.VAMPIRE)));
        assertTrue(BrewTargeting.matches(Target.WEREWOLF, facts(SupernaturalForm.WEREWOLF)));
        assertFalse(BrewTargeting.matches(Target.DEMON, facts(SupernaturalForm.VAMPIRE)));
    }

    @Test
    void arcaneCreatureKindsProvideFallbackCompatibility() {
        assertTrue(BrewTargeting.matches(Target.DEMON, facts(CreatureKind.IMP)));
        assertTrue(BrewTargeting.matches(Target.DEMON, facts(CreatureKind.EMBERHORN_ARCHFIEND)));
        assertTrue(BrewTargeting.matches(Target.VAMPIRE, facts(CreatureKind.CRIMSON_MATRIARCH)));
        assertTrue(BrewTargeting.matches(Target.WEREWOLF, facts(CreatureKind.LYCAN_VILLAGER)));
        assertFalse(BrewTargeting.matches(Target.WEREWOLF, facts(CreatureKind.WEREWOLF_HUNTER)));
    }

    private static Facts facts(final SupernaturalForm form) {
        return new Facts(false, form, Optional.empty());
    }

    private static Facts facts(final CreatureKind kind) {
        return new Facts(false, SupernaturalForm.NONE, Optional.of(kind));
    }
}
