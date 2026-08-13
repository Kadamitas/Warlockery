package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class CrossModCatalogContractTest {
    private static final Path DATA = Path.of("src/main/resources/data");
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path MANIFEST = DATA.resolve("warlockery/compatibility/catalog.json");
    private static final Pattern ENTITY_REGISTRATION = Pattern.compile(
        "(?:register|hobgoblin)\\(\\\"([a-z0-9_]+)\\\"|Map\\.entry\\(\\\"([a-z0-9_]+)\\\""
    );

    @TestFactory
    Stream<DynamicTest> everyRegisteredCatalogEntryHasACompatibilityClassification() {
        return classifications().entrySet().stream().map(entry -> DynamicTest.dynamicTest(
            entry.getKey().registry() + ":" + entry.getKey().id(),
            () -> {
                assertFalse(entry.getValue().isEmpty());
                assertFalse(entry.getValue().contains("private_magic") && entry.getValue().size() > 1);
            }
        ));
    }

    @Test
    void classificationCoversEveryRequestedCompatibilityFamily() {
        final Set<String> roles = classifications().values().stream()
            .flatMap(Set::stream)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertTrue(roles.containsAll(Set.of(
            "equipment",
            "seed",
            "crop",
            "wood_family",
            "metal_form",
            "brew",
            "projectile",
            "machine",
            "fuel",
            "fluid",
            "food",
            "drink",
            "common_container",
            "common_utility",
            "common_block",
            "spawn_egg",
            "private_magic"
        )));
    }

    @Test
    void catalogEnumerationMatchesEveryRegistrationPath() {
        final Set<CatalogEntry> entries = catalog();
        final Set<String> blocks = entries.stream()
            .filter(entry -> entry.registry().equals("block"))
            .map(CatalogEntry::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        final Set<String> items = entries.stream()
            .filter(entry -> entry.registry().equals("item"))
            .map(CatalogEntry::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        final Set<String> fluids = entries.stream()
            .filter(entry -> entry.registry().equals("fluid"))
            .map(CatalogEntry::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertEquals(ContentCatalog.BLOCKS.size(), blocks.size());
        assertTrue(items.containsAll(ContentCatalog.BREWS));
        assertTrue(items.containsAll(ContentCatalog.INGREDIENTS.stream().map(ContentCatalog::ingredientId).toList()));
        assertTrue(items.containsAll(spawnEggIds()));
        assertEquals(Set.of(
            "spirit",
            "flowing_spirit",
            "hollow_tears",
            "flowing_hollow_tears",
            "colored_brew_water",
            "flowing_colored_brew_water",
            "erosion_brew",
            "flowing_erosion_brew"
        ), fluids);
    }

    @TestFactory
    Stream<DynamicTest> everyDeclaredTagContractIsPresent() {
        return manifest().getAsJsonArray("tag_contracts").asList().stream().map(element -> {
            final JsonObject contract = element.getAsJsonObject();
            final String registry = contract.get("registry").getAsString();
            final String tag = contract.get("tag").getAsString();
            return DynamicTest.dynamicTest(registry + " " + tag, () -> {
                final Set<String> values = rawTagValues(tagPath(registry, tag));
                assertTrue(values.containsAll(strings(contract.getAsJsonArray("contains"))),
                    () -> tag + " lacks " + strings(contract.getAsJsonArray("contains")));
            });
        });
    }

    @TestFactory
    Stream<DynamicTest> everyMaterialSubstitutionRecipeUsesItsCanonicalTag() {
        return manifest().getAsJsonArray("recipe_contracts").asList().stream().map(element -> {
            final JsonObject contract = element.getAsJsonObject();
            final String path = contract.get("path").getAsString();
            return DynamicTest.dynamicTest(path, () -> {
                final String source = read(DATA.resolve("warlockery").resolve(path));
                assertTrue(source.contains(contract.get("required").getAsString()));
                assertFalse(source.contains(contract.get("forbidden").getAsString()));
            });
        });
    }

    @Test
    void obsoleteForgeTagReferencesAndInventedCommonPotionTagsAreAbsent() throws IOException {
        try (var paths = Files.walk(DATA)) {
            final List<Path> checked = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".json"))
                .filter(CrossModCatalogContractTest::canContainIngredientOrTagReference)
                .toList();
            checked.forEach(path -> assertFalse(read(path).contains("#forge:"), path::toString));
        }
        final Path obsoleteTags = DATA.resolve("forge/tags");
        if (Files.exists(obsoleteTags)) {
            try (var paths = Files.walk(obsoleteTags)) {
                assertTrue(paths.noneMatch(Files::isRegularFile));
            }
        }
        assertFalse(Files.exists(DATA.resolve("c/tags/item/potions/splash.json")));
        assertFalse(Files.exists(DATA.resolve("c/tags/fluid/acids.json")));
        assertFalse(Files.exists(DATA.resolve("c/tags/fluid/colored_water.json")));
        assertFalse(Files.exists(DATA.resolve("c/tags/block/brew_gases.json")));
        assertFalse(Files.exists(DATA.resolve("c/tags/damage_type/damage_types/magic.json")));
    }

    @Test
    void projectilesUseVanillaProjectileAndCrossbowMechanics() {
        final String itemRegistry = read(Path.of("src/main/java/com/kadamitas/warlockery/registry/ModItems.java"));
        final String hunter = read(Path.of("src/main/java/com/kadamitas/warlockery/entity/WerewolfHunterEntity.java"));
        final String worldIntegration = read(Path.of("src/main/java/com/kadamitas/warlockery/world/CreatureWorldIntegration.java"));
        final String combat = read(Path.of("src/main/java/com/kadamitas/warlockery/entity/CreatureCombat.java"));
        final String rock = read(Path.of("src/main/java/com/kadamitas/warlockery/item/RockItem.java"));
        final String brews = read(Path.of("src/main/java/com/kadamitas/warlockery/brew/BrewItem.java"));

        assertTrue(itemRegistry.contains("new ArrowItem(properties(id))"));
        assertFalse(itemRegistry.contains("silver_repeater"));
        assertTrue(hunter.contains("new ItemStack(Items.CROSSBOW)"));
        assertTrue(worldIntegration.contains("new ItemStack(Items.CROSSBOW)"));
        assertTrue(hunter.contains("ingredient_bolt_silver"));
        assertTrue(worldIntegration.contains("ingredient_bolt_silver"));
        assertTrue(combat.contains("WarlockeryTags.Items.SILVER_PROJECTILES"));
        assertTrue(rock.contains("extends SnowballItem"));
        assertTrue(brews.contains("extends SplashPotionItem"));
    }

    @Test
    void fuelsUseTheNeoForgeItemBurnTimeHook() {
        final String item = read(Path.of("src/main/java/com/kadamitas/warlockery/brew/BrewItem.java"));
        final String kind = read(Path.of("src/main/java/com/kadamitas/warlockery/brew/BrewKind.java"));
        assertTrue(item.contains("public int getBurnTime("));
        assertTrue(item.contains("final FuelValues fuelValues"));
        assertTrue(item.contains("kind.fuelBurnTime()"));
        assertTrue(kind.contains("return this == COMBUSTION ? 2_400 : 0;"));
    }

    @Test
    void machinesExposeSidedItemAndApplicableFluidCapabilities() {
        final String machine = read(Path.of(
            "src/main/java/com/kadamitas/warlockery/block/entity/MagicMachineBlockEntity.java"
        ));
        final String profiles = read(Path.of(
            "src/main/java/com/kadamitas/warlockery/crafting/MachineProfiles.java"
        ));
        assertTrue(machine.contains("Capabilities.Item.BLOCK"));
        assertTrue(machine.contains("Capabilities.Fluid.BLOCK"));
        assertTrue(machine.contains("WorldlyContainerWrapper::new"));
        assertTrue(machine.contains("machineProfile().supportsFluids()"));
        idClassifications("machine", "block").forEach(id -> assertTrue(profiles.contains("\"" + id + "\""), id));
    }

    @Test
    void spiritFluidRemainsPrivateAndManaInteroperabilityIsDocumentedHonestly() throws IOException {
        final Path commonFluids = DATA.resolve("c/tags/fluid");
        if (Files.exists(commonFluids)) {
            try (var paths = Files.walk(commonFluids)) {
                paths.filter(Files::isRegularFile).forEach(path -> {
                    final String source = read(path);
                    assertFalse(source.contains("warlockery:spirit"));
                    assertFalse(source.contains("warlockery:flowing_spirit"));
                    assertFalse(source.contains("warlockery:hollow_tears"));
                    assertFalse(source.contains("warlockery:flowing_hollow_tears"));
                });
            }
        }
        final String documentation = read(Path.of("docs/CROSS_MOD_COMPATIBILITY.md"));
        assertTrue(documentation.contains("NeoForge 26.2 has no universal mana capability"));
        assertTrue(documentation.contains("Warlockery altar power is not NeoForge Energy"));
    }

    @Test
    void dynamicSpawnEggRegistrationHasOneResourcePerEntity() throws IOException {
        final Set<String> registered = spawnEggIds();
        try (var paths = Files.list(ASSETS.resolve("items"))) {
            final Set<String> resources = paths
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith("_spawn_egg.json"))
                .map(name -> name.substring(0, name.length() - ".json".length()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            assertEquals(registered, resources);
        }
        assertTrue(read(Path.of("src/main/java/com/kadamitas/warlockery/Warlockery.java"))
            .contains("ModItems.registerSpawnEggs(ModEntities.ALL)"));
    }

    private static Map<CatalogEntry, Set<String>> classifications() {
        final Map<CatalogEntry, Set<String>> classifications = new TreeMap<>();
        catalog().forEach(entry -> classifications.put(entry, new TreeSet<>()));
        final JsonObject manifest = manifest();

        manifest.getAsJsonArray("tag_classifications").forEach(element -> {
            final JsonObject classification = element.getAsJsonObject();
            final String role = classification.get("role").getAsString();
            final String registry = classification.get("registry").getAsString();
            strings(classification.getAsJsonArray("tags")).stream()
                .flatMap(tag -> resolveTag(registry, tag, new HashSet<>()).stream())
                .forEach(id -> addRole(classifications, new CatalogEntry(registry, id), role));
        });

        manifest.getAsJsonArray("id_classifications").forEach(element -> {
            final JsonObject classification = element.getAsJsonObject();
            final String role = classification.get("role").getAsString();
            final String registry = classification.get("registry").getAsString();
            strings(classification.getAsJsonArray("ids")).forEach(id ->
                addRole(classifications, new CatalogEntry(registry, id), role)
            );
        });

        classifications.forEach((entry, roles) -> {
            if (entry.registry().equals("item") && entry.id().endsWith("_spawn_egg")) {
                roles.add("spawn_egg");
            }
            if (roles.isEmpty()) {
                roles.add("private_magic");
            }
        });
        return classifications;
    }

    private static void addRole(
        final Map<CatalogEntry, Set<String>> classifications,
        final CatalogEntry entry,
        final String role
    ) {
        final Set<String> roles = classifications.get(entry);
        assertNotNull(roles, () -> role + " classification references unregistered " + entry.registry() + ":" + entry.id());
        roles.add(role);
    }

    private static Set<CatalogEntry> catalog() {
        final Set<String> blocks = ContentCatalog.BLOCKS.stream()
            .map(ContentCatalog::modernize)
            .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        final Set<String> items = new TreeSet<>();
        blocks.stream()
            .filter(id -> !ContentCatalog.CROPS.contains(id))
            .filter(id -> !id.equals("pentacle"))
            .forEach(items::add);
        ContentCatalog.ITEMS.stream().map(ContentCatalog::modernize).forEach(items::add);
        ContentCatalog.INGREDIENTS.stream().map(ContentCatalog::ingredientId).forEach(items::add);
        items.addAll(ContentCatalog.BREWS);
        items.addAll(spawnEggIds());

        return Stream.of(
            blocks.stream().map(id -> new CatalogEntry("block", id)),
            items.stream().map(id -> new CatalogEntry("item", id)),
            Stream.of(
                new CatalogEntry("fluid", "spirit"),
                new CatalogEntry("fluid", "flowing_spirit"),
                new CatalogEntry("fluid", "hollow_tears"),
                new CatalogEntry("fluid", "flowing_hollow_tears"),
                new CatalogEntry("fluid", "colored_brew_water"),
                new CatalogEntry("fluid", "flowing_colored_brew_water"),
                new CatalogEntry("fluid", "erosion_brew"),
                new CatalogEntry("fluid", "flowing_erosion_brew")
            )
        ).flatMap(stream -> stream).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<String> spawnEggIds() {
        final var matcher = ENTITY_REGISTRATION.matcher(read(Path.of(
            "src/main/java/com/kadamitas/warlockery/registry/ModEntities.java"
        )));
        final Set<String> ids = new TreeSet<>();
        while (matcher.find()) {
            ids.add((matcher.group(1) == null ? matcher.group(2) : matcher.group(1)) + "_spawn_egg");
        }
        return Set.copyOf(ids);
    }

    private static Set<String> resolveTag(
        final String registry,
        final String tag,
        final Set<String> visited
    ) {
        final String key = registry + ":" + tag;
        if (!visited.add(key)) {
            return Set.of();
        }
        final Path path = tagPath(registry, tag);
        if (!Files.exists(path)) {
            return Set.of();
        }
        final Set<String> resolved = new TreeSet<>();
        rawTagValues(path).forEach(value -> {
            if (value.startsWith("#")) {
                resolved.addAll(resolveTag(registry, value.substring(1), visited));
            } else if (value.startsWith("warlockery:")) {
                resolved.add(value.substring("warlockery:".length()));
            }
        });
        return Set.copyOf(resolved);
    }

    private static Set<String> idClassifications(final String role, final String registry) {
        return manifest().getAsJsonArray("id_classifications").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .filter(value -> value.get("role").getAsString().equals(role))
            .filter(value -> value.get("registry").getAsString().equals(registry))
            .flatMap(value -> strings(value.getAsJsonArray("ids")).stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<String> rawTagValues(final Path path) {
        final JsonArray values = readJson(path).getAsJsonArray("values");
        assertNotNull(values, path::toString);
        final Set<String> result = new TreeSet<>();
        values.forEach(value -> {
            if (value.isJsonPrimitive()) {
                result.add(value.getAsString());
            } else if (value.isJsonObject() && value.getAsJsonObject().has("id")) {
                result.add(value.getAsJsonObject().get("id").getAsString());
            }
        });
        return Set.copyOf(result);
    }

    private static Path tagPath(final String registry, final String tag) {
        final int separator = tag.indexOf(':');
        final String namespace = separator < 0 ? "minecraft" : tag.substring(0, separator);
        final String path = separator < 0 ? tag : tag.substring(separator + 1);
        return DATA.resolve(namespace).resolve("tags").resolve(registry).resolve(path + ".json");
    }

    private static boolean canContainIngredientOrTagReference(final Path path) {
        final String normalized = path.toString().replace('\\', '/');
        return normalized.contains("/tags/")
            || normalized.contains("/recipe/")
            || normalized.contains("/ritual/")
            || normalized.contains("/warlockery_machine/");
    }

    private static Set<String> strings(final JsonArray values) {
        return values.asList().stream()
            .map(JsonElement::getAsString)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static JsonObject manifest() {
        return readJson(MANIFEST);
    }

    private static JsonObject readJson(final Path path) {
        return JsonParser.parseString(read(path)).getAsJsonObject();
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private record CatalogEntry(String registry, String id) implements Comparable<CatalogEntry> {
        @Override
        public int compareTo(final CatalogEntry other) {
            final int registryOrder = registry.compareTo(other.registry);
            return registryOrder != 0 ? registryOrder : id.compareTo(other.id);
        }
    }
}
