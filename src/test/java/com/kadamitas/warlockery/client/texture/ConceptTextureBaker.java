package com.kadamitas.warlockery.client.texture;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/**
 * Test-runtime asset tool which transfers approved turnaround-board paint onto a model's real UV faces.
 * The tool lives in the test source set deliberately: it authors source assets but is never packaged in the mod.
 */
final class ConceptTextureBaker {
    private static final double BACKGROUND_MAXIMUM_SATURATION = 0.30;
    private static final double BACKGROUND_STEP_DISTANCE = 12.0;

    private ConceptTextureBaker() {
    }

    static BufferedImage bake(
        final ModelPart root,
        final int textureWidth,
        final int textureHeight,
        final ConceptViews views
    ) {
        return bake(root, textureWidth, textureHeight, views, path -> true);
    }

    static BufferedImage bake(
        final ModelPart root,
        final int textureWidth,
        final int textureHeight,
        final ConceptViews views,
        final Predicate<String> includePath
    ) {
        if (textureWidth <= 0 || textureHeight <= 0) {
            throw new IllegalArgumentException("atlas dimensions must be positive");
        }
        final PreparedViews prepared = new PreparedViews(
            prepare(views.front()),
            prepare(views.left()),
            prepare(views.back()),
            prepare(views.right())
        );
        final Bounds bounds = bounds(root, includePath);
        @SuppressWarnings("unchecked")
        final List<Integer>[] candidates = (List<Integer>[]) new List<?>[textureWidth * textureHeight];

        root.visit(new PoseStack(), (pose, path, cubeIndex, cube) -> {
            if (!includePath.test(path)) {
                return;
            }
            for (final ModelPart.Polygon polygon : cube.polygons) {
                final Vector3f normal = pose.transformNormal(polygon.normal(), new Vector3f());
                final AxisFace face = AxisFace.of(normal);
                final VertexSample[] vertices = Arrays.stream(polygon.vertices())
                    .map(vertex -> {
                        final Vector3f point = pose.pose().transformPosition(
                            vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f()
                        ).mul(16.0F);
                        final Projected projected = projected(point, bounds);
                        return new VertexSample(
                            vertex.u() * textureWidth,
                            vertex.v() * textureHeight,
                            projected.x(),
                            projected.y(),
                            projected.z()
                        );
                    })
                    .toArray(VertexSample[]::new);
                if (vertices.length < 3) {
                    continue;
                }
                rasterizeTriangle(
                    vertices[0], vertices[1], vertices[2], face, prepared,
                    path, cubeIndex, textureWidth, textureHeight, candidates
                );
                if (vertices.length == 4) {
                    rasterizeTriangle(
                        vertices[0], vertices[2], vertices[3], face, prepared,
                        path, cubeIndex, textureWidth, textureHeight, candidates
                    );
                }
            }
        });

        final BufferedImage atlas = new BufferedImage(
            textureWidth,
            textureHeight,
            BufferedImage.TYPE_INT_ARGB
        );
        for (int index = 0; index < candidates.length; index++) {
            if (candidates[index] == null || candidates[index].isEmpty()) {
                continue;
            }
            atlas.setRGB(
                index % textureWidth,
                index / textureWidth,
                0xFF000000 | selectConceptMedoid(candidates[index])
            );
        }
        return cleanPixelArt(atlas);
    }

    /**
     * Turns painterly point samples into a deliberate, bounded pixel-art palette without
     * painting outside real UV coverage. Kept package-visible so its clustering and accent
     * policy can be tested independently of Minecraft geometry.
     */
    static BufferedImage cleanPixelArt(final BufferedImage source) {
        final int opaque = opaquePixelCount(source);
        if (opaque == 0) {
            return copy(source);
        }
        final int[] paletteSizes = {20, 18, 16, 14};
        BufferedImage best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (final int paletteSize : paletteSizes) {
            final BufferedImage candidate = mergeTinyRegions(
                snapToPalette(source, paletteSize),
                Math.max(1, opaque / 100)
            );
            final CleanupMetrics metrics = cleanupMetrics(candidate);
            final double score = Math.max(0.0, 10.0 - metrics.effectivePalette()) * 2.0
                + Math.max(0.0, 0.25 - metrics.variedEdges()) * 5.0
                + Math.max(0.0, metrics.variedEdges() - 0.72) * 10.0
                + Math.max(0.0, metrics.singletonShare() - 0.20) * 10.0;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
            if (metrics.effectivePalette() >= 10.0
                && metrics.variedEdges() >= 0.25
                && metrics.variedEdges() <= 0.72
                && metrics.singletonShare() <= 0.20) {
                return candidate;
            }
        }
        return best;
    }

    private static BufferedImage snapToPalette(final BufferedImage source, final int maximumColors) {
        final Map<Integer, Integer> histogram = new HashMap<>();
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                final int argb = source.getRGB(x, y);
                if ((argb >>> 24) != 0) {
                    histogram.merge(argb & 0xFFFFFF, 1, Integer::sum);
                }
            }
        }
        final List<ColorPoint> points = histogram.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new ColorPoint(
                entry.getKey(),
                entry.getValue(),
                toOklab(entry.getKey())
            ))
            .toList();
        if (points.size() <= maximumColors) {
            return copy(source);
        }
        final List<ColorPoint> reservedAccents = reservedAccents(
            points,
            Math.min(6, Math.max(2, maximumColors / 3))
        );
        final List<ColorPoint> reducible = points.stream()
            .filter(point -> reservedAccents.stream().noneMatch(accent -> accent.rgb() == point.rgb()))
            .toList();
        final List<ColorBox> boxes = new ArrayList<>();
        if (!reducible.isEmpty()) {
            boxes.add(new ColorBox(new ArrayList<>(reducible)));
        }
        final int reduciblePaletteSize = Math.max(1, maximumColors - reservedAccents.size());
        while (boxes.size() < reduciblePaletteSize) {
            final ColorBox selected = boxes.stream()
                .filter(box -> box.points().size() > 1)
                .max(Comparator.comparingDouble(ColorBox::priority)
                    .thenComparingInt(box -> -box.minimumRgb()))
                .orElse(null);
            if (selected == null) {
                break;
            }
            boxes.remove(selected);
            final List<ColorPoint> ordered = new ArrayList<>(selected.points());
            final int axis = selected.widestAxis();
            ordered.sort(Comparator
                .comparingDouble((ColorPoint point) -> point.lab().component(axis))
                .thenComparingInt(ColorPoint::rgb));
            final long totalWeight = ordered.stream().mapToLong(ColorPoint::weight).sum();
            long accumulated = 0L;
            int split = 1;
            for (int index = 0; index < ordered.size() - 1; index++) {
                accumulated += ordered.get(index).weight();
                if (accumulated * 2L >= totalWeight) {
                    split = index + 1;
                    break;
                }
            }
            boxes.add(new ColorBox(new ArrayList<>(ordered.subList(0, split))));
            boxes.add(new ColorBox(new ArrayList<>(ordered.subList(split, ordered.size()))));
        }
        final List<ColorPoint> palette = java.util.stream.Stream.concat(
                reservedAccents.stream(),
                boxes.stream().map(ConceptTextureBaker::medoid)
            )
            .distinct()
            .sorted(Comparator.comparingInt(ColorPoint::rgb))
            .toList();
        final BufferedImage result = new BufferedImage(
            source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB
        );
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                final int argb = source.getRGB(x, y);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                final Lab lab = toOklab(argb & 0xFFFFFF);
                final ColorPoint nearest = palette.stream()
                    .min(Comparator.comparingDouble((ColorPoint point) -> distance(lab, point.lab()))
                        .thenComparingInt(ColorPoint::rgb))
                    .orElseThrow();
                result.setRGB(x, y, 0xFF000000 | nearest.rgb());
            }
        }
        return result;
    }

    /**
     * Reserves a few genuinely chromatic source colors before weighted median-cut. Without
     * this pass, a small eye, ember, jewel, or horn-tip cluster is swallowed by the much
     * larger neutral armor/hair field even though it is one of the concept's focal cues.
     */
    private static List<ColorPoint> reservedAccents(
        final List<ColorPoint> points,
        final int maximumAccents
    ) {
        final List<ColorPoint> candidates = points.stream()
            .filter(point -> chroma(point.lab()) >= 0.055)
            .filter(point -> accentSupport(point, points) >= 2)
            .toList();
        final List<ColorPoint> selected = new ArrayList<>();
        while (selected.size() < maximumAccents) {
            final ColorPoint next = candidates.stream()
                .filter(candidate -> selected.stream().noneMatch(color -> color.rgb() == candidate.rgb()))
                .filter(candidate -> selected.stream().allMatch(color ->
                    distance(candidate.lab(), color.lab()) >= 0.035))
                .max(Comparator
                    .comparingDouble((ColorPoint candidate) ->
                        accentPriority(candidate, selected, points))
                    .thenComparingInt(candidate -> -candidate.rgb()))
                .orElse(null);
            if (next == null) {
                break;
            }
            selected.add(next);
        }
        return List.copyOf(selected);
    }

    private static double accentPriority(
        final ColorPoint candidate,
        final List<ColorPoint> selected,
        final List<ColorPoint> points
    ) {
        final int support = accentSupport(candidate, points);
        final double salience = chroma(candidate.lab())
            * (1.0 + Math.min(4.0, Math.log1p(support)) * 0.16);
        if (selected.isEmpty()) {
            return salience;
        }
        final double separation = selected.stream()
            .mapToDouble(color -> distance(candidate.lab(), color.lab()))
            .min()
            .orElse(0.0);
        return salience * Math.max(0.20, separation);
    }

    private static int accentSupport(
        final ColorPoint candidate,
        final List<ColorPoint> points
    ) {
        return points.stream()
            .filter(point -> distance(candidate.lab(), point.lab()) <= 0.030)
            .mapToInt(ColorPoint::weight)
            .sum();
    }

    private static double chroma(final Lab color) {
        return Math.hypot(color.a(), color.b());
    }

    private static ColorPoint medoid(final ColorBox box) {
        return box.points().stream()
            .min(Comparator.comparingDouble((ColorPoint candidate) -> box.points().stream()
                    .mapToDouble(point -> point.weight() * distance(candidate.lab(), point.lab()))
                    .sum())
                .thenComparingInt(ColorPoint::rgb))
            .orElseThrow();
    }

    private static BufferedImage mergeTinyRegions(
        final BufferedImage source,
        final int maximumProtectedAccentPixels
    ) {
        BufferedImage current = copy(source);
        for (int pass = 0; pass < 3; pass++) {
            int protectedAccentPixels = 0;
            final List<ColorRegion> regions = regions(current);
            final Map<Integer, Integer> replacements = new HashMap<>();
            for (final ColorRegion region : regions) {
                if (region.pixels().size() > 2) {
                    continue;
                }
                final Map<Integer, Integer> sharedEdges = neighboringEdges(current, region);
                if (sharedEdges.isEmpty()) {
                    continue;
                }
                final int dominantNeighbor = sharedEdges.entrySet().stream()
                    .max(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparingDouble(entry -> -distance(
                            toOklab(region.rgb()), toOklab(entry.getKey())
                        ))
                        .thenComparingInt(entry -> -entry.getKey()))
                    .orElseThrow()
                    .getKey();
                final boolean accent = distance(toOklab(region.rgb()), toOklab(dominantNeighbor)) >= 0.12
                    && hasAccentSupport(current, region);
                if (accent
                    && protectedAccentPixels + region.pixels().size() <= maximumProtectedAccentPixels) {
                    protectedAccentPixels += region.pixels().size();
                    continue;
                }
                for (final int index : region.pixels()) {
                    replacements.put(index, dominantNeighbor);
                }
            }
            if (replacements.isEmpty()) {
                break;
            }
            final BufferedImage next = copy(current);
            for (final Map.Entry<Integer, Integer> replacement : replacements.entrySet()) {
                next.setRGB(
                    replacement.getKey() % current.getWidth(),
                    replacement.getKey() / current.getWidth(),
                    0xFF000000 | replacement.getValue()
                );
            }
            current = next;
        }
        return current;
    }

    private static List<ColorRegion> regions(final BufferedImage image) {
        final boolean[] visited = new boolean[image.getWidth() * image.getHeight()];
        final List<ColorRegion> regions = new ArrayList<>();
        for (int start = 0; start < visited.length; start++) {
            final int startX = start % image.getWidth();
            final int startY = start / image.getWidth();
            final int argb = image.getRGB(startX, startY);
            if (visited[start] || (argb >>> 24) == 0) {
                continue;
            }
            final int rgb = argb & 0xFFFFFF;
            final ArrayDeque<Integer> pending = new ArrayDeque<>();
            final List<Integer> pixels = new ArrayList<>();
            pending.add(start);
            visited[start] = true;
            while (!pending.isEmpty()) {
                final int index = pending.removeFirst();
                pixels.add(index);
                final int x = index % image.getWidth();
                final int y = index / image.getWidth();
                enqueueSameColor(image, x - 1, y, rgb, visited, pending);
                enqueueSameColor(image, x + 1, y, rgb, visited, pending);
                enqueueSameColor(image, x, y - 1, rgb, visited, pending);
                enqueueSameColor(image, x, y + 1, rgb, visited, pending);
            }
            regions.add(new ColorRegion(rgb, List.copyOf(pixels)));
        }
        return regions;
    }

    private static void enqueueSameColor(
        final BufferedImage image,
        final int x,
        final int y,
        final int rgb,
        final boolean[] visited,
        final ArrayDeque<Integer> pending
    ) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return;
        }
        final int index = y * image.getWidth() + x;
        if (!visited[index] && (image.getRGB(x, y) >>> 24) != 0
            && (image.getRGB(x, y) & 0xFFFFFF) == rgb) {
            visited[index] = true;
            pending.addLast(index);
        }
    }

    private static Map<Integer, Integer> neighboringEdges(
        final BufferedImage image,
        final ColorRegion region
    ) {
        final Map<Integer, Integer> result = new HashMap<>();
        for (final int index : region.pixels()) {
            final int x = index % image.getWidth();
            final int y = index / image.getWidth();
            countNeighbor(image, x - 1, y, region.rgb(), result);
            countNeighbor(image, x + 1, y, region.rgb(), result);
            countNeighbor(image, x, y - 1, region.rgb(), result);
            countNeighbor(image, x, y + 1, region.rgb(), result);
        }
        return result;
    }

    private static void countNeighbor(
        final BufferedImage image,
        final int x,
        final int y,
        final int ownRgb,
        final Map<Integer, Integer> counts
    ) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return;
        }
        final int argb = image.getRGB(x, y);
        final int rgb = argb & 0xFFFFFF;
        if ((argb >>> 24) != 0 && rgb != ownRgb) {
            counts.merge(rgb, 1, Integer::sum);
        }
    }

    private static boolean hasAccentSupport(final BufferedImage image, final ColorRegion region) {
        if (region.pixels().size() >= 2) {
            return true;
        }
        final int index = region.pixels().getFirst();
        final int x = index % image.getWidth();
        final int y = index / image.getWidth();
        final Lab own = toOklab(region.rgb());
        for (int deltaY = -1; deltaY <= 1; deltaY++) {
            for (int deltaX = -1; deltaX <= 1; deltaX++) {
                if (deltaX == 0 && deltaY == 0) {
                    continue;
                }
                final int nextX = x + deltaX;
                final int nextY = y + deltaY;
                if (nextX < 0 || nextY < 0 || nextX >= image.getWidth() || nextY >= image.getHeight()) {
                    continue;
                }
                final int argb = image.getRGB(nextX, nextY);
                if ((argb >>> 24) != 0
                    && distance(own, toOklab(argb & 0xFFFFFF)) <= 0.04) {
                    return true;
                }
            }
        }
        return false;
    }

    private static CleanupMetrics cleanupMetrics(final BufferedImage image) {
        final Map<Integer, Integer> histogram = new HashMap<>();
        int compared = 0;
        int varied = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int argb = image.getRGB(x, y);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                final int rgb = argb & 0xFFFFFF;
                histogram.merge(rgb, 1, Integer::sum);
                if (x + 1 < image.getWidth() && (image.getRGB(x + 1, y) >>> 24) != 0) {
                    compared++;
                    varied += rgb == (image.getRGB(x + 1, y) & 0xFFFFFF) ? 0 : 1;
                }
                if (y + 1 < image.getHeight() && (image.getRGB(x, y + 1) >>> 24) != 0) {
                    compared++;
                    varied += rgb == (image.getRGB(x, y + 1) & 0xFFFFFF) ? 0 : 1;
                }
            }
        }
        final int total = histogram.values().stream().mapToInt(Integer::intValue).sum();
        double entropy = 0.0;
        for (final int count : histogram.values()) {
            final double probability = count / (double) total;
            entropy -= probability * Math.log(probability);
        }
        final List<ColorRegion> regions = regions(image);
        final long singletonCount = regions.stream().filter(region -> region.pixels().size() == 1).count();
        return new CleanupMetrics(
            Math.exp(entropy),
            compared == 0 ? 0.0 : varied / (double) compared,
            total == 0 ? 0.0 : singletonCount / (double) total
        );
    }

    private static int opaquePixelCount(final BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                count += (image.getRGB(x, y) >>> 24) == 0 ? 0 : 1;
            }
        }
        return count;
    }

    private static BufferedImage copy(final BufferedImage source) {
        final BufferedImage result = new BufferedImage(
            source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB
        );
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                result.setRGB(x, y, source.getRGB(x, y));
            }
        }
        return result;
    }

    private static Lab toOklab(final int rgb) {
        final double red = linear((rgb >>> 16) & 0xFF);
        final double green = linear((rgb >>> 8) & 0xFF);
        final double blue = linear(rgb & 0xFF);
        final double l = Math.cbrt(0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue);
        final double m = Math.cbrt(0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue);
        final double s = Math.cbrt(0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue);
        return new Lab(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        );
    }

    private static double linear(final int channel) {
        final double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static double distance(final Lab first, final Lab second) {
        final double deltaL = first.l() - second.l();
        final double deltaA = first.a() - second.a();
        final double deltaB = first.b() - second.b();
        return Math.sqrt(deltaL * deltaL + deltaA * deltaA + deltaB * deltaB);
    }

    static Rectangle subjectBounds(final BufferedImage image) {
        return boundsOf(largestForeground(image)).rectangle();
    }

    private static void rasterizeTriangle(
        final VertexSample first,
        final VertexSample second,
        final VertexSample third,
        final AxisFace face,
        final PreparedViews views,
        final String path,
        final int cubeIndex,
        final int width,
        final int height,
        final List<Integer>[] candidates
    ) {
        final double denominator = edge(first.u(), first.v(), second.u(), second.v(), third.u(), third.v());
        if (Math.abs(denominator) < 1.0E-9) {
            return;
        }
        final int minimumX = Math.max(0, (int) Math.floor(Math.min(first.u(), Math.min(second.u(), third.u()))));
        final int maximumX = Math.min(width - 1,
            (int) Math.ceil(Math.max(first.u(), Math.max(second.u(), third.u()))) - 1);
        final int minimumY = Math.max(0, (int) Math.floor(Math.min(first.v(), Math.min(second.v(), third.v()))));
        final int maximumY = Math.min(height - 1,
            (int) Math.ceil(Math.max(first.v(), Math.max(second.v(), third.v()))) - 1);
        for (int y = minimumY; y <= maximumY; y++) {
            for (int x = minimumX; x <= maximumX; x++) {
                final double sampleU = x + 0.5;
                final double sampleV = y + 0.5;
                final double firstWeight = edge(
                    second.u(), second.v(), third.u(), third.v(), sampleU, sampleV
                ) / denominator;
                final double secondWeight = edge(
                    third.u(), third.v(), first.u(), first.v(), sampleU, sampleV
                ) / denominator;
                final double thirdWeight = 1.0 - firstWeight - secondWeight;
                if (firstWeight < -1.0E-7 || secondWeight < -1.0E-7 || thirdWeight < -1.0E-7) {
                    continue;
                }
                final double conceptX = clamp(
                    firstWeight * first.x() + secondWeight * second.x() + thirdWeight * third.x()
                );
                final double conceptY = clamp(
                    firstWeight * first.y() + secondWeight * second.y() + thirdWeight * third.y()
                );
                final double conceptZ = clamp(
                    firstWeight * first.z() + secondWeight * second.z() + thirdWeight * third.z()
                );
                final double minimumU = Math.min(first.u(), Math.min(second.u(), third.u()));
                final double maximumU = Math.max(first.u(), Math.max(second.u(), third.u()));
                final double minimumV = Math.min(first.v(), Math.min(second.v(), third.v()));
                final double maximumV = Math.max(first.v(), Math.max(second.v(), third.v()));
                final double faceU = range(sampleU, minimumU, maximumU);
                final double faceV = range(sampleV, minimumV, maximumV);
                final int rgb = litRgb(
                    views.sample(
                        face, conceptX, conceptY, conceptZ, path, cubeIndex, faceU, faceV
                    ),
                    face.light()
                );
                final int index = y * width + x;
                if (candidates[index] == null) {
                    candidates[index] = new ArrayList<>(2);
                }
                candidates[index].add(rgb);
            }
        }
    }

    private static double edge(
        final double firstX,
        final double firstY,
        final double secondX,
        final double secondY,
        final double pointX,
        final double pointY
    ) {
        return (pointX - firstX) * (secondY - firstY)
            - (pointY - firstY) * (secondX - firstX);
    }

    private static PreparedView prepare(final BufferedImage image) {
        final Mask subject = largestForeground(image);
        final PixelBounds bounds = boundsOf(subject);
        final int[] nearest = nearestForeground(subject);
        return new PreparedView(image, bounds, nearest);
    }

    private static Mask largestForeground(final BufferedImage image) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final boolean[] background = new boolean[width * height];
        final ArrayDeque<Integer> pending = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            enqueueBackground(x, 0, width, background, pending);
            enqueueBackground(x, height - 1, width, background, pending);
        }
        for (int y = 0; y < height; y++) {
            enqueueBackground(0, y, width, background, pending);
            enqueueBackground(width - 1, y, width, background, pending);
        }
        while (!pending.isEmpty()) {
            final int index = pending.removeFirst();
            final int x = index % width;
            final int y = index / width;
            final int source = image.getRGB(x, y);
            visitBackground(image, x - 1, y, source, background, pending);
            visitBackground(image, x + 1, y, source, background, pending);
            visitBackground(image, x, y - 1, source, background, pending);
            visitBackground(image, x, y + 1, source, background, pending);
        }

        final boolean[] foreground = new boolean[background.length];
        for (int index = 0; index < foreground.length; index++) {
            foreground[index] = !background[index];
        }
        final boolean[] visited = new boolean[foreground.length];
        boolean[] largest = null;
        int largestScore = -1;
        for (int start = 0; start < foreground.length; start++) {
            if (!foreground[start] || visited[start]) {
                continue;
            }
            final ArrayDeque<Integer> componentQueue = new ArrayDeque<>();
            final List<Integer> component = new ArrayList<>();
            componentQueue.add(start);
            visited[start] = true;
            int minimumX = width;
            int minimumY = height;
            int maximumX = -1;
            int maximumY = -1;
            while (!componentQueue.isEmpty()) {
                final int index = componentQueue.removeFirst();
                component.add(index);
                final int x = index % width;
                final int y = index / width;
                minimumX = Math.min(minimumX, x);
                minimumY = Math.min(minimumY, y);
                maximumX = Math.max(maximumX, x);
                maximumY = Math.max(maximumY, y);
                for (int deltaY = -1; deltaY <= 1; deltaY++) {
                    for (int deltaX = -1; deltaX <= 1; deltaX++) {
                        final int nextX = x + deltaX;
                        final int nextY = y + deltaY;
                        if (nextX < 0 || nextY < 0 || nextX >= width || nextY >= height) {
                            continue;
                        }
                        final int next = nextY * width + nextX;
                        if (foreground[next] && !visited[next]) {
                            visited[next] = true;
                            componentQueue.addLast(next);
                        }
                    }
                }
            }
            final int componentWidth = maximumX - minimumX + 1;
            final int componentHeight = maximumY - minimumY + 1;
            final int score = component.size() + 4 * Math.min(componentWidth, componentHeight);
            if (score > largestScore) {
                largestScore = score;
                largest = new boolean[foreground.length];
                for (final int index : component) {
                    largest[index] = true;
                }
            }
        }
        if (largest == null) {
            throw new IllegalArgumentException("concept view contains no separable subject");
        }
        return new Mask(width, height, largest);
    }

    private static void enqueueBackground(
        final int x,
        final int y,
        final int width,
        final boolean[] background,
        final ArrayDeque<Integer> pending
    ) {
        final int index = y * width + x;
        if (!background[index]) {
            background[index] = true;
            pending.addLast(index);
        }
    }

    private static void visitBackground(
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
        if (background[index]) {
            return;
        }
        final int candidate = image.getRGB(x, y);
        if (saturation(candidate) <= BACKGROUND_MAXIMUM_SATURATION
            && colorDistance(source, candidate) <= BACKGROUND_STEP_DISTANCE) {
            background[index] = true;
            pending.addLast(index);
        }
    }

    private static PixelBounds boundsOf(final Mask mask) {
        int minimumX = mask.width();
        int minimumY = mask.height();
        int maximumX = -1;
        int maximumY = -1;
        for (int y = 0; y < mask.height(); y++) {
            for (int x = 0; x < mask.width(); x++) {
                if (!mask.pixels()[y * mask.width() + x]) {
                    continue;
                }
                minimumX = Math.min(minimumX, x);
                minimumY = Math.min(minimumY, y);
                maximumX = Math.max(maximumX, x);
                maximumY = Math.max(maximumY, y);
            }
        }
        if (maximumX < minimumX || maximumY < minimumY) {
            throw new IllegalArgumentException("concept subject is empty");
        }
        return new PixelBounds(minimumX, minimumY, maximumX, maximumY);
    }

    private static int[] nearestForeground(final Mask mask) {
        final int[] nearest = new int[mask.pixels().length];
        Arrays.fill(nearest, -1);
        final ArrayDeque<Integer> pending = new ArrayDeque<>();
        for (int index = 0; index < mask.pixels().length; index++) {
            if (mask.pixels()[index]) {
                nearest[index] = index;
                pending.addLast(index);
            }
        }
        while (!pending.isEmpty()) {
            final int index = pending.removeFirst();
            final int x = index % mask.width();
            final int y = index / mask.width();
            if (x > 0) {
                propagateNearest(index - 1, nearest[index], nearest, pending);
            }
            if (x + 1 < mask.width()) {
                propagateNearest(index + 1, nearest[index], nearest, pending);
            }
            if (y > 0) {
                propagateNearest(index - mask.width(), nearest[index], nearest, pending);
            }
            if (y + 1 < mask.height()) {
                propagateNearest(index + mask.width(), nearest[index], nearest, pending);
            }
        }
        return nearest;
    }

    private static void propagateNearest(
        final int index,
        final int source,
        final int[] nearest,
        final ArrayDeque<Integer> pending
    ) {
        if (nearest[index] == -1) {
            nearest[index] = source;
            pending.addLast(index);
        }
    }

    private static Bounds bounds(final ModelPart root, final Predicate<String> includePath) {
        final float[] values = {
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
        };
        root.visit(new PoseStack(), (pose, path, cubeIndex, cube) -> {
            if (!includePath.test(path)) {
                return;
            }
            for (final ModelPart.Polygon polygon : cube.polygons) {
                for (final ModelPart.Vertex vertex : polygon.vertices()) {
                    final Vector3f point = pose.pose().transformPosition(
                        vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f()
                    ).mul(16.0F);
                    values[0] = Math.min(values[0], point.x());
                    values[1] = Math.min(values[1], point.y());
                    values[2] = Math.min(values[2], point.z());
                    values[3] = Math.max(values[3], point.x());
                    values[4] = Math.max(values[4], point.y());
                    values[5] = Math.max(values[5], point.z());
                }
            }
        });
        return new Bounds(values[0], values[1], values[2], values[3], values[4], values[5]);
    }

    private static Projected projected(final Vector3f point, final Bounds bounds) {
        final double x = range(point.x(), bounds.minimumX(), bounds.maximumX());
        final double y = range(point.y(), bounds.minimumY(), bounds.maximumY());
        final double z = range(point.z(), bounds.minimumZ(), bounds.maximumZ());
        return new Projected(x, y, z);
    }

    private static double range(final double value, final double minimum, final double maximum) {
        return clamp((value - minimum) / Math.max(maximum - minimum, 1.0E-9));
    }

    private static double clamp(final double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static int lit(final int channel, final double lighting) {
        return Math.max(0, Math.min(255, (int) Math.round(channel * lighting)));
    }

    private static int litRgb(final int rgb, final double lighting) {
        return (lit((rgb >>> 16) & 0xFF, lighting) << 16)
            | (lit((rgb >>> 8) & 0xFF, lighting) << 8)
            | lit(rgb & 0xFF, lighting);
    }

    private static int selectConceptMedoid(final List<Integer> candidates) {
        final Map<Integer, Integer> counts = new HashMap<>();
        for (final int rgb : candidates) {
            counts.merge(rgb, 1, Integer::sum);
        }
        if (counts.size() == 1) {
            return counts.keySet().iterator().next();
        }
        return counts.keySet().stream()
            .min(Comparator.comparingDouble((Integer candidate) -> counts.entrySet().stream()
                    .mapToDouble(entry -> entry.getValue()
                        * distance(toOklab(candidate), toOklab(entry.getKey())))
                    .sum())
                .thenComparingInt(Integer::intValue))
            .orElseThrow();
    }

    private static double saturation(final int argb) {
        final int red = (argb >>> 16) & 0xFF;
        final int green = (argb >>> 8) & 0xFF;
        final int blue = argb & 0xFF;
        final int maximum = Math.max(red, Math.max(green, blue));
        final int minimum = Math.min(red, Math.min(green, blue));
        return maximum == 0 ? 0.0 : (maximum - minimum) / (double) maximum;
    }

    private static double colorDistance(final int first, final int second) {
        final int red = ((first >>> 16) & 0xFF) - ((second >>> 16) & 0xFF);
        final int green = ((first >>> 8) & 0xFF) - ((second >>> 8) & 0xFF);
        final int blue = (first & 0xFF) - (second & 0xFF);
        return Math.sqrt(red * red + green * green + blue * blue);
    }

    record ConceptViews(
        BufferedImage front,
        BufferedImage left,
        BufferedImage back,
        BufferedImage right
    ) {
        ConceptViews {
            if (front == null || left == null || back == null || right == null) {
                throw new IllegalArgumentException("all four concept views are required");
            }
        }
    }

    private record PreparedViews(
        PreparedView front,
        PreparedView left,
        PreparedView back,
        PreparedView right
    ) {
        int sample(
            final AxisFace face,
            final double x,
            final double y,
            final double z,
            final String path,
            final int cubeIndex,
            final double faceU,
            final double faceV
        ) {
            if (face == AxisFace.FRONT
                && (path.equals("face_head") || path.endsWith("/face_head"))
                && cubeIndex == 0) {
                if (y >= 0.12 && y <= 0.25
                    && ((x >= 0.36 && x <= 0.47)
                        || (x >= 0.53 && x <= 0.64))) {
                    return front.sampleVioletAccent(x, y);
                }
                return front.samplePaleFace(x, y);
            }
            final MaterialPreference preference = path.contains("/gold_eye_band")
                || path.endsWith("/central_eye")
                || path.endsWith("/left_eye_cluster")
                || path.endsWith("/right_eye_cluster")
                ? MaterialPreference.GOLD
                : path.endsWith("/faceted_core") || path.contains("/outer_rune_")
                    ? MaterialPreference.VIOLET
                    : path.contains("/infernal_torso")
                    || path.contains("/right_arm")
                    || path.contains("/left_arm")
                    || path.contains("/right_ember_claw")
                    || path.contains("/left_ember_claw")
                        ? MaterialPreference.WARM_RED
                        : MaterialPreference.NONE;
            return switch (face) {
                case FRONT -> front.sampleMaterial(x, y, preference);
                case BACK -> back.sampleMaterial(1.0 - x, y, preference);
                case LEFT -> left.sampleMaterial(z, y, preference);
                case RIGHT -> right.sampleMaterial(1.0 - z, y, preference);
                case TOP, BOTTOM -> average(
                    front.sample(x, y),
                    back.sample(1.0 - x, y),
                    left.sample(z, y),
                    right.sample(1.0 - z, y)
                );
            };
        }

        private static int average(final int... colors) {
            int red = 0;
            int green = 0;
            int blue = 0;
            for (final int color : colors) {
                red += (color >>> 16) & 0xFF;
                green += (color >>> 8) & 0xFF;
                blue += color & 0xFF;
            }
            return ((red / colors.length) << 16)
                | ((green / colors.length) << 8)
                | (blue / colors.length);
        }
    }

    private record PreparedView(BufferedImage image, PixelBounds bounds, int[] nearest) {
        int sample(final double normalizedX, final double normalizedY) {
            final int x = Math.max(bounds.minimumX(), Math.min(
                bounds.maximumX(),
                bounds.minimumX() + (int) Math.round(normalizedX * (bounds.width() - 1))
            ));
            final int y = Math.max(bounds.minimumY(), Math.min(
                bounds.maximumY(),
                bounds.minimumY() + (int) Math.round(normalizedY * (bounds.height() - 1))
            ));
            final int nearestIndex = nearest[y * image.getWidth() + x];
            final int center = image.getRGB(
                nearestIndex % image.getWidth(), nearestIndex / image.getWidth()
            ) & 0xFFFFFF;
            return center;
        }

        int sampleMaterial(
            final double normalizedX,
            final double normalizedY,
            final MaterialPreference preference
        ) {
            if (preference == MaterialPreference.NONE) {
                return sample(normalizedX, normalizedY);
            }
            final int x = Math.max(bounds.minimumX(), Math.min(
                bounds.maximumX(),
                bounds.minimumX() + (int) Math.round(normalizedX * (bounds.width() - 1))
            ));
            final int y = Math.max(bounds.minimumY(), Math.min(
                bounds.maximumY(),
                bounds.minimumY() + (int) Math.round(normalizedY * (bounds.height() - 1))
            ));
            final int centerIndex = nearest[y * image.getWidth() + x];
            final int center = image.getRGB(
                centerIndex % image.getWidth(), centerIndex / image.getWidth()
            ) & 0xFFFFFF;
            if (preference.matches(center)) {
                return center;
            }
            final int radius = preference == MaterialPreference.GOLD
                || preference == MaterialPreference.VIOLET ? 12 : 5;
            int selected = center;
            double selectedPriority = Double.NEGATIVE_INFINITY;
            for (int sampleY = Math.max(bounds.minimumY(), y - radius);
                 sampleY <= Math.min(bounds.maximumY(), y + radius); sampleY++) {
                for (int sampleX = Math.max(bounds.minimumX(), x - radius);
                     sampleX <= Math.min(bounds.maximumX(), x + radius); sampleX++) {
                    final int candidateIndex = nearest[sampleY * image.getWidth() + sampleX];
                    final int candidate = image.getRGB(
                        candidateIndex % image.getWidth(), candidateIndex / image.getWidth()
                    ) & 0xFFFFFF;
                    final Lab candidateLab = toOklab(candidate);
                    if (!preference.matches(candidate)
                        || !hasLocalConceptSupport(candidate, sampleX, sampleY, radius)) {
                        continue;
                    }
                    final double priority = chroma(candidateLab) + saturation(candidate) * 0.12;
                    if (priority > selectedPriority
                        || priority == selectedPriority && candidate < selected) {
                        selected = candidate;
                        selectedPriority = priority;
                    }
                }
            }
            return selected;
        }

        int sampleVioletAccent(final double normalizedX, final double normalizedY) {
            final int x = Math.max(bounds.minimumX(), Math.min(
                bounds.maximumX(),
                bounds.minimumX() + (int) Math.round(normalizedX * (bounds.width() - 1))
            ));
            final int y = Math.max(bounds.minimumY(), Math.min(
                bounds.maximumY(),
                bounds.minimumY() + (int) Math.round(normalizedY * (bounds.height() - 1))
            ));
            final int centerIndex = nearest[y * image.getWidth() + x];
            final int center = image.getRGB(
                centerIndex % image.getWidth(), centerIndex / image.getWidth()
            ) & 0xFFFFFF;
            int selected = center;
            double selectedPriority = Double.NEGATIVE_INFINITY;
            for (int sampleY = bounds.minimumY(); sampleY <= bounds.maximumY(); sampleY++) {
                for (int sampleX = bounds.minimumX(); sampleX <= bounds.maximumX(); sampleX++) {
                    final int candidateIndex = nearest[sampleY * image.getWidth() + sampleX];
                    final int candidate = image.getRGB(
                        candidateIndex % image.getWidth(), candidateIndex / image.getWidth()
                    ) & 0xFFFFFF;
                    final int red = (candidate >>> 16) & 0xFF;
                    final int green = (candidate >>> 8) & 0xFF;
                    final int blue = candidate & 0xFF;
                    if (red < 18 || blue < 65 || red < green * 1.05
                        || blue < green * 1.15
                        || !hasLocalConceptSupport(candidate, sampleX, sampleY, 3)) {
                        continue;
                    }
                    final Lab lab = toOklab(candidate);
                    final double priority = chroma(lab) + saturation(candidate) * 0.12;
                    if (priority > selectedPriority
                        || priority == selectedPriority && candidate < selected) {
                        selected = candidate;
                        selectedPriority = priority;
                    }
                }
            }
            return selected;
        }

        int samplePaleFace(final double normalizedX, final double normalizedY) {
            final int x = Math.max(bounds.minimumX(), Math.min(
                bounds.maximumX(),
                bounds.minimumX() + (int) Math.round(normalizedX * (bounds.width() - 1))
            ));
            final int y = Math.max(bounds.minimumY(), Math.min(
                bounds.maximumY(),
                bounds.minimumY() + (int) Math.round(normalizedY * (bounds.height() - 1))
            ));
            final int centerIndex = nearest[y * image.getWidth() + x];
            int selected = image.getRGB(
                centerIndex % image.getWidth(), centerIndex / image.getWidth()
            ) & 0xFFFFFF;
            double selectedPriority = Double.NEGATIVE_INFINITY;
            final int radius = 12;
            for (int sampleY = Math.max(bounds.minimumY(), y - radius);
                 sampleY <= Math.min(bounds.maximumY(), y + radius); sampleY++) {
                for (int sampleX = Math.max(bounds.minimumX(), x - radius);
                     sampleX <= Math.min(bounds.maximumX(), x + radius); sampleX++) {
                    final int candidateIndex = nearest[sampleY * image.getWidth() + sampleX];
                    final int candidate = image.getRGB(
                        candidateIndex % image.getWidth(), candidateIndex / image.getWidth()
                    ) & 0xFFFFFF;
                    final int red = (candidate >>> 16) & 0xFF;
                    final int green = (candidate >>> 8) & 0xFF;
                    final int blue = candidate & 0xFF;
                    if (red < 125 || green < 135 || blue < 145 || saturation(candidate) > 0.30) {
                        continue;
                    }
                    final double priority = (red + green + blue) / 765.0
                        - colorDistance(candidate, selected) / 2550.0;
                    if (priority > selectedPriority
                        || priority == selectedPriority && candidate < selected) {
                        selected = candidate;
                        selectedPriority = priority;
                    }
                }
            }
            return selected;
        }

        private boolean hasLocalConceptSupport(
            final int candidate,
            final int centerX,
            final int centerY,
            final int radius
        ) {
            final Lab candidateLab = toOklab(candidate);
            int support = 0;
            for (int y = Math.max(bounds.minimumY(), centerY - radius);
                 y <= Math.min(bounds.maximumY(), centerY + radius); y++) {
                for (int x = Math.max(bounds.minimumX(), centerX - radius);
                     x <= Math.min(bounds.maximumX(), centerX + radius); x++) {
                    final int index = nearest[y * image.getWidth() + x];
                    final int rgb = image.getRGB(index % image.getWidth(), index / image.getWidth())
                        & 0xFFFFFF;
                    if (distance(candidateLab, toOklab(rgb)) <= 0.035 && ++support >= 2) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private record PixelBounds(int minimumX, int minimumY, int maximumX, int maximumY) {
        int width() {
            return maximumX - minimumX + 1;
        }

        int height() {
            return maximumY - minimumY + 1;
        }

        Rectangle rectangle() {
            return new Rectangle(minimumX, minimumY, width(), height());
        }
    }

    private record Mask(int width, int height, boolean[] pixels) {
    }

    private record Bounds(
        float minimumX,
        float minimumY,
        float minimumZ,
        float maximumX,
        float maximumY,
        float maximumZ
    ) {
    }

    private record Projected(double x, double y, double z) {
    }

    private record VertexSample(double u, double v, double x, double y, double z) {
    }

    private record Lab(double l, double a, double b) {
        double component(final int axis) {
            return switch (axis) {
                case 0 -> l;
                case 1 -> a;
                case 2 -> b;
                default -> throw new IllegalArgumentException("invalid Oklab axis " + axis);
            };
        }
    }

    private record ColorPoint(int rgb, int weight, Lab lab) {
    }

    private record ColorBox(List<ColorPoint> points) {
        int widestAxis() {
            double widest = -1.0;
            int axis = 0;
            for (int candidate = 0; candidate < 3; candidate++) {
                final int current = candidate;
                final double minimum = points.stream()
                    .mapToDouble(point -> point.lab().component(current)).min().orElse(0.0);
                final double maximum = points.stream()
                    .mapToDouble(point -> point.lab().component(current)).max().orElse(0.0);
                final double range = maximum - minimum;
                if (range > widest) {
                    widest = range;
                    axis = candidate;
                }
            }
            return axis;
        }

        double priority() {
            final int axis = widestAxis();
            final double minimum = points.stream()
                .mapToDouble(point -> point.lab().component(axis)).min().orElse(0.0);
            final double maximum = points.stream()
                .mapToDouble(point -> point.lab().component(axis)).max().orElse(0.0);
            final long weight = points.stream().mapToLong(ColorPoint::weight).sum();
            return (maximum - minimum) * weight;
        }

        int minimumRgb() {
            return points.stream().mapToInt(ColorPoint::rgb).min().orElse(0);
        }
    }

    private record ColorRegion(int rgb, List<Integer> pixels) {
    }

    private record CleanupMetrics(
        double effectivePalette,
        double variedEdges,
        double singletonShare
    ) {
    }

    private enum MaterialPreference {
        NONE,
        WARM_RED,
        GOLD,
        VIOLET;

        boolean matches(final int rgb) {
            final int red = (rgb >>> 16) & 0xFF;
            final int green = (rgb >>> 8) & 0xFF;
            final int blue = rgb & 0xFF;
            return switch (this) {
                case NONE -> true;
                case WARM_RED -> red >= 70 && green <= red * 0.45
                    && red >= blue * 1.35;
                case GOLD -> red >= 115 && green >= 55 && red >= green * 1.20
                    && green >= blue * 1.35;
                case VIOLET -> red >= 18 && blue >= 65 && red >= green * 1.15
                    && blue >= green * 2.50;
            };
        }
    }

    private enum AxisFace {
        FRONT(1.00),
        BACK(0.82),
        LEFT(0.91),
        RIGHT(0.94),
        TOP(1.10),
        BOTTOM(0.72);

        private final double light;

        AxisFace(final double light) {
            this.light = light;
        }

        double light() {
            return light;
        }

        static AxisFace of(final Vector3f normal) {
            if (Math.abs(normal.x()) > Math.abs(normal.y())
                && Math.abs(normal.x()) > Math.abs(normal.z())) {
                return normal.x() < 0.0F ? LEFT : RIGHT;
            }
            if (Math.abs(normal.y()) > Math.abs(normal.z())) {
                return normal.y() < 0.0F ? TOP : BOTTOM;
            }
            return normal.z() < 0.0F ? FRONT : BACK;
        }
    }
}
