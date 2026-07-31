package com.kadamitas.warlockery.menu;

import com.kadamitas.warlockery.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class MachineMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOTS = 9;
    private final Container machine;
    private final String kind;
    private final ContainerData machineData;

    public static MachineMenu client(final String kind, final int containerId, final Inventory inventory) {
        return new MachineMenu(containerId, inventory, new SimpleContainer(MACHINE_SLOTS), kind);
    }

    public MachineMenu(
        final int containerId,
        final Inventory inventory,
        final Container machine,
        final String kind
    ) {
        super(ModMenus.machine(kind).get(), containerId);
        checkContainerSize(machine, MACHINE_SLOTS);
        this.machine = machine;
        this.kind = kind;
        machineData = machine instanceof com.kadamitas.warlockery.block.entity.MagicMachineBlockEntity blockEntity
            ? new ContainerData() {
                @Override
                public int get(final int index) {
                    return switch (index) {
                        case 0 -> blockEntity.getMachineDisplay().progressPercent();
                        case 1 -> blockEntity.getMachineDisplay().status().ordinal();
                        default -> 0;
                    };
                }

                @Override
                public void set(final int index, final int value) {
                }

                @Override
                public int getCount() {
                    return 2;
                }
            }
            : new SimpleContainerData(2);
        addDataSlots(machineData);
        machine.startOpen(inventory.player);
        final MachineUiLayout layout = MachineUiLayout.forKind(kind);
        for (int index = 0; index < MACHINE_SLOTS; index++) {
            final MachineUiLayout.SlotPosition position = layout.slots().get(index);
            addSlot(new RestrictedSlot(machine, index, position.x(), position.y()));
        }
        addPlayerInventory(inventory);
    }

    public String kind() {
        return kind;
    }

    public MachineUiLayout layout() {
        return MachineUiLayout.forKind(kind);
    }

    public int progressPercent() {
        return Math.clamp(machineData.get(0), 0, 100);
    }

    @Override
    public boolean stillValid(final Player player) {
        return machine.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int slotIndex) {
        final Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack copy = stack.copy();
        final boolean moved = slotIndex < MACHINE_SLOTS
            ? moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)
            : moveItemStackTo(stack, 0, MACHINE_SLOTS, false);
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
        machine.stopOpen(player);
    }

    private void addPlayerInventory(final Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 103 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 161));
        }
    }

    private static final class RestrictedSlot extends Slot {
        private RestrictedSlot(final Container container, final int slot, final int x, final int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(final ItemStack stack) {
            return container.canPlaceItem(getContainerSlot(), stack);
        }
    }
}
