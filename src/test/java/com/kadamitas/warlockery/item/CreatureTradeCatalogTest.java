package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.GoblinTradeCatalog;
import com.kadamitas.warlockery.entity.GoblinProfession;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CreatureTradeCatalogTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyCreaturePoolIsVariedAndSpeciesFlavored() {
        for (final BeastSpeechTradeCatalog.Partner partner : BeastSpeechTradeCatalog.Partner.values()) {
            if (partner == BeastSpeechTradeCatalog.Partner.INVALID) {
                continue;
            }
            assertTrue(ids(partner).size() >= 3, () -> partner + " should have at least three distinct rewards");
        }

        assertAll(
            () -> assertTrue(ids(BeastSpeechTradeCatalog.Partner.BAT).contains("warlockery:ingredient_bat_wool")),
            () -> assertTrue(ids(BeastSpeechTradeCatalog.Partner.WOLF).contains("warlockery:ingredient_dog_tongue")),
            () -> assertTrue(ids(BeastSpeechTradeCatalog.Partner.AQUATIC).contains("minecraft:heart_of_the_sea")),
            () -> assertTrue(ids(BeastSpeechTradeCatalog.Partner.TURTLE).contains("minecraft:turtle_scute")),
            () -> assertTrue(ids(BeastSpeechTradeCatalog.Partner.DEMON).containsAll(Set.of(
                "warlockery:ingredient_infernal_blood",
                "minecraft:echo_shard",
                "minecraft:ominous_bottle",
                "minecraft:ominous_trial_key"
            )))
        );
    }

    @Test
    void rareGoblinTreasurePricesStayScarceAndBounded() {
        final GoblinTradeCatalog.OfferSpec elytra = GoblinTradeCatalog.elytraOffer().orElseThrow();
        final List<String> sampled = LongStream.range(0, 100_000)
            .mapToObj(GoblinTradeCatalog::treasure)
            .map(offer -> offer.reward().id())
            .toList();
        final long elytras = sampled.stream().filter("minecraft:elytra"::equals).count();
        final long hearts = sampled.stream().filter("minecraft:heart_of_the_sea"::equals).count();

        assertAll(
            () -> assertEquals("warlockery:ingredient_delvealloyingot", elytra.cost().id()),
            () -> assertEquals(5, elytra.costCount()),
            () -> assertEquals("minecraft:elytra", elytra.reward().id()),
            () -> assertEquals(1, elytra.rewardCount()),
            () -> assertEquals(1, elytra.maxUses()),
            () -> assertTrue(GoblinTradeCatalog.treasureOffers().stream().allMatch(offer -> offer.maxUses() == 1)),
            () -> assertTrue(GoblinTradeCatalog.treasureOffers().stream().anyMatch(offer ->
                offer.reward().id().equals("minecraft:heart_of_the_sea") && offer.costCount() >= 2)),
            () -> assertTrue(GoblinTradeCatalog.treasureOffers().stream().anyMatch(offer ->
                offer.reward().id().equals("minecraft:ominous_trial_key") && offer.costCount() >= 4)),
            () -> assertTrue(elytras > 0 && elytras < hearts / 5,
                "the showcase Elytra must remain much rarer than the Heart of the Sea")
        );
    }

    @Test
    void rotatingGoblinOffersAreDeterministicAndSpeciesSpecific() {
        final long seed = 918_273L;

        assertEquals(
            GoblinTradeCatalog.specialty(CreatureKind.GOBLIN, seed),
            GoblinTradeCatalog.specialty(CreatureKind.GOBLIN, seed)
        );
        assertFalse(GoblinTradeCatalog.specialty(CreatureKind.GOBLIN, seed).equals(
            GoblinTradeCatalog.specialty(CreatureKind.HOBGOBLIN, seed)
        ));
        assertTrue(LongStream.range(0, 20_000)
            .mapToObj(GoblinTradeCatalog::treasure)
            .map(offer -> offer.reward().id())
            .distinct()
            .count() >= 8);
    }

    @Test
    void rareTreasureIsAddedOnlyOnceAtTheFinalTradeLevel() {
        final long seed = 73_991L;
        final GoblinProfession profession = GoblinProfession.MINER;

        assertTrue(GoblinTradeCatalog.offersForLevel(CreatureKind.HOBGOBLIN, profession, seed, 1)
            .stream().noneMatch(GoblinTradeCatalog.treasureOffers()::contains));
        for (int level = 2; level < 5; level++) {
            assertTrue(GoblinTradeCatalog.offersForLevel(CreatureKind.HOBGOBLIN, profession, seed, level)
                .stream().noneMatch(GoblinTradeCatalog.treasureOffers()::contains));
        }
        assertEquals(1, GoblinTradeCatalog.offersForLevel(CreatureKind.HOBGOBLIN, profession, seed, 5).size());
        assertTrue(GoblinTradeCatalog.treasureOffers().contains(
            GoblinTradeCatalog.offersForLevel(CreatureKind.HOBGOBLIN, profession, seed, 5).getFirst()
        ));
    }

    @Test
    void invalidOfferingsFailWhileValidExchangesProduceRewards() {
        final UtilityDecision rejected = BeastSpeechRules.diagnose(
            false,
            BeastSpeechRules.Audience.DEMON,
            true
        );
        final UtilityDecision accepted = BeastSpeechRules.diagnose(
            false,
            BeastSpeechRules.Audience.ANIMAL,
            true
        );
        final UtilityDecision wrongFood = BeastSpeechRules.diagnose(
            false,
            BeastSpeechRules.Audience.ANIMAL,
            false
        );

        assertAll(
            () -> assertFalse(rejected.success()),
            () -> assertFalse(wrongFood.success()),
            () -> assertFalse(BeastSpeechTradeCatalog.exchange(
                BeastSpeechTradeCatalog.Partner.CHICKEN,
                false,
                11L
            ).isPresent()),
            () -> assertTrue(accepted.success()),
            () -> assertTrue(BeastSpeechTradeCatalog.selectReward(
                BeastSpeechTradeCatalog.Partner.CHICKEN,
                true,
                11L
            ).isPresent())
        );
    }

    @Test
    void merchantOfferSpecificationQuotesItsExactCostAndReward() {
        final GoblinTradeCatalog.OfferSpec coalOffer = GoblinTradeCatalog.coreOffers(
            GoblinProfession.MINER
        ).stream().filter(offer -> offer.cost().id().equals("minecraft:coal")).findFirst().orElseThrow();

        assertEquals("minecraft:coal", coalOffer.cost().id());
        assertEquals(12, coalOffer.costCount());
        assertEquals("minecraft:emerald", coalOffer.reward().id());
        assertEquals(1, coalOffer.rewardCount());
        assertTrue(coalOffer.maxUses() > 1);
    }

    private static Set<String> ids(final BeastSpeechTradeCatalog.Partner partner) {
        return BeastSpeechTradeCatalog.rewards(partner).stream()
            .map(reward -> reward.item().id())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}

