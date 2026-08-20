package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThornedPursuerResourceTest {
    private static final Path ROOT = Path.of("src/main/resources");
    private static final List<String> FIXTURES = List.of(
        "thorned_pursuer_bays_before_it_commits_to_a_course",
        "thorned_pursuer_courses_by_trail_and_never_teleports",
        "thorned_pursuer_snares_once_and_presses_on_cadence",
        "thorned_pursuer_escort_is_owned_capped_and_released",
        "thorned_pursuer_breaks_recovers_and_cancels_deterministically",
        "thorned_pursuer_save_reload_and_zombie_lifecycle_are_replaced");

    @Test
    void protectedRitualLootAndNamesRemainPresent() throws Exception {
        JsonObject ritual = readJson("data/warlockery/ritual/summon_thorned_pursuer.json");
        String ritualText = ritual.toString();
        assertTrue(ritualText.contains("4800"));
        assertTrue(ritualText.contains("night_only"));
        assertTrue(ritualText.contains("full_moon"));
        assertTrue(Files.readString(ROOT.resolve("data/warlockery/loot_table/entities/thorned_pursuer.json"))
            .contains("killed_by_player"));
        try (var locales = Files.list(ROOT.resolve("assets/warlockery/lang"))) {
            assertEquals(12, locales.filter(path -> {
                try { return Files.readString(path).contains("Thorned Pursuer")
                    || Files.readString(path).contains("thorned_pursuer"); }
                catch (Exception ignored) { return false; }
            }).count());
        }
    }

    @Test
    void runtimeUsesCappedScansLocalRiteQueriesAndCompleteTeardown() throws Exception {
        String runtime = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/entity/ThornedPursuerRuntime.java"));
        String entity = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/entity/ThornedPursuerEntity.java"));
        String gameTests = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/entity/ThornedPursuerGameTests.java"));
        assertTrue(runtime.contains("candidate -> true, candidates, ThornedPursuerRules.MAX_SCAN_VISITS"),
            "quarry discovery must use Level's exact capped always-accept overload before filtering");
        assertFalse(gameTests.contains("getAllEntities()"),
            "the physical rite fixture must report results from a bounded local query");
        assertFalse(gameTests.contains("getEntitiesOfClass("),
            "every F30 live query must use Level's bounded output/maxResults overload");
        assertTrue(gameTests.contains("RITE_ALTAR = new BlockPos(5, 8, 6)"),
            "the physical altar must be vertically separated inside the 0..14 fixture cell");
        assertTrue(gameTests.contains("assertRiteCellPosition"),
            "every physical-rite block mutation must assert its fixture-relative cell bound");
        String replaceQuarry = runtime.substring(runtime.indexOf("private static void replaceQuarry"),
            runtime.indexOf("private static void createEscorts"));
        assertFalse(replaceQuarry.contains("setTarget("),
            "fresh quarry attribution must not rewrite an existing escort's vanilla target");
        assertTrue(entity.contains("getAbsorptionAmount()")
                && entity.contains("acceptedEffectiveLoss(beforeHealth, beforeAbsorption"),
            "accepted effective loss must include absorption");
        for (String required : List.of("getNavigation().stop()", "getMoveControl().setWait()",
                "setDeltaMovement(0.0D, pursuer.getDeltaMovement().y, 0.0D)",
                "quarryDimension = null", "escortEvaluated = false", "lastSight = false",
                "retaliationLedger.clear()")) {
            assertTrue(runtime.contains(required), "common teardown is missing: " + required);
        }
    }

    private static JsonObject readJson(String relative) throws Exception {
        return JsonParser.parseString(Files.readString(ROOT.resolve(relative))).getAsJsonObject();
    }
}
