package com.kadamitas.warlockery.item;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;

public final class VeilWaystoneRules {
    public static final int ATTUNEMENT_TICKS = 40;
    public static final int POSITION_BINDING_POWER = 500;
    public static final int CREATURE_BINDING_POWER = 1_600;
    public static final int TELEPORT_POWER = 1_000;
    public static final int MAX_POSITION_WAYSTONES = 8;
    public static final int INHIBITOR_RADIUS = 10;
    public static final double MESSAGE_RANGE = 32.0D;

    private static final List<BlockPos> BINDING_RING = IntStream.rangeClosed(-1, 1)
        .boxed()
        .flatMap(x -> IntStream.rangeClosed(-1, 1).mapToObj(z -> new BlockPos(x, 0, z)))
        .filter(offset -> !offset.equals(BlockPos.ZERO))
        .toList();

    private static final List<BlockPos> TRANSPOSITION_RING = IntStream.rangeClosed(-2, 2)
        .boxed()
        .flatMap(x -> IntStream.rangeClosed(-2, 2).mapToObj(z -> new BlockPos(x, 0, z)))
        .filter(offset -> Math.max(Math.abs(offset.getX()), Math.abs(offset.getZ())) == 2)
        .filter(offset -> Math.abs(offset.getX()) != 2 || Math.abs(offset.getZ()) != 2)
        .toList();
    private static final Set<BlockPos> BINDING_RING_SET = Set.copyOf(BINDING_RING);
    private static final Set<BlockPos> TRANSPOSITION_RING_SET = Set.copyOf(TRANSPOSITION_RING);
    private static final Set<BlockPos> BINDING_SQUARE = square(1);
    private static final Set<BlockPos> TRANSPOSITION_SQUARE = square(2);

    private VeilWaystoneRules() {
    }

    public static List<BlockPos> bindingRing() {
        return BINDING_RING;
    }

    public static List<BlockPos> transpositionRing() {
        return TRANSPOSITION_RING;
    }

    static Set<BlockPos> bindingRingSet() {
        return BINDING_RING_SET;
    }

    static Set<BlockPos> transpositionRingSet() {
        return TRANSPOSITION_RING_SET;
    }

    static Set<BlockPos> bindingSquare() {
        return BINDING_SQUARE;
    }

    static Set<BlockPos> transpositionSquare() {
        return TRANSPOSITION_SQUARE;
    }

    private static Set<BlockPos> square(final int radius) {
        return IntStream.rangeClosed(-radius, radius)
            .boxed()
            .flatMap(x -> IntStream.rangeClosed(-radius, radius).mapToObj(z -> new BlockPos(x, 0, z)))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static BindingMode bindingMode(final int plainWaystones, final boolean livingTargetPresent) {
        return plainWaystones == 1 && livingTargetPresent
            ? BindingMode.CREATURE
            : BindingMode.POSITION;
    }

    public static int requiredPower(final BindingMode mode) {
        return mode == BindingMode.CREATURE ? CREATURE_BINDING_POWER : POSITION_BINDING_POWER;
    }

    public enum BindingMode {
        POSITION,
        CREATURE
    }
}
