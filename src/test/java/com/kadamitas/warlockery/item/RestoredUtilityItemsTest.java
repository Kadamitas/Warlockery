package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.registry.ContentCatalog;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class RestoredUtilityItemsTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path DATA = Path.of("src/main/resources/data");
    private static final Set<String> IDS = Set.of(
        "hellhound_head",
        "ingredient_soul_of_torment",
        "ingredient_infernal_animus",
        "twisting_band",
        "ingredient_woven_cruor"
    );

    @Test
    void everyRestoredItemIsRegistered() {
        final Set<String> catalog = java.util.stream.Stream.concat(
            ContentCatalog.ITEMS.stream().map(ContentCatalog::modernize),
            ContentCatalog.INGREDIENTS.stream().map(ContentCatalog::ingredientId)
        ).collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertTrue(catalog.containsAll(IDS));
        assertTrue(UtilityItemFactory.supports("ingredient_soul_of_torment"));
        assertTrue(UtilityItemFactory.supports("ingredient_infernal_animus"));
    }

    @Test
    void gazeGeometryHasExplicitFailureAndSuccessBoundaries() {
        assertTrue(GazeGeometry.faces(new Vec3(0, 0, 1), new Vec3(0, 0, 4), 0.92));
        assertFalse(GazeGeometry.faces(new Vec3(0, 0, 1), new Vec3(4, 0, 0), 0.92));
        assertFalse(GazeGeometry.faces(Vec3.ZERO, new Vec3(0, 0, 1), 0.92));
    }

    @Test
    void banishmentDestinationsAreStableAndTargetSpecific() {
        final UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        final UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertEquals(AbyssalBanishment.arrivalFor(first), AbyssalBanishment.arrivalFor(first));
        assertNotEquals(AbyssalBanishment.arrivalFor(first), AbyssalBanishment.arrivalFor(second));
        assertEquals(64, AbyssalBanishment.arrivalFor(first).getY());
    }

    @Test
    void abyssDimensionUsesARegisteredTypeAndNetherNoiseSettings() {
        final JsonObject dimension = readJson(DATA.resolve("warlockery/dimension/abyss.json"));
        assertEquals("warlockery:abyss", dimension.get("type").getAsString());
        assertEquals(
            "minecraft:nether",
            dimension.getAsJsonObject("generator").get("settings").getAsString()
        );
        assertTrue(Files.isRegularFile(DATA.resolve("warlockery/dimension_type/abyss.json")));
    }

    @Test
    void restoredItemsUsePrivateExtensionTagsAndCanonicalArmorTags() {
        assertTrue(tag("warlockery/tags/item/twisting_bands.json").contains("warlockery:twisting_band"));
        assertTrue(tag("warlockery/tags/item/torment_souls.json").contains("warlockery:ingredient_soul_of_torment"));
        assertTrue(tag("warlockery/tags/item/infernal_animus.json").contains("warlockery:ingredient_infernal_animus"));
        assertTrue(tag("warlockery/tags/item/woven_cruor.json").contains("warlockery:ingredient_woven_cruor"));
        assertTrue(tag("minecraft/tags/item/head_armor.json").containsAll(Set.of(
            "warlockery:hellhound_head",
            "warlockery:twisting_band"
        )));
        assertTrue(tag("c/tags/item/armors/humanoid.json").containsAll(Set.of(
            "warlockery:hellhound_head",
            "warlockery:twisting_band"
        )));
    }

    @Test
    void infernalInfusionConsumesTheAnimus() {
        final String ritual = read(DATA.resolve("warlockery/ritual/infusion_hell.json"));
        assertTrue(ritual.contains("warlockery:ingredient_infernal_animus"));
    }

    @Test
    void everyNewIconIsOriginalSizedAndTransparent() {
        IDS.stream().filter(id -> !id.equals("ingredient_infernal_animus")).forEach(id -> {
            final BufferedImage image = readImage(ASSETS.resolve("textures/item/" + id + ".png"));
            assertEquals(16, image.getWidth(), id);
            assertEquals(16, image.getHeight(), id);
            assertTrue(java.util.stream.IntStream.range(0, 256)
                .anyMatch(index -> image.getRGB(index % 16, index / 16) >>> 24 == 0), id);
            assertTrue(java.util.stream.IntStream.range(0, 256)
                .anyMatch(index -> image.getRGB(index % 16, index / 16) >>> 24 > 0), id);
        });
    }

    private static Set<String> tag(final String path) {
        return readJson(DATA.resolve(path)).getAsJsonArray("values").asList().stream()
            .map(value -> value.getAsString())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
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

    private static BufferedImage readImage(final Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
