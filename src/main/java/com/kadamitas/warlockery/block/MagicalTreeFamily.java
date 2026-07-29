package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.Warlockery;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

public enum MagicalTreeFamily {
    ALDER("alder"),
    HAWTHORN("hawthorn"),
    ROWAN("rowan");

    private static final Map<String, MagicalTreeFamily> BY_ID = Arrays.stream(values())
        .collect(Collectors.toUnmodifiableMap(MagicalTreeFamily::id, Function.identity()));

    private final String id;
    private final ResourceKey<ConfiguredFeature<?, ?>> configuredFeature;
    private final TreeGrower treeGrower;

    MagicalTreeFamily(final String id) {
        this.id = id;
        configuredFeature = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, id + "_tree")
        );
        treeGrower = new TreeGrower(
            Warlockery.MOD_ID + "_" + id,
            Optional.empty(),
            Optional.of(configuredFeature),
            Optional.empty()
        );
    }

    public String id() {
        return id;
    }

    public ResourceKey<ConfiguredFeature<?, ?>> configuredFeature() {
        return configuredFeature;
    }

    public TreeGrower treeGrower() {
        return treeGrower;
    }

    public String blockId(final MagicalWoodPart part) {
        return id + "_" + part.suffix();
    }

    public static Optional<MagicalTreeFamily> find(final String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }
}
