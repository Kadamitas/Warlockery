package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SpiritPortalModelTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");

    @Test
    void returnColumnAndCraftedSquareHaveOnlyOneContinuousOuterBorderInBothAxes() throws Exception {
        final var multipart = read("blockstates/spiritportal.json").getAsJsonArray("multipart");
        for (String axis : new String[]{"x", "z"}) for (int width : new int[]{1, 2}) {
            final int height = 2;
            final int[][] border = new int[width * 16][height * 16];
            int surfaces = 0;
            for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
                final var state = Map.of("axis", axis, "left", String.valueOf(x > 0),
                    "right", String.valueOf(x < width - 1), "down", String.valueOf(y > 0),
                    "up", String.valueOf(y < height - 1));
                for (var raw : multipart) {
                    final var part = raw.getAsJsonObject();
                    if (part.getAsJsonObject("when").entrySet().stream()
                        .anyMatch(entry -> !entry.getValue().getAsString().equals(state.get(entry.getKey())))) continue;
                    final var apply = part.getAsJsonObject("apply");
                    assertEquals(axis.equals("z") ? 90 : 0, apply.has("y") ? apply.get("y").getAsInt() : 0);
                    final var model = read("models/" + apply.get("model").getAsString().split(":")[1] + ".json");
                    for (var element : model.getAsJsonArray("elements")) {
                        final var box = element.getAsJsonObject();
                        final var from = box.getAsJsonArray("from");
                        final var to = box.getAsJsonArray("to");
                        if (box.getAsJsonObject("faces").getAsJsonObject("north").get("texture").getAsString().equals("#portal")) {
                            assertEquals("[0,0,7]", from.toString());
                            assertEquals("[16,16,9]", to.toString());
                            surfaces++;
                        } else {
                            for (int px = from.get(0).getAsInt(); px < to.get(0).getAsInt(); px++)
                                for (int py = from.get(1).getAsInt(); py < to.get(1).getAsInt(); py++)
                                    border[x * 16 + px][y * 16 + py]++;
                        }
                    }
                }
            }
            assertEquals(width * height, surfaces);
            for (int x = 0; x < width * 16; x++) for (int y = 0; y < height * 16; y++) {
                final boolean outside = x == 0 || y == 0 || x == width * 16 - 1 || y == height * 16 - 1;
                assertEquals(outside ? 1 : 0, border[x][y], axis + " border at " + x + "," + y);
            }
        }
    }

    private static JsonObject read(final String relative) throws Exception {
        return JsonParser.parseString(Files.readString(ASSETS.resolve(relative))).getAsJsonObject();
    }
}
