package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.registry.ContentCatalog;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class SculptedBlockInventoryRenderingTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path BLOCK_MODELS = ASSETS.resolve("models/block");
    private static final Path ITEM_MODELS = ASSETS.resolve("models/item");
    private static final Path ITEM_DEFINITIONS = ASSETS.resolve("items");
    private static final Path ITEM_TEXTURES = ASSETS.resolve("textures/item");
    private static final Path BLOCK_TEXTURES = ASSETS.resolve("textures/block");

    @Test
    void sculptedBlocksRenderTheirPlacedModelsInHand() {
        final List<String> heldModels = heldSculptedModelIds();

        assertTrue(heldModels.size() >= 40, heldModels.toString());
        assertTrue(heldModels.containsAll(List.of(
            "altar",
            "brazier",
            "cauldron",
            "broken_hexes_statue",
            "occluded_summons_statue",
            "statuegoddess",
            "statueofworship"
        )));

        heldModels.forEach(id -> {
            assertTrue(Files.isRegularFile(ITEM_MODELS.resolve(id + ".json")), id + " needs an item model");
            assertTrue(Files.isRegularFile(ITEM_DEFINITIONS.resolve(id + ".json")), id + " needs an item definition");
            assertFalse(Files.exists(ITEM_TEXTURES.resolve(id + ".png")), id + " still has a flat icon");
            final JsonObject itemModel = json(ITEM_MODELS.resolve(id + ".json"));
            assertEquals("warlockery:block/" + id, itemModel.get("parent").getAsString(), id);
            assertFalse(itemModel.has("textures"), id + " still uses a flat inventory icon");

            final JsonObject definition = json(ITEM_DEFINITIONS.resolve(id + ".json"))
                .getAsJsonObject("model");
            assertEquals("minecraft:model", definition.get("type").getAsString(), id);
            assertEquals("warlockery:item/" + id, definition.get("model").getAsString(), id);
        });

        final JsonObject demonHeart = json(ITEM_MODELS.resolve("demonheart.json"));
        assertEquals("minecraft:item/generated", demonHeart.get("parent").getAsString());
        assertEquals(
            "warlockery:item/demonheart",
            demonHeart.getAsJsonObject("textures").get("layer0").getAsString()
        );
        assertTrue(Files.isRegularFile(ITEM_TEXTURES.resolve("demonheart.png")));
    }

    @Test
    void sculptedMaterialTexturesUseRestrainedPalettes() {
        final Map<String, Long> textureUse = sculptedModelIds()
            .map(id -> json(BLOCK_MODELS.resolve(id + ".json")))
            .filter(model -> model.has("textures"))
            .flatMap(model -> model.getAsJsonObject("textures").entrySet().stream())
            .map(Map.Entry::getValue)
            .map(JsonElement::getAsString)
            .filter(reference -> reference.startsWith("warlockery:block/"))
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        assertTrue(textureUse.size() >= 80, textureUse.keySet().toString());
        textureUse.keySet().stream().sorted(Comparator.naturalOrder()).forEach(reference -> {
            final Path texturePath = BLOCK_TEXTURES.resolve(
                reference.substring("warlockery:block/".length()) + ".png"
            );
            assertTrue(Files.isRegularFile(texturePath), reference);
            final BufferedImage image = image(texturePath);
            final Map<Integer, Integer> colors = new HashMap<>();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    final int argb = image.getRGB(x, y);
                    if (argb >>> 24 != 0) {
                        colors.merge(argb, 1, Integer::sum);
                    }
                }
            }
            final int opaquePixels = colors.values().stream().mapToInt(Integer::intValue).sum();
            final int dominantPixels = colors.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            assertTrue(colors.size() <= 4, reference + " uses " + colors.size() + " colors");
            assertTrue(dominantPixels * 10 >= opaquePixels * 8, reference + " is visually noisy");
        });
    }

    private static Stream<String> sculptedModelIds() {
        try {
            return Files.list(BLOCK_MODELS)
                .filter(path -> path.toString().endsWith(".json"))
                .filter(path -> json(path).has("elements"))
                .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                .toList()
                .stream();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static List<String> heldSculptedModelIds() {
        return ContentCatalog.BLOCKS.stream()
            .map(ContentCatalog::modernize)
            .distinct()
            .filter(id -> Files.isRegularFile(BLOCK_MODELS.resolve(id + ".json")))
            .filter(id -> json(BLOCK_MODELS.resolve(id + ".json")).has("elements"))
            .filter(id -> !id.equals("demonheart"))
            .sorted()
            .toList();
    }

    private static JsonObject json(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static BufferedImage image(final Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
