package com.kadamitas.warlockery.entity;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

final class SpouseCookingMachine {
    private final BlockEntity blockEntity;

    private SpouseCookingMachine(final BlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    static Optional<SpouseCookingMachine> at(final ServerLevel level, final BlockPos position) {
        if (!level.getBlockState(position).is(SpouseAmbientTags.FURNACE_WORKSTATIONS)
            && !(level.getBlockEntity(position) instanceof AbstractFurnaceBlockEntity)) {
            return Optional.empty();
        }
        return Optional.ofNullable(level.getBlockEntity(position)).map(SpouseCookingMachine::new);
    }

    boolean availableFor(final ServerLevel level, final ItemStack input) {
        if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) {
            return furnace.getItem(0).isEmpty()
                && furnace.getItem(2).isEmpty()
                && (!furnace.getItem(1).isEmpty()
                    || furnace.getBlockState().getOptionalValue(AbstractFurnaceBlock.LIT).orElse(false));
        }
        return handlers().map(handlers -> allEmpty(handlers.input())
            && allEmpty(handlers.output())
            && hasFuel(level, handlers.fuel())
            && accepts(handlers.input(), input)).orElse(false);
    }

    boolean insertOne(final ItemStack input) {
        final ItemStack one = input.copyWithCount(1);
        if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) {
            if (!furnace.getItem(0).isEmpty()) {
                return false;
            }
            furnace.setItem(0, one);
            furnace.setChanged();
            return true;
        }
        return handlers().map(handlers -> insert(handlers.input(), one)).orElse(false);
    }

    Optional<ItemStack> extractOne(final ItemStack expected) {
        if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) {
            final ItemStack result = furnace.getItem(2);
            if (!furnace.getItem(0).isEmpty()
                || result.getCount() != 1
                || !ItemStack.isSameItemSameComponents(result, expected)) {
                return Optional.empty();
            }
            final ItemStack extracted = result.copyWithCount(1);
            result.shrink(1);
            furnace.setItem(2, result);
            furnace.setChanged();
            return Optional.of(extracted);
        }
        return handlers()
            .filter(handlers -> allEmpty(handlers.input()))
            .flatMap(handlers -> extractExclusive(handlers.output(), expected));
    }

    boolean pending() {
        if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) {
            return !furnace.getItem(0).isEmpty() || !furnace.getItem(2).isEmpty();
        }
        return handlers().map(handlers -> !allEmpty(handlers.input()) || !allEmpty(handlers.output())).orElse(false);
    }

    RecipeType<? extends AbstractCookingRecipe> recipeType() {
        if (blockEntity instanceof SmokerBlockEntity) {
            return RecipeType.SMOKING;
        }
        if (blockEntity instanceof BlastFurnaceBlockEntity) {
            return RecipeType.BLASTING;
        }
        return RecipeType.SMELTING;
    }

    private Optional<Handlers> handlers() {
        final Optional<IItemHandler> input = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve();
        final Optional<IItemHandler> output = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).resolve();
        final Optional<IItemHandler> fuel = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).resolve();
        return input.flatMap(in -> output.flatMap(out -> fuel.map(side -> new Handlers(in, out, side))));
    }

    private static boolean allEmpty(final IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasFuel(final ServerLevel level, final IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (level.fuelValues().isFuel(handler.getStackInSlot(slot))) {
                return true;
            }
        }
        return false;
    }

    private static boolean accepts(final IItemHandler handler, final ItemStack input) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.insertItem(slot, input.copyWithCount(1), true).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean insert(final IItemHandler handler, final ItemStack input) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.insertItem(slot, input.copy(), false).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static Optional<ItemStack> extractExclusive(final IItemHandler handler, final ItemStack expected) {
        int matchingSlot = -1;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            final ItemStack present = handler.getStackInSlot(slot);
            if (present.isEmpty()) {
                continue;
            }
            if (matchingSlot >= 0
                || present.getCount() != 1
                || !ItemStack.isSameItemSameComponents(present, expected)) {
                return Optional.empty();
            }
            matchingSlot = slot;
        }
        if (matchingSlot < 0) {
            return Optional.empty();
        }
        final ItemStack candidate = handler.extractItem(matchingSlot, 1, true);
        return candidate.getCount() == 1 && ItemStack.isSameItemSameComponents(candidate, expected)
            ? Optional.of(handler.extractItem(matchingSlot, 1, false))
            : Optional.empty();
    }

    private record Handlers(IItemHandler input, IItemHandler output, IItemHandler fuel) {
    }
}
