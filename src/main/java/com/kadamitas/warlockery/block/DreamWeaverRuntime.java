package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.ModEntities;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;

public final class DreamWeaverRuntime {
    private DreamWeaverRuntime() {
    }

    public static void handleWake(final PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        final Optional<BlockPos> sleeping = player.getSleepingPos();
        final Optional<Weaver> weaver = sleeping.flatMap(pos -> nearest(level, pos));
        if (!DreamWeaverRules.canReward(
            true,
            player.getSleepTimer(),
            event.wakeImmediately(),
            weaver.isPresent()
        )) {
            return;
        }
        apply(level, player, sleeping.orElseThrow(), weaver.orElseThrow().mode());
    }

    private static Optional<Weaver> nearest(final ServerLevel level, final BlockPos bed) {
        return BlockPos.betweenClosedStream(
                bed.offset(-DreamWeaverRules.SEARCH_RADIUS, -DreamWeaverRules.SEARCH_RADIUS, -DreamWeaverRules.SEARCH_RADIUS),
                bed.offset(DreamWeaverRules.SEARCH_RADIUS, DreamWeaverRules.SEARCH_RADIUS, DreamWeaverRules.SEARCH_RADIUS)
            )
            .filter(level::isLoaded)
            .filter(pos -> pos.distSqr(bed) <= DreamWeaverRules.SEARCH_RADIUS * DreamWeaverRules.SEARCH_RADIUS)
            .map(pos -> new Weaver(pos.immutable(), level.getBlockState(pos)))
            .filter(weaver -> weaver.state().getBlock() instanceof DreamWeaverBlock)
            .min(Comparator.comparingDouble(weaver -> weaver.pos().distSqr(bed)));
    }

    private static void apply(
        final ServerLevel level,
        final ServerPlayer player,
        final BlockPos bed,
        final DreamWeaverMode mode
    ) {
        final boolean protectedDream = hasProtectivePlant(level, bed);
        final DreamWeaverRules.WakeReward reward = DreamWeaverRules.reward(mode, protectedDream);
        if (reward.nutrition() > 0) {
            player.getFoodData().eat(reward.nutrition(), reward.saturationModifier());
        }
        switch (reward.effect()) {
            case "regeneration" -> player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 1));
            case "saturation" -> player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 1, 1));
            case "speed" -> player.addEffect(new MobEffectInstance(MobEffects.SPEED, 2_400, 1));
            case "night_vision" -> player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 6_000, 0));
            case "strength" -> player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 2_400, 1));
            case "absorption" -> player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2_400, 1));
            case "darkness" -> player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 300, 0));
            default -> throw new IllegalStateException("Unsupported dream reward: " + reward.effect());
        }
        if (reward.spawnNightmare()) {
            spawnNightmare(level, player, bed);
        }
        player.sendSystemMessage(Component.translatable(
            reward.protectedDream()
                ? "message.warlockery.dream_weaver.protected"
                : "message.warlockery.dream_weaver.wake",
            Component.translatable("dream_weaver_mode.warlockery." + mode.getSerializedName())
        ));
    }

    private static boolean hasProtectivePlant(final ServerLevel level, final BlockPos bed) {
        return BlockPos.betweenClosedStream(bed.offset(-4, -2, -4), bed.offset(4, 2, 4))
            .filter(level::isLoaded)
            .anyMatch(pos -> level.getBlockState(pos).is(WitchcraftCompatibilityTags.DREAM_PROTECTIVE_PLANTS)
                || level.getFluidState(pos).is(WitchcraftCompatibilityTags.DREAM_PROTECTIVE_FLUIDS));
    }

    private static void spawnNightmare(
        final ServerLevel level,
        final ServerPlayer player,
        final BlockPos bed
    ) {
        final var entity = ModEntities.ALL.get("nightmare").get().create(level, EntitySpawnReason.EVENT);
        if (!(entity instanceof Mob nightmare)) {
            return;
        }
        nightmare.snapTo(bed.getX() + 3.5, bed.getY() + 1.0, bed.getZ() + 0.5);
        if (!level.noCollision(nightmare)) {
            nightmare.discard();
            return;
        }
        nightmare.setTarget(player);
        level.addFreshEntity(nightmare);
    }

    private record Weaver(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        private DreamWeaverMode mode() {
            return state.getValue(DreamWeaverBlock.MODE);
        }
    }
}
