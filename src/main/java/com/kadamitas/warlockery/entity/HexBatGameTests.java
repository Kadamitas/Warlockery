package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.brew.BrewRuntime;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.HexBatRules.Action;
import com.kadamitas.warlockery.entity.HexBatRules.Mode;
import com.kadamitas.warlockery.entity.HexBatRules.Provenance;
import com.kadamitas.warlockery.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Four bounded live F15 fixtures. They assert through spawned, AI-enabled,
 * self-ticking entities and depend on the coordinator-deferred
 * ModEntities/ModGameTests wiring to route warlockery:hex_bat through
 * HexBatEntity and register these functions.
 */
public final class HexBatGameTests {
    private HexBatGameTests() {
    }

    public static void hexBatRoostsByDayAndSortiesAtNight(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            // The world clock is shared across the whole simultaneous batch:
            // the swoop fixture needs night from tick 0 through its accepted
            // contact, so this fixture writes no time at tick 0 and instead
            // claims the clock for its day segment at tick 140, after the
            // swoop fixture's night-dependent window has closed.
            erectBarrierShell(fixture, new BlockPos(1, 1, 1));
            final HexBatEntity bat = spawnBat(fixture, new BlockPos(1, 1, 1));
            helper.assertValueEqual(bat.getClass().getName(), HexBatEntity.class.getName(),
                "the exact registered hex_bat must construct the dedicated HexBatEntity class");
            helper.assertValueEqual(bat.creatureKind(), CreatureKind.HEX_BAT, "exact kind");
            helper.assertValueEqual(bat.getAttributeValue(Attributes.MAX_HEALTH), 14.0D, "health 14");
            helper.assertValueEqual(bat.getAttributeValue(Attributes.ATTACK_DAMAGE), 4.0D, "attack 4");
            helper.assertValueEqual(bat.getAttributeValue(Attributes.FOLLOW_RANGE), 16.0D, "follow range 16");
            helper.assertTrue(bat.getMainHandItem().isEmpty() && bat.getOffhandItem().isEmpty(),
                "hands stay empty through finalize spawn; the Vex sword never appears");
            helper.assertTrue(bat.isNoGravity(), "the dedicated bat is a no-gravity flyer");
            helper.assertValueEqual(bat.operationalTargetGoalCount(), 0,
                "no inherited target goal survives; the runtime is the only target authority");
            helper.assertValueEqual(bat.batCounters().genericRuntimeDispatches(), 0L,
                "generic behavior, tactical, ambient, and hazard runtimes execute zero times");

            // Tagged ceiling support directly above a bat-sized air pocket.
            // The framework seals the 3x3x3 cell in a barrier shell, so the
            // support log replaces the sealed ceiling position at relative
            // y=3 and the roost air pocket is the top interior layer (y=2);
            // the bat and its anchor sit at y=1 so the near sweep's
            // anchor+(+1,+1,0) offset lands exactly on the roost air.
            final BlockPos supportRelative = new BlockPos(2, 3, 1);
            final BlockPos roostAirRelative = new BlockPos(2, 2, 1);
            helper.setBlock(supportRelative, Blocks.OAK_LOG.defaultBlockState());
            fixture.onClose(() -> helper.setBlock(supportRelative, Blocks.AIR.defaultBlockState()));
            helper.runAfterDelay(140L, () -> {
                try {
                    // Day begins: the due loaded search may now run.
                    helper.setTime(6_000L);
                    makeDue(bat);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(225L, () -> {
                try {
                    final HexBatState state = bat.batState();
                    helper.assertTrue(state.anchor().isPresent(),
                        "the first valid loaded position becomes the soft anchor");
                    helper.assertTrue(state.roost().isPresent(),
                        "the loaded tagged ceiling search finds the supported air position");
                    helper.assertValueEqual(state.roost().orElseThrow(),
                        helper.absolutePos(roostAirRelative),
                        "the roost is the air position directly below the tagged support");
                    helper.assertTrue(bat.batCounters().roostCandidates()
                            <= HexBatRules.MAX_ROOST_CANDIDATES * 2L,
                        "roost candidate work stays bounded per due search");
                    helper.assertTrue(bat.batCounters().roostBlockReads()
                            <= HexBatRules.MAX_ROOST_BLOCK_READS * 2L,
                        "roost block reads stay charged and bounded");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(300L, () -> {
                try {
                    helper.assertTrue(bat.isRoosting(),
                        "the collision-aware approach ends in a true synchronized hang");
                    // Night arrives: the roosted bat departs on a local sortie.
                    helper.setTime(14_000L);
                    makeDue(bat);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(340L, () -> {
                try {
                    helper.assertFalse(bat.isRoosting(), "night clears the roost pose");
                    helper.assertValueEqual(bat.batState().mode(), Mode.SORTIE,
                        "the bat enters a bounded night sortie");
                    // Destroy the support: the roost claim must release without a cooldown.
                    helper.setBlock(supportRelative, Blocks.AIR.defaultBlockState());
                    helper.setTime(6_000L);
                    makeDue(bat);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(385L, () -> {
                try {
                    helper.assertTrue(bat.batState().roost().isEmpty()
                            || !bat.batState().roost().orElseThrow()
                                .equals(helper.absolutePos(roostAirRelative))
                            || !bat.isRoosting(),
                        "a destroyed support cancels the hang instead of freezing a fake roost");
                } finally {
                    fixture.close();
                }
                helper.succeed();
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void hexBatSwoopMarksAndReleasesTargetSafely(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            helper.setTime(14_000L);
            erectBarrierShell(fixture, new BlockPos(1, 1, 1));
            final HexBatEntity bat = spawnBat(fixture, new BlockPos(1, 2, 1));
            final Zombie prey = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2), EntitySpawnReason.EVENT);
            prey.setNoAi(true);
            prey.setDeltaMovement(Vec3.ZERO);
            makeDue(bat);
            helper.runAfterDelay(30L, () -> {
                try {
                    final HexBatState state = bat.batState();
                    helper.assertTrue(state.action() == Action.SWOOP
                            || bat.batCounters().actionsBegun() >= 1L,
                        "a legal visible hostile in range begins one telegraphed swoop");
                    if (state.action() == Action.SWOOP) {
                        helper.assertValueEqual(state.actionTargetId().orElseThrow(), prey.getUUID(),
                            "the swoop freezes the target UUID immutably");
                        helper.assertTrue(state.deadlines().actionWindupUntil()
                                - helper.getLevel().getGameTime() <= HexBatRules.SWOOP_WINDUP_TICKS,
                            "windup is at least ten ticks and starts immediately");
                        helper.assertTrue(bat.isSwooping(), "the synchronized swoop pose is set");
                    }
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(120L, () -> {
                try {
                    if (bat.batCounters().contactsAccepted() >= 1L) {
                        helper.assertTrue(prey.hasEffect(MobEffects.UNLUCK),
                            "one accepted attributed contact applies exactly UNLUCK");
                        final var effect = prey.getEffect(MobEffects.UNLUCK);
                        helper.assertValueEqual(effect.getAmplifier(), HexBatRules.JINX_AMPLIFIER,
                            "amplifier stays UNLUCK I");
                        helper.assertTrue(effect.getDuration() <= HexBatRules.JINX_DURATION_TICKS,
                            "duration is exactly two hundred ticks at application");
                        // The accepted action identity must have cleared; a
                        // fresh second swoop begun after the sixty-tick
                        // recovery is legitimate re-acquisition, not a replay.
                        helper.assertTrue(bat.batState().action() == Action.NONE
                                || bat.batCounters().actionsBegun() >= 2L,
                            "the immutable action identity clears so the hit cannot replay");
                        helper.assertTrue(bat.batState().deadlines().actionRecoverUntil()
                                > helper.getLevel().getGameTime() - HexBatRules.SWOOP_RECOVERY_TICKS,
                            "accepted contact enters the sixty-tick recovery");
                    }
                    helper.assertTrue(bat.batCounters().contactAttempts()
                            >= bat.batCounters().contactsAccepted(),
                        "attempts and acceptances are counted separately");
                    // Owner-protection release: bind the prey to the same owner.
                    final java.util.UUID owner = java.util.UUID.randomUUID();
                    CreatureBehaviorState.bind(bat, owner);
                    CreatureBehaviorState.bind(prey, owner);
                    bat.setTarget(prey);
                    helper.assertFalse(bat.canAttack(prey),
                        "the final canAttack gate rejects a same-owner target even after external setTarget");
                    makeDue(bat);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(150L, () -> {
                try {
                    helper.assertTrue(bat.batState().action() == Action.NONE,
                        "a protected relation can never hold a bound action");
                    final Villager villager = fixture.spawn(
                        EntityTypes.VILLAGER, new BlockPos(0, 1, 2), EntitySpawnReason.EVENT
                    );
                    villager.setNoAi(true);
                    helper.assertTrue(HexBatRules.proactivelyExcluded(
                            HexBatRuntime.proactiveFacts(villager, 4.0D, true)),
                        "villagers are never proactive prey");
                } finally {
                    fixture.close();
                }
                helper.succeed();
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void murderousFlockProtectsCasterAndCallsLocally(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            erectBarrierShell(fixture, new BlockPos(1, 1, 1));
            final Zombie zombie = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1), EntitySpawnReason.EVENT);
            zombie.setNoAi(true);
            final BlockPos impact = helper.absolutePos(new BlockPos(1, 2, 1));
            // The REAL production brew path: one potency-1.5 Murderous Flock
            // cast through BrewRuntime attempts exactly six event spawns and
            // seeds provenance, anchor, persistence, and the ranked initial
            // target on every successful exact Hex Bat.
            final BrewRuntime.ImpactResult result = BrewRuntime.handleImpact(
                helper.getLevel(), BrewKind.MURDEROUS_FLOCK, Vec3.atCenterOf(impact), null, null
            );
            final List<HexBatEntity> bats = helper.getLevel().getEntitiesOfClass(
                HexBatEntity.class, new AABB(impact).inflate(8.0D)
            );
            bats.forEach(fixture::track);
            helper.assertValueEqual(bats.size(), 6,
                "potency 1.5 requests exactly six event spawns and the open shell accepts each");
            helper.assertValueEqual(result.affectedEntities(), 6,
                "the public result accounting reports every successful spawn exactly as before");
            for (final HexBatEntity bat : bats) {
                helper.assertValueEqual(bat.batState().provenance(), Provenance.MURDEROUS_FLOCK,
                    "every successful flock bat carries flock provenance");
                helper.assertValueEqual(bat.batState().anchor().orElseThrow(), impact,
                    "the impact position seeds the soft anchor");
                helper.assertTrue(bat.isPersistenceRequired(),
                    "successful flock bats are persistence-required");
                helper.assertValueEqual(bat.getTarget(), zombie,
                    "the existing observable no-owner zombie outcome is preserved");
            }
            // Ranked once-per-cast selection: the explicit cast target can
            // never lose to a nearer hostile.
            final Zombie nearer = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1), EntitySpawnReason.EVENT);
            nearer.setNoAi(true);
            final var ranked = HexBatRuntime.flockTargets(
                null, zombie, Vec3.atCenterOf(impact), List.of(nearer, zombie)
            );
            helper.assertValueEqual(ranked.get(0), zombie,
                "the explicit cast target outranks a strictly nearer ordinary hostile");
            // Owner and same-owner protection.
            final HexBatEntity owned = bats.get(0);
            final Zombie guarded = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(0, 1, 1), EntitySpawnReason.EVENT);
            guarded.setNoAi(true);
            final java.util.UUID owner = java.util.UUID.randomUUID();
            CreatureBehaviorState.bind(owned, owner);
            CreatureBehaviorState.bind(guarded, owner);
            helper.assertFalse(HexBatRuntime.eligibleTarget(owned, guarded),
                "a same-owner entity can never be attacked");
            helper.assertFalse(HexBatRuntime.eligibleTarget(owned, bats.get(1)),
                "an exact Hex Bat can never be attacked");
            helper.assertFalse(HexBatRuntime.legalFlockTarget(null, bats.get(1)),
                "flock selection never includes exact Hex Bats");
            // Local exact-species one-hop THREAT call.
            final HexBatEntity caller = bats.get(1);
            final HexBatEntity receiver = bats.get(2);
            final long now = helper.getLevel().getGameTime();
            caller.setBatState(caller.batState().withDeadlines(HexBatState.Deadlines.none()));
            final HexBatState afterCall = HexBatRuntime.emitThreatCall(
                caller, helper.getLevel(), caller.batState(), zombie.getUUID(), now
            );
            caller.setBatState(afterCall);
            helper.assertTrue(caller.batCounters().callsAttempted() >= 1L, "call work is counted");
            helper.assertTrue(caller.batCounters().callRecipients()
                    <= HexBatRules.MAX_CALL_RECIPIENTS,
                "at most three recipients accept a call");
            helper.assertTrue(receiver.batState().threatId()
                    .map(zombie.getUUID()::equals).orElse(true),
                "an accepted report carries the exact target identity");
            if (receiver.batState().threatId().isPresent()) {
                helper.assertValueEqual(receiver.batState().threatHopCount(), HexBatRules.MAX_CALL_HOPS,
                    "the received report stores hop one");
                final HexBatState reEmit = HexBatRuntime.emitThreatCall(
                    receiver, helper.getLevel(),
                    receiver.batState().withThreat(
                        receiver.batState().threatId(), receiver.batState().threatDimension(),
                        receiver.batState().threatExpiresAt(), HexBatRules.MAX_CALL_HOPS
                    ),
                    zombie.getUUID(), now
                );
                helper.assertTrue(receiver.batCounters().callsDeduped() >= 1L
                        || reEmit.deadlines().callDedupeUntil() == receiver.batState().deadlines().callDedupeUntil(),
                    "a received call can never recursively re-emit");
            }
            final HexBatState deduped = HexBatRuntime.emitThreatCall(
                caller, helper.getLevel(), caller.batState(), zombie.getUUID(), now + 1L
            );
            helper.assertTrue(caller.batCounters().callsDeduped() >= 1L,
                "the forty-tick caller-target dedupe blocks immediate repeats");
            caller.setBatState(deduped);
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void hexBatSaveReloadHazardAndWorkAreBounded(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            erectBarrierShell(fixture, new BlockPos(1, 1, 1));
            final HexBatEntity bat = spawnBat(fixture, new BlockPos(1, 2, 1));
            final Zombie prey = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1), EntitySpawnReason.EVENT);
            prey.setNoAi(true);
            final long[] cancelledBefore = {0L};
            final long now = helper.getLevel().getGameTime();
            // Arm a mid-windup swoop, then save and reload the real entity.
            bat.setBatState(bat.batState()
                .withProvenance(Provenance.MURDEROUS_FLOCK)
                .withAction(Action.SWOOP, Optional.of(prey.getUUID()),
                    Optional.of(helper.getLevel().dimension().identifier().toString()))
                .withDeadlines(new HexBatState.Deadlines(
                    now + 8L, now + 48L, 0L, 0L, 0L, 0L, 0L, 0L
                )));
            bat.setSwooping(true);
            final TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
            );
            bat.saveWithoutId(output);
            final var saved = output.buildResult().copy();
            final HexBatEntity reloaded = (HexBatEntity) ModEntities.ALL.get("hex_bat").get()
                .create(helper.getLevel(), EntitySpawnReason.LOAD);
            helper.assertTrue(reloaded != null, "the registered type must recreate saved state");
            fixture.track(reloaded);
            reloaded.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved
            ));
            helper.assertValueEqual(reloaded.batState().action(), Action.NONE,
                "load cancels the in-progress swoop so damage cannot replay");
            helper.assertFalse(reloaded.isSwooping(), "the synchronized swoop flag clears on load");
            helper.assertTrue(reloaded.getTarget() == null, "no live target survives a load");
            helper.assertValueEqual(reloaded.batState().provenance(), Provenance.MURDEROUS_FLOCK,
                "durable provenance survives the reload");
            helper.assertTrue(reloaded.batState().deadlines().actionRecoverUntil()
                    >= helper.getLevel().getGameTime(),
                "cancellation lands in bounded recovery");
            helper.assertTrue(reloaded.getMainHandItem().isEmpty() && reloaded.getOffhandItem().isEmpty(),
                "equipment normalizes empty on load even from hostile NBT");
            reloaded.discard();

            // Hostile NBT: injected equipment is normalized away.
            final var hostile = saved.copy();
            final HexBatEntity hostileLoaded = (HexBatEntity) ModEntities.ALL.get("hex_bat").get()
                .create(helper.getLevel(), EntitySpawnReason.LOAD);
            fixture.track(hostileLoaded);
            hostileLoaded.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), hostile
            ));
            hostileLoaded.setItemSlot(EquipmentSlot.MAINHAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            hostileLoaded.readAdditionalSaveData(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), hostile
            ));
            helper.assertTrue(hostileLoaded.getMainHandItem().isEmpty(),
                "the shared item-in-hand layer can never show a sword");
            hostileLoaded.discard();

            // Hazard priority: a burning bat cancels its action and escapes.
            bat.setBatState(bat.batState().withAction(Action.NONE, Optional.empty(), Optional.empty()));
            bat.igniteForSeconds(4.0F);
            makeDue(bat);
            helper.runAfterDelay(4L, () -> {
                try {
                    helper.assertValueEqual(bat.batState().mode(), Mode.HAZARD,
                        "fire wins the strict priority order");
                    helper.assertValueEqual(bat.batState().action(), Action.NONE,
                        "hazard cancels any unexecuted action");
                    bat.clearFire();

                    // Low health withdrawal without healing or invulnerability.
                    bat.setHealth(bat.getMaxHealth() * 0.15F);
                    makeDue(bat);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(30L, () -> {
                try {
                    helper.assertValueEqual(bat.batState().mode(), Mode.WITHDRAW,
                        "twenty percent health enters withdrawal");
                    helper.assertTrue(bat.batState().deadlines().withdrawUntil()
                            >= helper.getLevel().getGameTime(),
                        "withdrawal lasts at least one hundred ticks");
                    helper.assertTrue(bat.getHealth() <= bat.getMaxHealth() * 0.15F + 0.01F,
                        "withdrawal never heals");
                    helper.assertFalse(bat.isInvulnerable(), "withdrawal never grants invulnerability");

                    // Structural caps after all live work.
                    final HexBatRuntime.Counters counters = bat.batCounters();
                    helper.assertValueEqual(counters.genericRuntimeDispatches(), 0L,
                        "no generic runtime or navigation writer ever ran");
                    helper.assertTrue(counters.hazardReads()
                            <= (long) HexBatRules.MAX_HAZARD_BLOCK_READS
                                * Math.max(1L, helper.getLevel().getGameTime() - now),
                        "hazard reads stay within twenty-seven per due scan");
                    helper.assertTrue(counters.destinationCandidates() == 0L
                            || counters.destinationBlockReads() <= counters.destinationCandidates() * 3L + 3L,
                        "destination reads stay charged against candidates");
                    helper.assertTrue(counters.navigationFailures() <= counters.navigationRequests(),
                        "failures never exceed requests");

                    // Route failure and backoff contract on the pure gate.
                    HexBatState state = bat.batState().withRouteFailures(0)
                        .withDeadlines(HexBatState.Deadlines.none());
                    helper.assertValueEqual(HexBatRules.routeFailures(2), 3, "third failure saturates");
                    helper.assertTrue(HexBatRules.routeBackoffUntil(3, helper.getLevel().getGameTime())
                            >= helper.getLevel().getGameTime() + HexBatRules.ROUTE_BACKOFF_TICKS,
                        "three failures create at least one hundred ticks of backoff");
                    bat.setBatState(state);

                    // LOS-loss release: heal the bat, move the prey behind a
                    // full partition, and arm a long swoop. The 80-tick unseen
                    // release must cancel it with no effect and no replay.
                    bat.setHealth(bat.getMaxHealth());
                    bat.getNavigation().stop();
                    bat.setDeltaMovement(Vec3.ZERO);
                    final BlockPos batHome = helper.absolutePos(new BlockPos(1, 2, 1));
                    bat.snapTo(batHome.getX() + 0.5D, batHome.getY(), batHome.getZ() + 0.5D, 0.0F, 0.0F);
                    final BlockPos preyAway = helper.absolutePos(new BlockPos(5, 2, 1));
                    prey.snapTo(preyAway.getX() + 0.5D, preyAway.getY(), preyAway.getZ() + 0.5D, 0.0F, 0.0F);
                    prey.setDeltaMovement(Vec3.ZERO);
                    erectPartition(fixture, 3);
                    final long armAt = helper.getLevel().getGameTime();
                    bat.setBatState(bat.batState()
                        .withMode(Mode.SHELTER)
                        .withAction(Action.SWOOP, Optional.of(prey.getUUID()),
                            Optional.of(helper.getLevel().dimension().identifier().toString()))
                        .withDeadlines(new HexBatState.Deadlines(
                            armAt + 8L, armAt + 300L, 0L, 0L, 0L, 0L, 0L, 0L
                        )));
                    bat.setSwooping(true);
                    cancelledBefore[0] = bat.batCounters().actionsCancelled();
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(150L, () -> {
                try {
                    helper.assertValueEqual(bat.batState().action(), Action.NONE,
                        "the 80-tick unseen release cancels a walled-off swoop");
                    helper.assertTrue(bat.batCounters().actionsCancelled() > cancelledBefore[0],
                        "LOS loss counts as a cancellation, not a timeout or contact");
                    helper.assertFalse(prey.hasEffect(MobEffects.UNLUCK),
                        "a target that was never seen again receives no jinx");
                    helper.assertFalse(bat.isSwooping(),
                        "the synchronized swoop pose clears with the cancelled action");
                } finally {
                    fixture.close();
                }
                helper.succeed();
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---- shared fixture plumbing ----

    @SuppressWarnings("unchecked")
    private static HexBatEntity spawnBat(final FixtureScope fixture, final BlockPos position) {
        final GameTestHelper helper = fixture.helper;
        final Entity spawned = helper.spawn(
            (EntityType<? extends net.minecraft.world.entity.Mob>) ModEntities.ALL.get("hex_bat").get(),
            position, EntitySpawnReason.EVENT
        );
        final HexBatEntity bat = (HexBatEntity) spawned;
        fixture.track(bat);
        bat.setDeltaMovement(Vec3.ZERO);
        final BlockPos absolute = helper.absolutePos(position);
        bat.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
        return bat;
    }

    private static void makeDue(final HexBatEntity bat) {
        bat.setBatState(bat.batState().withCadence(HexBatState.Cadence.due()));
    }

    /**
     * Full interior partition at the given relative x plane: breaks both
     * line of sight and every route between the two sides of the shell.
     * Only previously-air positions are placed and all are restored on close.
     */
    private static void erectPartition(final FixtureScope fixture, final int relativeX) {
        final GameTestHelper helper = fixture.helper;
        final List<BlockPos> placed = new ArrayList<>();
        for (int dy = 1; dy <= 6; dy++) {
            for (int dz = -3; dz <= 5; dz++) {
                final BlockPos pos = helper.absolutePos(new BlockPos(relativeX, dy, dz));
                if (helper.getLevel().getBlockState(pos).isAir()) {
                    helper.getLevel().setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
                    placed.add(pos);
                }
            }
        }
        fixture.onClose(() -> placed.forEach(pos -> helper.getLevel().setBlock(
            pos, Blocks.AIR.defaultBlockState(), 3
        )));
    }

    /**
     * Barrier shell: the local scan radii exceed the 8-10 block batch grid,
     * so every fixture is walled with pass-local barriers and asserts through
     * entities it spawned itself.
     */
    private static void erectBarrierShell(final FixtureScope fixture, final BlockPos centerRelative) {
        final GameTestHelper helper = fixture.helper;
        final BlockPos center = helper.absolutePos(centerRelative);
        final int radius = 5;
        final int height = 6;
        final List<BlockPos> placed = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                final boolean wall = Math.abs(dx) == radius || Math.abs(dz) == radius;
                for (int dy = -1; dy <= height; dy++) {
                    final boolean cap = dy == -1 || dy == height;
                    if (!wall && !cap) continue;
                    final BlockPos pos = new BlockPos(
                        center.getX() + dx, center.getY() + dy, center.getZ() + dz
                    );
                    if (helper.getLevel().getBlockState(pos).isAir()) {
                        helper.getLevel().setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
                        placed.add(pos);
                    }
                }
            }
        }
        fixture.onClose(() -> placed.forEach(pos -> helper.getLevel().setBlock(
            pos, Blocks.AIR.defaultBlockState(), 3
        )));
    }

    private static final class FixtureScope implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<Entity> entities = new ArrayList<>();
        private final List<Runnable> cleanupActions = new ArrayList<>();
        private boolean closed;

        private FixtureScope(final GameTestHelper helper) {
            this.helper = helper;
        }

        private <T extends Entity> T spawn(
            final EntityType<T> type,
            final BlockPos position,
            final EntitySpawnReason reason
        ) {
            return track(helper.spawn(type, position, reason));
        }

        private <T extends Entity> T track(final T entity) {
            entities.add(entity);
            return entity;
        }

        private void onClose(final Runnable action) {
            cleanupActions.add(action);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            entities.forEach(Entity::discard);
            entities.clear();
            cleanupActions.forEach(Runnable::run);
            cleanupActions.clear();
        }
    }
}
