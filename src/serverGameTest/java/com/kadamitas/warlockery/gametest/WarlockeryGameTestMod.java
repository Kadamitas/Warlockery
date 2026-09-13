package com.kadamitas.warlockery.gametest;

import com.kadamitas.warlockery.registry.ModGameTestEnvironments;
import com.kadamitas.warlockery.registry.ModGameTests;
import net.fabricmc.api.ModInitializer;

/** Development-only registration; this source set is never part of the production artifact. */
public final class WarlockeryGameTestMod implements ModInitializer {
    @Override public void onInitialize() {
        ModGameTestEnvironments.register();
        ModGameTests.register();
    }
}
