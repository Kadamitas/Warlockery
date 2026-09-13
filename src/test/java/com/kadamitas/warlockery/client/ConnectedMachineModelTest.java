package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ConnectedMachineModelTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");

    @Test
    void assembledAltarSurfacesAreClosedInEitherOrientation() throws IOException {
        final JsonArray parts = read("blockstates/altar.json").getAsJsonArray("multipart");
        for (int width : new int[]{2, 3}) {
            final int depth = 6 / width;
            final JsonArray assembled = new JsonArray();
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    final Set<String> connected = new HashSet<>();
                    if (x > 0) connected.add("west");
                    if (x < width - 1) connected.add("east");
                    if (z > 0) connected.add("north");
                    if (z < depth - 1) connected.add("south");
                    for (final var raw : parts) {
                        final JsonObject part = raw.getAsJsonObject();
                        if (part.has("when") && part.getAsJsonObject("when").entrySet().stream()
                            .anyMatch(e -> connected.contains(e.getKey()) != e.getValue().getAsBoolean())) continue;
                        final JsonObject apply = part.getAsJsonObject("apply");
                        final String model = apply.get("model").getAsString().replace("warlockery:block/", "");
                        final int turns = apply.has("y") ? apply.get("y").getAsInt() / 90 : 0;
                        for (final var value : PlaceableModelFaceTest.resolvedElements(model)) {
                            final JsonObject element = value.getAsJsonObject().deepCopy();
                            for (int turn = 0; turn < turns; turn++) {
                                final JsonArray from = element.getAsJsonArray("from");
                                final JsonArray to = element.getAsJsonArray("to");
                                final double minX = from.get(0).getAsDouble(), maxX = to.get(0).getAsDouble();
                                from.set(0, new com.google.gson.JsonPrimitive(16 - to.get(2).getAsDouble()));
                                to.set(0, new com.google.gson.JsonPrimitive(16 - from.get(2).getAsDouble()));
                                from.set(2, new com.google.gson.JsonPrimitive(minX));
                                to.set(2, new com.google.gson.JsonPrimitive(maxX));
                                final JsonObject old = element.getAsJsonObject("faces"), rotated = new JsonObject();
                                old.entrySet().forEach(face -> rotated.add(switch (face.getKey()) {
                                    case "north" -> "east"; case "east" -> "south";
                                    case "south" -> "west"; case "west" -> "north";
                                    default -> face.getKey();
                                }, face.getValue()));
                                element.add("faces", rotated);
                            }
                            for (String end : new String[]{"from", "to"}) {
                                final JsonArray point = element.getAsJsonArray(end);
                                point.set(0, new com.google.gson.JsonPrimitive(point.get(0).getAsDouble() + 16 * x));
                                point.set(2, new com.google.gson.JsonPrimitive(point.get(2).getAsDouble() + 16 * z));
                            }
                            assembled.add(element);
                        }
                    }
                }
            }
            PlaceableModelFaceTest.assertClosedSurface(assembled, "assembled altar " + width + "x" + depth);
        }
    }

    @Test
    void spinningWheelHasNoFloatingPiecesAndThreadIsConfinedToSpool() throws IOException {
        final JsonArray elements = read("models/block/spinningwheel.json").getAsJsonArray("elements");
        final Set<Integer> grounded = new HashSet<>();
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).getAsJsonObject().getAsJsonArray("from").get(1).getAsDouble() == 0) grounded.add(i);
        }
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < elements.size(); i++) {
                if (grounded.contains(i)) continue;
                for (final int j : Set.copyOf(grounded)) {
                    if (touches(elements.get(i).getAsJsonObject(), elements.get(j).getAsJsonObject())) {
                        grounded.add(i);
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);
        assertEquals(elements.size(), grounded.size(), "Every component must connect to the base through solid geometry");
        int threadPieces = 0;
        for (final var raw : elements) {
            final JsonObject element = raw.getAsJsonObject();
            if (element.getAsJsonObject("faces").entrySet().stream()
                .anyMatch(e -> e.getValue().getAsJsonObject().get("texture").getAsString().equals("#thread"))) {
                threadPieces++;
                assertFalse(element.has("rotation"));
                assertTrue(element.getAsJsonArray("to").get(1).getAsDouble()
                    - element.getAsJsonArray("from").get(1).getAsDouble() <= 3);
            }
        }
        assertEquals(1, threadPieces);
    }

    private static boolean touches(final JsonObject a, final JsonObject b) {
        for (int axis = 0; axis < 3; axis++) {
            if (a.getAsJsonArray("from").get(axis).getAsDouble() > b.getAsJsonArray("to").get(axis).getAsDouble()
                || b.getAsJsonArray("from").get(axis).getAsDouble() > a.getAsJsonArray("to").get(axis).getAsDouble()) return false;
        }
        return true;
    }

    private static JsonObject read(final String path) throws IOException {
        return JsonParser.parseString(Files.readString(ASSETS.resolve(path))).getAsJsonObject();
    }
}
