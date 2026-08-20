package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile.Feature;
import com.kadamitas.warlockery.entity.ImpLifeRules.Authority;
import com.kadamitas.warlockery.item.InfernalPactEffects;
import com.kadamitas.warlockery.magic.ImpContractRules;
import com.kadamitas.warlockery.magic.ImpContractRules.Spell;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ImpCompatibilityTest {
    private static final UUID PLAYER_A = new UUID(4L, 4L);
    private static final UUID PLAYER_B = new UUID(5L, 5L);

    @Test
    void contractCostAndAllSixSpellGatesRemainExact() {
        assertEquals(25, ImpContractRules.BINDING_LEVEL_COST);
        assertEquals(6, Spell.values().length);
        final Map<Spell, Integer> favors = Map.of(
            Spell.FIERY_TOUCH, 1,
            Spell.EVAPORATION, 2,
            Spell.FIRE_TOLERANCE, 2,
            Spell.MELTING_TOUCH, 3,
            Spell.LIVING_FLAME, 4,
            Spell.TORMENT, 6
        );
        favors.forEach((spell, favor) -> assertEquals(favor, spell.favor(), spell.name()));
        assertEquals(Optional.of(Spell.FIERY_TOUCH), Spell.forItem("ingredient_contract_fiery_touch"));
        assertEquals(Optional.of(Spell.TORMENT), Spell.forItem("ingredient_contract_torment"));
    }

    @Test
    void legacyOwnerKeysStayOwnedByTheirExistingSystems() {
        assertEquals("WarlockeryInfernalOwner", InfernalPactEffects.OWNER_KEY,
            "the infernal owner key stays exactly where the legacy system wrote it");
    }

    @Test
    void impProfileFeaturesAndOfferingStayExact() {
        final CreatureBehaviorProfile imp = CreatureBehaviorProfile.audited().stream()
            .filter(profile -> profile.kind() == CreatureKind.IMP)
            .findFirst()
            .orElseThrow();
        assertTrue(imp.has(Feature.FAMILIAR_BOND));
        assertTrue(imp.has(Feature.OWNER_AURA));
        assertTrue(imp.has(Feature.PROTECT_OWNER));
        assertTrue(imp.has(Feature.FIRE_MELEE));
        assertEquals(20, imp.pulseIntervalTicks());
    }

    @Test
    void conflictingDualOwnershipRefusesWithoutTransferOrSettlement() {
        final Authority conflicted = ImpLifeRules.effectiveAuthority(
            Optional.of(PLAYER_A), Optional.of(PLAYER_B), false, false);
        assertEquals(Authority.CONFLICTED, conflicted);
        assertTrue(ImpLifeRules.infernalCommandRefused(conflicted));
        assertFalse(ImpLifeRules.infernalSacrificeAuthorized(conflicted));
        assertFalse(ImpLifeRules.commandAuthorityHolds(conflicted),
            "neither key silently transfers or wins duty command under conflict");
    }

    @Test
    void wingedSeamDefaultsToTheSharedPipelineAndOnlyTheImpClaimsIt() throws Exception {
        final Method seam = WingedArcaneMob.class.getDeclaredMethod("ownsSpecializedWingedAi");
        assertTrue(Modifier.isProtected(seam.getModifiers()),
            "the specialization seam stays a narrow protected hook");
        assertEquals(boolean.class, seam.getReturnType());
        assertTrue(Arrays.stream(StormSimianEntity.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("ownsSpecializedWingedAi")),
            "Storm Simian keeps the current shared pipeline byte for byte");
        assertTrue(Arrays.stream(ImpEntity.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("ownsSpecializedWingedAi")),
            "the Imp claims the specialized pipeline through the seam");
    }

    @Test
    void impFireballStaysAnUnregisteredNonpersistentServerSpecialization() throws Exception {
        final Class<?> fireball = Class.forName(
            "com.kadamitas.warlockery.entity.ImpFireball",
            false,
            ImpCompatibilityTest.class.getClassLoader()
        );
        assertEquals("net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball",
            fireball.getSuperclass().getName(),
            "the projectile remains visually and registrationally a vanilla SmallFireball");
        assertTrue(Arrays.stream(fireball.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("shouldBeSaved")),
            "the projectile declares itself nonpersistent so reloads cannot strip its filters");
        assertTrue(Arrays.stream(fireball.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("canHitEntity")),
            "the projectile filters effective allies before collision");
    }

    @Test
    void impEntityOwnsPersistenceDamageBoundaryAndProjectileConstruction() throws Exception {
        assertTrue(Arrays.stream(ImpEntity.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("addAdditionalSaveData")));
        assertTrue(Arrays.stream(ImpEntity.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("readAdditionalSaveData")));
        assertTrue(Arrays.stream(ImpEntity.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("hurtServer")));
        assertTrue(Arrays.stream(ImpEntity.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("performRangedAttack")));
    }
}
