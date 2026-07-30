package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.registry.SculptedBlockCatalog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class PlaceableModelFaceTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path MODELS = Path.of("src/main/resources/assets/warlockery/models/block");
    private static final Path BLOCK_STATES = ASSETS.resolve("blockstates");
    private static final Path TEXTURES = ASSETS.resolve("textures/block");
    private static final String MODEL_PREFIX = "warlockery:block/";

    @Test
    void sculptedElementsKeepAllSixFaces() {
        try (var files = Files.list(MODELS)) {
            files.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                final JsonObject model = json(path);
                if (!model.has("elements")) {
                    return;
                }
                model.getAsJsonArray("elements").forEach(value -> {
                    final JsonObject faces = value.getAsJsonObject().getAsJsonObject("faces");
                    List.of("down", "up", "north", "south", "west", "east")
                        .forEach(face -> assertTrue(faces.has(face), path.getFileName() + " missing " + face));
                });
            });
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Test
    void everySculptedBlockUsesTheNonOccludingRenderProfile() {
        assertEquals(sculptedBlockIds(), SculptedBlockCatalog.ids());
        assertTrue(SculptedBlockCatalog.contains("trent"));
        assertTrue(SculptedBlockCatalog.contains("silvervat"));
        assertTrue(SculptedBlockCatalog.contains("paradox_egg"));
    }

    @Test
    void appliedSculptedModelsResolveEveryFaceAndParticleTexture() {
        appliedModelIds().forEach(modelId -> {
            final ResolvedModel model = resolveModel(modelId);
            if (model.elements() == null) {
                return;
            }
            model.elements().forEach(element -> element.getAsJsonObject().getAsJsonObject("faces")
                .entrySet()
                .forEach(face -> assertTexture(modelId, face.getValue().getAsJsonObject().get("texture").getAsString(),
                    model.textures())));
            if (model.textures().containsKey("particle")) {
                assertTexture(modelId, "#particle", model.textures());
            }
        });
    }

    private static Set<String> sculptedBlockIds() {
        final Set<String> ids = new HashSet<>();
        try (Stream<Path> states = Files.list(BLOCK_STATES)) {
            states.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                final boolean sculpted = strings(json(path))
                    .filter(value -> value.startsWith(MODEL_PREFIX))
                    .map(value -> value.substring(MODEL_PREFIX.length()))
                    .map(PlaceableModelFaceTest::resolveModel)
                    .anyMatch(model -> model.elements() != null);
                if (sculpted) {
                    ids.add(path.getFileName().toString().replaceFirst("\\.json$", ""));
                }
            });
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return Set.copyOf(ids);
    }

    private static Stream<String> appliedModelIds() {
        try (Stream<Path> states = Files.list(BLOCK_STATES)) {
            return states.filter(path -> path.toString().endsWith(".json"))
                .flatMap(path -> strings(json(path)))
                .filter(value -> value.startsWith(MODEL_PREFIX))
                .map(value -> value.substring(MODEL_PREFIX.length()))
                .distinct()
                .toList()
                .stream();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static ResolvedModel resolveModel(final String modelId) {
        final JsonObject model = json(MODELS.resolve(modelId + ".json"));
        final String parentId = model.has("parent") ? model.get("parent").getAsString() : "";
        final ResolvedModel parent = parentId.startsWith(MODEL_PREFIX)
            ? resolveModel(parentId.substring(MODEL_PREFIX.length()))
            : new ResolvedModel(null, Map.of());
        final Map<String, String> textures = new HashMap<>(parent.textures());
        if (model.has("textures")) {
            model.getAsJsonObject("textures").entrySet()
                .forEach(entry -> textures.put(entry.getKey(), entry.getValue().getAsString()));
        }
        final JsonArray elements = model.has("elements") ? model.getAsJsonArray("elements") : parent.elements();
        return new ResolvedModel(elements, Map.copyOf(textures));
    }

    private static void assertTexture(
        final String modelId,
        final String textureReference,
        final Map<String, String> textures
    ) {
        String resolved = textureReference;
        final Set<String> visited = new HashSet<>();
        while (resolved.startsWith("#")) {
            final String key = resolved.substring(1);
            assertTrue(visited.add(key), modelId + " has a texture alias cycle at #" + key);
            assertTrue(textures.containsKey(key), modelId + " is missing texture slot #" + key);
            resolved = textures.get(key);
        }
        if (resolved.startsWith(MODEL_PREFIX)) {
            final String textureId = resolved.substring(MODEL_PREFIX.length());
            assertTrue(Files.isRegularFile(TEXTURES.resolve(textureId + ".png")),
                modelId + " is missing texture " + resolved);
        }
    }

    private static Stream<String> strings(final JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return Stream.of(element.getAsString());
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray().asList().stream().flatMap(PlaceableModelFaceTest::strings);
        }
        if (element.isJsonObject()) {
            return element.getAsJsonObject().entrySet().stream().flatMap(entry -> strings(entry.getValue()));
        }
        return Stream.empty();
    }

    private static JsonObject json(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record ResolvedModel(JsonArray elements, Map<String, String> textures) {
    }
}
