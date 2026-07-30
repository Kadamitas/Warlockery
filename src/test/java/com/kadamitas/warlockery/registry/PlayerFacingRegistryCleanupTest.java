package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.item.ReplicationChargeItem;
import com.kadamitas.warlockery.item.SunGrenadeItem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

final class PlayerFacingRegistryCleanupTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path DATA = Path.of("src/main/resources/data");

    @Test
    void unusedGenericAndLegacyMeatItemsAreNotRegistered() {
        final Set<String> items = ContentCatalog.ITEMS.stream()
            .map(ContentCatalog::modernize)
            .collect(Collectors.toUnmodifiableSet());
        final Set<String> ingredients = ContentCatalog.INGREDIENTS.stream()
            .map(ContentCatalog::ingredientId)
            .collect(Collectors.toUnmodifiableSet());

        assertFalse(items.contains("brewbottle"));
        assertFalse(items.contains("ingredient"));
        assertFalse(items.contains("potion"));
        assertTrue(items.contains("biomenote"));
        assertFalse(CreativeInventoryCatalog.isVisible("biomenote"));
        assertFalse(ingredients.contains("ingredient_muttonraw"));
        assertFalse(ingredients.contains("ingredient_muttoncooked"));
    }

    @Test
    void wolfMeatSystemsUseVanillaMutton() {
        final String wolfMeats = read(DATA.resolve("warlockery/tags/item/wolf_form_meats.json"));
        final String altarOfferings = read(DATA.resolve("warlockery/tags/item/wolf_altar_offerings.json"));
        assertTrue(wolfMeats.contains("minecraft:mutton"));
        assertTrue(altarOfferings.contains("minecraft:mutton"));
        assertFalse(wolfMeats.contains("ingredient_mutton"));
        assertFalse(altarOfferings.contains("ingredient_mutton"));
    }

    @Test
    void familiarFoodsUseVanillaItemArt() {
        assertEquals("minecraft:item/porkchop", layer("ingredient_odd_porkchop_raw"));
        assertEquals("minecraft:item/cooked_porkchop", layer("ingredient_odd_porkchop_cooked"));
        assertEquals("minecraft:item/apple", layer("ingredient_sleeping_apple"));
    }

    @Test
    void throwableUtilityItemsOwnTheirDisplayNames() throws ReflectiveOperationException {
        assertEquals(SunGrenadeItem.class,
            SunGrenadeItem.class.getDeclaredMethod("getName", ItemStack.class).getDeclaringClass());
        assertEquals(ReplicationChargeItem.class,
            ReplicationChargeItem.class.getDeclaredMethod("getName", ItemStack.class).getDeclaringClass());
    }

    @Test
    void englishNamesArePlayerFacing() {
        final JsonObject language = JsonParser.parseString(read(ASSETS.resolve("lang/en_us.json"))).getAsJsonObject();
        assertEquals("Vampire Pants", language.get("item.warlockery.vampirelegs").getAsString());
        assertEquals("Sun Grenade", language.get("item.warlockery.sungrenade").getAsString());
        assertEquals("Replication Charge", language.get("item.warlockery.replication_charge").getAsString());
        assertEquals("Blank Biome Note", language.get("item.warlockery.biomenote").getAsString());
        assertEquals("Biome Note: %s", language.get("item.warlockery.biomenote.recorded").getAsString());
    }

    private static String layer(final String id) {
        return JsonParser.parseString(read(ASSETS.resolve("models/item/" + id + ".json")))
            .getAsJsonObject()
            .getAsJsonObject("textures")
            .get("layer0")
            .getAsString();
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
