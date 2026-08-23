package com.kadamitas.warlockery.client.texture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.kadamitas.warlockery.client.model.BansheeModel;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Acceptance contract for concept-projected, UV-aware creature texture authoring. */
final class ConceptTextureBakerTest {
    @Test
    void findsThePaintedSubjectAgainstARealisticNeutralBoardGradient() {
        final BufferedImage view = boardView(48, 64, new Color(72, 34, 30));

        final Rectangle subject = ConceptTextureBaker.subjectBounds(view);

        assertTrue(Math.abs(subject.x - 10) <= 1, subject.toString());
        assertTrue(Math.abs(subject.y - 6) <= 1, subject.toString());
        assertTrue(Math.abs(subject.width - 28) <= 2, subject.toString());
        assertTrue(Math.abs(subject.height - 52) <= 2, subject.toString());
    }

    @Test
    void projectsIndependentFrontBackLeftAndRightArtworkOntoTheMatchingCubeFaces() {
        final ModelPart root = cubeRoot();
        final ConceptTextureBaker.ConceptViews views = new ConceptTextureBaker.ConceptViews(
            boardView(48, 64, new Color(196, 38, 30)),
            boardView(48, 64, new Color(37, 166, 66)),
            boardView(48, 64, new Color(45, 72, 205)),
            boardView(48, 64, new Color(221, 169, 38))
        );

        final BufferedImage atlas = ConceptTextureBaker.bake(root, 96, 96, views);

        assertChannelDominates(faceCenter(atlas, root, AxisFace.FRONT), 16, "front must use front art");
        assertChannelDominates(faceCenter(atlas, root, AxisFace.LEFT), 8, "left must use left art");
        assertChannelDominates(faceCenter(atlas, root, AxisFace.BACK), 0, "back must use back art");
        final Color right = new Color(faceCenter(atlas, root, AxisFace.RIGHT), true);
        assertTrue(right.getRed() > right.getBlue() * 2 && right.getGreen() > right.getBlue() * 2,
            "right must use the ochre right-view art: " + right);
    }

    @Test
    void keepsAuthoredClustersAndAccentPixelsWhileUnusedUvSpaceStaysTransparent() {
        final ModelPart root = cubeRoot();
        final BufferedImage detailed = detailedBoardView();
        final ConceptTextureBaker.ConceptViews views = new ConceptTextureBaker.ConceptViews(
            detailed,
            detailed,
            detailed,
            detailed
        );

        final BufferedImage atlas = ConceptTextureBaker.bake(root, 96, 96, views);
        final Set<Integer> colors = new HashSet<>();
        boolean foundCyanAccent = false;
        for (int y = 0; y < atlas.getHeight(); y++) {
            for (int x = 0; x < atlas.getWidth(); x++) {
                final int argb = atlas.getRGB(x, y);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                colors.add(argb & 0xFFFFFF);
                final Color color = new Color(argb, true);
                foundCyanAccent |= color.getBlue() > 155
                    && color.getGreen() > 130
                    && color.getRed() < 100;
            }
        }
        assertTrue(colors.size() >= 12, "concept detail must survive the projection: " + colors.size());
        assertTrue(foundCyanAccent, "small high-salience concept accents must survive quantization");
        assertEquals(0, atlas.getRGB(95, 95) >>> 24, "unused UV space must stay transparent");
    }

    @Test
    void identicalInputsProduceByteIdenticalPixels() {
        final ModelPart root = cubeRoot();
        final BufferedImage detailed = detailedBoardView();
        final ConceptTextureBaker.ConceptViews views = new ConceptTextureBaker.ConceptViews(
            detailed,
            detailed,
            detailed,
            detailed
        );

        final BufferedImage first = ConceptTextureBaker.bake(root, 96, 96, views);
        final BufferedImage second = ConceptTextureBaker.bake(root, 96, 96, views);

        assertArrayEquals(pixels(first), pixels(second));
    }

    @Test
    void collapsesPainterlySinglePixelNoiseIntoDeliberatePixelClusters() {
        final BufferedImage noisy = noisyPixelArtFixture();

        final BufferedImage cleaned = ConceptTextureBaker.cleanPixelArt(noisy);
        final PixelArtMetrics metrics = metrics(cleaned);

        assertTrue(metrics.effectivePalette() >= 10.0,
            "cleanup must retain authored material range: " + metrics);
        assertTrue(metrics.variedEdges() >= 0.25 && metrics.variedEdges() <= 0.72,
            "cleanup must form readable clusters instead of flat fill or painterly noise: " + metrics);
        assertTrue(metrics.singletonShare() <= 0.20,
            "isolated one-pixel regions must be cleaned: " + metrics);
        assertEquals(0, cleaned.getRGB(0, 0) >>> 24,
            "cleanup must not paint transparent off-island pixels");
    }

    @Test
    void preservesAClusteredHighContrastAccentButRemovesAnIsolatedSpeck() {
        final BufferedImage source = clusteredAccentFixture();

        final BufferedImage cleaned = ConceptTextureBaker.cleanPixelArt(source);

        final Color firstEye = new Color(cleaned.getRGB(4, 4), true);
        final Color secondEye = new Color(cleaned.getRGB(5, 5), true);
        assertTrue(firstEye.getBlue() > 170 && firstEye.getGreen() > 145 && firstEye.getRed() < 100,
            "first clustered cyan focal pixel must survive: " + firstEye);
        assertTrue(secondEye.getBlue() > 170 && secondEye.getGreen() > 145 && secondEye.getRed() < 100,
            "nearby matching focal pixel must protect the cluster: " + secondEye);
        final Color isolated = new Color(cleaned.getRGB(15, 15), true);
        assertTrue(!(isolated.getRed() > 190 && isolated.getBlue() > 170 && isolated.getGreen() < 80),
            "an isolated magenta speck must merge into its surrounding material: " + isolated);
    }

    @Test
    void realDemonAndBansheeBakesMeetThePixelClusterMarginBeforeBatchInstallation() throws Exception {
        for (final String atlasId : new String[] {"demon", "banshee"}) {
            final BufferedImage generated = ConceptTextureBatchTool.bakeForTest(atlasId);
            final PixelArtMetrics metrics = metrics(generated);
            assertTrue(metrics.effectivePalette() >= 10.0,
                atlasId + " must retain a genuinely authored material range: " + metrics);
            assertTrue(metrics.variedEdges() >= 0.25 && metrics.variedEdges() <= 0.72,
                atlasId + " must use deliberate pixel clusters: " + metrics);
            assertTrue(metrics.singletonShare() <= 0.20,
                atlasId + " must not ship painterly one-pixel noise: " + metrics);
        }
    }

    @Test
    void realBakesRetainTheConceptsRareFocalColorsInsteadOfCollapsingToNeutralBands() throws Exception {
        final BufferedImage demon = ConceptTextureBatchTool.bakeForTest("demon");
        final ColorShares demonColors = colorShares(demon);
        assertTrue(demonColors.warmRed() >= 0.05,
            "Demon must retain the concept's red body/armor material: " + demonColors);
        assertTrue(demonColors.fireGold() >= 0.003,
            "Demon must retain the concept's yellow-orange eyes and horn-tip fire: " + demonColors);

        final BufferedImage banshee = ConceptTextureBatchTool.bakeForTest("banshee");
        final ColorShares bansheeColors = colorShares(banshee);
        assertTrue(bansheeColors.icyCyan() >= 0.20,
            "Banshee must retain the concept's icy cyan dress material: " + bansheeColors);
        assertTrue(bansheeColors.violet() >= 0.001,
            "Banshee must retain the concept's violet eyes instead of neutralizing them: " + bansheeColors);
    }

    @Test
    void bansheeFacePaintsTwoDeliberateVioletEyesInsteadOfOneRandomBand() throws Exception {
        final BufferedImage atlas = ConceptTextureBatchTool.bakeForTest("banshee");
        final ModelPart root = BansheeModel.createBodyLayer().bakeRoot();
        final Rectangle face = uvBounds(root, "/face_head", 0, AxisFace.FRONT, atlas);
        final java.util.List<Integer> eyes = predicateRegions(atlas, face, ConceptTextureBakerTest::isViolet);

        assertEquals(2, eyes.size(), "the modeled feminine face needs two separated eye clusters: " + eyes);
        assertTrue(eyes.stream().allMatch(size -> size >= 2 && size <= 8),
            "each eye must be a small deliberate pixel cluster: " + eyes);
    }

    @Test
    void semanticEyeAndRunePartsRetainTheirConceptAccentMaterials() throws Exception {
        final BufferedImage watcher = ConceptTextureBatchTool.bakeForTest("eldritch_watcher");
        final double watcherAmber = colorShare(watcher, argb -> {
            final Color color = new Color(argb, true);
            return color.getRed() >= 125 && color.getGreen() >= 65
                && color.getRed() >= color.getBlue() * 1.45
                && color.getGreen() >= color.getBlue() * 1.15;
        });
        assertTrue(watcherAmber >= 0.01,
            "the Watcher's authored amber eye field must survive projection: " + watcherAmber);

        final BufferedImage sigil = ConceptTextureBatchTool.bakeForTest("umbral_sigil");
        final double sigilViolet = colorShare(sigil, ConceptTextureBakerTest::isViolet);
        assertTrue(sigilViolet >= 0.01,
            "the Umbral Sigil's violet crystal and runes must survive projection: " + sigilViolet);
    }

    @Test
    void horizontalFacesBlendCardinalMaterialFieldsInsteadOfUsingOneConceptScanline() {
        final ModelPart root = cubeRoot();
        final ConceptTextureBaker.ConceptViews views = new ConceptTextureBaker.ConceptViews(
            boardView(48, 64, new Color(206, 42, 34)),
            boardView(48, 64, new Color(38, 181, 68)),
            boardView(48, 64, new Color(45, 71, 204)),
            boardView(48, 64, new Color(222, 173, 37))
        );

        final BufferedImage atlas = ConceptTextureBaker.bake(root, 96, 96, views);
        final Color top = new Color(faceCenter(atlas, root, AxisFace.TOP), true);

        assertTrue(top.getRed() > 55 && top.getGreen() > 55,
            "top material must retain both front and side concept fields: " + top);
        assertTrue(top.getRed() < top.getGreen() * 2.0 && top.getGreen() < top.getRed() * 2.0,
            "top material must be a balanced cardinal blend, not a collapsed scanline: " + top);
    }

    @Test
    void materiallyDifferentSharedUvSamplesChooseARealConceptColorInsteadOfMuddyAveraging() {
        final ModelPart root = overlappingUvCubeRoot();
        final BufferedImage split = splitSubjectView();
        final ConceptTextureBaker.ConceptViews views = new ConceptTextureBaker.ConceptViews(
            split, split, split, split
        );

        final BufferedImage atlas = ConceptTextureBaker.bake(root, 96, 96, views);
        final Color shared = new Color(faceCenter(atlas, root, AxisFace.FRONT), true);

        assertTrue(
            shared.getRed() > shared.getBlue() * 1.8 || shared.getBlue() > shared.getRed() * 1.8,
            "shared UV must choose an actual red/blue concept material rather than average purple: " + shared
        );
    }

    private static ModelPart cubeRoot() {
        final MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -24.0F, -4.0F, 16.0F, 24.0F, 8.0F),
            PartPose.offset(0.0F, 24.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, 96, 96).bakeRoot();
    }

    private static ModelPart overlappingUvCubeRoot() {
        final MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
            "left_body",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-8.0F, -20.0F, -4.0F, 16.0F, 20.0F, 8.0F),
            PartPose.offset(-10.0F, 24.0F, 0.0F)
        );
        mesh.getRoot().addOrReplaceChild(
            "right_body",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-8.0F, -20.0F, -4.0F, 16.0F, 20.0F, 8.0F),
            PartPose.offset(10.0F, 24.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, 96, 96).bakeRoot();
    }

    private static BufferedImage boardView(final int width, final int height, final Color subject) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            final int neutral = 181 + (int) Math.round(y * 12.0 / Math.max(height - 1, 1));
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, new Color(neutral, neutral - 3, neutral - 6).getRGB());
            }
        }
        for (int y = 6; y < height - 6; y++) {
            for (int x = 10; x < width - 10; x++) {
                final int checker = ((x / 3) + (y / 4)) & 1;
                image.setRGB(x, y, shade(subject, checker == 0 ? 0 : -14).getRGB());
            }
        }
        return image;
    }

    private static BufferedImage detailedBoardView() {
        final BufferedImage image = boardView(48, 64, new Color(106, 43, 55));
        final Color[] bands = {
            new Color(46, 25, 34), new Color(78, 31, 48), new Color(118, 43, 58),
            new Color(151, 58, 58), new Color(187, 82, 57), new Color(221, 121, 60),
            new Color(115, 76, 93), new Color(70, 71, 91)
        };
        for (int y = 6; y < 58; y++) {
            for (int x = 10; x < 38; x++) {
                final Color base = bands[Math.min(bands.length - 1, (y - 6) * bands.length / 52)];
                final int clusteredShade = ((x / 4) + (y / 5)) % 3 == 0 ? 13 : -7;
                image.setRGB(x, y, shade(base, clusteredShade).getRGB());
            }
        }
        for (int y = 10; y < 15; y++) {
            for (int x = 21; x < 27; x++) {
                image.setRGB(x, y, new Color(42, 184, 211).getRGB());
            }
        }
        return image;
    }

    private static BufferedImage splitSubjectView() {
        final BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            final int neutral = 184 + y * 8 / Math.max(1, image.getHeight() - 1);
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, new Color(neutral, neutral - 3, neutral - 5).getRGB());
            }
        }
        for (int y = 6; y < 58; y++) {
            for (int x = 6; x < 58; x++) {
                image.setRGB(x, y, x < 32
                    ? new Color(205, 36, 44).getRGB()
                    : new Color(39, 57, 211).getRGB());
            }
        }
        return image;
    }

    private static BufferedImage noisyPixelArtFixture() {
        final BufferedImage image = new BufferedImage(28, 28, BufferedImage.TYPE_INT_ARGB);
        for (int y = 2; y < 26; y++) {
            for (int x = 2; x < 26; x++) {
                final int band = Math.min(5, (y - 2) / 4);
                final int panel = Math.min(4, (x - 2) / 5);
                final int red = 45 + band * 23 + panel * 5;
                final int green = 29 + band * 13 + panel * 7;
                final int blue = 38 + band * 10 + panel * 11;
                final int noise = Math.floorMod(x * 73 + y * 151 + x * y * 19, 29) - 14;
                image.setRGB(x, y, new Color(
                    Math.max(0, Math.min(255, red + noise)),
                    Math.max(0, Math.min(255, green - noise / 2)),
                    Math.max(0, Math.min(255, blue + noise / 3))
                ).getRGB());
            }
        }
        return image;
    }

    private static BufferedImage clusteredAccentFixture() {
        final BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y < 19; y++) {
            for (int x = 1; x < 19; x++) {
                final int shade = 48 + ((x / 4 + y / 4) % 4) * 14;
                image.setRGB(x, y, new Color(shade + 24, shade, shade + 8).getRGB());
            }
        }
        image.setRGB(4, 4, new Color(34, 190, 224).getRGB());
        image.setRGB(5, 5, new Color(39, 185, 219).getRGB());
        image.setRGB(15, 15, new Color(232, 31, 211).getRGB());
        return image;
    }

    private static PixelArtMetrics metrics(final BufferedImage image) {
        final java.util.Map<Integer, Integer> histogram = new java.util.HashMap<>();
        int varied = 0;
        int compared = 0;
        final boolean[] visited = new boolean[image.getWidth() * image.getHeight()];
        int regions = 0;
        int singletonPixels = 0;
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
                final int index = y * image.getWidth() + x;
                if (visited[index]) {
                    continue;
                }
                regions++;
                final int size = floodRegion(image, x, y, rgb, visited);
                singletonPixels += size == 1 ? 1 : 0;
            }
        }
        final int total = histogram.values().stream().mapToInt(Integer::intValue).sum();
        double entropy = 0.0;
        for (final int count : histogram.values()) {
            final double probability = count / (double) total;
            entropy -= probability * Math.log(probability);
        }
        return new PixelArtMetrics(
            Math.exp(entropy),
            compared == 0 ? 0.0 : varied / (double) compared,
            total == 0 ? 0.0 : singletonPixels / (double) total
        );
    }

    private static ColorShares colorShares(final BufferedImage image) {
        int opaque = 0;
        int warmRed = 0;
        int fireGold = 0;
        int icyCyan = 0;
        int violet = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final Color color = new Color(image.getRGB(x, y), true);
                if (color.getAlpha() == 0) {
                    continue;
                }
                opaque++;
                final int red = color.getRed();
                final int green = color.getGreen();
                final int blue = color.getBlue();
                warmRed += red >= 70 && red >= green * 1.35 && red >= blue * 1.35 ? 1 : 0;
                fireGold += red >= 115 && green >= 55 && red >= green * 1.20
                    && green >= blue * 1.35 ? 1 : 0;
                icyCyan += blue >= 80 && green >= 65 && blue >= red * 1.15 ? 1 : 0;
                violet += red >= 18 && blue >= 65 && red >= green * 1.15
                    && blue >= green * 2.50 ? 1 : 0;
            }
        }
        return new ColorShares(
            warmRed / (double) opaque,
            fireGold / (double) opaque,
            icyCyan / (double) opaque,
            violet / (double) opaque
        );
    }

    private static Rectangle uvBounds(
        final ModelPart root,
        final String expectedPath,
        final int expectedCube,
        final AxisFace expected,
        final BufferedImage atlas
    ) {
        final Rectangle[] result = {null};
        root.visit(new PoseStack(), (pose, path, cubeIndex, cube) -> {
            if (result[0] != null || !path.equals(expectedPath) || cubeIndex != expectedCube) {
                return;
            }
            for (final ModelPart.Polygon polygon : cube.polygons) {
                if (AxisFace.of(new Vector3f(polygon.normal())) != expected) {
                    continue;
                }
                final double minimumU = java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.u() * atlas.getWidth()).min().orElseThrow();
                final double maximumU = java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.u() * atlas.getWidth()).max().orElseThrow();
                final double minimumV = java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.v() * atlas.getHeight()).min().orElseThrow();
                final double maximumV = java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.v() * atlas.getHeight()).max().orElseThrow();
                result[0] = new Rectangle(
                    (int) Math.floor(minimumU),
                    (int) Math.floor(minimumV),
                    Math.max(1, (int) Math.ceil(maximumU) - (int) Math.floor(minimumU)),
                    Math.max(1, (int) Math.ceil(maximumV) - (int) Math.floor(minimumV))
                );
                return;
            }
        });
        if (result[0] == null) {
            throw new AssertionError("missing " + expectedPath + " " + expected + " face");
        }
        return result[0];
    }

    private static java.util.List<Integer> predicateRegions(
        final BufferedImage image,
        final Rectangle bounds,
        final java.util.function.IntPredicate predicate
    ) {
        final boolean[] visited = new boolean[image.getWidth() * image.getHeight()];
        final java.util.List<Integer> regions = new java.util.ArrayList<>();
        for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
            for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
                final int index = y * image.getWidth() + x;
                if (visited[index] || !predicate.test(image.getRGB(x, y))) {
                    continue;
                }
                final java.util.ArrayDeque<Integer> pending = new java.util.ArrayDeque<>();
                pending.add(index);
                visited[index] = true;
                int size = 0;
                while (!pending.isEmpty()) {
                    final int current = pending.removeFirst();
                    size++;
                    final int currentX = current % image.getWidth();
                    final int currentY = current / image.getWidth();
                    final int[][] neighbors = {
                        {currentX - 1, currentY}, {currentX + 1, currentY},
                        {currentX, currentY - 1}, {currentX, currentY + 1}
                    };
                    for (final int[] neighbor : neighbors) {
                        if (!bounds.contains(neighbor[0], neighbor[1])) {
                            continue;
                        }
                        final int next = neighbor[1] * image.getWidth() + neighbor[0];
                        if (!visited[next] && predicate.test(image.getRGB(neighbor[0], neighbor[1]))) {
                            visited[next] = true;
                            pending.addLast(next);
                        }
                    }
                }
                regions.add(size);
            }
        }
        return regions;
    }

    private static boolean isViolet(final int argb) {
        if ((argb >>> 24) == 0) {
            return false;
        }
        final int red = (argb >>> 16) & 0xFF;
        final int green = (argb >>> 8) & 0xFF;
        final int blue = argb & 0xFF;
        return red >= 18 && blue >= 65 && red >= green * 1.15 && blue >= green * 2.50;
    }

    private static double colorShare(
        final BufferedImage image,
        final java.util.function.IntPredicate predicate
    ) {
        int opaque = 0;
        int matching = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int argb = image.getRGB(x, y);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                opaque++;
                matching += predicate.test(argb) ? 1 : 0;
            }
        }
        return matching / (double) opaque;
    }

    private static int floodRegion(
        final BufferedImage image,
        final int startX,
        final int startY,
        final int rgb,
        final boolean[] visited
    ) {
        final java.util.ArrayDeque<Integer> pending = new java.util.ArrayDeque<>();
        pending.add(startY * image.getWidth() + startX);
        visited[startY * image.getWidth() + startX] = true;
        int size = 0;
        while (!pending.isEmpty()) {
            final int index = pending.removeFirst();
            size++;
            final int x = index % image.getWidth();
            final int y = index / image.getWidth();
            final int[][] neighbors = {{x - 1, y}, {x + 1, y}, {x, y - 1}, {x, y + 1}};
            for (final int[] neighbor : neighbors) {
                if (neighbor[0] < 0 || neighbor[1] < 0
                    || neighbor[0] >= image.getWidth() || neighbor[1] >= image.getHeight()) {
                    continue;
                }
                final int next = neighbor[1] * image.getWidth() + neighbor[0];
                if (!visited[next] && (image.getRGB(neighbor[0], neighbor[1]) >>> 24) != 0
                    && (image.getRGB(neighbor[0], neighbor[1]) & 0xFFFFFF) == rgb) {
                    visited[next] = true;
                    pending.addLast(next);
                }
            }
        }
        return size;
    }

    private record PixelArtMetrics(double effectivePalette, double variedEdges, double singletonShare) {
    }

    private record ColorShares(double warmRed, double fireGold, double icyCyan, double violet) {
    }

    private static Color shade(final Color color, final int delta) {
        return new Color(
            Math.max(0, Math.min(255, color.getRed() + delta)),
            Math.max(0, Math.min(255, color.getGreen() + delta)),
            Math.max(0, Math.min(255, color.getBlue() + delta))
        );
    }

    private static int faceCenter(
        final BufferedImage atlas,
        final ModelPart root,
        final AxisFace expected
    ) {
        final int[] result = {0};
        final boolean[] found = {false};
        root.visit(new PoseStack(), (pose, path, cubeIndex, cube) -> {
            for (final ModelPart.Polygon polygon : cube.polygons) {
                final Vector3f normal = pose.transformNormal(polygon.normal(), new Vector3f());
                if (AxisFace.of(normal) != expected) {
                    continue;
                }
                final double u = java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.u() * atlas.getWidth()).average().orElseThrow();
                final double v = java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.v() * atlas.getHeight()).average().orElseThrow();
                result[0] = atlas.getRGB(
                    Math.max(0, Math.min(atlas.getWidth() - 1, (int) u)),
                    Math.max(0, Math.min(atlas.getHeight() - 1, (int) v))
                );
                found[0] = true;
            }
        });
        assertTrue(found[0], "missing " + expected + " polygon");
        return result[0];
    }

    private static void assertChannelDominates(final int argb, final int shift, final String message) {
        final Color color = new Color(argb, true);
        final int selected = (argb >>> shift) & 0xFF;
        final int otherOne = shift == 16 ? color.getGreen() : color.getRed();
        final int otherTwo = shift == 0 ? color.getGreen() : color.getBlue();
        assertTrue(selected > otherOne * 1.5 && selected > otherTwo * 1.5, message + ": " + color);
    }

    private static int[] pixels(final BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private enum AxisFace {
        FRONT,
        BACK,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM;

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
