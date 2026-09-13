package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.gametest.IsolatedTestEnvironment;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModGameTestEnvironments {
    public static final DeferredRegister<MapCodec<? extends TestEnvironmentDefinition<?>>> REGISTRY =
        DeferredRegister.create(Registries.TEST_ENVIRONMENT_DEFINITION_TYPE, Warlockery.MOD_ID);
    public static final RegistryObject<MapCodec<? extends TestEnvironmentDefinition<?>>> ISOLATED =
        REGISTRY.register("isolated", () -> IsolatedTestEnvironment.CODEC);

    private ModGameTestEnvironments() {
    }
}
