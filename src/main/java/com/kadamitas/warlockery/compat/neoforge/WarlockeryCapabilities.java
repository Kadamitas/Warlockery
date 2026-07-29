package com.kadamitas.warlockery.compat.neoforge;

import com.kadamitas.warlockery.block.entity.DollShelfBlockEntity;
import com.kadamitas.warlockery.block.entity.MagicMachineBlockEntity;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class WarlockeryCapabilities {
    private WarlockeryCapabilities() {
    }

    public static void register(final RegisterCapabilitiesEvent event) {
        MagicMachineBlockEntity.registerCapabilities(event);
        DollShelfBlockEntity.registerCapabilities(event);
    }
}
