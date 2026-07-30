package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.TicketType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModChunkTickets {
    public static final DeferredRegister<TicketType> REGISTRY = DeferredRegister.create(
        Registries.TICKET_TYPE,
        Warlockery.MOD_ID
    );
    public static final DeferredHolder<TicketType, TicketType> DOLL_SHELF = REGISTRY.register(
        "doll_shelf",
        () -> new TicketType(
            TicketType.NO_TIMEOUT,
            TicketType.FLAG_PERSIST
                | TicketType.FLAG_LOADING
                | TicketType.FLAG_SIMULATION
                | TicketType.FLAG_KEEP_DIMENSION_ACTIVE
        )
    );

    private ModChunkTickets() {
    }
}
