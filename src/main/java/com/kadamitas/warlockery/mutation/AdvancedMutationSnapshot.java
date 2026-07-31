package com.kadamitas.warlockery.mutation;

import java.util.stream.IntStream;

public record AdvancedMutationSnapshot(
    boolean cobweb,
    boolean water,
    int slimeSnares,
    int diagonalGrasspers,
    int mutandisExtremis,
    int chargedAttunedStones,
    int focusedWill,
    int matureCardinalMandrakes,
    int toadHosts,
    int creeperHosts,
    int livingMandrakes,
    int batSnares,
    int wolfHosts
) {
    public AdvancedMutationSnapshot {
        if (IntStream.of(
            slimeSnares,
            diagonalGrasspers,
            mutandisExtremis,
            chargedAttunedStones,
            focusedWill,
            matureCardinalMandrakes,
            toadHosts,
            creeperHosts,
            livingMandrakes,
            batSnares,
            wolfHosts
        ).anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException("Mutation counts must be nonnegative");
        }
    }

    public AdvancedMutationSnapshot(
        final boolean cobweb,
        final boolean water,
        final int slimeSnares,
        final int diagonalGrasspers,
        final int mutandisExtremis,
        final int chargedAttunedStones,
        final int focusedWill,
        final int matureCardinalMandrakes,
        final int toadHosts,
        final int creeperHosts,
        final int livingMandrakes
    ) {
        this(
            cobweb,
            water,
            slimeSnares,
            diagonalGrasspers,
            mutandisExtremis,
            chargedAttunedStones,
            focusedWill,
            matureCardinalMandrakes,
            toadHosts,
            creeperHosts,
            livingMandrakes,
            0,
            0
        );
    }
}
