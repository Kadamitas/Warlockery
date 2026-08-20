package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class InfernalPactEffects {
    public static final String OWNER_KEY = "WarlockeryInfernalOwner";

    private InfernalPactEffects() {
    }

    public static void tick(final Player owner) {
        if (!(owner.level() instanceof ServerLevel level) || owner.tickCount % 20 != 0) {
            return;
        }
        final LivingEntity commandedTarget = owner.getLastHurtMob() != null
            ? owner.getLastHurtMob()
            : owner.getLastHurtByMob();
        level.getEntitiesOfClass(
            Mob.class,
            new AABB(owner.blockPosition()).inflate(32),
            mob -> mob.typeHolder().is(WarlockeryTags.EntityTypes.DEMONS)
                && com.kadamitas.warlockery.entity.InfernalHierarchyRules.commandAccepted(
                    owner.getUUID(),
                    com.kadamitas.warlockery.entity.InfernalHierarchyRuntime.directPactOwner(mob),
                    com.kadamitas.warlockery.entity.InfernalHierarchyRuntime.animusOwner(mob)
                )
        ).forEach(demon -> {
            if (demon instanceof com.kadamitas.warlockery.entity.HellhoundEntity hellhound) {
                // F09: Hellhound commands travel through the dedicated bounded semantic seam
                // instead of an external raw target write.
                com.kadamitas.warlockery.entity.HellhoundLifeRuntime.deliverOwnerCommand(
                    hellhound, level, owner,
                    commandedTarget != null && commandedTarget.isAlive() ? commandedTarget : null
                );
                return;
            }
            if (demon.getTarget() == owner) {
                demon.setTarget(null);
            }
            if (commandedTarget != null && commandedTarget != owner && commandedTarget.isAlive()
                && demon.canAttack(commandedTarget)) {
                demon.setTarget(commandedTarget);
            }
        });
    }
}
