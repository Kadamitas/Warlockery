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

    private static JsonObject readJson(String relative) throws Exception {
        return JsonParser.parseString(Files.readString(ROOT.resolve(relative))).getAsJsonObject();
    }
}
