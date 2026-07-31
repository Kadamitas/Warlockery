package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class RequestedVisualAssetRepairTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path ITEM_TEXTURES = ASSETS.resolve("textures/item");
    private static final Path BLOCK_TEXTURES = ASSETS.resolve("textures/block");

    @Test
    void critterSnareAndWispyCottonUseRecognizableIndependentSprites() {
        assertGeneratedItemSprite("crittersnare");
        assertGeneratedItemSprite("somniancotton");

        final BufferedImage snare = image(ITEM_TEXTURES.resolve("crittersnare.png"));
        final BufferedImage cotton = image(ITEM_TEXTURES.resolve("somniancotton.png"));
        assertEquals(16, snare.getWidth());
        assertEquals(16, snare.getHeight());
        assertEquals(16, cotton.getWidth());
        assertEquals(16, cotton.getHeight());
        assertTrue(count(snare, color -> color.getGreen() > color.getRed() * 1.15
            && color.getGreen() > color.getBlue() * 1.05) >= 30);
        assertTrue(count(snare, color -> color.getRed() + color.getGreen() + color.getBlue() < 180) >= 20);
        assertTrue(count(cotton, color -> color.getRed() > 170
            && color.getGreen() > 190 && color.getBlue() > 185) >= 30);
        assertTrue(count(cotton, color -> color.getGreen() > color.getRed() * 1.15) >= 10);
        assertTrue(transparentPixels(snare) >= 120);
        assertTrue(transparentPixels(cotton) >= 100);
        assertNotEquals(pixelHash(snare), pixelHash(cotton));
    }

    @Test
    void filteredFumeFunnelHasItsOwnSilhouetteAndVisibleFilterMaterial() {
        final JsonObject basic = json(ASSETS.resolve("models/block/fumefunnel.json"));
        final JsonObject filtered = json(ASSETS.resolve("models/block/filteredfumefunnel.json"));
        assertEquals("warlockery:block/fumefunnel",
            basic.getAsJsonObject("textures").get("metal").getAsString());
        assertFalse(basic.getAsJsonObject("textures").has("filter"));
        assertEquals("warlockery:block/filteredfumefunnel",
            filtered.getAsJsonObject("textures").get("metal").getAsString());
        assertEquals("warlockery:block/fumefunnel_filter",
            filtered.getAsJsonObject("textures").get("filter").getAsString());
        assertNotEquals(basic.getAsJsonArray("elements"), filtered.getAsJsonArray("elements"));
        assertTrue(filtered.getAsJsonArray("elements").size() > basic.getAsJsonArray("elements").size());

        final BufferedImage basicMetal = image(BLOCK_TEXTURES.resolve("fumefunnel.png"));
        final BufferedImage filteredMetal = image(BLOCK_TEXTURES.resolve("filteredfumefunnel.png"));
        final BufferedImage filter = image(BLOCK_TEXTURES.resolve("fumefunnel_filter.png"));
        assertNotEquals(pixelHash(basicMetal), pixelHash(filteredMetal));
        assertNotEquals(pixelHash(filteredMetal), pixelHash(filter));
        assertTrue(average(filter).getRed() > average(filter).getBlue() + 50);
        assertTrue(average(filteredMetal).getBlue() > average(filteredMetal).getRed() + 30);

        assertEquals("warlockery:block/fumefunnel",
            json(ASSETS.resolve("models/item/fumefunnel.json")).get("parent").getAsString());
        assertEquals("warlockery:block/filteredfumefunnel",
            json(ASSETS.resolve("models/item/filteredfumefunnel.json")).get("parent").getAsString());
    }

    @Test
    void wolfTrapTeethUseBrightSilverInsteadOfTheBlackenedFrame() {
        final JsonObject model = json(ASSETS.resolve("models/block/wolftrap.json"));
        assertEquals("warlockery:block/wolftrap_silver",
            model.getAsJsonObject("textures").get("silver").getAsString());
        final long silverTeeth = model.getAsJsonArray("elements").asList().stream()
            .map(element -> element.getAsJsonObject())
            .filter(RequestedVisualAssetRepairTest::isRaisedTooth)
            .filter(RequestedVisualAssetRepairTest::usesOnlySilverFaces)
            .count();
        assertEquals(6, silverTeeth);

        final BufferedImage frame = image(BLOCK_TEXTURES.resolve("wolftrap.png"));
        final BufferedImage silver = image(BLOCK_TEXTURES.resolve("wolftrap_silver.png"));
        assertNotEquals(pixelHash(frame), pixelHash(silver));
        assertTrue(luminance(silver) >= luminance(frame) + 80);
        assertTrue(count(silver, color -> Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()))
            - Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue())) <= 35) >= 200);
    }

    private static void assertGeneratedItemSprite(final String id) {
        final JsonObject model = json(ASSETS.resolve("models/item/" + id + ".json"));
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("warlockery:item/" + id,
            model.getAsJsonObject("textures").get("layer0").getAsString());
        assertEquals("warlockery:item/" + id,
            json(ASSETS.resolve("items/" + id + ".json"))
                .getAsJsonObject("model").get("model").getAsString());
    }

    private static boolean isRaisedTooth(final JsonObject element) {
        final JsonArray from = element.getAsJsonArray("from");
        final JsonArray to = element.getAsJsonArray("to");
        return from.get(1).getAsInt() == 3 && to.get(1).getAsInt() == 7;
    }

    private static boolean usesOnlySilverFaces(final JsonObject element) {
        return element.getAsJsonObject("faces").entrySet().stream()
            .allMatch(face -> "#silver".equals(face.getValue().getAsJsonObject().get("texture").getAsString()));
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

    private static long count(final BufferedImage image, final java.util.function.Predicate<Color> predicate) {
        return pixels(image)
            .filter(pixel -> pixel >>> 24 > 0)
            .mapToObj(Color::new)
            .filter(predicate)
            .count();
    }

    private static long transparentPixels(final BufferedImage image) {
        return pixels(image).filter(pixel -> pixel >>> 24 == 0).count();
    }

    private static Color average(final BufferedImage image) {
        final int[] colors = pixels(image).filter(pixel -> pixel >>> 24 > 0).toArray();
        return new Color(
            (int) Arrays.stream(colors).map(pixel -> pixel >> 16 & 255).average().orElseThrow(),
            (int) Arrays.stream(colors).map(pixel -> pixel >> 8 & 255).average().orElseThrow(),
            (int) Arrays.stream(colors).map(pixel -> pixel & 255).average().orElseThrow()
        );
    }

    private static int luminance(final BufferedImage image) {
        final Color color = average(image);
        return (color.getRed() + color.getGreen() + color.getBlue()) / 3;
    }

    private static int pixelHash(final BufferedImage image) {
        return pixels(image).reduce(1, (hash, color) -> 31 * hash + color);
    }

    private static IntStream pixels(final BufferedImage image) {
        return Arrays.stream(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
    }
}
