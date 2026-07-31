package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class GobliniteAndIceTextureTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path BLOCK_TEXTURES = ASSETS.resolve("textures/block");
    private static final Path BLOCK_MODELS = ASSETS.resolve("models/block");

    @Test
    void gobliniteBlocksUseDedicatedTexturesInsteadOfCopper() {
        for (final String id : List.of(
            "delvealloy_ore",
            "deepslate_delvealloy_ore",
            "delvealloy_block",
            "raw_delvealloy_block"
        )) {
            final String texture = json(BLOCK_MODELS.resolve(id + ".json"))
                .getAsJsonObject("textures")
                .get("all")
                .getAsString();
            assertEquals("warlockery:block/" + id, texture, id);
            assertFalse(texture.contains("copper"), id);
            final BufferedImage image = image(BLOCK_TEXTURES.resolve(id + ".png"));
            assertEquals(16, image.getWidth(), id);
            assertEquals(16, image.getHeight(), id);
        }
    }

    @Test
    void gobliniteMetalContainsGreenShadesWithoutCopperOrange() {
        for (final String id : List.of(
            "delvealloy_ore",
            "deepslate_delvealloy_ore",
            "delvealloy_block",
            "raw_delvealloy_block"
        )) {
            final int[] pixels = pixels(image(BLOCK_TEXTURES.resolve(id + ".png"))).toArray();
            assertTrue(IntStream.of(pixels).anyMatch(GobliniteAndIceTextureTest::isGreen), id);
            assertTrue(IntStream.of(pixels).noneMatch(GobliniteAndIceTextureTest::isWarmMetal), id);
        }
        for (final String id : List.of("delvealloy_block", "raw_delvealloy_block")) {
            assertTrue(pixels(image(BLOCK_TEXTURES.resolve(id + ".png"))).allMatch(
                pixel -> pixel >>> 24 == 0 || isGreen(pixel)
            ), id);
        }
    }

    @Test
    void iceStockadeUsesTheSimpleVanillaPackedIceFamily() {
        for (final String id : List.of("icestockade", "icestockade_post", "icestockade_side", "icestockade_inventory")) {
            final JsonObject textures = json(BLOCK_MODELS.resolve(id + ".json")).getAsJsonObject("textures");
            assertTrue(textures.entrySet().stream().allMatch(entry ->
                entry.getValue().getAsString().equals("minecraft:block/packed_ice")
            ), id);
        }
        assertEquals(
            "warlockery:block/icestockade_inventory",
            json(ASSETS.resolve("models/item/icestockade.json")).get("parent").getAsString()
        );
    }

    private static boolean isGreen(final int pixel) {
        final int red = pixel >> 16 & 255;
        final int green = pixel >> 8 & 255;
        final int blue = pixel & 255;
        return green > red && green > blue;
    }

    private static boolean isWarmMetal(final int pixel) {
        final int red = pixel >> 16 & 255;
        final int green = pixel >> 8 & 255;
        final int blue = pixel & 255;
        return red > green * 1.15 && red > blue * 1.15;
    }

    private static IntStream pixels(final BufferedImage image) {
        return IntStream.of(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
    }

    private static BufferedImage image(final Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static JsonObject json(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
