package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.block.ConnectedGlyphGeometry.Bounds;
import com.kadamitas.warlockery.block.ConnectedGlyphGeometry.Side;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class ConnectedGlyphGeometryTest {
    @Test
    void thinChalkLinesKeepAPronouncedFourPixelNode() {
        assertEquals(4.0, ConnectedGlyphGeometry.CENTER.maxX() - ConnectedGlyphGeometry.CENTER.minX());
        assertEquals(0.25, ConnectedGlyphGeometry.CENTER.maxY());
        for (final Side side : Side.values()) {
            for (final Bounds part : ConnectedGlyphGeometry.parts(side)) {
                assertEquals(0.125, part.maxY());
                assertTrue(Math.min(part.maxX() - part.minX(), part.maxZ() - part.minZ()) <= 2.0);
            }
        }
    }

    @Test
    void sampledChalkPigmentHasOnlySubtleOpaqueTonalVariation() throws IOException {
        final Path assets = Path.of("src/main/resources/assets/warlockery");
        for (final String id : ConnectedGlyphGeometry.IDS) {
            final String texture = json(assets.resolve("models/block/" + id + ".json"))
                .getAsJsonObject("textures").get("glyph").getAsString();
            final var image = ImageIO.read(assets.resolve("textures/" + texture.substring("warlockery:".length()) + ".png").toFile());
            final Set<Integer> colors = new HashSet<>();
            final int[] min = {255, 255, 255};
            final int[] max = {0, 0, 0};
            for (int x = 5; x < 11; x++) for (int z = 6; z < 10; z++) {
                final int pixel = image.getRGB(x, z);
                assertEquals(255, pixel >>> 24, "sampled chalk pigment must not open holes");
                colors.add(pixel);
                for (int channel = 0; channel < 3; channel++) {
                    final int value = pixel >> (channel * 8) & 255;
                    min[channel] = Math.min(min[channel], value);
                    max[channel] = Math.max(max[channel], value);
                }
            }
            assertTrue(colors.size() >= 2, "chalk retains faint lighter and darker pigment");
            for (int channel = 0; channel < 3; channel++) {
                assertTrue(max[channel] - min[channel] <= 18, "pigment contrast stays subtle for " + id);
            }
        }
    }

    @Test
    void everyChalkGlyphIdUsesTheConnectedImplementation() {
        assertEquals(Set.of(
                "circle", "circleglyphgolden", "circleglyphritual", "circleglyphinfernal", "circleglyph_veil"
            ),
            ConnectedGlyphGeometry.IDS);
        assertFalse(ConnectedGlyphGeometry.IDS.contains("pentacle"));
    }

    @Test
    void centerOnlyGeometryStaysInsideTheBlockCenter() {
        final ConnectedGlyphGeometry.Bounds center = ConnectedGlyphGeometry.bounds(Set.of());
        assertEquals(6.0, center.minX());
        assertEquals(6.0, center.minZ());
        assertEquals(10.0, center.maxX());
        assertEquals(10.0, center.maxZ());
        assertEquals(0.25, center.maxY());
    }

    @Test
    void geometryOnlyReachesEnabledEdges() {
        final ConnectedGlyphGeometry.Bounds northEast = ConnectedGlyphGeometry.bounds(Set.of(
            ConnectedGlyphGeometry.Side.NORTH,
            ConnectedGlyphGeometry.Side.EAST
        ));
        assertEquals(0.0, northEast.minZ());
        assertEquals(16.0, northEast.maxX());
        assertEquals(6.0, northEast.minX());
        assertEquals(10.0, northEast.maxZ());

        final ConnectedGlyphGeometry.Bounds cross = ConnectedGlyphGeometry.bounds(EnumSet.allOf(ConnectedGlyphGeometry.Side.class));
        assertEquals(0.0, cross.minX());
        assertEquals(0.0, cross.minZ());
        assertEquals(16.0, cross.maxX());
        assertEquals(16.0, cross.maxZ());
        assertTrue(cross.maxY() <= 1.0);
    }

    @Test
    void diagonalRibbonsReachTheirCornerWithNarrowConnectedNonoverlappingSteps() {
        for (final Side side : Side.values()) {
            if (!side.diagonal()) continue;
            final List<Bounds> parts = ConnectedGlyphGeometry.parts(side);
            assertEquals(8, parts.size());
            double area = 0.0;
            for (int index = 0; index < parts.size(); index++) {
                final Bounds part = parts.get(index);
                assertTrue(part.minX() >= 0 && part.minZ() >= 0 && part.maxX() <= 16 && part.maxZ() <= 16);
                area += (part.maxX() - part.minX()) * (part.maxZ() - part.minZ());
                if (index > 0) {
                    final Bounds previous = parts.get(index - 1);
                    assertTrue(Math.min(previous.maxZ(), part.maxZ()) > Math.max(previous.minZ(), part.minZ()));
                    assertTrue(previous.maxX() == part.minX() || part.maxX() == previous.minX());
                }
            }
            assertEquals(22.0, area, "ribbons must remain much narrower than a filled quadrant");
            final Bounds bounds = ConnectedGlyphGeometry.bounds(Set.of(side));
            assertEquals(side.dx() < 0 ? 0.0 : 6.0, bounds.minX());
            assertEquals(side.dx() > 0 ? 16.0 : 10.0, bounds.maxX());
            assertEquals(side.dz() < 0 ? 0.0 : 6.0, bounds.minZ());
            assertEquals(side.dz() > 0 ? 16.0 : 10.0, bounds.maxZ());
        }
    }

    @Test
    void allEightDirectionsRotateAndMirrorByTheirActualOffsets() {
        for (final Side side : Side.values()) {
            assertEquals(-side.dz(), side.rotateQuarterTurns(1).dx());
            assertEquals(side.dx(), side.rotateQuarterTurns(1).dz());
            assertEquals(-side.dx(), side.mirrorX().dx());
            assertEquals(side.dz(), side.mirrorX().dz());
            assertEquals(side.dx(), side.mirrorZ().dx());
            assertEquals(-side.dz(), side.mirrorZ().dz());
            assertEquals(side, side.rotateQuarterTurns(4));
            assertEquals(side, side.mirrorX().mirrorX());
            assertEquals(side, side.mirrorZ().mirrorZ());
        }
    }

    @Test
    void everyGlyphMapsEveryDirectionToItsOwnTextureAndMatchingGeometry() throws IOException {
        final Path assets = Path.of("src/main/resources/assets/warlockery");
        for (final String id : ConnectedGlyphGeometry.IDS) {
            final JsonObject blockstate = json(assets.resolve("blockstates/" + id + ".json"));
            final var multipart = blockstate.getAsJsonArray("multipart");
            assertEquals(9, multipart.size(), id);
            final Set<String> mapped = new HashSet<>();
            for (int index = 1; index < multipart.size(); index++) {
                final JsonObject part = multipart.get(index).getAsJsonObject();
                assertEquals(1, part.getAsJsonObject("when").size());
                final var condition = part.getAsJsonObject("when").entrySet().iterator().next();
                assertTrue(mapped.add(condition.getKey()), "each direction is mapped exactly once");
                assertEquals("true", condition.getValue().getAsString());
                assertEquals("warlockery:block/" + id + "_" + condition.getKey(),
                    part.getAsJsonObject("apply").get("model").getAsString());
            }
            assertEquals(Set.of("north", "east", "south", "west", "north_east", "south_east", "south_west", "north_west"), mapped);
            final String texture = json(assets.resolve("models/block/" + id + ".json"))
                .getAsJsonObject("textures").get("glyph").getAsString();
            final var image = ImageIO.read(assets.resolve("textures/" + texture.substring("warlockery:".length()) + ".png").toFile());
            for (int x = 7; x < 9; x++) for (int z = 7; z < 9; z++) {
                assertEquals(255, image.getRGB(x, z) >>> 24, id + " diagonal UV must sample opaque existing pigment");
            }
            for (final Side side : ConnectedGlyphGeometry.DIAGONALS.keySet()) {
                final JsonObject child = json(assets.resolve("models/block/" + id + "_" + side.id() + ".json"));
                assertEquals("warlockery:block/chalk_glyph_" + side.id(), child.get("parent").getAsString());
                assertEquals(texture, child.getAsJsonObject("textures").get("glyph").getAsString());
            }
        }
        for (final Side side : ConnectedGlyphGeometry.DIAGONALS.keySet()) {
            final var elements = json(assets.resolve("models/block/chalk_glyph_" + side.id() + ".json")).getAsJsonArray("elements");
            assertEquals(1, elements.size(), "diagonals render one straight strip rather than stair-step cubes");
            for (int index = 0; index < elements.size(); index++) {
                final JsonObject element = elements.get(index).getAsJsonObject();
                final var from = element.getAsJsonArray("from");
                final var to = element.getAsJsonArray("to");
                assertEquals(2.0, to.get(2).getAsDouble() - from.get(2).getAsDouble());
                assertEquals("y", element.getAsJsonObject("rotation").get("axis").getAsString());
                final double angle = element.getAsJsonObject("rotation").get("angle").getAsDouble();
                assertEquals(45.0, Math.abs(angle));
                final double endX = (side.dx() > 0 ? to : from).get(0).getAsDouble() - 8.0;
                assertEquals(8.0 * side.dx(), endX * Math.cos(Math.toRadians(angle)), 0.00001);
                assertEquals(8.0 * side.dz(), -endX * Math.sin(Math.toRadians(angle)), 0.00001);
                assertEquals(ConnectedGlyphGeometry.surface(side), to.get(1).getAsDouble());
                assertTrue(to.get(1).getAsDouble() > 0.125, "diagonal top must not fight existing center or cardinal faces");
                assertEquals(Set.of("up", "down", "north", "south", "east", "west"), element.getAsJsonObject("faces").keySet(),
                    "each shallow chalk strip is closed on all six sides");
                assertEquals("down", element.getAsJsonObject("faces").getAsJsonObject("down").get("cullface").getAsString());
            }
        }
    }

    private static JsonObject json(final Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @Test
    void consecutiveStraightDiagonalsMeetExactlyAndOverlapEveryCenterNode() throws IOException {
        final Path models = Path.of("src/main/resources/assets/warlockery/models/block");
        for (final Side side : ConnectedGlyphGeometry.DIAGONALS.keySet()) {
            final JsonObject strip = json(models.resolve("chalk_glyph_" + side.id() + ".json"))
                .getAsJsonArray("elements").get(0).getAsJsonObject();
            final double radians = Math.toRadians(strip.getAsJsonObject("rotation").get("angle").getAsDouble());
            final double outer = strip.getAsJsonArray(side.dx() > 0 ? "to" : "from").get(0).getAsDouble() - 8;
            final double inner = strip.getAsJsonArray(side.dx() > 0 ? "from" : "to").get(0).getAsDouble() - 8;
            final double innerX = 8 + inner * Math.cos(radians);
            final double innerZ = 8 - inner * Math.sin(radians);
            assertTrue(innerX > ConnectedGlyphGeometry.CENTER.minX() && innerX < ConnectedGlyphGeometry.CENTER.maxX());
            assertTrue(innerZ > ConnectedGlyphGeometry.CENTER.minZ() && innerZ < ConnectedGlyphGeometry.CENTER.maxZ());
            final Side opposite = side.rotateQuarterTurns(2);
            final JsonObject returnStrip = json(models.resolve("chalk_glyph_" + opposite.id() + ".json"))
                .getAsJsonArray("elements").get(0).getAsJsonObject();
            final double returnRadians = Math.toRadians(returnStrip.getAsJsonObject("rotation").get("angle").getAsDouble());
            final double returnOuter = returnStrip.getAsJsonArray(opposite.dx() > 0 ? "to" : "from").get(0).getAsDouble() - 8;
            for (int mark = 0; mark < 4; mark++) {
                final double endpointX = mark * side.dx() * 16 + 8 + outer * Math.cos(radians);
                final double endpointZ = mark * side.dz() * 16 + 8 - outer * Math.sin(radians);
                assertEquals(endpointX, (mark + 1) * side.dx() * 16 + 8 + returnOuter * Math.cos(returnRadians), 0.00001);
                assertEquals(endpointZ, (mark + 1) * side.dz() * 16 + 8 - returnOuter * Math.sin(returnRadians), 0.00001);
            }
            assertEquals(strip.getAsJsonArray("to").get(1).getAsDouble(), returnStrip.getAsJsonArray("to").get(1).getAsDouble());
        }
    }
}
