package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.magic.MagicPathState;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.util.DataParsing;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The sole F17 server controller and the only F17 navigation writer. Runs from
 * {@link CorpseEntity#customServerAiStep} every loaded server AI tick and owns the
 * complete decision pipeline; no goal, generic runtime, or external caller may
 * write Corpse navigation.
 */
public final class CorpseRuntime {
    private static final String GRAVE_OWNER_KEY = "WarlockeryGraveOwner";
    private static final String GRAVE_EXPIRATION_KEY = "WarlockeryGraveExpiration";
    private static final TagKey<net.minecraft.world.level.block.Block> CONTACT_HAZARDS = TagKey.create(
        Registries.BLOCK,
        Identifier.fromNamespaceAndPath("warlockery", "ai/contact_hazards")
    );
    private static final int[] ESCAPE_STEPS = {-3, -2, 2, 3};
    private static final Map<ServerLevel, Quota> QUOTAS = new WeakHashMap<>();

    private CorpseRuntime() {
    }

    // ----------------------------------------------------------------- quotas

    /** Primitive-only per-level record keyed by {@code MinecraftServer#getTickCount}. */
    private static final class Quota {
        private long serverTick = Long.MIN_VALUE;
        private final int[] used = new int[CorpseRules.Work.values().length];
    }

    static boolean charge(final ServerLevel level, final CorpseRules.Work work, final int amount) {
        if (!level.getServer().isSameThread()) {
            return false;
        }
        final Quota quota = QUOTAS.computeIfAbsent(level, ignored -> new Quota());
        final long serverTick = level.getServer().getTickCount();
        if (CorpseRules.quotaExpired(serverTick, quota.serverTick)) {
            quota.serverTick = serverTick;
            Arrays.fill(quota.used, 0);
        }
        if (!CorpseRules.mayCharge(quota.used[work.ordinal()], amount, work)) {
            return false;
        }
        quota.used[work.ordinal()] += amount;
        return true;
    }

    /** Grave world-command scan token for the MagicPathRuntime adapter. */
    public static boolean takeGraveScanToken(final ServerLevel level) {
        return charge(level, CorpseRules.Work.GRAVE_SCAN, 1);
    }

    /** Grave directive delivery token for the MagicPathRuntime adapter. */
    public static boolean takeGraveDirectiveToken(final ServerLevel level) {
        return charge(level, CorpseRules.Work.GRAVE_DIRECTIVE, 1);
    }

    // ------------------------------------------------------- transient facts

    /** Fixed-cardinality transient execution facts; never serialized. */
    public static final class Transient {
        CorpseRules.Activity activity = CorpseRules.Activity.IDLE;
        UUID targetId;
        int lostSightTicks;
        long lastLosTick = Long.MIN_VALUE;
        boolean lastLosResult = true;
        UUID attackerId;
        long attackerUntil;
        CorpseRules.ItemToken foodToken;
        long foodBackoffUntil;
        Vec3 graveDestination;
        int clutchWindup = -1;
        long recoveryUntil;
        CorpseRules.Route route = CorpseRules.Route.fresh();
        long lastPathTick = Long.MIN_VALUE;
        long lastHazardObservationTick = Long.MIN_VALUE;
        boolean contactHazardCached;
        boolean fireBlockCached;
        long hazardBackoffUntil;
        long lastOwnerResolveTick = Long.MIN_VALUE;
        UUID raiseOwnerId;
        UUID graveOwnerId;
        long graveExpiry;
        boolean graveAuthorityActive;
        long lastItemScanTick = Long.MIN_VALUE;
        boolean reconciled;

        public CorpseRules.Activity activity() {
            return activity;
        }

        public Optional<Vec3> graveDestination() {
            return Optional.ofNullable(graveDestination);
        }

        public void clearAll() {
            activity = CorpseRules.Activity.IDLE;
            targetId = null;
            lostSightTicks = 0;
            lastLosTick = Long.MIN_VALUE;
            lastLosResult = true;
            attackerId = null;
            attackerUntil = 0L;
            foodToken = null;
            foodBackoffUntil = 0L;
            graveDestination = null;
            clutchWindup = -1;
            recoveryUntil = 0L;
            route = CorpseRules.Route.fresh();
            lastPathTick = Long.MIN_VALUE;
            lastHazardObservationTick = Long.MIN_VALUE;
            contactHazardCached = false;
            fireBlockCached = false;
            hazardBackoffUntil = 0L;
            lastOwnerResolveTick = Long.MIN_VALUE;
            raiseOwnerId = null;
            graveOwnerId = null;
            graveExpiry = 0L;
            graveAuthorityActive = false;
            lastItemScanTick = Long.MIN_VALUE;
            reconciled = false;
        }
    }

    /** Resettable bounded structural counters; never saved, synced, or gameplay-effective. */
    public static final class Counters {
        public int corpseAiTicks;
        public int cohesionUpdates;
        public int cohesionDecrements;
        public int dormancyEntries;
        public int wakes;
        public int expensiveTokensDeferred;
        public int itemQueries;
        public int itemCandidatesVisited;
        public int foodTargetsBound;
        public int foodTargetsReleased;
        public int autonomousMutationsAccepted;
        public int autonomousMutationsDeferred;
        public int itemsConsumed;
        public int ownerResolves;
        public int graveBindNotifications;
        public int graveDirectivesReceived;
        public int graveExpiries;
        public int targetAcquisitions;
        public int targetReleases;
        public int losRays;
        public int directAttackerWrites;
        public int clutchesStarted;
        public int clutchesCancelled;
        public int attackAttempts;
        public int attacksAccepted;
        public int attacksRejected;
        public int slownessApplications;
        public int hazardObservationReads;
        public int safeCandidates;
        public int safeReads;
        public int safeEntityVisits;
        public int hazardRoutes;
        public int pathRequests;
        public int pathAccepted;
        public int pathFailures;
        public int pathBackoffs;
        public int feedbackEmitted;
        public int feedbackSuppressed;
        public int genericBehaviorDispatches;
        public int reinforcements;
        public int villagerConversions;
        public int drownedConversions;
        public int doorBreaks;
        public int blockEdits;
        public int chunkLoadRequests;
    }

    // ------------------------------------------------------------ entry gates

    /** Accepted effective damage attribution; called only from {@link CorpseEntity#hurtServer}. */
    public static void recordAcceptedDamage(
        final CorpseEntity body,
        final ServerLevel level,
        final DamageSource source
    ) {
        final Transient facts = body.transientFacts();
        final boolean protectedAttacker = source.getEntity() != null
            && protectedIdentity(body, source.getEntity().getUUID());
        wake(body, level, protectedAttacker ? "owner_damage" : "damage");
        if (protectedAttacker) {
            return;
        }
        if (source.getEntity() instanceof LivingEntity attacker
            && attacker.level() == level
            && legalTarget(body, attacker)) {
            facts.attackerId = attacker.getUUID();
            facts.attackerUntil = body.tickCount + CorpseRules.DIRECT_ATTACKER_TICKS;
            body.corpseCounters().directAttackerWrites++;
        }
    }

    /** Final absolute relation gate; called from {@link CorpseEntity#canAttack}. */
    public static boolean legalTarget(final CorpseEntity body, final LivingEntity target) {
        final CorpseRules.TargetLegality legality = CorpseRules.TargetLegality.of(true)
            .withSelf(target == body)
            .withDead(!target.isAlive())
            .withInvulnerable(target.isInvulnerable())
            .withCrossLevel(target.level() != body.level())
            .withProtectedOwner(protectedIdentity(body, target.getUUID()))
            .withCorpse(target instanceof CorpseEntity)
            .withCreativeOrSpectator(target instanceof Player player
                && (player.isCreative() || player.isSpectator()))
            .withGarbed(target instanceof Player player
                && player.getItemBySlot(EquipmentSlot.CHEST).is(WarlockeryTags.Items.NECROMANCER_GARB));
        return CorpseRules.targetLegal(legality);
    }

    /** Owner manual feed; called only from {@link CorpseEntity#mobInteract}. */
    public static InteractionResult manualFeed(
        final CorpseEntity body,
        final Player player,
        final InteractionHand hand
    ) {
        final ItemStack offered = player.getItemInHand(hand);
        if (!offered.is(Items.ROTTEN_FLESH)) {
            return InteractionResult.PASS;
        }
        if (!(body.level() instanceof ServerLevel level)) {
            return InteractionResult.CONSUME;
        }
        final CorpseRules.OwnerFacts facts = ownerFacts(body, level);
        final boolean accepted = CorpseRules.manualFeedAccepted(
            facts,
            player.getUUID(),
            level.getGameTime(),
            body.getHealth() < body.getMaxHealth(),
            body.corpseState().cohesion()
        );
        if (!accepted) {
            return InteractionResult.PASS;
        }
        if (!player.hasInfiniteMaterials()) {
            offered.shrink(1);
        }
        applyFeedResult(body, level, false);
        return InteractionResult.SUCCESS;
    }

    /** Grave bind notification; called from the MagicPathRuntime adapter. */
    public static void notifyGraveBind(final CorpseEntity body, final ServerLevel level) {
        body.corpseCounters().graveBindNotifications++;
        body.transientFacts().lastOwnerResolveTick = Long.MIN_VALUE;
        wake(body, level, "grave_bind");
    }

    /** One typed Grave world-command directive; called from the MagicPathRuntime adapter. */
    public static void deliverGraveDirective(
        final CorpseEntity body,
        final ServerLevel level,
        final BlockPos position
    ) {
        final Transient facts = body.transientFacts();
        body.setTarget(null);
        facts.targetId = null;
        cancelClutch(body, facts);
        facts.graveDestination = new Vec3(
            position.getX() + 0.5D,
            position.getY() + 1.0D,
            position.getZ() + 0.5D
        );
        body.corpseCounters().graveDirectivesReceived++;
        wake(body, level, "grave_command");
    }

    // ------------------------------------------------------------- main tick

    public static void tick(final CorpseEntity body, final ServerLevel level) {
        if (!body.isAlive() || !level.getServer().isSameThread()) {
            return;
        }
        final Transient facts = body.transientFacts();
        final Counters counters = body.corpseCounters();
        counters.corpseAiTicks++;
        final int now = body.tickCount;

        if (!facts.reconciled) {
            facts.reconciled = true;
            body.normalizeLifecycle();
            reconcileExpiredGraveKeysOnce(body, level);
            stopNavigation(body);
            body.synchronizeDormant(body.corpseState().dormant());
        }

        tickClocks(body, level, facts, counters);
        if (facts.lastOwnerResolveTick == Long.MIN_VALUE
            || now - facts.lastOwnerResolveTick >= CorpseRules.OWNER_RESOLVE_INTERVAL_TICKS) {
            facts.lastOwnerResolveTick = now;
            resolveOwners(body, level, facts, counters);
        }
        if (facts.attackerId != null && now > facts.attackerUntil) {
            facts.attackerId = null;
        }

        final CorpseRules.Hazard hazard = observeHazard(body, level, facts, counters, now);
        if (hazard != CorpseRules.Hazard.NONE) {
            if (body.corpseState().dormant()) {
                wake(body, level, "hazard");
            }
            enterActivity(body, facts, CorpseRules.Activity.HAZARD);
            escapeHazard(body, level, facts, counters, now);
            return;
        }

        if (body.corpseState().dormant()) {
            enterActivity(body, facts, CorpseRules.Activity.DORMANT);
            dormantArrivalMeal(body, level, facts, counters, now);
            return;
        }

        if (now < facts.recoveryUntil) {
            enterActivity(body, facts, CorpseRules.Activity.RECOVERY);
            return;
        }

        if (facts.graveDestination != null && !facts.graveAuthorityActive) {
            facts.graveDestination = null;
        }
        final boolean graveCommand = facts.graveDestination != null;

        final LivingEntity target = selectTarget(body, level, facts, counters, now, graveCommand);
        if (target != null) {
            enterActivity(body, facts, CorpseRules.Activity.COMBAT);
            driveClutch(body, level, facts, counters, target, now);
            return;
        }

        if (graveCommand) {
            enterActivity(body, facts, CorpseRules.Activity.GRAVE_COMMAND);
            driveGraveCommand(body, level, facts, counters, now);
            return;
        }

        if (CorpseRules.mayScavenge(
            body.corpseState().cohesion(),
            body.corpseState().groundMealCooldown(),
            false
        ) && now >= facts.foodBackoffUntil) {
            final ItemEntity meal = acquireScavengeTarget(body, level, facts, counters, now);
            if (meal != null) {
                enterActivity(body, facts, CorpseRules.Activity.SCAVENGE);
                if (driveScavenge(body, level, facts, counters, meal, now)) {
                    return;
                }
            }
        }

        final LivingEntity followedOwner = followSubject(body, level, facts);
        if (followedOwner != null) {
            enterActivity(body, facts, CorpseRules.Activity.FOLLOW);
            driveFollow(body, level, facts, counters, followedOwner, now);
            return;
        }

        enterActivity(body, facts, CorpseRules.Activity.IDLE);
    }

    // ------------------------------------------------------------ components

    private static void tickClocks(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts,
        final Counters counters
    ) {
        CorpseState state = body.corpseState();
        if (state.groundMealCooldown() > 0) {
            state = state.withCooldown(CorpseRules.cooldownTick(state.groundMealCooldown()));
        }
        if (state.dormant()) {
            body.setCorpseState(state);
            if (!body.isDormant()) {
                enterDormancy(body, level, facts, counters);
            }
            return;
        }
        final CorpseRules.Decay decay = CorpseRules.decay(state.cohesion(), state.decayRemainder());
        counters.cohesionUpdates++;
        if (decay.decremented()) {
            counters.cohesionDecrements++;
        }
        state = state.withDecay(decay);
        if (state.dormant()) {
            body.setCorpseState(state);
            enterDormancy(body, level, facts, counters);
            return;
        }
        body.setCorpseState(state);
    }

    private static void enterDormancy(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts,
        final Counters counters
    ) {
        counters.dormancyEntries++;
        body.setTarget(null);
        facts.targetId = null;
        facts.foodToken = null;
        facts.graveDestination = null;
        cancelClutch(body, facts);
        stopNavigation(body);
        body.synchronizeDormant(true);
        feedback(body, level, SoundEvents.ZOMBIE_AMBIENT, ParticleTypes.ASH, 8);
    }

    private static void wake(final CorpseEntity body, final ServerLevel level, final String reason) {
        final CorpseState state = body.corpseState();
        if (!state.dormant()) {
            return;
        }
        body.setCorpseState(state.withCohesion(CorpseRules.woken(state.cohesion())));
        body.synchronizeDormant(false);
        body.corpseCounters().wakes++;
        feedback(body, level, SoundEvents.ZOMBIE_AMBIENT, ParticleTypes.SOUL, 8);
    }

    private static void reconcileExpiredGraveKeysOnce(final CorpseEntity body, final ServerLevel level) {
        final long expiry = body.getPersistentData().getLongOr(GRAVE_EXPIRATION_KEY, 0L);
        if (!body.getPersistentData().getStringOr(GRAVE_OWNER_KEY, "").isEmpty()
            && level.getGameTime() >= expiry) {
            clearGraveKeys(body);
            body.corpseCounters().graveExpiries++;
        }
    }

    private static void clearGraveKeys(final CorpseEntity body) {
        body.getPersistentData().remove(GRAVE_OWNER_KEY);
        body.getPersistentData().remove(GRAVE_EXPIRATION_KEY);
        body.transientFacts().graveDestination = null;
        body.transientFacts().graveOwnerId = null;
        body.transientFacts().graveAuthorityActive = false;
    }

    private static void resolveOwners(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts,
        final Counters counters
    ) {
        counters.ownerResolves++;
        final CorpseRules.OwnerFacts owner = ownerFacts(body, level);
        facts.raiseOwnerId = owner.raiseOwner().orElse(null);
        facts.graveOwnerId = owner.graveOwner().orElse(null);
        facts.graveExpiry = owner.graveExpiry();
        facts.graveAuthorityActive = false;
        if (owner.graveOwner().isEmpty()) {
            return;
        }
        final long gameTime = level.getGameTime();
        if (!CorpseRules.graveKeysUnexpired(owner, gameTime)) {
            clearGraveKeys(body);
            counters.graveExpiries++;
            return;
        }
        if (owner.graveOwnerLoaded() && !owner.graveOwnerHasPath()) {
            clearGraveKeys(body);
            counters.graveExpiries++;
            return;
        }
        facts.graveAuthorityActive = CorpseRules.graveAuthorityActive(owner, gameTime);
    }

    private static CorpseRules.OwnerFacts ownerFacts(final CorpseEntity body, final ServerLevel level) {
        final Optional<UUID> raise = CreatureBehaviorState.owner(body);
        final Optional<UUID> grave = DataParsing.uuid(
            body.getPersistentData().getStringOr(GRAVE_OWNER_KEY, "")
        );
        final long expiry = body.getPersistentData().getLongOr(GRAVE_EXPIRATION_KEY, 0L);
        final ServerPlayer owner = grave
            .map(id -> level.getServer().getPlayerList().getPlayer(id))
            .orElse(null);
        final boolean loaded = owner != null && owner.level() == level;
        final boolean hasPath = loaded && MagicPathState.has(owner, MagicPath.GRAVE);
        return new CorpseRules.OwnerFacts(raise, grave, expiry, loaded, hasPath);
    }

    private static boolean protectedIdentity(final CorpseEntity body, final UUID candidate) {
        final CorpseRules.OwnerFacts identities = new CorpseRules.OwnerFacts(
            CreatureBehaviorState.owner(body),
            DataParsing.uuid(body.getPersistentData().getStringOr(GRAVE_OWNER_KEY, "")),
            0L,
            false,
            false
        );
        return CorpseRules.protectedIdentity(identities, candidate);
    }

    // --------------------------------------------------------------- hazard

    private static CorpseRules.Hazard observeHazard(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts,
        final Counters counters,
        final int now
    ) {
        if (CorpseRules.hazardObservationDue(facts.lastHazardObservationTick, now)
            && charge(level, CorpseRules.Work.CHARGED_READ, CorpseRules.HAZARD_OBSERVATION_READS)) {
            facts.lastHazardObservationTick = now;
            boolean contact = false;
            boolean fireBlock = false;
            int reads = 0;
            final AABB box = body.getBoundingBox();
            for (final BlockPos position : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY - 1.0D, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ)
            )) {
                if (reads >= CorpseRules.HAZARD_OBSERVATION_READS) {
                    break;
                }
                reads++;
                counters.hazardObservationReads++;
                final BlockState state = level.getBlockState(position);
                contact |= state.is(CONTACT_HAZARDS);
                fireBlock |= state.is(BlockTags.FIRE);
            }
            facts.contactHazardCached = contact;
            facts.fireBlockCached = fireBlock;
        }
        return CorpseRules.hazard(
            body.isOnFire() || facts.fireBlockCached,
            body.isInLava(),
            facts.contactHazardCached
        );
    }

    private static void escapeHazard(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts,
        final Counters counters,
        final int now
    ) {
        if (now < facts.hazardBackoffUntil
            || !CorpseRules.pathDue(facts.lastPathTick, now)
            || !CorpseRules.routeAllowed(facts.route, now)) {
            return;
        }
        if (!charge(level, CorpseRules.Work.EXPENSIVE, 1)) {
            counters.expensiveTokensDeferred++;
            return;
        }
        int searchReads = 0;
        int searchVisits = 0;
        BlockPos chosen = null;
        for (final int dx : ESCAPE_STEPS) {
            for (final int dz : ESCAPE_STEPS) {
                if (chosen != null) {
                    break;
                }
                counters.safeCandidates++;
                final int candidateReads = 3;
                final int candidateVisits = CorpseRules.SAFE_ENTITY_VISITS_PER_CANDIDATE;
                if (!CorpseRules.safeSearchAffordable(searchReads, searchVisits, candidateReads, candidateVisits)
                    || !charge(level, CorpseRules.Work.CHARGED_READ, candidateReads)) {
                    break;
                }
                searchReads += candidateReads;
                final BlockPos candidate = body.blockPosition().offset(dx, 0, dz);
                if (!level.getWorldBorder().isWithinBounds(candidate)) {
                    continue;
                }
                final AABB moved = body.getBoundingBox().move(
                    candidate.getX() + 0.5D - body.getX(),
                    candidate.getY() - body.getY(),
                    candidate.getZ() + 0.5D - body.getZ()
                );
                final HaloReadCache collisionReads = new HaloReadCache(
                    level, moved.inflate(1.0D), candidateReads);
                if (!collisionReads.haloLoaded()) {
                    continue;
                }
                final BlockState below = collisionReads.getBlockState(candidate.below());
                final BlockState feet = collisionReads.getBlockState(candidate);
                final BlockState head = collisionReads.getBlockState(candidate.above());
                final boolean standable = below.isFaceSturdy(collisionReads, candidate.below(),
                        net.minecraft.core.Direction.UP)
                    && feet.getCollisionShape(collisionReads, candidate).isEmpty()
                    && head.getCollisionShape(collisionReads, candidate.above()).isEmpty()
                    && !feet.is(CONTACT_HAZARDS)
                    && !feet.is(BlockTags.FIRE)
                    && !feet.getFluidState().is(FluidTags.LAVA)
                    && collisionReads.withinContract();
                counters.safeReads += collisionReads.actualReads();
                if (!standable) {
                    continue;
                }
                final int[] visited = {0};
                final boolean[] occupied = {false};
                if (charge(level, CorpseRules.Work.SAFE_ENTITY_VISIT, candidateVisits)) {
                    level.getEntities().get(EntityTypeTest.forClass(Entity.class), moved, entity -> {
                        visited[0]++;
                        counters.safeEntityVisits++;
                        if (entity != body && entity.canBeCollidedWith(body)) {
                            occupied[0] = true;
                            return AbortableIterationConsumer.Continuation.ABORT;
                        }
                        return visited[0] >= CorpseRules.SAFE_ENTITY_VISITS_PER_CANDIDATE
                            ? AbortableIterationConsumer.Continuation.ABORT
                            : AbortableIterationConsumer.Continuation.CONTINUE;
                    });
                    searchVisits += visited[0];
                } else {
                    continue;
                }
                if (!occupied[0]) {
                    chosen = candidate;
                }
            }
            if (chosen != null) {
                break;
            }
        }
        if (chosen == null) {
            routeFailed(body, facts, counters, now);
            facts.hazardBackoffUntil = now + CorpseRules.ROUTE_BACKOFF_TICKS;
            return;
        }
        requestPath(body, level, facts, counters, now,
            chosen.getX() + 0.5D, chosen.getY(), chosen.getZ() + 0.5D, CorpseRules.COMBAT_SPEED);
        counters.hazardRoutes++;
    }

    /**
     * Read-counting, cache-backed, halo-restricted {@link net.minecraft.world.level.BlockGetter}
     * for safe-destination collision reads (frozen invariant 19). The complete moved AABB
     * plus its one-block halo must be loaded ({@link #haloLoaded()}) before any read; every
     * actual level read is counted once and repeats are served from the cache; any
     * out-of-halo, over-budget, or block-entity request is rejected with void air and
     * poisons the candidate via {@link #withinContract()}.
     */
    private static final class HaloReadCache implements net.minecraft.world.level.BlockGetter {
        private final ServerLevel level;
        private final BlockPos min;
        private final BlockPos max;
        private final int budget;
        private final Map<BlockPos, BlockState> cache = new java.util.HashMap<>();
        private int reads;
        private boolean rejected;

        private HaloReadCache(final ServerLevel level, final AABB halo, final int budget) {
            this.level = level;
            this.min = BlockPos.containing(halo.minX, halo.minY, halo.minZ);
            this.max = BlockPos.containing(halo.maxX, halo.maxY, halo.maxZ);
            this.budget = budget;
        }

        /** The halo spans at most two chunks per axis, so its four corners prove loading. */
        private boolean haloLoaded() {
            return level.hasChunkAt(min) && level.hasChunkAt(max)
                && level.hasChunkAt(new BlockPos(min.getX(), min.getY(), max.getZ()))
                && level.hasChunkAt(new BlockPos(max.getX(), max.getY(), min.getZ()));
        }

        private boolean withinContract() {
            return !rejected;
        }

        private int actualReads() {
            return reads;
        }

        @Override
        public BlockState getBlockState(final BlockPos position) {
            if (position.getX() < min.getX() || position.getX() > max.getX()
                || position.getY() < min.getY() || position.getY() > max.getY()
                || position.getZ() < min.getZ() || position.getZ() > max.getZ()) {
                rejected = true;
                return net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState();
            }
            final BlockPos key = position.immutable();
            final BlockState cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            if (reads >= budget) {
                rejected = true;
                return net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState();
            }
            reads++;
            final BlockState read = level.getBlockState(key);
            cache.put(key, read);
            return read;
        }

        @Override
        public net.minecraft.world.level.material.FluidState getFluidState(final BlockPos position) {
            return getBlockState(position).getFluidState();
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(final BlockPos position) {
            rejected = true;
            return null;
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinY() {
            return level.getMinY();
        }
    }

    // -------------------------------------------------------------- combat

    private static LivingEntity selectTarget(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts,
        final Counters counters,
        final int now,
        final boolean graveCommand
    ) {
        final LivingEntity attacker = facts.attackerId == null
            ? null
            : resolveLiving(level, facts.attackerId);
        final boolean directAttacker = attacker != null && legalTarget(body, attacker);
        LivingEntity retained = resolveLiving(level, facts.targetId);
        if (retained == null) {
            final LivingEntity imported = body.getTarget();
            if (imported != null && legalTarget(body, imported)) {
                retained = imported;
            }
        }
        final LivingEntity explicit = retained;
        final LivingEntity graveSignal = graveControllerThreat(body, level, facts);
        final LivingEntity raiseSignal = raiseDefenseThreat(body, level, facts);
        final LivingEntity subject = CorpseRules.targetSource(
                directAttacker, graveCommand, explicit != null, graveSignal != null)
            .or(() -> CorpseRules.targetSourceWithoutExplicit(
                directAttacker, graveCommand, graveSignal != null, raiseSignal != null))
            .map(source -> switch (source) {
                case DIRECT_ATTACKER -> attacker;
                case EXPLICIT -> explicit;
                case GRAVE_CONTROLLER -> graveSignal;
                case RAISE_OWNER -> raiseSignal;
            })
            .orElse(null);
        if (subject == null) {
            releaseTarget(body, facts, counters);
            return null;
        }
        if (CorpseRules.lineOfSightDue(facts.lastLosTick, now)) {
            facts.lastLosTick = now;
            counters.losRays++;
            facts.lastLosResult = body.getSensing().hasLineOfSight(subject);
        }
        facts.lostSightTicks = facts.lastLosResult ? 0 : facts.lostSightTicks + 1;
        final CorpseRules.Release release = CorpseRules.retention(
            subject.isAlive(),
            subject.level() == level,
            legalTarget(body, subject),
            body.distanceTo(subject),
            facts.lostSightTicks
        );
        if (release != CorpseRules.Release.NONE) {
            releaseTarget(body, facts, counters);
            return null;
        }
        if (!subject.getUUID().equals(facts.targetId)) {
            cancelClutch(body, facts);
            stopNavigation(body);
            facts.targetId = subject.getUUID();
            facts.lostSightTicks = 0;
            body.setTarget(subject);
            counters.targetAcquisitions++;
        }
        return subject;
    }

    private static LivingEntity graveControllerThreat(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts
    ) {
        if (!facts.graveAuthorityActive || facts.graveOwnerId == null) {
            return null;
        }
        final ServerPlayer owner = level.getServer().getPlayerList().getPlayer(facts.graveOwnerId);
        if (owner == null || owner.level() != level) {
            return null;
        }
        final LivingEntity hurtMob = owner.getLastHurtMob();
        if (hurtMob != null
            && CorpseRules.timestampFresh(owner.tickCount, owner.getLastHurtMobTimestamp(),
                CorpseRules.GRAVE_TIMESTAMP_MAX_AGE)
            && hurtMob.level() == level && legalTarget(body, hurtMob)) {
            return hurtMob;
        }
        final LivingEntity hurtBy = owner.getLastHurtByMob();
        if (hurtBy != null
            && CorpseRules.timestampFresh(owner.tickCount, owner.getLastHurtByMobTimestamp(),
                CorpseRules.GRAVE_TIMESTAMP_MAX_AGE)
            && hurtBy.level() == level && legalTarget(body, hurtBy)) {
            return hurtBy;
        }
        return null;
    }

    private static LivingEntity raiseDefenseThreat(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts
    ) {
        if (!CorpseRules.raiseDefenseAvailable(facts.graveAuthorityActive) || facts.raiseOwnerId == null) {
            return null;
        }
        final LivingEntity owner = resolveLiving(level, facts.raiseOwnerId);
        if (owner == null || body.distanceTo(owner) > CorpseRules.OWNER_ENVELOPE) {
            return null;
        }
        final LivingEntity hurtBy = owner.getLastHurtByMob();
        if (hurtBy != null
            && CorpseRules.timestampFresh(owner.tickCount, owner.getLastHurtByMobTimestamp(),
                CorpseRules.RAISE_TIMESTAMP_MAX_AGE)
            && hurtBy.level() == level
            && CorpseRules.ownerDefenseInRange(body.distanceTo(hurtBy))
            && legalTarget(body, hurtBy)) {
            return hurtBy;
        }
        return null;
    }

    private static void driveClutch(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts,
        final Counters counters,
        final LivingEntity target,
        final int now
    ) {
        final boolean inReach = body.isWithinMeleeAttackRange(target);
        if (facts.clutchWindup < 0) {
            if (!inReach || !facts.lastLosResult) {
                if (CorpseRules.pathDue(facts.lastPathTick, now) && CorpseRules.routeAllowed(facts.route, now)) {
                    requestPath(body, level, facts, counters, now,
                        target.getX(), target.getY(), target.getZ(), CorpseRules.COMBAT_SPEED);
                }
                return;
            }
            facts.clutchWindup = 0;
            counters.clutchesStarted++;
            stopNavigation(body);
            body.setAggressive(true);
            feedback(body, level, SoundEvents.ZOMBIE_AMBIENT, ParticleTypes.ASH, 4);
            return;
        }
        body.getLookControl().setLookAt(target);
        if (!inReach || !facts.lastLosResult || !target.isAlive() || !legalTarget(body, target)) {
            cancelClutch(body, facts);
            counters.clutchesCancelled++;
            return;
        }
        facts.clutchWindup++;
        if (!CorpseRules.clutchComplete(facts.clutchWindup)) {
            return;
        }
        facts.clutchWindup = -1;
        facts.recoveryUntil = now + CorpseRules.CLUTCH_RECOVERY_TICKS;
        body.setAggressive(false);
        if (!charge(level, CorpseRules.Work.CLUTCH, 1)) {
            counters.expensiveTokensDeferred++;
            return;
        }
        final float before = target.getHealth() + target.getAbsorptionAmount();
        counters.attackAttempts++;
        body.swing(InteractionHand.MAIN_HAND);
        final boolean hit = body.doHurtTarget(level, target);
        final float after = target.getHealth() + target.getAbsorptionAmount();
        if (CorpseRules.applySlowness(hit, before, after)) {
            counters.attacksAccepted++;
            target.addEffect(new MobEffectInstance(
                MobEffects.SLOWNESS,
                CorpseRules.SLOWNESS_DURATION_TICKS,
                CorpseRules.SLOWNESS_AMPLIFIER
            ), body);
            counters.slownessApplications++;
            feedback(body, level, null, ParticleTypes.ASH, 8);
        } else {
            counters.attacksRejected++;
        }
    }

    private static void cancelClutch(final CorpseEntity body, final Transient facts) {
        if (facts.clutchWindup >= 0) {
            facts.clutchWindup = -1;
            body.setAggressive(false);
        }
    }

    private static void releaseTarget(final CorpseEntity body, final Transient facts, final Counters counters) {
        if (facts.targetId != null) {
            facts.targetId = null;
            facts.lostSightTicks = 0;
            counters.targetReleases++;
            cancelClutch(body, facts);
            stopNavigation(body);
        }
        if (body.getTarget() != null && !legalTarget(body, body.getTarget())) {
            body.setTarget(null);
        }
    }

    // -------------------------------------------------------- grave command

    private static void driveGraveCommand(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts,
        final Counters counters,
        final int now
    ) {
        final Vec3 destination = facts.graveDestination;
        if (destination == null) {
            return;
        }
        if (body.position().distanceToSqr(destination) <= CorpseRules.FINAL_ARRIVAL_DISTANCE_SQR) {
            facts.graveDestination = null;
            stopNavigation(body);
            facts.route = CorpseRules.routeSucceeded();
            return;
        }
        final BlockPos footprint = BlockPos.containing(destination);
        if (!level.isLoaded(footprint) || !level.getWorldBorder().isWithinBounds(footprint)) {
            facts.graveDestination = null;
            stopNavigation(body);
            return;
        }
        if (CorpseRules.pathDue(facts.lastPathTick, now) && CorpseRules.routeAllowed(facts.route, now)) {
            requestPath(body, level, facts, counters, now,
                destination.x, destination.y, destination.z, CorpseRules.GRAVE_COMMAND_SPEED);
            if (facts.route.released()) {
                facts.graveDestination = null;
            }
        }
    }

    // ------------------------------------------------------------ scavenging

    /**
     * Token/scan acquisition only; performs no navigation write so the activity
     * transition (with its full cancellation sequence) runs BEFORE any path request.
     */
    private static ItemEntity acquireScavengeTarget(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts,
        final Counters counters,
        final int now
    ) {
        if (facts.foodToken == null) {
            final int phase = CorpseRules.stagger(body.getUUID(), CorpseRules.ITEM_SCAN_INTERVAL_TICKS);
            if (facts.lastItemScanTick != Long.MIN_VALUE
                && now - facts.lastItemScanTick < CorpseRules.ITEM_SCAN_INTERVAL_TICKS) {
                return null;
            }
            if (Math.floorMod(now, CorpseRules.ITEM_SCAN_INTERVAL_TICKS) != phase
                && facts.lastItemScanTick != Long.MIN_VALUE) {
                return null;
            }
            if (!charge(level, CorpseRules.Work.EXPENSIVE, 1)) {
                counters.expensiveTokensDeferred++;
                return null;
            }
            facts.lastItemScanTick = now;
            final Optional<CorpseRules.ItemCandidate> selected =
                scanForFood(body, level, counters, false);
            if (selected.isEmpty()) {
                return null;
            }
            facts.foodToken = new CorpseRules.ItemToken(
                selected.orElseThrow().id(),
                selected.orElseThrow().itemId(),
                selected.orElseThrow().count()
            );
            counters.foodTargetsBound++;
        }
        final ItemEntity item = resolveItem(level, facts.foodToken.id());
        if (item == null) {
            releaseFood(facts, counters, now);
            return null;
        }
        return item;
    }

    /** Movement/mutation half; runs only after the SCAVENGE activity transition. */
    private static boolean driveScavenge(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts,
        final Counters counters,
        final ItemEntity item,
        final int now
    ) {
        final double distanceSqr = body.distanceToSqr(item);
        if (distanceSqr <= CorpseRules.FINAL_ARRIVAL_DISTANCE_SQR) {
            attemptMutation(body, level, facts, counters, item, now);
            return true;
        }
        if (CorpseRules.pathDue(facts.lastPathTick, now) && CorpseRules.routeAllowed(facts.route, now)) {
            requestPath(body, level, facts, counters, now,
                item.getX(), item.getY(), item.getZ(), CorpseRules.COMBAT_SPEED);
            if (facts.route.released()) {
                releaseFood(facts, counters, now);
                return false;
            }
        }
        return true;
    }

    private static void dormantArrivalMeal(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts,
        final Counters counters,
        final int now
    ) {
        if (!CorpseRules.mayScavenge(0, body.corpseState().groundMealCooldown(), false)
            || now < facts.foodBackoffUntil) {
            return;
        }
        if (facts.lastItemScanTick != Long.MIN_VALUE
            && now - facts.lastItemScanTick < CorpseRules.ITEM_SCAN_INTERVAL_TICKS) {
            return;
        }
        if (!charge(level, CorpseRules.Work.EXPENSIVE, 1)) {
            counters.expensiveTokensDeferred++;
            return;
        }
        facts.lastItemScanTick = now;
        scanForFood(body, level, counters, true).ifPresent(candidate -> {
            if (candidate.distanceSqr() > CorpseRules.FINAL_ARRIVAL_DISTANCE_SQR) {
                return;
            }
            facts.foodToken = new CorpseRules.ItemToken(candidate.id(), candidate.itemId(), candidate.count());
            counters.foodTargetsBound++;
            final ItemEntity item = resolveItem(level, candidate.id());
            if (item != null) {
                attemptMutation(body, level, facts, counters, item, now);
            } else {
                releaseFood(facts, counters, now);
            }
        });
    }

    private static Optional<CorpseRules.ItemCandidate> scanForFood(
        final CorpseEntity body,
        final ServerLevel level,
        final Counters counters,
        final boolean dormant
    ) {
        counters.itemQueries++;
        final double radius = CorpseRules.scanRadius(dormant);
        final AABB box = body.getBoundingBox().inflate(radius);
        final List<CorpseRules.ItemCandidate> raw = new ArrayList<>(CorpseRules.MAX_ITEM_CANDIDATES);
        level.getEntities().get(EntityTypeTest.forClass(ItemEntity.class), box, item -> {
            if (!charge(level, CorpseRules.Work.ITEM_VISIT, 1)) {
                return AbortableIterationConsumer.Continuation.ABORT;
            }
            counters.itemCandidatesVisited++;
            raw.add(new CorpseRules.ItemCandidate(
                item.getUUID(),
                BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString(),
                item.getItem().getCount(),
                body.distanceToSqr(item)
            ));
            return raw.size() >= CorpseRules.MAX_ITEM_CANDIDATES
                ? AbortableIterationConsumer.Continuation.ABORT
                : AbortableIterationConsumer.Continuation.CONTINUE;
        });
        return CorpseRules.selectItem(raw);
    }

    private static void attemptMutation(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts,
        final Counters counters,
        final ItemEntity item,
        final int now
    ) {
        final boolean populationToken = charge(level, CorpseRules.Work.ITEM_MUTATION, 1);
        if (!populationToken) {
            counters.autonomousMutationsDeferred++;
            return;
        }
        final CorpseRules.ItemCandidate current = new CorpseRules.ItemCandidate(
            item.getUUID(),
            BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString(),
            item.getItem().getCount(),
            body.distanceToSqr(item)
        );
        final boolean valid = CorpseRules.tokenValid(
            facts.foodToken,
            current,
            item.isAlive() && item.level() == level,
            true
        );
        if (!valid) {
            releaseFood(facts, counters, now);
            return;
        }
        item.getItem().shrink(1);
        if (item.getItem().isEmpty()) {
            item.discard();
        }
        counters.autonomousMutationsAccepted++;
        counters.itemsConsumed++;
        applyFeedResult(body, level, true);
        facts.foodToken = null;
        stopNavigation(body);
    }

    private static void applyFeedResult(
        final CorpseEntity body,
        final ServerLevel level,
        final boolean groundMeal
    ) {
        body.heal(CorpseRules.FOOD_DIRECT_HEAL);
        CorpseState state = body.corpseState()
            .withCohesion(CorpseRules.fed(body.corpseState().cohesion()));
        if (groundMeal) {
            state = state.withCooldown(CorpseRules.GROUND_MEAL_COOLDOWN_TICKS);
        }
        body.setCorpseState(state);
        body.synchronizeDormant(state.dormant());
        feedback(body, level, SoundEvents.GENERIC_EAT.value(), ParticleTypes.SOUL, 6);
    }

    private static void releaseFood(final Transient facts, final Counters counters, final int now) {
        if (facts.foodToken != null) {
            facts.foodToken = null;
            counters.foodTargetsReleased++;
            facts.foodBackoffUntil = now + CorpseRules.ITEM_RELEASE_BACKOFF_TICKS;
        }
    }

    // ---------------------------------------------------------------- follow

    /**
     * Follow eligibility with hysteresis: start beyond eight, keep closing while
     * inside the band, and end only at the designed 4-block stop or outside the
     * owner envelope. Performs no navigation write so the activity transition
     * (with its full cancellation sequence) runs BEFORE any path request; the
     * stop itself is executed by the IDLE handoff's cancellation.
     */
    private static LivingEntity followSubject(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts
    ) {
        if (facts.graveAuthorityActive || facts.raiseOwnerId == null) {
            return null;
        }
        final LivingEntity owner = resolveLiving(level, facts.raiseOwnerId);
        if (owner == null || owner.level() != level) {
            return null;
        }
        final double distance = body.distanceTo(owner);
        if (facts.activity == CorpseRules.Activity.FOLLOW) {
            return CorpseRules.followShouldContinue(distance) ? owner : null;
        }
        return CorpseRules.followShouldStart(distance) ? owner : null;
    }

    /** Movement half; runs only after the FOLLOW activity transition. */
    private static void driveFollow(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts,
        final Counters counters,
        final LivingEntity owner,
        final int now
    ) {
        if (CorpseRules.pathDue(facts.lastPathTick, now) && CorpseRules.routeAllowed(facts.route, now)) {
            requestPath(body, level, facts, counters, now,
                owner.getX(), owner.getY(), owner.getZ(), CorpseRules.COMBAT_SPEED);
        }
    }

    // ------------------------------------------------------------ navigation

    private static void requestPath(
        final CorpseEntity body,
        final ServerLevel level,
        final Transient facts,
        final Counters counters,
        final int now,
        final double x,
        final double y,
        final double z,
        final double speed
    ) {
        if (!charge(level, CorpseRules.Work.PATH, 1)) {
            counters.expensiveTokensDeferred++;
            return;
        }
        facts.lastPathTick = now;
        counters.pathRequests++;
        final boolean accepted = body.getNavigation().moveTo(x, y, z, speed);
        if (accepted) {
            counters.pathAccepted++;
            facts.route = CorpseRules.routeSucceeded();
        } else {
            routeFailed(body, facts, counters, now);
        }
    }

    private static void routeFailed(
        final CorpseEntity body,
        final Transient facts,
        final Counters counters,
        final int now
    ) {
        counters.pathFailures++;
        facts.route = CorpseRules.routeFailed(facts.route, now);
        if (facts.route.released()) {
            counters.pathBackoffs++;
            stopNavigation(body);
        }
    }

    private static void stopNavigation(final CorpseEntity body) {
        body.getNavigation().stop();
        body.getMoveControl().setWait();
        final Vec3 movement = body.getDeltaMovement();
        body.setDeltaMovement(0.0D, movement.y, 0.0D);
    }

    private static void enterActivity(
        final CorpseEntity body,
        final Transient facts,
        final CorpseRules.Activity activity
    ) {
        if (facts.activity != activity) {
            cancelClutch(body, facts);
            stopNavigation(body);
            facts.route = CorpseRules.routeSucceeded();
            facts.activity = activity;
        }
    }

    // -------------------------------------------------------------- feedback

    private static void feedback(
        final CorpseEntity body,
        final ServerLevel level,
        final net.minecraft.sounds.SoundEvent sound,
        final net.minecraft.core.particles.SimpleParticleType particle,
        final int particleCount
    ) {
        if (!charge(level, CorpseRules.Work.FEEDBACK, 1)) {
            body.corpseCounters().feedbackSuppressed++;
            return;
        }
        body.corpseCounters().feedbackEmitted++;
        if (sound != null) {
            level.playSound(null, body.getX(), body.getY(), body.getZ(),
                sound, body.getSoundSource(), 1.0F, 1.0F);
        }
        if (particle != null && particleCount > 0) {
            level.sendParticles(particle, body.getX(), body.getY() + 1.0D, body.getZ(),
                particleCount, 0.3D, 0.5D, 0.3D, 0.01D);
        }
    }

    // --------------------------------------------------------------- lookups

    private static LivingEntity resolveLiving(final ServerLevel level, final UUID id) {
        if (id == null) {
            return null;
        }
        return level.getEntity(id) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    private static ItemEntity resolveItem(final ServerLevel level, final UUID id) {
        return level.getEntity(id) instanceof ItemEntity item && item.isAlive() ? item : null;
    }
}
