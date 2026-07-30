package com.kadamitas.warlockery.brew.custom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.brew.BrewBehavior;
import com.kadamitas.warlockery.brew.BrewEffectSpec;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CustomBrewInputAcceptanceTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void clearComponentCatalog() {
        CustomBrewDefinitionManager.INSTANCE.apply(Map.of(), null, null);
    }

    @Test
    void cauldronAcceptsReloadedCustomComponentsOnlyWhileTheyAreDefined() {
        final CustomBrewComponentDefinition component = new CustomBrewComponentDefinition(
            "minecraft:sugar",
            CustomBrewComponentRole.EFFECT,
            0,
            1.0F,
            CustomBrewModifier.NONE,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            List.of(new BrewEffectSpec("minecraft:speed", 200, 0)),
            List.of(BrewBehavior.PUSH),
            1,
            0x55AAFF,
            0
        );
        CustomBrewDefinitionManager.INSTANCE.apply(
            Map.of(Identifier.parse("warlockery:test_sugar_component"), component),
            null,
            null
        );

        final var cauldron = MachineProfiles.forBlock("cauldron");
        assertTrue(MachineRecipeManager.INSTANCE.acceptsInput(cauldron, stack(Items.SUGAR)));
        assertFalse(MachineRecipeManager.INSTANCE.acceptsInput(cauldron, stack(Items.IRON_SWORD)));

        CustomBrewDefinitionManager.INSTANCE.apply(Map.of(), null, null);
        assertFalse(MachineRecipeManager.INSTANCE.acceptsInput(cauldron, stack(Items.SUGAR)));
    }

    private static ItemStack stack(final net.minecraft.world.level.ItemLike item) {
        return new ItemStack(Holder.direct(item.asItem()));
    }
}
