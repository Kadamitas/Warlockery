package com.kadamitas.warlockery.entity;

import java.util.Arrays;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The four declared Warlockery goblin-society professions, extracted from the nested
 * {@code HobgoblinEntity.GoblinProfession} so the dedicated F10 {@link GoblinEntity} merchant body,
 * the retained {@link HobgoblinEntity}, and the shared {@link GoblinTradeCatalog} can all name one
 * type without inheriting the full human Villager implementation.
 *
 * <p>The declared ordinal order, string ids, workstation blocks, engine-profession mapping, and
 * displayed-name translation keys are unchanged public surface: the enum move is a compile-time
 * relocation only.</p>
 */
public enum GoblinProfession {
    MINER("miner", Blocks.STONECUTTER, VillagerProfession.MASON),
    SMITH("smith", Blocks.BLAST_FURNACE, VillagerProfession.ARMORER),
    SHAMAN("shaman", Blocks.BREWING_STAND, VillagerProfession.CLERIC),
    PROSPECTOR("prospector", Blocks.CARTOGRAPHY_TABLE, VillagerProfession.CARTOGRAPHER);

    /** The safe fallback for unfinalized, malformed, and unknown persisted professions. */
    public static final GoblinProfession FALLBACK = PROSPECTOR;

    private final String id;
    private final Block workstation;
    private final ResourceKey<VillagerProfession> engineProfession;

    GoblinProfession(
        final String id,
        final Block workstation,
        final ResourceKey<VillagerProfession> engineProfession
    ) {
        this.id = id;
        this.workstation = workstation;
        this.engineProfession = engineProfession;
    }

    public String id() {
        return id;
    }

    public Block workstation() {
        return workstation;
    }

    public ResourceKey<VillagerProfession> engineProfession() {
        return engineProfession;
    }

    public static GoblinProfession byId(final String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst().orElse(FALLBACK);
    }
}
