package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class CustomBlockTextureAuditTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path BLOCK_MODELS = ASSETS.resolve("models/block");
    private static final Path TEXTURES = ASSETS.resolve("textures");
    private static final Path REPORT = Path.of("build/reports/visual-audit/sculpted-block-textures.png");
    private static final List<String> MODELS = List.of(
        "scarecrow",
        "wickerbundle",
        "doll_shelf",
        "daylightcollector",
        "fumefunnel",
        "filteredfumefunnel",
        "distilleryidle",
        "distilleryburning",
        "trent",
        "silvervat",
        "paradox_egg"
    );

    @Test
    void customBlockModelsResolveConcreteTexturesWithoutMissingTexturePalettes() {
        final List<TextureEntry> entries = textureEntries();
        MODELS.forEach(id -> assertTrue(entries.stream().anyMatch(entry -> entry.modelId().equals(id)), id));
        entries.forEach(entry -> {
            assertTrue(Files.isRegularFile(entry.path()), entry.reference());
            final BufferedImage image = image(entry.path());
            assertNotNull(image, entry.reference());
            assertTrue(image.getWidth() > 0 && image.getHeight() > 0, entry.reference());
            assertFalse(usesMissingTexturePalette(image), entry.reference());
        });
    }

    @Test
    void writesSculptedBlockTextureContactSheet() throws IOException {
        final List<TextureEntry> entries = textureEntries();
        final int columns = 5;
        final int cellWidth = 210;
        final int cellHeight = 132;
        final BufferedImage sheet = new BufferedImage(
            columns * cellWidth,
            Math.ceilDiv(entries.size(), columns) * cellHeight,
            BufferedImage.TYPE_INT_ARGB
        );
        final Graphics2D graphics = sheet.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        for (int index = 0; index < entries.size(); index++) {
            final int x = index % columns * cellWidth;
            final int y = index / columns * cellHeight;
            drawCheckerboard(graphics, x, y, cellWidth, 98);
            final TextureEntry entry = entries.get(index);
            final BufferedImage texture = image(entry.path());
            final int frameSize = Math.min(texture.getWidth(), texture.getHeight());
            graphics.drawImage(texture, x + 59, y + 5, x + 151, y + 97,
                0, 0, frameSize, frameSize, null);
            graphics.setColor(new Color(0x20242A));
            graphics.fillRect(x, y + 98, cellWidth, cellHeight - 98);
            graphics.setColor(Color.WHITE);
            graphics.drawString(entry.modelId(), x + 5, y + 113);
            graphics.setColor(new Color(0xBFC8D8));
            graphics.drawString(shortReference(entry.reference()), x + 5, y + 127);
        }
        graphics.dispose();
        Files.createDirectories(REPORT.getParent());
        assertTrue(ImageIO.write(sheet, "png", REPORT.toFile()));
        assertTrue(Files.size(REPORT) > 5_000L);
    }

    private static List<TextureEntry> textureEntries() {
        final List<TextureEntry> entries = new ArrayList<>();
        MODELS.forEach(id -> {
            final Path modelPath = BLOCK_MODELS.resolve(id + ".json");
            assertTrue(Files.isRegularFile(modelPath), id);
            final JsonObject model = json(modelPath);
            assertTrue(model.has("textures"), id);
            final JsonObject textures = model.getAsJsonObject("textures");
            collectStrings(model)
                .filter(value -> value.startsWith("#"))
                .forEach(value -> assertTrue(textures.has(value.substring(1)), id + " unresolved " + value));
            final Map<String, TextureEntry> distinct = new LinkedHashMap<>();
            textures.entrySet().stream()
                .map(Map.Entry::getValue)
                .map(JsonElement::getAsString)
                .filter(reference -> !reference.startsWith("#"))
                .forEach(reference -> {
                    if (reference.startsWith("warlockery:")) {
                        distinct.putIfAbsent(reference, new TextureEntry(id, reference, texturePath(reference)));
                    } else {
                        assertTrue(reference.startsWith("minecraft:"), id + " unsupported texture " + reference);
                    }
                });
            assertFalse(distinct.isEmpty(), id + " needs an auditable Warlockery texture");
            entries.addAll(distinct.values());
        });
        return List.copyOf(entries);
    }

    private static Stream<String> collectStrings(final JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return Stream.of(element.getAsString());
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray().asList().stream().flatMap(CustomBlockTextureAuditTest::collectStrings);
        }
        if (element.isJsonObject()) {
            return element.getAsJsonObject().entrySet().stream()
                .flatMap(entry -> collectStrings(entry.getValue()));
        }
        return Stream.empty();
    }

    private static boolean usesMissingTexturePalette(final BufferedImage image) {
        long opaque = 0;
        long nearBlack = 0;
        long missingMagenta = 0;
        long other = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int argb = image.getRGB(x, y);
                if (argb >>> 24 == 0) {
                    continue;
                }
                opaque++;
                final int red = argb >>> 16 & 0xFF;
                final int green = argb >>> 8 & 0xFF;
                final int blue = argb & 0xFF;
                if (red <= 24 && green <= 24 && blue <= 24) {
                    nearBlack++;
                } else if (red >= 220 && green <= 40 && blue >= 220) {
                    missingMagenta++;
                } else {
                    other++;
                }
            }
        }
        return opaque > 0 && other == 0 && nearBlack * 5 >= opaque && missingMagenta * 5 >= opaque;
    }

    private static void drawCheckerboard(
        final Graphics2D graphics,
        final int x,
        final int y,
        final int width,
        final int height
    ) {
        graphics.setColor(new Color(0xC8CBD0));
        graphics.fillRect(x, y, width, height);
        graphics.setColor(new Color(0xA4A8AE));
        for (int tileY = 0; tileY < height; tileY += 8) {
            for (int tileX = 0; tileX < width; tileX += 8) {
                if (((tileX + tileY) / 8 & 1) == 0) {
                    graphics.fillRect(x + tileX, y + tileY, 8, 8);
                }
            }
        }
    }

    private static String shortReference(final String reference) {
        final int separator = reference.indexOf(':');
        return separator < 0 ? reference : reference.substring(separator + 1);
    }

    private static Path texturePath(final String reference) {
        return TEXTURES.resolve(reference.substring("warlockery:".length()) + ".png");
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

    private record TextureEntry(String modelId, String reference, Path path) {
    }
}
