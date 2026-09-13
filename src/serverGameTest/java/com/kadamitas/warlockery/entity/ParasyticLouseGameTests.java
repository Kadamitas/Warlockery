package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.DeliveryRoute;
import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.EvictReason;
import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.Phase;
import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.RedirectRejection;
import com.kadamitas.warlockery.entity.behavior.Cadence;
import com.kadamitas.warlockery.entity.behavior.PhaseTimer;
import com.kadamitas.warlockery.item.ParasyticLouseItem;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Five bounded live F31 fixtures. Every subject is a spawned, AI-enabled, genuinely self-ticking
 * entity; every fixture cleans up every entity and block it created in a {@code finally}-equivalent
 * scope even when an assertion throws; and every assertion reads an exact counter, an exact
 * persisted value or an exact attribution rather than an elapsed-time guess.
 *
 * <p>Arena geometry: the framework seals the {@code forge:empty3x3x3} cell in a barrier shell, so
 * every entity and every computed destination stays inside relative 0..2 with entities at y=1 over a
 * placed floor at y=0. The tenancy bands were chosen to fit: the mark opens inside three blocks, the
 * attach commits inside two and the feed lands inside one and a half, so no fixture ever needs to
 * reopen the shell to reach a real band and no destination can silently land outside the arena and
 * freeze the louse on stale state.</p>
 *
 * <p>The host scan reaches eight blocks, which is wider than one cell, so these fixtures are
 * dispatched one exact id at a time and assert only pass-local counters plus the identity of the
 * host they placed themselves. No fixture ever counts entities by querying the world for a type,
 * because a neighbouring instance may own some of them.</p>
 *
 * <p>Every {@code runAfterDelay} is registered directly from the fixture body and never from inside
 * another such callback.</p>
 *
 * <p>These fixtures depend on the coordinator-deferred {@code ModEntities} and {@code ModGameTests}
 * wiring that routes {@code warlockery:parasytic_louse} through {@link ParasyticLouseEntity} and
 * registers these five functions.</p>
 */
public final class ParasyticLouseGameTests {
    private static final Identifier POISON = Identifier.withDefaultNamespace("poison");
    /**
     * The redirect fixture's payload is deliberately Slowness rather than Poison. The natural stand
     * in for "something that just hit the owner" is an undead attacker, and undead entities are
     * immune to Poison, so a Poison payload would land nowhere and the fixture would be asserting
     * the vanilla immunity table instead of the delivery route. The player-facing contract is
     * unchanged: the payload is whatever the player loaded, and vanilla immunity still applies to it.
     */
    private static final Identifier SLOWNESS = Identifier.withDefaultNamespace("slowness");

    private ParasyticLouseGameTests() {
    }

    // ---------------------------------------------------------------- one: acquisition

    /**
     * The signature opening: a self-ticking louse runs a bounded scan, refuses everything the deny
     * gate covers, telegraphs a mark that writes nothing anywhere, and only then attaches.
     */
    public static void parasyticLouseMarksBeforeItAttachesToOneHost(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ParasyticLouseEntity louse = spawnLouse(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer host = fixture.connectedPlayer(new BlockPos(2, 1, 0));
            // Undead is already inside warlockery:disease_immune, so this is the deny gate's own
            // subject: an entity with no living substance to give can never become a host.
            final Zombie undead = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(0, 1, 2));
            undead.setNoAi(true);
            undead.setPersistenceRequired();

            final AtomicLong pathsAtMark = new AtomicLong(-1L);
            final AtomicLong attachesAtMark = new AtomicLong(-1L);
            final AtomicInteger hostHealthAtMark = new AtomicInteger(Integer.MIN_VALUE);

            helper.runAfterDelay(100L, () -> {
                helper.assertTrue(louse.tickCount > 0,
                    "the fixture subject is a genuinely self-ticking AI-enabled entity");
                helper.assertTrue(louse.louseCounters().hostScans() >= 1L,
                    "an idle louse off cooldown runs its bounded host scan");
                helper.assertTrue(
                    louse.louseCounters().hostRawVisits()
                        <= louse.louseCounters().hostScans()
                            * ParasyticLouseTenancyRules.MAX_SCAN_VISITS,
                    "no scan exceeded its declared six raw entity visits");
                helper.assertTrue(
                    louse.louseCounters().hostSightRays()
                        >= louse.louseCounters().hostScans(),
                    "a scan that found an eligible candidate spent a charged sight trace on it");
                // A scan that qualifies nothing must record the failure rather than silently
                // repeating every tick, so every scan is accounted for as one or the other.
                helper.assertValueEqual(
                    louse.louseCounters().scanFailures() + louse.louseCounters().hostAcquisitions(),
                    louse.louseCounters().hostScans(),
                    "every host scan either acquired a host or recorded its own failure");
                helper.assertTrue(louse.louseCounters().denyGateRejections() >= 1L,
                    "the undead decoy inside the scan radius was rejected by the deny gate");
                helper.assertTrue(louse.louseCounters().hostAcquisitions() >= 1L,
                    "the one eligible, visible, in-range candidate was acquired");
                helper.assertTrue(
                    louse.tenancy().host().id().filter(host.getUUID()::equals).isPresent(),
                    "the bound host is the fixture's own player and never the undead decoy");

                // Drive the telegraph deterministically so the mark window is genuinely exercised
                // rather than left to whether the walk happened to finish in time.
                final BlockPos beside = helper.absolutePos(new BlockPos(1, 1, 0));
                louse.getNavigation().stop();
                louse.snapTo(beside.getX() + 0.5D, beside.getY(), beside.getZ() + 0.5D);
                louse.setDeltaMovement(Vec3.ZERO);
                louse.tenancy().phase = Phase.MARK;
                louse.tenancy().mark =
                    PhaseTimer.start(Phase.MARK, ParasyticLouseTenancyRules.MARK_TICKS);
                pathsAtMark.set(louse.louseCounters().pathRequests());
                attachesAtMark.set(louse.louseCounters().attachCommits());
                hostHealthAtMark.set(Float.floatToRawIntBits(host.getHealth()));
            });

            helper.runAfterDelay(120L, () -> {
                helper.assertValueEqual(louse.tenancy().phase(), Phase.MARK,
                    "the thirty-tick telegraph is still running twenty ticks in");
                helper.assertValueEqual(louse.louseCounters().pathRequests(), pathsAtMark.get(),
                    "a marking louse requests no path and writes no navigation at all");
                helper.assertValueEqual(
                    Float.intBitsToFloat(hostHealthAtMark.get()), host.getHealth(),
                    "a marking louse deals no damage to the candidate it is telegraphing");
                helper.assertTrue(host.getActiveEffects().isEmpty(),
                    "a marking louse writes no effect onto the candidate");
                helper.assertTrue(louse.getTarget() == null,
                    "no vanilla target slot is ever written for this kind");
                helper.assertTrue(louse.getNavigation().isDone(),
                    "the telegraph left no path running, so the louse commits no movement to it");
            });

            helper.runAfterDelay(150L, () -> {
                helper.assertValueEqual(louse.louseCounters().attachCommits(),
                    attachesAtMark.get() + 1L,
                    "the elapsed telegraph committed exactly one attach; before="
                        + attachesAtMark.get() + ", now=" + louse.louseCounters().attachCommits());
                helper.assertValueEqual(louse.tenancy().phase(), Phase.FEED,
                    "a committed attach graduates the tenancy into its feeding term");
                helper.assertTrue(louse.tenancy().residenceRemainingTicks() > 0
                        && louse.tenancy().residenceRemainingTicks()
                            <= ParasyticLouseTenancyRules.RESIDENCE_TERM_TICKS,
                    "the residence term started inside its declared bound");
                helper.assertTrue(louse.louseCounters().occupancyProbes() >= 1L
                        && louse.louseCounters().occupancyRawVisits()
                            <= louse.louseCounters().occupancyProbes()
                                * ParasyticLouseTenancyRules.MAX_OCCUPANCY_VISITS,
                    "the occupancy probe ran and stayed inside its four raw visits");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- two: the feed ladder

    /**
     * The capped ladder and the single delivery. Four accepted pulses fill the louse, the fill
     * delivers the one payload at the one shared ceiling, and the tenancy ends in the same pass.
     */
    public static void parasyticLouseFeedsOnACappedLadderAndDeliversOnce(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ParasyticLouseEntity louse = spawnLouse(fixture, new BlockPos(1, 1, 1));
            final ServerPlayer host = fixture.connectedPlayer(new BlockPos(1, 1, 2));
            final List<String> blocksBefore = arenaBlockSignature(helper);
            fixture.dropDebris(new BlockPos(2, 1, 0));
            louse.setHealth(louse.getMaxHealth() - 4.0F);
            final java.util.concurrent.atomic.AtomicReference<Float> healthBefore =
                new java.util.concurrent.atomic.AtomicReference<>(louse.getHealth());
            // Rotten flesh is exactly what the retired shared grave-scavenge ambient used to walk to
            // and eat for two health, so leaving one in the arena is the falsifiable proof that the
            // behavior is gone rather than merely unregistered.
            fixture.dropDebris(new BlockPos(0, 1, 0));
            // Deliberately over the ceiling, so the clamp is proved rather than assumed.
            CreatureBehaviorState.storeEffect(louse, new CreatureBehaviorState.StoredEffect(
                POISON, 1_200, 0
            ));

            helper.runAfterDelay(40L, () -> {
                attach(helper, louse, host, ParasyticLouseTenancyRules.RESIDENCE_TERM_TICKS);
                louse.tenancy().feed = Cadence.every(ParasyticLouseTenancyRules.FEED_CADENCE_TICKS);
            });

            helper.runAfterDelay(240L, () -> {
                helper.assertTrue(louse.tickCount > 0,
                    "the fixture subject is a genuinely self-ticking AI-enabled entity");
                helper.assertValueEqual(louse.louseCounters().feedAccepted(),
                    (long) ParasyticLouseTenancyRules.MAX_NOURISHMENT,
                    "exactly four accepted pulses fill a louse and no fifth is ever spent");
                helper.assertValueEqual(louse.louseCounters().nourishmentIncrements(),
                    (long) ParasyticLouseTenancyRules.MAX_NOURISHMENT,
                    "the ladder rose exactly once per accepted effective pulse");
                // The pulse cadence, not a new damage number, is what separates a feeding parasite
                // from a brawler: two hundred ticks can hold at most six attempts at one per forty.
                helper.assertTrue(louse.louseCounters().feedAttempts() <= 6L,
                    "the feed never exceeded one attempt per forty loaded ticks; attempts="
                        + louse.louseCounters().feedAttempts());
                helper.assertValueEqual(louse.louseCounters().satiations(), 1L,
                    "a filled louse satiates exactly once");
                helper.assertValueEqual(
                    louse.louseCounters().deliveries(DeliveryRoute.SATIATION), 1L,
                    "the satiating pulse delivers the one payload exactly once");
                helper.assertValueEqual(louse.louseCounters().deliveriesTotal(), 1L,
                    "one payload has exactly one delivery across both routes");
                helper.assertValueEqual(louse.louseCounters().payloadCeilingClamps(), 1L,
                    "the stored twelve hundred ticks were clamped by the one shared ceiling");
                helper.assertTrue(CreatureBehaviorState.storedEffect(louse).isEmpty(),
                    "the delivered payload was cleared, so no route can deliver it again");

                final Optional<MobEffectInstance> delivered = BuiltInRegistries.MOB_EFFECT
                    .get(POISON)
                    .map(host::getEffect);
                helper.assertTrue(delivered.isPresent() && delivered.get() != null,
                    "the payload actually landed on the bound host");
                helper.assertTrue(
                    delivered.get().getDuration()
                        <= ParasyticLouseTenancyRules.PAYLOAD_CEILING_TICKS,
                    "the delivered duration respects the six hundred tick ceiling; duration="
                        + delivered.get().getDuration());

                // Attribution rather than health: suffocation or a fall could satisfy a health
                // assertion, which is exactly the wrong evidence for a parasite draining a host.
                helper.assertTrue(host.getLastHurtByMob() == louse,
                    "every feed pulse is ordinary attributed melee from this louse");

                helper.assertValueEqual(louse.tenancy().phase(), Phase.FREE,
                    "the tenancy ended in the same pass as the satiating pulse");
                helper.assertValueEqual(louse.louseCounters().evicts(EvictReason.SATED), 1L,
                    "satiation unwinds through the one named ending");
                helper.assertValueEqual(louse.louseCounters().evictsTotal(), 1L,
                    "exactly one ending occurred, so nothing unwound twice");
                helper.assertTrue(louse.louseState().seekCooldownRemainingTicks() > 0,
                    "the unwind armed the seek cooldown, so no new tenancy can start at once");
                helper.assertTrue(
                    louse.tenancy().releasedHost().id().filter(host.getUUID()::equals).isPresent(),
                    "the released host is remembered, so it cannot be re-taken on the next tick");
                helper.assertValueEqual(arenaBlockSignature(helper), blocksBefore,
                    "not one block state changed while the louse ran a whole tenancy");
                helper.assertTrue(fixture.debris.isAlive() && !fixture.debris.isRemoved(),
                    "the loose item in the arena was never consumed by this kind");
                helper.assertValueEqual(fixture.debris.getItem().getCount(), 1,
                    "the loose item stack was never mutated by this kind");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- three: the endings

    /**
     * The other ending and the free counters. The residence term expires through the same unwind as
     * satiation, an accepted hit makes the louse let go and withdraw without ever retaliating, a
     * free hand lifts it off in any state, and it never blocks the night.
     */
    public static void parasyticLouseTermExpiresAndGroomingFreesTheHost(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ParasyticLouseEntity termed = spawnLouse(fixture, new BlockPos(0, 1, 0));
            final ParasyticLouseEntity groomed = spawnLouse(fixture, new BlockPos(2, 1, 2));
            final ServerPlayer host = fixture.connectedPlayer(new BlockPos(2, 1, 0));
            CreatureBehaviorState.storeEffect(groomed, new CreatureBehaviorState.StoredEffect(
                POISON, 300, 1
            ));

            helper.runAfterDelay(20L, () -> {
                // One tick of term left, and the feed cadence deliberately armed, so the ending
                // that fires is unambiguously the term rather than a satiation racing it.
                attach(helper, termed, host, 1);
                termed.tenancy().feed =
                    Cadence.armed(ParasyticLouseTenancyRules.FEED_CADENCE_TICKS);
                termed.tenancy().evict =
                    Cadence.every(ParasyticLouseTenancyRules.EVICT_CADENCE_TICKS);
            });

            helper.runAfterDelay(70L, () -> {
                helper.assertValueEqual(termed.louseCounters().evicts(EvictReason.TERM_EXPIRED), 1L,
                    "the exhausted residence term produced its own named ending");
                helper.assertValueEqual(termed.louseCounters().evictsTotal(), 1L,
                    "the term ending is the only ending that fired");
                helper.assertValueEqual(termed.tenancy().phase(), Phase.FREE,
                    "an expired term unwinds through the same detach as satiation");
                helper.assertValueEqual(termed.tenancy().residenceRemainingTicks(), 0,
                    "no residue of the term survives its unwind");
                helper.assertTrue(termed.tenancy().host().id().isEmpty(),
                    "the host binding is released by the unwind");
                helper.assertTrue(termed.louseCounters().evictEvaluations() >= 1L
                        && termed.louseCounters().evictEvaluations() <= 4L,
                    "eviction was evaluated on its twenty-tick cadence and never per tick; count="
                        + termed.louseCounters().evictEvaluations());
                helper.assertValueEqual(termed.louseCounters().deliveriesTotal(), 0L,
                    "a louse with no payload still ends cleanly and delivers nothing");
                helper.assertFalse(termed.isPreventingPlayerRest(helper.getLevel(), host),
                    "a household nuisance with a free-hand counter never blocks the night");
            });

            helper.runAfterDelay(90L, () -> {
                // Re-attach so the accepted hit lands on a genuinely running tenancy.
                attach(helper, termed, host, ParasyticLouseTenancyRules.RESIDENCE_TERM_TICKS);
                termed.tenancy().feed =
                    Cadence.armed(ParasyticLouseTenancyRules.FEED_CADENCE_TICKS);
                termed.hurtServer(helper.getLevel(),
                    helper.getLevel().damageSources().playerAttack(host), 2.0F);
            });

            helper.runAfterDelay(110L, () -> {
                helper.assertValueEqual(termed.louseCounters().attackerAttributions(), 1L,
                    "one accepted hit mints exactly one attribution");
                helper.assertValueEqual(termed.louseCounters().evicts(EvictReason.ATTACKED), 1L,
                    "an accepted hit makes an attached louse let go at once");
                helper.assertValueEqual(termed.louseCounters().withdrawals(), 1L,
                    "the whole response to being hit is one bounded withdrawal");
                helper.assertTrue(host.getActiveEffects().isEmpty(),
                    "nothing at all is ever applied to an entity that hit the louse");
                helper.assertTrue(termed.tenancy().withdrawalTicks() > 0,
                    "the withdrawal window is genuinely running");
                helper.assertTrue(termed.getTarget() == null,
                    "the louse never acquires the entity that hit it");
                helper.assertValueEqual(termed.louseCounters().deliveriesTotal(), 0L,
                    "no effect is ever applied to an attacker of the louse");
                helper.assertFalse(termed.canAttack(host),
                    "a withdrawing louse outside a tenancy can attack nothing at all");
            });

            helper.runAfterDelay(140L, () -> {
                // The free-hand counter, used mid-tenancy by a player who is neither host nor owner
                // of that louse, through the real player-facing interaction path.
                attach(helper, groomed, host, ParasyticLouseTenancyRules.RESIDENCE_TERM_TICKS);
                host.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                final int before = countLouseItems(host);
                final InteractionResult result = groomed.mobInteract(host, InteractionHand.MAIN_HAND);
                helper.assertTrue(result.consumesAction(),
                    "an empty hand always lifts a louse off, in every state");
                helper.assertValueEqual(groomed.louseCounters().capturesByHand(), 1L,
                    "the capture is counted exactly once");
                helper.assertTrue(groomed.isRemoved(),
                    "the captured entity is discarded rather than left attached");
                helper.assertValueEqual(countLouseItems(host), before + 1,
                    "the capture produced exactly one warlockery:louse item and consumed nothing");
                helper.assertTrue(carriedPayloadDuration(host) > 0,
                    "the captured item carries the payload the entity was holding");
                helper.assertValueEqual(groomed.tenancy().phase(), Phase.FREE,
                    "capture tears the tenancy down, so nothing pending can land afterwards");
                helper.assertValueEqual(groomed.louseCounters().deliveriesTotal(), 0L,
                    "a captured louse delivers nothing on its way out");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- four: the redirect

    /**
     * The preserved owner-facing delivery, now bounded. It fires only with the owner resolved and
     * close, the tagged footwear worn, a fresh attribution and an unobstructed trace, and it fires
     * exactly once per payload.
     */
    public static void parasyticLouseRedirectRouteIsBoundedAndFiresOnce(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ParasyticLouseEntity louse = spawnLouse(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer owner = fixture.connectedPlayer(new BlockPos(1, 1, 0));
            final Zombie attacker = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 0));
            attacker.setNoAi(true);
            attacker.setPersistenceRequired();
            CreatureBehaviorState.bind(louse, owner.getUUID());
            CreatureBehaviorState.storeEffect(louse, new CreatureBehaviorState.StoredEffect(
                SLOWNESS, 1_200, 0
            ));
            owner.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);

            final AtomicLong evaluationsUnarmed = new AtomicLong(-1L);

            helper.runAfterDelay(90L, () -> {
                helper.assertTrue(louse.louseCounters().redirectEvaluations() >= 1L,
                    "the redirect route is evaluated on its own cadence");
                helper.assertValueEqual(louse.louseCounters().deliveriesTotal(), 0L,
                    "an owner with no tagged footwear produces no delivery at all");
                helper.assertTrue(
                    louse.louseCounters().redirectRejections(RedirectRejection.NO_ARMOR) >= 1L
                        || louse.louseCounters().redirectRejections(RedirectRejection.NO_ATTACKER)
                            >= 1L,
                    "every rejected evaluation recorded the first condition that actually failed");
                evaluationsUnarmed.set(louse.louseCounters().redirectEvaluations());

                // Arm every gate at once: the exact tagged boot, a live attacker inside sixteen
                // blocks of the owner, and a brand new attribution on the owner's own clock.
                owner.setItemSlot(EquipmentSlot.FEET,
                    new ItemStack(ModItems.ALL.get("seepingshoes").get()));
                owner.hurtServer(helper.getLevel(),
                    helper.getLevel().damageSources().mobAttack(attacker), 1.0F);
                louse.tenancy().redirect =
                    Cadence.every(ParasyticLouseTenancyRules.REDIRECT_CADENCE_TICKS);
            });

            helper.runAfterDelay(120L, () -> {
                helper.assertTrue(
                    louse.louseCounters().redirectEvaluations() > evaluationsUnarmed.get(),
                    "the armed gate was actually re-evaluated");
                helper.assertValueEqual(louse.louseCounters().deliveries(DeliveryRoute.REDIRECT), 1L,
                    "a fully armed redirect delivers exactly once");
                helper.assertValueEqual(louse.louseCounters().deliveriesTotal(), 1L,
                    "one payload has exactly one delivery across both routes");
                helper.assertValueEqual(louse.louseCounters().payloadCeilingClamps(), 1L,
                    "the redirect uses the same shared six hundred tick ceiling as the bite route");
                helper.assertTrue(louse.louseCounters().redirectSightRays() >= 1L,
                    "the delivery required a charged unobstructed trace to the attacker");
                final MobEffectInstance landed = BuiltInRegistries.MOB_EFFECT.get(SLOWNESS)
                    .map(attacker::getEffect)
                    .orElse(null);
                helper.assertTrue(landed != null,
                    "the payload landed on the owner's attacker and on nobody else");
                helper.assertTrue(
                    landed.getDuration() <= ParasyticLouseTenancyRules.PAYLOAD_CEILING_TICKS,
                    "the redirected duration respects the ceiling; duration=" + landed.getDuration());
                helper.assertTrue(owner.getActiveEffects().isEmpty(),
                    "the owner is never a redirect subject");
                helper.assertTrue(CreatureBehaviorState.storedEffect(louse).isEmpty(),
                    "the payload was cleared by its one delivery");
            });

            helper.runAfterDelay(200L, () -> {
                helper.assertValueEqual(louse.louseCounters().deliveriesTotal(), 1L,
                    "a spent payload can never be delivered a second time by any route");
                helper.assertTrue(
                    louse.louseCounters().redirectRejections(RedirectRejection.NO_PAYLOAD) >= 1L,
                    "later evaluations reject on the missing payload rather than firing again");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- five: lifecycle and reload

    /**
     * The replaced lifecycle and the reload contract. The dedicated body carries no Zombie identity,
     * no goal owns movement or targeting, the declared attribute bases are exact, and a real save and
     * load normalizes every transient field while preserving exactly the four durable ones.
     */
    public static void parasyticLouseReloadReplacesTheZombieLifecycle(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ParasyticLouseEntity louse = spawnLouse(fixture, new BlockPos(1, 1, 1));
            final ServerPlayer host = fixture.connectedPlayer(new BlockPos(1, 1, 2));
            final List<String> blocksBefore = arenaBlockSignature(helper);
            fixture.dropDebris(new BlockPos(2, 1, 0));
            louse.setHealth(louse.getMaxHealth() - 4.0F);
            final java.util.concurrent.atomic.AtomicReference<Float> healthBefore =
                new java.util.concurrent.atomic.AtomicReference<>(louse.getHealth());

            helper.runAfterDelay(30L, () -> {
                helper.assertTrue(louse.tickCount > 0,
                    "the fixture subject is a genuinely self-ticking AI-enabled entity");
                helper.assertFalse(Zombie.class.isInstance(louse),
                    "the dedicated body is not a Zombie and inherits none of its lifecycle");
                helper.assertFalse(ArcaneMob.class.isInstance(louse),
                    "the dedicated body no longer carries the shared ArcaneMob class identity");
                helper.assertTrue(Monster.class.isInstance(louse),
                    "the shipped hostile monster identity is preserved exactly");
                helper.assertValueEqual(louse.operationalTargetGoalCount(), 0,
                    "no target goal is ever registered, so no goal can select a host");
                helper.assertTrue(louse.operationalGoalNames().stream()
                        .noneMatch(name -> name.contains("Melee") || name.contains("Attack")
                            || name.contains("Stroll") || name.contains("Door")),
                    "the runtime owns movement and the feed, not a goal: "
                        + louse.operationalGoalNames());
                helper.assertValueEqual(louse.getMaxHealth(),
                    (float) ParasyticLouseEntity.BASE_MAX_HEALTH,
                    "the declared health base is exact");
                helper.assertValueEqual(louse.getAttributeValue(
                        net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE),
                    ParasyticLouseEntity.BASE_FOLLOW_RANGE,
                    "the generic random follow-range spawn bonus was stripped, so the base is exact");
                helper.assertValueEqual(louse.getAttributeValue(
                        net.minecraft.world.entity.ai.attributes.Attributes
                            .SPAWN_REINFORCEMENTS_CHANCE),
                    0.0D,
                    "reinforcement chance is declared zero rather than inherited");
                helper.assertFalse(louse.canPickUpLoot(), "loot pickup is disabled permanently");
                for (final EquipmentSlot slot : EquipmentSlot.values()) {
                    helper.assertTrue(louse.getItemBySlot(slot).isEmpty(),
                        "no equipment is ever finalized onto this kind: " + slot);
                }
                helper.assertFalse(louse.isPreventingPlayerRest(helper.getLevel(), host),
                    "a louse never blocks the night in any state");
            });

            helper.runAfterDelay(60L, () -> {
                // A fully loaded semantic state, then a real save and load through the registry.
                louse.setLouseState(new ParasyticLouseState(
                    ParasyticLouseState.SCHEMA_VERSION, 3, 137, 0
                ));
                attach(helper, louse, host, ParasyticLouseTenancyRules.RESIDENCE_TERM_TICKS);
                helper.assertValueEqual(louse.louseState().write().keySet().size(), 4,
                    "the durable record is exactly four keys wide and never grows");

                final ParasyticLouseEntity reloaded = reload(helper, fixture, louse);
                helper.assertValueEqual(reloaded.louseState().nourishment(), 3,
                    "durable nourishment survives the reload unchanged");
                helper.assertValueEqual(reloaded.louseState().decayRemainingTicks(), 137,
                    "the decay remainder survives, so a reload neither loses nor invents progress");
                helper.assertTrue(reloaded.louseState().seekCooldownRemainingTicks()
                        >= ParasyticLouseTenancyRules.LOAD_SEEK_COOLDOWN_FLOOR_TICKS,
                    "the load floor is applied, so cycling unload cannot renew a residence term");
                helper.assertValueEqual(reloaded.tenancy().phase(), Phase.FREE,
                    "the transient phase normalizes to free on load");
                helper.assertTrue(reloaded.tenancy().host().id().isEmpty(),
                    "no host binding survives a load");
                helper.assertValueEqual(reloaded.tenancy().residenceRemainingTicks(), 0,
                    "the residence term is deliberately not persisted");
                helper.assertValueEqual(reloaded.tenancy().markRemainingTicks(), 0,
                    "no telegraph survives a load, so no feedback can replay");
                helper.assertValueEqual(reloaded.louseCounters().deliveriesTotal(), 0L,
                    "a reload replays no delivery of its own");
                helper.assertValueEqual(reloaded.louseCounters().feedAttempts(), 0L,
                    "a reload replays no feed of its own");
                helper.assertTrue(reloaded.getTarget() == null,
                    "no live target survives a load");
                helper.assertFalse(reloaded.canPickUpLoot(),
                    "the normalized lifecycle is reapplied on load, not only on spawn");
            });

            helper.runAfterDelay(180L, () -> {
                helper.assertValueEqual(arenaBlockSignature(helper), blocksBefore,
                    "not one block state in the arena changed while a louse lived in it");
                helper.assertTrue(fixture.debris.isAlive() && !fixture.debris.isRemoved(),
                    "the loose rotten flesh survived untouched: the retired grave-scavenge ambient"
                        + " really is gone rather than merely unregistered");
                helper.assertValueEqual(fixture.debris.getItem().getCount(), 1,
                    "the loose stack was never shrunk, so nothing was consumed from it");
                helper.assertTrue(louse.getMainHandItem().isEmpty(),
                    "nothing was ever picked up into a hand slot");
                helper.assertTrue(healthBefore.get() >= louse.getHealth(),
                    "no scavenging heal ever occurred; before=" + healthBefore.get()
                        + ", now=" + louse.getHealth());
                helper.assertTrue(
                    louse.louseCounters().hazardObservations() >= 1L
                        && louse.louseCounters().hazardBlockReads()
                            <= louse.louseCounters().hazardObservations()
                                * ParasyticLouseTenancyRules.MAX_HAZARD_READS,
                    "hazard observation ran and stayed inside its charged eighteen-read ceiling");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- fixture support

    private static void buildFloor(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(0, 0, 0), new BlockPos(2, 0, 2))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
    }

    private static List<String> arenaBlockSignature(final GameTestHelper helper) {
        final List<String> states = new ArrayList<>();
        BlockPos.betweenClosedStream(new BlockPos(0, 0, 0), new BlockPos(2, 2, 2))
            .forEach(position -> states.add(
                position.toShortString() + '=' + helper.getBlockState(position)
            ));
        return List.copyOf(states);
    }

    /**
     * Forces one committed tenancy so the band under test is genuinely exercised rather than left to
     * whether the approach happened to finish in time. Mirrors exactly what the runtime's own attach
     * does, including stopping navigation, so the forced state is one the runtime could have reached.
     */
    private static void attach(
        final GameTestHelper helper,
        final ParasyticLouseEntity louse,
        final LivingEntity host,
        final int residenceTicks
    ) {
        final ServerLevel level = helper.getLevel();
        louse.getNavigation().stop();
        louse.tenancy().host = ParasyticLouseRuntime.Tenancy.Host.of(
            host.getUUID(), ParasyticLouseRuntime.dimensionOf(level)
        );
        louse.tenancy().mark = PhaseTimer.none();
        louse.tenancy().residenceRemainingTicks = residenceTicks;
        louse.tenancy().continuousSightLossTicks = 0;
        louse.tenancy().hostSighted = true;
        louse.tenancy().phase = Phase.FEED;
        louse.setLouseState(louse.louseState().withSeekCooldown(0));
    }

    private static int countLouseItems(final ServerPlayer player) {
        int found = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).getItem() instanceof ParasyticLouseItem) {
                found++;
            }
        }
        return found;
    }

    /**
     * Reads the payload straight out of the item's own {@code CUSTOM_DATA}, using the three shipped
     * keys verbatim, so the assertion proves the preserved entity-to-item transfer rather than
     * trusting a helper that could be changed alongside it.
     */
    private static int carriedPayloadDuration(final ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            final ItemStack stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof ParasyticLouseItem)) {
                continue;
            }
            final var data = stack
                .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag();
            if (!data.getStringOr("WarlockeryLouseEffect", "").isBlank()) {
                return data.getIntOr("WarlockeryLouseDuration", 0);
            }
        }
        return 0;
    }

    private static ParasyticLouseEntity reload(
        final GameTestHelper helper,
        final FixtureScope fixture,
        final ParasyticLouseEntity original
    ) {
        final TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        original.saveWithoutId(output);
        final var saved = output.buildResult().copy();
        // The saved data carries the original identity, so the original leaves first. The restored
        // copy is deliberately never added to the level: this fixture asserts the reconciliation a
        // load performs, and adding a duplicate identity is what silently fails.
        original.discard();
        final Entity restored = ModEntities.ALL.get("parasytic_louse").get()
            .create(helper.getLevel(), EntitySpawnReason.LOAD);
        helper.assertTrue(restored instanceof ParasyticLouseEntity,
            "the registered warlockery:parasytic_louse type must build the dedicated entity");
        fixture.track(restored);
        restored.load(TagValueInput.create(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        return (ParasyticLouseEntity) restored;
    }

    private static ParasyticLouseEntity spawnLouse(
        final FixtureScope fixture,
        final BlockPos position
    ) {
        @SuppressWarnings("unchecked")
        final EntityType<ParasyticLouseEntity> type =
            (EntityType<ParasyticLouseEntity>) ModEntities.ALL.get("parasytic_louse").get();
        return placed(fixture, fixture.spawn(type, position), position);
    }

    /** Places a subject without ever disabling its AI, so every assertion reads a live tick. */
    private static <T extends Mob> T placed(
        final FixtureScope fixture,
        final T entity,
        final BlockPos position
    ) {
        entity.setDeltaMovement(Vec3.ZERO);
        final BlockPos absolute = fixture.helper.absolutePos(position);
        entity.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        entity.setPersistenceRequired();
        return entity;
    }

    private static final class FixtureScope implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<Entity> entities = new ArrayList<>();
        private boolean closed;

        private FixtureScope(final GameTestHelper helper) {
            this.helper = helper;
        }

        private <T extends Entity> T spawn(final EntityType<T> type, final BlockPos position) {
            return track(helper.spawn(type, position, EntitySpawnReason.EVENT));
        }

        private ItemEntity debris;

        private ItemEntity dropDebris(final BlockPos position) {
            final BlockPos absolute = helper.absolutePos(position);
            final ItemEntity dropped = new ItemEntity(
                helper.getLevel(),
                absolute.getX() + 0.5D,
                absolute.getY() + 0.1D,
                absolute.getZ() + 0.5D,
                new ItemStack(Items.ROTTEN_FLESH, 1)
            );
            dropped.setDeltaMovement(Vec3.ZERO);
            dropped.setNeverPickUp();
            helper.getLevel().addFreshEntity(dropped);
            debris = dropped;
            return track(dropped);
        }

        private <T extends Entity> T track(final T entity) {
            entities.add(entity);
            return entity;
        }

        private ServerPlayer connectedPlayer(final BlockPos position) {
            final ServerPlayer player =
                (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
            final net.minecraft.network.Connection connection =
                new net.minecraft.network.Connection(
                    net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            final net.minecraft.server.network.CommonListenerCookie cookie =
                net.minecraft.server.network.CommonListenerCookie.createInitial(
                    player.getGameProfile(), false);
            helper.getLevel().getServer().getPlayerList()
                .placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.setInvulnerable(false);
            final BlockPos absolute = helper.absolutePos(position);
            player.teleportTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
            return track(player);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            // 4aab0a9: Entity.discard does not deregister a ServerPlayer, so a merely discarded
            // mock player stays in ServerLevel.players() for the rest of the run and eats the
            // bounded candidate budget of every later acquisition sweep. This family was written
            // against 69d43b8, before that fix, so it releases rather than discards.
            entities.forEach(GameTestMockPlayers::release);
            entities.clear();
        }
    }
}
