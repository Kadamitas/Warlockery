package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.config.WarlockeryConfig;
import com.kadamitas.warlockery.entity.HobgoblinEntity;
import com.kadamitas.warlockery.entity.WerewolfEntity;
import com.kadamitas.warlockery.entity.WerewolfHunterEntity;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class CreatureWorldIntegration {
    private CreatureWorldIntegration() {
    }

    public static void tick(final ServerLevel level) {
        if (level.players().isEmpty()) return;
        final long gameTime = level.getGameTime();
        final boolean scanForPillagers = WarlockeryConfig.armPillagers()
            && scheduled(gameTime, WarlockeryConfig.pillagerScanInterval());
        final boolean attemptEnclave = WarlockeryConfig.hobgoblinEnclaves()
            && scheduled(gameTime, WarlockeryConfig.hobgoblinEnclaveInterval());
        final boolean attemptHunt = WarlockeryConfig.silverHunts()
            && scheduled(gameTime, WarlockeryConfig.silverHuntInterval());
        if (!scanForPillagers && !attemptEnclave && !attemptHunt) return;

        final ServerPlayer player = level.players().get(level.getRandom().nextInt(level.players().size()));
        if (scanForPillagers) armNearbyPillagers(level, player);

        if (attemptEnclave && level.getRandom().nextDouble() < WarlockeryConfig.hobgoblinEnclaveChance()) {
            tryFoundHobgoblinEnclave(level, player);
        }
        if (attemptHunt
            && level.getRandom().nextDouble() < WarlockeryConfig.silverHuntChance()
            && isFullMoonNight(level, player)) {
            spawnSilverHunt(level, player);
        }
    }

    static boolean scheduled(final long gameTime, final int interval) {
        return gameTime % interval == 0L;
    }

    private static void tryFoundHobgoblinEnclave(final ServerLevel level, final ServerPlayer player) {
        if (!level.isVillage(player.blockPosition())) return;
        final AABB village = new AABB(player.blockPosition()).inflate(48, 20, 48);
        if (!level.getEntitiesOfClass(HobgoblinEntity.class, village).isEmpty()) return;
        final Optional<BlockPos> bell = BlockPos.betweenClosedStream(
                player.blockPosition().offset(-32, -12, -32), player.blockPosition().offset(32, 12, 32))
            .filter(pos -> level.getBlockState(pos).is(Blocks.BELL))
            .map(BlockPos::immutable)
            .min(Comparator.comparingDouble(pos -> pos.distSqr(player.blockPosition())));
        if (bell.isEmpty()) return;

        final int residents = 3 + level.getRandom().nextInt(3);
        for (int index = 0; index < residents; index++) {
            final BlockPos spawn = surface(level, bell.get().offset(level.getRandom().nextInt(9) - 4, 0, level.getRandom().nextInt(9) - 4));
            final HobgoblinEntity hobgoblin = ModEntities.HOBGOBLIN.get().spawn(level, spawn, EntitySpawnReason.EVENT);
            if (hobgoblin != null) hobgoblin.assignProfessionFromVillage();
        }
        if (level.getRandom().nextInt(5) == 0) ModEntities.STONEBROKER.get().spawn(level, surface(level, bell.get()), EntitySpawnReason.EVENT);
        Warlockery.LOGGER.info("A hobgoblin enclave settled around village bell at {}", bell.get());
    }

    private static void spawnSilverHunt(final ServerLevel level, final ServerPlayer player) {
        final BlockPos origin = surface(level, player.blockPosition().offset(20 + level.getRandom().nextInt(12), 0, level.getRandom().nextInt(25) - 12));
        final WerewolfEntity werewolf = ModEntities.WEREWOLF.get().spawn(level, origin, EntitySpawnReason.EVENT);
        final WerewolfHunterEntity hunter = ModEntities.WEREWOLF_HUNTER.get().spawn(level, surface(level, origin.offset(9, 0, 2)), EntitySpawnReason.PATROL);
        final Pillager pillager = EntityTypes.PILLAGER.spawn(level, surface(level, origin.offset(11, 0, -2)), EntitySpawnReason.PATROL);
        if (werewolf == null || hunter == null) return;
        hunter.setTarget(werewolf);
        werewolf.setTarget(hunter);
        if (pillager != null) {
            equipSilver(pillager);
            pillager.setTarget(werewolf);
        }
    }

    private static void armNearbyPillagers(final ServerLevel level, final ServerPlayer player) {
        final List<WerewolfEntity> werewolves = level.getEntitiesOfClass(WerewolfEntity.class,
            new AABB(player.blockPosition()).inflate(48));
        if (werewolves.isEmpty()) return;
        level.getEntitiesOfClass(Pillager.class, new AABB(player.blockPosition()).inflate(48)).forEach(pillager -> {
            final WerewolfEntity target = werewolves.stream().min(Comparator.comparingDouble(pillager::distanceToSqr)).orElseThrow();
            equipSilver(pillager);
            pillager.setTarget(target);
        });
    }

    private static void equipSilver(final Pillager pillager) {
        pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.ALL.get("silver_repeater").get()));
        pillager.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.ALL.get("ingredient_bolt_silver").get(), 64));
    }

    private static boolean isFullMoonNight(final ServerLevel level, final ServerPlayer player) {
        final long time = level.getOverworldClockTime() % 24_000L;
        return time >= 13_000L && time <= 23_000L
            && level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, player.position()) == MoonPhase.FULL_MOON;
    }

    private static BlockPos surface(final ServerLevel level, final BlockPos pos) {
        return new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()), pos.getZ());
    }
}
