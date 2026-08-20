package com.kadamitas.warlockery.menu;

import com.kadamitas.warlockery.block.entity.MagicMachineBlockEntity;
import com.kadamitas.warlockery.registry.ModMenus;
import com.kadamitas.warlockery.crafting.MachineStatus;
import com.kadamitas.warlockery.crafting.PowerMode;
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
    private static final int DISPLAY_VALUES = 12;
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
        machineData = machine instanceof MagicMachineBlockEntity blockEntity
            ? new SyncedMachineData(blockEntity)
            : new SimpleContainerData(DISPLAY_VALUES);
        addDataSlots(machineData);
        machine.startOpen(inventory.player);
        final MachineUiLayout layout = MachineUiLayout.forKind(kind);
        for (int index = 0; index < MACHINE_SLOTS; index++) {
            final MachineUiLayout.SlotPosition position = layout.slots().get(index);
            addSlot(new RestrictedSlot(machine, index, position.x(), position.y()));
        }
        addPlayerInventory(inventory, layout);
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

    public MachineStatus status() {
        final MachineStatus[] statuses = MachineStatus.values();
        return statuses[Math.clamp(machineData.get(1), 0, statuses.length - 1)];
    }

    public int fluidAmount() {
        return Math.clamp(machineData.get(2), 0, 4_000);
    }

    public int availableAltarPower() {
        return combineWords(machineData.get(3), machineData.get(4));
    }

    public int requiredAltarPower() {
        return combineWords(machineData.get(5), machineData.get(6));
    }

    public int totalAltarPower() {
        return combineWords(machineData.get(7), machineData.get(8));
    }

    public int altarMillipowerPerTick() {
        return combineWords(machineData.get(9), machineData.get(10));
    }

    public PowerMode powerMode() {
        final PowerMode[] modes = PowerMode.values();
        return modes[Math.clamp(machineData.get(11), 0, modes.length - 1)];
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

    private void addPlayerInventory(final Inventory inventory, final MachineUiLayout layout) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                    inventory,
                    column + row * 9 + 9,
                    layout.inventoryX() + column * 18,
                    layout.inventoryY() + row * 18
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                inventory,
                column,
                layout.inventoryX() + column * 18,
                layout.inventoryY() + 58
            ));
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

    static int lowWord(final int value) {
        return Math.max(0, value) & 0xFFFF;
    }

    static int highWord(final int value) {
        return Math.max(0, value) >>> 16 & 0xFFFF;
    }

    static int combineWords(final int low, final int high) {
        return Math.max(0, (low & 0xFFFF) | (high & 0xFFFF) << 16);
    }

    private static final class SyncedMachineData implements ContainerData {
        private final MagicMachineBlockEntity machine;
        private long snapshotTick = Long.MIN_VALUE;
        private MagicMachineBlockEntity.MachineMenuSnapshot snapshot;

        private SyncedMachineData(final MagicMachineBlockEntity machine) {
            this.machine = machine;
        }

        @Override
        public int get(final int index) {
            final MagicMachineBlockEntity.MachineMenuSnapshot current = snapshot();
            return switch (index) {
                case 0 -> current.progressPercent();
                case 1 -> current.statusOrdinal();
                case 2 -> current.fluidAmount();
                case 3 -> lowWord(current.availableAltarPower());
                case 4 -> highWord(current.availableAltarPower());
                case 5 -> lowWord(current.requiredAltarPower());
                case 6 -> highWord(current.requiredAltarPower());
                case 7 -> lowWord(current.totalAltarPower());
                case 8 -> highWord(current.totalAltarPower());
                case 9 -> lowWord(current.altarMillipowerPerTick());
                case 10 -> highWord(current.altarMillipowerPerTick());
                case 11 -> current.powerModeOrdinal();
                default -> 0;
            };
        }

        @Override
        public void set(final int index, final int value) {
        }

        @Override
        public int getCount() {
            return DISPLAY_VALUES;
        }

        private MagicMachineBlockEntity.MachineMenuSnapshot snapshot() {
            final long gameTime = machine.getLevel() == null
                ? Long.MIN_VALUE
                : machine.getLevel().getGameTime();
            if (snapshot == null || snapshotTick != gameTime) {
                snapshot = machine.getMachineMenuSnapshot();
                snapshotTick = gameTime;
            }
            return snapshot;
        }
    }
}
