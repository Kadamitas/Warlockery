package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.block.WickerBundleBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

public final class HuntsmanSummoningStructure {
    public static final int REQUIRED_BUNDLES = 4;

    private HuntsmanSummoningStructure() {
    }

    public static List<BlockPos> positions(final BlockPos center) {
        return List.of(
            center.offset(2, 0, 0),
            center.offset(-2, 0, 0),
            center.offset(0, 0, 2),
            center.offset(0, 0, -2)
        );
    }

    public static int completedBundles(final ServerLevel level, final BlockPos center) {
        return (int) positions(center).stream()
            .map(level::getBlockState)
            .filter(state -> state.getBlock() instanceof WickerBundleBlock)
            .filter(state -> state.getValue(WickerBundleBlock.BLOODIED))
            .count();
    }

    public static boolean ready(final int completedBundles) {
        return completedBundles >= REQUIRED_BUNDLES;
    }

    public static boolean consume(final ServerLevel level, final BlockPos center) {
        if (!ready(completedBundles(level, center))) {
            return false;
        }
        positions(center).forEach(position -> level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState()));
        return true;
    }
}
