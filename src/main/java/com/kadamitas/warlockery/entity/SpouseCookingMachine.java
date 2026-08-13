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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

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
        return handlers(level).map(handlers -> allEmpty(handlers.input())
            && allEmpty(handlers.output())
            && hasFuel(level, handlers.fuel())
            && accepts(handlers.input(), input)).orElse(false);
    }

    boolean insertOne(final ServerLevel level, final ItemStack input) {
        final ItemStack one = input.copyWithCount(1);
        if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) {
            if (!furnace.getItem(0).isEmpty()) {
                return false;
            }
            furnace.setItem(0, one);
            furnace.setChanged();
            return true;
        }
        return handlers(level).map(handlers -> insert(handlers.input(), one)).orElse(false);
    }

    Optional<ItemStack> extractOne(final ServerLevel level, final ItemStack expected) {
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
        return handlers(level)
            .filter(handlers -> allEmpty(handlers.input()))
            .flatMap(handlers -> extractExclusive(handlers.output(), expected));
    }

    boolean pending(final ServerLevel level) {
        if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) {
            return !furnace.getItem(0).isEmpty() || !furnace.getItem(2).isEmpty();
        }
        return handlers(level)
            .map(handlers -> !allEmpty(handlers.input()) || !allEmpty(handlers.output()))
            .orElse(false);
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

    private Optional<Handlers> handlers(final ServerLevel level) {
        final BlockPos position = blockEntity.getBlockPos();
        final var state = level.getBlockState(position);
        final ResourceHandler<ItemResource> input = level.getCapability(
            Capabilities.Item.BLOCK, position, state, blockEntity, Direction.UP
        );
        final ResourceHandler<ItemResource> output = level.getCapability(
            Capabilities.Item.BLOCK, position, state, blockEntity, Direction.DOWN
        );
        final ResourceHandler<ItemResource> fuel = level.getCapability(
            Capabilities.Item.BLOCK, position, state, blockEntity, Direction.NORTH
        );
        return input == null || output == null || fuel == null
            ? Optional.empty()
            : Optional.of(new Handlers(input, output, fuel));
    }

    private static boolean allEmpty(final ResourceHandler<ItemResource> handler) {
        for (int slot = 0; slot < handler.size(); slot++) {
            if (!handler.getResource(slot).isEmpty() && handler.getAmountAsLong(slot) > 0L) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasFuel(final ServerLevel level, final ResourceHandler<ItemResource> handler) {
        for (int slot = 0; slot < handler.size(); slot++) {
            if (handler.getAmountAsLong(slot) > 0L
                && level.fuelValues().isFuel(handler.getResource(slot).toStack())) {
                return true;
            }
        }
        return false;
    }

    private static boolean accepts(final ResourceHandler<ItemResource> handler, final ItemStack input) {
        final ItemResource resource = ItemResource.of(input);
        for (int slot = 0; slot < handler.size(); slot++) {
            try (Transaction transaction = Transaction.openRoot()) {
                if (handler.insert(slot, resource, 1, transaction) == 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean insert(final ResourceHandler<ItemResource> handler, final ItemStack input) {
        final ItemResource resource = ItemResource.of(input);
        for (int slot = 0; slot < handler.size(); slot++) {
            try (Transaction transaction = Transaction.openRoot()) {
                if (handler.insert(slot, resource, 1, transaction) == 1) {
                    transaction.commit();
                    return true;
                }
            }
        }
        return false;
    }

    private static Optional<ItemStack> extractExclusive(
        final ResourceHandler<ItemResource> handler,
        final ItemStack expected
    ) {
        int matchingSlot = -1;
        ItemResource matchingResource = ItemResource.EMPTY;
        for (int slot = 0; slot < handler.size(); slot++) {
            final ItemResource present = handler.getResource(slot);
            final long amount = handler.getAmountAsLong(slot);
            if (present.isEmpty() || amount <= 0L) {
                continue;
            }
            if (matchingSlot >= 0
                || amount != 1L
                || !ItemStack.isSameItemSameComponents(present.toStack(), expected)) {
                return Optional.empty();
            }
            matchingSlot = slot;
            matchingResource = present;
        }
        if (matchingSlot < 0) {
            return Optional.empty();
        }
        try (Transaction transaction = Transaction.openRoot()) {
            if (handler.extract(matchingSlot, matchingResource, 1, transaction) != 1) {
                return Optional.empty();
            }
            transaction.commit();
            return Optional.of(matchingResource.toStack());
        }
    }

    private record Handlers(
        ResourceHandler<ItemResource> input,
        ResourceHandler<ItemResource> output,
        ResourceHandler<ItemResource> fuel
    ) {
    }
}
