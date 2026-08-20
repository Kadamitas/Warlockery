package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile.Feature;
import com.kadamitas.warlockery.entity.CreatureVisualProfile.Archetype;
import com.kadamitas.warlockery.entity.MimicryRules.Species;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Structural identity of the four dedicated mimic bodies, plus the protected surfaces both families
 * promised to leave exactly as they are.
 */
final class MimicryIdentityTest {

    private static final Map<Class<? extends AbstractMimicEntity>, CreatureKind> BODIES = Map.of(
        IllusionCreeperEntity.class, CreatureKind.ILLUSION_CREEPER,
        IllusionSpiderEntity.class, CreatureKind.ILLUSION_SPIDER,
        IllusionZombieEntity.class, CreatureKind.ILLUSION_ZOMBIE,
        GlassDoppelgangerEntity.class, CreatureKind.GLASS_DOPPELGANGER
    );

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void allFourBodiesAreDedicatedFinalMonstersRatherThanZombieBackedArcaneMobs() {
        assertEquals(Monster.class, AbstractMimicEntity.class.getSuperclass());
        assertFalse(Zombie.class.isAssignableFrom(AbstractMimicEntity.class),
            "no mimic may inherit the zombie lifecycle, conversion, reinforcement or door breaking");
        assertFalse(ArcaneMob.class.isAssignableFrom(AbstractMimicEntity.class));
        assertFalse(SpiritMob.class.isAssignableFrom(AbstractMimicEntity.class));
        assertFalse(WingedArcaneMob.class.isAssignableFrom(AbstractMimicEntity.class));
        BODIES.forEach((body, kind) -> {
            assertTrue(Modifier.isFinal(body.getModifiers()), body.getSimpleName());
            assertEquals(AbstractMimicEntity.class, body.getSuperclass(), body.getSimpleName());
            assertTrue(ArcaneCreature.class.isAssignableFrom(body), body.getSimpleName());
            assertTrue(MimicryRuntime.MimicBody.class.isAssignableFrom(body), body.getSimpleName());
        });
    }

    @Test
    void eachBodyReportsItsOwnKindAndItsOwnSpeciesConsistently() {
        BODIES.forEach((body, kind) -> {
            final Species species = MimicryRules.speciesOf(kind).orElseThrow();
            assertEquals(kind, species.kind(), body.getSimpleName());
        });
    }

    @Test
    void theOneTickSeamAndTheOneStateKeyAreDeclaredExactlyOnceForAllFour() {
        assertEquals("WarlockeryMimicry", AbstractMimicEntity.STATE_KEY);
        final List<String> declaredOnSubclasses = BODIES.keySet().stream()
            .flatMap(body -> java.util.Arrays.stream(body.getDeclaredMethods()))
            .map(java.lang.reflect.Method::getName)
            .distinct()
            .sorted()
            .toList();
        assertEquals(List.of("ambientSound", "creatureKind"), declaredOnSubclasses,
            "a subclass may declare only its kind and its sound set; everything else, including the"
                + " species, is derived once and would otherwise be duplicated four times");
    }

    @Test
    void registryOwnedDimensionsAndArchetypesAreUntouched() {
        assertEquals(0.6F, CreatureVisualProfile.forKind(CreatureKind.ILLUSION_CREEPER).width());
        assertEquals(1.7F, CreatureVisualProfile.forKind(CreatureKind.ILLUSION_CREEPER).height());
        assertEquals(Archetype.CREEPER,
            CreatureVisualProfile.forKind(CreatureKind.ILLUSION_CREEPER).archetype());
        assertEquals(1.4F, CreatureVisualProfile.forKind(CreatureKind.ILLUSION_SPIDER).width());
        assertEquals(0.9F, CreatureVisualProfile.forKind(CreatureKind.ILLUSION_SPIDER).height());
        assertEquals(Archetype.ARTHROPOD,
            CreatureVisualProfile.forKind(CreatureKind.ILLUSION_SPIDER).archetype());
        for (final CreatureKind kind : List.of(CreatureKind.ILLUSION_ZOMBIE, CreatureKind.GLASS_DOPPELGANGER)) {
            assertEquals(0.6F, CreatureVisualProfile.forKind(kind).width(), kind.name());
            assertEquals(1.95F, CreatureVisualProfile.forKind(kind).height(), kind.name());
            assertEquals(Archetype.HUMANOID, CreatureVisualProfile.forKind(kind).archetype(), kind.name());
        }
    }

    @Test
    void noIllusionKindGainsAGenericBehaviourProfileRowAndTheReflectionRowStaysCatalogued() {
        for (final CreatureKind kind : List.of(
            CreatureKind.ILLUSION_CREEPER, CreatureKind.ILLUSION_SPIDER, CreatureKind.ILLUSION_ZOMBIE
        )) {
            assertTrue(CreatureBehaviorProfile.find(kind).isEmpty(),
                kind + " must not gain a generic dispatch expectation the dedicated body bypasses");
        }
        final CreatureBehaviorProfile reflection =
            CreatureBehaviorProfile.find(CreatureKind.GLASS_DOPPELGANGER).orElseThrow();
        assertTrue(reflection.has(Feature.MIRROR_COPY),
            "the catalogued reflection row stays exactly as it is and simply never executes");
        assertTrue(reflection.has(Feature.PHASED));
    }

    @Test
    void theCataloguedTacticalDoctrineRowsAreLeftExactlyAsTheyAre() {
        assertEquals(TacticalCombatRules.Doctrine.PACK,
            TacticalCombatRules.profile(CreatureKind.ILLUSION_SPIDER).doctrine());
        assertEquals(TacticalCombatRules.Doctrine.BRUTE,
            TacticalCombatRules.profile(CreatureKind.ILLUSION_CREEPER).doctrine());
        assertEquals(TacticalCombatRules.Doctrine.BRUTE,
            TacticalCombatRules.profile(CreatureKind.ILLUSION_ZOMBIE).doctrine());
        assertEquals(TacticalCombatRules.Doctrine.STALKER,
            TacticalCombatRules.profile(CreatureKind.GLASS_DOPPELGANGER).doctrine());
    }

    @Test
    void noMimicKindIsClassifiedUndead() {
        BODIES.forEach((body, kind) -> assertFalse(kind.isUndead(), kind.name()));
    }
}


