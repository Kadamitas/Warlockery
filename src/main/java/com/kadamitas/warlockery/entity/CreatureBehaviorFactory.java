package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

public final class CreatureBehaviorFactory {
    private static final Map<CreatureKind, CreatureBehavior> BEHAVIORS = CreatureBehaviorProfile.audited().stream()
        .map(ProfiledBehavior::new)
        .collect(Collectors.toUnmodifiableMap(behavior -> behavior.profile().kind(), Function.identity()));

    private CreatureBehaviorFactory() {
    }

    public static CreatureBehavior create(final CreatureKind kind) {
        return BEHAVIORS.getOrDefault(kind, new InertBehavior(kind));
    }

    private record ProfiledBehavior(CreatureBehaviorProfile profile) implements CreatureBehavior {
        @Override
        public void tick(final Mob creature, final ServerLevel level) {
            CreatureBehaviorRuntime.tick(creature, level, profile);
        }

        @Override
        public InteractionResult interact(
            final Mob creature,
            final Player player,
            final InteractionHand hand
        ) {
            return CreatureBehaviorRuntime.interact(creature, player, hand, profile);
        }

        @Override
        public boolean canAttack(final Mob creature, final LivingEntity target) {
            return CreatureBehaviorRuntime.canAttack(creature, target, profile);
        }

        @Override
        public float attackDamageBonus(final Mob creature, final ServerLevel level) {
            return CreatureBehaviorRuntime.attackDamageBonus(creature, level, profile);
        }

        @Override
        public void afterAttack(final Mob creature, final ServerLevel level, final Entity target) {
            CreatureBehaviorRuntime.afterAttack(creature, level, target, profile);
        }

        @Override
        public void afterHurt(
            final Mob creature,
            final ServerLevel level,
            final DamageSource source,
            final float amount
        ) {
            CreatureBehaviorRuntime.afterHurt(creature, level, source, amount, profile);
        }
    }

    private record InertBehavior(CreatureKind kind) implements CreatureBehavior {
        @Override
        public CreatureBehaviorProfile profile() {
            throw new IllegalStateException("No audited creature behavior profile for " + kind);
        }
    }
}
