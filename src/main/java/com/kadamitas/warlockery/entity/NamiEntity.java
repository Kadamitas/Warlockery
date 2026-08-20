package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.ritual.marriage.MarriageData;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class NamiEntity extends PathfinderMob {
    private static final double FOLLOW_DISTANCE = 9.0;
    private static final double TELEPORT_DISTANCE = 1024.0;
    private final MoveControl walkingControl;
    private final MoveControl swimmingControl;
    private NamiLifeState lifeState = NamiLifeState.empty();
    private long fullDecisions;
    private long targetDiscoveries;
    private long blockStatesExamined;
    private int maximumBlockStatesPerDiscovery;
    private long socialCandidatesAppraised;
    private long threatCandidatesAppraised;
    private long navigationRequests;

    public NamiEntity(final EntityType<? extends PathfinderMob> type, final Level level) {
        super(type, level);
        // Nami is led to a drowned monument to become Naamah, so following her spouse underwater
        // is the whole journey, not a corner case. Water carries no pathfinding penalty, and she
        // swims with the control the aquatic mobs use only while she is actually in water: that
        // control also governs walking, and driving her overland with it makes her stop short of
        // a destination she is supposed to arrive at.
        setPathfindingMalus(PathType.WATER, 0.0F);
        walkingControl = moveControl;
        swimmingControl = new SmoothSwimmingMoveControl<>(this, 85, 10, 0.02F, 0.1F, true);
        setCustomName(Component.translatable("entity.warlockery.nami"));
        setCustomNameVisible(true);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        // No FloatGoal: it forces a mob to bob at the surface, which would strand her above a
        // spouse who has swum down. No WaterAvoidingRandomStrollGoal either, for the same reason
        // in reverse; she must be willing to idle in water she is meant to live in.
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        moveControl = isInWater() ? swimmingControl : walkingControl;
        super.customServerAiStep(level);
        if (HazardEscapeRuntime.tick(this, level)) {
            NamiLifeRuntime.interruptForHazard(this, level);
            return;
        }
        NamiLifeRuntime.tick(this, level);
    }

    @Override
    protected PathNavigation createNavigation(final Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    /** She is a creature of the water; drowning on the way to her own transformation is absurd. */
    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    /** A current must not shove her out of a monument corridor she is trying to hold station in. */
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return !(level() instanceof ServerLevel serverLevel
            && spouse(serverLevel).filter(player -> player == target).isPresent())
            && super.canAttack(target);
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        if (amount >= getHealth() && rescueAtSpouseBed(level)) {
            return true;
        }
        final boolean hurt = super.hurtServer(level, source, amount);
        final Entity attacker = source.getEntity();
        if (hurt && attacker != null) {
            NamiLifeRuntime.recordAggressor(this, level, attacker);
        }
        return hurt;
    }

    @Override
    public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer
            && NamiLifeRuntime.greet(this, (ServerLevel) serverPlayer.level(), serverPlayer)) {
            return InteractionResult.SUCCESS_SERVER;
        }
        return super.mobInteract(player, hand);
    }

    public void acceptMarriage(final ServerPlayer player, final String spouseName) {
        CreatureBehaviorState.bind(this, player.getUUID());
        setCustomName(Component.literal(spouseName));
        setCustomNameVisible(true);
        setPersistenceRequired();
    }

    public void divorce() {
        if (level() instanceof ServerLevel level) {
            spouse(level).ifPresent(player -> SpouseAmbientRuntime.abort(this, level, player));
        }
        CreatureBehaviorState.unbind(this);
        setCustomName(Component.translatable("entity.warlockery.nami"));
        setTarget(null);
        getNavigation().stop();
    }

    private Optional<ServerPlayer> spouse(final ServerLevel level) {
        return MarriageData.get(level).ownerForNami(getUUID())
            .map(level.getServer().getPlayerList()::getPlayer);
    }

    void follow(final ServerPlayer player) {
        if (player.level() != level()) {
            teleportTo(
                (ServerLevel) player.level(),
                player.getX() + 1.0,
                player.getY(),
                player.getZ() + 1.0,
                Set.<Relative>of(),
                getYRot(),
                getXRot(),
                false
            );
            return;
        }
        final double distance = distanceToSqr(player);
        if (distance > TELEPORT_DISTANCE) {
            teleportTo(player.getX() + 1.0, player.getY(), player.getZ() + 1.0);
        } else if (distance > FOLLOW_DISTANCE) {
            getNavigation().moveTo(player, 1.2);
        } else {
            getNavigation().stop();
        }
    }

    private boolean rescueAtSpouseBed(final ServerLevel currentLevel) {
        final Optional<ServerPlayer> spouse = spouse(currentLevel);
        if (spouse.isEmpty()) {
            return false;
        }
        final ServerPlayer player = spouse.orElseThrow();
        final ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
        final ServerLevel destination;
        final BlockPos position;
        if (respawn == null) {
            destination = currentLevel.getServer().overworld();
            position = destination.getRespawnData().pos();
        } else {
            destination = currentLevel.getServer().getLevel(respawn.respawnData().dimension());
            if (destination == null) {
                return false;
            }
            position = respawn.respawnData().pos();
        }
        setHealth(1.0F);
        clearFire();
        setTarget(null);
        teleportTo(
            destination,
            position.getX() + 0.5,
            position.getY() + 1.0,
            position.getZ() + 0.5,
            Set.<Relative>of(),
            getYRot(),
            getXRot(),
            false
        );
        return true;
    }

    NamiLifeState lifeState() {
        return lifeState;
    }

    void setLifeState(final NamiLifeState state) {
        lifeState = state;
    }

    NamiLifeRuntime.Counters lifeCounters() {
        return new NamiLifeRuntime.Counters(
            fullDecisions,
            targetDiscoveries,
            blockStatesExamined,
            maximumBlockStatesPerDiscovery,
            socialCandidatesAppraised,
            threatCandidatesAppraised,
            navigationRequests
        );
    }

    void recordFullDecision() {
        fullDecisions++;
    }

    void recordDiscovery() {
        targetDiscoveries++;
    }

    void recordBlockDiscovery(final int examined) {
        blockStatesExamined += examined;
        maximumBlockStatesPerDiscovery = Math.max(maximumBlockStatesPerDiscovery, examined);
    }

    void recordSocialCandidates(final int appraised) {
        socialCandidatesAppraised += appraised;
    }

    void recordThreatCandidates(final int appraised) {
        threatCandidatesAppraised += appraised;
    }

    void recordNavigationRequest() {
        navigationRequests++;
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("WarlockeryNamiLife", CompoundTag.CODEC, lifeState.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        lifeState = input.read("WarlockeryNamiLife", CompoundTag.CODEC)
            .map(tag -> NamiLifeState.read(tag, level().getGameTime()))
            .orElse(NamiLifeState.empty());
    }
}
