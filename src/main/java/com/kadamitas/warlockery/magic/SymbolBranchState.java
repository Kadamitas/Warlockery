package com.kadamitas.warlockery.magic;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class SymbolBranchState {
    private static final String SELECTED = "WarlockerySymbolSpell";
    private static final String UNLOCK_PREFIX = "WarlockerySymbolUnlocked_";

    private SymbolBranchState() {
    }

    public static SymbolSpell selected(final ItemStack stack) {
        return selected(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    static SymbolSpell selected(final CompoundTag data) {
        return SymbolSpell.find(data.getStringOr(SELECTED, SymbolSpell.WITCHLIGHT.id())).orElse(SymbolSpell.WITCHLIGHT);
    }

    public static SymbolSpell cycle(final ItemStack stack) {
        final SymbolSpell next = selected(stack).next();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.putString(SELECTED, next.id()));
        return next;
    }

    public static boolean unlocked(final ItemStack stack, final SymbolSpell spell) {
        return spell.soulIngredient().isEmpty() || stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag().getBooleanOr(UNLOCK_PREFIX + spell.id(), false);
    }

    public static void unlock(final ItemStack stack, final SymbolSpell spell) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data ->
            data.putBoolean(UNLOCK_PREFIX + spell.id(), true)
        );
    }
}
