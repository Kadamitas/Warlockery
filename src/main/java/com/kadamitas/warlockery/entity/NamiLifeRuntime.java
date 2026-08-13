package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.NamiLifeRules.Activity;
import com.kadamitas.warlockery.entity.NamiLifeRules.Defense;
import com.kadamitas.warlockery.ritual.marriage.MarriageData;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class NamiLifeRuntime {
    private static final double WARD_RANGE_SQUARED = 144.0;
    private static final double ACTIVITY_USE_RANGE_SQUARED = 4.0;
    private static final double HOME_RANGE_SQUARED = 4.0;

    private NamiLifeRuntime() {
    }

    public static void tick(final NamiEntity nami, final ServerLevel level) {
        tick(nami, level, level.getOverworldClockTime());
    }

    static void tick(final NamiEntity nami, final ServerLevel level, final long dayTime) {
        final long now = level.getGameTime();
        NamiLifeState state = nami.lifeState().reconcile(now);
        if (state.home().isEmpty() && level.isLoaded(nami.blockPosition())) {
            state = state.withHome(level.dimension().identifier().toString(), nami.blockPosition());
        }
        nami.setLifeState(state);

        final Optional<ServerPlayer> spouse = spouse(nami, level);
        spouse.ifPresent(player -> CreatureBehaviorState.bind(nami, player.getUUID()));
        if (spouse.filter(player -> regroupIfSeparated(nami, level, player)).isPresent()) {
            return;
        }

        final boolean decisionDue = now >= state.nextDecisionAt()
            && NamiLifeRules.shouldDecide(nami.tickCount, nami.getId());
        final boolean discoveryDue = NamiLifeRules.shouldDiscover(now, state.nextDiscoveryAt());
        final boolean spouseActionStored = !nami.getPersistentData()
            .getStringOr(SpouseAmbientRuntime.ACTION, "").isBlank();
        final boolean spouseRoutine = spouse
            .map(player -> NamiLifeRules.shouldPollSpouseRoutine(nami.tickCount, spouseActionStored)
                ? SpouseAmbientRuntime.tick(nami, level, player)
                : false)
            .orElse(false);

        Optional<LivingEntity> wardTarget = state.wardTarget()
            .map(level::getEntity)
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast);
        final boolean wardActionable = wardTarget.filter(target -> actionableThreat(nami, spouse, target)).isPresent();
        if (state.wardTarget().isPresent() && !wardActionable) {
            state = state.releaseWard();
            nami.setTarget(null);
            nami.setLifeState(state);
            wardTarget = Optional.empty();
        }

        if (decisionDue && wardTarget.isEmpty() && now >= state.wardCooldownUntil()) {
            wardTarget = findThreat(nami, level, spouse);
        }
        final boolean lowHealth = nami.getHealth() / nami.getMaxHealth() <= NamiLifeRules.WITHDRAW_HEALTH_FRACTION;
        final boolean playerAggressor = state.recentAggressor()
            .map(level::getPlayerByUUID)
            .filter(java.util.Objects::nonNull)
            .isPresent();
        final Defense defense = NamiLifeRules.chooseDefense(new NamiLifeRules.DefenseContext(
            lowHealth,
            state.wardTarget().isPresent(),
            playerAggressor,
            wardTarget.isPresent(),
            state.wardTarget().isPresent() && !wardActionable,
            wardTarget.filter(target -> spouse.filter(target::equals).isPresent()).isPresent()
        ));
        if (defense == Defense.WITHDRAW) {
            withdraw(nami, level, spouse, now);
            return;
        }
        if (defense == Defense.WARN) {
            warnAndAvoidAggressor(nami, level, state, now);
            return;
        }
        if (defense == Defense.WARD && wardTarget.isPresent()) {
            ward(nami, level, wardTarget.orElseThrow(), now);
            return;
        }

        if (!decisionDue) {
            execute(nami, level, spouse, now);
            return;
        }
        nami.recordFullDecision();
        state = nami.lifeState();
        final Activity activity = NamiLifeRules.chooseActivity(new NamiLifeRules.ActivityContext(
            dayTime,
            now,
            state.activity(),
            state.activityExpiresAt(),
            targetValid(nami, level, spouse, state),
            false,
            lowHealth,
            wardTarget.isPresent(),
            spouseRoutine,
            level.isRaining() || level.isThundering()
        ));
        final long expiresAt = Math.addExact(now, NamiLifeRules.ACTIVITY_COMMITMENT_TICKS);
        if (spouseRoutine) {
            state = state.begin(Activity.SPOUSE_ROUTINE, expiresAt, Optional.empty(),
                spouse.map(ServerPlayer::getUUID));
        } else if (discoveryDue && needsDiscovery(activity)) {
            nami.recordDiscovery();
            state = discover(nami, level, spouse, state, activity, expiresAt);
        } else if (state.activity() != activity || state.activityExpiresAt() <= now) {
            state = state.begin(activity, expiresAt, Optional.empty(), Optional.empty());
        }
        state = state.withSchedule(
            Math.addExact(now, NamiLifeRules.DECISION_INTERVAL_TICKS),
            discoveryDue ? Math.addExact(now, NamiLifeRules.DISCOVERY_INTERVAL_TICKS) : state.nextDiscoveryAt(),
            state.lastNavigationAt()
        );
        nami.setLifeState(state);
        execute(nami, level, spouse, now);
    }

    public static boolean greet(final NamiEntity nami, final ServerLevel level, final ServerPlayer player) {
        if (!nami.isAlive() || !player.isAlive() || player.isSpectator() || !player.getMainHandItem().isEmpty()) {
            return false;
        }
        final long now = level.getGameTime();
        NamiLifeState state = nami.lifeState().reconcile(now);
        if (now >= state.greetingReadyAt()) {
            state = state.rememberVisitor(
                player.getUUID(),
                Math.addExact(now, NamiLifeRules.VISITOR_MEMORY_TICKS),
                Math.addExact(now, NamiLifeRules.GREETING_COOLDOWN_TICKS)
            );
            nami.setLifeState(state);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, nami.getX(), nami.getEyeY(), nami.getZ(),
                4, 0.25, 0.3, 0.25, 0.03);
            level.playSound(null, nami.blockPosition(), SoundEvents.VILLAGER_AMBIENT,
                SoundSource.NEUTRAL, 0.45F, 1.2F);
        }
        return true;
    }

    public static void recordAggressor(final NamiEntity nami, final ServerLevel level, final Entity attacker) {
        final long now = level.getGameTime();
        if (attacker instanceof ServerPlayer) {
            nami.setLifeState(nami.lifeState().rememberAggressor(
                attacker.getUUID(), Math.addExact(now, NamiLifeRules.AGGRESSOR_MEMORY_TICKS)
            ));
        } else if (attacker instanceof Monster monster && actionableThreat(nami, spouse(nami, level), monster)) {
            nami.setLifeState(nami.lifeState().chargeWard(
                monster.getUUID(),
                Math.addExact(now, NamiLifeRules.WARD_CHARGE_TICKS),
                Math.addExact(now, NamiLifeRules.WARD_COOLDOWN_TICKS)
            ).begin(Activity.WARD, Math.addExact(now, NamiLifeRules.WARD_COOLDOWN_TICKS),
                Optional.empty(), Optional.of(monster.getUUID())));
        }
    }

    public static void interruptForHazard(final NamiEntity nami, final ServerLevel level) {
        final long now = level.getGameTime();
        nami.setTarget(null);
        nami.setLifeState(nami.lifeState().releaseWard().begin(
            Activity.WITHDRAW,
            Math.addExact(now, NamiLifeRules.ACTIVITY_COMMITMENT_TICKS),
            Optional.empty(), Optional.empty()
        ));
    }

    private static NamiLifeState discover(
        final NamiEntity nami,
        final ServerLevel level,
        final Optional<ServerPlayer> spouse,
        final NamiLifeState state,
        final Activity activity,
        final long expiresAt
    ) {
        return switch (activity) {
            case APOTHECARY -> state.begin(activity, expiresAt, findBlock(nami, level, true), Optional.empty());
            case HERB_WALK -> state.begin(activity, expiresAt, findBlock(nami, level, false), Optional.empty());
            case SOCIAL_VISIT -> state.begin(activity, expiresAt, Optional.empty(), findSocial(nami, level, spouse));
            case SHELTER -> state.begin(activity, expiresAt, state.home(), Optional.empty());
            default -> state.begin(activity, expiresAt, Optional.empty(), Optional.empty());
        };
    }

    private static Optional<BlockPos> findBlock(
        final NamiEntity nami,
        final ServerLevel level,
        final boolean apothecary
    ) {
        final BlockPos origin = nami.lifeState().home().orElse(nami.blockPosition());
        int examined = 0;
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (final BlockPos candidate : BlockPos.betweenClosed(origin.offset(-8, -1, -8), origin.offset(8, 2, 8))) {
            if (examined >= NamiLifeRules.MAX_BLOCK_STATES_EXAMINED) {
                break;
            }
            if (!level.isLoaded(candidate)) {
                continue;
            }
            examined++;
            final BlockState block = level.getBlockState(candidate);
            final boolean matches = apothecary
                ? block.is(Blocks.BREWING_STAND) || block.is(Blocks.CAULDRON)
                    || block.is(Blocks.WATER_CAULDRON) || block.is(Blocks.LAVA_CAULDRON)
                : block.is(BlockTags.FLOWERS);
            final double distance = candidate.distSqr(origin);
            if (matches && distance < nearestDistance) {
                nearest = candidate.immutable();
                nearestDistance = distance;
            }
        }
        nami.recordBlockDiscovery(examined);
        return Optional.ofNullable(nearest);
    }

    private static Optional<UUID> findSocial(
        final NamiEntity nami,
        final ServerLevel level,
        final Optional<ServerPlayer> spouse
    ) {
        if (spouse.filter(player -> player.level() == level && player.isAlive()).isPresent()) {
            return spouse.map(ServerPlayer::getUUID);
        }
        final Optional<UUID> visitor = nami.lifeState().welcomedVisitor()
            .map(level::getPlayerByUUID)
            .filter(java.util.Objects::nonNull)
            .filter(Entity::isAlive)
            .map(Entity::getUUID);
        if (visitor.isPresent()) {
            return visitor;
        }
        final java.util.List<LivingEntity> candidates = level.getEntitiesOfClass(
            LivingEntity.class,
            nami.getBoundingBox().inflate(NamiLifeRules.SOCIAL_RADIUS),
            entity -> entity != nami && entity.isAlive() && (entity instanceof Villager || entity instanceof NamiEntity)
        );
        final int appraised = Math.min(candidates.size(), NamiLifeRules.MAX_SOCIAL_CANDIDATES);
        nami.recordSocialCandidates(appraised);
        return candidates.stream().limit(NamiLifeRules.MAX_SOCIAL_CANDIDATES)
            .min(Comparator.comparingDouble(nami::distanceToSqr))
            .map(Entity::getUUID);
    }

    private static Optional<LivingEntity> findThreat(
        final NamiEntity nami,
        final ServerLevel level,
        final Optional<ServerPlayer> spouse
    ) {
        final java.util.List<Monster> candidates = level.getEntitiesOfClass(
            Monster.class,
            nami.getBoundingBox().inflate(Math.sqrt(WARD_RANGE_SQUARED)),
            monster -> actionableThreat(nami, spouse, monster)
        );
        nami.recordThreatCandidates(Math.min(candidates.size(), NamiLifeRules.MAX_SOCIAL_CANDIDATES));
        return candidates.stream().limit(NamiLifeRules.MAX_SOCIAL_CANDIDATES)
            .min(Comparator.comparingDouble(nami::distanceToSqr))
            .map(LivingEntity.class::cast);
    }

    private static boolean actionableThreat(
        final NamiEntity nami,
        final Optional<ServerPlayer> spouse,
        final LivingEntity target
    ) {
        if (!(target instanceof Monster monster) || !target.isAlive()
            || nami.distanceToSqr(target) > WARD_RANGE_SQUARED || !nami.canAttack(target)) {
            return false;
        }
        final LivingEntity attacked = monster.getTarget();
        return attacked == nami || spouse.filter(player -> player == attacked).isPresent();
    }

    private static void ward(
        final NamiEntity nami,
        final ServerLevel level,
        final LivingEntity target,
        final long now
    ) {
        NamiLifeState state = nami.lifeState();
        if (state.wardTarget().isEmpty()) {
            state = state.chargeWard(
                target.getUUID(),
                Math.addExact(now, NamiLifeRules.WARD_CHARGE_TICKS),
                Math.addExact(now, NamiLifeRules.WARD_COOLDOWN_TICKS)
            ).begin(Activity.WARD, Math.addExact(now, NamiLifeRules.WARD_COOLDOWN_TICKS),
                Optional.empty(), Optional.of(target.getUUID()));
            nami.setLifeState(state);
        }
        nami.setTarget(target);
        nami.lookAt(target, 45.0F, 45.0F);
        if (now < state.wardChargeReadyAt()) {
            level.sendParticles(ParticleTypes.ENCHANT, nami.getX(), nami.getEyeY(), nami.getZ(),
                2, 0.25, 0.25, 0.25, 0.01);
            return;
        }
        target.hurtServer(level, level.damageSources().indirectMagic(nami, nami), 6.0F);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getEyeY(), target.getZ(),
            18, 0.35, 0.45, 0.35, 0.08);
        level.playSound(null, nami.blockPosition(), SoundEvents.EVOKER_CAST_SPELL,
            SoundSource.NEUTRAL, 0.8F, 1.15F);
        nami.setTarget(null);
        nami.setLifeState(state.releaseWard());
    }

    private static void warnAndAvoidAggressor(
        final NamiEntity nami,
        final ServerLevel level,
        final NamiLifeState state,
        final long now
    ) {
        state.recentAggressor().map(level::getPlayerByUUID).filter(java.util.Objects::nonNull).ifPresent(player -> {
            final Vec3 away = nami.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() > 1.0E-4 && requestNavigation(nami, now)) {
                final Vec3 destination = nami.position().add(away.normalize().scale(6.0));
                navigate(nami, destination.x, destination.y, destination.z, 1.2, now);
            }
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, nami.getX(), nami.getEyeY(), nami.getZ(),
                2, 0.2, 0.25, 0.2, 0.0);
        });
    }

    private static void withdraw(
        final NamiEntity nami,
        final ServerLevel level,
        final Optional<ServerPlayer> spouse,
        final long now
    ) {
        nami.setTarget(null);
        final Optional<BlockPos> destination = loadedHome(nami, level)
            .or(() -> spouse.filter(player -> player.level() == level).map(Entity::blockPosition));
        nami.setLifeState(nami.lifeState().releaseWard().begin(
            Activity.WITHDRAW,
            Math.addExact(now, NamiLifeRules.ACTIVITY_COMMITMENT_TICKS),
            destination, Optional.empty()
        ));
        destination.ifPresent(position -> moveTo(nami, position, 1.25, now));
    }

    private static void execute(
        final NamiEntity nami,
        final ServerLevel level,
        final Optional<ServerPlayer> spouse,
        final long now
    ) {
        final NamiLifeState state = nami.lifeState();
        if (state.activity() == Activity.SPOUSE_ROUTINE || state.activity() == Activity.WARD
            || state.activity() == Activity.WITHDRAW || state.activity() == Activity.IDLE) {
            return;
        }
        if (state.activityEntity().isPresent()) {
            final Entity target = level.getEntity(state.activityEntity().orElseThrow());
            if (!(target instanceof LivingEntity living) || !living.isAlive()) {
                nami.setLifeState(state.begin(Activity.IDLE, now, Optional.empty(), Optional.empty()));
                nami.getNavigation().stop();
                return;
            }
            if (nami.distanceToSqr(target) > ACTIVITY_USE_RANGE_SQUARED) {
                moveTo(nami, target.blockPosition(), 0.9, now);
            } else {
                nami.getNavigation().stop();
                nami.lookAt(living, 35.0F, 35.0F);
            }
            return;
        }
        final Optional<BlockPos> target = state.activityBlock()
            .or(() -> state.activity() == Activity.SHELTER ? loadedHome(nami, level) : Optional.empty());
        if (target.isEmpty() || !level.isLoaded(target.orElseThrow())) {
            return;
        }
        final BlockPos position = target.orElseThrow();
        final double range = state.activity() == Activity.SHELTER ? HOME_RANGE_SQUARED : ACTIVITY_USE_RANGE_SQUARED;
        if (position.distSqr(nami.blockPosition()) > range) {
            moveTo(nami, position, state.activity() == Activity.SHELTER ? 1.0 : 0.85, now);
            return;
        }
        nami.getNavigation().stop();
        nami.getLookControl().setLookAt(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
        if (nami.tickCount % 40 == 0) {
            final net.minecraft.core.particles.ParticleOptions particle = state.activity() == Activity.SHELTER
                ? ParticleTypes.ENCHANT : ParticleTypes.HAPPY_VILLAGER;
            level.sendParticles(particle, nami.getX(), nami.getEyeY(), nami.getZ(),
                2, 0.2, 0.2, 0.2, 0.01);
        }
    }

    private static void moveTo(
        final NamiEntity nami,
        final BlockPos position,
        final double speed,
        final long now
    ) {
        if (!requestNavigation(nami, now)) {
            return;
        }
        navigate(nami, position.getX() + 0.5, position.getY(), position.getZ() + 0.5, speed, now);
    }

    private static boolean requestNavigation(final NamiEntity nami, final long now) {
        final NamiLifeState state = nami.lifeState();
        return NamiLifeRules.mayRequestNavigation(
            now, state.lastNavigationAt(), state.routeFailures(), state.retryAfter()
        );
    }

    private static void navigate(
        final NamiEntity nami,
        final double x,
        final double y,
        final double z,
        final double speed,
        final long now
    ) {
        nami.recordNavigationRequest();
        final boolean accepted = nami.getNavigation().moveTo(x, y, z, speed);
        final int failures = accepted ? 0 : nami.lifeState().routeFailures() + 1;
        final long retryAfter = NamiLifeRules.retryAfterFailure(now, failures);
        nami.setLifeState(nami.lifeState()
            .withRouteFailure(failures, retryAfter)
            .withSchedule(nami.lifeState().nextDecisionAt(), nami.lifeState().nextDiscoveryAt(), now));
    }

    private static Optional<BlockPos> loadedHome(final NamiEntity nami, final ServerLevel level) {
        final NamiLifeState state = nami.lifeState();
        if (state.homeDimension().filter(level.dimension().identifier().toString()::equals).isEmpty()) {
            return Optional.empty();
        }
        return state.home().filter(level::isLoaded);
    }

    private static boolean targetValid(
        final NamiEntity nami,
        final ServerLevel level,
        final Optional<ServerPlayer> spouse,
        final NamiLifeState state
    ) {
        if (state.activityEntity().isPresent()) {
            return Optional.ofNullable(level.getEntity(state.activityEntity().orElseThrow()))
                .filter(Entity::isAlive).isPresent();
        }
        if (state.activityBlock().isPresent()) {
            return level.isLoaded(state.activityBlock().orElseThrow());
        }
        return state.activity() == Activity.IDLE || state.activity() == Activity.SHELTER
            || state.activity() == Activity.SPOUSE_ROUTINE && spouse.isPresent();
    }

    private static boolean needsDiscovery(final Activity activity) {
        return activity == Activity.APOTHECARY || activity == Activity.HERB_WALK
            || activity == Activity.SOCIAL_VISIT || activity == Activity.SHELTER;
    }

    private static Optional<ServerPlayer> spouse(final NamiEntity nami, final ServerLevel level) {
        return MarriageData.get(level).ownerForNami(nami.getUUID())
            .map(level.getServer().getPlayerList()::getPlayer)
            .filter(java.util.Objects::nonNull);
    }

    private static boolean regroupIfSeparated(
        final NamiEntity nami,
        final ServerLevel level,
        final ServerPlayer spouse
    ) {
        if (spouse.level() != level || nami.distanceToSqr(spouse) > 1_024.0) {
            nami.follow(spouse);
            return true;
        }
        if (nami.distanceToSqr(spouse) > 81.0 && nami.lifeState().activity() == Activity.SHELTER) {
            nami.follow(spouse);
            return true;
        }
        return false;
    }

    public record Counters(
        long fullDecisions,
        long targetDiscoveries,
        long blockStatesExamined,
        int maximumBlockStatesPerDiscovery,
        long socialCandidatesAppraised,
        long threatCandidatesAppraised,
        long navigationRequests
    ) {
    }
}
