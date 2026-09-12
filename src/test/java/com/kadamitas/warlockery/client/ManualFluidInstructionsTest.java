package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

final class ManualFluidInstructionsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyArcaneFluidBucketHasSearchableControlsSourcesAndContactOutcomes() throws Exception {
        final ManualProfile profile = ManualProfile.find("cauldronbook").orElseThrow();
        final JsonObject english;
        try (var stream = ManualFluidInstructionsTest.class.getResourceAsStream(
            "/assets/warlockery/lang/en_us.json"
        )) {
            english = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        final Map<String, List<String>> promises = Map.of(
            "bucketspirit", List.of("Cauldron Flowing Spirit", "1200 altar power", "only runs in the Spirit World", "Regeneration I", "Weakness II"),
            "buckethollowtears", List.of("Distilling Condensed Fear", "heal 2 health", "Resistance I", "Mining Fatigue I"),
            "bucketerosionbrew", List.of("Brew of Erosion", "2 magic damage", "durability wear", "Erosion burden"),
            "bucketbrew", List.of("Cauldron Colored Brew Water", "Brew of Combustion", "no direct contact effect", "machine working")
        );
        for (var entry : promises.entrySet()) {
            final String id = entry.getKey();
            assertEquals("fluid_vessels", profile.chapterFor(id).id(), id);
            assertEquals(1, profile.sections().stream().filter(id::equals).count(), id);
            assertTrue(english.has(profile.translatedSectionTitleKey(id)), id + " title");
            assertTrue(english.has(profile.translatedSectionKey(id)), id + " body");
            final String body = english.get(profile.translatedSectionKey(id)).getAsString();
            assertTrue(body.contains("Use the filled bucket"), id + " pour control");
            assertTrue(body.contains("empty Bucket"), id + " collection control");
            assertTrue(body.contains("\n\n"), id + " paragraph spacing");
            for (String phrase : entry.getValue()) assertTrue(body.contains(phrase), id + ": " + phrase);
            assertEquals("warlockery:" + id,
                ManualArticleCatalog.article(profile, id).pictograms().getFirst().itemId(), id + " picture");
        }
    }
}
