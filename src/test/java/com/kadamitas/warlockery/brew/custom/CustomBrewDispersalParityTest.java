package com.kadamitas.warlockery.brew.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.brew.BrewBehavior;
import com.kadamitas.warlockery.brew.BrewEffectSpec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CustomBrewDispersalParityTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyLegacyDispersalMethodHasAStableCodecIdentity() {
        assertEquals(
            List.of("drinkable", "throwable", "gas", "liquid", "trigger"),
            java.util.Arrays.stream(CustomBrewDelivery.values()).map(CustomBrewDelivery::id).toList()
        );
        java.util.Arrays.stream(CustomBrewDelivery.values()).forEach(delivery -> {
            final var encoded = CustomBrewDelivery.CODEC.encodeStart(JsonOps.INSTANCE, delivery).getOrThrow();
            assertEquals(delivery, CustomBrewDelivery.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        });
    }

    @Test
    void gasAndLiquidAreLingeringWhileTriggersArmBlocks() {
        assertTrue(CustomBrewDelivery.GAS.lingering());
        assertTrue(CustomBrewDelivery.LIQUID.lingering());
        assertFalse(CustomBrewDelivery.TRIGGER.lingering());
        assertTrue(CustomBrewDelivery.TRIGGER.triggered());
        assertEquals(6.0F, CustomBrewDelivery.GAS.cloudRadius(6.0F));
        assertEquals(4.8F, CustomBrewDelivery.LIQUID.cloudRadius(6.0F));
        assertEquals(1_600, CustomBrewDelivery.GAS.cloudDuration(4));
        assertEquals(3_200, CustomBrewDelivery.LIQUID.cloudDuration(4));
    }

    @Test
    void triggerDispersalSupportsRedstoneControlsAndStoresCharges() {
        assertTrue(CustomBrewTriggerData.supports(Blocks.STONE_BUTTON.defaultBlockState()));
        assertTrue(CustomBrewTriggerData.supports(Blocks.LEVER.defaultBlockState()));
        assertTrue(CustomBrewTriggerData.supports(Blocks.OAK_DOOR.defaultBlockState()));
        assertTrue(CustomBrewTriggerData.supports(Blocks.STONE_PRESSURE_PLATE.defaultBlockState()));
        assertFalse(CustomBrewTriggerData.supports(Blocks.STONE.defaultBlockState()));
        final var trigger = new CustomBrewTriggerData.ArmedTrigger(1L, formula(), 1, false);
        assertEquals(2, trigger.addCharge().charges());
        assertEquals(1, trigger.addCharge().consume().charges());
        assertTrue(trigger.withActive(true).active());
    }

    @Test
    void triggerOwnerSurvivesChargesStateChangesAndCodecRoundTrip() {
        final UUID owner = UUID.fromString("3b241101-e2bb-4255-8caf-4136c566a962");
        final var trigger = new CustomBrewTriggerData.ArmedTrigger(
            42L,
            formula(),
            3,
            true,
            Optional.of(owner)
        );
        assertEquals(Optional.of(owner), trigger.addCharge().consume().withActive(false).owner());
        final var encoded = CustomBrewTriggerData.ArmedTrigger.CODEC
            .encodeStart(JsonOps.INSTANCE, trigger)
            .getOrThrow();
        assertEquals(
            trigger,
            CustomBrewTriggerData.ArmedTrigger.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow()
        );
    }

    @Test
    void cloudReapplicationAndGasImmunityAreEnforcedBeforeDelivery() {
        assertTrue(CustomBrewCloudRules.ready(20L, 20L));
        assertFalse(CustomBrewCloudRules.ready(19L, 20L));
        assertEquals(40L, CustomBrewCloudRules.nextApplicationTime(20L));
        assertTrue(CustomBrewCloudRules.blocksDelivery(CustomBrewDelivery.GAS, true));
        assertFalse(CustomBrewCloudRules.blocksDelivery(CustomBrewDelivery.GAS, false));
        assertFalse(CustomBrewCloudRules.blocksDelivery(CustomBrewDelivery.LIQUID, true));
    }

    @Test
    void lingeringReapplicationIncludesEntityBehaviorsWithoutRepeatingBlockOnlyBehaviors() {
        final CustomBrewFormula formula = formula(
            List.of(BrewBehavior.PUSH, BrewBehavior.PLACE_WATER),
            false
        );
        assertEquals(List.of(BrewBehavior.PUSH), formula.entityBehaviorKind().behaviors());
        assertTrue(formula(List.of(BrewBehavior.PUSH), true).entityBehaviorKind().behaviors().isEmpty());
    }

    @Test
    void ingredientTagsAndDeliveryDefinitionsMatchTheManualComponents() throws IOException {
        assertDelivery("gas", "warlockery:ingredient_bat_wool");
        assertDelivery("liquid", "warlockery:ingredient_wormwood");
        assertDelivery("trigger", "minecraft:zombie_head");
    }

    private static void assertDelivery(final String id, final String ingredient) throws IOException {
        final Path definition = DATA.resolve("custom_brew_component").resolve("delivery_" + id + ".json");
        final JsonObject object = JsonParser.parseString(Files.readString(definition)).getAsJsonObject();
        assertEquals(id, object.get("delivery").getAsString());
        final Path tag = DATA.resolve("tags/item/custom_brew/delivery").resolve(id + ".json");
        final JsonObject tagObject = JsonParser.parseString(Files.readString(tag)).getAsJsonObject();
        assertEquals(ingredient, tagObject.getAsJsonArray("values").get(0).getAsString());
    }

    private static CustomBrewFormula formula() {
        return formula(List.of(BrewBehavior.PUSH), false);
    }

    private static CustomBrewFormula formula(
        final List<BrewBehavior> behaviors,
        final boolean skipEntities
    ) {
        return new CustomBrewFormula(
            List.of("trigger", "effect"),
            List.of("speed"),
            CustomBrewDelivery.TRIGGER,
            List.of(new BrewEffectSpec("minecraft:speed", 200, 0)),
            behaviors,
            4,
            2,
            1,
            1,
            1,
            1,
            0,
            0x55AAFF,
            4.0F,
            1.0F,
            false,
            false,
            skipEntities,
            false,
            0
        );
    }
}
