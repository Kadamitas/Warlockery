package com.kadamitas.warlockery.magic;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

public final class MagicCompatibilityTags {
    public static final TagKey<EntityType<?>> INFERNAL_ENTHRALLMENT_IMMUNE = entity("infernal_enthrallment_immune");
    public static final TagKey<EntityType<?>> INFERNAL_EXPLOSION_POWER = entity("infernal_sacrifices/explosion");
    public static final TagKey<EntityType<?>> INFERNAL_PROJECTILE_POWER = entity("infernal_sacrifices/projectile");
    public static final TagKey<EntityType<?>> INFERNAL_WEB_POWER = entity("infernal_sacrifices/web");
    public static final TagKey<EntityType<?>> INFERNAL_FIRE_POWER = entity("infernal_sacrifices/fire");
    public static final TagKey<EntityType<?>> INFERNAL_SPEED_POWER = entity("infernal_sacrifices/speed");
    public static final TagKey<EntityType<?>> INFERNAL_TELEPORT_POWER = entity("infernal_sacrifices/teleport");
    public static final TagKey<EntityType<?>> INFERNAL_LEAPING_POWER = entity("infernal_sacrifices/leaping");
    public static final TagKey<EntityType<?>> INFERNAL_FLIGHT_POWER = entity("infernal_sacrifices/flight");
    public static final TagKey<EntityType<?>> INFERNAL_AQUATIC_POWER = entity("infernal_sacrifices/aquatic");
    public static final TagKey<EntityType<?>> INFERNAL_UNDEAD_POWER = entity("infernal_sacrifices/undead");
    public static final TagKey<EntityType<?>> GRAVE_NOURISHING_VICTIMS = entity("grave_nourishing_victims");
    public static final TagKey<EntityType<?>> MANIFESTABLE_SPIRITS = entity("manifestable_spirits");
    public static final TagKey<Item> METAL_EQUIPMENT = item("magic/metal_equipment");
    public static final TagKey<Item> METAL_DROPS = item("magic/metal_drops");
    public static final TagKey<Item> IMP_GIFTS = item("creature_interactions/imp_gifts");
    public static final TagKey<Block> EARTH_CONTROLLED_BLOCKS = block("magic/earth_controlled_blocks");
    public static final TagKey<Block> OVERWORLD_TRANSMUTABLE_ORES = block("magic/overworld_transmutable_ores");
    public static final TagKey<Block> OVERWORLD_LANDING_BLOCKS = block("magic/overworld_landing_blocks");
    public static final TagKey<Block> IMP_SMELTABLE_BLOCKS = block("magic/imp_smeltable_blocks");
    public static final TagKey<Fluid> IMP_EVAPORATABLE_FLUIDS = fluid("magic/imp_evaporatable_fluids");

    private MagicCompatibilityTags() {
    }

    private static TagKey<EntityType<?>> entity(final String path) {
        return TagKey.create(Registries.ENTITY_TYPE, id(path));
    }

    private static TagKey<Item> item(final String path) {
        return TagKey.create(Registries.ITEM, id(path));
    }

    private static TagKey<Block> block(final String path) {
        return TagKey.create(Registries.BLOCK, id(path));
    }

    private static TagKey<Fluid> fluid(final String path) {
        return TagKey.create(Registries.FLUID, id(path));
    }

    private static Identifier id(final String path) {
        return Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path);
    }
}
