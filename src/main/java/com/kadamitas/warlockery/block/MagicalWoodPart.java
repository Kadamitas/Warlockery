package com.kadamitas.warlockery.block;

public enum MagicalWoodPart {
    LOG("log"),
    PLANKS("planks"),
    LEAVES("leaves"),
    SAPLING("sapling");

    private final String suffix;

    MagicalWoodPart(final String suffix) {
        this.suffix = suffix;
    }

    public String suffix() {
        return suffix;
    }
}
