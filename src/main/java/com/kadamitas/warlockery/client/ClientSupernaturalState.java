package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.transformation.SupernaturalAbilityRules;
import com.kadamitas.warlockery.transformation.WerewolfShape;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Warlockery.MOD_ID, value = Dist.CLIENT)
public final class ClientSupernaturalState {
    private static volatile ModNetwork.SupernaturalSnapshot snapshot = emptySnapshot();

    private ClientSupernaturalState() {
    }

    public static void update(final ModNetwork.SupernaturalSnapshotPayload payload) {
        snapshot = payload.snapshot();
    }

    public static ModNetwork.SupernaturalSnapshot snapshot() {
        return snapshot;
    }

    public static boolean isVampire() {
        final String identity = snapshot.identity();
        return "vampire".equals(identity) || identity.endsWith(".vampire");
    }

    public static void clear() {
        snapshot = emptySnapshot();
    }

    @SubscribeEvent
    public static void handleBreakSpeed(final PlayerEvent.BreakSpeed event) {
        if (!event.getEntity().level().isClientSide()) {
            return;
        }
        final ModNetwork.SupernaturalSnapshot current = snapshot;
        if (!isWerewolf(current.identity())) {
            return;
        }
        final BlockState state = event.getState();
        event.setNewSpeed(SupernaturalAbilityRules.wolfDiggingSpeed(
            event.getNewSpeed(),
            current.level(),
            shape(current.shape()),
            event.getEntity().getMainHandItem().isEmpty(),
            state.is(BlockTags.DIRT) || state.is(BlockTags.SAND),
            event.getEntity().isShiftKeyDown(),
            isEarth(state)
        ));
    }

    private static boolean isWerewolf(final String identity) {
        return "werewolf".equals(identity) || identity.endsWith(".werewolf");
    }

    private static WerewolfShape shape(final String translationKey) {
        final int separator = translationKey.lastIndexOf('.');
        return WerewolfShape.parse(separator < 0 ? translationKey : translationKey.substring(separator + 1));
    }

    private static boolean isEarth(final BlockState state) {
        return state.is(BlockTags.DIRT)
            || state.is(BlockTags.SAND)
            || state.is(Blocks.GRAVEL)
            || state.is(Blocks.CLAY);
    }

    private static ModNetwork.SupernaturalSnapshot emptySnapshot() {
        return new ModNetwork.SupernaturalSnapshot("none", 0, 0, 0, "", "", "", "");
    }
}
