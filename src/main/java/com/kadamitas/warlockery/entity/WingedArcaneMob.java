package com.kadamitas.warlockery.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public abstract class WingedArcaneMob extends Monster implements ArcaneCreature, RangedAttackMob {
    private final CreatureKind kind;
    private final CreatureBehavior behavior;

    protected WingedArcaneMob(
        final EntityType<? extends Monster> type,
        final Level level,
        final CreatureKind kind
    ) {
        super(type, level);
        this.kind = kind;
        this.behavior = CreatureBehaviorFactory.create(kind);
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes(final CreatureKind kind) {
        final double health = kind == CreatureKind.IMP ? 24.0 : 30.0;
        final double attack = kind == CreatureKind.IMP ? 5.0 : 4.0;
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, health)
            .add(Attributes.ATTACK_DAMAGE, attack)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.FLYING_SPEED, kind == CreatureKind.IMP ? 0.38 : 0.34)
            .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1.1, 30, 16.0F));
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.9));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        registerArcaneTargets();
    }

    protected abstract void registerArcaneTargets();

    protected final void targetPlayers() {
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    protected final void targetHostileMobs() {
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
            this,
            Monster.class,
            true,
            (target, level) -> !(target instanceof ArcaneCreature)
        ));
    }

    @Override
    protected PathNavigation createNavigation(final Level level) {
        final FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    public CreatureKind creatureKind() {
        return kind;
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        behavior.tick(this, level);
        customWingedAiStep(level);
        TacticalCombatRuntime.tick(this, level, kind);
        AmbientActivityRuntime.tick(this, level, kind);
    }

    protected void customWingedAiStep(final ServerLevel level) {
    }

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        final InteractionResult result = behavior.interact(this, player, hand);
        return result == InteractionResult.PASS ? super.mobInteract(player, hand) : result;
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
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
            TacticalCombatRuntime.rememberIncomingThreat(this, level, source);
            behavior.afterHurt(this, level, source, amount);
        }
        return hurt;
    }
}
