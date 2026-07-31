package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.block.WitchcraftCompatibilityTags;
import com.kadamitas.warlockery.item.FlyingBroomRules;
import com.kadamitas.warlockery.registry.ModEffects;
import com.kadamitas.warlockery.registry.ModItems;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class BroomEntity extends VehicleEntity {
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);
    private FlyingBroomRules.ControlInput controlInput = FlyingBroomRules.ControlInput.IDLE;
    private long lastControlTick = Long.MIN_VALUE;
    private static final EntityDataAccessor<Boolean> DATA_GLIDING = SynchedEntityData.defineId(
        BroomEntity.class,
        EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<ItemStack> DATA_BROOM = SynchedEntityData.defineId(
        BroomEntity.class,
        EntityDataSerializers.ITEM_STACK
    );
    private static final EntityDataAccessor<Byte> DATA_HAND = SynchedEntityData.defineId(
        BroomEntity.class,
        EntityDataSerializers.BYTE
    );

    public BroomEntity(final EntityType<? extends BroomEntity> type, final Level level) {
        super(type, level);
        blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GLIDING, false);
        builder.define(DATA_BROOM, ItemStack.EMPTY);
        builder.define(DATA_HAND, (byte) InteractionHand.MAIN_HAND.ordinal());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            interpolation.interpolate();
            return;
        }
        if (!(getControllingPassenger() instanceof ServerPlayer rider) || !rider.isAlive()) {
            releaseBroom(null);
            discard();
            return;
        }
        final ItemStack broom = getBroomStack();
        final boolean controlsFresh = FlyingBroomRules.controlsAreFresh(level().getGameTime(), lastControlTick);
        if (!controlsFresh && isGliding()) {
            setGliding(false);
        }
        final FlyingBroomRules.FlightDecision decision = FlyingBroomRules.decide(
            true,
            broom.is(WitchcraftCompatibilityTags.FLYING_BROOMS),
            controlsFresh && isGliding(),
            rider.hasEffect(ModEffects.SOARING)
        );
        if (!decision.active()) {
            rider.stopRiding();
            discard();
            return;
        }
        final float vehicleYaw = FlyingBroomRules.nextYaw(getYRot(), rider.getYRot(), decision.torque());
        setYRot(vehicleYaw);
        setXRot(Mth.lerp(0.16F, getXRot(), rider.getXRot() * 0.5F));
        final Vec3 requestedVelocity = FlyingBroomRules.nextVelocity(
            getDeltaMovement(),
            vehicleYaw,
            rider.getXRot(),
            controlsFresh ? controlInput : FlyingBroomRules.ControlInput.IDLE,
            decision
        );
        final Vec3 startingPosition = position();
        setDeltaMovement(requestedVelocity);
        move(MoverType.SELF, requestedVelocity);
        setDeltaMovement(FlyingBroomRules.retainUnblockedVelocity(
            requestedVelocity,
            position().subtract(startingPosition)
        ));
        applyEffectsFromBlocks();
        rider.resetFallDistance();
        resetFallDistance();
        hurtMarked = true;
        if (tickCount % 20 == 0) {
            broom.hurtAndBreak(1, rider, preferredHand());
            setBroomStack(broom);
            if (broom.isEmpty()) {
                rider.stopRiding();
                discard();
            }
        }
    }

    public void setGliding(final boolean gliding) {
        entityData.set(DATA_GLIDING, gliding);
    }

    public boolean isGliding() {
        return entityData.get(DATA_GLIDING);
    }

    public float getFlightSpeed() {
        return (float) getDeltaMovement().length();
    }

    public void setControlInput(final FlyingBroomRules.ControlInput input) {
        controlInput = java.util.Objects.requireNonNull(input, "input");
        lastControlTick = level().getGameTime();
    }

    public FlyingBroomRules.ControlInput getControlInput() {
        return currentControlInput();
    }

    public ItemStack getBroomStack() {
        return entityData.get(DATA_BROOM);
    }

    public void takeBroom(final ItemStack broom, final InteractionHand hand) {
        setBroomStack(broom);
        entityData.set(DATA_HAND, (byte) hand.ordinal());
    }

    public void returnBroomTo(final Player rider) {
        releaseBroom(rider);
        if (rider.getVehicle() == this) {
            rider.stopRiding();
        }
        discard();
    }

    @Override
    public boolean isClientAuthoritative() {
        return false;
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return interpolation;
    }

    @Override
    public boolean hurtClient(final DamageSource source) {
        return false;
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(final @Nullable Entity other) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    protected boolean canAddPassenger(final Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof LivingEntity living ? living : null;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(
        final Entity passenger,
        final EntityDimensions dimensions,
        final float scale
    ) {
        return new Vec3(0.0D, 0.28D, 0.0D);
    }

    @Override
    protected void removePassenger(final Entity passenger) {
        super.removePassenger(passenger);
        setGliding(false);
        controlInput = FlyingBroomRules.ControlInput.IDLE;
        lastControlTick = Long.MIN_VALUE;
        if (!level().isClientSide()) {
            releaseBroom(passenger instanceof Player player && player.isAlive() ? player : null);
        }
    }

    @Override
    public void remove(final RemovalReason reason) {
        if (!level().isClientSide() && !getBroomStack().isEmpty()) {
            releaseBroom(getFirstPassenger() instanceof Player player && player.isAlive() ? player : null);
        }
        super.remove(reason);
    }

    @Override
    protected Item getDropItem() {
        return ModItems.ALL.get("ingredient_broom_enchanted").get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(getDropItem());
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        output.putBoolean("Gliding", isGliding());
        output.putByte("PreferredHand", (byte) preferredHand().ordinal());
        if (!getBroomStack().isEmpty()) {
            output.store("Broom", ItemStack.CODEC, getBroomStack());
        }
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        setGliding(input.getBooleanOr("Gliding", false));
        setBroomStack(input.read("Broom", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        entityData.set(DATA_HAND, input.getByteOr("PreferredHand", (byte) 0));
    }

    private void releaseBroom(final @Nullable Player rider) {
        final ItemStack broom = getBroomStack();
        if (broom.isEmpty()) {
            return;
        }
        setBroomStack(ItemStack.EMPTY);
        if (rider != null) {
            final InteractionHand hand = preferredHand();
            if (rider.getItemInHand(hand).isEmpty()) {
                rider.setItemInHand(hand, broom);
                return;
            }
            if (rider.getInventory().add(broom)) {
                return;
            }
            rider.drop(broom, false);
            return;
        }
        if (level() instanceof ServerLevel serverLevel) {
            spawnAtLocation(serverLevel, broom, 0.25F);
        }
    }

    private void setBroomStack(final ItemStack broom) {
        entityData.set(DATA_BROOM, broom.copy());
    }

    private FlyingBroomRules.ControlInput currentControlInput() {
        return FlyingBroomRules.controlsAreFresh(level().getGameTime(), lastControlTick)
            ? controlInput
            : FlyingBroomRules.ControlInput.IDLE;
    }

    private InteractionHand preferredHand() {
        return entityData.get(DATA_HAND) == InteractionHand.OFF_HAND.ordinal()
            ? InteractionHand.OFF_HAND
            : InteractionHand.MAIN_HAND;
    }
}
