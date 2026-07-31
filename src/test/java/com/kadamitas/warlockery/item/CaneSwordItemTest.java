package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ToolMaterial;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CaneSwordItemTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void caneModeUsesPunchDamageAndBoostsWalkingSpeed() {
        assertEquals(1.0, CaneSwordItem.attackDamage(false));
        assertEquals(0.115, CaneSwordItem.movementSpeed(false, 0.1), 0.000_001);
        assertEquals(CaneSwordItem.CANE_MODEL, CaneSwordItem.model(false));
        assertEquals(ToolMaterial.IRON.durability(), CaneSwordItem.DURABILITY);
    }

    @Test
    void drawnModeRemovesSpeedAndDealsSevenTotalDamage() {
        assertEquals(7.0, CaneSwordItem.attackDamage(true));
        assertEquals(0.1, CaneSwordItem.movementSpeed(true, 0.1), 0.000_001);
        assertEquals(1.6, CaneSwordItem.attributes(true).compute(
            Attributes.ATTACK_SPEED,
            4.0,
            EquipmentSlot.MAINHAND
        ), 0.000_001);
        assertEquals(CaneSwordItem.DRAWN_MODEL, CaneSwordItem.model(true));
    }

    @Test
    void customDataPersistsAndClearsTheDrawnState() {
        final CompoundTag state = new CompoundTag();

        CaneSwordItem.writeState(state, true);
        assertTrue(CaneSwordItem.isDrawn(state));
        assertTrue(CaneSwordItem.isDrawn(state.copy()));

        CaneSwordItem.writeState(state, false);
        assertFalse(CaneSwordItem.isDrawn(state));
        assertTrue(state.isEmpty());
    }

    @Test
    void registryUsesTheStatefulItemAndItsModeSpecificProperties() throws IOException {
        final String registry = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/registry/ModItems.java"
        ));
        assertTrue(registry.contains("new CaneSwordItem(properties(id))"));
        assertTrue(registry.contains("case \"canesword\" -> CaneSwordItem.applyProperties(properties)"));
        assertFalse(registry.contains("case \"ritual_knife\", \"boline\", \"canesword\""));
        final String item = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/item/CaneSwordItem.java"
        ));
        assertTrue(item.contains("InteractionResult use(final Level level"));
        assertTrue(item.contains("InteractionResult useOn(final UseOnContext context)"));
        assertTrue(item.contains("player.isShiftKeyDown()"));
    }

    @Test
    void bothFormsHaveDedicatedItemDefinitionsModelsAndSprites() throws IOException {
        final Set<String> forms = Set.of("canesword", "canesword_drawn");
        for (final String form : forms) {
            final JsonObject definition = json(ASSETS.resolve("items/" + form + ".json"));
            assertEquals("warlockery:item/" + form, definition.getAsJsonObject("model").get("model").getAsString());
            final JsonObject model = json(ASSETS.resolve("models/item/" + form + ".json"));
            assertEquals("minecraft:item/generated", model.get("parent").getAsString());
            assertEquals(
                "warlockery:item/" + form,
                model.getAsJsonObject("textures").get("layer0").getAsString()
            );
            final BufferedImage image = ImageIO.read(ASSETS.resolve("textures/item/" + form + ".png").toFile());
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
        }
        final byte[] cane = Files.readAllBytes(ASSETS.resolve("textures/item/canesword.png"));
        final byte[] sword = Files.readAllBytes(ASSETS.resolve("textures/item/canesword_drawn.png"));
        assertNotEquals(java.util.Arrays.hashCode(cane), java.util.Arrays.hashCode(sword));
    }

    @Test
    void everySupportedLanguageExplainsBothToggleStates() throws IOException {
        for (final String locale : Set.of("en_us", "fr_fr", "es_es", "pt_br", "de_de", "pl_pl", "ja_jp", "zh_tw")) {
            final JsonObject language = json(ASSETS.resolve("lang/" + locale + ".json"));
            assertTrue(language.has("message.warlockery.cane_sword.drawn"), locale);
            assertTrue(language.has("message.warlockery.cane_sword.sheathed"), locale);
        }
    }

    private static JsonObject json(final Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
