package com.kadamitas.warlockery.item;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record ReplicationSelection(Identifier dimension, BlockPos first, Optional<BlockPos> second) {
    private static final String DIMENSION = "WarlockeryReplicationDimension";
    private static final String FIRST = "WarlockeryReplicationFirst";
    private static final String SECOND = "WarlockeryReplicationSecond";
    public static final int MAX_VOLUME = 512;

    public ReplicationSelection {
        second = second == null ? Optional.empty() : second;
    }

    public static Optional<ReplicationSelection> read(final ItemStack stack) {
        return read(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    static Optional<ReplicationSelection> read(final CompoundTag data) {
        final String dimension = data.getStringOr(DIMENSION, "");
        return data.getLong(FIRST).flatMap(first -> {
            try {
                return Optional.of(new ReplicationSelection(
                    Identifier.parse(dimension),
                    BlockPos.of(first),
                    data.getLong(SECOND).map(BlockPos::of)
                ));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        });
    }

    public ReplicationSelection withSecond(final BlockPos position) {
        return new ReplicationSelection(dimension, first, Optional.of(position.immutable()));
    }

    public int volume() {
        if (second.isEmpty()) {
            return 0;
        }
        final BlockPos other = second.orElseThrow();
        return (Math.abs(first.getX() - other.getX()) + 1)
            * (Math.abs(first.getY() - other.getY()) + 1)
            * (Math.abs(first.getZ() - other.getZ()) + 1);
    }

    public UtilityDecision diagnose(final Identifier currentDimension) {
        if (!dimension.equals(currentDimension)) {
            return UtilityDecision.failure("wrong_dimension");
        }
        if (second.isEmpty()) {
            return UtilityDecision.failure("missing_second_corner");
        }
        return volume() <= MAX_VOLUME
            ? UtilityDecision.success("ready")
            : UtilityDecision.failure("selection_too_large");
    }

    public void write(final ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
            data.putString(DIMENSION, dimension.toString());
            data.putLong(FIRST, first.asLong());
            second.ifPresentOrElse(position -> data.putLong(SECOND, position.asLong()), () -> data.remove(SECOND));
        });
    }

    public static void clear(final ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
            data.remove(DIMENSION);
            data.remove(FIRST);
            data.remove(SECOND);
        });
    }
}
