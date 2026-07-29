package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

final class HighTierReagentParityTest {
    private static final Path DATA = Path.of("src/main/resources/data/warlockery");

    @Test
    void refinedEvilDrivesEveryDocumentedDemonProgressionRoute() throws IOException {
        assertTrue(ritualIngredients("summon_demon").contains("warlockery:ingredient_refined_evil"));
        assertTrue(machineInputs("kettle_brew_inferno").contains("warlockery:ingredient_refined_evil"));
        assertTrue(machineInputs("cauldron_drop_of_luck").contains("warlockery:ingredient_refined_evil"));
        assertTrue(machineInputs("kettle_brew_summon_abyssal_regent")
            .contains("warlockery:ingredient_refined_evil"));
    }

    @Test
    void quartzSphereFeedsDivinationAndSolarProgression() throws IOException {
        assertTrue(ritualIngredients("infuse_crystal_ball").contains("warlockery:ingredient_quartz_sphere"));
        assertTrue(ritualIngredients("infuse_seer_stone").contains("warlockery:ingredient_quartz_sphere"));
        final JsonArray values = json(DATA.resolve("tags/item/solar_chargeables.json")).getAsJsonArray("values");
        assertTrue(StreamSupport.stream(values.spliterator(), false)
            .map(element -> element.getAsString())
            .anyMatch("warlockery:ingredient_quartz_sphere"::equals));
    }

    @Test
    void leonardBrewCreatesTheUrnDroppingShade() throws IOException {
        assertEquals(
            "warlockery:brew_summon_abyssal_regent",
            json(DATA.resolve("warlockery_machine/kettle_brew_summon_abyssal_regent.json"))
                .getAsJsonArray("outputs").get(0).getAsJsonObject().get("item").getAsString()
        );
        assertEquals(
            "warlockery:archfiends_urn",
            json(DATA.resolve("loot_table/entities/emberhorn_archfiend.json"))
                .getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                .getAsJsonObject().get("name").getAsString()
        );
        final String runtime = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/brew/BrewRuntime.java"
        ));
        assertTrue(runtime.contains("summonLeonardShade"));
        assertTrue(runtime.contains("ModEntities.ALL.get(\"emberhorn_archfiend\")"));
    }

    private static Set<String> ritualIngredients(final String id) throws IOException {
        return ingredientValues(json(DATA.resolve("ritual/" + id + ".json"))
            .getAsJsonObject("requirements").getAsJsonArray("ingredients"));
    }

    private static Set<String> machineInputs(final String id) throws IOException {
        return ingredientValues(json(DATA.resolve("warlockery_machine/" + id + ".json"))
            .getAsJsonArray("inputs"));
    }

    private static Set<String> ingredientValues(final JsonArray ingredients) {
        return StreamSupport.stream(ingredients.spliterator(), false)
            .map(element -> element.getAsJsonObject().get("ingredient").getAsString())
            .collect(Collectors.toUnmodifiableSet());
    }

    private static JsonObject json(final Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
