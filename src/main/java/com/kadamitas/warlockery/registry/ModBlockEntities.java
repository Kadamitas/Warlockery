package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.block.entity.MagicMachineBlockEntity;
import com.kadamitas.warlockery.block.entity.WolfTrapBlockEntity;
import com.kadamitas.warlockery.block.entity.DollShelfBlockEntity;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import java.util.Set;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTRY =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Warlockery.MOD_ID);

    public static final RegistryObject<BlockEntityType<AltarBlockEntity>> ALTAR = REGISTRY.register(
        "altar",
        () -> new BlockEntityType<>(AltarBlockEntity::new, Set.of(ModBlocks.ALTAR.get()))
    );

    public static final RegistryObject<BlockEntityType<MagicMachineBlockEntity>> MAGIC_MACHINE = REGISTRY.register(
        "magic_machine",
        () -> new BlockEntityType<>(
            MagicMachineBlockEntity::new,
            MachineProfiles.blockIds().stream()
                .map(id -> ModBlocks.ALL.get(id).get())
                .collect(java.util.stream.Collectors.toUnmodifiableSet())
        )
    );

    public static final RegistryObject<BlockEntityType<WolfTrapBlockEntity>> WOLF_TRAP = REGISTRY.register(
        "wolf_trap",
        () -> new BlockEntityType<>(WolfTrapBlockEntity::new, Set.of(ModBlocks.ALL.get("wolftrap").get()))
    );

    public static final RegistryObject<BlockEntityType<DollShelfBlockEntity>> DOLL_SHELF = REGISTRY.register(
        "doll_shelf",
        () -> new BlockEntityType<>(DollShelfBlockEntity::new, Set.of(ModBlocks.ALL.get("doll_shelf").get()))
    );

    private ModBlockEntities() {
    }
}
