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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

final class BroomPresentationAssetTest {
    private static final Path PLAIN_ITEM_DEFINITION = Path.of(
        "src/main/resources/assets/warlockery/items/ingredient_broom.json"
    );
    private static final Path ITEM_DEFINITION = Path.of(
        "src/main/resources/assets/warlockery/items/ingredient_broom_enchanted.json"
    );
    private static final Path PLAIN_MODEL = Path.of(
        "src/main/resources/assets/warlockery/models/item/ingredient_broom.json"
    );
    private static final Path MODEL = Path.of(
        "src/main/resources/assets/warlockery/models/item/ingredient_broom_enchanted.json"
    );
    private static final Path RENDERER = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/BroomEntityRenderer.java"
    );
    private static final Path CLIENT = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/WarlockeryClient.java"
    );

    @Test
    void enchantedBroomUsesTheSculptedModelInsteadOfAFlatSprite() throws IOException {
        final JsonObject definition = read(ITEM_DEFINITION);
        final JsonObject model = definition.getAsJsonObject("model");
        assertEquals("minecraft:model", model.get("type").getAsString());
        assertEquals("warlockery:item/ingredient_broom_enchanted", model.get("model").getAsString());

        final JsonObject sculpted = read(MODEL);
        assertEquals("minecraft:block/block", sculpted.get("parent").getAsString());
        assertFalse(sculpted.get("parent").getAsString().contains("generated"));
        assertEquals(
            Set.of("handle", "collar", "bristle_core", "bristle_left", "bristle_right", "bristle_tip"),
            StreamSupport.stream(sculpted.getAsJsonArray("elements").spliterator(), false)
                .map(element -> element.getAsJsonObject().get("name").getAsString())
                .collect(Collectors.toSet())
        );
    }

    @Test
    void plainAndEnchantedBroomsShareTheLongSculptedPresentation() throws IOException {
        final JsonObject definition = read(PLAIN_ITEM_DEFINITION).getAsJsonObject("model");
        assertEquals("minecraft:model", definition.get("type").getAsString());
        assertEquals("warlockery:item/ingredient_broom", definition.get("model").getAsString());
        assertEquals(
            "warlockery:item/ingredient_broom_enchanted",
            read(PLAIN_MODEL).get("parent").getAsString()
        );
    }

    @Test
    void broomSilhouetteIsLongAndSlimWithAFullThreeDimensionalHead() throws IOException {
        final JsonArray elements = read(MODEL).getAsJsonArray("elements");
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (final var element : elements) {
            final JsonObject cuboid = element.getAsJsonObject();
            final JsonArray from = cuboid.getAsJsonArray("from");
            final JsonArray to = cuboid.getAsJsonArray("to");
            minX = Math.min(minX, from.get(0).getAsDouble());
            minY = Math.min(minY, from.get(1).getAsDouble());
            minZ = Math.min(minZ, from.get(2).getAsDouble());
            maxX = Math.max(maxX, to.get(0).getAsDouble());
            maxY = Math.max(maxY, to.get(1).getAsDouble());
            maxZ = Math.max(maxZ, to.get(2).getAsDouble());
            assertEquals(6, cuboid.getAsJsonObject("faces").size(), cuboid.get("name").getAsString());
        }

        final double width = maxX - minX;
        final double height = maxY - minY;
        final double length = maxZ - minZ;
        assertTrue(length >= width * 3.0, "broom must read as a long vehicle rather than a cube");
        assertTrue(length >= height * 6.0, "broom must remain thin beneath its rider");
        assertTrue(width > height, "bristle head must be wider than the handle profile");
    }

    @Test
    void modelUsesPlainVanillaMaterialTexturesAndEveryDisplayContext() throws IOException {
        final JsonObject model = read(MODEL);
        final JsonObject textures = model.getAsJsonObject("textures");
        assertEquals("minecraft:block/stripped_spruce_log", textures.get("handle").getAsString());
        assertEquals("minecraft:block/copper_block", textures.get("binding").getAsString());
        assertEquals("minecraft:block/hay_block_side", textures.get("bristles").getAsString());

        final JsonObject display = model.getAsJsonObject("display");
        assertTrue(display.keySet().containsAll(Set.of(
            "gui",
            "ground",
            "fixed",
            "thirdperson_righthand",
            "thirdperson_lefthand",
            "firstperson_righthand",
            "firstperson_lefthand"
        )));
    }

    @Test
    void inventoryAndHandViewsAreQuarterTurnedWithoutChangingMountedGroundOrientation() throws IOException {
        final JsonObject display = read(MODEL).getAsJsonObject("display");
        assertEquals(72, rotation(display, "gui", 2));
        assertEquals(55, rotation(display, "thirdperson_righthand", 2));
        assertEquals(-55, rotation(display, "thirdperson_lefthand", 2));
        assertEquals(62, rotation(display, "firstperson_righthand", 2));
        assertEquals(-62, rotation(display, "firstperson_lefthand", 2));
        assertEquals(0, rotation(display, "ground", 2));
    }

    @Test
    void vehicleRendererUsesTheSynchronizedBroomStackAndRiderRotation() throws IOException {
        final String renderer = Files.readString(RENDERER);
        final String client = Files.readString(CLIENT);

        assertTrue(renderer.contains("entity.getBroomStack()"));
        assertTrue(renderer.contains("entity.getYRot(partialTicks)"));
        assertTrue(renderer.contains("entity.getXRot(partialTicks)"));
        assertTrue(renderer.contains("entity.isGliding()"));
        assertTrue(renderer.contains("ItemDisplayContext.GROUND"));
        assertFalse(renderer.contains("getDefaultInstance()"));
        assertTrue(client.contains("event.registerEntityRenderer(ModEntities.BROOM.get(), BroomEntityRenderer::new)"));
    }

    private static JsonObject read(final Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static int rotation(final JsonObject display, final String context, final int axis) {
        return display.getAsJsonObject(context).getAsJsonArray("rotation").get(axis).getAsInt();
    }
}
