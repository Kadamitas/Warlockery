package com.kadamitas.warlockery.entity;

import java.util.Optional;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
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
        final ServerLevel level = (ServerLevel) blockEntity.getLevel();
        if (level == null) {
            return Optional.empty();
        }
        final BlockPos position = blockEntity.getBlockPos();
        final Storage<ItemVariant> input = ItemStorage.SIDED.find(level, position, Direction.UP);
        final Storage<ItemVariant> output = ItemStorage.SIDED.find(level, position, Direction.DOWN);
        final Storage<ItemVariant> fuel = ItemStorage.SIDED.find(level, position, Direction.NORTH);
        return input == null || output == null || fuel == null
            ? Optional.empty()
            : Optional.of(new Handlers(input, output, fuel));
    }

    private static boolean allEmpty(final Storage<ItemVariant> handler) {
        for (StorageView<ItemVariant> view : handler) {
            if (!view.isResourceBlank() && view.getAmount() > 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasFuel(final ServerLevel level, final Storage<ItemVariant> handler) {
        for (StorageView<ItemVariant> view : handler) {
            if (!view.isResourceBlank()
                && view.getAmount() > 0
                && level.fuelValues().isFuel(view.getResource().toStack())) {
                return true;
            }
        }
        return false;
    }

    private static boolean accepts(final Storage<ItemVariant> handler, final ItemStack input) {
        try (Transaction transaction = Transaction.openOuter()) {
            return handler.insert(ItemVariant.of(input), 1, transaction) == 1;
        }
    }

    private static boolean insert(final Storage<ItemVariant> handler, final ItemStack input) {
        try (Transaction transaction = Transaction.openOuter()) {
            if (handler.insert(ItemVariant.of(input), input.getCount(), transaction) == input.getCount()) {
                transaction.commit();
                return true;
            }
            return false;
        }
    }

    private static Optional<ItemStack> extractExclusive(final Storage<ItemVariant> handler, final ItemStack expected) {
        StorageView<ItemVariant> matchingView = null;
        for (StorageView<ItemVariant> view : handler) {
            if (view.isResourceBlank() || view.getAmount() <= 0) {
                continue;
            }
            if (matchingView != null
                || view.getAmount() != 1
                || !ItemVariant.of(expected).equals(view.getResource())) {
                return Optional.empty();
            }
            matchingView = view;
        }
        if (matchingView == null) {
            return Optional.empty();
        }
        final ItemVariant resource = matchingView.getResource();
        try (Transaction transaction = Transaction.openOuter()) {
            if (matchingView.extract(resource, 1, transaction) != 1) {
                return Optional.empty();
            }
            transaction.commit();
            return Optional.of(resource.toStack());
        }
    }

    private record Handlers(
        Storage<ItemVariant> input,
        Storage<ItemVariant> output,
        Storage<ItemVariant> fuel
    ) {
    }
}
