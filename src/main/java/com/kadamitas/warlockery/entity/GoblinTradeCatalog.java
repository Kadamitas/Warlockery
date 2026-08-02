package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.util.WeightedPool;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;

public final class GoblinTradeCatalog {
    private static final long TREASURE_SALT = 0x6a09e667f3bcc909L;
    private static final long LEVEL_SALT = 0xbb67ae8584caa73bL;
    private static final Map<HobgoblinEntity.GoblinProfession, List<OfferSpec>> CORE = Map.of(
        HobgoblinEntity.GoblinProfession.MINER, List.of(
            offer(vanilla("coal", Items.COAL), 12, vanilla("emerald", Items.EMERALD), 1, 16, 2, 0.05F),
            offer(vanilla("emerald", Items.EMERALD), 8, mod("raw_delvealloy"), 1, 8, 8, 0.12F),
            offer(mod("ingredient_delvealloydust"), 8, vanilla("emerald", Items.EMERALD), 1, 12, 10, 0.08F)
        ),
        HobgoblinEntity.GoblinProfession.SMITH, List.of(
            offer(mod("raw_delvealloy"), 4, vanilla("emerald", Items.EMERALD), 1, 12, 5, 0.08F),
            offer(vanilla("emerald", Items.EMERALD), 32, mod("delvealloypickaxe"), 1, 2, 20, 0.2F)
        ),
        HobgoblinEntity.GoblinProfession.SHAMAN, List.of(
            offer(vanilla("redstone", Items.REDSTONE), 8, mod("ingredient_whiff_of_magic"), 1, 12, 8, 0.08F),
            offer(vanilla("emerald", Items.EMERALD), 6, mod("ingredient_attuned_stone"), 1, 8, 12, 0.12F)
        ),
        HobgoblinEntity.GoblinProfession.PROSPECTOR, List.of(
            offer(mod("raw_silver"), 5, vanilla("emerald", Items.EMERALD), 1, 12, 5, 0.08F),
            offer(mod("ingredient_delvealloydust"), 18, mod("ingredient_delvealloynugget"), 1, 12, 12, 0.12F),
            offer(vanilla("emerald", Items.EMERALD), 12, mod("ingredient_delvealloynugget"), 1, 12, 8, 0.12F)
        )
    );
    private static final Map<CreatureKind, WeightedPool<OfferSpec>> SPECIALTIES = Map.of(
        CreatureKind.HOBGOBLIN, WeightedPool.of(
            weighted(vanillaOffer(Items.EMERALD, 10, Items.AMETHYST_SHARD, 4), 14),
            weighted(vanillaOffer(Items.EMERALD, 14, Items.BREEZE_ROD, 2), 10),
            weighted(vanillaOffer(Items.EMERALD, 16, Items.ECHO_SHARD, 2), 8),
            weighted(vanillaOffer(Items.EMERALD, 18, Items.NAUTILUS_SHELL, 2), 6),
            weighted(vanillaOffer(Items.EMERALD, 24, Items.HEART_OF_THE_SEA, 1), 3)
        ),
        CreatureKind.GOBLIN, WeightedPool.of(
            weighted(vanillaOffer(Items.EMERALD, 8, Items.WIND_CHARGE, 4), 14),
            weighted(vanillaOffer(Items.EMERALD, 12, Items.TNT, 3), 12),
            weighted(vanillaOffer(Items.EMERALD, 15, Items.OMINOUS_BOTTLE, 1), 7),
            weighted(vanillaOffer(Items.EMERALD, 18, Items.TRIAL_KEY, 1), 5),
            weighted(vanillaOffer(Items.EMERALD, 28, Items.OMINOUS_TRIAL_KEY, 1), 2)
        )
    );
    private static final WeightedPool<OfferSpec> TREASURES = WeightedPool.of(
        weighted(gobliniteOffer(1, Items.NAUTILUS_SHELL, 4), 18),
        weighted(gobliniteOffer(2, Items.HEART_OF_THE_SEA, 1), 12),
        weighted(gobliniteOffer(2, Items.ECHO_SHARD, 4), 12),
        weighted(gobliniteOffer(2, Items.OMINOUS_BOTTLE, 1), 9),
        weighted(gobliniteOffer(3, Items.TRIAL_KEY, 1), 7),
        weighted(gobliniteOffer(4, Items.OMINOUS_TRIAL_KEY, 1), 4),
        weighted(gobliniteOffer(5, Items.HEAVY_CORE, 1), 2),
        weighted(gobliniteOffer(5, Items.ELYTRA, 1), 1)
    );

    private GoblinTradeCatalog() {
    }

    public static List<MerchantOffer> createOffers(
        final CreatureKind kind,
        final HobgoblinEntity.GoblinProfession profession,
        final long seed,
        final int level
    ) {
        return offersForLevel(kind, profession, seed, level).stream()
            .map(OfferSpec::toMerchantOffer)
            .toList();
    }

    public static List<OfferSpec> offersForLevel(
        final CreatureKind kind,
        final HobgoblinEntity.GoblinProfession profession,
        final long seed,
        final int level
    ) {
        return switch (Math.clamp(level, 1, 5)) {
            case 1 -> CORE.getOrDefault(profession, List.of());
            case 2, 3, 4 -> List.of(specialty(kind, seed ^ LEVEL_SALT * level));
            case 5 -> List.of(treasure(seed));
            default -> throw new IllegalStateException("Clamped goblin trade level is outside its range");
        };
    }

    public static List<OfferSpec> coreOffers(final HobgoblinEntity.GoblinProfession profession) {
        return CORE.getOrDefault(profession, List.of());
    }

    public static OfferSpec specialty(final CreatureKind kind, final long seed) {
        return SPECIALTIES.getOrDefault(kind, SPECIALTIES.get(CreatureKind.HOBGOBLIN)).select(seed);
    }

    public static OfferSpec treasure(final long seed) {
        return TREASURES.select(seed ^ TREASURE_SALT);
    }

    public static List<OfferSpec> treasureOffers() {
        return TREASURES.entries().stream().map(WeightedPool.Entry::value).toList();
    }

    public static Optional<OfferSpec> elytraOffer() {
        return treasureOffers().stream().filter(offer -> offer.reward().id().equals("minecraft:elytra")).findFirst();
    }

    private static WeightedPool.Entry<OfferSpec> weighted(final OfferSpec offer, final int weight) {
        return WeightedPool.entry(offer, weight);
    }

    private static OfferSpec vanillaOffer(
        final Item cost,
        final int costCount,
        final Item reward,
        final int rewardCount
    ) {
        return offer(vanillaId(cost), costCount, vanillaId(reward), rewardCount, 4, 18, 0.2F);
    }

    private static OfferSpec gobliniteOffer(final int ingots, final Item reward, final int rewardCount) {
        return offer(mod("ingredient_delvealloyingot"), ingots, vanillaId(reward), rewardCount, 1, 30, 0.3F);
    }

    private static OfferSpec offer(
        final ItemRef cost,
        final int costCount,
        final ItemRef reward,
        final int rewardCount,
        final int maxUses,
        final int xp,
        final float priceMultiplier
    ) {
        return new OfferSpec(cost, costCount, reward, rewardCount, maxUses, xp, priceMultiplier);
    }

    private static ItemRef vanillaId(final Item item) {
        return vanilla(BuiltInRegistries.ITEM.getKey(item).getPath(), item);
    }

    private static ItemRef vanilla(final String id, final Item item) {
        return new ItemRef("minecraft:" + id, () -> item);
    }

    private static ItemRef mod(final String id) {
        return new ItemRef("warlockery:" + id, () -> ModItems.ALL.get(id).get());
    }

    public record ItemRef(String id, Supplier<? extends ItemLike> item) {
        public ItemRef {
            if (id.isBlank() || item == null) {
                throw new IllegalArgumentException("Trade item references need an id and item supplier");
            }
        }
    }

    public record OfferSpec(
        ItemRef cost,
        int costCount,
        ItemRef reward,
        int rewardCount,
        int maxUses,
        int xp,
        float priceMultiplier
    ) {
        public OfferSpec {
            if (costCount < 1 || rewardCount < 1 || maxUses < 1 || xp < 0 || priceMultiplier < 0.0F) {
                throw new IllegalArgumentException("Trade quantities, uses, experience, and pricing must be valid");
            }
        }

        public MerchantOffer toMerchantOffer() {
            return new MerchantOffer(
                new ItemCost(cost.item().get(), costCount),
                new ItemStack(reward.item().get(), rewardCount),
                maxUses,
                xp,
                priceMultiplier
            );
        }
    }
}

