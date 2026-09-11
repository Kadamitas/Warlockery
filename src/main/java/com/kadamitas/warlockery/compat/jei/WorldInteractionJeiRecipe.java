package com.kadamitas.warlockery.compat.jei;

import net.minecraft.resources.Identifier;

public record WorldInteractionJeiRecipe(Identifier id, String ingredient, String placedBlock, String result) {
    public static final WorldInteractionJeiRecipe ANOINT_CAULDRON = new WorldInteractionJeiRecipe(
        Identifier.fromNamespaceAndPath("warlockery", "anoint_cauldron"),
        "warlockery:ingredient_annointing_paste", "minecraft:cauldron", "warlockery:cauldron");
}
