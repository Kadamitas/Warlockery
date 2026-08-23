package com.kadamitas.warlockery.client.texture;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/** Deterministic textured software renderer used for close-up preflight before launching Minecraft. */
final class TexturedModelPreview {
    private static final Color BACKGROUND = new Color(192, 188, 182);

    private TexturedModelPreview() {
    }

    static BufferedImage render(
        final ModelPart root,
        final BufferedImage atlas,
        final View view,
        final int size
    ) {
        if (size < 32) {
            throw new IllegalArgumentException("preview size must be at least 32 pixels");
        }
        final List<ProjectedPolygon> polygons = new ArrayList<>();
        root.visit(new PoseStack(), (pose, path, cubeIndex, cube) -> {
            for (final ModelPart.Polygon polygon : cube.polygons) {
                final List<ProjectedVertex> vertices = Arrays.stream(polygon.vertices())
                    .map(vertex -> {
                        final Vector3f point = pose.pose().transformPosition(
                            vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f()
                        ).mul(16.0F);
                        final Projection projected = view.project(point);
                        return new ProjectedVertex(
                            projected.x(),
                            projected.y(),
                            projected.depth(),
                            vertex.u(),
                            vertex.v()
                        );
                    })
                    .toList();
                polygons.add(new ProjectedPolygon(vertices));
            }
        });
        final double minimumX = polygons.stream().flatMap(face -> face.vertices().stream())
            .mapToDouble(ProjectedVertex::x).min().orElseThrow();
        final double maximumX = polygons.stream().flatMap(face -> face.vertices().stream())
            .mapToDouble(ProjectedVertex::x).max().orElseThrow();
        final double minimumY = polygons.stream().flatMap(face -> face.vertices().stream())
            .mapToDouble(ProjectedVertex::y).min().orElseThrow();
        final double maximumY = polygons.stream().flatMap(face -> face.vertices().stream())
            .mapToDouble(ProjectedVertex::y).max().orElseThrow();
        final double padding = size * 0.115;
        final double scale = Math.min(
            (size - 2.0 * padding) / Math.max(maximumX - minimumX, 1.0E-9),
            (size - 2.0 * padding) / Math.max(maximumY - minimumY, 1.0E-9)
        );
        final double drawnWidth = (maximumX - minimumX) * scale;
        final double drawnHeight = (maximumY - minimumY) * scale;
        final double offsetX = (size - drawnWidth) / 2.0 - minimumX * scale;
        final double offsetY = (size - drawnHeight) / 2.0 - minimumY * scale;

        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        final int[] pixels = new int[size * size];
        Arrays.fill(pixels, BACKGROUND.getRGB());
        final double[] depths = new double[pixels.length];
        Arrays.fill(depths, Double.POSITIVE_INFINITY);
        for (final ProjectedPolygon polygon : polygons) {
            final ScreenVertex[] vertices = polygon.vertices().stream()
                .map(vertex -> new ScreenVertex(
                    offsetX + vertex.x() * scale,
                    offsetY + vertex.y() * scale,
                    vertex.depth(),
                    vertex.u(),
                    vertex.v()
                ))
                .toArray(ScreenVertex[]::new);
            if (vertices.length < 3) {
                continue;
            }
            rasterize(vertices[0], vertices[1], vertices[2], atlas, size, pixels, depths);
            if (vertices.length == 4) {
                rasterize(vertices[0], vertices[2], vertices[3], atlas, size, pixels, depths);
            }
        }
        image.setRGB(0, 0, size, size, pixels, 0, size);
        return image;
    }

    private static void rasterize(
        final ScreenVertex first,
        final ScreenVertex second,
        final ScreenVertex third,
        final BufferedImage atlas,
        final int size,
        final int[] pixels,
        final double[] depths
    ) {
        final double denominator = edge(first.x(), first.y(), second.x(), second.y(), third.x(), third.y());
        if (Math.abs(denominator) < 1.0E-9) {
            return;
        }
        final int minimumX = Math.max(0,
            (int) Math.floor(Math.min(first.x(), Math.min(second.x(), third.x()))));
        final int maximumX = Math.min(size - 1,
            (int) Math.ceil(Math.max(first.x(), Math.max(second.x(), third.x()))));
        final int minimumY = Math.max(0,
            (int) Math.floor(Math.min(first.y(), Math.min(second.y(), third.y()))));
        final int maximumY = Math.min(size - 1,
            (int) Math.ceil(Math.max(first.y(), Math.max(second.y(), third.y()))));
        for (int y = minimumY; y <= maximumY; y++) {
            for (int x = minimumX; x <= maximumX; x++) {
                final double pointX = x + 0.5;
                final double pointY = y + 0.5;
                final double firstWeight = edge(
                    second.x(), second.y(), third.x(), third.y(), pointX, pointY
                ) / denominator;
                final double secondWeight = edge(
                    third.x(), third.y(), first.x(), first.y(), pointX, pointY
                ) / denominator;
                final double thirdWeight = 1.0 - firstWeight - secondWeight;
                if (firstWeight < -1.0E-7 || secondWeight < -1.0E-7 || thirdWeight < -1.0E-7) {
                    continue;
                }
                final double depth = firstWeight * first.depth()
                    + secondWeight * second.depth()
                    + thirdWeight * third.depth();
                final int index = y * size + x;
                if (depth >= depths[index]) {
                    continue;
                }
                final double u = firstWeight * first.u() + secondWeight * second.u() + thirdWeight * third.u();
                final double v = firstWeight * first.v() + secondWeight * second.v() + thirdWeight * third.v();
                final int textureX = Math.max(0, Math.min(
                    atlas.getWidth() - 1,
                    (int) Math.floor(u * atlas.getWidth())
                ));
                final int textureY = Math.max(0, Math.min(
                    atlas.getHeight() - 1,
                    (int) Math.floor(v * atlas.getHeight())
                ));
                final int argb = atlas.getRGB(textureX, textureY);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                pixels[index] = argb;
                depths[index] = depth;
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

    enum View {
        FRONT {
            @Override
            Projection project(final Vector3f point) {
                return new Projection(point.x(), point.y(), point.z());
            }
        },
        BACK {
            @Override
            Projection project(final Vector3f point) {
                return new Projection(-point.x(), point.y(), -point.z());
            }
        },
        LEFT {
            @Override
            Projection project(final Vector3f point) {
                return new Projection(point.z(), point.y(), point.x());
            }
        },
        RIGHT {
            @Override
            Projection project(final Vector3f point) {
                return new Projection(-point.z(), point.y(), -point.x());
            }
        },
        THREE_QUARTER {
            @Override
            Projection project(final Vector3f point) {
                final double inverseRootTwo = 1.0 / Math.sqrt(2.0);
                return new Projection(
                    (point.x() - point.z()) * inverseRootTwo,
                    point.y(),
                    (point.x() + point.z()) * inverseRootTwo
                );
            }
        };

        abstract Projection project(Vector3f point);
    }

    private record Projection(double x, double y, double depth) {
    }

    private record ProjectedVertex(double x, double y, double depth, float u, float v) {
    }

    private record ProjectedPolygon(List<ProjectedVertex> vertices) {
    }

    private record ScreenVertex(double x, double y, double depth, double u, double v) {
    }
}
