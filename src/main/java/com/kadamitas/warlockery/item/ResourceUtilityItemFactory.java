package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModFluids;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.FactoryCatalog;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ResourceUtilityItemFactory {
    private static final FactoryCatalog<Item.Properties, Item> FACTORIES = new FactoryCatalog<>(
        "resource utility item",
        Map.ofEntries(
            FactoryCatalog.entry("circletalisman", properties -> new CircleTalismanItem(properties.stacksTo(1))),
            FactoryCatalog.entry("bucketbrew", properties -> new BucketItem(
                ModFluids.COLORED_BREW_WATER_SOURCE,
                properties.stacksTo(1).craftRemainder(Items.BUCKET)
            )),
            FactoryCatalog.entry("bucketerosionbrew", properties -> new BucketItem(
                ModFluids.EROSION_SOURCE,
                properties.stacksTo(1).craftRemainder(Items.BUCKET)
            )),
            FactoryCatalog.entry("bucketspirit", properties -> new BucketItem(
                ModFluids.SPIRIT_SOURCE,
                properties.stacksTo(1).craftRemainder(Items.BUCKET)
            )),
            FactoryCatalog.entry("buckethollowtears", properties -> new BucketItem(
                ModFluids.HOLLOW_TEARS_SOURCE,
                properties.stacksTo(1).craftRemainder(Items.BUCKET)
            )),
            FactoryCatalog.entry("mutator", MutatingSprigItem::new),
            FactoryCatalog.entry("seedsdreamroot", properties ->
                new MinedrakeBulbItem(ModBlocks.ALL.get("dreamroot").get(), properties)),
            FactoryCatalog.entry("ingredient_apple_wormy", properties ->
                new ResourceFoodItem(properties, ResourceFoodItem.Profile.WORMY_APPLE)),
            FactoryCatalog.entry("ingredient_artichoke", WaterArtichokeGlobeItem::new),
            FactoryCatalog.entry("ingredient_attuned_stone", properties -> new AttunedStoneItem(properties, false)),
            FactoryCatalog.entry("ingredient_attuned_stone_charged", properties -> new AttunedStoneItem(properties, true)),
            FactoryCatalog.entry("ingredient_bat_ball", properties -> new BatBallItem(properties.stacksTo(1))),
            FactoryCatalog.entry("ingredient_berries_rowan", properties ->
                new ResourceFoodItem(properties, ResourceFoodItem.Profile.ROWAN_BERRIES)),
            FactoryCatalog.entry("ingredient_bone_needle", BoneNeedleItem::new),
            FactoryCatalog.entry("ingredient_creeper_heart", CreeperHeartItem::new),
            FactoryCatalog.entry("ingredient_graveyard_dust", GraveyardDustItem::new),
            FactoryCatalog.entry("ingredient_icy_needle", IcyNeedleItem::new),
            FactoryCatalog.entry("ingredient_purified_milk", PurifiedMilkItem::new),
            FactoryCatalog.entry("ingredient_bramble_colossus_seed", TreefydSeedItem::new),
            FactoryCatalog.entry("ingredient_rock", RockItem::new),
            FactoryCatalog.entry("ingredient_redstone_soup", RedstoneSoupItem::new),
            FactoryCatalog.entry("ingredient_sleeping_apple", NightmareAppleItem::new),
            FactoryCatalog.entry("ingredient_subdued_spirit", properties -> new VillageSpiritItem(properties, true)),
            FactoryCatalog.entry("ingredient_subdued_spirit_village", properties ->
                new VillageSpiritItem(properties.stacksTo(1), false)),
            FactoryCatalog.entry("ingredient_wolfsbane", WolfsbaneItem::new)
        )
    );

    private ResourceUtilityItemFactory() {
    }

    public static boolean supports(final String id) {
        return FACTORIES.supports(id);
    }

    public static Set<String> ids() {
        return FACTORIES.ids();
    }

    public static Item create(final Item.Properties properties, final String id) {
        return FACTORIES.create(id, properties);
    }
}
