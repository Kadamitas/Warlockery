package com.kadamitas.warlockery.compat.rrv;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

record WarlockeryRrvType(String path, Component title, List<ItemStack> stations) implements ReliableClientRecipeType {
    static final int WIDTH = 236;
    static final int HEIGHT = 172;
    static final int INPUTS = 8;
    static final int OUTPUT_START = 8;
    static final int TOOL_START = 12;

    @Override public Identifier getId() { return Identifier.fromNamespaceAndPath("warlockery", path); }
    @Override public Component getDisplayName() { return title; }
    @Override public int getDisplayWidth() { return WIDTH; }
    @Override public int getDisplayHeight() { return HEIGHT; }
    @Override public Identifier getGuiTexture() { return null; }
    @Override public int getSlotCount() { return 20; }
    @Override public ItemStack getIcon() { return stations.isEmpty() ? ItemStack.EMPTY : stations.getFirst(); }
    @Override public List<ItemStack> getCraftReferences() { return stations; }
    @Override public void placeSlots(RecipeViewMenu.SlotDefinition slots) {
        for (int i = 0; i < INPUTS; i++) slots.addItemSlot(i, 4 + i % 4 * 20, 4 + i / 4 * 20);
        for (int i = 0; i < 4; i++) slots.addItemSlot(OUTPUT_START + i, 156 + i * 20, 14);
        for (int i = 0; i < 8; i++) slots.addItemSlot(TOOL_START + i, 4 + i * 20, 46);
    }
}
