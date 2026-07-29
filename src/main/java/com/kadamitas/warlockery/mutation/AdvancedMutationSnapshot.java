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
    int livingMandrakes
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
            livingMandrakes
        ).anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException("Mutation counts must be nonnegative");
        }
    }
}
