package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.ritual.marriage.MarriageData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.AABB;

public final class SpouseAmbientGameTests {
    private SpouseAmbientGameTests() {
    }

    public static void spouseCooksOneMeatAndDeliversOneMeal(final GameTestHelper helper) {
        buildFloor(helper);
        final ServerPlayer player = connectedPlayer(helper, new BlockPos(0, 1, 0));
        final NamiEntity nami = marriedNami(helper, player, new BlockPos(1, 1, 0));
        final BlockPos furnaceRelative = new BlockPos(2, 1, 2);
        helper.setBlock(furnaceRelative, Blocks.FURNACE);
        final BlockPos furnaceAbsolute = helper.absolutePos(furnaceRelative);
        final AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(furnaceAbsolute);
        furnace.setItem(1, new ItemStack(Items.COAL));
        final BlockPos sourcePosition = helper.absolutePos(new BlockPos(2, 1, 0));
        final ItemEntity raw = new ItemEntity(
            helper.getLevel(),
            sourcePosition.getX() + 0.5,
            sourcePosition.getY(),
            sourcePosition.getZ() + 0.5,
            new ItemStack(Items.BEEF)
        );
        raw.setThrower(player);
        helper.getLevel().addFreshEntity(raw);
        final Optional<SpouseAmbientRuntime.CookingTask> task = SpouseAmbientRuntime.findCookingTask(
            nami,
            helper.getLevel(),
            player
        );
        helper.assertTrue(task.isPresent(), "an empty fueled furnace and one tagged raw meat must form a cooking task");
        helper.assertTrue(SpouseAmbientRuntime.beginCooking(nami, helper.getLevel(), task.orElseThrow()),
            "the spouse must accept the bounded cooking task");
        helper.onEachTick(() -> {
            nami.setTarget(null);
            nami.setLastHurtByMob(null);
            player.setLastHurtByMob(null);
            SpouseAmbientRuntime.tick(nami, helper.getLevel(), player);
        });
        helper.runAfterDelay(360, () -> {
            final int cookedInInventory = player.getInventory().countItem(Items.COOKED_BEEF);
            final int rawInWorld = countWorldItem(helper, Items.BEEF);
            final int cookedInWorld = countWorldItem(helper, Items.COOKED_BEEF);
            final int rawInFurnace = furnace.getItem(0).is(Items.BEEF) ? furnace.getItem(0).getCount() : 0;
            final int cookedInFurnace = furnace.getItem(2).is(Items.COOKED_BEEF) ? furnace.getItem(2).getCount() : 0;
            final int carriedCooked = nami.getMainHandItem().is(Items.COOKED_BEEF) ? nami.getMainHandItem().getCount() : 0;
            helper.assertValueEqual(cookedInInventory, 1,
                "one consumed raw meat must become exactly one meal in the spouse's inventory; action="
                    + nami.getPersistentData().getStringOr(SpouseAmbientRuntime.ACTION, "")
                    + ", input=" + furnace.getItem(0)
                    + ", output=" + furnace.getItem(2)
                    + ", hand=" + nami.getMainHandItem());
            helper.assertValueEqual(
                rawInWorld + cookedInWorld + rawInFurnace + cookedInFurnace + carriedCooked + cookedInInventory,
                1,
                "the complete cooking workflow must conserve exactly one item without duplication"
            );
            helper.assertTrue(nami.getPersistentData().getLongOr(SpouseAmbientRuntime.COOK_READY, 0L)
                > helper.getLevel().getGameTime(), "successful delivery must persist its long cooking cooldown");
            helper.succeed();
        });
    }

    public static void spouseRejectsOccupiedFurnaceWithoutTakingMeat(final GameTestHelper helper) {
        buildFloor(helper);
        final ServerPlayer player = connectedPlayer(helper, new BlockPos(0, 1, 0));
        final NamiEntity nami = marriedNami(helper, player, new BlockPos(1, 1, 0));
        final BlockPos furnaceRelative = new BlockPos(2, 1, 2);
        helper.setBlock(furnaceRelative, Blocks.FURNACE);
        final AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(
            helper.absolutePos(furnaceRelative)
        );
        furnace.setItem(0, new ItemStack(Items.POTATO));
        furnace.setItem(1, new ItemStack(Items.COAL));
        final BlockPos sourcePosition = helper.absolutePos(new BlockPos(2, 1, 0));
        final ItemEntity raw = new ItemEntity(
            helper.getLevel(),
            sourcePosition.getX() + 0.5,
            sourcePosition.getY(),
            sourcePosition.getZ() + 0.5,
            new ItemStack(Items.BEEF)
        );
        raw.setThrower(player);
        helper.getLevel().addFreshEntity(raw);

        helper.assertFalse(SpouseCookingMachine.at(helper.getLevel(), helper.absolutePos(furnaceRelative))
                .orElseThrow()
                .availableFor(helper.getLevel(), raw.getItem()),
            "an occupied machine must not be claimed for an ambient cooking routine");
        helper.assertValueEqual(raw.getItem().getCount(), 1,
            "failure to reserve a machine must leave the dropped raw meat untouched");
        helper.assertTrue(furnace.getItem(0).is(Items.POTATO) && furnace.getItem(0).getCount() == 1,
            "failure to reserve a machine must leave its existing input untouched");
        helper.succeed();
    }

    public static void spouseKissPersistsCooldown(final GameTestHelper helper) {
        buildFloor(helper);
        final ServerPlayer player = connectedPlayer(helper, new BlockPos(0, 1, 0));
        final NamiEntity nami = marriedNami(helper, player, new BlockPos(0, 1, 1));
        helper.assertTrue(SpouseAmbientRuntime.beginKiss(nami, helper.getLevel()),
            "a married spouse must be able to begin the affection approach");
        helper.runAfterDelay(2, () -> {
            SpouseAmbientRuntime.tick(nami, helper.getLevel(), player);
            final long readyAt = nami.getPersistentData().getLongOr(SpouseAmbientRuntime.KISS_READY, 0L);
            helper.assertTrue(readyAt >= helper.getLevel().getGameTime() + SpouseAmbientRules.KISS_COOLDOWN_TICKS - 1L,
                "a completed kiss must persist its full cooldown on the spouse entity");
            helper.assertFalse(nami.getPersistentData().contains(SpouseAmbientRuntime.ACTION),
                "a completed kiss must clear its active routine state");
            final SpouseAmbientRules.Context context = SpouseAmbientRuntime.context(nami, helper.getLevel(), player, false);
            helper.assertValueEqual(
                SpouseAmbientRules.choose(context, 0, 1),
                SpouseAmbientRules.Routine.NONE,
                "the persisted cooldown must block an immediate repeated kiss"
            );
            helper.succeed();
        });
    }

    private static NamiEntity marriedNami(
        final GameTestHelper helper,
        final ServerPlayer player,
        final BlockPos position
    ) {
        final NamiEntity nami = helper.spawn(ModEntities.NAMI.get(), position, EntitySpawnReason.EVENT);
        helper.assertValueEqual(
            MarriageData.get(helper.getLevel()).marryNami(player.getUUID(), nami.getUUID()),
            MarriageData.MarriageResult.SUCCESS,
            "the test spouse must be married exactly once"
        );
        nami.acceptMarriage(player, MarriageData.get(helper.getLevel()).bond(player.getUUID()).orElseThrow().spouseName());
        return nami;
    }

    private static ServerPlayer connectedPlayer(final GameTestHelper helper, final BlockPos relativePosition) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        final BlockPos position = helper.absolutePos(relativePosition);
        player.teleportTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        return player;
    }

    private static int countWorldItem(final GameTestHelper helper, final net.minecraft.world.item.Item item) {
        return helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(32.0),
                entity -> entity.getItem().is(item)
            ).stream()
            .mapToInt(entity -> entity.getItem().getCount())
            .sum();
    }

    private static void buildFloor(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(0, 0, 0), new BlockPos(2, 0, 2))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
    }
}
