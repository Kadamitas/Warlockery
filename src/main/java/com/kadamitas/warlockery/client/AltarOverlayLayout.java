package com.kadamitas.warlockery.client;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class AltarOverlayLayout {
    private AltarOverlayLayout() {
    }

    public static Placement place(final Collection<BlockPos> connected) {
        if (connected.isEmpty()) {
            throw new IllegalArgumentException("An altar display requires at least one block");
        }
        final BlockPos anchor = connected.stream().filter(Objects::nonNull)
            .min(BlockPos::compareTo)
            .orElseThrow(() -> new IllegalArgumentException("An altar display requires a valid block"));
        final double x = connected.stream().filter(Objects::nonNull).mapToInt(BlockPos::getX)
            .average().orElse(anchor.getX());
        final double z = connected.stream().filter(Objects::nonNull).mapToInt(BlockPos::getZ)
            .average().orElse(anchor.getZ());
        return new Placement(anchor, new Vec3(x - anchor.getX() + 0.5, 1.3, z - anchor.getZ() + 0.5));
    }

    public static Optional<Placement> placeIfPresent(final Collection<BlockPos> connected) {
        final var valid = connected.stream().filter(Objects::nonNull).toList();
        return valid.isEmpty() ? Optional.empty() : Optional.of(place(valid));
    }

    public record Placement(BlockPos anchor, Vec3 position) {
        public Placement {
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(position, "position");
        }
    }
}
