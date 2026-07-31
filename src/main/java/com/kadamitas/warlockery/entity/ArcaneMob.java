package com.kadamitas.warlockery.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ArcaneMob extends Zombie implements ArcaneCreature {
    private final CreatureKind kind;
    private final CreatureBehavior behavior;

    public ArcaneMob(final EntityType<? extends Zombie> type, final Level level, final CreatureKind kind) {
        super(type, level);
        this.kind = kind;
        this.behavior = CreatureBehaviorFactory.create(kind);
        this.setCanPickUpLoot(kind == CreatureKind.IRONBOUND_SENTINEL || kind == CreatureKind.CIRCLE_MAGE);
        if (kind == CreatureKind.OWL || kind == CreatureKind.TOAD || kind == CreatureKind.CAT) {
            this.goalSelector.removeAllGoals(goal -> true);
            this.targetSelector.removeAllGoals(goal -> true);
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
            this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
            if (CompanionCombatRules.requiresDedicatedMeleeGoal(kind)) {
                this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
            }
        } else if (kind == CreatureKind.IRONBOUND_SENTINEL) {
            this.targetSelector.removeAllGoals(goal -> true);
            this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Monster.class, true,
                (target, serverLevel) -> !(target instanceof ArcaneCreature)));
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
        return behavior.canAttack(this, target) && super.canAttack(target);
    }

    @Override
    public boolean doHurtTarget(final ServerLevel level, final Entity target) {
        final float pairedBonus = behavior.attackDamageBonus(this, level);
        final float deathBonus = kind == CreatureKind.DEATH && target instanceof LivingEntity living
            ? Math.max(0.0F, DeathCombatRules.meleeDamage(living.getMaxHealth()) - (float) getAttributeValue(Attributes.ATTACK_DAMAGE))
            : 0.0F;
        final boolean hurt = PrimaryAttackModifier.withDamageBonus(
            this,
            pairedBonus + deathBonus,
            () -> super.doHurtTarget(level, target)
        );
        if (hurt) {
            behavior.afterAttack(this, level, target);
        }
        return hurt;
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        if (FamiliarBondRules.isClassicFamiliar(kind)
            && FamiliarBondRules.ignoresEnvironmentalDamage(source)) {
            return false;
        }
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            behavior.afterHurt(this, level, source, amount);
        }
        return hurt;
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(final Entity passenger) {
        return passenger instanceof Player player
            && SpectralMountRules.canControl(kind, CreatureBehaviorState.owner(this), player.getUUID())
            && super.canAddPassenger(passenger);
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player
            && SpectralMountRules.canControl(kind, CreatureBehaviorState.owner(this), player.getUUID())
                ? player
                : super.getControllingPassenger();
    }

    @Override
    protected void tickRidden(final Player controller, final Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        setRot(controller.getYRot(), controller.getXRot() * 0.5F);
        yRotO = yBodyRot = yHeadRot = getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(final Player controller, final Vec3 selfInput) {
        return SpectralMountRules.input(controller.xxa, controller.zza);
    }

    @Override
    protected float getRiddenSpeed(final Player controller) {
        return SpectralMountRules.speed(kind, getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
    }
}
