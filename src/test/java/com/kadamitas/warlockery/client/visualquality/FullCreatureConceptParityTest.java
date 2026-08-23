package com.kadamitas.warlockery.client.visualquality;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.client.model.CreatureModelTestSupport;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.junit.jupiter.api.Test;

/** Full-catalog comparison between dedicated creature rigs, atlases, and approved concepts. */
final class FullCreatureConceptParityTest {
    private static final Path CATALOG = Path.of(
        "tools/creature_models/concept_texture_sources.json"
    );
    private static final Path TEXTURE_ROOT = Path.of(
        "src/main/resources/assets/warlockery/textures/entity"
    );
    private static final Path AUTHORED_PALETTE_CONTROL = TEXTURE_ROOT.resolve("nami.png");
    private static final String MODEL_PACKAGE = "com.kadamitas.warlockery.client.model.";
    private static final String FILTER_PROPERTY = "warlockery.creatureParityFilter";
    private static final int EXPECTED_ATLAS_COUNT = 47;
    private static final int EXPECTED_SPECIES_COUNT = 46;
    private static final int NORMALIZED_SIZE = 128;
    private static final int NORMALIZED_PADDING = 6;
    private static final double MINIMUM_DICE = 0.25;
    private static final double MAXIMUM_WIDTH_PROFILE_ERROR = 0.50;
    private static final double MAXIMUM_ASPECT_ERROR = 1.35;
    private static final double MAXIMUM_OKLAB_BIDIRECTIONAL_DISTANCE = 0.085;
    private static final double MAXIMUM_OKLAB_TRANSPORT_DISTANCE = 0.09;
    private static final double MINIMUM_AUTHORED_PALETTE_RATIO = 0.12;
    private static final List<Oklab> TRANSPORT_DIRECTIONS = List.of(
        new Oklab(1.0, 0.0, 0.0),
        new Oklab(0.0, 1.0, 0.0),
        new Oklab(0.0, 0.0, 1.0),
        direction(1.0, 1.0, 0.0),
        direction(1.0, 0.0, 1.0),
        direction(0.0, 1.0, 1.0),
        direction(0.0, 1.0, -1.0),
        direction(1.0, 1.0, 1.0),
        direction(1.0, -1.0, 1.0)
    );

    @Test
    void catalogClosesOverEveryDedicatedSpeciesAndAtlas() throws Exception {
        final List<CatalogEntry> entries = catalog();
        assertEquals(EXPECTED_ATLAS_COUNT, entries.size(), "catalog every dedicated atlas");
        assertEquals(EXPECTED_ATLAS_COUNT, distinct(entries, CatalogEntry::atlasId).size(),
            "atlas IDs must be unique");
        assertEquals(EXPECTED_ATLAS_COUNT, distinct(entries, CatalogEntry::atlasFile).size(),
            "atlas files must be unique");
        assertEquals(EXPECTED_SPECIES_COUNT, distinct(entries, CatalogEntry::modelClass).size(),
            "VampireModel is the one species with two independently authored atlases");
        assertEquals(92, requiredViewCount(entries), "default diagnostics must cover every required view");
        assertEquals(Set.of("VampireModel"), duplicatedModelClasses(entries));
        for (final CatalogEntry entry : entries) {
            assertTrue(Files.isRegularFile(entry.board()), entry.board().toString());
            assertTrue(Files.isRegularFile(TEXTURE_ROOT.resolve(entry.atlasFile())), entry.atlasFile());
            bakeModel(entry);
        }
    }

    @Test
    void vampireCatalogRowsCompareOnlyTheirOwnMasculineOrFeminineGeometry() throws Exception {
        final Map<String, CatalogEntry> vampires = catalog().stream()
            .filter(entry -> entry.atlasId().startsWith("vampire_"))
            .collect(java.util.stream.Collectors.toMap(CatalogEntry::atlasId, entry -> entry));
        assertEquals(Set.of("vampire_masculine", "vampire_feminine"), vampires.keySet());

        final ModelPart masculine = bakeModel(vampires.get("vampire_masculine"));
        final ModelPart feminine = bakeModel(vampires.get("vampire_feminine"));
        assertCollapsed(masculine.getChild("feminine_variant"),
            "masculine comparison hidden feminine rig");
        assertCollapsed(feminine.getChild("masculine_variant"),
            "feminine comparison hidden masculine rig");
    }

    @Test
    void diagnosticFilterDefaultsToFullCatalogAndRejectsUnknownIds() throws Exception {
        final List<CatalogEntry> entries = catalog();
        assertEquals(entries, filteredCatalog(entries, null));
        assertEquals(entries, filteredCatalog(entries, "  "));
        assertEquals(
            List.of("goblin", "naamah"),
            filteredCatalog(entries, "goblin, naamah").stream()
                .map(CatalogEntry::atlasId)
                .toList()
        );
        final IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> filteredCatalog(entries, "goblin,missing_creature")
        );
        assertTrue(exception.getMessage().contains("missing_creature"));
    }

    @Test
    void everyCatalogRigMatchesItsFrontAndLeftConceptSilhouettes() throws Exception {
        final Map<Path, BufferedImage> boards = new HashMap<>();
        final List<String> failures = new ArrayList<>();
        for (final CatalogEntry entry : diagnosticCatalog()) {
            final BufferedImage board = board(entry.board(), boards);
            final ModelPart model = bakeModel(entry);
            compareSilhouette(
                entry.atlasId(),
                "front",
                conceptMask(board, entry.front(), entry.atlasId() + " front"),
                renderedMask(model, CreatureModelTestSupport.Projection.FRONT),
                failures
            );
            if (!entry.singleViewFallback()) {
                compareSilhouette(
                    entry.atlasId(),
                    "left",
                    conceptMask(board, entry.left(), entry.atlasId() + " left"),
                    renderedMask(model, CreatureModelTestSupport.Projection.SIDE),
                    failures
                );
            }
        }
        assertTrue(
            failures.isEmpty(),
            () -> "Dedicated creature rigs diverge from catalog concept silhouettes:\n"
                + String.join("\n", failures)
        );
    }

    @Test
    void everyUsedUvAtlasPaletteTracksItsConceptForeground() throws Exception {
        final Map<Path, BufferedImage> boards = new HashMap<>();
        final List<String> failures = new ArrayList<>();
        final BufferedImage authoredControl = ImageIO.read(AUTHORED_PALETTE_CONTROL.toFile());
        assertNotNull(authoredControl, AUTHORED_PALETTE_CONTROL.toString());
        final double authoredControlEffectivePalette = effectivePalette(
            opaqueColors(authoredControl)
        );
        final double minimumEffectivePalette = authoredControlEffectivePalette
            * MINIMUM_AUTHORED_PALETTE_RATIO;
        for (final CatalogEntry entry : diagnosticCatalog()) {
            final BufferedImage board = board(entry.board(), boards);
            final Map<Integer, Integer> conceptColors = new LinkedHashMap<>();
            collectConceptColors(board, entry.front(), entry.atlasId() + " front", conceptColors);
            if (!entry.singleViewFallback()) {
                collectConceptColors(board, entry.left(), entry.atlasId() + " left", conceptColors);
            }

            final ModelPart model = bakeModel(entry);
            final Path atlasPath = TEXTURE_ROOT.resolve(entry.atlasFile());
            final BufferedImage atlas = ImageIO.read(atlasPath.toFile());
            assertNotNull(atlas, atlasPath.toString());
            final Map<Integer, Integer> atlasColors = usedUvColors(atlas, model);
            final List<Swatch> conceptPalette = palette(conceptColors);
            final List<Swatch> atlasPalette = palette(atlasColors);
            final double bidirectionalDistance = bidirectionalPaletteDistance(
                conceptPalette,
                atlasPalette
            );
            final double transportDistance = slicedTransportDistance(
                conceptPalette,
                atlasPalette
            );
            final double atlasEffectivePalette = effectivePalette(opaqueColors(atlas));
            if (bidirectionalDistance > MAXIMUM_OKLAB_BIDIRECTIONAL_DISTANCE
                || transportDistance > MAXIMUM_OKLAB_TRANSPORT_DISTANCE
                || atlasEffectivePalette < minimumEffectivePalette) {
                failures.add(String.format(
                    Locale.ROOT,
                    "%s [OklabBidirectional=%.3f required<=%.3f, "
                        + "OklabSlicedTransport=%.3f required<=%.3f, "
                        + "effectivePalette=%.2f required>=%.2f (%.0f%% of Nami %.2f), "
                        + "conceptBuckets=%d, atlasBuckets=%d]",
                    entry.atlasId(),
                    bidirectionalDistance,
                    MAXIMUM_OKLAB_BIDIRECTIONAL_DISTANCE,
                    transportDistance,
                    MAXIMUM_OKLAB_TRANSPORT_DISTANCE,
                    atlasEffectivePalette,
                    minimumEffectivePalette,
                    MINIMUM_AUTHORED_PALETTE_RATIO * 100.0,
                    authoredControlEffectivePalette,
                    conceptColors.size(),
                    atlasColors.size()
                ));
            }
        }
        assertTrue(
            failures.isEmpty(),
            () -> "Used-UV creature atlas palettes diverge from catalog concept foregrounds:\n"
                + String.join("\n", failures)
        );
    }

    private static void compareSilhouette(
        final String atlasId,
        final String view,
        final Mask concept,
        final Mask rendered,
        final List<String> failures
    ) {
        final Mask normalizedConcept = normalize(concept);
        final Mask normalizedRender = normalize(rendered);
        final double dice = Math.max(
            dice(normalizedConcept, normalizedRender),
            dice(normalizedConcept, mirror(normalizedRender))
        );
        final double profileError = widthProfileError(normalizedConcept, normalizedRender);
        final double conceptAspect = aspect(concept);
        final double modelAspect = aspect(rendered);
        final double aspectError = Math.abs(conceptAspect - modelAspect) / conceptAspect;
        if (dice < MINIMUM_DICE
            || profileError > MAXIMUM_WIDTH_PROFILE_ERROR
            || aspectError > MAXIMUM_ASPECT_ERROR) {
            failures.add(String.format(
                Locale.ROOT,
                "%s %s [dice=%.3f required>=%.2f, widthProfileMAE=%.3f required<=%.2f, "
                    + "conceptAspect=%.3f, modelAspect=%.3f, aspectError=%.1f%% required<=%.0f%%]",
                atlasId,
                view,
                dice,
                MINIMUM_DICE,
                profileError,
                MAXIMUM_WIDTH_PROFILE_ERROR,
                conceptAspect,
                modelAspect,
                aspectError * 100.0,
                MAXIMUM_ASPECT_ERROR * 100.0
            ));
        }
    }

    private static BufferedImage board(
        final Path path,
        final Map<Path, BufferedImage> cache
    ) throws IOException {
        final BufferedImage cached = cache.get(path);
        if (cached != null) {
            return cached;
        }
        final BufferedImage loaded = ImageIO.read(path.toFile());
        assertNotNull(loaded, path.toString());
        cache.put(path, loaded);
        return loaded;
    }

    private static ModelPart bakeModel(final CatalogEntry entry) throws Exception {
        final Class<?> modelClass = Class.forName(MODEL_PACKAGE + entry.modelClass());
        final Method factory = modelClass.getMethod("createBodyLayer");
        assertTrue(Modifier.isStatic(factory.getModifiers()), entry.modelClass() + ".createBodyLayer");
        assertEquals(LayerDefinition.class, factory.getReturnType(),
            entry.modelClass() + ".createBodyLayer return type");
        final ModelPart root = ((LayerDefinition) factory.invoke(null)).bakeRoot();
        if (entry.atlasId().equals("vampire_masculine")) {
            collapse(root.getChild("feminine_variant"));
        } else if (entry.atlasId().equals("vampire_feminine")) {
            collapse(root.getChild("masculine_variant"));
        }
        return root;
    }

    private static void collapse(final ModelPart part) {
        part.visible = false;
        part.xScale = 0.0F;
        part.yScale = 0.0F;
        part.zScale = 0.0F;
    }

    private static void assertCollapsed(final ModelPart part, final String description) {
        assertEquals(0.0F, part.xScale, description + " xScale");
        assertEquals(0.0F, part.yScale, description + " yScale");
        assertEquals(0.0F, part.zScale, description + " zScale");
    }

    private static Mask renderedMask(
        final ModelPart root,
        final CreatureModelTestSupport.Projection projection
    ) {
        final BufferedImage image = softwareSnapshot(root, projection, 256, 8);
        final boolean[] pixels = new boolean[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                pixels[y * image.getWidth() + x] = (image.getRGB(x, y) >>> 24) != 0;
            }
        }
        return largestComponent(new Mask(image.getWidth(), image.getHeight(), pixels));
    }

    private static Mask conceptMask(
        final BufferedImage board,
        final Crop crop,
        final String description
    ) {
        assertTrue(crop.x() >= 0 && crop.y() >= 0
            && crop.maximumX() <= board.getWidth()
            && crop.maximumY() <= board.getHeight(), description + " " + crop);
        final BufferedImage image = board.getSubimage(
            crop.x(), crop.y(), crop.width(), crop.height()
        );
        final int width = image.getWidth();
        final int height = image.getHeight();
        final boolean[] background = new boolean[width * height];
        final ArrayDeque<Integer> pending = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            enqueueBackground(image, x, 0, background, pending);
            enqueueBackground(image, x, height - 1, background, pending);
        }
        for (int y = 0; y < height; y++) {
            enqueueBackground(image, 0, y, background, pending);
            enqueueBackground(image, width - 1, y, background, pending);
        }
        while (!pending.isEmpty()) {
            final int index = pending.removeFirst();
            final int x = index % width;
            final int y = index / width;
            final int source = image.getRGB(x, y);
            visitBackgroundNeighbor(image, x - 1, y, source, background, pending);
            visitBackgroundNeighbor(image, x + 1, y, source, background, pending);
            visitBackgroundNeighbor(image, x, y - 1, source, background, pending);
            visitBackgroundNeighbor(image, x, y + 1, source, background, pending);
        }
        final boolean[] foreground = new boolean[background.length];
        for (int index = 0; index < foreground.length; index++) {
            foreground[index] = !background[index];
        }
        final Mask result = largestComponent(new Mask(width, height, foreground));
        final Bounds subjectBounds = bounds(result);
        assertTrue(subjectBounds.width() >= width * 0.10
                && subjectBounds.height() >= height * 0.10
                && pixelCount(result) >= width * height * 0.0025,
            description + " crop contains no substantial foreground subject: " + crop);
        return result;
    }

    private static void enqueueBackground(
        final BufferedImage image,
        final int x,
        final int y,
        final boolean[] background,
        final ArrayDeque<Integer> pending
    ) {
        final int index = y * image.getWidth() + x;
        if (!background[index] && isBackground(image.getRGB(x, y))) {
            background[index] = true;
            pending.addLast(index);
        }
    }

    private static void visitBackgroundNeighbor(
        final BufferedImage image,
        final int x,
        final int y,
        final int source,
        final boolean[] background,
        final ArrayDeque<Integer> pending
    ) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return;
        }
        final int index = y * image.getWidth() + x;
        final int candidate = image.getRGB(x, y);
        if (!background[index]
            && isBackground(candidate)
            && colorDistance(source, candidate) <= 12.0) {
            background[index] = true;
            pending.addLast(index);
        }
    }

    private static boolean isBackground(final int argb) {
        return (argb >>> 24) < 32
            || (saturation(argb) <= 0.24 && luminance(argb) >= 0.34);
    }

    private static double saturation(final int argb) {
        final int red = (argb >>> 16) & 0xFF;
        final int green = (argb >>> 8) & 0xFF;
        final int blue = argb & 0xFF;
        final int maximum = Math.max(red, Math.max(green, blue));
        final int minimum = Math.min(red, Math.min(green, blue));
        return maximum == 0 ? 0.0 : (maximum - minimum) / (double) maximum;
    }

    private static double luminance(final int argb) {
        return (0.2126 * ((argb >>> 16) & 0xFF)
            + 0.7152 * ((argb >>> 8) & 0xFF)
            + 0.0722 * (argb & 0xFF)) / 255.0;
    }

    private static double colorDistance(final int first, final int second) {
        final int red = ((first >>> 16) & 0xFF) - ((second >>> 16) & 0xFF);
        final int green = ((first >>> 8) & 0xFF) - ((second >>> 8) & 0xFF);
        final int blue = (first & 0xFF) - (second & 0xFF);
        return Math.sqrt(red * red + green * green + blue * blue);
    }

    private static Mask largestComponent(final Mask mask) {
        final boolean[] visited = new boolean[mask.pixels().length];
        boolean[] largest = new boolean[mask.pixels().length];
        int largestSize = 0;
        for (int start = 0; start < mask.pixels().length; start++) {
            if (!mask.pixels()[start] || visited[start]) {
                continue;
            }
            final ArrayDeque<Integer> pending = new ArrayDeque<>();
            final List<Integer> component = new ArrayList<>();
            pending.addLast(start);
            visited[start] = true;
            while (!pending.isEmpty()) {
                final int index = pending.removeFirst();
                component.add(index);
                final int x = index % mask.width();
                final int y = index / mask.width();
                for (int deltaY = -1; deltaY <= 1; deltaY++) {
                    for (int deltaX = -1; deltaX <= 1; deltaX++) {
                        final int nextX = x + deltaX;
                        final int nextY = y + deltaY;
                        if (nextX < 0 || nextY < 0
                            || nextX >= mask.width() || nextY >= mask.height()) {
                            continue;
                        }
                        final int next = nextY * mask.width() + nextX;
                        if (mask.pixels()[next] && !visited[next]) {
                            visited[next] = true;
                            pending.addLast(next);
                        }
                    }
                }
            }
            if (component.size() > largestSize) {
                largestSize = component.size();
                largest = new boolean[mask.pixels().length];
                for (final int index : component) {
                    largest[index] = true;
                }
            }
        }
        assertTrue(largestSize > 0, "silhouette extraction produced no foreground");
        return new Mask(mask.width(), mask.height(), largest);
    }

    private static Mask normalize(final Mask source) {
        final Bounds bounds = bounds(source);
        final boolean[] normalized = new boolean[NORMALIZED_SIZE * NORMALIZED_SIZE];
        final double scale = (NORMALIZED_SIZE - NORMALIZED_PADDING * 2.0) / bounds.height();
        final double scaledWidth = bounds.width() * scale;
        final double left = (NORMALIZED_SIZE - scaledWidth) / 2.0;
        for (int y = NORMALIZED_PADDING; y < NORMALIZED_SIZE - NORMALIZED_PADDING; y++) {
            final int sourceY = bounds.minY() + Math.min(
                bounds.height() - 1,
                Math.max(0, (int) ((y - NORMALIZED_PADDING + 0.5) / scale))
            );
            for (int x = 0; x < NORMALIZED_SIZE; x++) {
                final int sourceX = bounds.minX() + (int) ((x - left + 0.5) / scale);
                if (sourceX >= bounds.minX() && sourceX <= bounds.maxX()
                    && source.pixels()[sourceY * source.width() + sourceX]) {
                    normalized[y * NORMALIZED_SIZE + x] = true;
                }
            }
        }
        return new Mask(NORMALIZED_SIZE, NORMALIZED_SIZE, normalized);
    }

    private static Mask mirror(final Mask source) {
        final boolean[] mirrored = new boolean[source.pixels().length];
        for (int y = 0; y < source.height(); y++) {
            for (int x = 0; x < source.width(); x++) {
                mirrored[y * source.width() + x] = source.pixels()[
                    y * source.width() + source.width() - 1 - x
                ];
            }
        }
        return new Mask(source.width(), source.height(), mirrored);
    }

    private static double dice(final Mask first, final Mask second) {
        int firstPixels = 0;
        int secondPixels = 0;
        int intersection = 0;
        for (int index = 0; index < first.pixels().length; index++) {
            if (first.pixels()[index]) {
                firstPixels++;
            }
            if (second.pixels()[index]) {
                secondPixels++;
            }
            if (first.pixels()[index] && second.pixels()[index]) {
                intersection++;
            }
        }
        return 2.0 * intersection / Math.max(firstPixels + secondPixels, 1);
    }

    private static double widthProfileError(final Mask first, final Mask second) {
        double total = 0.0;
        int rows = 0;
        for (int y = NORMALIZED_PADDING; y < NORMALIZED_SIZE - NORMALIZED_PADDING; y++) {
            final double firstWidth = rowWidth(first, y)
                / (double) (NORMALIZED_SIZE - 2 * NORMALIZED_PADDING);
            final double secondWidth = rowWidth(second, y)
                / (double) (NORMALIZED_SIZE - 2 * NORMALIZED_PADDING);
            total += Math.abs(firstWidth - secondWidth);
            rows++;
        }
        return total / rows;
    }

    private static int rowWidth(final Mask mask, final int y) {
        int minimum = mask.width();
        int maximum = -1;
        for (int x = 0; x < mask.width(); x++) {
            if (mask.pixels()[y * mask.width() + x]) {
                minimum = Math.min(minimum, x);
                maximum = Math.max(maximum, x);
            }
        }
        return maximum < minimum ? 0 : maximum - minimum + 1;
    }

    private static double aspect(final Mask mask) {
        final Bounds bounds = bounds(mask);
        return bounds.width() / (double) bounds.height();
    }

    private static int pixelCount(final Mask mask) {
        int pixels = 0;
        for (final boolean foreground : mask.pixels()) {
            if (foreground) {
                pixels++;
            }
        }
        return pixels;
    }

    private static Bounds bounds(final Mask mask) {
        int minimumX = mask.width();
        int minimumY = mask.height();
        int maximumX = -1;
        int maximumY = -1;
        for (int y = 0; y < mask.height(); y++) {
            for (int x = 0; x < mask.width(); x++) {
                if (mask.pixels()[y * mask.width() + x]) {
                    minimumX = Math.min(minimumX, x);
                    minimumY = Math.min(minimumY, y);
                    maximumX = Math.max(maximumX, x);
                    maximumY = Math.max(maximumY, y);
                }
            }
        }
        assertTrue(maximumX >= minimumX && maximumY >= minimumY, "empty silhouette");
        return new Bounds(minimumX, minimumY, maximumX, maximumY);
    }

    private static void collectConceptColors(
        final BufferedImage board,
        final Crop crop,
        final String description,
        final Map<Integer, Integer> colors
    ) {
        final BufferedImage image = board.getSubimage(
            crop.x(), crop.y(), crop.width(), crop.height()
        );
        final Mask foreground = conceptMask(board, crop, description);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (foreground.pixels()[y * image.getWidth() + x]) {
                    colors.merge(colorBucket(image.getRGB(x, y)), 1, Integer::sum);
                }
            }
        }
    }

    private static Map<Integer, Integer> usedUvColors(
        final BufferedImage atlas,
        final ModelPart model
    ) {
        final int width = atlas.getWidth();
        final int height = atlas.getHeight();
        final boolean[] used = usedUvMask(model, width, height);
        final Map<Integer, Integer> colors = new LinkedHashMap<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int index = y * width + x;
                final int argb = atlas.getRGB(x, y);
                if (used[index] && (argb >>> 24) != 0) {
                    colors.merge(colorBucket(argb), 1, Integer::sum);
                }
            }
        }
        assertTrue(!colors.isEmpty(), "model must sample opaque atlas paint");
        return colors;
    }

    private static Map<Integer, Integer> opaqueColors(final BufferedImage image) {
        final Map<Integer, Integer> colors = new LinkedHashMap<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int argb = image.getRGB(x, y);
                if ((argb >>> 24) != 0) {
                    colors.merge(colorBucket(argb), 1, Integer::sum);
                }
            }
        }
        assertTrue(!colors.isEmpty(), "image must contain opaque palette paint");
        return colors;
    }

    private static boolean[] usedUvMask(
        final ModelPart root,
        final int textureWidth,
        final int textureHeight
    ) {
        final boolean[] used = new boolean[textureWidth * textureHeight];
        for (final CreatureModelTestSupport.CubeVisit visit : CreatureModelTestSupport.cubes(root)) {
            for (final ModelPart.Polygon polygon : visit.polygons()) {
                final int minimumU = Math.max(0, (int) Math.floor(java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.u() * textureWidth).min().orElseThrow()));
                final int maximumU = Math.min(textureWidth, (int) Math.ceil(java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.u() * textureWidth).max().orElseThrow()));
                final int minimumV = Math.max(0, (int) Math.floor(java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.v() * textureHeight).min().orElseThrow()));
                final int maximumV = Math.min(textureHeight, (int) Math.ceil(java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.v() * textureHeight).max().orElseThrow()));
                for (int v = minimumV; v < maximumV; v++) {
                    for (int u = minimumU; u < maximumU; u++) {
                        used[v * textureWidth + u] = true;
                    }
                }
            }
        }
        return used;
    }

    private static int colorBucket(final int argb) {
        return (((argb >>> 19) & 0x1F) << 10)
            | (((argb >>> 11) & 0x1F) << 5)
            | ((argb >>> 3) & 0x1F);
    }

    private static List<Swatch> palette(final Map<Integer, Integer> buckets) {
        return buckets.entrySet().stream()
            .map(entry -> {
                final int bucket = entry.getKey();
                final int red = ((bucket >>> 10) & 0x1F) * 8 + 4;
                final int green = ((bucket >>> 5) & 0x1F) * 8 + 4;
                final int blue = (bucket & 0x1F) * 8 + 4;
                return new Swatch(oklab(red, green, blue), entry.getValue());
            })
            .toList();
    }

    private static double effectivePalette(final Map<Integer, Integer> colors) {
        final int pixels = colors.values().stream().mapToInt(Integer::intValue).sum();
        double entropyBits = 0.0;
        for (final int count : colors.values()) {
            final double probability = count / (double) pixels;
            entropyBits -= probability * (Math.log(probability) / Math.log(2.0));
        }
        return Math.pow(2.0, entropyBits);
    }

    private static double bidirectionalPaletteDistance(
        final List<Swatch> first,
        final List<Swatch> second
    ) {
        assertTrue(!first.isEmpty() && !second.isEmpty(), "palettes must not be empty");
        return (directedPaletteDistance(first, second)
            + directedPaletteDistance(second, first)) / 2.0;
    }

    private static double directedPaletteDistance(
        final List<Swatch> source,
        final List<Swatch> target
    ) {
        double weightedDistance = 0.0;
        double totalWeight = 0.0;
        for (final Swatch sourceSwatch : source) {
            double nearest = Double.POSITIVE_INFINITY;
            for (final Swatch targetSwatch : target) {
                nearest = Math.min(nearest, oklabDistance(sourceSwatch.color(), targetSwatch.color()));
            }
            final double paletteWeight = Math.sqrt(sourceSwatch.pixelCount());
            weightedDistance += paletteWeight * nearest;
            totalWeight += paletteWeight;
        }
        return weightedDistance / totalWeight;
    }

    private static double slicedTransportDistance(
        final List<Swatch> first,
        final List<Swatch> second
    ) {
        double distance = 0.0;
        for (final Oklab direction : TRANSPORT_DIRECTIONS) {
            distance += projectedTransportDistance(first, second, direction);
        }
        return distance / TRANSPORT_DIRECTIONS.size();
    }

    private static double projectedTransportDistance(
        final List<Swatch> first,
        final List<Swatch> second,
        final Oklab direction
    ) {
        final List<ProjectionWeight> firstProjection = projection(first, direction);
        final List<ProjectionWeight> secondProjection = projection(second, direction);
        final double firstTotal = firstProjection.stream()
            .mapToDouble(ProjectionWeight::weight)
            .sum();
        final double secondTotal = secondProjection.stream()
            .mapToDouble(ProjectionWeight::weight)
            .sum();
        int firstIndex = 0;
        int secondIndex = 0;
        double firstRemaining = firstProjection.get(0).weight() / firstTotal;
        double secondRemaining = secondProjection.get(0).weight() / secondTotal;
        double distance = 0.0;
        while (firstIndex < firstProjection.size() && secondIndex < secondProjection.size()) {
            final double transported = Math.min(firstRemaining, secondRemaining);
            distance += transported * Math.abs(
                firstProjection.get(firstIndex).value()
                    - secondProjection.get(secondIndex).value()
            );
            firstRemaining -= transported;
            secondRemaining -= transported;
            if (firstRemaining <= 1.0E-12) {
                firstIndex++;
                if (firstIndex < firstProjection.size()) {
                    firstRemaining = firstProjection.get(firstIndex).weight() / firstTotal;
                }
            }
            if (secondRemaining <= 1.0E-12) {
                secondIndex++;
                if (secondIndex < secondProjection.size()) {
                    secondRemaining = secondProjection.get(secondIndex).weight() / secondTotal;
                }
            }
        }
        return distance;
    }

    private static List<ProjectionWeight> projection(
        final List<Swatch> palette,
        final Oklab direction
    ) {
        return palette.stream()
            .map(swatch -> new ProjectionWeight(
                dot(swatch.color(), direction),
                swatch.pixelCount()
            ))
            .sorted(java.util.Comparator.comparingDouble(ProjectionWeight::value))
            .toList();
    }

    private static Oklab direction(
        final double lightness,
        final double greenRed,
        final double blueYellow
    ) {
        final double length = Math.sqrt(
            lightness * lightness + greenRed * greenRed + blueYellow * blueYellow
        );
        return new Oklab(lightness / length, greenRed / length, blueYellow / length);
    }

    private static double dot(final Oklab first, final Oklab second) {
        return first.lightness() * second.lightness()
            + first.greenRed() * second.greenRed()
            + first.blueYellow() * second.blueYellow();
    }

    private static Oklab oklab(final int red, final int green, final int blue) {
        final double r = linear(red / 255.0);
        final double g = linear(green / 255.0);
        final double b = linear(blue / 255.0);
        final double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        final double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        final double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new Oklab(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        );
    }

    private static double linear(final double value) {
        return value <= 0.04045
            ? value / 12.92
            : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static double oklabDistance(final Oklab first, final Oklab second) {
        final double lightness = first.lightness() - second.lightness();
        final double greenRed = first.greenRed() - second.greenRed();
        final double blueYellow = first.blueYellow() - second.blueYellow();
        return Math.sqrt(lightness * lightness + greenRed * greenRed + blueYellow * blueYellow);
    }

    private static List<CatalogEntry> catalog() throws IOException {
        final JsonObject root = JsonParser.parseString(Files.readString(CATALOG)).getAsJsonObject();
        assertEquals(1, root.get("schemaVersion").getAsInt(), "unsupported concept catalog schema");
        final JsonArray sources = root.getAsJsonArray("sources");
        final List<CatalogEntry> entries = new ArrayList<>();
        for (int index = 0; index < sources.size(); index++) {
            final JsonObject source = sources.get(index).getAsJsonObject();
            final JsonObject views = source.getAsJsonObject("views");
            entries.add(new CatalogEntry(
                source.get("atlasId").getAsString(),
                source.get("atlasFile").getAsString(),
                source.get("modelClass").getAsString(),
                Path.of(source.get("board").getAsString()),
                crop(views.getAsJsonObject("front")),
                crop(views.getAsJsonObject("left")),
                source.has("singleViewFallback")
                    && source.get("singleViewFallback").getAsBoolean()
            ));
        }
        return List.copyOf(entries);
    }

    private static List<CatalogEntry> diagnosticCatalog() throws IOException {
        return filteredCatalog(catalog(), System.getProperty(FILTER_PROPERTY));
    }

    private static List<CatalogEntry> filteredCatalog(
        final List<CatalogEntry> entries,
        final String filter
    ) {
        if (filter == null || filter.isBlank()) {
            return entries;
        }
        final Set<String> requested = new LinkedHashSet<>();
        for (final String value : filter.split(",")) {
            if (!value.isBlank()) {
                requested.add(value.trim());
            }
        }
        if (requested.isEmpty()) {
            throw new IllegalArgumentException(FILTER_PROPERTY + " must name at least one atlas ID");
        }
        final Set<String> known = distinct(entries, CatalogEntry::atlasId);
        final Set<String> unknown = new LinkedHashSet<>(requested);
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                FILTER_PROPERTY + " contains unknown atlas IDs: " + String.join(", ", unknown)
            );
        }
        return entries.stream()
            .filter(entry -> requested.contains(entry.atlasId()))
            .toList();
    }

    private static int requiredViewCount(final List<CatalogEntry> entries) {
        return entries.stream()
            .mapToInt(entry -> entry.singleViewFallback() ? 1 : 2)
            .sum();
    }

    private static Crop crop(final JsonObject crop) {
        return new Crop(
            crop.get("x0").getAsInt(),
            crop.get("y0").getAsInt(),
            crop.get("x1").getAsInt(),
            crop.get("y1").getAsInt()
        );
    }

    private static Set<String> duplicatedModelClasses(final List<CatalogEntry> entries) {
        final Set<String> seen = new HashSet<>();
        final Set<String> duplicates = new HashSet<>();
        for (final CatalogEntry entry : entries) {
            if (!seen.add(entry.modelClass())) {
                duplicates.add(entry.modelClass());
            }
        }
        return Set.copyOf(duplicates);
    }

    private static Set<String> distinct(
        final List<CatalogEntry> entries,
        final java.util.function.Function<CatalogEntry, String> selector
    ) {
        final Set<String> values = new HashSet<>();
        for (final CatalogEntry entry : entries) {
            values.add(selector.apply(entry));
        }
        return values;
    }

    private record CatalogEntry(
        String atlasId,
        String atlasFile,
        String modelClass,
        Path board,
        Crop front,
        Crop left,
        boolean singleViewFallback
    ) {
    }

    private record Crop(int x, int y, int maximumX, int maximumY) {
        int width() {
            return maximumX - x;
        }

        int height() {
            return maximumY - y;
        }
    }

    private record Mask(int width, int height, boolean[] pixels) {
    }

    private record Bounds(int minX, int minY, int maxX, int maxY) {
        int width() {
            return maxX - minX + 1;
        }

        int height() {
            return maxY - minY + 1;
        }
    }

    private record Swatch(Oklab color, double pixelCount) {
    }

    private record Oklab(double lightness, double greenRed, double blueYellow) {
    }

    private record ProjectionWeight(double value, double weight) {
    }
}
