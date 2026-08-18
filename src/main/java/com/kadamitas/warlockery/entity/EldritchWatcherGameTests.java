package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.EldritchWatcherRules.ActionType;
import com.kadamitas.warlockery.entity.EldritchWatcherRules.EvidenceType;
import com.kadamitas.warlockery.entity.EldritchWatcherRules.Mode;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.util.GameTestAssertions;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public final class EldritchWatcherGameTests {
    private EldritchWatcherGameTests() {
    }

    public static void vigilObservesAndEscalatesOnReciprocalGaze(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final EldritchWatcherEntity watcher = spawnWatcher(fixture, new BlockPos(1, 1, 1));
            helper.assertValueEqual(watcher.creatureKind(), CreatureKind.ELDRITCH_WATCHER,
                "the public creature kind stays exact");
            for (final String forbidden : List.of("VexChargeAttackGoal", "VexRandomMoveGoal")) {
                helper.assertFalse(watcher.operationalGoalNames().stream()
                        .anyMatch(name -> name.equals(forbidden)),
                    "inherited Vex combat or wander goal must be removed: " + forbidden);
            }
            helper.assertValueEqual(watcher.operationalTargetGoalCount(), 0,
                "no inherited nearest-player, retaliation, or copy-owner target goal survives");
            for (final EquipmentSlot slot : EquipmentSlot.values()) {
                helper.assertTrue(watcher.getItemBySlot(slot).isEmpty(),
                    "equipment normalizes empty: " + slot);
            }

            makeDue(watcher);
            EldritchWatcherRuntime.tick(watcher, helper.getLevel());
            helper.assertTrue(watcher.watcherState().anchor().isPresent(),
                "the first valid loaded position becomes the soft vigil anchor");

            final Zombie stranger = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 0), EntitySpawnReason.EVENT);
            stranger.setNoAi(true);
            faceAway(stranger, watcher);
            makeDue(watcher);
            EldritchWatcherRuntime.tick(watcher, helper.getLevel());
            helper.assertTrue(watcher.getTarget() == null,
                "presence alone never creates immediate nearest-player style hostility");
            helper.assertFalse(EldritchWatcherRuntime.eligibleTarget(watcher, stranger),
                "an unescalated observed subject is not an eligible attack target");

            faceToward(stranger, watcher);
            helper.assertTrue(EldritchWatcherRuntime.gazeTowardWatcher(watcher, stranger),
                "the oriented subject satisfies the reciprocal gaze dot threshold");
            makeDue(watcher);
            EldritchWatcherRuntime.tick(watcher, helper.getLevel());
            final EldritchWatcherState afterOne = watcher.watcherState();
            GameTestAssertions.assertPresentValueEqual(
                helper, afterOne.evidenceType(), EvidenceType.RECIPROCAL_GAZE,
                "one reciprocal sample records gaze evidence");
            helper.assertFalse(EldritchWatcherRuntime.eligibleTarget(watcher, stranger),
                "one reciprocal sample never escalates");
            makeDue(watcher);
            EldritchWatcherRuntime.tick(watcher, helper.getLevel());
            helper.assertTrue(watcher.watcherState().attentionSamples() >= EldritchWatcherRules.ESCALATION_SAMPLES,
                "two consecutive reciprocal samples reach the escalation threshold");
            helper.assertTrue(EldritchWatcherRuntime.eligibleTarget(watcher, stranger),
                "a two-sample reciprocal gaze subject becomes an eligible target");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void revelationIsBoundVisibleAndAttributed(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final long now = helper.getLevel().getGameTime();
            final EldritchWatcherEntity watcher = spawnWatcher(fixture, new BlockPos(1, 1, 1));
            makeDue(watcher);
            EldritchWatcherRuntime.tick(watcher, helper.getLevel());
            final Zombie victim = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2), EntitySpawnReason.EVENT);
            victim.setNoAi(true);
            watcher.setWatcherState(watcher.watcherState().withSubject(
                Optional.of(victim.getUUID()), Optional.of(EvidenceType.DIRECT_HARM),
                now + EldritchWatcherRules.SEEN_EVIDENCE_TICKS, EldritchWatcherRules.ESCALATION_SAMPLES
            ));
            makeDue(watcher);
            EldritchWatcherRuntime.tick(watcher, helper.getLevel());
            final EldritchWatcherState armed = watcher.watcherState();
            helper.assertValueEqual(armed.action(), ActionType.REVELATION,
                "an escalated intercept begins the telegraphed revelation");
            GameTestAssertions.assertPresentValueEqual(
                helper, armed.actionTargetId(), victim.getUUID(),
                "the action target is stored immutably at start");
            helper.assertTrue(armed.actionExecuteAt()
                    >= now + EldritchWatcherRules.REVELATION_WINDUP_TICKS,
                "the windup is at least twenty ticks");

            final float healthBefore = victim.getHealth();
            watcher.setWatcherState(armed.withAction(
                ActionType.REVELATION, armed.actionTargetId(), armed.actionDimension(),
                Math.max(1L, helper.getLevel().getGameTime()), 0L
            ));
            makeDue(watcher);
            EldritchWatcherRuntime.tick(watcher, helper.getLevel());
            helper.assertTrue(victim.getHealth() <= healthBefore - EldritchWatcherRules.REVELATION_DAMAGE + 0.01F,
                "an accepted visible revelation deals exactly three attributed magic damage before mitigation");
            helper.assertTrue(victim.hasEffect(MobEffects.GLOWING),
                "accepted revelation applies Glowing");
            helper.assertValueEqual(watcher.watcherState().action(), ActionType.NONE,
                "the executed action enters recovery");
            helper.assertTrue(watcher.watcherState().actionRecoverUntil()
                    >= helper.getLevel().getGameTime() + EldritchWatcherRules.REVELATION_RECOVERY_TICKS - 1,
                "recovery is at least fifty ticks");

            final Zombie hidden = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 0), EntitySpawnReason.EVENT);
            hidden.setNoAi(true);
            final float hiddenBefore = hidden.getHealth();
            watcher.setWatcherState(watcher.watcherState().withAction(
                ActionType.REVELATION, Optional.of(hidden.getUUID()),
                Optional.of(helper.getLevel().dimension().identifier().toString()),
                Math.max(1L, helper.getLevel().getGameTime()), 0L
            ));
            hidden.discard();
            makeDue(watcher);
            EldritchWatcherRuntime.tick(watcher, helper.getLevel());
            helper.assertValueEqual(watcher.watcherState().action(), ActionType.NONE,
                "a missing immutable target cancels the due action without damage");
            helper.assertTrue(watcher.watcherCounters().actionCancellations() >= 1,
                "the cancellation is counted rather than silently retargeted");
            helper.assertTrue(hiddenBefore == hidden.getHealth(),
                "a cancelled revelation never deals damage");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void bindingWarningLureAndReturnRemainLocal(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final boolean[] scheduled = {false};
        try {
            final EldritchWatcherEntity watcher = spawnWatcher(fixture, new BlockPos(1, 1, 1));
            makeDue(watcher);
            EldritchWatcherRuntime.tick(watcher, helper.getLevel());
            final UUID ownerId = UUID.randomUUID();
            helper.assertTrue(CreatureBehaviorState.bind(watcher, ownerId),
                "the existing Warlockery owner key binds the living Watcher");
            final Zombie ownerStandIn = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(0, 1, 0), EntitySpawnReason.EVENT);
            ownerStandIn.setNoAi(true);

            final List<EldritchWatcherEntity> peers = new ArrayList<>();
            for (int index = 0; index < 5; index++) {
                final EldritchWatcherEntity peer = spawnWatcher(fixture, new BlockPos(2, 1, index % 3));
                CreatureBehaviorState.bind(peer, ownerId);
                peers.add(peer);
            }
            final Zombie attacker = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(0, 1, 2), EntitySpawnReason.EVENT);
            attacker.setNoAi(true);
            watcher.invulnerableTime = 0;
            helper.assertTrue(watcher.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 1.0F
            ), "the warning fixture needs one real accepted hit");
            GameTestAssertions.assertPresentValueEqual(
                helper, watcher.watcherState().threatId(), attacker.getUUID(),
                "accepted direct harm records the direct threat");
            helper.assertTrue(watcher.watcherCounters().warningRecipients()
                    <= EldritchWatcherRules.MAX_WARNING_RECIPIENTS,
                "at most three compatible recipients receive the one-hop warning");
            helper.assertTrue(watcher.watcherCounters().warningVisits()
                    <= EldritchWatcherRules.MAX_WARNING_VISITS,
                "at most eight Watcher candidates are visited");
            final long informed = peers.stream()
                .filter(peer -> peer.watcherState().threatId().isPresent())
                .count();
            helper.assertTrue(informed <= EldritchWatcherRules.MAX_WARNING_RECIPIENTS,
                "recipients beyond the cap stay uninformed");
            for (final EldritchWatcherEntity peer : peers) {
                helper.assertFalse(EldritchWatcherRuntime.eligibleTarget(peer, attacker)
                        && !peer.getSensing().hasLineOfSight(attacker),
                    "reported harm never authorizes attack without independent sight");
            }

            final EldritchWatcherEntity idle = spawnWatcher(fixture, new BlockPos(0, 1, 1));
            makeDue(idle);
            EldritchWatcherRuntime.tick(idle, helper.getLevel());
            final BlockPos lure = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.assertTrue(idle.acceptExternalLure(helper.getLevel(), lure),
                "an idle Watcher accepts a nearby semantic lure");
            helper.assertTrue(idle.watcherState().lure().isPresent(),
                "the lure stores one bounded local site");
            helper.assertTrue(idle.watcherState().lure().orElseThrow().expiresAt()
                    <= helper.getLevel().getGameTime() + EldritchWatcherRules.LURE_TICKS,
                "the lure expires within forty ticks");
            final BlockPos remote = helper.absolutePos(new BlockPos(1, 1, 1)).offset(48, 0, 0);
            helper.assertFalse(idle.acceptExternalLure(helper.getLevel(), remote),
                "a lure outside sixteen blocks is refused");

            final EldritchWatcherEntity guard = spawnWatcher(fixture, new BlockPos(1, 1, 0));
            makeDue(guard);
            EldritchWatcherRuntime.tick(guard, helper.getLevel());
            helper.assertTrue(CreatureBehaviorState.bind(guard, ownerStandIn.getUUID()),
                "the guard fixture binds the Watcher to a present living owner");
            ownerStandIn.invulnerableTime = 0;
            helper.assertTrue(ownerStandIn.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 1.0F
            ), "the guard fixture needs one real accepted hit on the owner");
            makeDue(guard);
            // A full perception cadence, not two ticks. The scan runs on a twenty tick interval
            // that each body staggers from its own id, so a two tick window only ever passed when
            // the guard happened to draw a low offset. The harm stays reported for eighty ticks,
            // so waiting one whole cadence still asserts the same thing: that a self-ticking bound
            // Watcher recorded this exact attacker.
            helper.runAfterDelay(EldritchWatcherRules.PERCEPTION_INTERVAL_TICKS, () -> {
                try {
                    GameTestAssertions.assertPresentValueEqual(
                        helper, guard.watcherState().threatId(),
                        attacker.getUUID(),
                        "the self-ticking bound Watcher observes fresh direct harm to its owner");
                    GameTestAssertions.assertPresentValueEqual(helper, 
                        guard.watcherState().evidenceType(),
                        EldritchWatcherRules.EvidenceType.DIRECT_HARM,
                        "owner-guard harm records direct guard evidence");
                    helper.assertTrue(EldritchWatcherRuntime.eligibleTarget(guard, attacker),
                        "the observed owner attacker becomes an eligible target");
                    helper.assertFalse(EldritchWatcherRuntime.eligibleTarget(guard, ownerStandIn),
                        "the owner itself is never an eligible target");
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
            scheduled[0] = true;
        } finally {
            if (!scheduled[0]) {
                fixture.close();
            }
        }
    }

    public static void saveReloadFocusHazardAndWorkAreBounded(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final EldritchWatcherEntity watcher = spawnWatcher(fixture, new BlockPos(1, 1, 1));
            makeDue(watcher);
            EldritchWatcherRuntime.tick(watcher, helper.getLevel());
            final Zombie subject = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2), EntitySpawnReason.EVENT);
            subject.setNoAi(true);
            watcher.setWatcherState(watcher.watcherState().withSubject(
                Optional.of(subject.getUUID()), Optional.of(EvidenceType.DIRECT_HARM),
                helper.getLevel().getGameTime() + EldritchWatcherRules.SEEN_EVIDENCE_TICKS,
                EldritchWatcherRules.ESCALATION_SAMPLES
            ));
            makeDue(watcher);
            EldritchWatcherRuntime.tick(watcher, helper.getLevel());
            helper.assertValueEqual(watcher.watcherState().action(), ActionType.REVELATION,
                "the reload fixture arms one pending action");

            final TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
            );
            watcher.saveWithoutId(output);
            final var saved = output.buildResult().copy();
            final EldritchWatcherEntity loaded = spawnWatcher(fixture, new BlockPos(1, 1, 0));
            loaded.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved
            ));
            helper.assertTrue(loaded.watcherState().anchor().isPresent(),
                "the saved anchor survives reload");
            helper.assertTrue(loaded.watcherState().mode() != Mode.INTERCEPTING,
                "transient interception never survives reload");
            for (final EquipmentSlot slot : EquipmentSlot.values()) {
                helper.assertTrue(loaded.getItemBySlot(slot).isEmpty(),
                    "reload normalizes equipment empty: " + slot);
            }

            final EldritchWatcherEntity legacy = spawnWatcher(fixture, new BlockPos(0, 1, 2));
            final var legacyTag = saved.copy();
            legacyTag.remove("WarlockeryEldritchWatcher");
            legacy.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), legacyTag
            ));
            makeDue(legacy);
            EldritchWatcherRuntime.tick(legacy, helper.getLevel());
            helper.assertTrue(legacy.watcherState().anchor().isPresent(),
                "a legacy compound-free Watcher establishes a fresh anchor on first loaded tick");
            helper.assertValueEqual(legacy.watcherState().action(), ActionType.NONE,
                "a legacy Watcher starts in quiet vigil with no action");

            subject.discard();
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.LECTERN);
            final EldritchWatcherEntity scholar = spawnWatcher(fixture, new BlockPos(0, 1, 0));
            makeDue(scholar);
            EldritchWatcherRuntime.tick(scholar, helper.getLevel());
            makeDue(scholar);
            EldritchWatcherRuntime.tick(scholar, helper.getLevel());
            helper.assertTrue(scholar.watcherState().focus().isPresent(),
                "an idle Watcher finds one tagged knowledge focus on its own layer");
            helper.assertTrue(scholar.watcherCounters().focusBlockReads()
                    <= 2L * EldritchWatcherRules.MAX_FOCUS_BLOCK_READS,
                "each focus search charges at most one hundred twenty-eight block states");
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
            makeDue(scholar);
            EldritchWatcherRuntime.tick(scholar, helper.getLevel());
            helper.assertTrue(scholar.watcherState().focus().isEmpty()
                    || scholar.watcherState().mode() != Mode.FOCUS_INSPECTION,
                "a destroyed focus cancels inspection");

            helper.assertTrue(watcher.watcherCounters().entityVisits()
                    <= watcher.watcherCounters().perceptionScans() * EldritchWatcherRules.MAX_ENTITIES_VISITED,
                "perception visits stay within sixteen entities per scan");
            helper.assertTrue(watcher.watcherCounters().lineOfSightClips()
                    <= watcher.watcherCounters().perceptionScans() * EldritchWatcherRules.MAX_LINE_OF_SIGHT_CLIPS
                        + watcher.watcherCounters().executedActions() * 4,
                "line-of-sight clips stay within the perception budget");
            final EldritchWatcherEntity steady = spawnWatcher(fixture, new BlockPos(2, 1, 0));
            for (int tick = 0; tick < 10; tick++) {
                EldritchWatcherRuntime.tick(steady, helper.getLevel());
            }
            helper.assertTrue(steady.watcherCounters().hazardBlockReads()
                    <= EldritchWatcherRules.MAX_HAZARD_BLOCK_READS,
                "ten consecutive no-hazard ticks at one game time charge exactly one 27-read scan, "
                    + "proving the hazard cadence is armed on every scan");
            helper.assertTrue(steady.watcherCounters().hazardBlockReads() > 0,
                "the first due tick performs the bounded local hazard scan");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static EldritchWatcherEntity spawnWatcher(final FixtureScope fixture, final BlockPos position) {
        final GameTestHelper helper = fixture.helper;
        final EntityType<? extends Vex> type =
            (EntityType<? extends Vex>) ModEntities.ALL.get("eldritch_watcher").get();
        final EldritchWatcherEntity watcher = new EldritchWatcherEntity(type, helper.getLevel());
        final BlockPos absolute = helper.absolutePos(position);
        watcher.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
        watcher.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(watcher);
        return fixture.track(watcher);
    }

    private static void makeDue(final EldritchWatcherEntity watcher) {
        watcher.setWatcherState(watcher.watcherState().withCadence(
            new EldritchWatcherState.Cadence(0L, 0L, 0L, 0L, 0L)
        ));
    }

    private static void faceToward(final Zombie subject, final EldritchWatcherEntity watcher) {
        final Vec3 delta = watcher.getEyePosition().subtract(subject.getEyePosition());
        final double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        final float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D);
        final float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        subject.snapTo(subject.getX(), subject.getY(), subject.getZ(), yaw, pitch);
        subject.setYHeadRot(yaw);
    }

    private static void faceAway(final Zombie subject, final EldritchWatcherEntity watcher) {
        faceToward(subject, watcher);
        final float yaw = subject.getYRot() + 180.0F;
        subject.snapTo(subject.getX(), subject.getY(), subject.getZ(), yaw, 0.0F);
        subject.setYHeadRot(yaw);
    }

    private static final class FixtureScope implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<Entity> entities = new ArrayList<>();
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

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            entities.forEach(GameTestMockPlayers::release);
            entities.clear();
        }
    }
}
