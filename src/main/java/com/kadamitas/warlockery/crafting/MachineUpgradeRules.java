package com.kadamitas.warlockery.crafting;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;

public final class MachineUpgradeRules {
    private static final int MAX_RANK = 4;

    private MachineUpgradeRules() {
    }

    public static Upgrade around(
        final LevelReader level,
        final BlockPos machinePos,
        final MachineProfile profile
    ) {
        if (!"alchemical_oven".equals(profile.recipeType())) {
            return Upgrade.NONE;
        }
        return combine(Arrays.stream(Direction.values()).mapToInt(direction -> {
            final var state = level.getBlockState(machinePos.relative(direction));
            if (state.is(WarlockeryTags.Blocks.FILTERED_FUME_FUNNELS)) {
                return 2;
            }
            return state.is(WarlockeryTags.Blocks.FUME_FUNNELS) ? 1 : 0;
        }));
    }

    public static Upgrade combine(final IntStream ranks) {
        final int rank = Math.clamp(ranks.filter(value -> value > 0).sum(), 0, MAX_RANK);
        return rank == 0 ? Upgrade.NONE : new Upgrade(1 + rank, rank);
    }

    public static List<ItemStack> enhanceOutputs(final List<ItemStack> outputs, final Upgrade upgrade) {
        if (upgrade.extraFumes() == 0) {
            return List.copyOf(outputs);
        }
        return outputs.stream().map(stack -> {
            final ItemStack enhanced = stack.copy();
            if (enhanced.is(WarlockeryTags.Items.ALCHEMICAL_FUMES)) {
                enhanced.grow(upgrade.extraFumes());
            }
            return enhanced;
        }).toList();
    }

    public record Upgrade(int progressPerTick, int extraFumes) {
        public static final Upgrade NONE = new Upgrade(1, 0);

        public Upgrade {
            if (progressPerTick < 1 || extraFumes < 0) {
                throw new IllegalArgumentException("Invalid machine upgrade");
            }
        }
    }
}
