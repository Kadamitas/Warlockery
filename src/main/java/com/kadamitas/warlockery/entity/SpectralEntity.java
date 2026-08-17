package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Minimal shared base for the two F19 spectral neighbours. It supplies only what both genuinely
 * share as bodies: a collision-enabled flying frame with no gravity, no fall damage, no step
 * sound, no target selector at all, the preserved Vex-family sounds and three XP, the exact
 * registry attribute baseline, and the generic spirit-binder interaction that writes the one
 * owner UUID.
 *
 * <p>It deliberately supplies no motive. Deciding what to look for, when to withdraw, whether to
 * warn or defend, and where to go is owned entirely by the per-species runtime, so a Lost Soul
 * and a Spirit can never converge on shared behavior through this class.</p>
 *
 * <p>Deliberately not an {@code Enemy}, Vex, Monster, Zombie, {@link SpiritMob},
 * {@link WingedArcaneMob}, familiar, or caster.</p>
 */
public abstract class SpectralEntity extends PathfinderMob implements ArcaneCreature {
    static final TagKey<Block> CONTACT_HAZARDS = TagKey.create(
        Registries.BLOCK,
        Identifier.fromNamespaceAndPath("warlockery", "ai/contact_hazards")
    );
    static final TagKey<Block> SOUL_LIGHTS = AmbientActivityTags.SOUL_LIGHTS;
    static final double ROUTE_SPEED = 1.0D;
    static final double ESCAPE_SPEED = 1.2D;

    private final CreatureKind spectralKind;
    private final CreatureBehavior bindingBehavior;

    protected SpectralEntity(
        final EntityType<? extends PathfinderMob> type,
        final Level level,
        final CreatureKind kind
    ) {
        super(type, level);
        this.spectralKind = kind;
        this.bindingBehavior = CreatureBehaviorFactory.create(kind);
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
        this.xpReward = 3;
        setNoGravity(true);
    }

    @Override
    public final CreatureKind creatureKind() {
        return spectralKind;
    }

    /**
     * LOOK only. Movement authority belongs exclusively to the species runtime, and no target
     * goal is ever registered, so neither species can acquire a target through the goal selector.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new LookOnlyRandomLookGoal(this));
    }

    private static final class LookOnlyRandomLookGoal extends RandomLookAroundGoal {
        private LookOnlyRandomLookGoal(final Mob mob) {
            super(mob);
            setFlags(EnumSet.of(Flag.LOOK));
        }
    }

    public int operationalTargetGoalCount() {
        return targetSelector.getAvailableGoals().size();
    }

    public List<String> operationalGoalNames() {
        return goalSelector.getAvailableGoals().stream()
            .map(goal -> goal.getGoal().getClass().getSimpleName())
            .toList();
    }

    @Override
    protected PathNavigation createNavigation(final Level level) {
        final FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void checkFallDamage(
        final double ya,
        final boolean onGround,
        final BlockState onState,
        final BlockPos pos
    ) {
    }

    @Override
    protected void playStepSound(final BlockPos pos, final BlockState blockState) {
    }

    /**
     * The one preserved outward interaction: the audited spirit-binder tag writes the single
     * generic owner UUID. Any interaction that actually establishes ownership is completed with
     * {@link #onBindingCommitted} inside the same call, so the transition is atomic and no
     * species episode can survive the interaction that bound it.
     */
    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        final Optional<UUID> before = CreatureBehaviorState.owner(this);
        final InteractionResult result = bindingBehavior.interact(this, player, hand);
        final Optional<UUID> after = CreatureBehaviorState.owner(this);
        if (before.isEmpty() && after.isPresent() && level() instanceof ServerLevel serverLevel) {
            onBindingCommitted(serverLevel, after.orElseThrow());
        }
        return result == InteractionResult.PASS ? super.mobInteract(player, hand) : result;
    }

    protected abstract void onBindingCommitted(ServerLevel level, UUID owner);

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VEX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VEX_DEATH;
    }

    @Override
    protected void populateDefaultEquipmentSlots(
        final net.minecraft.util.RandomSource random,
        final DifficultyInstance difficulty
    ) {
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level,
        final DifficultyInstance difficulty,
        final EntitySpawnReason reason,
        final @Nullable SpawnGroupData groupData
    ) {
        final SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
        normalizeEquipment();
        // The registry-owned Vex attribute baseline is exact; the generic Mob random
        // follow-range spawn bonus would make it nondeterministic.
        final AttributeInstance followRange = getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.removeModifier(RANDOM_SPAWN_BONUS_ID);
        }
        setDeltaMovement(Vec3.ZERO);
        return result;
    }

    protected final void normalizeEquipment() {
        for (final EquipmentSlot slot : EquipmentSlot.values()) {
            if (!getItemBySlot(slot).isEmpty()) {
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    // ---------------------------------------------------------------- shared mechanics

    /**
     * The exact audited owner aura, preserved byte for byte from the generic writer that used to
     * own it: ambient, invisible Night Vision for 240 ticks. Nothing else is applied.
     */
    static void applyOwnerAura(final LivingEntity owner, final int durationTicks) {
        owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, durationTicks, 0, true, false));
    }

    static Optional<LivingEntity> resolveOwner(final Mob creature, final ServerLevel level) {
        return CreatureBehaviorState.owner(creature)
            .map(level::getEntity)
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(LivingEntity::isAlive);
    }

    static String dimensionOf(final ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    static boolean isHazardBlock(final BlockState state) {
        return state.is(Blocks.FIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.SOUL_CAMPFIRE)
            || state.is(Blocks.LAVA)
            || state.is(CONTACT_HAZARDS);
    }

    static boolean footprintLoaded(final ServerLevel level, final AABB box) {
        return level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
            && level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.maxZ))
            && level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.maxZ))
            && level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.minZ));
    }

    /**
     * Bounded local hazard observation over the 3 x 3 x 3 neighbourhood. Reads stop at
     * {@code maxReads} and an unloaded footprint is never forced: it simply reports no hazard.
     */
    static boolean observeHazard(final Mob creature, final ServerLevel level, final int maxReads) {
        if (creature.isOnFire() || creature.isInLava()) {
            return true;
        }
        if (creature.isUnderWater() && creature.getAirSupply() < creature.getMaxAirSupply()) {
            return true;
        }
        if (!footprintLoaded(level, creature.getBoundingBox().inflate(1.0D))) {
            return false;
        }
        final BlockPos center = creature.blockPosition();
        int reads = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (reads >= maxReads) {
                        return false;
                    }
                    reads++;
                    if (isHazardBlock(level.getBlockState(center.offset(dx, dy, dz)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The honest worst-case read cost of one {@link #qualifySafeCandidate} call: the world-border
     * test, four chunk-presence tests, the block state, the fluid state and the collision sweep.
     * Callers charge this before the candidate can be filtered, so a rejected candidate costs
     * exactly what it actually spent and the charged-read ceiling genuinely bounds the search.
     */
    static final int READS_PER_SAFE_CANDIDATE = 8;

    /** What one qualified candidate turned out to be, so no caller has to read the position twice. */
    record SafeQualification(boolean hazardFree) {
    }

    /**
     * Shared candidate qualification for a bounded safe destination. Only positions whose entire
     * entity footprint is already loaded, inside the world border, collision free, and not lava
     * qualify. Nothing here decides preference: the caller's own species rules do.
     */
    static Optional<SafeQualification> qualifySafeCandidate(
        final Mob creature,
        final ServerLevel level,
        final BlockPos candidate,
        final boolean avoidHazards
    ) {
        final AABB box = creature.getType().getDimensions()
            .makeBoundingBox(Vec3.atBottomCenterOf(candidate));
        if (!level.getWorldBorder().isWithinBounds(box) || !footprintLoaded(level, box)) {
            return Optional.empty();
        }
        final BlockState blockState = level.getBlockState(candidate);
        final var fluidState = level.getFluidState(candidate);
        final boolean hazardous = isHazardBlock(blockState) || !fluidState.isEmpty();
        if (avoidHazards && hazardous) {
            return Optional.empty();
        }
        if (blockState.is(Blocks.LAVA) || fluidState.is(net.minecraft.tags.FluidTags.LAVA)) {
            return Optional.empty();
        }
        return level.noCollision(creature, box)
            ? Optional.of(new SafeQualification(!hazardous))
            : Optional.empty();
    }

    /**
     * Bounded soul-light envelope check. The complete 2r+1 by 2v+1 by 2r+1 box must already be
     * loaded before any read happens, so a scan never forces a chunk and never crosses into an
     * unloaded neighbour.
     */
    static boolean envelopeLoaded(
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRadius,
        final int verticalRadius
    ) {
        return level.hasChunkAt(center.offset(-horizontalRadius, 0, -horizontalRadius))
            && level.hasChunkAt(center.offset(horizontalRadius, 0, -horizontalRadius))
            && level.hasChunkAt(center.offset(-horizontalRadius, 0, horizontalRadius))
            && level.hasChunkAt(center.offset(horizontalRadius, 0, horizontalRadius))
            && verticalRadius >= 0;
    }
}
