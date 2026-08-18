package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.entity.HobgoblinJourneyRules;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.CampPhase;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.Mode;
import com.kadamitas.warlockery.entity.HobgoblinTravelerEntity;
import com.kadamitas.warlockery.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The three registered identifiers in this file are retained 1.4 regression contracts, but their
 * subject moved.
 *
 * <p>F11 gave the exact Hobgoblin a dedicated {@code AbstractVillager} body and replaced the whole
 * settlement, hut and tunnel subsystem with the caravan camp lifecycle (matrix rows HB-15, HB-16,
 * HB-17). {@code GoblinSettlementLifeRuntime} is typed on the retained shared {@code
 * HobgoblinEntity} and its gate is {@code GoblinSettlementLifeRules.participates}, which admits
 * only non-boss {@code GOBLIN} and {@code HOBGOBLIN} kinds. After the {@code ModEntities.HOBGOBLIN}
 * flip the only remaining {@code HobgoblinEntity} instances are the two F12 patrons, and both are
 * bosses, so that runtime no longer serves any obtainable entity. Its subject did not merely stop
 * compiling here, it stopped existing.
 *
 * <p>These fixtures are therefore reinterpreted against the replacement contracts rather than
 * retyped: the camp reservation replaces the hut, the bounded camp journal and the exclusive
 * worksite claim replace the single protected tunnel, and the child-play vocabulary replaces the
 * settlement children. Every assertion below fails on a default record or a default entity, so
 * none of them can pass vacuously.
 *
 * <p>Deliberately NOT stubbed to {@code helper.succeed()}: a green fixture that asserts nothing is
 * how a family comes to look tested when it is not.
 */
public final class GoblinSettlementLifeGameTests {
    private GoblinSettlementLifeGameTests() {
    }

    /**
     * The camp reservation replaces the hut. A camp commits its exact material counts before any
     * block is touched, is born in {@link CampPhase#RESERVE} rather than active, and is capped at
     * one per caravan by the persistent record rather than by a counter the body holds.
     */
    public static void goblinHutConsumesMaterialsAndRespectsPersistentCaps(final GameTestHelper helper) {
        final BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
        final long caravanKey = fixtureCaravanKey(anchor, 0xD00DL);
        final long campKey = HobgoblinJourneyRules.campKey(caravanKey);
        final HobgoblinJourneyData data = HobgoblinJourneyData.get(helper.getLevel());
        data.clearForGameTest(caravanKey);
        try {
            helper.assertTrue(!data.camp(campKey).present(),
                "an unreserved camp key must read as absent rather than as a null record");

            helper.assertTrue(data.openCamp(campKey, caravanKey, anchor,
                    HobgoblinJourneyRules.CAMP_DIRT_COST, HobgoblinJourneyRules.CAMP_LOG_COST),
                "the first camp reservation for a caravan must be granted");
            final HobgoblinJourneyData.CampRecord reserved = data.camp(campKey);
            helper.assertTrue(reserved.present(), "a granted reservation must produce a present record");
            helper.assertValueEqual(reserved.phase(), CampPhase.RESERVE,
                "a fresh camp record is never born active");
            helper.assertValueEqual(reserved.reservedDirt(), HobgoblinJourneyRules.CAMP_DIRT_COST,
                "dirt reserved by one camp");
            helper.assertValueEqual(reserved.reservedLogs(), HobgoblinJourneyRules.CAMP_LOG_COST,
                "logs reserved by one camp");
            helper.assertValueEqual(reserved.anchor().orElseThrow(), anchor, "camp anchor");
            helper.assertTrue(data.caravanHasCamp(caravanKey),
                "the reservation must be visible from the owning caravan record");

            helper.assertTrue(!data.openCamp(campKey + 1L, caravanKey, anchor, 0, 0),
                "persistent caravan data must reject a second camp for one caravan");
            helper.assertTrue(!data.camp(campKey + 1L).present(),
                "a rejected reservation must leave no record behind");

            // The cap is a live reservation, not a permanent lock. A leaked camp key would make
            // this fail, which is the half of the contract a count-only check cannot see.
            data.closeCamp(campKey);
            helper.assertTrue(!data.caravanHasCamp(caravanKey),
                "closing a camp must release the caravan's reservation");
            helper.assertTrue(data.openCamp(campKey + 1L, caravanKey, anchor, 0, 0),
                "a caravan whose camp closed must be able to reserve again");
            helper.succeed();
        } finally {
            data.clearForGameTest(caravanKey);
        }
    }

    /**
     * The bounded camp journal and the exclusive worksite claim replace the single bounded tunnel.
     * Only journalled owned edits are ever reverted, which is the successor to refusing to destroy
     * a block entity: a position the camp never claimed is not the camp's to touch.
     */
    public static void goblinTunnelIsSingleBoundedAndProtectsContainers(final GameTestHelper helper) {
        final BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
        final long caravanKey = fixtureCaravanKey(anchor, 0xFEEDL);
        final long campKey = HobgoblinJourneyRules.campKey(caravanKey);
        final HobgoblinJourneyData data = HobgoblinJourneyData.get(helper.getLevel());
        data.clearForGameTest(caravanKey);
        try {
            helper.assertTrue(data.openCamp(campKey, caravanKey, anchor,
                    HobgoblinJourneyRules.CAMP_DIRT_COST, HobgoblinJourneyRules.CAMP_LOG_COST),
                "the camp under test must reserve before it may journal an edit");

            for (int index = 0; index < HobgoblinJourneyRules.CAMP_MAX_EDITS; index++) {
                helper.assertTrue(
                    data.recordCampEdit(campKey, anchor.offset(index, 0, 0), "minecraft:dirt"),
                    "owned camp edit " + index + " must be journalled");
            }
            helper.assertValueEqual(data.campJournal(campKey).size(),
                HobgoblinJourneyRules.CAMP_MAX_EDITS, "journal size at the declared edit budget");
            helper.assertTrue(!data.recordCampEdit(campKey,
                    anchor.offset(HobgoblinJourneyRules.CAMP_MAX_EDITS, 0, 0), "minecraft:dirt"),
                "a camp journal must never exceed its edit budget");

            // A released edit frees exactly one slot, so the budget is a live cap rather than a
            // permanent ceiling that would strand a long-lived camp.
            data.removeCampEdit(campKey, anchor);
            helper.assertValueEqual(data.campJournal(campKey).size(),
                HobgoblinJourneyRules.CAMP_MAX_EDITS - 1,
                "journal size after releasing one owned edit");
            helper.assertTrue(data.recordCampEdit(campKey,
                    anchor.offset(HobgoblinJourneyRules.CAMP_MAX_EDITS, 0, 0), "minecraft:dirt"),
                "the edit budget must accept a new edit once one is released");

            // Exclusivity is what "a single tunnel per settlement" became: one claimant per site,
            // one live claim per claimant, and a released claim frees the site again.
            final UUID firstClaimant = UUID.randomUUID();
            final UUID secondClaimant = UUID.randomUUID();
            final BlockPos site = anchor.above();
            final Optional<UUID> granted = data.claim("camp", firstClaimant, Optional.of(site));
            helper.assertTrue(granted.isPresent(),
                "the first claimant must be granted the unclaimed camp worksite");
            helper.assertTrue(data.siteClaimed(site),
                "a granted claim must mark its site as taken");
            helper.assertTrue(data.claim("camp", secondClaimant, Optional.of(site)).isEmpty(),
                "an already claimed worksite must never be granted a second time");
            helper.assertTrue(data.holdsClaim(granted.orElseThrow()),
                "the rejected second claim must not have displaced the first");

            data.releaseClaim(granted.orElseThrow());
            helper.assertTrue(!data.siteClaimed(site),
                "releasing a claim must free its site for the next claimant");
            helper.succeed();
        } finally {
            data.clearForGameTest(caravanKey);
        }
    }

    /**
     * The child-play vocabulary replaces the settlement children. A child surveys for a flower only
     * with an empty hand, qualifies for the bounded circle dance only with enough caravan siblings
     * loaded, and qualifies to gift only while actually holding a flower and off its cooldown.
     *
     * <p>The gather is observed by its outcome after 200 ticks rather than by the transient scan
     * plan. This fixture drives a live 40-tick work-survey cadence that the synchronous 1.4 fixture
     * never had, and that cadence is seeded from the entity UUID, so the first survey lands
     * anywhere in ticks 1 to 40 and the approach costs a further navigation lease. The 1.4 budget
     * of 100 ticks left no margin for either and is raised to 400 alongside. The assertions are
     * monotonic under a longer delay: with no player in the arena the gathered flower is never
     * gifted away, so a later read cannot turn a real pass into a vacuous one.
     */
    public static void goblinChildrenGatherDanceAndGiftFlowers(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(0, 0, 0), new BlockPos(2, 0, 2))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
        final BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos relativeFlower = new BlockPos(1, 1, 0);
        final BlockPos absoluteFlower = helper.absolutePos(relativeFlower);
        final long caravanKey = fixtureCaravanKey(anchor, 0xC0DEL);
        final HobgoblinJourneyData data = HobgoblinJourneyData.get(helper.getLevel());
        data.clearForGameTest(caravanKey);

        final List<HobgoblinTravelerEntity> children = new ArrayList<>();
        try {
            final HobgoblinTravelerEntity gatherer =
                child(helper, children, new BlockPos(0, 1, 0), caravanKey);
            // The two bystanders hold a non-flower item on purpose. A child surveys for a flower
            // only with an empty hand, so leaving their hands empty would put three children in a
            // race for the arena's single flower and let any of them win it; the fixture would
            // then be asserting which child happened to arrive first. They still count toward the
            // dance, which counts caravan children and not what they are carrying.
            final HobgoblinTravelerEntity firstBystander =
                child(helper, children, new BlockPos(2, 1, 0), caravanKey);
            final HobgoblinTravelerEntity secondBystander =
                child(helper, children, new BlockPos(0, 1, 2), caravanKey);
            firstBystander.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
            secondBystander.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
            final HobgoblinTravelerEntity giver =
                child(helper, children, new BlockPos(2, 1, 2), caravanKey);
            giver.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.POPPY));

            helper.setBlock(new BlockPos(1, 0, 0), Blocks.DIRT);
            helper.setBlock(relativeFlower, Blocks.POPPY);
            helper.assertTrue(helper.getBlockState(relativeFlower).is(Blocks.POPPY),
                "the flower fixture must survive before the child surveys it");
            helper.assertTrue(gatherer.isBaby(), "the flower gatherer must retain its baby state");
            helper.assertTrue(gatherer.getMainHandItem().isEmpty(),
                "the flower gatherer must begin with an empty hand");
            helper.assertTrue(giver.getMainHandItem().is(Items.POPPY),
                "the gifting child must begin holding a flower");
            helper.assertValueEqual(data.population(caravanKey), children.size(),
                "every child in the fixture must share one caravan");

            children.forEach(child -> child.journeyTransient().resetForLoad());

            helper.runAfterDelay(200L, () -> {
                try {
                    // The gather is asserted by its outcome, never by the transient plan.
                    // surveyWork and executeChildPlay run inside the same decision tick, so
                    // plan.flower is populated and consumed without ever being observable
                    // between ticks; latching it is a race, not a contract. The outcome is
                    // unambiguous instead: this child began with a provably empty hand and the
                    // arena contained exactly one flower block.
                    helper.assertTrue(gatherer.getMainHandItem().is(Items.POPPY),
                        "a child with an empty hand must gather the tagged flower into its visible hand");
                    helper.assertTrue(helper.getLevel().getBlockState(absoluteFlower).isAir(),
                        "the gathered flower must be taken from its block rather than conjured");

                    final HobgoblinJourneyRules.WorkAvailability play =
                        gatherer.journeyTransient().work();
                    helper.assertTrue(play.childDance(),
                        "children sharing one caravan must qualify for the bounded circle dance");
                    helper.assertTrue(play.childGift(),
                        "a child that has gathered a flower must then qualify to gift it");
                    helper.assertValueEqual(gatherer.journeyState().mode(), Mode.CHILD_PLAY,
                        "a child with available play must commit to the child play mode");

                    helper.assertTrue(!firstBystander.journeyTransient().work().childFlower()
                            && !secondBystander.journeyTransient().work().childFlower(),
                        "a child holding any non-flower item must not survey for a flower at all");

                    final HobgoblinJourneyRules.WorkAvailability gift =
                        giver.journeyTransient().work();
                    helper.assertTrue(gift.childGift(),
                        "a child holding a flower and off cooldown must qualify to gift it");
                    helper.assertTrue(!gift.childFlower(),
                        "a child already holding a flower must not survey for a second one");

                    helper.assertValueEqual(gatherer.journeyCounters().campEditsCommitted(), 0L,
                        "child play must never place or journal a camp block");
                    helper.assertTrue(gatherer.getTarget() == null,
                        "a child must never acquire a combat target");
                    helper.succeed();
                } finally {
                    cleanup(helper, children, data, caravanKey, relativeFlower);
                }
            });
        } catch (RuntimeException | Error failure) {
            cleanup(helper, children, data, caravanKey, relativeFlower);
            throw failure;
        }
    }

    // ---------------------------------------------------------------- fixture helpers

    /**
     * Every GameTest arena in one batch sits inside the same 128-block region, so the production
     * regional caravan key is identical for all of them and one fixture's reset would wipe
     * another's records mid-run. Each fixture therefore salts its own key.
     */
    private static long fixtureCaravanKey(final BlockPos anchor, final long salt) {
        return HobgoblinJourneyRules.caravanKey(anchor.getX(), anchor.getZ()) * 31L + salt;
    }

    private static HobgoblinTravelerEntity child(
        final GameTestHelper helper,
        final List<HobgoblinTravelerEntity> tracked,
        final BlockPos position,
        final long caravanKey
    ) {
        final HobgoblinTravelerEntity traveler = helper.spawn(
            ModEntities.HOBGOBLIN.get(), position, EntitySpawnReason.BREEDING
        );
        traveler.setAge(-24_000);
        traveler.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        traveler.setDeltaMovement(Vec3.ZERO);
        traveler.setJourneyState(traveler.journeyState()
            .withCaravan(traveler.journeyState().caravan().withKey(caravanKey)));
        HobgoblinJourneyData.get(helper.getLevel()).joinCaravan(caravanKey, traveler.getUUID());
        tracked.add(traveler);
        return traveler;
    }

    private static void cleanup(
        final GameTestHelper helper,
        final List<HobgoblinTravelerEntity> children,
        final HobgoblinJourneyData data,
        final long caravanKey,
        final BlockPos relativeFlower
    ) {
        children.forEach(child -> {
            if (!child.isRemoved()) {
                child.discard();
            }
        });
        helper.setBlock(relativeFlower, Blocks.AIR);
        data.clearForGameTest(caravanKey);
    }
}
