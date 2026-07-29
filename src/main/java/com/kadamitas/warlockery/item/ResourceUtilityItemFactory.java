package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModFluids;
import com.kadamitas.warlockery.registry.ModBlocks;
import java.util.Set;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ResourceUtilityItemFactory {
    private static final Set<String> SUPPORTED = Set.of(
        "circletalisman",
        "bucketbrew",
        "bucketerosionbrew",
        "bucketspirit",
        "buckethollowtears",
        "mutator",
        "seedsdreamroot",
        "ingredient_apple_wormy",
        "ingredient_attuned_stone",
        "ingredient_attuned_stone_charged",
        "ingredient_bat_ball",
        "ingredient_berries_rowan",
        "ingredient_bone_needle",
        "ingredient_icy_needle",
        "ingredient_bramble_colossus_seed",
        "ingredient_rock",
        "ingredient_redstone_soup",
        "ingredient_sleeping_apple",
        "ingredient_subdued_spirit",
        "ingredient_subdued_spirit_village",
        "ingredient_wolfsbane"
    );

    private ResourceUtilityItemFactory() {
    }

    public static boolean supports(final String id) {
        return SUPPORTED.contains(id);
    }

    public static Set<String> ids() {
        return SUPPORTED;
    }

    public static Item create(final Item.Properties properties, final String id) {
        return switch (id) {
            case "circletalisman" -> new CircleTalismanItem(properties.stacksTo(1));
            case "bucketbrew" -> new BucketItem(
                ModFluids.COLORED_BREW_WATER_SOURCE,
                properties.stacksTo(1).craftRemainder(Items.BUCKET)
            );
            case "bucketerosionbrew" -> new BucketItem(
                ModFluids.EROSION_SOURCE,
                properties.stacksTo(1).craftRemainder(Items.BUCKET)
            );
            case "bucketspirit" -> new BucketItem(
                ModFluids.SPIRIT_SOURCE,
                properties.stacksTo(1).craftRemainder(Items.BUCKET)
            );
            case "buckethollowtears" -> new BucketItem(
                ModFluids.HOLLOW_TEARS_SOURCE,
                properties.stacksTo(1).craftRemainder(Items.BUCKET)
            );
            case "mutator" -> new MutatingSprigItem(properties);
            case "seedsdreamroot" -> new MinedrakeBulbItem(ModBlocks.ALL.get("dreamroot").get(), properties);
            case "ingredient_apple_wormy" -> new ResourceFoodItem(properties, ResourceFoodItem.Profile.WORMY_APPLE);
            case "ingredient_attuned_stone" -> new AttunedStoneItem(properties, false);
            case "ingredient_attuned_stone_charged" -> new AttunedStoneItem(properties, true);
            case "ingredient_bat_ball" -> new BatBallItem(properties.stacksTo(1));
            case "ingredient_berries_rowan" -> new ResourceFoodItem(properties, ResourceFoodItem.Profile.ROWAN_BERRIES);
            case "ingredient_bone_needle" -> new BoneNeedleItem(properties);
            case "ingredient_icy_needle" -> new IcyNeedleItem(properties);
            case "ingredient_bramble_colossus_seed" -> new TreefydSeedItem(properties);
            case "ingredient_rock" -> new RockItem(properties);
            case "ingredient_redstone_soup" -> new RedstoneSoupItem(properties);
            case "ingredient_sleeping_apple" -> new NightmareAppleItem(properties);
            case "ingredient_subdued_spirit" -> new VillageSpiritItem(properties, true);
            case "ingredient_subdued_spirit_village" -> new VillageSpiritItem(properties.stacksTo(1), false);
            case "ingredient_wolfsbane" -> new WolfsbaneItem(properties);
            default -> throw new IllegalArgumentException("Unsupported resource utility item: " + id);
        };
    }
}
