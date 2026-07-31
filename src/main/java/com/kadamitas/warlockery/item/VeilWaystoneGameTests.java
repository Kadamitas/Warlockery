package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.crafting.AltarPowerNetwork;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public final class VeilWaystoneGameTests {
    private static final BlockPos CENTER = new BlockPos(1, 2, 1);

    private VeilWaystoneGameTests() {
    }

    public static void failedVeilBindingReportsOnlyOncePerDrop(final GameTestHelper helper) {
        placeSupportedRing(helper, CENTER, VeilWaystoneRules.bindingRing());
        helper.assertTrue(
            VeilWaystoneRuntime.ringCenter(
                helper.getLevel(),
                helper.absolutePos(CENTER),
                WaystoneItem.Kind.BASE
            ).isPresent(),
            "the complete Veil Chalk binding ring must resolve before an offering is dropped"
        );
        final ItemEntity waystone = drop(helper, CENTER, new ItemStack(
            ModItems.ALL.get("ingredient_waystone").get()
        ));
        helper.assertTrue(
            waystone.getItem().getItem() instanceof WaystoneItem,
            "the plain waystone must retain its dropped-item ritual behavior"
        );
        helper.startSequence()
            .thenWaitUntil(() -> {
                helper.assertTrue(VeilWaystoneRuntime.attempted(waystone),
                    "a complete ring with no powered altar must record its single failed attempt");
                helper.assertTrue(waystone.getItem().is(ModItems.ALL.get("ingredient_waystone").get()),
                    "a failed binding must leave the plain waystone intact");
            })
            .thenExecuteAfter(VeilWaystoneRules.ATTUNEMENT_TICKS, () -> {
                helper.assertTrue(VeilWaystoneRuntime.attempted(waystone),
                    "the same dropped offering must not start a second attempt");
                helper.assertTrue(waystone.getItem().is(ModItems.ALL.get("ingredient_waystone").get()),
                    "a suppressed repeat must not mutate the offering");
            })
            .thenSucceed();
    }

    public static void veilRingBindsWaystonesToItsCenter(final GameTestHelper helper) {
        placeAltar(helper);
        helper.runAfterDelay(45, () -> {
            powerAltars(helper);
            placeSupportedRing(helper, CENTER, VeilWaystoneRules.bindingRing());
            helper.assertTrue(
                VeilWaystoneRuntime.ringCenter(
                    helper.getLevel(),
                    helper.absolutePos(CENTER),
                    WaystoneItem.Kind.BASE
                ).isPresent(),
                "the complete Veil Chalk binding ring must resolve before an offering is dropped"
            );
            final ItemEntity waystone = drop(helper, CENTER, new ItemStack(
                ModItems.ALL.get("ingredient_waystone").get()
            ));
            helper.assertTrue(
                waystone.getItem().getItem() instanceof WaystoneItem,
                "the plain waystone must retain its dropped-item ritual behavior"
            );
            helper.runAfterDelay(VeilWaystoneRules.ATTUNEMENT_TICKS + 10, () -> {
                helper.assertTrue(waystone.getItem().is(ModItems.ALL.get("ingredient_waystone_bound").get()),
                    "the Veil Chalk ring must turn a plain waystone into a position-bound waystone; item="
                        + waystone.getItem().getItem() + ", attempted=" + VeilWaystoneRuntime.attempted(waystone)
                        + ", power=" + AltarPowerNetwork.available(helper.getLevel(), helper.absolutePos(CENTER)));
                final WaystoneState.Location location = WaystoneState.read(waystone.getItem()).orElseThrow();
                helper.assertValueEqual(location.position(), helper.absolutePos(CENTER),
                    "the bound waystone destination");
                helper.succeed();
            });
        });
    }

    public static void veilRingTransposesLivingAndDroppedTravellers(final GameTestHelper helper) {
        final BlockPos center = new BlockPos(1, 2, 1);
        final BlockPos destination = new BlockPos(12, 2, 4);
        helper.setBlock(destination.below(), Blocks.STONE);
        placeAltar(helper);
        helper.runAfterDelay(45, () -> {
            powerAltars(helper);
            placeSupportedRing(helper, center, VeilWaystoneRules.transpositionRing());
            final ItemStack bound = new ItemStack(ModItems.ALL.get("ingredient_waystone_bound").get());
            WaystoneState.write(bound, helper.getLevel().dimension().identifier(), helper.absolutePos(destination));
            final ItemEntity waystone = drop(helper, center, bound);
            final ItemEntity offering = drop(helper, center.offset(1, 0, 0), new ItemStack(
                net.minecraft.world.item.Items.AMETHYST_SHARD
            ));
            final Villager traveller = helper.spawn(EntityTypes.VILLAGER, center.offset(0, 1, 1));
            traveller.setNoAi(true);
            helper.runAfterDelay(VeilWaystoneRules.ATTUNEMENT_TICKS + 10, () -> {
                final BlockPos arrival = helper.absolutePos(destination);
                helper.assertTrue(waystone.distanceToSqr(
                    arrival.getX() + 0.5D,
                    arrival.getY(),
                    arrival.getZ() + 0.5D
                ) < 4.0D, "the bound waystone must travel with the group; attempted="
                    + VeilWaystoneRuntime.attempted(waystone) + ", power="
                    + AltarPowerNetwork.available(helper.getLevel(), helper.absolutePos(center)));
                helper.assertTrue(offering.distanceToSqr(
                    arrival.getX() + 0.5D,
                    arrival.getY(),
                    arrival.getZ() + 0.5D
                ) < 4.0D, "loose offerings inside the ring must travel");
                helper.assertTrue(traveller.distanceToSqr(
                    arrival.getX() + 0.5D,
                    arrival.getY(),
                    arrival.getZ() + 0.5D
                ) < 4.0D, "living travellers inside the ring must travel");
                helper.succeed();
            });
        });
    }

    private static void placeSupportedRing(
        final GameTestHelper helper,
        final BlockPos center,
        final List<BlockPos> offsets
    ) {
        final int radius = offsets.stream()
            .mapToInt(offset -> Math.max(Math.abs(offset.getX()), Math.abs(offset.getZ())))
            .max()
            .orElseThrow();
        BlockPos.betweenClosedStream(
            center.offset(-radius, -1, -radius),
            center.offset(radius, -1, radius)
        ).forEach(position -> helper.setBlock(position, Blocks.STONE));
        offsets.forEach(offset -> {
            helper.setBlock(center.offset(offset), ModBlocks.ALL.get("circleglyph_veil").get());
        });
    }

    private static ItemEntity drop(
        final GameTestHelper helper,
        final BlockPos relativePosition,
        final ItemStack stack
    ) {
        final BlockPos position = helper.absolutePos(relativePosition);
        final ItemEntity entity = new ItemEntity(
            helper.getLevel(),
            position.getX() + 0.5D,
            position.getY() + 0.1D,
            position.getZ() + 0.5D,
            stack
        );
        entity.setDeltaMovement(0.0D, 0.0D, 0.0D);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void placeAltar(final GameTestHelper helper) {
        altarPositions().forEach(position -> helper.setBlock(position, ModBlocks.ALTAR.get()));
    }

    private static void powerAltars(final GameTestHelper helper) {
        altarPositions().forEach(position -> {
            final AltarBlockEntity altar = helper.getBlockEntity(position, AltarBlockEntity.class);
            helper.assertTrue(altar.isMultiblockValid(), "the nearby 3 by 2 altar must be valid");
            altar.receivePower(altar.getCapacity());
        });
    }

    private static List<BlockPos> altarPositions() {
        return List.of(
            new BlockPos(8, 1, 2),
            new BlockPos(9, 1, 2),
            new BlockPos(10, 1, 2),
            new BlockPos(8, 1, 3),
            new BlockPos(9, 1, 3),
            new BlockPos(10, 1, 3)
        );
    }
}
