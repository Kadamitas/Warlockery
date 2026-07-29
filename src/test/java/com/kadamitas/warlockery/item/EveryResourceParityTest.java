package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class EveryResourceParityTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data");
    private static final String REAGENT_TAG = "warlockery:resource_reagents";

    @Test
    void catalogOwnsEveryAuditedPartialResourceExactlyOnce() {
        assertEquals(72, ResourceParityCatalog.PROFILES.size());
        assertEquals(72, ResourceParityCatalog.PROFILES.stream()
            .map(ResourceParityCatalog.Profile::wikiPage)
            .collect(java.util.stream.Collectors.toUnmodifiableSet())
            .size());
    }

    @TestFactory
    Stream<DynamicContainer> oneFailureDiagnosticAndSuccessSuitePerResource() {
        final EvidenceIndex evidence = EvidenceIndex.load(DATA);
        final Set<String> compatibleItems = evidence.tags().directItems(REAGENT_TAG);
        return ResourceParityCatalog.PROFILES.stream().map(profile -> DynamicContainer.dynamicContainer(
            profile.wikiPage(),
            List.of(
                DynamicTest.dynamicTest("failure", () -> assertEquals(
                    ResourceParityCatalog.Diagnostic.MISSING_ACQUISITION,
                    ResourceParityCatalog.diagnose(false, true, true)
                )),
                DynamicTest.dynamicTest("diagnostic and compatibility", () -> {
                    assertEquals(
                        ResourceParityCatalog.Diagnostic.MISSING_CONSUMER,
                        ResourceParityCatalog.diagnose(true, false, true)
                    );
                    assertEquals(REAGENT_TAG, profile.compatibilityTag());
                    assertTrue(compatibleItems.contains(profile.registryId()), profile.registryId());
                }),
                DynamicTest.dynamicTest("success", () -> {
                    assertEquals(
                        ResourceParityCatalog.Diagnostic.READY,
                        ResourceParityCatalog.diagnose(true, true, true)
                    );
                    final Set<Evidence> acquisition = evidence.acquisition(profile.registryId());
                    final Set<Evidence> consumers = evidence.consumers(profile.registryId());
                    final List<ResourceParityCatalog.RuntimeEvidence> runtimeAcquisition =
                        profile.runtimeEvidence(ResourceParityCatalog.EvidenceKind.ACQUISITION);
                    final List<ResourceParityCatalog.RuntimeEvidence> runtimeConsumers =
                        profile.runtimeEvidence(ResourceParityCatalog.EvidenceKind.CONSUMER);
                    Stream.concat(runtimeAcquisition.stream(), runtimeConsumers.stream())
                        .forEach(EveryResourceParityTest::assertRuntimeMember);
                    assertFalse(
                        acquisition.isEmpty() && runtimeAcquisition.isEmpty(),
                        profile.registryId() + " lacks acquisition evidence"
                    );
                    assertFalse(
                        consumers.isEmpty() && runtimeConsumers.isEmpty(),
                        profile.registryId() + " lacks consumer evidence"
                    );
                })
            )
        ));
    }

    private static JsonObject readObject(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static void assertRuntimeMember(final ResourceParityCatalog.RuntimeEvidence evidence) {
        assertTrue(
            Stream.of(evidence.owner().getDeclaredMethods()).anyMatch(method ->
                method.getName().equals(evidence.member())
                    && method.getParameterCount() == evidence.parameterCount()
            ),
            evidence.owner().getName() + "#" + evidence.member() + "/" + evidence.parameterCount()
        );
    }

    private record Evidence(Path path, String semantic) {
    }

    private record EvidenceIndex(
        TagIndex tags,
        Map<String, Set<Evidence>> acquisitions,
        Map<String, Set<Evidence>> consumption
    ) {
        private static EvidenceIndex load(final Path data) {
            final TagIndex tags = TagIndex.load(data);
            final Map<String, Set<Evidence>> acquisitions = new HashMap<>();
            final Map<String, Set<Evidence>> consumption = new HashMap<>();
            jsonFiles(data).forEach(path -> index(path, data, tags, acquisitions, consumption));
            return new EvidenceIndex(tags, immutable(acquisitions), immutable(consumption));
        }

        private Set<Evidence> acquisition(final String item) {
            return acquisitions.getOrDefault(item, Set.of());
        }

        private Set<Evidence> consumers(final String item) {
            return consumption.getOrDefault(item, Set.of());
        }

        private static void index(
            final Path path,
            final Path data,
            final TagIndex tags,
            final Map<String, Set<Evidence>> acquisitions,
            final Map<String, Set<Evidence>> consumption
        ) {
            final Path relative = data.relativize(path);
            if (relative.getNameCount() < 2) {
                return;
            }
            final JsonObject json = readObject(path);
            switch (relative.getName(1).toString()) {
                case "recipe" -> indexRecipe(path, json, tags, acquisitions, consumption);
                case "warlockery_machine" -> indexMachine(path, json, tags, acquisitions, consumption);
                case "loot_table" -> indexLoot(path, json, acquisitions);
                case "ritual" -> indexRitual(path, json, tags, consumption);
                case "custom_brew_component" ->
                    ingredient(json.get("ingredient"), path, "brew component", tags, consumption);
                default -> {
                }
            }
        }

        private static void indexRecipe(
            final Path path,
            final JsonObject json,
            final TagIndex tags,
            final Map<String, Set<Evidence>> acquisitions,
            final Map<String, Set<Evidence>> consumption
        ) {
            output(json.get("result"), path, "recipe result", acquisitions);
            if (json.has("key")) {
                json.getAsJsonObject("key").entrySet().forEach(entry ->
                    ingredient(entry.getValue(), path, "recipe key " + entry.getKey(), tags, consumption)
                );
            }
            ingredient(json.get("ingredient"), path, "recipe ingredient", tags, consumption);
            ingredient(json.get("ingredients"), path, "recipe ingredients", tags, consumption);
            ingredient(json.get("base"), path, "recipe base", tags, consumption);
            ingredient(json.get("addition"), path, "recipe addition", tags, consumption);
            ingredient(json.get("template"), path, "recipe template", tags, consumption);
        }

        private static void indexMachine(
            final Path path,
            final JsonObject json,
            final TagIndex tags,
            final Map<String, Set<Evidence>> acquisitions,
            final Map<String, Set<Evidence>> consumption
        ) {
            if (json.has("outputs")) {
                json.getAsJsonArray("outputs").forEach(output ->
                    output(output, path, "machine output", acquisitions)
                );
            }
            if (json.has("inputs")) {
                json.getAsJsonArray("inputs").forEach(input -> {
                    final JsonObject object = input.getAsJsonObject();
                    ingredient(object.get("ingredient"), path, "machine input", tags, consumption);
                });
            }
        }

        private static void indexLoot(
            final Path path,
            final JsonElement element,
            final Map<String, Set<Evidence>> acquisitions
        ) {
            if (element.isJsonArray()) {
                element.getAsJsonArray().forEach(child -> indexLoot(path, child, acquisitions));
                return;
            }
            if (!element.isJsonObject()) {
                return;
            }
            final JsonObject object = element.getAsJsonObject();
            if (string(object, "type").filter("minecraft:item"::equals).isPresent()) {
                string(object, "name").ifPresent(item -> add(acquisitions, item, path, "loot item entry"));
            }
            object.entrySet().forEach(entry -> indexLoot(path, entry.getValue(), acquisitions));
        }

        private static void indexRitual(
            final Path path,
            final JsonObject json,
            final TagIndex tags,
            final Map<String, Set<Evidence>> consumption
        ) {
            if (!json.has("requirements")) {
                return;
            }
            final JsonObject requirements = json.getAsJsonObject("requirements");
            if (!requirements.has("ingredients")) {
                return;
            }
            requirements.getAsJsonArray("ingredients").forEach(requirement ->
                ingredient(
                    requirement.getAsJsonObject().get("ingredient"),
                    path,
                    "ritual requirement",
                    tags,
                    consumption
                )
            );
        }

        private static void output(
            final JsonElement element,
            final Path path,
            final String semantic,
            final Map<String, Set<Evidence>> acquisitions
        ) {
            if (element == null || element.isJsonNull()) {
                return;
            }
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                add(acquisitions, element.getAsString(), path, semantic);
                return;
            }
            if (!element.isJsonObject()) {
                return;
            }
            final JsonObject object = element.getAsJsonObject();
            string(object, "id").or(() -> string(object, "item"))
                .ifPresent(item -> add(acquisitions, item, path, semantic));
        }

        private static void ingredient(
            final JsonElement element,
            final Path path,
            final String semantic,
            final TagIndex tags,
            final Map<String, Set<Evidence>> consumption
        ) {
            if (element == null || element.isJsonNull()) {
                return;
            }
            if (element.isJsonArray()) {
                element.getAsJsonArray().forEach(value -> ingredient(value, path, semantic, tags, consumption));
                return;
            }
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                consumeReference(element.getAsString(), path, semantic, tags, consumption);
                return;
            }
            if (!element.isJsonObject()) {
                return;
            }
            final JsonObject object = element.getAsJsonObject();
            string(object, "item").ifPresent(value -> consumeReference(value, path, semantic, tags, consumption));
            string(object, "id").ifPresent(value -> consumeReference(value, path, semantic, tags, consumption));
            string(object, "tag").ifPresent(value -> consumeReference("#" + value, path, semantic, tags, consumption));
            ingredient(object.get("ingredient"), path, semantic, tags, consumption);
        }

        private static void consumeReference(
            final String reference,
            final Path path,
            final String semantic,
            final TagIndex tags,
            final Map<String, Set<Evidence>> consumption
        ) {
            if (reference.startsWith("#")) {
                tags.items(reference.substring(1)).forEach(item ->
                    add(consumption, item, path, semantic + " via " + reference)
                );
                return;
            }
            add(consumption, reference, path, semantic);
        }

        private static void add(
            final Map<String, Set<Evidence>> destination,
            final String item,
            final Path path,
            final String semantic
        ) {
            destination.computeIfAbsent(item, ignored -> new LinkedHashSet<>()).add(new Evidence(path, semantic));
        }

        private static Map<String, Set<Evidence>> immutable(final Map<String, Set<Evidence>> source) {
            return source.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> Set.copyOf(entry.getValue())
            ));
        }
    }

    private record TagIndex(Map<String, Set<String>> directValues) {
        private static TagIndex load(final Path data) {
            final Map<String, Set<String>> values = new HashMap<>();
            jsonFiles(data).stream()
                .filter(path -> isItemTag(data.relativize(path)))
                .forEach(path -> values.put(tagId(data, path), tagValues(path)));
            return new TagIndex(Map.copyOf(values));
        }

        private Set<String> directItems(final String tag) {
            return directValues.getOrDefault(tag, Set.of()).stream()
                .filter(value -> !value.startsWith("#"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        private Set<String> items(final String tag) {
            return resolve(tag, new HashSet<>());
        }

        private Set<String> resolve(final String tag, final Set<String> visited) {
            if (!visited.add(tag)) {
                return Set.of();
            }
            final Set<String> result = new LinkedHashSet<>();
            directValues.getOrDefault(tag, Set.of()).forEach(value -> {
                if (value.startsWith("#")) {
                    result.addAll(resolve(value.substring(1), visited));
                } else {
                    result.add(value);
                }
            });
            return Set.copyOf(result);
        }

        private static String tagId(final Path data, final Path path) {
            final Path relative = data.relativize(path);
            final String namespace = relative.getName(0).toString();
            final Path itemRoot = Path.of(namespace, "tags", "item");
            final String name = itemRoot.relativize(relative).toString().replace('\\', '/');
            return namespace + ":" + name.substring(0, name.length() - ".json".length());
        }

        private static boolean isItemTag(final Path relative) {
            return relative.getNameCount() > 3
                && relative.getName(1).toString().equals("tags")
                && relative.getName(2).toString().equals("item");
        }

        private static Set<String> tagValues(final Path path) {
            final JsonObject json = readObject(path);
            if (!json.has("values")) {
                return Set.of();
            }
            return json.getAsJsonArray("values").asList().stream()
                .map(TagIndex::tagValue)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        private static String tagValue(final JsonElement value) {
            if (value.isJsonPrimitive()) {
                return value.getAsString();
            }
            return value.getAsJsonObject().get("id").getAsString();
        }
    }

    private static List<Path> jsonFiles(final Path root) {
        try (var paths = Files.walk(root)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".json"))
                .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(root.toString(), exception);
        }
    }

    private static java.util.Optional<String> string(final JsonObject object, final String key) {
        return object.has(key) && object.get(key).isJsonPrimitive()
            ? java.util.Optional.of(object.get(key).getAsString())
            : java.util.Optional.empty();
    }
}
