package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.entity.GoblinHostilityRules;
import com.kadamitas.warlockery.entity.HobgoblinEntity;
import com.kadamitas.warlockery.world.VillageAssaultRules.AssaultKind;
import com.kadamitas.warlockery.world.VillageAssaultRules.SettlementKind;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GoblinRaidRuntime {
    private GoblinRaidRuntime() {
    }

    public static void tick(final ServerLevel level) {
        VillageAssaultRuntime.tick(level);
    }

    public static void coordinate(final HobgoblinEntity goblin, final ServerLevel level) {
        final Optional<BlockPos> center = goblin.raidCenter();
        if (center.isEmpty() || !goblin.isVillageRaider() || goblin.isTrading() || goblin.isNoAi()) {
            return;
        }
        final LivingEntity current = goblin.getTarget();
        if (current != null && current.isAlive() && GoblinHostilityRules.isHumanVillager(current.getType())) {
            return;
        }
        final Optional<LivingEntity> sharedTarget = level.getEntitiesOfClass(
                HobgoblinEntity.class,
                goblin.getBoundingBox().inflate(32.0),
                other -> other != goblin
                    && other.isVillageRaider()
                    && other.raidCenter().filter(center.orElseThrow()::equals).isPresent()
                    && other.getTarget() != null
                    && other.getTarget().isAlive()
                    && GoblinHostilityRules.isHumanVillager(other.getTarget().getType())
            ).stream()
            .map(HobgoblinEntity::getTarget)
            .findFirst();
        final Optional<LivingEntity> target = sharedTarget.or(() -> level.getEntitiesOfClass(
                Villager.class,
                new AABB(center.orElseThrow()).inflate(48.0, 16.0, 48.0),
                villager -> villager.isAlive() && GoblinHostilityRules.isHumanVillager(villager.getType())
            ).stream()
            .min(Comparator.comparingDouble(goblin::distanceToSqr))
            .map(LivingEntity.class::cast));
        if (target.isPresent()) {
            goblin.setTarget(target.orElseThrow());
            return;
        }
        if (goblin.distanceToSqr(Vec3.atCenterOf(center.orElseThrow())) > 16.0) {
            final BlockPos destination = center.orElseThrow();
            goblin.getNavigation().moveTo(
                destination.getX() + 0.5,
                destination.getY(),
                destination.getZ() + 0.5,
                1.0
            );
        }
    }

    static int spawnWave(
        final ServerLevel level,
        final BlockPos center,
        final int wave,
        final int radius
    ) {
        return VillageAssaultRuntime.spawnWave(
            level,
            center,
            wave,
            AssaultKind.GOBLIN,
            SettlementKind.HUMAN,
            radius
        );
    }
}
