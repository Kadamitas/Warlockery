package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.item.ManualProfile;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ManualEquipmentSetInstructionsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void remainingSpecialEquipmentHasReachableNamedUsageAndMatchingPictures() throws Exception {
        final JsonObject english = english();
        final ManualProfile profile = ManualProfile.find("ingredient_book_infusions").orElseThrow();
        final List<String> ids = new ArrayList<>(List.of("bitingbelt", "emberstep_slippers", "hedge_crones_hat",
            "necromancerrobe", "witchhat", "witchrobe", "silverhelm", "silverchestplate", "silverleggings", "silverboots"));
        for (String suffix : List.of("", "_silvered", "_dawn"))
            for (String piece : List.of("hat", "coat", "leggings", "boots"))
                ids.add("werewolf_hunter_" + piece + suffix);
        assertEquals(22, ids.size());
        for (String id : ids) {
            assertEquals(1, profile.sections().stream().filter(id::equals).count(), id);
            assertEquals(english.get("item.warlockery." + id).getAsString(),
                english.get(profile.translatedSectionTitleKey(id)).getAsString(), id);
            final String text = english.get(profile.translatedSectionKey(id)).getAsString();
            assertTrue(text.contains("slot"), id + " must name how to wear the item");
            assertTrue(text.contains("\n\n"), id + " must separate use and conditions");
            assertEquals("warlockery:" + id, ManualArticleCatalog.article(profile, id).pictograms().getFirst().itemId(), id);
        }
    }

    @Test
    void hunterInstructionsSeparateCompleteSetsFromOrdinarySilverArmor() throws Exception {
        final JsonObject english = english();
        for (String suffix : List.of("", "_silvered", "_dawn")) {
            for (String piece : List.of("hat", "coat", "leggings", "boots")) {
                final String id = "werewolf_hunter_" + piece + suffix;
                require(english, id, "all four matching pieces", "Mixing", "one durability",
                    "Protection dolls", "Nullifying Bolt", "bow or crossbow", "Poison and Wither");
            }
        }
        require(english, "werewolf_hunter_hat_silvered", "40%", "four seconds");
        require(english, "werewolf_hunter_hat_dawn", "one quarter", "vampire");
        for (String id : List.of("silverhelm", "silverchestplate", "silverleggings", "silverboots"))
            require(english, id, "werewolf", "one", "durability");
    }

    @Test
    void conditionalGarbAbilitiesExplainTheirCostsAndFailureLimits() throws Exception {
        final JsonObject english = english();
        require(english, "witchhat", "35%", "kettle", "brewer", "eight blocks", "again");
        require(english, "witchrobe", "35%", "kettle", "brewer", "eight blocks", "again");
        require(english, "hedge_crones_hat", "one-in-four", "eight reserve", "attempt", "landing", "infusion");
        require(english, "necromancerrobe", "sixteen blocks", "45%", "non-brew", "not tamed");
        require(english, "bitingbelt", "other", "consumed", "replaces", "helpful", "harmful", "does not prevent");
        require(english, "emberstep_slippers", "Fire Protection IV", "still take damage", "no separate ability");
    }

    private static void require(final JsonObject english, final String item, final String... phrases) {
        final String body = english.get("manual.warlockery.infusions." + item).getAsString();
        for (String phrase : phrases) assertTrue(body.contains(phrase), item + ": " + phrase);
    }

    private static JsonObject english() throws Exception {
        try (var stream = ManualEquipmentSetInstructionsTest.class.getResourceAsStream("/assets/warlockery/lang/en_us.json")) {
            assertNotNull(stream);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
