package com.kadamitas.warlockery.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.TicketType;

public final class ModChunkTickets {
    public static final RegistrationHandle<TicketType> DOLL_SHELF = RegistrationHandle.create(
        "doll_shelf",
        () -> new TicketType(
            TicketType.NO_TIMEOUT,
            TicketType.FLAG_PERSIST
                | TicketType.FLAG_LOADING
                | TicketType.FLAG_SIMULATION
                | TicketType.FLAG_KEEP_DIMENSION_ACTIVE
        )
    );

    public static final RegistrationHandle<TicketType> SLEEPING_BODY = RegistrationHandle.create(
        "sleeping_body",
        () -> new TicketType(60L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE)
    );

    private ModChunkTickets() {
    }

    public static void register() {
        DOLL_SHELF.register(BuiltInRegistries.TICKET_TYPE);
        SLEEPING_BODY.register(BuiltInRegistries.TICKET_TYPE);
    }
}
