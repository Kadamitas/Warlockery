package com.kadamitas.warlockery.client.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class ConceptTextureSourceCatalogTest {
    private static final Path CATALOG = Path.of("tools/creature_models/concept_texture_sources.json");
    private static final Path TEXTURES = Path.of("src/main/resources/assets/warlockery/textures/entity");
    private static final Path MODELS = Path.of("src/main/java/com/kadamitas/warlockery/client/model");
    private static final Path RENDERERS = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/DedicatedCreatureRenderers.java"
    );
    private static final List<String> VIEW_NAMES = List.of("front", "left", "back", "right");

    @Test
    void catalogClosesAllFortySevenDedicatedAtlasesExactlyOnce() throws Exception {
        final JsonObject catalog = catalog();
        assertEquals(1, catalog.get("schemaVersion").getAsInt());
        final JsonArray sources = catalog.getAsJsonArray("sources");
        assertEquals(47, sources.size());

        final Set<String> actual = new HashSet<>();
        final Set<String> singleViewFallbacks = new HashSet<>();
        final Set<String> detachedFrontTitles = new HashSet<>();
        for (int index = 0; index < sources.size(); index++) {
            final JsonObject source = sources.get(index).getAsJsonObject();
            final String atlasId = string(source, "atlasId");
            assertTrue(actual.add(atlasId), "duplicate atlasId: " + atlasId);
            assertEquals(atlasId + ".png", string(source, "atlasFile"), atlasId);
            assertTrue(Files.isRegularFile(TEXTURES.resolve(string(source, "atlasFile"))), atlasId);
            assertTrue(Files.isRegularFile(MODELS.resolve(string(source, "modelClass") + ".java")), atlasId);
            if (source.has("singleViewFallback") && source.get("singleViewFallback").getAsBoolean()) {
                singleViewFallbacks.add(atlasId);
                final JsonObject views = source.getAsJsonObject("views");
                assertEquals(views.get("front"), views.get("left"), atlasId);
                assertEquals(views.get("front"), views.get("back"), atlasId);
                assertEquals(views.get("front"), views.get("right"), atlasId);
            }
            if (source.has("frontIncludesDetachedRowTitle")) {
                assertTrue(source.get("frontIncludesDetachedRowTitle").getAsBoolean(), atlasId);
                detachedFrontTitles.add(atlasId);
            }
        }

        assertEquals(expectedDedicatedAtlases(), actual);
        assertEquals(Set.of("mandrake", "dreamroot"), singleViewFallbacks);
        assertEquals(Set.of("goblin", "hobgoblin", "stonebroker", "forgewarden"), detachedFrontTitles);
        assertFalse(actual.contains("nami"));
        assertFalse(actual.contains("glass_doppelganger"));
        assertFalse(actual.contains("vampire"));
        assertTrue(actual.containsAll(Set.of("naamah", "vampire_masculine", "vampire_feminine")));
    }

    @Test
    void boardFilesHashesDimensionsRowsAndHalfOpenCropsAreValid() throws Exception {
        final Map<Path, BufferedImage> images = new HashMap<>();
        for (final JsonObject source : sources()) {
            final String atlasId = string(source, "atlasId");
            final Path board = firstPartyBoard(source, atlasId);
            assertTrue(Files.isRegularFile(board), atlasId + " missing board " + board);
            assertEquals(string(source, "boardSha256"), sha256(board), atlasId + " board hash");

            final BufferedImage image = images.computeIfAbsent(board, ConceptTextureSourceCatalogTest::readImage);
            assertNotNull(image, atlasId + " unreadable board");
            assertEquals(integer(source, "boardWidth"), image.getWidth(), atlasId + " board width");
            assertEquals(integer(source, "boardHeight"), image.getHeight(), atlasId + " board height");

            final int rowCount = integer(source, "rowCount");
            final int rowIndex = integer(source, "rowIndex");
            assertTrue(rowCount > 0, atlasId + " rowCount");
            assertTrue(rowIndex >= 0 && rowIndex < rowCount, atlasId + " row index");

            final JsonObject views = source.getAsJsonObject("views");
            assertNotNull(views, atlasId + " views");
            assertEquals(Set.copyOf(VIEW_NAMES), views.keySet(), atlasId + " view closure");
            for (final String viewName : VIEW_NAMES) {
                final Crop crop = crop(views.getAsJsonObject(viewName));
                assertTrue(crop.x0 >= 0 && crop.y0 >= 0, atlasId + " " + viewName + " origin");
                assertTrue(crop.x1 > crop.x0 && crop.y1 > crop.y0, atlasId + " " + viewName + " area");
                assertTrue(crop.x1 <= image.getWidth(), atlasId + " " + viewName + " x bounds");
                assertTrue(crop.y1 <= image.getHeight(), atlasId + " " + viewName + " y bounds");
            }
        }
    }

    @Test
    void everyCropContainsMeaningfulFirstPartyConceptForeground() throws Exception {
        final Map<Path, BufferedImage> images = new HashMap<>();
        for (final JsonObject source : sources()) {
            final String atlasId = string(source, "atlasId");
            final Path board = firstPartyBoard(source, atlasId);
            final BufferedImage image = images.computeIfAbsent(board, ConceptTextureSourceCatalogTest::readImage);
            for (final String viewName : VIEW_NAMES) {
                final Crop crop = crop(source.getAsJsonObject("views").getAsJsonObject(viewName));
                final double foreground = foregroundFraction(image, crop);
                assertTrue(
                    foreground >= 0.04,
                    atlasId + " " + viewName + " foreground fraction " + foreground
                );
            }
        }
    }

    private static Set<String> expectedDedicatedAtlases() throws IOException {
        final Matcher matcher = Pattern.compile("register\\(\"([a-z0-9_]+)\"")
            .matcher(Files.readString(RENDERERS));
        final Set<String> species = new HashSet<>();
        while (matcher.find()) {
            species.add(matcher.group(1));
        }
        assertEquals(46, species.size(), "dedicated renderer species closure");
        assertTrue(species.remove("vampire"));
        species.add("vampire_masculine");
        species.add("vampire_feminine");
        assertEquals(47, species.size());
        return species;
    }

    private static Path firstPartyBoard(final JsonObject source, final String atlasId) {
        final String value = string(source, "board").replace('\\', '/');
        final String lower = value.toLowerCase(java.util.Locale.ROOT);
        assertFalse(Path.of(value).isAbsolute(), atlasId + " board must be repository-relative");
        assertFalse(lower.contains(".."), atlasId + " board traversal");
        assertFalse(lower.contains("://"), atlasId + " remote board");
        assertFalse(lower.contains("minecraftskins"), atlasId + " skin board");
        assertFalse(lower.contains("namemc"), atlasId + " skin board");
        assertFalse(lower.contains("fandom"), atlasId + " wiki board");
        assertFalse(lower.contains("ftb"), atlasId + " wiki board");
        assertFalse(lower.contains("/wiki/"), atlasId + " wiki board");
        assertFalse(lower.contains("/skins/"), atlasId + " skin board");
        assertTrue(lower.endsWith(".png"), atlasId + " board type");
        assertTrue(
            lower.startsWith("docs/art-source/creature-concepts/production/")
                || lower.equals("docs/art-source/creature-concepts/verdant-creatures.png"),
            atlasId + " board is not an approved first-party concept: " + value
        );
        return Path.of(value);
    }

    private static double foregroundFraction(final BufferedImage image, final Crop crop) {
        final List<Integer> borderRed = new ArrayList<>();
        final List<Integer> borderGreen = new ArrayList<>();
        final List<Integer> borderBlue = new ArrayList<>();
        for (int x = crop.x0; x < crop.x1; x += 2) {
            addColor(image.getRGB(x, crop.y0), borderRed, borderGreen, borderBlue);
            addColor(image.getRGB(x, crop.y1 - 1), borderRed, borderGreen, borderBlue);
        }
        for (int y = crop.y0; y < crop.y1; y += 2) {
            addColor(image.getRGB(crop.x0, y), borderRed, borderGreen, borderBlue);
            addColor(image.getRGB(crop.x1 - 1, y), borderRed, borderGreen, borderBlue);
        }
        final int backgroundRed = median(borderRed);
        final int backgroundGreen = median(borderGreen);
        final int backgroundBlue = median(borderBlue);
        int foreground = 0;
        int sampled = 0;
        for (int y = crop.y0; y < crop.y1; y += 2) {
            for (int x = crop.x0; x < crop.x1; x += 2) {
                final Color color = new Color(image.getRGB(x, y), true);
                final int red = color.getRed() - backgroundRed;
                final int green = color.getGreen() - backgroundGreen;
                final int blue = color.getBlue() - backgroundBlue;
                if (red * red + green * green + blue * blue >= 32 * 32) {
                    foreground++;
                }
                sampled++;
            }
        }
        return foreground / (double) sampled;
    }

    private static void addColor(
        final int argb,
        final List<Integer> red,
        final List<Integer> green,
        final List<Integer> blue
    ) {
        final Color color = new Color(argb, true);
        red.add(color.getRed());
        green.add(color.getGreen());
        blue.add(color.getBlue());
    }

    private static int median(final List<Integer> values) {
        values.sort(Comparator.naturalOrder());
        return values.get(values.size() / 2);
    }

    private static List<JsonObject> sources() throws IOException {
        final JsonArray sources = catalog().getAsJsonArray("sources");
        final List<JsonObject> result = new ArrayList<>(sources.size());
        sources.forEach(value -> result.add(value.getAsJsonObject()));
        return result;
    }

    private static JsonObject catalog() throws IOException {
        return JsonParser.parseString(Files.readString(CATALOG)).getAsJsonObject();
    }

    private static BufferedImage readImage(final Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (final IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    private static String sha256(final Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static Crop crop(final JsonObject json) {
        assertNotNull(json, "missing crop");
        assertEquals(Set.of("x0", "y0", "x1", "y1"), json.keySet(), "crop schema");
        return new Crop(integer(json, "x0"), integer(json, "y0"), integer(json, "x1"), integer(json, "y1"));
    }

    private static String string(final JsonObject json, final String name) {
        assertTrue(json.has(name), "missing " + name);
        return json.get(name).getAsString();
    }

    private static int integer(final JsonObject json, final String name) {
        assertTrue(json.has(name), "missing " + name);
        return json.get(name).getAsInt();
    }

    private record Crop(int x0, int y0, int x1, int y1) {
    }
}
