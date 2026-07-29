package com.kadamitas.warlockery.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import java.util.Set;
import net.minecraft.world.level.Level;

public class SpiritMob extends Vex implements ArcaneCreature {
    private final CreatureKind kind;
    private final CreatureBehavior behavior;

    public SpiritMob(final EntityType<? extends Vex> type, final Level level, final CreatureKind kind) {
        super(type, level);
        this.kind = kind;
        this.behavior = CreatureBehaviorFactory.create(kind);
        if (kind == CreatureKind.SPIRIT) {
            this.targetSelector.removeAllGoals(goal -> true);
            this.goalSelector.addGoal(1, new AvoidEntityGoal<>(
                this,
                Player.class,
                player -> SpiritTemperamentRules.shouldFlee(
                    CreatureBehaviorState.owner(this).isPresent(),
                    player.isAlive(),
                    this.distanceToSqr(player)
                ),
                12.0F,
                1.2,
                1.5,
                player -> true
            ));
        } else if (Set.of(CreatureKind.HEX_BAT, CreatureKind.BANSHEE, CreatureKind.UMBRAL_SIGIL,
            CreatureKind.POLTERGEIST, CreatureKind.SPECTRE, CreatureKind.IMP).contains(kind)) {
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }
    }

    @Override
    public CreatureKind creatureKind() {
        return this.kind;
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        behavior.tick(this, level);
    }

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        final InteractionResult result = behavior.interact(this, player, hand);
        return result == InteractionResult.PASS ? super.mobInteract(player, hand) : result;
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        if (kind == CreatureKind.SPIRIT) {
            final Player owner = CreatureBehaviorState.owner(this)
                .map(this.level()::getPlayerByUUID)
                .orElse(null);
            return SpiritTemperamentRules.canAttack(owner != null, owner != null && owner.getLastHurtByMob() == target)
                && behavior.canAttack(this, target)
                && super.canAttack(target);
        }
        return behavior.canAttack(this, target) && super.canAttack(target);
    }

    @Override
    public boolean doHurtTarget(final ServerLevel level, final Entity target) {
        final boolean hurt = super.doHurtTarget(level, target);
        if (hurt) {
            behavior.afterAttack(this, level, target);
        }
        return hurt;
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            behavior.afterHurt(this, level, source, amount);
        }
        return hurt;
    }
}
