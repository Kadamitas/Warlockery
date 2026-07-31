package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

final class CreatureModelVisualAuditTest {
    private static final Path TEXTURES = Path.of("src/main/resources/assets/warlockery/textures/entity");
    private static final Path CONCEPTS = Path.of("docs/art-source/creature-concepts");
    private static final Path REPORT = Path.of("build/reports/visual-audit/creature-models.png");
    private static final Path COMPARISON_REPORT = Path.of(
        "build/reports/visual-audit/creature-concept-comparison.png"
    );
    private static final int CELL_WIDTH = 180;
    private static final int CELL_HEIGHT = 230;
    private static final int COLUMNS = 6;

    @Test
    void rendersEveryCreatureModelWithItsProductionTexture() throws IOException {
        final List<CreatureModelProfile.Variant> variants = Arrays.stream(CreatureModelProfile.Variant.values())
            .sorted(Comparator.comparing(CreatureModelProfile.Variant::id))
            .toList();
        final int rows = Math.ceilDiv(variants.size(), COLUMNS);
        final BufferedImage sheet = new BufferedImage(
            COLUMNS * CELL_WIDTH,
            rows * CELL_HEIGHT,
            BufferedImage.TYPE_INT_ARGB
        );
        final Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(0x202124));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        final List<String> renderHashes = new ArrayList<>();
        for (int index = 0; index < variants.size(); index++) {
            final CreatureModelProfile.Variant variant = variants.get(index);
            final BufferedImage texture = ImageIO.read(TEXTURES.resolve(variant.id() + ".png").toFile());
            assertEquals(64, texture.getWidth(), variant.id() + " texture width");
            assertEquals(64, texture.getHeight(), variant.id() + " texture height");
            final BufferedImage render = render(variant, texture);
            final int x = index % COLUMNS * CELL_WIDTH;
            final int y = index / COLUMNS * CELL_HEIGHT;
            graphics.drawImage(render, x, y, null);
            graphics.setColor(new Color(0xF2E8D5));
            graphics.drawString(variant.id(), x + 8, y + CELL_HEIGHT - 9);
            renderHashes.add(pixelHash(render));
            assertTrue(opaquePixels(render) > 1_000, variant.id() + " render is visually empty");
        }
        graphics.dispose();

        Files.createDirectories(REPORT.getParent());
        ImageIO.write(sheet, "png", REPORT.toFile());
        writeConceptComparison();
        assertEquals(variants.size(), renderHashes.stream().distinct().count(),
            "every creature needs a visually distinct textured model render");
    }

    private static void writeConceptComparison() throws IOException {
        final JsonObject manifest = JsonParser.parseString(
            Files.readString(CONCEPTS.resolve("concept-manifest.json"))
        ).getAsJsonObject().getAsJsonObject("sheets");
        final Map<String, Integer> columns = Map.of(
            "occult-humanoids.png", 4,
            "spectral-entities.png", 5,
            "lycans-and-mounts.png", 5,
            "familiars-and-vermin.png", 5,
            "infernal-and-bosses.png", 3,
            "verdant-creatures.png", 5,
            "goblin-clans.png", 4,
            "illusions-and-anomalies.png", 3
        );
        final List<Concept> concepts = manifest.entrySet().stream().flatMap(entry -> {
            final String sheet = entry.getKey();
            final List<String> creatures = entry.getValue().getAsJsonObject().getAsJsonArray("creatures")
                .asList().stream().map(value -> value.getAsString()).toList();
            final int sheetColumns = columns.get(sheet);
            final int rows = Math.ceilDiv(creatures.size(), sheetColumns);
            return java.util.stream.IntStream.range(0, creatures.size())
                .mapToObj(index -> new Concept(sheet, creatures.get(index), index % sheetColumns,
                    index / sheetColumns, sheetColumns, rows));
        }).toList();
        final List<String> expected = Arrays.stream(CreatureModelProfile.Variant.values())
            .map(CreatureModelProfile.Variant::id)
            .filter(id -> !id.equals("imp") && !id.equals("storm_simian"))
            .sorted()
            .toList();
        assertEquals(expected, concepts.stream().map(Concept::creature).sorted().toList(),
            "concept manifest must cover every creature except the separately saved imp and storm simian reference");

        final int columnsPerReport = 4;
        final int width = 300;
        final int height = 330;
        final BufferedImage comparison = new BufferedImage(
            columnsPerReport * width,
            Math.ceilDiv(concepts.size(), columnsPerReport) * height,
            BufferedImage.TYPE_INT_ARGB
        );
        final Graphics2D graphics = comparison.createGraphics();
        graphics.setColor(new Color(0x202124));
        graphics.fillRect(0, 0, comparison.getWidth(), comparison.getHeight());
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        final Map<String, BufferedImage> sheets = concepts.stream().map(Concept::sheet).distinct().collect(
            Collectors.toMap(name -> name, name -> readImage(CONCEPTS.resolve(name)))
        );
        for (int index = 0; index < concepts.size(); index++) {
            final Concept concept = concepts.get(index);
            final BufferedImage source = sheets.get(concept.sheet());
            final int sourceWidth = source.getWidth() / concept.columns();
            final int sourceHeight = source.getHeight() / concept.rows();
            final int x = index % columnsPerReport * width;
            final int y = index / columnsPerReport * height;
            graphics.drawImage(
                source,
                x + 8,
                y + 8,
                x + width - 8,
                y + 176,
                concept.column() * sourceWidth,
                concept.row() * sourceHeight,
                Math.min(source.getWidth(), (concept.column() + 1) * sourceWidth),
                Math.min(source.getHeight(), (concept.row() + 1) * sourceHeight),
                null
            );
            final CreatureModelProfile.Variant variant = CreatureModelProfile.Variant.fromId(concept.creature());
            final BufferedImage texture = readImage(TEXTURES.resolve(variant.id() + ".png"));
            final BufferedImage rendered = render(variant, texture);
            graphics.drawImage(rendered, x + 86, y + 176, x + 214, y + 316, 0, 0,
                rendered.getWidth(), rendered.getHeight() - 24, null);
            graphics.setColor(new Color(0xF2E8D5));
            graphics.drawString(concept.creature(), x + 8, y + height - 7);
        }
        graphics.dispose();
        ImageIO.write(comparison, "png", COMPARISON_REPORT.toFile());
    }

    private static BufferedImage readImage(final Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (final IOException exception) {
            throw new IllegalStateException("Unable to read visual audit input " + path, exception);
        }
    }

    private static BufferedImage render(
        final CreatureModelProfile.Variant variant,
        final BufferedImage texture
    ) {
        final CreatureVisualProfile visual = new CreatureVisualProfile(
            0.8F,
            1.8F,
            CreatureVisualProfile.Archetype.HUMANOID
        );
        final ModelPart root = ArcaneCreatureModel.createLayer(
            CreatureModelProfile.forEntity(variant.id(), visual)
        ).bakeRoot();
        final List<Face> faces = collectFaces(root);
        final Bounds bounds = Bounds.of(faces);
        final double scale = Math.min(
            (CELL_WIDTH - 20.0) / Math.max(0.01, bounds.maxX() - bounds.minX()),
            (CELL_HEIGHT - 42.0) / Math.max(0.01, bounds.maxY() - bounds.minY())
        );
        final double offsetX = CELL_WIDTH / 2.0 - (bounds.minX() + bounds.maxX()) * scale / 2.0;
        final double offsetY = 8.0 - bounds.minY() * scale;
        final BufferedImage image = new BufferedImage(CELL_WIDTH, CELL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        faces.stream().sorted(Comparator.comparingDouble(Face::depth)).forEach(face ->
            paintFace(graphics, texture, face, scale, offsetX, offsetY)
        );
        graphics.dispose();
        return image;
    }

    private static List<Face> collectFaces(final ModelPart root) {
        final List<Face> faces = new ArrayList<>();
        final PoseStack poses = new PoseStack();
        root.visit(poses, (pose, path, cubeIndex, cube) -> {
            for (final ModelPart.Polygon polygon : cube.polygons) {
                final List<Vertex> vertices = Arrays.stream(polygon.vertices())
                    .map(vertex -> {
                        final Vector3f world = pose.pose().transformPosition(
                            vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f()
                        );
                        return project(world, vertex.u(), vertex.v());
                    })
                    .toList();
                final Vector3f normal = pose.transformNormal(polygon.normal(), new Vector3f());
                faces.add(new Face(vertices, vertices.stream().mapToDouble(Vertex::depth).average().orElse(0.0),
                    brightness(normal)));
            }
        });
        return List.copyOf(faces);
    }

    private static Vertex project(final Vector3f point, final float u, final float v) {
        final double yaw = Math.toRadians(35.0);
        final double pitch = Math.toRadians(18.0);
        final double horizontal = point.x() * Math.cos(yaw) - point.z() * Math.sin(yaw);
        final double depth = point.x() * Math.sin(yaw) + point.z() * Math.cos(yaw);
        final double vertical = point.y() * Math.cos(pitch) - depth * Math.sin(pitch);
        final double cameraDepth = point.y() * Math.sin(pitch) + depth * Math.cos(pitch);
        return new Vertex(horizontal, vertical, cameraDepth, u, v);
    }

    private static float brightness(final Vector3f normal) {
        final Vector3f light = new Vector3f(-0.4F, -0.8F, -0.45F).normalize();
        return Math.max(0.55F, Math.min(1.0F, 0.72F + normal.normalize().dot(light) * 0.28F));
    }

    private static void paintFace(
        final Graphics2D graphics,
        final BufferedImage texture,
        final Face face,
        final double scale,
        final double offsetX,
        final double offsetY
    ) {
        final List<Vertex> vertices = face.vertices();
        if (vertices.size() != 4) {
            return;
        }
        final int subdivisions = 8;
        for (int row = 0; row < subdivisions; row++) {
            for (int column = 0; column < subdivisions; column++) {
                final double left = column / (double) subdivisions;
                final double right = (column + 1) / (double) subdivisions;
                final double top = row / (double) subdivisions;
                final double bottom = (row + 1) / (double) subdivisions;
                final Vertex a = interpolate(vertices, left, top);
                final Vertex b = interpolate(vertices, right, top);
                final Vertex c = interpolate(vertices, right, bottom);
                final Vertex d = interpolate(vertices, left, bottom);
                final Vertex sample = interpolate(vertices, (left + right) / 2.0, (top + bottom) / 2.0);
                final int sourceX = Math.clamp((int) (sample.u() * texture.getWidth()), 0, texture.getWidth() - 1);
                final int sourceY = Math.clamp((int) (sample.v() * texture.getHeight()), 0, texture.getHeight() - 1);
                final Color source = new Color(texture.getRGB(sourceX, sourceY), true);
                if (source.getAlpha() == 0) {
                    continue;
                }
                graphics.setColor(shade(source, face.brightness()));
                graphics.fillPolygon(polygon(List.of(a, b, c, d), scale, offsetX, offsetY));
            }
        }
    }

    private static Vertex interpolate(final List<Vertex> vertices, final double x, final double y) {
        final Vertex top = vertices.get(0).mix(vertices.get(1), x);
        final Vertex bottom = vertices.get(3).mix(vertices.get(2), x);
        return top.mix(bottom, y);
    }

    private static Polygon polygon(
        final List<Vertex> vertices,
        final double scale,
        final double offsetX,
        final double offsetY
    ) {
        final Polygon polygon = new Polygon();
        vertices.forEach(vertex -> polygon.addPoint(
            (int) Math.round(vertex.x() * scale + offsetX),
            (int) Math.round(vertex.y() * scale + offsetY)
        ));
        return polygon;
    }

    private static Color shade(final Color color, final float brightness) {
        return new Color(
            Math.clamp(Math.round(color.getRed() * brightness), 0, 255),
            Math.clamp(Math.round(color.getGreen() * brightness), 0, 255),
            Math.clamp(Math.round(color.getBlue() * brightness), 0, 255),
            color.getAlpha()
        );
    }

    private static long opaquePixels(final BufferedImage image) {
        return Arrays.stream(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()))
            .filter(pixel -> pixel >>> 24 != 0)
            .count();
    }

    private static String pixelHash(final BufferedImage image) {
        return Arrays.stream(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()))
            .filter(pixel -> pixel >>> 24 != 0)
            .mapToObj(Integer::toHexString)
            .collect(Collectors.joining());
    }

    private record Face(List<Vertex> vertices, double depth, float brightness) {
    }

    private record Concept(
        String sheet,
        String creature,
        int column,
        int row,
        int columns,
        int rows
    ) {
    }

    private record Vertex(double x, double y, double depth, float u, float v) {
        private Vertex mix(final Vertex other, final double amount) {
            final double inverse = 1.0 - amount;
            return new Vertex(
                x * inverse + other.x * amount,
                y * inverse + other.y * amount,
                depth * inverse + other.depth * amount,
                (float) (u * inverse + other.u * amount),
                (float) (v * inverse + other.v * amount)
            );
        }
    }

    private record Bounds(double minX, double maxX, double minY, double maxY) {
        private static Bounds of(final List<Face> faces) {
            final List<Vertex> vertices = faces.stream().flatMap(face -> face.vertices().stream()).toList();
            return new Bounds(
                vertices.stream().mapToDouble(Vertex::x).min().orElse(0.0),
                vertices.stream().mapToDouble(Vertex::x).max().orElse(1.0),
                vertices.stream().mapToDouble(Vertex::y).min().orElse(0.0),
                vertices.stream().mapToDouble(Vertex::y).max().orElse(1.0)
            );
        }
    }
}
