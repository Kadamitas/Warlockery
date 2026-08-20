package com.kadamitas.warlockery.entity;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The only F09 cure mutation owner. It preserves every successful observation of the current
 * cure: Weakness prerequisite, exact Golden Apple requirement, sturdy-face acceleration, one
 * consumption per valid attempt, the current diagnostic messages, the persistent finisher-owned
 * vanilla Wolf created with the CONVERSION reason, and the final Hellhound discard. The success
 * path is transactional: the Wolf is constructed before any consumption or progress settlement,
 * so a failed construction produces no duplicate Wolf, double settlement, or completed-and-stuck
 * state, and completion releases every Hellhound-only claim including the exact legacy hearth.
 */
public final class HellhoundCureRuntime {
    static final String CURE_PROGRESS_KEY = "WarlockeryHellhoundCure";

    private HellhoundCureRuntime() {
    }

    public static InteractionResult cure(
        final Mob creature,
        final ServerLevel level,
        final Player player,
        final ItemStack held
    ) {
        final int walls = sturdyWalls(creature, level);
        final HellhoundCureRules.Result result = HellhoundCureRules.advance(
            creature.getPersistentData().getIntOr(CURE_PROGRESS_KEY, 0),
            creature.hasEffect(MobEffects.WEAKNESS),
            held.is(Items.GOLDEN_APPLE),
            walls
        );
        send(player, "message.warlockery.creature.hellhound_cure."
            + result.diagnostic().name().toLowerCase(Locale.ROOT));
        if (result.diagnostic() == HellhoundCureRules.Diagnostic.NEEDS_WEAKNESS) {
            return InteractionResult.PASS;
        }
        if (result.diagnostic() == HellhoundCureRules.Diagnostic.NEEDS_GOLDEN_APPLE) {
            return InteractionResult.FAIL;
        }
        if (!result.cured()) {
            consumeOne(player, held);
            creature.getPersistentData().putInt(CURE_PROGRESS_KEY, result.progress());
            return InteractionResult.SUCCESS;
        }
        // Prepare: construct the replacement before consuming or settling anything.
        final Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.CONVERSION);
        if (wolf == null) {
            return InteractionResult.FAIL;
        }
        // Commit: settle consumption and progress exactly once, release Hellhound-only state,
        // then perform the unchanged replacement chain.
        consumeOne(player, held);
        creature.getPersistentData().putInt(CURE_PROGRESS_KEY, result.progress());
        releaseHellhoundClaims(creature, level);
        wolf.snapTo(creature.getX(), creature.getY(), creature.getZ(), creature.getYRot(), creature.getXRot());
        wolf.tame(player);
        wolf.setPersistenceRequired();
        level.addFreshEntity(wolf);
        creature.discard();
        return InteractionResult.SUCCESS;
    }

    /**
     * Releases target, navigation, pack role/call, territory activity, owner command delivery,
     * heat point, and the exact still-owned legacy hearth claim before the final discard.
     */
    private static void releaseHellhoundClaims(final Mob creature, final ServerLevel level) {
        if (creature instanceof HellhoundEntity hellhound) {
            HellhoundLifeRuntime.releaseAll(hellhound, level, true);
        } else {
            creature.setTarget(null);
            creature.getNavigation().stop();
            AmbientActivityRuntime.releaseExactOwnedLegacyHearth(creature, level);
        }
    }

    private static int sturdyWalls(final Mob creature, final ServerLevel level) {
        int walls = 0;
        for (final Direction direction : new Direction[] {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
        }) {
            final BlockPos wall = creature.blockPosition().relative(direction);
            if (level.getBlockState(wall).isFaceSturdy(level, wall, direction.getOpposite())) {
                walls++;
            }
        }
        return walls;
    }

    private static void consumeOne(final Player player, final ItemStack stack) {
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
    }

    private static void send(final Player player, final String key, final Object... arguments) {
        player.sendSystemMessage(Component.translatable(key, arguments));
    }
}
