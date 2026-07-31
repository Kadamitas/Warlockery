package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class ConceptDrivenIconTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path TEXTURES = ASSETS.resolve("textures/item");
    private static final Path ITEM_MODELS = ASSETS.resolve("models/item");
    private static final Path BLOCK_MODELS = ASSETS.resolve("models/block");
    private static final List<String> ICONS = List.of("boline", "ritual_knife", "demonheart");
    private static final List<String> SCULPTED_CONCEPTS = List.of(
        "glowglobe",
        "paradox_egg",
        "dreamcatcher",
        "statuegoddess",
        "statueofworship",
        "broken_hexes_statue",
        "occluded_summons_statue"
    );

    @Test
    void requestedConceptIconsAreDistinctTransparentPixelArt() throws IOException {
        final Set<Integer> fingerprints = new HashSet<>();
        for (final String id : ICONS) {
            final BufferedImage image = ImageIO.read(TEXTURES.resolve(id + ".png").toFile());
            assertEquals(16, image.getWidth(), id);
            assertEquals(16, image.getHeight(), id);
            int transparent = 0;
            int opaque = 0;
            int fingerprint = 1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    final int pixel = image.getRGB(x, y);
                    fingerprint = 31 * fingerprint + pixel;
                    if ((pixel >>> 24) == 0) {
                        transparent++;
                    } else {
                        opaque++;
                    }
                }
            }
            assertTrue(transparent > 0, id);
            assertTrue(opaque >= 12, id);
            fingerprints.add(fingerprint);
        }
        assertEquals(ICONS.size(), fingerprints.size());
        assertTrue(Files.isRegularFile(Path.of("docs/concept_art/occult_items_and_statues_v2.png")));
    }

    @Test
    void sculptedConceptsUseTheirBlockModelsWithoutFlatIcons() throws IOException {
        for (final String id : SCULPTED_CONCEPTS) {
            assertFalse(Files.exists(TEXTURES.resolve(id + ".png")), id + " still has a flat inventory icon");
            final var itemModel = JsonParser.parseString(Files.readString(ITEM_MODELS.resolve(id + ".json")))
                .getAsJsonObject();
            assertEquals("warlockery:block/" + id, itemModel.get("parent").getAsString(), id);
            final var blockModel = JsonParser.parseString(Files.readString(BLOCK_MODELS.resolve(id + ".json")))
                .getAsJsonObject();
            assertTrue(blockModel.getAsJsonArray("elements").size() >= 3, id);
        }
    }
}
