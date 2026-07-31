package com.kadamitas.warlockery.crafting;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import net.minecraft.resources.Identifier;

public final class SpiritWorldMachineRules {
    public static final Identifier FLOWING_SPIRIT = Identifier.fromNamespaceAndPath(
        Warlockery.MOD_ID,
        "cauldron_flowing_spirit"
    );

    private SpiritWorldMachineRules() {
    }

    public static boolean allows(final Identifier recipe, final Identifier dimension) {
        return !FLOWING_SPIRIT.equals(recipe) || SpiritWorldRuntime.SPIRIT_WORLD.identifier().equals(dimension);
    }
}
