package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

public final class WarlockeryTags {
    private WarlockeryTags() {
    }

    public static final class Blocks {
        public static final TagKey<Block> MACHINE_HEAT_SOURCES = create("machine_heat_sources");
        public static final TagKey<Block> RITUAL_ORES = create("ritual_ores");
        public static final TagKey<Block> RITUAL_CROPS = create("ritual_crops");
        public static final TagKey<Block> RITUAL_FLOWERS = create("ritual_flowers");
        public static final TagKey<Block> RITUAL_LOGS = create("ritual_logs");
        public static final TagKey<Block> RITUAL_LEAVES = create("ritual_leaves");
        public static final TagKey<Block> RITUAL_SAPLINGS = create("ritual_saplings");
        public static final TagKey<Block> RITUAL_GROWABLES = create("ritual_growables");
        public static final TagKey<Block> RITUAL_STONES = create("ritual_stones");
        public static final TagKey<Block> BLIGHT_VEGETATION = create("blight_vegetation");
        public static final TagKey<Block> BLIGHT_SOILS = create("blight_soils");
        public static final TagKey<Block> LIVING_GROUND = create("living_ground");
        public static final TagKey<Block> CHALK_GLYPHS = create("chalk_glyphs");
        public static final TagKey<Block> HOBGOBLIN_MINEABLES = create("hobgoblin_mineables");
        public static final TagKey<Block> HOBGOBLIN_AUTO_SMELTABLE_ORES = create("hobgoblin_auto_smeltable_ores");
        public static final TagKey<Block> NIGHTMARE_HARVEST_PLANTS = create("nightmare_harvest_plants");
        public static final TagKey<Block> NATURE_REPAIRABLE_SOILS = create("nature_repairable_soils");
        public static final TagKey<Block> NATURE_DAMAGED_VEGETATION = create("nature_damaged_vegetation");
        public static final TagKey<Block> FISSURE_BREAKABLES = create("fissure_breakables");
        public static final TagKey<Block> CONTROLLED_FIRE_SUPPORTS = create("controlled_fire_supports");
        public static final TagKey<Block> GLINT_WEED_SPREADABLE_GROUND = create("glint_weed_spreadable_ground");
        public static final TagKey<Block> EMBER_MOSS_SPREADABLE_GROUND = create("ember_moss_spreadable_ground");
        public static final TagKey<Block> ENDER_BRAMBLE_TELEPORT_GROUND = create("ender_bramble_teleport_ground");
        public static final TagKey<Block> PLANT_MINE_GROWABLES = create("plant_mine_growables");
        public static final TagKey<Block> PLANT_MINE_GROWTH_GROUND = create("plant_mine_growth_ground");
        public static final TagKey<Block> PLANT_MINE_CACTUS_GROUND = create("plant_mine_cactus_ground");
        public static final TagKey<Block> PLANT_MINE_THORN_GROUND = create("plant_mine_thorn_ground");
        public static final TagKey<Block> PLANT_MINE_WEB_SUPPORTS = create("plant_mine_web_supports");
        public static final TagKey<Block> BREW_ERODIBLE = create("brew_erodible");
        public static final TagKey<Block> BREW_LEVELABLE_TERRAIN = create("brew_levelable_terrain");
        public static final TagKey<Block> BREW_PULVERIZABLE_ROCK = create("brew_pulverizable_rock");
        public static final TagKey<Block> BREW_TRANSPOSABLE_ORES = create("brew_transposable_ores");
        public static final TagKey<Block> BREW_THORN_SUPPORTS = create("brew_thorn_supports");
        public static final TagKey<Block> ANOINTABLE_CAULDRONS = create("anointable_cauldrons");
        public static final TagKey<Block> ALTAR_CANDELABRA_UPGRADES = create("altar_upgrades/candelabra");
        public static final TagKey<Block> ALTAR_CHALICE_UPGRADES = create("altar_upgrades/chalice");
        public static final TagKey<Block> ALTAR_PENTACLE_UPGRADES = create("altar_upgrades/pentacle");
        public static final TagKey<Block> FUME_FUNNELS = create("machine_upgrades/fume_funnels");
        public static final TagKey<Block> FILTERED_FUME_FUNNELS = create("machine_upgrades/filtered_fume_funnels");
        public static final TagKey<Block> RITUAL_INHIBITORS = create("ritual_inhibitors");

        private Blocks() {
        }

        private static TagKey<Block> create(final String path) {
            return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
        }
    }

    public static final class EntityTypes {
        public static final TagKey<EntityType<?>> DEMONS = create("demons");
        public static final TagKey<EntityType<?>> SPECTRAL = create("spectral");
        public static final TagKey<EntityType<?>> VAMPIRES = create("vampires");
        public static final TagKey<EntityType<?>> WEREWOLVES = create("werewolves");
        public static final TagKey<EntityType<?>> RITUAL_BEASTS = create("ritual_beasts");
        public static final TagKey<EntityType<?>> BLIGHT_VICTIMS = create("blight_victims");
        public static final TagKey<EntityType<?>> NIGHTMARES = create("nightmares");
        public static final TagKey<EntityType<?>> WOLF_FORM_LAMB_SOURCES = create("wolf_form_lamb_sources");
        public static final TagKey<EntityType<?>> FERTILITY_FAMILIARS = create("fertility_familiars");
        public static final TagKey<EntityType<?>> EMBER_MOSS_IMMUNE = create("ember_moss_immune");
        public static final TagKey<EntityType<?>> ENDER_BRAMBLE_IMMUNE = create("ender_bramble_immune");
        public static final TagKey<EntityType<?>> PLANT_MINE_IMMUNE = create("plant_mine_immune");
        public static final TagKey<EntityType<?>> INSANITY_THREATS = create("insanity_threats");
        public static final TagKey<EntityType<?>> WAKING_NIGHTMARE_THREATS = create("waking_nightmare_threats");
        public static final TagKey<EntityType<?>> HEX_TOADS = create("hex_toads");
        public static final TagKey<EntityType<?>> ALLURING_SKULL_TARGETS = create("alluring_skull_targets");
        public static final TagKey<EntityType<?>> BEARTRAP_IMMUNE = create("beartrap_immune");
        public static final TagKey<EntityType<?>> HOLLOW_TEARS_BENEFICIARIES = create("hollow_tears_beneficiaries");
        public static final TagKey<EntityType<?>> HOLLOW_TEARS_VICTIMS = create("hollow_tears_victims");
        public static final TagKey<EntityType<?>> NECROMANTIC_COMMANDABLES = create("necromantic_commandables");
        public static final TagKey<EntityType<?>> SUNLIGHT_VULNERABLE = create("sunlight_vulnerable");
        public static final TagKey<EntityType<?>> DISEASE_IMMUNE = create("disease_immune");
        public static final TagKey<EntityType<?>> ARTHANA_BAT_SOURCES = create("arthana_bat_sources");
        public static final TagKey<EntityType<?>> ARTHANA_DOG_SOURCES = create("arthana_dog_sources");
        public static final TagKey<EntityType<?>> ARTHANA_OWL_SOURCES = create("arthana_owl_sources");
        public static final TagKey<EntityType<?>> ARTHANA_FROG_SOURCES = create("arthana_frog_sources");
        public static final TagKey<EntityType<?>> ARTHANA_HEART_SOURCES = create("arthana_heart_sources");
        public static final TagKey<EntityType<?>> ARTHANA_SPECTRAL_SOURCES = create("arthana_spectral_sources");

        private EntityTypes() {
        }

        private static TagKey<EntityType<?>> create(final String path) {
            return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
        }
    }

    public static final class Fluids {
        public static final TagKey<Fluid> VOLCANIC_FLUIDS = create("volcanic_fluids");
        public static final TagKey<Fluid> SINKING_FLUIDS = create("sinking_fluids");
        public static final TagKey<Fluid> HOLLOW_TEARS = create("hollow_tears");

        private Fluids() {
        }

        private static TagKey<Fluid> create(final String path) {
            return TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
        }
    }

    public static final class Biomes {
        public static final TagKey<Biome> OVERHEATING = create("overheating");

        private Biomes() {
        }

        private static TagKey<Biome> create(final String path) {
            return TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
        }
    }

    public static final class Items {
        public static final TagKey<Item> SILVER_WEAPONS = create("silver_weapons");
        public static final TagKey<Item> SILVER_PROJECTILES = create("silver_projectiles");
        public static final TagKey<Item> SUPERNATURAL_WEAKNESSES = create("supernatural_weaknesses");
        public static final TagKey<Item> BREWS = create("brews");
        public static final TagKey<Item> THROWABLE_BREWS = create("throwable_brews");
        public static final TagKey<Item> WARLOCK_HUNTER_ARMOR = create("warlock_hunter_armor");
        public static final TagKey<Item> SILVERED_HUNTER_ARMOR = create("silvered_hunter_armor");
        public static final TagKey<Item> DAWN_HUNTER_ARMOR = create("dawn_hunter_armor");
        public static final TagKey<Item> DEATH_DISGUISE_ARMOR = create("death_disguise_armor");
        public static final TagKey<Item> POISON_REDIRECTING_FOOTWEAR = create("poison_redirecting_footwear");
        public static final TagKey<Item> BARK_ARMOR = create("bark_armor");
        public static final TagKey<Item> BITING_ARMOR = create("biting_armor");
        public static final TagKey<Item> UNARMED_POWER_ARMOR = create("unarmed_power_armor");
        public static final TagKey<Item> ARCHERY_ARMOR = create("archery_armor");
        public static final TagKey<Item> NECROMANCER_GARB = create("necromancer_garb");
        public static final TagKey<Item> TWISTING_BANDS = create("twisting_bands");
        public static final TagKey<Item> BAT_BINDING_FIBERS = create("bat_binding_fibers");
        public static final TagKey<Item> DISTURBED_FIBERS = create("disturbed_fibers");
        public static final TagKey<Item> LUCK_ESSENCES = create("luck_essences");
        public static final TagKey<Item> LUCK_CATALYSTS = create("luck_catalysts");
        public static final TagKey<Item> THROWING_STONES = create("throwing_stones");
        public static final TagKey<Item> VILLAGE_SPIRITS = create("village_spirits");
        public static final TagKey<Item> WOLF_FORM_MEATS = create("wolf_form_meats");
        public static final TagKey<Item> ROWAN_DOOR_KEYS = create("rowan_door_keys");
        public static final TagKey<Item> PLANT_MINE_INK_PAYLOADS = create("plant_mine_payloads/ink");
        public static final TagKey<Item> PLANT_MINE_SPROUTING_PAYLOADS = create("plant_mine_payloads/sprouting");
        public static final TagKey<Item> PLANT_MINE_THORNS_PAYLOADS = create("plant_mine_payloads/thorns");
        public static final TagKey<Item> PLANT_MINE_WEBS_PAYLOADS = create("plant_mine_payloads/webs");
        public static final TagKey<Item> HOBGOBLIN_MINING_TOOLS = create("hobgoblin_mining_tools");
        public static final TagKey<Item> ENHANCED_HOBGOBLIN_MINING_TOOLS = create("enhanced_hobgoblin_mining_tools");
        public static final TagKey<Item> ALLURING_SKULL_ACTIVATORS = create("alluring_skull_activators");
        public static final TagKey<Item> CHALICE_FILLERS = create("chalice_fillers");
        public static final TagKey<Item> ALTAR_CANDELABRA_UPGRADES = create("altar_upgrades/candelabra");
        public static final TagKey<Item> ALTAR_CHALICE_UPGRADES = create("altar_upgrades/chalice");
        public static final TagKey<Item> ALTAR_PENTACLE_UPGRADES = create("altar_upgrades/pentacle");
        public static final TagKey<Item> ALCHEMICAL_FUMES = create("alchemical_fumes");
        public static final TagKey<Item> DIVINATION_CATALYSTS = create("divination_catalysts");
        public static final TagKey<Item> DIVINATION_TARGETS = create("divination_targets");
        public static final TagKey<Item> MANUALS = create("manuals");
        public static final TagKey<Item> NECROMANTIC_FOCI = create("necromantic_foci");
        public static final TagKey<Item> PATRON_OFFERINGS = create("patron_offerings");
        public static final TagKey<Item> SOLAR_CHARGEABLES = create("solar_chargeables");
        public static final TagKey<Item> DEATH_WEAPONS = create("death_weapons");
        public static final TagKey<Item> SOUND_DAMPENING_ARMOR = create("sound_dampening_armor");
        public static final TagKey<Item> BREWING_GARB = create("brewing_garb");
        public static final TagKey<Item> BLOOD_SOURCES = create("blood_sources");
        public static final TagKey<Item> SYMPATHETIC_CONTAINERS = create("sympathetic_containers");
        public static final TagKey<Item> MIRROR_TOOLS = create("mirror_tools");
        public static final TagKey<Item> TRENT_OFFERINGS = create("trent_offerings");
        public static final TagKey<Item> WOLF_ALTAR_HEADS = create("wolf_altar_heads");
        public static final TagKey<Item> WOLF_ALTAR_OFFERINGS = create("wolf_altar_offerings");
        public static final TagKey<Item> DOLLS = create("dolls");
        public static final TagKey<Item> BABA_YAGA_SUMMONERS = create("baba_yaga_summoners");
        public static final TagKey<Item> BRAZIER_IGNITERS = create("brazier_igniters");
        public static final TagKey<Item> ARTHANAS = create("arthanas");
        public static final TagKey<Item> ALTAR_RANGE_FOCI = create("altar_range_foci");
        public static final TagKey<Item> NIGHTMARE_GUARD_CHARMS = create("nightmare_guard_charms");

        private Items() {
        }

        private static TagKey<Item> create(final String path) {
            return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
        }
    }
}
