package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile.AuditStatus;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile.Feature;
import com.kadamitas.warlockery.item.ResourceUtilityItemFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class MinedrakeMutationParityTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery", "tags");

    @TestFactory
    Stream<DynamicContainer> oneSuitePerMinedrakeInteraction() {
        return Stream.of(
            DynamicContainer.dynamicContainer("dropped bulb", List.of(
                DynamicTest.dynamicTest("failure", () -> assertFalse(
                    MinedrakeCombatRules.bulbReady(MinedrakeCombatRules.BULB_WAKE_TICKS - 1, 1, true)
                )),
                DynamicTest.dynamicTest("timing signal", () -> {
                    assertEquals(60, MinedrakeCombatRules.BULB_WAKE_TICKS);
                    assertEquals(4, MinedrakeCombatRules.BULB_PER_WAKE_BATCH);
                    assertTrue(MinedrakeCombatRules.TARGET_RANGE >= 24.0);
                }),
                DynamicTest.dynamicTest("success", () -> assertTrue(
                    MinedrakeCombatRules.bulbReady(MinedrakeCombatRules.BULB_WAKE_TICKS, 1, true)
                ))
            )),
            DynamicContainer.dynamicContainer("safe combat blast", List.of(
                DynamicTest.dynamicTest("failure", () -> assertFalse(
                    MinedrakeCombatRules.blastReady(true, 100, 119)
                )),
                DynamicTest.dynamicTest("terrain signal", () -> assertEquals(
                    Level.ExplosionInteraction.NONE,
                    MinedrakeCombatRules.EXPLOSION_INTERACTION
                )),
                DynamicTest.dynamicTest("success", () -> assertTrue(
                    MinedrakeCombatRules.blastReady(true, 100, 120)
                ))
            ))
        );
    }

    @Test
    void creatureProfilesRecordCompletedMutationParity() {
        final CreatureBehaviorProfile minedrake = CreatureBehaviorProfile.find(CreatureKind.DREAMROOT).orElseThrow();
        final CreatureBehaviorProfile toad = CreatureBehaviorProfile.find(CreatureKind.TOAD).orElseThrow();
        assertEquals(AuditStatus.COMPLETE, minedrake.auditStatus());
        assertEquals(AuditStatus.COMPLETE, toad.auditStatus());
        assertTrue(minedrake.has(Feature.MUTATION_CREATED));
        assertFalse(minedrake.has(Feature.SAFE_BLAST));
        assertTrue(minedrake.has(Feature.ROOTED_DRAIN));
        assertTrue(minedrake.has(Feature.HEART_EMPOWERMENT));
        assertTrue(toad.has(Feature.MUTATION_CREATED));
    }

    @Test
    void factoryAndPrivateTagsExposeEveryMutationRole() {
        assertTrue(ResourceUtilityItemFactory.ids().containsAll(List.of("mutator", "seedsdreamroot")));
        Stream.of(
            "block/mutation/cobwebs.json",
            "block/mutation/grasspers.json",
            "block/mutation/toad/slime_snares.json",
            "block/mutation/minedrake/mandrake_crops.json",
            "item/mutation/mutandis_extremis.json",
            "item/mutation/focused_will.json",
            "item/mutation/charged_attuned_stones.json",
            "entity_type/mutation/toad/hosts.json",
            "entity_type/mutation/minedrake/creeper_hosts.json",
            "entity_type/mutation/minedrake/living_mandrakes.json",
            "fluid/mutation/water.json"
        ).forEach(relative -> assertTrue(read(DATA.resolve(relative)).contains("\"replace\": false"), relative));
        final String recipe = read(Path.of(
            "src", "main", "resources", "data", "warlockery", "recipe", "mutator.json"
        ));
        assertTrue(recipe.contains("#warlockery:mutation/mutandis_extremis"));
        assertTrue(recipe.contains("#c:rods/wooden"));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
