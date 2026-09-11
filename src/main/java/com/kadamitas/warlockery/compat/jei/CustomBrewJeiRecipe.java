package com.kadamitas.warlockery.compat.jei;

import com.kadamitas.warlockery.brew.custom.CustomBrewComponentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public record CustomBrewJeiRecipe(Identifier id, CustomBrewComponentDefinition definition) {
    public Component role() {
        return Component.translatable("jei.warlockery.custom.role." + definition.role().id());
    }

    public Component details() {
        String key = "jei.warlockery.custom.detail." + definition.role().id();
        return switch (definition.role()) {
            case CAPACITY, POWER, EXTENT, LINGERING -> Component.translatable(key, definition.value());
            case DURATION -> Component.translatable(key, definition.multiplier());
            case EFFECT -> Component.translatable(key, definition.capacityCost());
            case MODIFIER -> Component.translatable(key, Component.translatable(
                "jei.warlockery.custom.modifier." + definition.modifier().id(), definition.value()));
            case DELIVERY -> Component.translatable(key, Component.translatable(
                "custom_brew_delivery.warlockery." + definition.delivery().orElseThrow().id()));
            case CONTAINER -> Component.translatable(key);
        };
    }
}
