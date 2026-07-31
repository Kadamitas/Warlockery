package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class RequestedTextureFamiliesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path BLOCKS = ASSETS.resolve("textures/block");
    private static final Path ITEMS = ASSETS.resolve("textures/item");
    private static final List<String> REQUESTED_BREWS = List.of(
        "ingredient_brew_bats", "ingredient_brew_congealed_spirit", "ingredient_brew_depths",
        "ingredient_brew_erosion", "ingredient_brew_frogs_tongue", "ingredient_brew_grave",
        "ingredient_brew_hexed_leaping", "ingredient_brew_infection", "ingredient_brew_ink",
        "ingredient_brew_love", "ingredient_brew_raising", "ingredient_brew_revealing",
        "ingredient_brew_sleep", "ingredient_brew_solid_dirt", "ingredient_brew_solid_erosion",
        "ingredient_brew_solid_sand", "ingredient_brew_solid_sandstone", "ingredient_brew_solid_stone",
        "ingredient_brew_soul_anguish", "ingredient_brew_soul_hunger", "ingredient_brew_soul_torment",
        "ingredient_brew_sprouting", "ingredient_brew_substitution", "ingredient_brew_thorns",
        "ingredient_brew_vines", "ingredient_brew_wasting", "ingredient_brew_web"
    );

    @Test
    void requestedBrewsHaveDistinctRecognizableLiquidPalettes() {
        final List<Path> textures = REQUESTED_BREWS.stream().map(id -> ITEMS.resolve(id + ".png")).toList();
        textures.forEach(RequestedTextureFamiliesTest::assertSixteenPixelTexture);
        assertEquals(textures.size(), textures.stream().map(RequestedTextureFamiliesTest::sha256).distinct().count());
        final Set<Integer> hueFamilies = textures.stream()
            .map(RequestedTextureFamiliesTest::dominantHueFamily)
            .collect(java.util.stream.Collectors.toSet());
        assertTrue(hueFamilies.size() >= 9, () -> "brew palettes collapsed into " + hueFamilies);
    }

    @Test
    void activeWoodFamiliesUseCompactVanillaStylePalettes() {
        final List<String> textures = List.of(
            "alder_planks", "alder_log", "alder_log_top", "alder_leaves",
            "hawthorn_planks", "hawthorn_log", "hawthorn_log_top", "hawthorn_leaves",
            "rowan_planks", "rowan_log", "rowan_log_top", "rowan_leaves",
            "hexwood", "hex_log", "hex_leaves"
        );
        textures.forEach(id -> {
            final BufferedImage image = image(BLOCKS.resolve(id + ".png"));
            assertEquals(16, image.getWidth(), id);
            assertEquals(16, image.getHeight(), id);
            assertTrue(opaqueColors(image).size() <= 4, id + " should stay low detail");
        });
        assertEquals(textures.size(), textures.stream()
            .map(id -> sha256(BLOCKS.resolve(id + ".png")))
            .distinct()
            .count());
    }

    @Test
    void perpetualIceIsDarkBlueAndWebsAreWhite() {
        final BufferedImage ice = image(BLOCKS.resolve("perpetualice.png"));
        final Color average = averageOpaqueColor(ice);
        assertTrue(average.getBlue() > average.getRed() + 25);
        assertTrue((average.getRed() + average.getGreen() + average.getBlue()) / 3 < 135);

        List.of(BLOCKS.resolve("web.png"), ITEMS.resolve("ingredient_web.png"))
            .forEach(path -> opaqueColors(image(path)).forEach(rgb -> {
                final Color color = new Color(rgb, true);
                assertTrue(Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue())) >= 180,
                    path + " contains a non-white strand");
            }));
    }

    @Test
    void wormyAppleReadsAsARegAppleWithAGreenWorm() {
        final BufferedImage apple = image(ITEMS.resolve("ingredient_apple_wormy.png"));
        assertTrue(countColors(apple, color -> color.getRed() > color.getGreen() * 1.5) >= 45);
        assertTrue(countColors(apple, color -> color.getGreen() > color.getRed() * 1.2) >= 6);
    }

    @Test
    void arcaneFocusAndWolfHeadUsePurposeBuiltOccultPalettes() {
        final BufferedImage focus = image(ITEMS.resolve("arcane_focus.png"));
        assertTrue(countColors(focus, color -> color.getBlue() > color.getRed() * 1.25) >= 20);
        assertTrue(countColors(focus, color -> color.getRed() > color.getGreen() * 1.25) >= 20);
        assertTrue(pixels(focus).filter(pixel -> pixel >>> 24 == 0).count() >= 100);

        final BufferedImage fur = image(BLOCKS.resolve("wolfhead.png"));
        assertTrue(countColors(fur, color -> Math.abs(color.getRed() - color.getBlue()) < 45) >= 220);
        final BufferedImage eyes = image(BLOCKS.resolve("wolfhead_eye.png"));
        assertTrue(countColors(eyes, color -> color.getRed() > color.getBlue() * 2) >= 220);
    }

    @Test
    void boltsAreSmallDistinctArrowSilhouettes() {
        final List<String> bolts = List.of(
            "ingredient_bolt_stake", "ingredient_bolt_holy", "ingredient_bolt_silver",
            "ingredient_bolt_splitting", "ingredient_bolt_anti_magic"
        );
        bolts.forEach(id -> {
            final BufferedImage image = image(ITEMS.resolve(id + ".png"));
            final long opaque = pixels(image).filter(pixel -> pixel >>> 24 > 0).count();
            assertTrue(opaque >= 18 && opaque <= 40, id + " should remain a compact bolt");
            assertTrue(pixels(image).anyMatch(pixel -> pixel >>> 24 == 0), id + " needs transparency");
        });
        assertEquals(bolts.size(), bolts.stream()
            .map(id -> sha256(ITEMS.resolve(id + ".png")))
            .distinct()
            .count());
    }

    @Test
    void enchantedBroomUsesASculptedModelWithARealGlint() throws IOException {
        final var model = JsonParser.parseString(Files.readString(
            ASSETS.resolve("models/item/ingredient_broom_enchanted.json")
        )).getAsJsonObject();
        assertEquals("minecraft:block/block", model.get("parent").getAsString());
        assertTrue(model.getAsJsonArray("elements").size() >= 3);
        final String source = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/item/FlyingBroomItem.java"
        ));
        assertTrue(source.contains("boolean isFoil(final ItemStack stack)"));
        assertTrue(source.contains("return true;"));
    }

    @Test
    void leapingLilyHasLeavesAndABlossom() {
        final BufferedImage lily = image(ITEMS.resolve("leapinglily.png"));
        assertTrue(countColors(lily, color -> color.getGreen() > color.getRed() * 1.2) >= 30);
        assertTrue(countColors(lily, color -> color.getRed() > color.getGreen() * 1.2
            && color.getBlue() > color.getGreen()) >= 5);
    }

    private static void assertSixteenPixelTexture(final Path path) {
        final BufferedImage image = image(path);
        assertEquals(16, image.getWidth(), path.toString());
        assertEquals(16, image.getHeight(), path.toString());
        assertTrue(opaqueColors(image).size() >= 4, path + " needs readable shading");
    }

    private static int dominantHueFamily(final Path path) {
        final BufferedImage image = image(path);
        final double[] vectors = pixels(image)
            .filter(pixel -> pixel >>> 24 > 0)
            .mapToObj(Color::new)
            .map(color -> color.getRGBColorComponents(null))
            .filter(rgb -> {
                final float maximum = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
                final float minimum = Math.min(rgb[0], Math.min(rgb[1], rgb[2]));
                return maximum - minimum > 0.2F;
            })
            .map(rgb -> Color.RGBtoHSB(
                Math.round(rgb[0] * 255), Math.round(rgb[1] * 255), Math.round(rgb[2] * 255), null
            ))
            .collect(
                () -> new double[2],
                (sum, hsb) -> {
                    sum[0] += Math.sin(hsb[0] * Math.PI * 2.0);
                    sum[1] += Math.cos(hsb[0] * Math.PI * 2.0);
                },
                (left, right) -> {
                    left[0] += right[0];
                    left[1] += right[1];
                }
            );
        final double radians = Math.atan2(vectors[0], vectors[1]);
        final double hue = (radians / (Math.PI * 2.0) + 1.0) % 1.0;
        return (int) Math.floor(hue * 18.0);
    }

    private static long countColors(final BufferedImage image, final java.util.function.Predicate<Color> predicate) {
        return pixels(image)
            .filter(pixel -> pixel >>> 24 > 0)
            .mapToObj(Color::new)
            .filter(predicate)
            .count();
    }

    private static Set<Integer> opaqueColors(final BufferedImage image) {
        return pixels(image)
            .filter(pixel -> pixel >>> 24 > 0)
            .boxed()
            .collect(java.util.stream.Collectors.toSet());
    }

    private static Color averageOpaqueColor(final BufferedImage image) {
        final int[] colors = pixels(image).filter(pixel -> pixel >>> 24 > 0).toArray();
        return new Color(
            (int) Arrays.stream(colors).map(pixel -> pixel >> 16 & 255).average().orElseThrow(),
            (int) Arrays.stream(colors).map(pixel -> pixel >> 8 & 255).average().orElseThrow(),
            (int) Arrays.stream(colors).map(pixel -> pixel & 255).average().orElseThrow()
        );
    }

    private static IntStream pixels(final BufferedImage image) {
        return Arrays.stream(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
    }

    private static BufferedImage image(final Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static String sha256(final Path path) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
