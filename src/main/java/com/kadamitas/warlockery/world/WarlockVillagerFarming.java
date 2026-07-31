package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.block.WarlockeryCropBlock;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.ModVillagers;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;

public final class WarlockVillagerFarming {
    private static final String STARTER_SEEDS = "warlockery:warlock_starter_seeds";
    private static final List<String> SEEDS = List.of(
        "seedsbelladonna",
        "seedsmandrake",
        "seedswormwood",
        "seedswolfsbane",
        "seedsartichoke",
        "seedssnowbell"
    );

    private WarlockVillagerFarming() {
    }

    public static void handleTick(final LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            tick(villager);
        }
    }

    public static void tick(final Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)
            || !villager.getVillagerData().profession().is(ModVillagers.WARLOCK_KEY)
            || villager.tickCount % 40 != 0
            || villager.isBaby()
            || villager.isTrading()
            || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return;
        }
        supplyStarterSeeds(villager, level);
        final Optional<BlockPos> crop = nearby(level, villager, position -> isMatureWarlockeryCrop(level, position));
        if (crop.isPresent()) {
            tend(villager, crop.orElseThrow());
            return;
        }
        nearby(level, villager, position -> isOpenFarmland(level, position))
            .ifPresent(position -> plant(villager, position));
    }

    private static void supplyStarterSeeds(final Villager villager, final ServerLevel level) {
        if (villager.getPersistentData().getBooleanOr(STARTER_SEEDS, false)) {
            return;
        }
        final String seed = SEEDS.get(level.getRandom().nextInt(SEEDS.size()));
        villager.getInventory().addItem(new ItemStack(ModItems.ALL.get(seed).get(), 4));
        villager.getPersistentData().putBoolean(STARTER_SEEDS, true);
    }

    private static Optional<BlockPos> nearby(
        final ServerLevel level,
        final Villager villager,
        final java.util.function.Predicate<BlockPos> predicate
    ) {
        return BlockPos.betweenClosedStream(
                villager.blockPosition().offset(-5, -2, -5),
                villager.blockPosition().offset(5, 2, 5)
            )
            .filter(predicate)
            .map(BlockPos::immutable)
            .min(Comparator.comparingDouble(position -> villager.distanceToSqr(Vec3.atCenterOf(position))));
    }

    private static boolean isMatureWarlockeryCrop(final ServerLevel level, final BlockPos position) {
        final BlockState state = level.getBlockState(position);
        return state.getBlock() instanceof WarlockeryCropBlock crop && crop.isMaxAge(state);
    }

    private static boolean isOpenFarmland(final ServerLevel level, final BlockPos position) {
        return level.getBlockState(position).isAir() && level.getBlockState(position.below()).is(Blocks.FARMLAND);
    }

    private static void tend(final Villager villager, final BlockPos position) {
        if (villager.distanceToSqr(Vec3.atCenterOf(position)) > 5.0D) {
            villager.getNavigation().moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.65D);
            return;
        }
        final ServerLevel level = (ServerLevel) villager.level();
        final BlockState state = level.getBlockState(position);
        if (!(state.getBlock() instanceof WarlockeryCropBlock crop) || !crop.isMaxAge(state)) {
            return;
        }
        Block.getDrops(state, level, position, level.getBlockEntity(position), villager, ItemStack.EMPTY)
            .forEach(drop -> {
                final ItemStack remaining = villager.getInventory().addItem(drop);
                if (!remaining.isEmpty()) {
                    Block.popResource(level, position, remaining);
                }
            });
        level.setBlockAndUpdate(position, crop.defaultBlockState());
        villager.swing(InteractionHand.MAIN_HAND);
    }

    private static void plant(final Villager villager, final BlockPos position) {
        if (villager.distanceToSqr(Vec3.atCenterOf(position)) > 5.0D) {
            villager.getNavigation().moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.65D);
            return;
        }
        for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
            final ItemStack stack = villager.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof WarlockeryCropBlock crop)) {
                continue;
            }
            villager.level().setBlockAndUpdate(position, crop.defaultBlockState());
            stack.shrink(1);
            villager.swing(InteractionHand.MAIN_HAND);
            return;
        }
    }
}
