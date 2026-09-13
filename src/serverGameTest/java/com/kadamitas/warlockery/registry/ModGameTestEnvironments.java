package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.gametest.IsolatedTestEnvironment;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class ModGameTestEnvironments {
    private ModGameTestEnvironments() {
    }

    public static void register() {
        Registry.register(
            BuiltInRegistries.TEST_ENVIRONMENT_DEFINITION_TYPE,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "isolated"),
            IsolatedTestEnvironment.CODEC
        );
    }
}
