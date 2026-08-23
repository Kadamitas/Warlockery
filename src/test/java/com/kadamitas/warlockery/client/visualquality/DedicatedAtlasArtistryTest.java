package com.kadamitas.warlockery.client.visualquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.client.model.CreatureModelTestSupport;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.junit.jupiter.api.Test;

/** Pixel-level acceptance floor for every creature atlas owned by a dedicated model. */
final class DedicatedAtlasArtistryTest {
    private static final Path TEXTURE_ROOT = Path.of(
        "src/main/resources/assets/warlockery/textures/entity"
    );
    private static final Map<String, String> MODELS = models();
    private static final List<AtlasSpec> DEDICATED_ATLASES = dedicatedAtlases();
    private static final Set<String> INTENTIONAL_NON_DEDICATED_TOP_LEVEL_TEXTURES = Set.of(
        "glass_doppelganger.png",
        "nami.png",
        "vampire.png"
    );

    private static final double MINIMUM_EFFECTIVE_PALETTE = 2.0;
    private static final double MAXIMUM_DOMINANT_COLOR_SHARE = 0.65;
    private static final double MAXIMUM_FLAT_INTERIOR_SHARE = 0.75;
    private static final double MINIMUM_VARIED_NEIGHBOR_EDGE_SHARE = 0.05;
    private static final double MAXIMUM_VARIED_NEIGHBOR_EDGE_SHARE = 0.80;
    private static final double MAXIMUM_LARGEST_SAME_COLOR_REGION_SHARE = 0.35;
    private static final double MAXIMUM_SINGLETON_REGION_SHARE = 0.35;

    @Test
    void dedicatedCatalogClosesOverEveryTopLevelEntityAtlas() throws Exception {
        final Set<String> expected = DEDICATED_ATLASES.stream()
            .map(AtlasSpec::fileName)
            .collect(Collectors.toCollection(TreeSet::new));
        expected.addAll(INTENTIONAL_NON_DEDICATED_TOP_LEVEL_TEXTURES);
        final Set<String> actual;
        try (Stream<Path> files = Files.list(TEXTURE_ROOT)) {
            actual = files
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".png"))
                .collect(Collectors.toCollection(TreeSet::new));
        }
        assertEquals(expected, actual, "classify every new top-level entity atlas explicitly");
    }

    @Test
    void authoredNamiSkinCalibratesTheArtistryFloor() throws Exception {
        final Path atlas = TEXTURE_ROOT.resolve("nami.png");
        final AtlasMetrics metrics = inspect(atlas, null);
        assertTrue(
            violations(metrics).isEmpty(),
            () -> atlas + " must remain a passing authored control: " + describe(metrics)
                + "; violations=" + violations(metrics)
        );
    }

    @Test
    void everyDedicatedCreatureAtlasMeetsTheAuthoredPixelArtFloor() throws Exception {
        assertEquals(46, MODELS.size(), "update the dedicated model catalog when a rig is added");
        assertEquals(47, DEDICATED_ATLASES.size(), "Vampire owns two independently authored atlases");
        final List<String> failures = new ArrayList<>();
        for (final AtlasSpec spec : DEDICATED_ATLASES) {
            final Path atlas = TEXTURE_ROOT.resolve(spec.fileName());
            assertTrue(Files.isRegularFile(atlas), atlas.toString());
            final Class<?> modelClass = Class.forName(
                "com.kadamitas.warlockery.client.model." + spec.modelClass()
            );
            final LayerDefinition layer = (LayerDefinition) modelClass
                .getMethod("createBodyLayer")
                .invoke(null);
            final AtlasMetrics metrics = inspect(atlas, layer.bakeRoot());
            final List<String> atlasViolations = violations(metrics);
            if (!atlasViolations.isEmpty()) {
                failures.add(spec.fileName() + " " + describe(metrics) + " -> "
                    + String.join(", ", atlasViolations));
            }
        }
        assertTrue(
            failures.isEmpty(),
            () -> "Dedicated creature atlases below the authored pixel-art floor (Nami is the passing "
                + "calibration control):\n" + String.join("\n", failures)
        );
    }

    private static AtlasMetrics inspect(final Path atlas, final ModelPart model) throws Exception {
        final BufferedImage image = ImageIO.read(atlas.toFile());
        assertNotNull(image, atlas.toString());
        final int width = image.getWidth();
        final int height = image.getHeight();
        final boolean[] usedUvs = model == null ? null : usedUvMask(model, width, height);
        final int[] colors = new int[width * height];
        final boolean[] opaque = new boolean[colors.length];
        final Map<Integer, Integer> histogram = new HashMap<>();
        int opaquePixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int index = y * width + x;
                final int argb = image.getRGB(x, y);
                if ((usedUvs == null || usedUvs[index]) && (argb >>> 24) != 0) {
                    final int rgb = quantizedRgb(argb);
                    opaque[index] = true;
                    colors[index] = rgb;
                    histogram.merge(rgb, 1, Integer::sum);
                    opaquePixels++;
                }
            }
        }
        assertTrue(opaquePixels > 0, atlas + " must contain opaque paint");

        long neighborEdges = 0;
        long variedNeighborEdges = 0;
        int flatInteriorPixels = 0;
        int eligibleInteriorPixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int index = y * width + x;
                if (!opaque[index]) {
                    continue;
                }
                if (x + 1 < width && opaque[index + 1]) {
                    neighborEdges++;
                    if (colors[index] != colors[index + 1]) {
                        variedNeighborEdges++;
                    }
                }
                if (y + 1 < height && opaque[index + width]) {
                    neighborEdges++;
                    if (colors[index] != colors[index + width]) {
                        variedNeighborEdges++;
                    }
                }
                if (x > 0 && x + 1 < width && y > 0 && y + 1 < height
                    && opaque[index - 1] && opaque[index + 1]
                    && opaque[index - width] && opaque[index + width]) {
                    eligibleInteriorPixels++;
                    if (colors[index] == colors[index - 1]
                        && colors[index] == colors[index + 1]
                        && colors[index] == colors[index - width]
                        && colors[index] == colors[index + width]) {
                        flatInteriorPixels++;
                    }
                }
            }
        }

        final boolean[] visited = new boolean[colors.length];
        int largestRegion = 0;
        int microRegionPixels = 0;
        for (int start = 0; start < colors.length; start++) {
            if (!opaque[start] || visited[start]) {
                continue;
            }
            final int regionColor = colors[start];
            final ArrayDeque<Integer> pending = new ArrayDeque<>();
            pending.add(start);
            visited[start] = true;
            int regionSize = 0;
            while (!pending.isEmpty()) {
                final int index = pending.removeFirst();
                regionSize++;
                final int x = index % width;
                final int y = index / width;
                if (x > 0) {
                    enqueueSameColor(index - 1, regionColor, colors, opaque, visited, pending);
                }
                if (x + 1 < width) {
                    enqueueSameColor(index + 1, regionColor, colors, opaque, visited, pending);
                }
                if (y > 0) {
                    enqueueSameColor(index - width, regionColor, colors, opaque, visited, pending);
                }
                if (y + 1 < height) {
                    enqueueSameColor(index + width, regionColor, colors, opaque, visited, pending);
                }
            }
            largestRegion = Math.max(largestRegion, regionSize);
            if (regionSize == 1) {
                microRegionPixels += regionSize;
            }
        }

        final int dominantPixels = histogram.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
        double entropyBits = 0.0;
        for (final int count : histogram.values()) {
            final double probability = count / (double) opaquePixels;
            entropyBits -= probability * (Math.log(probability) / Math.log(2.0));
        }
        return new AtlasMetrics(
            histogram.size(),
            Math.pow(2.0, entropyBits),
            dominantPixels / (double) opaquePixels,
            flatInteriorPixels / (double) Math.max(eligibleInteriorPixels, 1),
            variedNeighborEdges / (double) Math.max(neighborEdges, 1),
            largestRegion / (double) opaquePixels,
            microRegionPixels / (double) opaquePixels
        );
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
        assertTrue(java.util.stream.IntStream.range(0, used.length).anyMatch(index -> used[index]),
            "model must sample at least one UV pixel");
        return used;
    }

    private static int quantizedRgb(final int argb) {
        return ((argb >>> 9) & 0x7C00) | ((argb >>> 6) & 0x03E0) | ((argb >>> 3) & 0x001F);
    }

    private static void enqueueSameColor(
        final int index,
        final int regionColor,
        final int[] colors,
        final boolean[] opaque,
        final boolean[] visited,
        final ArrayDeque<Integer> pending
    ) {
        if (opaque[index] && !visited[index] && colors[index] == regionColor) {
            visited[index] = true;
            pending.addLast(index);
        }
    }

    private static List<String> violations(final AtlasMetrics metrics) {
        final List<String> failures = new ArrayList<>();
        if (metrics.effectivePaletteSize() < MINIMUM_EFFECTIVE_PALETTE) {
            failures.add("effective palette " + decimal(metrics.effectivePaletteSize()) + " < "
                + decimal(MINIMUM_EFFECTIVE_PALETTE)
                + " (rare speckles do not count as a materially used color)");
        }
        if (metrics.dominantColorShare() > MAXIMUM_DOMINANT_COLOR_SHARE) {
            failures.add("dominant-color share " + percent(metrics.dominantColorShare()) + " > 65.0%");
        }
        if (metrics.flatInteriorShare() > MAXIMUM_FLAT_INTERIOR_SHARE) {
            failures.add("flat-interior share " + percent(metrics.flatInteriorShare()) + " > 75.0%");
        }
        if (metrics.variedNeighborEdgeShare() < MINIMUM_VARIED_NEIGHBOR_EDGE_SHARE) {
            failures.add("varied neighbor edges " + percent(metrics.variedNeighborEdgeShare()) + " < 5.0%");
        }
        if (metrics.variedNeighborEdgeShare() > MAXIMUM_VARIED_NEIGHBOR_EDGE_SHARE) {
            failures.add("varied neighbor edges " + percent(metrics.variedNeighborEdgeShare())
                + " > 80.0% (high-frequency patterning is not shading)");
        }
        if (metrics.largestSameColorRegionShare() > MAXIMUM_LARGEST_SAME_COLOR_REGION_SHARE) {
            failures.add("largest same-color region " + percent(metrics.largestSameColorRegionShare())
                + " > 35.0%");
        }
        if (metrics.singletonRegionShare() > MAXIMUM_SINGLETON_REGION_SHARE) {
            failures.add("singleton-region share " + percent(metrics.singletonRegionShare())
                + " > 35.0% (periodic speckle cannot substitute for authored clusters)");
        }
        return List.copyOf(failures);
    }

    private static String describe(final AtlasMetrics metrics) {
        return String.format(
            Locale.ROOT,
            "[quantizedPalette=%d, effectivePalette=%s, dominant=%s, flat=%s, variedEdges=%s, "
                + "largestRegion=%s, singletonRegions=%s]",
            metrics.paletteSize(),
            decimal(metrics.effectivePaletteSize()),
            percent(metrics.dominantColorShare()),
            percent(metrics.flatInteriorShare()),
            percent(metrics.variedNeighborEdgeShare()),
            percent(metrics.largestSameColorRegionShare()),
            percent(metrics.singletonRegionShare())
        );
    }

    private static String decimal(final double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String percent(final double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0);
    }

    private static List<AtlasSpec> dedicatedAtlases() {
        final List<AtlasSpec> atlases = new ArrayList<>();
        for (final Map.Entry<String, String> model : MODELS.entrySet()) {
            if (model.getKey().equals("vampire")) {
                atlases.add(new AtlasSpec("vampire_feminine.png", model.getValue()));
                atlases.add(new AtlasSpec("vampire_masculine.png", model.getValue()));
            } else {
                atlases.add(new AtlasSpec(model.getKey() + ".png", model.getValue()));
            }
        }
        return List.copyOf(atlases);
    }

    private static Map<String, String> models() {
        final Map<String, String> models = new LinkedHashMap<>();
        models.put("abyssal_regent", "AbyssalRegentModel");
        models.put("banshee", "BansheeModel");
        models.put("blood_thrall", "BloodThrallModel");
        models.put("bramble_colossus", "BrambleColossusModel");
        models.put("circle_mage", "CircleMageModel");
        models.put("corpse", "CorpseModel");
        models.put("death", "DeathModel");
        models.put("demon", "DemonModel");
        models.put("dreamroot", "DreamrootModel");
        models.put("echo_shade", "EchoShadeModel");
        models.put("eldritch_watcher", "EldritchWatcherModel");
        models.put("emberhorn_archfiend", "EmberhornArchfiendModel");
        models.put("ent", "EntModel");
        models.put("familiar_cat", "FamiliarCatModel");
        models.put("feral_lycan", "FeralLycanModel");
        models.put("forgewarden", "ForgewardenModel");
        models.put("goblin", "GoblinModel");
        models.put("hedge_crone", "HedgeCroneModel");
        models.put("hellhound", "HellhoundModel");
        models.put("hex_bat", "HexBatModel");
        models.put("hobgoblin", "HobgoblinModel");
        models.put("illusion_creeper", "IllusionCreeperModel");
        models.put("illusion_spider", "IllusionSpiderModel");
        models.put("illusion_zombie", "IllusionZombieModel");
        models.put("imp", "ImpModel");
        models.put("ironbound_sentinel", "IronboundSentinelModel");
        models.put("lost_soul", "LostSoulModel");
        models.put("lycan_villager", "LycanVillagerModel");
        models.put("mandrake", "MandrakeModel");
        models.put("naamah", "NaamahModel");
        models.put("nightmare", "NightmareModel");
        models.put("owl", "OwlModel");
        models.put("pale_steed", "PaleSteedModel");
        models.put("parasytic_louse", "ParasyticLouseModel");
        models.put("poltergeist", "PoltergeistModel");
        models.put("spectral_familiar", "SpectralFamiliarModel");
        models.put("spectre", "SpectreModel");
        models.put("spirit", "SpiritModel");
        models.put("stonebroker", "StonebrokerModel");
        models.put("storm_simian", "StormSimianModel");
        models.put("thorned_pursuer", "ThornedPursuerModel");
        models.put("toad", "ToadModel");
        models.put("umbral_sigil", "UmbralSigilModel");
        models.put("vampire", "VampireModel");
        models.put("werewolf", "WerewolfModel");
        models.put("werewolf_hunter", "WerewolfHunterModel");
        return Collections.unmodifiableMap(models);
    }

    private record AtlasSpec(String fileName, String modelClass) {
    }

    private record AtlasMetrics(
        int paletteSize,
        double effectivePaletteSize,
        double dominantColorShare,
        double flatInteriorShare,
        double variedNeighborEdgeShare,
        double largestSameColorRegionShare,
        double singletonRegionShare
    ) {
    }
}
