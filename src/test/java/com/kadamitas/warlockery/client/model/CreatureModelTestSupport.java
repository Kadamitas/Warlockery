package com.kadamitas.warlockery.client.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class CreatureModelTestSupport {
    private CreatureModelTestSupport() {
    }

    public static ModelPart requiredChild(final ModelPart parent, final String name) {
        return assertDoesNotThrow(() -> parent.getChild(name), "missing semantic child " + name);
    }

    public static long solidPartCount(final ModelPart root) {
        return root.getAllParts().stream().filter(part -> !part.isEmpty()).count();
    }

    public static Bounds bounds(final ModelPart root) {
        final float[] values = {
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
        };
        root.visit(new PoseStack(), (pose, path, cubeIndex, cube) -> {
            for (final ModelPart.Polygon polygon : cube.polygons) {
                for (final ModelPart.Vertex vertex : polygon.vertices()) {
                    final Vector3f transformed = pose.pose().transformPosition(
                        vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f()
                    ).mul(16.0F);
                    values[0] = Math.min(values[0], transformed.x());
                    values[1] = Math.min(values[1], transformed.y());
                    values[2] = Math.min(values[2], transformed.z());
                    values[3] = Math.max(values[3], transformed.x());
                    values[4] = Math.max(values[4], transformed.y());
                    values[5] = Math.max(values[5], transformed.z());
                }
            }
        });
        return new Bounds(values[0], values[1], values[2], values[3], values[4], values[5]);
    }

    public static List<CubeVisit> cubes(final ModelPart root) {
        final List<CubeVisit> visits = new ArrayList<>();
        root.visit(new PoseStack(), (pose, path, cubeIndex, cube) ->
            visits.add(new CubeVisit(path, cubeIndex, List.of(cube.polygons)))
        );
        return List.copyOf(visits);
    }

    public static void assertUvsWithin(
        final ModelPart root,
        final int textureWidth,
        final int textureHeight
    ) {
        for (final CubeVisit visit : cubes(root)) {
            for (final ModelPart.Polygon polygon : visit.polygons()) {
                for (final ModelPart.Vertex vertex : polygon.vertices()) {
                    assertTrue(vertex.u() >= 0.0F && vertex.u() <= 1.0F,
                        visit.path() + " U outside atlas: " + vertex.u());
                    assertTrue(vertex.v() >= 0.0F && vertex.v() <= 1.0F,
                        visit.path() + " V outside atlas: " + vertex.v());
                    assertTrue(vertex.u() * textureWidth <= textureWidth,
                        visit.path() + " U exceeds width");
                    assertTrue(vertex.v() * textureHeight <= textureHeight,
                        visit.path() + " V exceeds height");
                }
            }
        }
    }

    public static void assertOpaqueUvs(
        final ModelPart root,
        final BufferedImage texture,
        final Predicate<CubeVisit> selector
    ) {
        for (final CubeVisit visit : cubes(root).stream().filter(selector).toList()) {
            for (final ModelPart.Polygon polygon : visit.polygons()) {
                final int minU = (int) Math.floor(java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.u() * texture.getWidth()).min().orElseThrow());
                final int maxU = (int) Math.ceil(java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.u() * texture.getWidth()).max().orElseThrow());
                final int minV = (int) Math.floor(java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.v() * texture.getHeight()).min().orElseThrow());
                final int maxV = (int) Math.ceil(java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.v() * texture.getHeight()).max().orElseThrow());
                for (int v = minV; v < maxV; v++) {
                    for (int u = minU; u < maxU; u++) {
                        assertEquals(255, texture.getRGB(u, v) >>> 24,
                            visit.path() + " transparent UV at " + u + "," + v);
                    }
                }
            }
        }
    }

    public static String geometrySnapshot(final ModelPart root) {
        final List<GeometryVisit> visits = new ArrayList<>();
        root.visit(new PoseStack(), (pose, path, cubeIndex, cube) -> {
            final MessageDigest cubeDigest = sha256();
            for (final ModelPart.Polygon polygon : cube.polygons) {
                final Vector3f normal = pose.transformNormal(polygon.normal(), new Vector3f());
                updateFloat(cubeDigest, normal.x());
                updateFloat(cubeDigest, normal.y());
                updateFloat(cubeDigest, normal.z());
                for (final ModelPart.Vertex vertex : polygon.vertices()) {
                    final Vector3f transformed = pose.pose().transformPosition(
                        vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f()
                    );
                    updateFloat(cubeDigest, transformed.x());
                    updateFloat(cubeDigest, transformed.y());
                    updateFloat(cubeDigest, transformed.z());
                    updateFloat(cubeDigest, vertex.u());
                    updateFloat(cubeDigest, vertex.v());
                }
            }
            visits.add(new GeometryVisit(path, cubeIndex, cubeDigest.digest()));
        });
        final MessageDigest digest = sha256();
        visits.stream()
            .sorted(Comparator.comparing(GeometryVisit::path).thenComparingInt(GeometryVisit::index))
            .forEach(visit -> {
                digest.update(visit.path().getBytes(StandardCharsets.UTF_8));
                updateInt(digest, visit.index());
                digest.update(visit.digest());
            });
        return HexFormat.of().formatHex(digest.digest());
    }

    public static BufferedImage softwareSnapshot(
        final ModelPart root,
        final Projection projection,
        final int size,
        final int padding
    ) {
        final List<Face> faces = new ArrayList<>();
        root.visit(new PoseStack(), (pose, path, cubeIndex, cube) -> {
            for (final ModelPart.Polygon polygon : cube.polygons) {
                final List<Point> points = java.util.Arrays.stream(polygon.vertices())
                    .map(vertex -> projected(pose.pose(), vertex, projection))
                    .toList();
                faces.add(new Face(points, points.stream().mapToDouble(Point::depth).average().orElse(0.0)));
            }
        });
        final double minX = faces.stream().flatMap(face -> face.points().stream())
            .mapToDouble(Point::x).min().orElse(0.0);
        final double maxX = faces.stream().flatMap(face -> face.points().stream())
            .mapToDouble(Point::x).max().orElse(1.0);
        final double minY = faces.stream().flatMap(face -> face.points().stream())
            .mapToDouble(Point::y).min().orElse(0.0);
        final double maxY = faces.stream().flatMap(face -> face.points().stream())
            .mapToDouble(Point::y).max().orElse(1.0);
        final double scale = Math.min(
            (size - padding * 2.0) / Math.max(maxX - minX, 1.0E-6),
            (size - padding * 2.0) / Math.max(maxY - minY, 1.0E-6)
        );
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setColor(Color.WHITE);
        faces.stream().sorted(Comparator.comparingDouble(Face::depth)).forEach(face -> {
            final int[] x = face.points().stream()
                .mapToInt(point -> padding + (int) Math.round((point.x() - minX) * scale)).toArray();
            final int[] y = face.points().stream()
                .mapToInt(point -> padding + (int) Math.round((point.y() - minY) * scale)).toArray();
            graphics.fillPolygon(x, y, x.length);
        });
        graphics.dispose();
        return image;
    }

    public static String imageSnapshot(final BufferedImage image) {
        final MessageDigest digest = sha256();
        updateInt(digest, image.getWidth());
        updateInt(digest, image.getHeight());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                updateInt(digest, image.getRGB(x, y));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String matrixSnapshot(final PoseStack poseStack) {
        final MessageDigest digest = sha256();
        final Matrix4fc matrix = poseStack.last().pose();
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                updateFloat(digest, matrix.get(column, row));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Point projected(
        final Matrix4fc matrix,
        final ModelPart.Vertex vertex,
        final Projection projection
    ) {
        final Vector3f point = matrix.transformPosition(
            vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f()
        );
        return switch (projection) {
            case FRONT -> new Point(point.x(), point.y(), point.z());
            case SIDE -> new Point(point.z(), point.y(), point.x());
            case TOP -> new Point(point.x(), point.z(), point.y());
        };
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void updateFloat(final MessageDigest digest, final float value) {
        updateInt(digest, Float.floatToIntBits(value));
    }

    private static void updateInt(final MessageDigest digest, final int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    public enum Projection {
        FRONT,
        SIDE,
        TOP
    }

    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    }

    public record CubeVisit(String path, int index, List<ModelPart.Polygon> polygons) {
    }

    private record Point(double x, double y, double depth) {
    }

    private record Face(List<Point> points, double depth) {
    }

    private record GeometryVisit(String path, int index, byte[] digest) {
    }
}
