package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.block.entity.MagicMachineBlockEntity;
import com.kadamitas.warlockery.block.entity.WolfTrapBlockEntity;
import com.kadamitas.warlockery.block.entity.DollShelfBlockEntity;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static final RegistrationHandle<BlockEntityType<AltarBlockEntity>> ALTAR = RegistrationHandle.create(
        "altar",
        () -> new BlockEntityType<>(AltarBlockEntity::new, Set.of(ModBlocks.ALTAR.get()))
    );

    public static final RegistrationHandle<BlockEntityType<MagicMachineBlockEntity>> MAGIC_MACHINE = RegistrationHandle.create(
        "magic_machine",
        () -> new BlockEntityType<>(
            MagicMachineBlockEntity::new,
            MachineProfiles.blockIds().stream()
                .map(id -> ModBlocks.ALL.get(id).get())
                .collect(java.util.stream.Collectors.toUnmodifiableSet())
        )
    );

    public static final RegistrationHandle<BlockEntityType<WolfTrapBlockEntity>> WOLF_TRAP = RegistrationHandle.create(
        "wolf_trap",
        () -> new BlockEntityType<>(WolfTrapBlockEntity::new, Set.of(ModBlocks.ALL.get("wolftrap").get()))
    );

    public static final RegistrationHandle<BlockEntityType<DollShelfBlockEntity>> DOLL_SHELF = RegistrationHandle.create(
        "doll_shelf",
        () -> new BlockEntityType<>(DollShelfBlockEntity::new, Set.of(ModBlocks.ALL.get("doll_shelf").get()))
    );

    private ModBlockEntities() {
    }

    public static void register() {
        ALTAR.register(BuiltInRegistries.BLOCK_ENTITY_TYPE);
        MAGIC_MACHINE.register(BuiltInRegistries.BLOCK_ENTITY_TYPE);
        WOLF_TRAP.register(BuiltInRegistries.BLOCK_ENTITY_TYPE);
        DOLL_SHELF.register(BuiltInRegistries.BLOCK_ENTITY_TYPE);
    }
}
