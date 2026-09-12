package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ParasyticLouseItemTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aFreshOrSpentLouseHasNoStoredEffect() {
        final ItemStack stack = new ItemStack(Holder.direct(Items.PAPER));
        assertTrue(ParasyticLouseItem.storedEffect(stack).isEmpty(), "fresh item data must permit its first potion");
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
            data.putString("UnrelatedNote", "preserved");
            data.putInt("WarlockeryLouseDuration", 1200);
            data.putInt("WarlockeryLouseAmplifier", 2);
        });
        assertTrue(ParasyticLouseItem.storedEffect(stack).isEmpty(), "leftover values cannot invent a missing effect");
        assertEquals("preserved", stack.get(DataComponents.CUSTOM_DATA).copyTag().getStringOr("UnrelatedNote", ""));
    }

    @Test
    void blankAndMalformedSavedEffectIdsAreEmpty() {
        for (final String encoded : List.of("", " ", "minecraft:", ":", "Minecraft:speed", "bad effect")) {
            final ItemStack stack = new ItemStack(Holder.direct(Items.PAPER));
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.putString("WarlockeryLouseEffect", encoded));
            assertTrue(ParasyticLouseItem.storedEffect(stack).isEmpty(), "invalid saved effect must not block potion loading: " + encoded);
        }
    }

    @Test
    void aValidPotionRetainsItsEffectStrengthAndDuration() {
        final ItemStack stack = new ItemStack(Holder.direct(Items.PAPER));
        ParasyticLouseItem.storeEffect(stack, new MobEffectInstance(MobEffects.SPEED, 1234, 2));
        final var stored = ParasyticLouseItem.storedEffect(stack).orElseThrow();
        assertEquals("minecraft:speed", stored.effectId().toString());
        assertEquals(1234, stored.durationTicks());
        assertEquals(2, stored.amplifier());
    }
}
