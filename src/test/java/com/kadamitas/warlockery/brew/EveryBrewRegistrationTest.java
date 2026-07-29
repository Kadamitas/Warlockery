package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.crafting.MachineProfile;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeDefinition;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.crafting.MachineStatus;
import com.kadamitas.warlockery.crafting.MachineUiState;
import com.kadamitas.warlockery.registry.ContentCatalog;
import com.kadamitas.warlockery.testutil.JsonFixtureLoader;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class EveryBrewRegistrationTest {
    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "warlockery");
    private static final Path RECIPES = Path.of(
        "src", "main", "resources", "data", "warlockery", "warlockery_machine"
    );
    private static final Map<String, MachineRecipeDefinition> ACQUISITION_RECIPES = JsonFixtureLoader.load(
        RECIPES, MachineRecipeDefinition.CODEC
    ).stream().filter(fixture -> fixture.id().startsWith("kettle_brew_"))
        .collect(Collectors.toUnmodifiableMap(JsonFixtureLoader.Fixture::id, JsonFixtureLoader.Fixture::value));
    private static final JsonObject TRANSLATIONS = readJson(ASSETS.resolve("lang/en_us.json"));
    private static final String MOD_ITEMS_SOURCE = readText(Path.of(
        "src", "main", "java", "com", "kadamitas", "warlockery", "registry", "ModItems.java"
    ));

    @TestFactory
    Stream<DynamicContainer> oneSuitePerBuiltInBrew() {
        return BrewKind.builtIns().stream().map(kind -> DynamicContainer.dynamicContainer(
            BrewFactory.itemId(kind),
            List.of(
                DynamicTest.dynamicTest("failure reports a missing acquisition condition", () -> failureContract(kind)),
                DynamicTest.dynamicTest("visible outcome has translated tinted pixel art", () -> visibleOutcome(kind)),
                DynamicTest.dynamicTest("success dispatches through registration and kettle output", () -> successContract(kind))
            )
        ));
    }

    private static void failureContract(final BrewKind kind) {
        final String itemId = BrewFactory.itemId(kind);
        assertThrows(IllegalArgumentException.class, () -> BrewFactory.requireKind("missing_" + itemId));
        final MachineRecipeDefinition recipe = recipe(itemId);
        final MachineRecipeDefinition.Input missing = recipe.inputs().getFirst();
        final MachineProfile profile = MachineProfiles.forRecipeType(recipe.machine()).orElseThrow();
        final MachineRecipeManager.Diagnostic diagnostic = new MachineRecipeManager.Diagnostic(
            "kettle_" + itemId,
            recipe.outputs().getFirst().item(),
            recipe.processingTime(),
            List.of(new MachineRecipeManager.MissingInput(missing.ingredient(), missing.count())),
            List.of()
        );
        assertFalse(MachineUiState.from(profile, diagnostic, MachineStatus.READY).showGreenCheck());
        assertEquals(missing.ingredient(), diagnostic.missing().getFirst().ingredient());
        assertEquals(missing.count(), diagnostic.missing().getFirst().count());
        assertFalse(missing.ingredient().isBlank());
    }

    private static void visibleOutcome(final BrewKind kind) {
        final String itemId = BrewFactory.itemId(kind);
        final String translationKey = "item.warlockery." + itemId;
        assertTrue(TRANSLATIONS.has(translationKey), translationKey);
        assertFalse(TRANSLATIONS.get(translationKey).getAsString().isBlank(), translationKey);
        final JsonObject definition = readJson(ASSETS.resolve("items/" + itemId + ".json"))
            .getAsJsonObject("model");
        assertEquals("minecraft:model", definition.get("type").getAsString());
        assertEquals("warlockery:item/" + itemId, definition.get("model").getAsString());
        assertEquals("minecraft:potion", definition.getAsJsonArray("tints").get(0)
            .getAsJsonObject().get("type").getAsString());
        final JsonObject model = readJson(ASSETS.resolve("models/item/" + itemId + ".json"));
        assertEquals("warlockery:item/brew_splash_bottle", model.getAsJsonObject("textures")
            .get("layer0").getAsString());
        final BufferedImage texture = readImage(ASSETS.resolve("textures/item/brew_splash_bottle.png"));
        assertEquals(16, texture.getWidth());
        assertEquals(16, texture.getHeight());
        final int[] pixels = texture.getRGB(0, 0, 16, 16, null, 0, 16);
        assertTrue(java.util.Arrays.stream(pixels).anyMatch(pixel -> pixel >>> 24 == 0));
        assertTrue(java.util.Arrays.stream(pixels).anyMatch(pixel -> pixel >>> 24 > 0));
    }

    private static void successContract(final BrewKind kind) {
        final String itemId = BrewFactory.itemId(kind);
        assertEquals(kind, BrewFactory.requireKind(itemId));
        assertTrue(ContentCatalog.BREWS.contains(itemId));
        assertTrue(MOD_ITEMS_SOURCE.contains(
            "ContentCatalog.BREWS.forEach(id -> register(id, () -> BrewFactory.create(properties(id), id)))"
        ));
        final MachineRecipeDefinition recipe = recipe(itemId);
        assertEquals("kettle", recipe.machine());
        assertTrue(recipe.fluid().isPresent());
        assertEquals("#minecraft:water", recipe.fluid().orElseThrow().ingredient());
        assertTrue(recipe.inputs().stream().map(MachineRecipeDefinition.Input::ingredient)
            .anyMatch(ingredient -> ingredient.startsWith("#c:") || ingredient.startsWith("#minecraft:")));
        assertEquals("warlockery:" + itemId, recipe.outputs().getFirst().item());
        assertEquals(1, recipe.outputs().getFirst().count());
        assertTrue(kind.hasPotionEffects() || !kind.behaviors().isEmpty());
    }

    private static MachineRecipeDefinition recipe(final String itemId) {
        return java.util.Optional.ofNullable(ACQUISITION_RECIPES.get("kettle_" + itemId)).orElseThrow();
    }

    private static JsonObject readJson(final Path path) {
        return JsonParser.parseString(readText(path)).getAsJsonObject();
    }

    private static String readText(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static BufferedImage readImage(final Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
