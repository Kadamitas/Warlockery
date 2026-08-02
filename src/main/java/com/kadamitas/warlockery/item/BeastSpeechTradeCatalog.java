package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.entity.CreatureBehaviorTags;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.util.WeightedPool;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public final class BeastSpeechTradeCatalog {
    private static final List<String> HERBAL_REWARDS = List.of(
        "seedsbelladonna",
        "seedsmandrake",
        "seedsartichoke",
        "seedssnowbell",
        "seedswormwood"
    );
    private static final Map<Partner, WeightedPool<RewardSpec>> REWARDS = Map.ofEntries(
        Map.entry(Partner.DEMON, pool(
            reward(mod("ingredient_infernal_blood"), 1, 1, 30),
            reward(vanilla("blaze_powder", Items.BLAZE_POWDER), 2, 4, 20),
            reward(vanilla("magma_cream", Items.MAGMA_CREAM), 1, 3, 14),
            reward(vanilla("ghast_tear", Items.GHAST_TEAR), 1, 2, 10),
            reward(vanilla("crying_obsidian", Items.CRYING_OBSIDIAN), 1, 2, 9),
            reward(vanilla("echo_shard", Items.ECHO_SHARD), 1, 2, 6),
            reward(vanilla("ominous_bottle", Items.OMINOUS_BOTTLE), 1, 1, 4),
            reward(vanilla("trial_key", Items.TRIAL_KEY), 1, 1, 3),
            reward(vanilla("ominous_trial_key", Items.OMINOUS_TRIAL_KEY), 1, 1, 1)
        )),
        Map.entry(Partner.BAT, pool(
            reward(mod("ingredient_bat_wool"), 1, 2, 24),
            reward(vanilla("phantom_membrane", Items.PHANTOM_MEMBRANE), 1, 1, 6),
            reward(vanilla("echo_shard", Items.ECHO_SHARD), 1, 1, 1)
        )),
        Map.entry(Partner.WOLF, pool(
            reward(mod("ingredient_dog_tongue"), 1, 1, 20),
            reward(vanilla("bone", Items.BONE), 1, 3, 14),
            reward(vanilla("rabbit_hide", Items.RABBIT_HIDE), 1, 2, 5)
        )),
        Map.entry(Partner.CAT, pool(
            reward(vanilla("string", Items.STRING), 1, 3, 18),
            reward(vanilla("rabbit_foot", Items.RABBIT_FOOT), 1, 1, 5),
            reward(vanilla("phantom_membrane", Items.PHANTOM_MEMBRANE), 1, 1, 2)
        )),
        Map.entry(Partner.COW, pool(
            reward(vanilla("leather", Items.LEATHER), 1, 3, 18),
            reward(vanilla("beef", Items.BEEF), 1, 2, 10),
            reward(vanilla("bone_meal", Items.BONE_MEAL), 2, 5, 6)
        )),
        Map.entry(Partner.CHICKEN, pool(
            reward(vanilla("feather", Items.FEATHER), 2, 5, 20),
            reward(vanilla("egg", Items.EGG), 1, 3, 12),
            reward(vanilla("phantom_membrane", Items.PHANTOM_MEMBRANE), 1, 1, 1)
        )),
        Map.entry(Partner.SHEEP, pool(
            reward(vanilla("white_wool", Items.WOOL.white()), 1, 2, 18),
            reward(vanilla("string", Items.STRING), 2, 4, 12),
            reward(vanilla("shears", Items.SHEARS), 1, 1, 2)
        )),
        Map.entry(Partner.PIG, pool(
            reward(vanilla("brown_mushroom", Items.BROWN_MUSHROOM), 1, 3, 16),
            reward(vanilla("carrot", Items.CARROT), 1, 3, 12),
            reward(vanilla("porkchop", Items.PORKCHOP), 1, 2, 8)
        )),
        Map.entry(Partner.RABBIT, pool(
            reward(vanilla("rabbit_hide", Items.RABBIT_HIDE), 1, 3, 18),
            reward(vanilla("rabbit_foot", Items.RABBIT_FOOT), 1, 1, 5),
            reward(vanilla("golden_carrot", Items.GOLDEN_CARROT), 1, 1, 2)
        )),
        Map.entry(Partner.GOAT, pool(
            reward(vanilla("goat_horn", Items.GOAT_HORN), 1, 1, 3),
            reward(vanilla("wheat", Items.WHEAT), 2, 5, 14),
            reward(vanilla("emerald", Items.EMERALD), 1, 2, 4)
        )),
        Map.entry(Partner.FROG, pool(
            reward(vanilla("slime_ball", Items.SLIME_BALL), 1, 4, 18),
            reward(vanilla("ochre_froglight", Items.OCHRE_FROGLIGHT), 1, 1, 3),
            reward(vanilla("verdant_froglight", Items.VERDANT_FROGLIGHT), 1, 1, 3),
            reward(vanilla("pearlescent_froglight", Items.PEARLESCENT_FROGLIGHT), 1, 1, 3)
        )),
        Map.entry(Partner.TURTLE, pool(
            reward(vanilla("seagrass", Items.SEAGRASS), 2, 5, 18),
            reward(vanilla("turtle_scute", Items.TURTLE_SCUTE), 1, 1, 5),
            reward(vanilla("nautilus_shell", Items.NAUTILUS_SHELL), 1, 1, 2)
        )),
        Map.entry(Partner.BEE, pool(
            reward(vanilla("honeycomb", Items.HONEYCOMB), 1, 3, 18),
            reward(vanilla("honey_bottle", Items.HONEY_BOTTLE), 1, 1, 6),
            reward(vanilla("spore_blossom", Items.SPORE_BLOSSOM), 1, 1, 2)
        )),
        Map.entry(Partner.HORSE, pool(
            reward(vanilla("leather", Items.LEATHER), 1, 3, 18),
            reward(vanilla("golden_carrot", Items.GOLDEN_CARROT), 1, 2, 7),
            reward(vanilla("saddle", Items.SADDLE), 1, 1, 2)
        )),
        Map.entry(Partner.AQUATIC, pool(
            reward(vanilla("prismarine_crystals", Items.PRISMARINE_CRYSTALS), 1, 4, 48),
            reward(vanilla("seagrass", Items.SEAGRASS), 2, 5, 30),
            reward(vanilla("nautilus_shell", Items.NAUTILUS_SHELL), 1, 2, 8),
            reward(vanilla("heart_of_the_sea", Items.HEART_OF_THE_SEA), 1, 1, 1)
        )),
        Map.entry(Partner.SPIDER, pool(
            reward(vanilla("string", Items.STRING), 2, 5, 18),
            reward(vanilla("cobweb", Items.COBWEB), 1, 2, 8),
            reward(vanilla("fermented_spider_eye", Items.FERMENTED_SPIDER_EYE), 1, 1, 4)
        )),
        Map.entry(Partner.GENERIC, new WeightedPool<>(HERBAL_REWARDS.stream()
            .map(id -> WeightedPool.entry(new RewardSpec(mod(id), 1, 1), 5))
            .toList()))
    );
    private static final Map<EntityType<?>, Partner> PARTNERS = Map.ofEntries(
        Map.entry(EntityTypes.BAT, Partner.BAT),
        Map.entry(EntityTypes.WOLF, Partner.WOLF),
        Map.entry(EntityTypes.CAT, Partner.CAT),
        Map.entry(EntityTypes.COW, Partner.COW),
        Map.entry(EntityTypes.MOOSHROOM, Partner.COW),
        Map.entry(EntityTypes.CHICKEN, Partner.CHICKEN),
        Map.entry(EntityTypes.SHEEP, Partner.SHEEP),
        Map.entry(EntityTypes.PIG, Partner.PIG),
        Map.entry(EntityTypes.RABBIT, Partner.RABBIT),
        Map.entry(EntityTypes.GOAT, Partner.GOAT),
        Map.entry(EntityTypes.FROG, Partner.FROG),
        Map.entry(EntityTypes.TURTLE, Partner.TURTLE),
        Map.entry(EntityTypes.BEE, Partner.BEE),
        Map.entry(EntityTypes.HORSE, Partner.HORSE),
        Map.entry(EntityTypes.DONKEY, Partner.HORSE),
        Map.entry(EntityTypes.MULE, Partner.HORSE),
        Map.entry(EntityTypes.LLAMA, Partner.HORSE),
        Map.entry(EntityTypes.TRADER_LLAMA, Partner.HORSE),
        Map.entry(EntityTypes.CAMEL, Partner.HORSE),
        Map.entry(EntityTypes.DOLPHIN, Partner.AQUATIC),
        Map.entry(EntityTypes.AXOLOTL, Partner.AQUATIC),
        Map.entry(EntityTypes.SQUID, Partner.AQUATIC),
        Map.entry(EntityTypes.GLOW_SQUID, Partner.AQUATIC),
        Map.entry(EntityTypes.COD, Partner.AQUATIC),
        Map.entry(EntityTypes.SALMON, Partner.AQUATIC),
        Map.entry(EntityTypes.TROPICAL_FISH, Partner.AQUATIC),
        Map.entry(EntityTypes.PUFFERFISH, Partner.AQUATIC),
        Map.entry(EntityTypes.TADPOLE, Partner.AQUATIC)
    );

    private BeastSpeechTradeCatalog() {
    }

    public static Partner partner(final LivingEntity target) {
        if (target.typeHolder().is(com.kadamitas.warlockery.registry.WarlockeryTags.EntityTypes.DEMONS)) {
            return Partner.DEMON;
        }
        final Partner typed = PARTNERS.get(target.getType());
        if (typed != null) {
            return typed;
        }
        if (target instanceof Spider) {
            return Partner.SPIDER;
        }
        return target instanceof Animal ? Partner.GENERIC : Partner.INVALID;
    }

    public static boolean acceptsOffering(final LivingEntity target, final ItemStack offering) {
        if (target.typeHolder().is(com.kadamitas.warlockery.registry.WarlockeryTags.EntityTypes.DEMONS)) {
            return offering.is(CreatureBehaviorTags.Items.DEMON_BARTER);
        }
        if (target instanceof Animal animal && animal.isFood(offering)) {
            return true;
        }
        return switch (partner(target)) {
            case BAT -> offering.is(Items.SWEET_BERRIES) || offering.is(Items.GLOW_BERRIES);
            case AQUATIC -> offering.is(Items.COD)
                || offering.is(Items.SALMON)
                || offering.is(Items.TROPICAL_FISH)
                || offering.is(Items.SEAGRASS)
                || offering.is(Items.KELP);
            case SPIDER -> offering.is(Items.SPIDER_EYE) || offering.is(Items.ROTTEN_FLESH);
            default -> false;
        };
    }

    public static Optional<ItemStack> exchange(final Partner partner, final boolean accepted, final long seed) {
        return selectReward(partner, accepted, seed).map(selected -> new ItemStack(
            selected.item().item().get(),
            selected.count(seed ^ 0xbb67ae8584caa73bL)
        ));
    }

    public static Optional<RewardSpec> selectReward(
        final Partner partner,
        final boolean accepted,
        final long seed
    ) {
        return !accepted || partner == Partner.INVALID
            ? Optional.empty()
            : Optional.of(poolFor(partner).select(seed));
    }

    public static List<RewardSpec> rewards(final Partner partner) {
        return poolFor(partner).entries().stream().map(WeightedPool.Entry::value).toList();
    }

    private static WeightedPool<RewardSpec> poolFor(final Partner partner) {
        return REWARDS.getOrDefault(partner, REWARDS.get(Partner.GENERIC));
    }

    @SafeVarargs
    private static WeightedPool<RewardSpec> pool(final WeightedPool.Entry<RewardSpec>... entries) {
        return WeightedPool.of(entries);
    }

    private static WeightedPool.Entry<RewardSpec> reward(
        final ItemRef item,
        final int minimum,
        final int maximum,
        final int weight
    ) {
        return WeightedPool.entry(new RewardSpec(item, minimum, maximum), weight);
    }

    private static ItemRef vanilla(final String id, final Item item) {
        return new ItemRef("minecraft:" + id, () -> item);
    }

    private static ItemRef mod(final String id) {
        return new ItemRef("warlockery:" + id, () -> ModItems.ALL.get(id).get());
    }

    public enum Partner {
        DEMON,
        BAT,
        WOLF,
        CAT,
        COW,
        CHICKEN,
        SHEEP,
        PIG,
        RABBIT,
        GOAT,
        FROG,
        TURTLE,
        BEE,
        HORSE,
        AQUATIC,
        SPIDER,
        GENERIC,
        INVALID
    }

    public record ItemRef(String id, Supplier<? extends ItemLike> item) {
        public ItemRef {
            if (id.isBlank() || item == null) {
                throw new IllegalArgumentException("Creature rewards need an id and item supplier");
            }
        }
    }

    public record RewardSpec(ItemRef item, int minimum, int maximum) {
        public RewardSpec {
            if (minimum < 1 || maximum < minimum) {
                throw new IllegalArgumentException("Creature reward counts must form a positive range");
            }
        }

        public int count(final long seed) {
            return minimum + Math.floorMod((int) (seed ^ seed >>> 32), maximum - minimum + 1);
        }
    }
}
