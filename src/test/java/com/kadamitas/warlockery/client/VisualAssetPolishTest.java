package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class VisualAssetPolishTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path ITEM_TEXTURES = ASSETS.resolve("textures/item");
    private static final Path BLOCK_TEXTURES = ASSETS.resolve("textures/block");
    private static final Path BLOCK_MODELS = ASSETS.resolve("models/block");
    private static final Path BLOCK_STATES = ASSETS.resolve("blockstates");
    private static final List<String> GLYPHS = List.of(
        "circle", "circleglyphritual", "circleglyphinfernal", "circleglyph_veil"
    );

    @Test
    void writesNearestNeighborReleaseSpriteContactSheet() throws IOException {
        final List<SpritePreview> focused = new ArrayList<>(List.of(
            sprite("boline"), sprite("ritual_knife"), sprite("chalk_heart"), sprite("chalk_ritual"),
            sprite("chalk_ritual_charged"), sprite("chalk_infernal"), sprite("chalk_the_veil"),
            sprite("ingredient_graveyard_dust"), sprite("ingredient_silverdust"), sprite("ingredient_spectral_dust"),
            sprite("ingredient_delvealloydust"), sprite("coffin"), sprite("coffinblock"), sprite("vcoffin"),
            sprite("alder_planks"), sprite("hawthorn_planks"), sprite("rowan_planks"), sprite("ice_slippers"),
            sprite("ruby_slippers"), sprite("glassgoblet"), sprite("glassgobletfull"), sprite("chalice"),
            sprite("ingredient_chalice"), sprite("ingredient_chalice_full"), sprite("delvealloysword"),
            sprite("delvealloyaxe"), sprite("delvealloypickaxe"), sprite("delvealloyshovel"), sprite("delvealloyhoe"),
            sprite("delvealloyhelm"), sprite("delvealloychestplate"), sprite("delvealloyleggings"),
            sprite("delvealloyboots"), sprite("ingredient_delvealloyingot"), sprite("ingredient_delvealloynugget"),
            sprite("vampirebook", "vbook"), sprite("ingredient_vbook_page"), sprite("cauldronbook", "bookcauldron"),
            sprite("bookbiomes2", "biomebook2"), sprite("ingredient_book_oven"),
            sprite("ingredient_book_distilling"), sprite("ingredient_book_circle_magic"),
            sprite("ingredient_book_infusions"), sprite("ingredient_book_herbology"),
            sprite("ingredient_book_biomes"), sprite("ingredient_book_wands"), sprite("ingredient_book_burning"),
            sprite("brew_murderous_flock", "brew_splash_bottle"), sprite("brew_drinkable"),
            sprite("brew_splash_bottle"), sprite("brewgas"), sprite("brewliquid"),
            sprite("ingredient_brew_hitchcock"), sprite("ingredient_brew_murder_of_crows"),
            sprite("ingredient_brew_soaring"), sprite("erosionbrew"), sprite("alluringskull"),
            sprite("ingredient_tormented_twine"), sprite("ingredient_waystone"),
            sprite("ingredient_waystone_bound"), sprite("ingredient_waystone_creature_bound"),
            sprite("silversword"), sprite("altar"), sprite("wolfaltar"), sprite("crystalball")
        ));
        java.util.stream.IntStream.range(0, 33)
            .mapToObj(frame -> sprite("playercompass" + frame))
            .forEach(focused::add);
        java.util.stream.IntStream.range(0, 33)
            .mapToObj(frame -> sprite("shelfcompass_" + frame))
            .forEach(focused::add);
        List.of("circleglyph1.9", "circleglyphritual", "circleglyphinfernal", "circleglyph_veil")
            .stream().map(id -> new SpritePreview(id, BLOCK_TEXTURES.resolve(id + ".png"))).forEach(focused::add);
        try (Stream<Path> textures = Files.list(ITEM_TEXTURES)) {
            textures.filter(path -> path.getFileName().toString().endsWith("_spawn_egg.png"))
                .sorted()
                .map(path -> new SpritePreview(
                    path.getFileName().toString().replaceFirst("\\.png$", ""),
                    path
                ))
                .forEach(focused::add);
        }

        final int columns = 8;
        final int cellWidth = 200;
        final int cellHeight = 148;
        final int rows = Math.ceilDiv(focused.size(), columns);
        final BufferedImage contactSheet = new BufferedImage(
            columns * cellWidth,
            rows * cellHeight,
            BufferedImage.TYPE_INT_ARGB
        );
        final Graphics2D graphics = contactSheet.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        for (int index = 0; index < focused.size(); index++) {
            final int cellX = index % columns * cellWidth;
            final int cellY = index / columns * cellHeight;
            drawCheckerboard(graphics, cellX, cellY, cellWidth, 120);
            final SpritePreview preview = focused.get(index);
            graphics.drawImage(
                image(preview.texture()),
                cellX + (cellWidth - 112) / 2,
                cellY + 4,
                112,
                112,
                null
            );
            graphics.setColor(new Color(0x20242A));
            graphics.fillRect(cellX, cellY + 120, cellWidth, cellHeight - 120);
            graphics.setColor(new Color(0xF0F0F0));
            graphics.drawString(preview.label(), cellX + 4, cellY + 138);
        }
        graphics.dispose();

        final Path output = Path.of("build/reports/visual-audit/warlockery-release-sprites.png");
        Files.createDirectories(output.getParent());
        assertTrue(ImageIO.write(contactSheet, "png", output.toFile()));
        assertTrue(Files.size(output) > 10_000L);
    }

    @Test
    void itemSpritesDoNotKeepDetachedPaletteMarksAlongTheBottom() throws IOException {
        try (Stream<Path> files = Files.list(ITEM_TEXTURES)) {
            files.filter(path -> path.toString().endsWith(".png")).forEach(path -> {
                final BufferedImage image = image(path);
                final List<List<Integer>> components = components(image);
                final List<List<Integer>> marks = components.stream()
                    .filter(component -> component.size() <= 6)
                    .filter(component -> component.stream()
                        .mapToInt(pixel -> pixel / image.getWidth())
                        .min()
                        .orElse(0) >= image.getHeight() - 2)
                    .toList();
                assertTrue(marks.isEmpty(), () -> path.getFileName() + " has detached bottom marks");
            });
        }
    }

    @Test
    void releaseFacingSpritesUseReadableDistinctSilhouettes() {
        final List<String> sprites = List.of(
            "boline", "ritual_knife", "ingredient_graveyard_dust", "ingredient_silverdust",
            "ingredient_spectral_dust", "ingredient_delvealloydust", "coffin", "vcoffin",
            "alder_planks", "hawthorn_planks", "rowan_planks", "ice_slippers", "ruby_slippers",
            "glassgoblet", "glassgobletfull", "delvealloysword", "delvealloyaxe",
            "delvealloypickaxe", "delvealloyshovel", "delvealloyhoe", "delvealloyhelm",
            "delvealloychestplate", "delvealloyleggings", "delvealloyboots"
        );
        final Set<String> digests = sprites.stream().map(id -> ITEM_TEXTURES.resolve(id + ".png")).map(path -> {
            final BufferedImage image = image(path);
            assertEquals(16, image.getWidth(), path.toString());
            assertEquals(16, image.getHeight(), path.toString());
            assertEquals(0, alpha(image, 0, 0), path + " needs a transparent background");
            assertTrue(opaquePixels(image) >= 12, path + " needs a readable silhouette");
            return sha256(path);
        }).collect(java.util.stream.Collectors.toSet());
        assertEquals(sprites.size(), digests.size());

        final BufferedImage knife = image(ITEM_TEXTURES.resolve("ritual_knife.png"));
        assertTrue(alpha(knife, 2, 14) > 0, "ritual knife handle should sit at the lower left");
        assertTrue(alpha(knife, 14, 2) > 0, "ritual knife blade should point toward the upper right");
        final BufferedImage boline = image(ITEM_TEXTURES.resolve("boline.png"));
        assertTrue(alpha(boline, 2, 14) > 0, "boline handle should sit at the lower left");
        assertTrue(alpha(boline, 14, 2) > 0, "boline crescent should open at the upper right");
    }

    @Test
    void compassFamiliesUseUniqueWarlockeryFrames() {
        final List<Path> frames = Stream.concat(
            java.util.stream.IntStream.range(0, 33).mapToObj(frame -> ITEM_TEXTURES.resolve("playercompass" + frame + ".png")),
            java.util.stream.IntStream.range(0, 33).mapToObj(frame -> ITEM_TEXTURES.resolve("shelfcompass_" + frame + ".png"))
        ).toList();
        assertEquals(frames.size(), frames.stream().map(VisualAssetPolishTest::sha256).distinct().count());
        frames.forEach(path -> {
            final BufferedImage image = image(path);
            assertEquals(0, alpha(image, 0, 0));
            assertTrue(alpha(image, 8, 8) > 0, path + " needs a visible needle hub");
        });
    }

    @Test
    void chalkTexturesReachEveryConnectedEdgeAndKeepTransparentCorners() {
        final List<String> textures = List.of(
            "circleglyph1.9", "circleglyphritual", "circleglyphinfernal", "circleglyph_veil"
        );
        textures.forEach(id -> {
            final BufferedImage image = image(BLOCK_TEXTURES.resolve(id + ".png"));
            assertEquals(0, alpha(image, 0, 0), id);
            assertEquals(0, alpha(image, 15, 15), id);
            assertTrue(alpha(image, 7, 0) > 0, id + " north connector");
            assertTrue(alpha(image, 15, 7) > 0, id + " east connector");
            assertTrue(alpha(image, 7, 15) > 0, id + " south connector");
            assertTrue(alpha(image, 0, 7) > 0, id + " west connector");
            assertTrue(alpha(image, 8, 8) > 0, id + " center rune");
        });
    }

    @Test
    void glyphBlockstatesRenderCenterAndFourConditionalArms() {
        GLYPHS.forEach(id -> {
            final JsonObject blockState = json(BLOCK_STATES.resolve(id + ".json"));
            assertFalse(blockState.has("variants"), id + " must use connected multipart rendering");
            assertEquals(5, blockState.getAsJsonArray("multipart").size(), id);
            assertEquals("warlockery:block/" + id,
                blockState.getAsJsonArray("multipart").get(0).getAsJsonObject()
                    .getAsJsonObject("apply").get("model").getAsString());
            for (int index = 1; index < 5; index++) {
                final JsonObject part = blockState.getAsJsonArray("multipart").get(index).getAsJsonObject();
                assertEquals(1, part.getAsJsonObject("when").size(), id);
                assertEquals("true", part.getAsJsonObject("when").entrySet().iterator().next().getValue().getAsString());
            }
        });
        for (String part : List.of("center", "north", "east", "south", "west")) {
            final JsonObject model = json(BLOCK_MODELS.resolve("chalk_glyph_" + part + ".json"));
            assertEquals("minecraft:cutout", model.get("render_type").getAsString());
            assertEquals(6, model.getAsJsonArray("elements").get(0).getAsJsonObject()
                .getAsJsonObject("faces").size());
        }
    }

    @Test
    void exposedFixtureBoundariesDeclareMatchingCullfaces() {
        final List<String> fixtures = List.of(
            "brazier", "brazier_lit", "kettle", "spinningwheel", "altar", "wolfaltar",
            "cauldron", "silvervat", "distilleryidle", "distilleryburning"
        );
        fixtures.forEach(id -> json(BLOCK_MODELS.resolve(id + ".json")).getAsJsonArray("elements").forEach(value -> {
            final JsonObject element = value.getAsJsonObject();
            if (element.has("rotation")) {
                return;
            }
            final var from = element.getAsJsonArray("from");
            final var to = element.getAsJsonArray("to");
            final JsonObject faces = element.getAsJsonObject("faces");
            assertBoundaryCull(from.get(1).getAsDouble() == 0.0, faces, "down", id);
            assertBoundaryCull(to.get(1).getAsDouble() == 16.0, faces, "up", id);
            assertBoundaryCull(from.get(2).getAsDouble() == 0.0, faces, "north", id);
            assertBoundaryCull(to.get(2).getAsDouble() == 16.0, faces, "south", id);
            assertBoundaryCull(from.get(0).getAsDouble() == 0.0, faces, "west", id);
            assertBoundaryCull(to.get(0).getAsDouble() == 16.0, faces, "east", id);
        }));
    }

    private static void assertBoundaryCull(
        final boolean boundary,
        final JsonObject faces,
        final String direction,
        final String id
    ) {
        if (boundary) {
            assertEquals(direction, faces.getAsJsonObject(direction).get("cullface").getAsString(), id + " " + direction);
        }
    }

    private static List<List<Integer>> components(final BufferedImage image) {
        final boolean[][] visited = new boolean[image.getHeight()][image.getWidth()];
        final List<List<Integer>> result = new ArrayList<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (visited[y][x] || alpha(image, x, y) == 0) {
                    continue;
                }
                final ArrayDeque<Integer> remaining = new ArrayDeque<>();
                final List<Integer> component = new ArrayList<>();
                remaining.add(y * image.getWidth() + x);
                visited[y][x] = true;
                while (!remaining.isEmpty()) {
                    final int pixel = remaining.removeFirst();
                    final int pixelX = pixel % image.getWidth();
                    final int pixelY = pixel / image.getWidth();
                    component.add(pixel);
                    for (int neighborY = Math.max(0, pixelY - 1); neighborY <= Math.min(image.getHeight() - 1, pixelY + 1); neighborY++) {
                        for (int neighborX = Math.max(0, pixelX - 1); neighborX <= Math.min(image.getWidth() - 1, pixelX + 1); neighborX++) {
                            if (!visited[neighborY][neighborX] && alpha(image, neighborX, neighborY) > 0) {
                                visited[neighborY][neighborX] = true;
                                remaining.addLast(neighborY * image.getWidth() + neighborX);
                            }
                        }
                    }
                }
                result.add(component);
            }
        }
        return result;
    }

    private static long opaquePixels(final BufferedImage image) {
        long count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (alpha(image, x, y) > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Path item(final String id) {
        return ITEM_TEXTURES.resolve(id + ".png");
    }

    private static SpritePreview sprite(final String id) {
        return sprite(id, id);
    }

    private static SpritePreview sprite(final String label, final String texture) {
        return new SpritePreview(label, item(texture));
    }

    private static void drawCheckerboard(
        final Graphics2D graphics,
        final int x,
        final int y,
        final int width,
        final int height
    ) {
        final Color light = new Color(0xC8CBD0);
        final Color dark = new Color(0x9FA3AA);
        final int tile = 8;
        for (int tileY = 0; tileY < height; tileY += tile) {
            for (int tileX = 0; tileX < width; tileX += tile) {
                graphics.setColor(((tileX + tileY) / tile & 1) == 0 ? light : dark);
                graphics.fillRect(x + tileX, y + tileY, Math.min(tile, width - tileX), Math.min(tile, height - tileY));
            }
        }
    }

    private static int alpha(final BufferedImage image, final int x, final int y) {
        return image.getRGB(x, y) >>> 24;
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

    private static String sha256(final Path path) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record SpritePreview(String label, Path texture) {
    }
}
