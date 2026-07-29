package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class BoundedBrewParityTest {
    private static final Set<String> SECOND_SLICE = Set.of(
        "bat_burst", "cactus_thorned", "combustion", "disease", "dissipate_gas", "drain_magic",
        "duration_boost", "endless_water", "fortune", "frogs_leg", "grues_prey", "moonshine",
        "part_lava", "part_water", "planting", "poison_toad", "raise_dead", "vines_flammable",
        "volatility"
    );
    private static final Map<String, BrewBehavior> BEHAVIORS = Map.ofEntries(
        Map.entry("bat_burst", BrewBehavior.SUMMON_BATS),
        Map.entry("cactus_thorned", BrewBehavior.PLACE_THORNS),
        Map.entry("combustion", BrewBehavior.IGNITE),
        Map.entry("disease", BrewBehavior.SPREAD_HARMFUL),
        Map.entry("dissipate_gas", BrewBehavior.DISSIPATE_GAS),
        Map.entry("drain_magic", BrewBehavior.DRAIN_RESERVES),
        Map.entry("duration_boost", BrewBehavior.EXTEND_EFFECTS),
        Map.entry("endless_water", BrewBehavior.PLACE_WATER),
        Map.entry("grues_prey", BrewBehavior.DARKNESS_PREY),
        Map.entry("moonshine", BrewBehavior.MOONLIGHT),
        Map.entry("part_lava", BrewBehavior.PART_LAVA),
        Map.entry("part_water", BrewBehavior.PART_WATER),
        Map.entry("planting", BrewBehavior.PLANT_DROPS),
        Map.entry("poison_toad", BrewBehavior.SUMMON_POISON_TOADS),
        Map.entry("raise_dead", BrewBehavior.RAISE_DEAD),
        Map.entry("vines_flammable", BrewBehavior.PLACE_VINES),
        Map.entry("volatility", BrewBehavior.APPLY_VOLATILITY)
    );

    @Test
    void everySecondSlicePageHasARegisteredOutcome() {
        assertEquals(SECOND_SLICE, BrewKind.builtIns().stream()
            .map(BrewKind::id)
            .filter(SECOND_SLICE::contains)
            .collect(Collectors.toUnmodifiableSet()));
        BEHAVIORS.forEach((id, behavior) ->
            assertTrue(BrewKind.require(id).behaviors().contains(behavior), id)
        );
        assertTrue(BrewKind.FORTUNE.effects().stream()
            .anyMatch(effect -> effect.effect().equals("minecraft:luck")));
        assertTrue(BrewKind.FROGS_LEG.effects().stream()
            .anyMatch(effect -> effect.effect().equals("minecraft:jump_boost")));
    }

    @Test
    void combustionAndEndlessWaterExposeTheirItemContracts() {
        assertEquals(2_400, BrewKind.COMBUSTION.fuelBurnTime());
        assertTrue(BrewKind.ENDLESS_WATER.returnsAfterImpact());
        SECOND_SLICE.stream().map(BrewKind::require).filter(kind -> kind != BrewKind.COMBUSTION)
            .forEach(kind -> assertEquals(0, kind.fuelBurnTime(), kind.id()));
        SECOND_SLICE.stream().map(BrewKind::require).filter(kind -> kind != BrewKind.ENDLESS_WATER)
            .forEach(kind -> assertFalse(kind.returnsAfterImpact(), kind.id()));
    }

    @Test
    void dissipateGasUsesAnExtensiblePrivateTag() {
        final Path tag = Path.of(
            "src", "main", "resources", "data", "warlockery", "tags", "block", "brew_gases.json"
        );
        try {
            assertTrue(JsonParser.parseString(Files.readString(tag))
                .getAsJsonObject()
                .getAsJsonArray("values")
                .asList()
                .stream()
                .anyMatch(value -> value.getAsString().equals("warlockery:brewgas")));
        } catch (IOException exception) {
            throw new UncheckedIOException(tag.toString(), exception);
        }
    }
}
