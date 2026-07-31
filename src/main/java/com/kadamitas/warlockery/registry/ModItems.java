package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.brew.BrewFactory;
import com.kadamitas.warlockery.block.ConnectedGlyphBlock;
import com.kadamitas.warlockery.block.DreamWeaverBlock;
import com.kadamitas.warlockery.block.DreamWeaverMode;
import com.kadamitas.warlockery.item.VerdantCatalystItem;
import com.kadamitas.warlockery.item.CaneSwordItem;
import com.kadamitas.warlockery.item.ChalkItem;
import com.kadamitas.warlockery.item.DollFactory;
import com.kadamitas.warlockery.item.DollKind;
import com.kadamitas.warlockery.item.GobliniteEquipmentFactory;
import com.kadamitas.warlockery.item.SilverEquipmentFactory;
import com.kadamitas.warlockery.item.WeddingRingItem;
import com.kadamitas.warlockery.item.UtilityItemFactory;
import com.kadamitas.warlockery.item.SympatheticVialItem;
import com.kadamitas.warlockery.item.ArcaneFocusItem;
import com.kadamitas.warlockery.item.ResourceUtilityItemFactory;
import com.kadamitas.warlockery.item.UtilityDeviceItemFactory;
import com.kadamitas.warlockery.item.InfusedBrewItem;
import com.kadamitas.warlockery.item.MoonCharmItem;
import com.kadamitas.warlockery.item.ParasyticLouseItem;
import com.kadamitas.warlockery.item.SplittingBoltItem;
import com.kadamitas.warlockery.magic.MagicPath;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
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
import net.minecraft.world.item.component.BlockItemStateProperties;
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
        "playercompass", "stonebrokers_quiver", "shelfcompass", "silversword", "sympathetic_vial", "arcane_focus",
        "louse"
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
    private static final Map<String, DreamWeaverMode> DREAM_WEAVER_MODES = DreamWeaverMode.VALUES.stream()
        .collect(java.util.stream.Collectors.toUnmodifiableMap(DreamWeaverMode::itemId, mode -> mode));
    private static final Map<String, String> CHALK_GLYPHS = Map.of(
        "chalkinfernal", "circleglyphinfernal",
        "chalk_veil", "circleglyph_veil",
        "chalkritual", "circleglyphritual"
    );
    private static final Set<String> ARROW_IDS = Set.of(
        "ingredient_bolt_anti_magic",
        "ingredient_bolt_holy",
        "ingredient_bolt_silver",
        "ingredient_bolt_stake"
    );
    private static final List<ItemFactoryRule> CATALOG_FACTORY_RULES = List.of(
        ItemFactoryRule.exact("seedsdreamroot", (id, properties) ->
            ResourceUtilityItemFactory.create(properties, id)),
        new ItemFactoryRule(SEED_TO_CROP::containsKey, (id, properties) ->
            new BlockItem(ModBlocks.ALL.get(SEED_TO_CROP.get(id)).get(), properties)),
        ItemFactoryRule.exact("sympathetic_vial", (_, properties) -> new SympatheticVialItem(properties)),
        ItemFactoryRule.exact("arcane_focus", (_, properties) -> new ArcaneFocusItem(properties)),
        new ItemFactoryRule(DREAM_WEAVER_MODES::containsKey, ModItems::createDreamWeaver),
        ItemFactoryRule.exact("louse", (_, properties) -> new ParasyticLouseItem(properties)),
        new ItemFactoryRule(DollFactory::supports, (id, properties) -> DollFactory.create(properties, id)),
        new ItemFactoryRule(GobliniteEquipmentFactory::supports, (id, properties) ->
            GobliniteEquipmentFactory.create(properties, id)),
        new ItemFactoryRule(SilverEquipmentFactory::supports, (id, properties) ->
            SilverEquipmentFactory.create(properties, id)),
        ItemFactoryRule.exact("wedding_ring", (_, properties) -> new WeddingRingItem(properties)),
        new ItemFactoryRule(ResourceUtilityItemFactory::supports, (id, properties) ->
            ResourceUtilityItemFactory.create(properties, id)),
        new ItemFactoryRule(UtilityItemFactory::supports, (id, properties) ->
            UtilityItemFactory.create(properties, id)),
        new ItemFactoryRule(id -> id.startsWith("chalk"), ModItems::createChalk),
        ItemFactoryRule.exact("mooncharm", (_, properties) -> new MoonCharmItem(properties))
    );
    private static final List<ItemFactoryRule> INGREDIENT_FACTORY_RULES = List.of(
        ItemFactoryRule.exact("ingredient_brew_soaring", (_, properties) -> new Item(properties)),
        new ItemFactoryRule(BrewFactory::supportsLegacy, (id, properties) -> BrewFactory.createLegacy(properties, id)),
        new ItemFactoryRule(UtilityDeviceItemFactory::supports, (id, properties) ->
            UtilityDeviceItemFactory.create(properties, id)),
        new ItemFactoryRule(ResourceUtilityItemFactory::supports, (id, properties) ->
            ResourceUtilityItemFactory.create(properties, id)),
        new ItemFactoryRule(UtilityItemFactory::supports, (id, properties) ->
            UtilityItemFactory.create(properties, id)),
        new ItemFactoryRule(
            id -> Set.of("ingredient_verdant_catalyst", "ingredient_verdant_catalyst_prime").contains(id),
            (id, properties) -> new VerdantCatalystItem(properties, id.endsWith("_prime"))
        ),
        ItemFactoryRule.exact("ingredient_matriarchs_blood", (_, properties) -> new Item(properties)),
        ItemFactoryRule.exact("ingredient_brew_grave", (_, properties) -> new InfusedBrewItem(
            properties,
            MagicPath.GRAVE,
            InfusedBrewItem.GRAVE_DURATION
        )),
        ItemFactoryRule.exact("ingredient_bolt_splitting", (_, properties) -> new SplittingBoltItem(properties))
    );

    public static final Map<String, RegistryObject<Item>> ALL;

    static {
        ModBlocks.ALL.forEach((id, block) -> {
            if (!ContentCatalog.CROPS.contains(id)
                && !ConnectedGlyphBlock.supports(id)
                && !UtilityDeviceItemFactory.isInternalBlock(id)) {
                register(id, () -> new BlockItem(block.get(), properties(id)));
            }
        });

        ContentCatalog.ITEMS.stream()
            .map(ContentCatalog::modernize)
            .filter(id -> !MUTABLE_ITEMS.containsKey(id))
            .forEach(id -> register(id, () -> createCatalogItem(id)));

        ContentCatalog.INGREDIENTS.forEach(catalogName -> {
            final String id = ContentCatalog.ingredientId(catalogName);
            register(id, () -> createIngredient(id));
        });
        ContentCatalog.BREWS.forEach(id -> register(id, () -> BrewFactory.create(properties(id), id)));
        ALL = Collections.unmodifiableMap(MUTABLE_ITEMS);
    }

    private ModItems() {
    }

    private static Item createCatalogItem(final String id) {
        if ("canesword".equals(id)) {
            return new CaneSwordItem(properties(id));
        }
        final Item.Properties properties = properties(id);
        return resolve(CATALOG_FACTORY_RULES, id, properties).orElseGet(() -> new Item(properties));
    }

    private static Item createIngredient(final String id) {
        if (ARROW_IDS.contains(id)) {
            return new ArrowItem(properties(id));
        }
        final Item.Properties properties = properties(id);
        return resolve(INGREDIENT_FACTORY_RULES, id, properties).orElseGet(() -> new Item(properties));
    }

    private static Item createDreamWeaver(final String id, final Item.Properties properties) {
        return new BlockItem(
            ModBlocks.ALL.get("dreamcatcher").get(),
            properties.component(
                DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY.with(DreamWeaverBlock.MODE, DREAM_WEAVER_MODES.get(id))
            )
        );
    }

    private static Item createChalk(final String id, final Item.Properties properties) {
        if ("chalkheart".equals(id)) {
            return new ChalkItem(properties.durability(64), context ->
                ModBlocks.ALL.get(context.isSecondaryUseActive() ? "circleglyphgolden" : "circle").get());
        }
        final String glyphId = Optional.ofNullable(CHALK_GLYPHS.get(id))
            .orElseThrow(() -> new IllegalArgumentException("Unknown chalk item: " + id));
        return new ChalkItem(properties.durability(64), ModBlocks.ALL.get(glyphId));
    }

    private static Optional<Item> resolve(
        final List<ItemFactoryRule> rules,
        final String id,
        final Item.Properties properties
    ) {
        return rules.stream()
            .filter(rule -> rule.supports(id))
            .findFirst()
            .map(rule -> rule.create(id, properties));
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
        if (GobliniteEquipmentFactory.supports(id) || SilverEquipmentFactory.supports(id)) {
            return properties;
        }
        final Item.Properties toolProperties = switch (id) {
            case "ritual_knife", "boline" -> properties.sword(ToolMaterial.IRON, 3.0F, -2.4F);
            case "canesword" -> CaneSwordItem.applyProperties(properties);
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
            case "deathsfeet" -> enchanted(equipmentProperties, Map.of(
                Enchantments.FROST_WALKER, 2,
                Enchantments.FIRE_PROTECTION, 4
            ));
            default -> equipmentProperties;
        };
    }

    private static Item.Properties enchanted(
        final Item.Properties properties,
        final ResourceKey<Enchantment> enchantment,
        final int level
    ) {
        return enchanted(properties, Map.of(enchantment, level));
    }

    private static Item.Properties enchanted(
        final Item.Properties properties,
        final Map<ResourceKey<Enchantment>, Integer> enchantmentsToApply
    ) {
        return properties.delayedComponent(DataComponents.ENCHANTMENTS, registries -> {
            final ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            enchantmentsToApply.forEach((enchantment, level) -> enchantments.set(
                registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment),
                level
            ));
            return enchantments.toImmutable();
        });
    }

    private static Optional<ArmorType> armorType(final String id) {
        if (id.contains("hat") || id.endsWith("helm") || id.endsWith("helmet")
            || Set.of("deathscowl", "earmuffs", "hellhound_head", "twisting_band", "wolfhead").contains(id)) {
            return Optional.of(ArmorType.HELMET);
        }
        if (id.contains("coat") || id.endsWith("robe") || id.equals("stonebrokers_quiver")) {
            return Optional.of(ArmorType.CHESTPLATE);
        }
        if (id.contains("legs") || Set.of(
            "barkbelt", "bitingbelt", "forgewardens_girdle"
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

    private record ItemFactoryRule(
        Predicate<String> selector,
        BiFunction<String, Item.Properties, Item> factory
    ) {
        private static ItemFactoryRule exact(
            final String id,
            final BiFunction<String, Item.Properties, Item> factory
        ) {
            return new ItemFactoryRule(id::equals, factory);
        }

        private boolean supports(final String id) {
            return selector.test(id);
        }

        private Item create(final String id, final Item.Properties properties) {
            return factory.apply(id, properties);
        }
    }
}
