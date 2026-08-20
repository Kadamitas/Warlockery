package com.kadamitas.warlockery.menu;

import com.kadamitas.warlockery.block.entity.DollShelfBlockEntity;
import com.kadamitas.warlockery.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class DollShelfMenu extends AbstractContainerMenu {
    private static final int SHELF_SLOTS = 9;
    public static final int SCREEN_WIDTH = 196;
    public static final int SCREEN_HEIGHT = 212;
    public static final int INVENTORY_X = 17;
    public static final int INVENTORY_Y = 132;
    public static final int SHELF_X = 62;
    public static final int SHELF_Y = 32;
    private final Container shelf;

    public static DollShelfMenu client(final int containerId, final Inventory inventory) {
        return new DollShelfMenu(containerId, inventory, new SimpleContainer(SHELF_SLOTS));
    }

    public DollShelfMenu(final int containerId, final Inventory inventory, final Container shelf) {
        super(ModMenus.DOLL_SHELF.get(), containerId);
        checkContainerSize(shelf, SHELF_SLOTS);
        this.shelf = shelf;
        shelf.startOpen(inventory.player);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                final int index = column + row * 3;
                addSlot(new ShelfSlot(shelf, index, SHELF_X + column * 24, SHELF_Y + row * 24));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                    inventory,
                    column + row * 9 + 9,
                    INVENTORY_X + column * 18,
                    INVENTORY_Y + row * 18
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 58));
        }
    }

    @Override
    public boolean stillValid(final Player player) {
        return shelf.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int slotIndex) {
        final Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack copy = stack.copy();
        final boolean moved = slotIndex < SHELF_SLOTS
            ? moveItemStackTo(stack, SHELF_SLOTS, slots.size(), true)
            : moveItemStackTo(stack, 0, SHELF_SLOTS, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public void removed(final Player player) {
        super.removed(player);
        shelf.stopOpen(player);
    }

    private static final class ShelfSlot extends Slot {
        private ShelfSlot(final Container container, final int slot, final int x, final int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(final ItemStack stack) {
            return container.canPlaceItem(getContainerSlot(), stack);
        }
    }
}
