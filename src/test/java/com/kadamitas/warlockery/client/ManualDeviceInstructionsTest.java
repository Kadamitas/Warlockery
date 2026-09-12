package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.item.ManualProfile;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ManualDeviceInstructionsTest {
    private static final Map<String, String> ITEMS = Map.of(
        "device_alluring_skull", "alluringskull",
        "device_bear_trap", "beartrap",
        "device_plant_mine", "plantmine",
        "device_wicker_bundle", "wickerbundle",
        "device_demon_heart", "demonheart",
        "device_crystal_ball", "crystalball",
        "device_sun_collector", "daylightcollector",
        "device_void_bramble", "voidbramble",
        "device_dream_weaver", "dreamcatcher"
    );

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nineDeviceInstructionsAreIndexedWithActualItemPictures() throws Exception {
        final JsonObject english = english();
        assertEquals(9, ITEMS.size());
        for (var entry : ITEMS.entrySet()) {
            final var profile = profile(entry.getKey());
            assertEquals(1, profile.sections().stream().filter(entry.getKey()::equals).count());
            assertTrue(english.has(profile.translatedSectionTitleKey(entry.getKey())));
            final String body = english.get(profile.translatedSectionKey(entry.getKey())).getAsString();
            assertTrue(body.contains("\n\n"), entry.getKey());
            assertFalse(body.contains("\\n"), entry.getKey());
            assertFalse(body.contains("before publishing"), entry.getKey());
            assertEquals("warlockery:" + entry.getValue(),
                ManualArticleCatalog.article(profile, entry.getKey()).pictograms().getFirst().itemId());
        }
    }

    @Test
    void deviceAcquisitionPagesUsePackagedRecipesAndKeepItemIdsDistinctFromUsageIds() {
        for (String section : List.of("device_alluring_skull", "device_bear_trap", "device_plant_mine",
            "device_wicker_bundle", "device_sun_collector", "device_void_bramble", "device_dream_weaver")) {
            final var profile = profile(section);
            final String item = ITEMS.get(section);
            final String crafting = "crafting_" + item;
            assertTrue(profile.sections().contains(crafting), crafting);
            assertEquals("warlockery:" + item,
                ManualArticleCatalog.article(profile, crafting).pictograms().getFirst().itemId(), crafting);
        }
        final var dreams = profile("device_dream_weaver");
        for (String mode : List.of("fasting", "fleet_foot", "intensity", "iron_arm", "nightmares"))
            assertTrue(dreams.sections().contains("crafting_dream_weaver_" + mode), mode);
        assertTrue(profile("device_crystal_ball").sections().contains("rite_infuse_crystal_ball"));
    }

    @Test
    void activationItemsHazardsAndOneTimeCostsAreExplicit() throws Exception {
        require("device_alluring_skull", "Necromantic Stone", "not consumed", "16 blocks", "does not summon");
        require("device_bear_trap", "empty hand", "6 damage", "Slowness VI", "reset", "spectral");
        require("device_plant_mine", "one payload", "destroys the mine", "second payload cannot replace", "Brew of Webs");
        require("device_wicker_bundle", "blood", "four bloodied bundles", "two blocks", "consumes all four");
        require("device_demon_heart", "Place", "empty your hand", "destroys the block", "Nausea", "Fire Resistance");
        require("device_crystal_ball", "Happenstance Oil", "night", "32 blocks", "bound Waystone", "without consuming");
        require("device_sun_collector", "Daylight Detector", "dawn through noon", "entire held stack", "empties",
            "four copper ingots");
        require("device_void_bramble", "owner", "500 blocks along each horizontal axis", "planter is affected",
            "32 blocks", "Ender Dew", "Refined Evil");
        require("device_dream_weaver", "Arcane Focus", "8 blocks", "5 seconds", "Nightmares", "Spirit fluid", "Absorption II");
    }

    private static ManualProfile profile(final String section) {
        return ManualProfile.profiles().stream().filter(profile -> profile.sections().contains(section)).findFirst().orElseThrow();
    }

    private static void require(final String section, final String... phrases) throws Exception {
        final String body = english().get(profile(section).translatedSectionKey(section)).getAsString().toLowerCase(java.util.Locale.ROOT);
        for (String phrase : phrases) assertTrue(body.contains(phrase.toLowerCase(java.util.Locale.ROOT)), section + ": " + phrase);
    }

    private static JsonObject english() throws Exception {
        try (var stream = ManualDeviceInstructionsTest.class.getResourceAsStream("/assets/warlockery/lang/en_us.json")) {
            assertNotNull(stream);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
