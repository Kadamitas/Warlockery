package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class LegacyCharmAndDoorParityTest {
    private static final Path RECIPES = Path.of("src", "main", "resources", "data", "warlockery", "recipe");

    @Test
    void rowanKeyringHasNoGameplayCapacityCeiling() throws IOException {
        RowanKeyState state = new RowanKeyState(List.of());
        for (int index = 0; index < 128; index++) {
            state = state.bind(new RowanKeyState.Door(
                Identifier.parse("minecraft:overworld"),
                new BlockPos(index, 64, index)
            ), RowanKeyItem.UNLIMITED_CAPACITY);
        }
        assertEquals(128, state.doors().size());
        assertEquals(Integer.MAX_VALUE, RowanKeyItem.UNLIMITED_CAPACITY);
        assertTrue(UtilityItemFactory.supports("ingredient_door_keyring"));
        assertTrue(Files.readString(Path.of(
            "src", "main", "java", "com", "kadamitas", "warlockery", "item", "UtilityItemFactory.java"
        )).contains("RowanKeyItem.UNLIMITED_CAPACITY"));
    }

    @Test
    void ordinaryRowanKeyStillBindsOnlyOneDoor() {
        final RowanKeyState state = IntStream.range(0, 2).boxed().reduce(
            new RowanKeyState(List.of()),
            (keys, index) -> keys.bind(new RowanKeyState.Door(
                Identifier.parse("minecraft:overworld"),
                new BlockPos(index, 64, 0)
            ), 1),
            (_, right) -> right
        );
        assertEquals(1, state.doors().size());
    }

    @Test
    void speechCharmsCoverAnimalsAndDemonsWithLegacyDurability() {
        assertTrue(BeastSpeechRules.diagnose(false, BeastSpeechRules.Audience.ANIMAL, true).success());
        assertFalse(BeastSpeechRules.diagnose(false, BeastSpeechRules.Audience.DEMON, true).success());
        assertTrue(BeastSpeechRules.diagnose(true, BeastSpeechRules.Audience.DEMON, true).success());
        assertEquals(50, BeastSpeechRules.durability(false));
        assertEquals(10, BeastSpeechRules.durability(true));
    }

    @Test
    void nullifiedLeatherCraftsTheFullHunterArmorSet() throws IOException {
        for (String piece : List.of("hat", "coat", "leggings", "boots")) {
            final String recipe = Files.readString(RECIPES.resolve("werewolf_hunter_" + piece + ".json"));
            assertTrue(recipe.contains("warlockery:ingredient_nullifiedleather"));
            assertTrue(recipe.contains("warlockery:werewolf_hunter_" + piece));
        }
    }
}
