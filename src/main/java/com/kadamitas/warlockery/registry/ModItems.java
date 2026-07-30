package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.brew.BrewFactory;
import com.kadamitas.warlockery.item.VerdantCatalystItem;
import com.kadamitas.warlockery.item.ChalkItem;
import com.kadamitas.warlockery.item.DollFactory;
import com.kadamitas.warlockery.item.DollKind;
import com.kadamitas.warlockery.item.KobolditeEquipmentFactory;
import com.kadamitas.warlockery.item.UtilityItemFactory;
import com.kadamitas.warlockery.item.SympatheticVialItem;
import com.kadamitas.warlockery.item.TransformationItem;
import com.kadamitas.warlockery.item.ArcaneFocusItem;
import com.kadamitas.warlockery.item.ResourceUtilityItemFactory;
import com.kadamitas.warlockery.item.UtilityDeviceItemFactory;
import com.kadamitas.warlockery.item.InfusedBrewItem;
import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, Warlockery.MOD_ID);
    private static final Map<String, RegistryObject<Item>> MUTABLE_ITEMS = new LinkedHashMap<>();
    private static final Set<String> SINGLE_STACK_ITEMS = Set.of(
        "ritual_knife", "boline", "canesword", "coffin", "deathscowl", "deathshand", "divinerlava", "divinerwater",
        "replication_staff", "hornofthehunt", "thorn_spear", "delvealloypickaxe", "mirror", "mysticbranch",
        "playercompass", "stonebrokers_quiver", "shelfcompass", "silversword", "sympathetic_vial", "arcane_focus"
    );
    private static final Map<String, String> SEED_TO_CROP = Map.of(
        "garlic", "garlicplant",
        "seedsartichoke", "artichoke",
        "seedsbelladonna", "belladonna",
        "seedsmandrake", "mandrake",
        "seedsdreamroot", "dreamroot",
        "seedssnowbell", "snowbell",
        "seedswolfsbane", "wolfsbane",
        "seedswormwood", "wormwood"
    );

    public static final Map<String, RegistryObject<Item>> ALL;

    static {
        ModBlocks.ALL.forEach((id, block) -> {
            if (!ContentCatalog.CROPS.contains(id) && !UtilityDeviceItemFactory.isInternalBlock(id)) {
                register(id, () -> new BlockItem(block.get(), properties(id)));
            }
        });

        ContentCatalog.ITEMS.stream()
            .map(ContentCatalog::modernize)
            .filter(id -> !MUTABLE_ITEMS.containsKey(id))
            .forEach(id -> {
                final String cropId = SEED_TO_CROP.get(id);
                if ("seedsdreamroot".equals(id)) {
                    register(id, () -> ResourceUtilityItemFactory.create(properties(id), id));
                } else if (cropId != null) {
                    register(id, () -> new BlockItem(ModBlocks.ALL.get(cropId).get(), properties(id)));
                } else if ("sympathetic_vial".equals(id)) {
                    register(id, () -> new SympatheticVialItem(properties(id)));
                } else if ("arcane_focus".equals(id)) {
                    register(id, () -> new ArcaneFocusItem(properties(id)));
                } else if (DollFactory.supports(id)) {
                    register(id, () -> DollFactory.create(properties(id), id));
                } else if (KobolditeEquipmentFactory.supports(id)) {
                    register(id, () -> KobolditeEquipmentFactory.create(properties(id), id));
                } else if (ResourceUtilityItemFactory.supports(id)) {
                    register(id, () -> ResourceUtilityItemFactory.create(properties(id), id));
                } else if (UtilityItemFactory.supports(id)) {
                    register(id, () -> UtilityItemFactory.create(properties(id), id));
                } else if (id.startsWith("chalk")) {
                    final String glyphId = switch (id) {
                        case "chalkinfernal" -> "circleglyphinfernal";
                        case "chalk_veil" -> "circleglyph_veil";
                        case "chalkritual" -> "circleglyphritual";
                        default -> "circle";
                    };
                    register(id, () -> new ChalkItem(properties(id).durability(64), ModBlocks.ALL.get(glyphId)));
                } else if ("mooncharm".equals(id) || "wolftoken".equals(id)) {
                    register(id, () -> new TransformationItem(properties(id), SupernaturalForm.WEREWOLF, true, false));
                } else {
                    register(id, () -> new Item(properties(id)));
                }
            });

        ContentCatalog.INGREDIENTS.forEach(catalogName -> {
            final String id = ContentCatalog.ingredientId(catalogName);
            if ("ingredient_brew_soaring".equals(id)) {
                register(id, () -> new Item(properties(id)));
            } else if (BrewFactory.supportsLegacy(id)) {
                register(id, () -> BrewFactory.createLegacy(properties(id), id));
            } else if (UtilityDeviceItemFactory.supports(id)) {
                register(id, () -> UtilityDeviceItemFactory.create(properties(id), id));
            } else if (ResourceUtilityItemFactory.supports(id)) {
                register(id, () -> ResourceUtilityItemFactory.create(properties(id), id));
            } else if (UtilityItemFactory.supports(id)) {
                register(id, () -> UtilityItemFactory.create(properties(id), id));
            } else if ("ingredient_verdant_catalyst".equals(id) || "ingredient_verdant_catalyst_prime".equals(id)) {
                register(id, () -> new VerdantCatalystItem(properties(id)));
            } else if ("ingredient_matriarchs_blood".equals(id)) {
                register(id, () -> new Item(properties(id)));
            } else if ("ingredient_purified_milk".equals(id)) {
                register(id, () -> new TransformationItem(properties(id), SupernaturalForm.NONE, false, true));
            } else if ("ingredient_brew_grave".equals(id)) {
                register(id, () -> new InfusedBrewItem(
                    properties(id),
                    MagicPath.GRAVE,
                    InfusedBrewItem.GRAVE_DURATION
                ));
            } else if (Set.of(
                "ingredient_bolt_anti_magic", "ingredient_bolt_holy", "ingredient_bolt_silver",
                "ingredient_bolt_splitting", "ingredient_bolt_stake"
            ).contains(id)) {
                register(id, () -> new ArrowItem(properties(id)));
            } else {
                register(id, () -> new Item(properties(id)));
            }
        });
        ContentCatalog.BREWS.forEach(id -> register(id, () -> BrewFactory.create(properties(id), id)));
        ALL = Collections.unmodifiableMap(MUTABLE_ITEMS);
    }

    private ModItems() {
    }

    private static void register(final String id, final java.util.function.Supplier<? extends Item> factory) {
        MUTABLE_ITEMS.put(id, REGISTRY.register(id, factory));
    }

    public static void registerSpawnEggs(final Map<String, RegistryObject<? extends net.minecraft.world.entity.EntityType<?>>> entityTypes) {
        entityTypes.forEach((id, type) -> register(id + "_spawn_egg",
            () -> new SpawnEggItem(new Item.Properties().setId(REGISTRY.key(id + "_spawn_egg")).spawnEgg(type.get()))));
    }

    private static Item.Properties properties(final String id) {
        Item.Properties properties = new Item.Properties().setId(REGISTRY.key(id));
        if ("sympathetic_vial".equals(id)) {
            properties = properties.component(DataComponents.LORE, new ItemLore(java.util.List.of(
                net.minecraft.network.chat.Component.translatable("tooltip.warlockery.sympathetic_vial.empty")
            )));
        } else {
            final Optional<DollKind> dollKind = DollKind.find(id);
            if (dollKind.isPresent()) {
                properties = properties.component(DataComponents.LORE, new ItemLore(java.util.List.of(
                    net.minecraft.network.chat.Component.translatable("tooltip.warlockery.doll.empty"),
                    net.minecraft.network.chat.Component.translatable(dollKind.orElseThrow().descriptionKey())
                )));
            }
        }
        properties = applyEquipmentProperties(id, properties);
        properties = applyFoodProperties(id, properties);
        properties = applyBrewProperties(id, properties);
        return SINGLE_STACK_ITEMS.contains(id) ? properties.stacksTo(1) : properties;
    }

    private static Item.Properties applyEquipmentProperties(final String id, final Item.Properties properties) {
        if (KobolditeEquipmentFactory.supports(id)) {
            return properties;
        }
        final Item.Properties toolProperties = switch (id) {
            case "ritual_knife", "boline", "canesword" -> properties.sword(ToolMaterial.IRON, 3.0F, -2.4F);
            case "silversword" -> properties.sword(SilverMaterials.TOOL, 3.0F, -2.4F);
            case "deathshand" -> properties.sword(ToolMaterial.DIAMOND, 4.0F, -2.2F);
            case "thorn_spear" -> properties.spear(
                ToolMaterial.IRON, 0.95F, 0.95F, 0.6F, 2.5F, 11.0F, 6.75F, 5.1F, 11.25F, 4.6F
            );
            default -> properties;
        };

        final Item.Properties equipmentProperties = armorType(id)
            .map(type -> toolProperties.humanoidArmor(armorMaterial(id), type))
            .orElse(toolProperties);
        return switch (id) {
            case "iceslippers" -> enchanted(equipmentProperties, Enchantments.FROST_WALKER, 2);
            case "emberstep_slippers" -> enchanted(equipmentProperties, Enchantments.FIRE_PROTECTION, 4);
            case "seepingshoes" -> enchanted(equipmentProperties, Enchantments.DEPTH_STRIDER, 3);
            default -> equipmentProperties;
        };
    }

    private static Item.Properties enchanted(
        final Item.Properties properties,
        final ResourceKey<Enchantment> enchantment,
        final int level
    ) {
        return properties.delayedComponent(DataComponents.ENCHANTMENTS, registries -> {
            final ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            enchantments.set(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), level);
            return enchantments.toImmutable();
        });
    }

    private static Optional<ArmorType> armorType(final String id) {
        if (id.contains("hat") || id.endsWith("helm") || id.endsWith("helmet")
            || Set.of("deathscowl", "earmuffs", "hellhound_head", "twisting_band", "wolfhead").contains(id)) {
            return Optional.of(ArmorType.HELMET);
        }
        if (id.contains("coat") || id.endsWith("robe")) {
            return Optional.of(ArmorType.CHESTPLATE);
        }
        if (id.contains("legs") || Set.of(
            "barkbelt", "bitingbelt", "forgewardens_girdle", "stonebrokers_quiver"
        ).contains(id)) {
            return Optional.of(ArmorType.LEGGINGS);
        }
        if (id.contains("boots") || Set.of(
            "deathsfeet", "iceslippers", "emberstep_slippers", "seepingshoes", "ruby_slippers"
        ).contains(id)) {
            return Optional.of(ArmorType.BOOTS);
        }
        return Optional.empty();
    }

    private static ArmorMaterial armorMaterial(final String id) {
        if (id.startsWith("werewolf_hunter_")) {
            return HunterArmorMaterials.forItem(id);
        }
        if (id.contains("_dawn")) {
            return ArmorMaterials.DIAMOND;
        }
        if (id.contains("silvered") || id.contains("chain")) {
            return ArmorMaterials.IRON;
        }
        if (id.startsWith("death")) {
            return ArmorMaterials.DIAMOND;
        }
        return ArmorMaterials.LEATHER;
    }

    private static Item.Properties applyFoodProperties(final String id, final Item.Properties properties) {
        return switch (id) {
            case "stew" -> properties.stacksTo(1).food(food(10, 0.8F)).usingConvertsTo(Items.BOWL);
            case "stewraw" -> properties.stacksTo(1).food(food(4, 0.3F)).usingConvertsTo(Items.BOWL);
            case "ingredient_odd_porkchop_raw" -> properties.food(food(3, 0.3F));
            case "ingredient_odd_porkchop_cooked" -> properties.food(food(8, 0.8F));
            case "ingredient_sleeping_apple" -> properties.food(food(4, 0.3F)).component(
                DataComponents.CONSUMABLE,
                Consumable.builder()
                    .consumeSeconds(1.6F)
                    .animation(ItemUseAnimation.EAT)
                    .sound(SoundEvents.GENERIC_EAT)
                    .hasConsumeParticles(true)
                    .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.SLOWNESS, 300, 1)))
                    .build()
            );
            default -> properties;
        };
    }

    private static FoodProperties food(final int nutrition, final float saturation) {
        return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
    }

    private static Item.Properties applyBrewProperties(final String id, final Item.Properties properties) {
        return brewEffect(id).map(effect -> properties
            .stacksTo(16)
            .usingConvertsTo(Items.GLASS_BOTTLE)
            .component(
                DataComponents.CONSUMABLE,
                Consumable.builder()
                    .consumeSeconds(1.6F)
                    .animation(ItemUseAnimation.DRINK)
                    .sound(SoundEvents.GENERIC_DRINK)
                    .hasConsumeParticles(false)
                    .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(effect, brewDuration(id), 0)))
                    .build()
            ))
            .orElse(properties);
    }

    private static Optional<Holder<MobEffect>> brewEffect(final String id) {
        return Optional.ofNullable(switch (id) {
            case "ingredient_brew_bats" -> MobEffects.NIGHT_VISION;
            case "ingredient_brew_depths" -> MobEffects.WATER_BREATHING;
            case "ingredient_brew_grave", "ingredient_brew_congealed_spirit" -> MobEffects.INVISIBILITY;
            case "ingredient_brew_love" -> MobEffects.REGENERATION;
            case "ingredient_brew_raising" -> MobEffects.STRENGTH;
            case "ingredient_brew_revealing" -> MobEffects.GLOWING;
            case "ingredient_brew_soaring" -> ModEffects.SOARING.getHolder().orElseThrow();
            case "ingredient_brew_grotesque" -> MobEffects.RESISTANCE;
            case "ingredient_brew_murder_of_crows" -> MobEffects.BLINDNESS;
            case "ingredient_brew_ice", "ingredient_brew_sleep" -> MobEffects.SLOWNESS;
            case "ingredient_brew_infection", "ingredient_brew_wasting" -> MobEffects.POISON;
            case "ingredient_brew_soul_anguish", "ingredient_brew_soul_fear", "ingredient_brew_soul_torment" -> MobEffects.WEAKNESS;
            case "ingredient_brew_soul_hunger" -> MobEffects.HUNGER;
            default -> null;
        });
    }

    private static int brewDuration(final String id) {
        return "ingredient_brew_soaring".equals(id) ? 20 * 60 * 120 : 20 * 90;
    }

    public static Optional<ItemLike> seedFor(final Block crop) {
        return SEED_TO_CROP.entrySet().stream()
            .filter(entry -> ModBlocks.ALL.get(entry.getValue()).get() == crop)
            .map(entry -> (ItemLike) MUTABLE_ITEMS.get(entry.getKey()).get())
            .findFirst();
    }
}
