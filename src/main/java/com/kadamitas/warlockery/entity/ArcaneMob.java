package com.kadamitas.warlockery.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ArcaneMob extends Zombie implements ArcaneCreature {
    private static final EntityDataAccessor<Boolean> DATA_HOBGOBLIN_ASSAULT_VARIANT = SynchedEntityData.defineId(
        ArcaneMob.class,
        EntityDataSerializers.BOOLEAN
    );
    private final CreatureKind kind;
    private final CreatureBehavior behavior;

    public ArcaneMob(final EntityType<? extends Zombie> type, final Level level, final CreatureKind kind) {
        super(type, level);
        this.kind = kind;
        this.behavior = CreatureBehaviorFactory.create(kind);
        // F13 dropped the CIRCLE_MAGE disjunct when CircleMageEntity took that kind, and F36 has
        // now done the same for IRONBOUND_SENTINEL: both kinds are built by dedicated bodies that
        // normalize loot pickup off, so no construction path reaches this gate with either kind
        // and the expression had no remaining true branch. No arcane ground mob picks up loot.
        this.setCanPickUpLoot(false);
        if (kind == CreatureKind.OWL || kind == CreatureKind.TOAD || kind == CreatureKind.CAT) {
            this.goalSelector.removeAllGoals(goal -> true);
            this.targetSelector.removeAllGoals(goal -> true);
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
            this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
            if (CompanionCombatRules.requiresDedicatedMeleeGoal(kind)) {
                this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
            }
        }
    }

    @Override
    public CreatureKind creatureKind() {
        return this.kind;
    }

    public boolean isHobgoblinAssaultVariant() {
        return entityData.get(DATA_HOBGOBLIN_ASSAULT_VARIANT);
    }

    public void setHobgoblinAssaultVariant(final boolean variant) {
        entityData.set(DATA_HOBGOBLIN_ASSAULT_VARIANT, variant);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HOBGOBLIN_ASSAULT_VARIANT, false);
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        behavior.tick(this, level);
        tickSpecializedActivity(level);
    }

    /**
     * Narrow specialization seam for mobs whose runtime replaces the generic tactical and ambient layers.
     */
    protected void tickSpecializedActivity(final ServerLevel level) {
        TacticalCombatRuntime.tick(this, level, kind);
        AmbientActivityRuntime.tick(this, level, kind);
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
        final boolean hurt = PrimaryAttackModifier.withDamageBonus(
            this,
            behavior.attackDamageBonus(this, level),
            () -> super.doHurtTarget(level, target)
        );
        if (hurt) {
            behavior.afterAttack(this, level, target);
        }
        return hurt;
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        if (isEnvironmentallyImmuneFamiliar()
            && (FamiliarBondRules.ignoresEnvironmentalDamage(source)
                || source.getEntity() == null && source.getDirectEntity() == null)) {
            return false;
        }
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            TacticalCombatRuntime.rememberIncomingThreat(this, level, source);
            behavior.afterHurt(this, level, source, amount);
        }
        return hurt;
    }

    private boolean isEnvironmentallyImmuneFamiliar() {
        return FamiliarBondRules.isClassicFamiliar(kind) || kind == CreatureKind.FAMILIAR;
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

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("WarlockeryHobgoblinAssaultVariant", isHobgoblinAssaultVariant());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        setHobgoblinAssaultVariant(input.getBooleanOr("WarlockeryHobgoblinAssaultVariant", false));
    }
}
