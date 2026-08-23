package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.gametest.IsolatedTestEnvironment;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModGameTestEnvironments {
    public static final DeferredRegister<MapCodec<? extends TestEnvironmentDefinition<?>>> REGISTRY =
        DeferredRegister.create(Registries.TEST_ENVIRONMENT_DEFINITION_TYPE, Warlockery.MOD_ID);
    public static final DeferredHolder<
        MapCodec<? extends TestEnvironmentDefinition<?>>,
        MapCodec<? extends TestEnvironmentDefinition<?>>
    > ISOLATED = REGISTRY.register("isolated", () -> IsolatedTestEnvironment.CODEC);

    private ModGameTestEnvironments() {
    }
}
