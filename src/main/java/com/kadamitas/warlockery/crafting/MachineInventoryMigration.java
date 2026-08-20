package com.kadamitas.warlockery.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.IntStream;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/** Lossless upgrade path from the pre-1.5 shared nine-slot machine layout. */
public final class MachineInventoryMigration {
    public static final int CURRENT_VERSION = 1;
    private static final int INVENTORY_SIZE = 9;

    private MachineInventoryMigration() {
    }

    public static Result migrate(
        final String machine,
        final int storedVersion,
        final List<ItemStack> stored,
        final BiPredicate<Integer, ItemStack> acceptsInput
    ) {
        final NonNullList<ItemStack> source = copyInventory(stored);
        if (storedVersion >= CURRENT_VERSION) {
            return new Result(source, List.of(), false);
        }
        return switch (machine) {
            case "spinningwheel" -> remap(source, range(0, 6), range(6, 9), range(0, 4), new int[] {4},
                acceptsInput);
            case "distillery" -> remap(source, range(0, 5), range(5, 9), range(0, 3), range(3, 7),
                acceptsInput);
            case "brazier" -> remap(source, range(0, 6), range(6, 9), range(0, 3), new int[] {3},
                acceptsInput);
            default -> new Result(source, List.of(), false);
        };
    }

    private static Result remap(
        final NonNullList<ItemStack> source,
        final int[] oldInputs,
        final int[] oldOutputs,
        final int[] newInputs,
        final int[] newOutputs,
        final BiPredicate<Integer, ItemStack> acceptsInput
    ) {
        final NonNullList<ItemStack> migrated = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        final List<ItemStack> overflow = new ArrayList<>();
        for (int slot : oldOutputs) {
            place(source.get(slot), newOutputs, migrated, (_, _) -> true, overflow);
        }
        for (int slot : oldInputs) {
            place(source.get(slot), newInputs, migrated, acceptsInput, overflow);
        }
        return new Result(migrated, overflow, true);
    }

    private static void place(
        final ItemStack original,
        final int[] targets,
        final NonNullList<ItemStack> migrated,
        final BiPredicate<Integer, ItemStack> accepts,
        final List<ItemStack> overflow
    ) {
        if (original.isEmpty()) {
            return;
        }
        final ItemStack remaining = original.copy();
        for (int slot : targets) {
            final ItemStack present = migrated.get(slot);
            if (!accepts.test(slot, remaining) || present.isEmpty()
                || !ItemStack.isSameItemSameComponents(present, remaining)) {
                continue;
            }
            final int moved = Math.min(remaining.getCount(), present.getMaxStackSize() - present.getCount());
            if (moved > 0) {
                present.grow(moved);
                remaining.shrink(moved);
            }
            if (remaining.isEmpty()) {
                return;
            }
        }
        for (int slot : targets) {
            if (!migrated.get(slot).isEmpty() || !accepts.test(slot, remaining)) {
                continue;
            }
            migrated.set(slot, remaining.split(Math.min(remaining.getCount(), remaining.getMaxStackSize())));
            if (remaining.isEmpty()) {
                return;
            }
        }
        if (!remaining.isEmpty()) {
            overflow.add(remaining.copy());
        }
    }

    private static NonNullList<ItemStack> copyInventory(final List<ItemStack> stored) {
        final NonNullList<ItemStack> copy = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        IntStream.range(0, Math.min(INVENTORY_SIZE, stored.size()))
            .forEach(slot -> copy.set(slot, stored.get(slot).copy()));
        return copy;
    }

    private static int[] range(final int start, final int end) {
        return IntStream.range(start, end).toArray();
    }

    public record Result(NonNullList<ItemStack> inventory, List<ItemStack> overflow, boolean migrated) {
        public Result {
            inventory = copyInventory(inventory);
            overflow = overflow.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
        }
    }
}

