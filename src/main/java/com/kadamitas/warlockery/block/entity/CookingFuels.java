package com.kadamitas.warlockery.block.entity;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;

/** Resolves 26.3 cooking fuel components in the machine's server-side loot context. */
public final class CookingFuels {
    private static final ContextKeySet PARAMETERS = new ContextKeySet.Builder()
        .required(LootContextParams.CONTAINER)
        .optional(LootContextParams.BLOCK_ENTITY)
        .optional(LootContextParams.BLOCK_STATE)
        .optional(LootContextParams.ORIGIN).build();

    private CookingFuels() {}

    public static boolean isFuel(final ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.COOKING_FUEL);
    }

    public static int burnDuration(final Level level, final ItemStack stack, final Container container) {
        if (!(level instanceof ServerLevel serverLevel) || !isFuel(stack)) {
            return 0;
        }
        final var parameters = new LootParams.Builder(serverLevel)
            .withParameter(LootContextParams.CONTAINER, container);
        if (container instanceof BlockEntity block) {
            parameters.withParameter(LootContextParams.BLOCK_ENTITY, block)
                .withParameter(LootContextParams.BLOCK_STATE, block.getBlockState())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(block.getBlockPos()));
        }
        final var context = new LootContext.Builder(parameters.create(PARAMETERS)).create(Optional.empty());
        return Math.max(0, ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL,
            CookingFuel::burnTime, context, 0));
    }
}

