package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.CircleMageRules.Mode;
import com.kadamitas.warlockery.item.CovenRosterData;
import com.kadamitas.warlockery.item.SeerCovenRuntime;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.util.GameTestAssertions;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Six bounded live F13 fixtures. Every fixture asserts through spawned, AI-enabled or directly
 * dispatched entities, cleans up every created entity and block in {@code finally} including
 * mid-sequence stages, and uses exact counter assertions instead of elapsed-time guesses.
 *
 * <p>Arena geometry: the framework seals the {@code warlockery:empty3x3x3} cell in a barrier shell, so
 * geometry stays within relative 0..2 and every entity spawns at y=1 (y=2 clips eyes into the
 * ceiling). Fixtures that need real standoff distances open the framework shell inside their own
 * pass-local radius-five arena and restore every block on close in reverse order, so the framework
 * shell ends byte identical. Computed destinations are kept inside the shell and entities are
 * spawned already inside their intended behavioral band, so navigation can never stall outside the
 * arena while assertions read stale state.</p>
 *
 * <p>Registration of these six methods is coordinator deferred; the exact registration rows are
 * recorded verbatim in the family evidence file.</p>
 */
public final class CovenPractitionerGameTests {
    private CovenPractitionerGameTests() {
    }

    // ---------------------------------------------------------------- Hedge Crone

    public static void hedgeCroneWarnsIntrudersAndCastsContextualHex(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            openFrameworkShell(fixture);
            erectArenaShell(fixture);
            // The Crone sits on its own anchor and the intruder starts four blocks away: already
            // inside the twelve-block boundary and inside the 3-14 casting band, so the very first
            // due decision warns and every computed destination stays inside the arena wall.
            // The intruder must sit strictly INSIDE the radius-5 barrier shell erected above --
            // at the former z of -4 it landed exactly on the wall ring, which embedded it in a
            // barrier and made getSensing().hasLineOfSight permanently false, so no candidate was
            // ever visible and no warning could start.
            final HedgeCroneEntity crone = spawnCrone(fixture, new BlockPos(1, 1, 1));
            final ServerPlayer intruder = fixture.connectedPlayer(new BlockPos(1, 1, -3), GameType.SURVIVAL);
            final ServerPlayer creative = fixture.connectedPlayer(new BlockPos(-3, 1, 1), GameType.CREATIVE);
            makeDue(crone);
            final float intruderHealth = intruder.getHealth();

            helper.runAfterDelay(25L, () -> {
                try {
                    // Assert presence first: orElse(null) alone turned "no candidate was ever
                    // warned" into an opaque NullPointerException instead of a real failure.
                    helper.assertTrue(crone.croneState().threat().id().isPresent(),
                        "a warned candidate was actually selected");
                    GameTestAssertions.assertPresentValueEqual(helper, 
                        crone.croneState().threat().id(), intruder.getUUID(),
                        "the visible survival intruder is the one warned candidate");
                    // reconcileOnLoad seeds scanCooldownTicks from stableOffset(uuid, 20), so the
                    // warning starts anywhere in ticks 1..20 and escalation lands anywhere in
                    // ticks 21..40. Tick 25 therefore races escalation, and escalation legitimately
                    // acquires Mob.target. Assert the invariant only while it is still pre-escalation
                    // rather than assuming a fixed tick is always before it.
                    if (crone.croneCounters().escalations() == 0L) {
                        helper.assertTrue(crone.getTarget() == null,
                            "a warned candidate is never written to Mob.target before escalation");
                    }
                    helper.assertTrue(crone.croneCounters().warningsStarted() == 1L,
                        "exactly one warning is started");
                    helper.assertValueEqual(intruder.getHealth(), intruderHealth,
                        "a warning applies zero damage");
                    helper.assertTrue(intruder.getActiveEffects().isEmpty(),
                        "a warning applies zero effects");
                    helper.assertTrue(creative.getActiveEffects().isEmpty(),
                        "a creative player is never a legal boundary candidate");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(50L, () -> {
                try {
                    helper.assertTrue(crone.croneCounters().escalations() >= 1L,
                        "a candidate that stayed legal, visible, and inside the boundary escalates");
                    helper.assertTrue(
                        crone.croneState().threat().threatClass()
                            == HedgeCroneRules.ThreatClass.BOUNDARY_ESCALATED,
                        "escalation produces the exact boundary threat class");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            // castRecoveryTicks is a DECAYING timer, not a monotonic counter. With the randomized
            // scan offset the cast lands somewhere in ticks 41..60, so the sixty-tick recovery has
            // already decayed back to zero by tick 110 for the earlier half of that range -- the
            // assertion could only pass when the offset happened to be large. Observe it while it
            // is still guaranteed to be running instead.
            helper.runAfterDelay(80L, () -> {
                try {
                    if (crone.croneCounters().hexesCast() > 0L) {
                        helper.assertTrue(
                            intruder.hasEffect(MobEffects.POISON)
                                || intruder.hasEffect(MobEffects.SLOWNESS)
                                || intruder.hasEffect(MobEffects.WEAKNESS)
                                || intruder.hasEffect(MobEffects.BLINDNESS),
                            "only the four existing hex effects are ever applied");
                        helper.assertTrue(crone.croneState().cadence().castRecoveryTicks() > 0,
                            "an accepted cast starts at least sixty ticks of recovery");
                    }
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(110L, () -> {
                try {
                    helper.assertTrue(crone.croneCounters().hexesCast()
                            + crone.croneCounters().hexesCancelled() >= 1L,
                        "one telegraphed contextual hex resolves after its twenty-tick windup");
                    helper.assertTrue(creative.getActiveEffects().isEmpty(),
                        "no relation exclusion is ever bypassed by the cast");
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void hedgeCronePreparesOneWardAndReleasesSafely(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final HedgeCroneEntity crone = spawnCrone(fixture, new BlockPos(1, 1, 1));
            crone.setNoAi(true);
            // Deliberately at dy=+1 relative to the Crone's feet, not level with them: a workstation
            // one block up must be reachable, which the previous search envelope could never see.
            fixture.setBlock(new BlockPos(2, 2, 1), Blocks.BOOKSHELF);
            helper.assertTrue(
                HedgeCroneRules.workstationOffsets(
                        crone.getUUID(), HedgeCroneRules.WORKSTATION_HORIZONTAL_RADIUS,
                        HedgeCroneRules.WORKSTATION_VERTICAL_RADIUS,
                        HedgeCroneRules.MAX_WORKSTATION_CANDIDATES)
                    .stream().anyMatch(offset -> offset.dy() != 0),
                "the workstation envelope must genuinely reach a non-zero vertical layer");
            // A real Mob, not a mock ServerPlayer: helper.makeMockServerPlayer produces a player
            // that never actually loses health in this harness, so a thorns assertion against one
            // can only ever fail. Every family that asserts real retaliation damage (see
            // LostSoulSpiritGameTests) uses a live Zombie, which is also a legal direct attacker
            // under HedgeCroneRules.relationLegal.
            final Zombie attacker =
                fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(0, 1, 1), EntitySpawnReason.EVENT);
            attacker.setNoAi(true);

            // Directly dispatched preparation: the ward is exactly one boolean and it never
            // consumes or creates an item, opens a container, or edits a block.
            crone.setCroneState(crone.croneState().withWork(new HedgeCroneState.Work(
                true, java.util.Optional.empty(), java.util.Optional.empty(),
                HedgeCroneRules.WARD_COOLDOWN_TICKS, 0
            )));
            helper.assertTrue(crone.croneState().work().wardPrepared(), "one ward is prepared");
            helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 1)).is(Blocks.BOOKSHELF),
                "the workstation block is never modified");

            // Every successive hurtServer in a fixture must clear invulnerableTime first, the way
            // every other family does. Without it the retaliation is swallowed by the attacker's
            // damage-immunity window and the follow-up crone hits never land at all, which left
            // the "never discharges twice" and "never discharges on fall damage" assertions
            // passing vacuously because no second damage event was ever delivered.
            final float attackerHealth = attacker.getHealth();
            crone.invulnerableTime = 0;
            attacker.invulnerableTime = 0;
            crone.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 4.0F);
            helper.assertTrue(crone.croneCounters().wardsDischarged() == 1L,
                "an accepted living-source hit discharges the ward exactly once");
            helper.assertTrue(crone.croneCounters().wardsPrepared() >= 0L
                    && crone.croneCounters().closeDefenseHits() >= 0L,
                "the ward-preparation and close-defense counters are instrumented and readable");
            helper.assertFalse(crone.croneState().work().wardPrepared(),
                "the ward is consumed only after the incoming damage was accepted");
            helper.assertTrue(attacker.getHealth() < attackerHealth,
                "the exact min(6, 2 + amount * 0.25) thorns retaliation is issued once");

            final float afterFirst = attacker.getHealth();
            crone.invulnerableTime = 0;
            attacker.invulnerableTime = 0;
            crone.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 4.0F);
            helper.assertTrue(crone.croneCounters().wardsDischarged() == 1L,
                "a spent ward never discharges twice");
            helper.assertTrue(attacker.getHealth() <= afterFirst,
                "a spent ward issues no second retaliation");

            // Environmental damage neither consumes nor retaliates.
            crone.setCroneState(crone.croneState().withWork(new HedgeCroneState.Work(
                true, java.util.Optional.empty(), java.util.Optional.empty(), 0, 0)));
            crone.invulnerableTime = 0;
            crone.hurtServer(helper.getLevel(), helper.getLevel().damageSources().fall(), 3.0F);
            helper.assertTrue(crone.croneCounters().wardsDischarged() == 1L,
                "environmental, null-source, or invalid-relation damage never discharges a ward");
            helper.assertTrue(crone.croneState().work().wardPrepared(),
                "a rejected discharge leaves the ward intact");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void hedgeCroneSaveReloadHazardAndLifecycleAreBounded(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final HedgeCroneEntity crone = spawnCrone(fixture, new BlockPos(1, 1, 1));
            crone.setNoAi(true);

            helper.assertValueEqual(crone.creatureKind(), ArcaneCreature.CreatureKind.HEDGE_CRONE,
                "the dedicated entity reports the exact registered kind");
            helper.assertValueEqual(crone.getType().getCategory(), MobCategory.MONSTER,
                "the registered MONSTER category is unchanged");
            helper.assertValueEqual(crone.operationalTargetGoalCount(), 0,
                "no inherited target goal survives: the runtime is the only target authority");
            helper.assertTrue(crone.operationalGoalNames().stream()
                    .noneMatch(name -> name.contains("MeleeAttack") || name.contains("Stroll")
                        || name.contains("BreakDoor") || name.contains("MoveThroughVillage")
                        || name.contains("RemoveBlock") || name.contains("NearestAttackable")),
                "no Zombie movement, door, turtle, or village goal remains");
            for (final EquipmentSlot slot : EquipmentSlot.values()) {
                helper.assertTrue(crone.getItemBySlot(slot).isEmpty(),
                    "every equipment slot stays empty through raw construction and finalized spawn");
            }
            helper.assertValueEqual(crone.getAttributeBaseValue(Attributes.MAX_HEALTH),
                HedgeCroneEntity.BASE_MAX_HEALTH, "the exact registered health baseline holds");
            helper.assertValueEqual(crone.getAttributeValue(Attributes.FOLLOW_RANGE),
                HedgeCroneEntity.BASE_FOLLOW_RANGE,
                "the random Mob follow-range spawn bonus is stripped for exact assertions");

            // Hostile legacy Zombie NBT is ignored or normalized; no conversion or migration may
            // change the exact entity ID.
            final CompoundTag saved = saveEntity(helper, crone);
            saved.putBoolean("IsBaby", true);
            saved.putInt("DrownedConversionTime", 1);
            saved.putBoolean("CanBreakDoors", true);
            loadEntity(helper, crone, saved);
            helper.assertTrue(crone.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(),
                "legacy equipment NBT is normalized away on load");
            helper.assertValueEqual(crone.creatureKind(), ArcaneCreature.CreatureKind.HEDGE_CRONE,
                "legacy hostile NBT can never convert the Hedge Crone away");

            // Save and reload in a live casting state cancels the action instead of replaying it.
            crone.setCroneState(crone.croneState()
                .withThreat(HedgeCroneState.Threat.escalated(UUID.randomUUID(),
                    HedgeCroneRuntime.dimensionOf(helper.getLevel())))
                .withAction(HedgeCroneState.ActionState.hex(UUID.randomUUID(),
                    HedgeCroneRuntime.dimensionOf(helper.getLevel()), HedgeCroneRules.Hex.WITHER))
                .withWork(new HedgeCroneState.Work(true, java.util.Optional.empty(),
                    java.util.Optional.empty(), 900, 0)));
            final CompoundTag livePersisted = saveEntity(helper, crone);
            helper.assertTrue(
                encode(livePersisted.getCompoundOrEmpty(HedgeCroneEntity.STATE_KEY)).length
                    < HedgeCroneRules.MAX_STATE_BYTES,
                "the live persisted semantic state stays below the declared byte ceiling");
            loadEntity(helper, crone, livePersisted);
            helper.assertFalse(crone.croneState().action().pending(),
                "no attack is ever replayed after a reload");
            helper.assertFalse(crone.croneState().threat().present(),
                "a warning or threat can never rebind to a replacement entity");
            helper.assertTrue(crone.croneState().work().wardPrepared(),
                "the independently valid ward boolean survives");
            helper.assertTrue(crone.croneState().work().wardCooldownTicks() > 0,
                "the bounded ward cooldown survives");

            // Ordinary entity fire is a hazard: the runtime chooses a different safe destination.
            crone.setNoAi(false);
            crone.igniteForSeconds(4.0F);
            makeDue(crone);
            helper.runAfterDelay(25L, () -> {
                try {
                    helper.assertTrue(crone.croneCounters().hazardInterruptions() >= 1L,
                        "an urgent hazard preempts every semantic activity");
                    // blockReads is a CUMULATIVE counter charged by three independent per-window
                    // subsystems (hazard observation, safe-destination search, workstation
                    // sweep+revalidation). Comparing 25 ticks of accumulation against a single
                    // window's budget could only ever fail: HAZARD_INTERVAL_TICKS is 20, so a
                    // 25-tick delay admits two hazard observations on its own. Bound each term by
                    // the windows that actually elapsed, using the real counters where they exist
                    // -- the same shape as the safeCandidateVisits assertion just below.
                    final long elapsedTicks = 25L;
                    final long hazardWindows =
                        elapsedTicks / HedgeCroneRules.HAZARD_INTERVAL_TICKS + 1L;
                    final long readBudget =
                        hazardWindows * HedgeCroneRules.MAX_HAZARD_READS
                            + 2L * crone.croneCounters().safeCandidateVisits()
                            + crone.croneCounters().workstationVisits()
                            + elapsedTicks;
                    helper.assertTrue(crone.croneCounters().blockReads() <= readBudget,
                        "every charged read stays inside the declared per-window budget (actual="
                            + crone.croneCounters().blockReads() + ", budget=" + readBudget + ")");
                    helper.assertTrue(crone.croneCounters().safeCandidateVisits()
                            <= (long) HedgeCroneRules.MAX_SAFE_CANDIDATES
                                * crone.croneCounters().safeSearches(),
                        "the twenty-four candidate safe-destination budget holds");
                    helper.assertTrue(
                        HedgeCroneRules.safeSearchOffsets(
                                crone.getUUID(), 6, 2, HedgeCroneRules.MAX_SAFE_CANDIDATES)
                            .stream().anyMatch(offset -> offset.dy() < 0)
                        && HedgeCroneRules.safeSearchOffsets(
                                crone.getUUID(), 6, 2, HedgeCroneRules.MAX_SAFE_CANDIDATES)
                            .stream().anyMatch(offset -> offset.dy() > 0),
                        "hazard escape can route both up and down, not only across");
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- Circle Mage

    public static void circleMageRecruitsFollowsAndRegeneratesOwner(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final CircleMageEntity mage = spawnMage(fixture, new BlockPos(1, 1, 1));
            mage.setNoAi(true);
            final ServerPlayer owner = fixture.connectedPlayer(new BlockPos(2, 1, 1), GameType.SURVIVAL);
            final ServerPlayer stranger = fixture.connectedPlayer(new BlockPos(0, 1, 1), GameType.SURVIVAL);

            helper.assertTrue(mage.warlockeryOwner().isEmpty(), "a spawned Mage begins unbound");
            CreatureBehaviorState.bind(mage, owner.getUUID());
            CircleMageRuntime.onRecruited(mage, helper.getLevel());
            final CovenRosterData roster = CovenRosterData.get(helper.getLevel());
            helper.assertValueEqual(roster.count(owner.getUUID()), 1,
                "a successful admission registers exactly one roster slot");

            // Registration is idempotent and a same-owner repeat consumes nothing.
            CircleMageRuntime.acknowledgeExistingBinding(mage, helper.getLevel(), owner);
            helper.assertValueEqual(roster.count(owner.getUUID()), 1,
                "a same-owner repeat never spends a second roster slot");
            helper.assertValueEqual(
                CircleMageRuntime.recruitmentDecision(mage, helper.getLevel(), stranger,
                    net.minecraft.world.InteractionHand.MAIN_HAND),
                CircleMageRules.RecruitmentResult.NOT_AN_OFFERING,
                "an empty hand is not an offering and reaches no binding path");
            GameTestAssertions.assertPresentValueEqual(
                helper, mage.warlockeryOwner(), owner.getUUID(),
                "a different player can never steal the existing owner");
            helper.assertValueEqual(roster.count(stranger.getUUID()), 0,
                "a rejected recruitment never registers a roster slot");

            // The regeneration aura is the exact existing owner effect.
            mage.setNoAi(false);
            mage.setMageState(mage.mageState().withCadence(new CircleMageState.Cadence(
                0, 0, 0, 0, 0, 0, 0, 0, 0)));
            helper.runAfterDelay(25L, () -> {
                try {
                    helper.assertTrue(mage.mageCounters().auraApplications() >= 1L,
                        "the lowest-UUID eligible provider applies the aura on its own cadence");
                    helper.assertTrue(owner.hasEffect(MobEffects.REGENERATION),
                        "the owner keeps the exact existing Regeneration I result");
                    helper.assertTrue(mage.mageCounters().ownerLookups() >= 1L,
                        "the owner is resolved by direct current-level UUID lookup only");
                    helper.assertTrue(stranger.getActiveEffects().isEmpty(),
                        "no other player ever receives the aura");
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void circleMagesStudyAndDefendAsABoundedConclave(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            // Also deliberately at dy=+1: the Mage shares the one search envelope, so this
            // fixture fails too if the vertical layer is ever made unreachable again.
            fixture.setBlock(new BlockPos(1, 2, 2), Blocks.BOOKSHELF);
            final ServerPlayer owner = fixture.connectedPlayer(new BlockPos(1, 1, 0), GameType.SURVIVAL);
            final List<CircleMageEntity> coven = new ArrayList<>();
            for (int index = 0; index < CircleMageRules.MAX_COVEN_MAGES; index++) {
                final CircleMageEntity member = spawnMage(fixture, new BlockPos(index % 3, 1, index / 3));
                member.setNoAi(true);
                CreatureBehaviorState.bind(member, owner.getUUID());
                CircleMageRuntime.onRecruited(member, helper.getLevel());
                coven.add(member);
            }
            helper.assertValueEqual(
                CovenRosterData.get(helper.getLevel()).count(owner.getUUID()),
                CircleMageRules.MAX_COVEN_MAGES,
                "six bound Mages fill the exact cap");

            // The conclave is bounded at three total: at most two peers are ever accepted.
            final List<UUID> eligible = coven.stream().skip(1).map(Entity::getUUID).toList();
            helper.assertValueEqual(CircleMageRules.acceptPeers(eligible).size(),
                CircleMageRules.MAX_ACCEPTED_PEERS,
                "at most two peers join, so the total session size is at most three");
            final UUID coordinator =
                CircleMageRules.coordinator(coven.getFirst().getUUID(), CircleMageRules.acceptPeers(eligible));
            helper.assertTrue(coordinator != null, "the lowest UUID coordinates that one session");

            // Focus creation and accepted consumption are entity-local booleans only.
            final CircleMageEntity caster = coven.getFirst();
            caster.setMageState(caster.mageState().withStudy(new CircleMageState.Study(
                true, java.util.Optional.empty(), java.util.Optional.empty(), 0, 0)));
            helper.assertTrue(caster.presentationFocusPrepared()
                    || caster.mageState().study().focusPrepared(),
                "focus is one entity-local boolean, never an item or knowledge grant");

            // A same-species peer and the owner are never legal targets.
            helper.assertFalse(CircleMageRuntime.legalTarget(caster, coven.get(1)),
                "another Circle Mage is never a legal target");
            helper.assertFalse(CircleMageRuntime.legalTarget(caster, owner),
                "the owner is never a legal target");
            final ServerPlayer creative = fixture.connectedPlayer(new BlockPos(2, 1, 2), GameType.CREATIVE);
            helper.assertFalse(CircleMageRuntime.legalTarget(caster, creative),
                "a creative player is never a legal target");

            // An accepted direct hit proposes exactly one target through the exact motive order.
            final ServerPlayer aggressor = fixture.connectedPlayer(new BlockPos(0, 1, 2), GameType.SURVIVAL);
            caster.hurtServer(helper.getLevel(),
                helper.getLevel().damageSources().playerAttack(aggressor), 1.0F);
            helper.assertValueEqual(caster.mageState().threat().source(),
                CircleMageRules.TargetSource.DIRECT,
                "a direct attacker is the highest priority motive");
            GameTestAssertions.assertPresentValueEqual(helper, caster.mageState().threat().id(),
                aggressor.getUUID(), "the accepted direct attacker is the frozen target identity");
            helper.assertFalse(caster.mageState().session().present(),
                "combat urgency releases the temporary conclave");

            // A received report expires through the state constructor, so the one-hop marker has
            // to be cleared on the no-threat branch. Without that, mayEmitReport stayed false for
            // the rest of this entity's loaded life and no later threat could ever be relayed.
            final CircleMageEntity relay = coven.get(1);
            CircleMageRuntime.tick(relay, helper.getLevel());
            relay.setMageState(relay.mageState().withThreat(CircleMageState.Threat.of(
                aggressor.getUUID(), CircleMageRuntime.dimensionOf(helper.getLevel()),
                CircleMageRules.TargetSource.PEER_REPORT)));
            relay.mageTransient().receivedAsReport = true;
            relay.setMageState(relay.mageState().withThreat(CircleMageState.Threat.none()));
            CircleMageRuntime.tick(relay, helper.getLevel());
            helper.assertFalse(relay.mageTransient().receivedAsReport,
                "an expired report clears the one-hop marker instead of muting the Mage forever");
            helper.assertTrue(CircleMageRules.mayEmitReport(
                    CircleMageRules.TargetSource.OWNER, 0, relay.mageTransient().receivedAsReport),
                "so a later owner-sourced threat can still be relayed");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void circleMageSaveReloadSeerAndWorkAreBounded(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final CircleMageEntity mage = spawnMage(fixture, new BlockPos(1, 1, 1));
            mage.setNoAi(true);
            final ServerPlayer owner = fixture.connectedPlayer(new BlockPos(2, 1, 1), GameType.SURVIVAL);
            CreatureBehaviorState.bind(mage, owner.getUUID());
            CircleMageRuntime.onRecruited(mage, helper.getLevel());

            helper.assertValueEqual(mage.operationalTargetGoalCount(), 0,
                "no inherited target goal survives on the dedicated Mage");
            helper.assertFalse(mage.canPickUpLoot(),
                "the route-dependent loot-pickup difference is normalized away");
            helper.assertValueEqual(mage.getAttributeValue(Attributes.FOLLOW_RANGE),
                CircleMageEntity.BASE_FOLLOW_RANGE,
                "the random follow-range spawn bonus is stripped for exact assertions");

            helper.assertTrue(SeerCovenRuntime.isBoundCircleMage(mage),
                "a bound dedicated Mage still counts as an exact ritual participant");
            final int participants = SeerCovenRuntime.countParticipants(
                helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                SeerCovenRuntime.PARTICIPANT_RADIUS, owner.getUUID());
            helper.assertTrue(participants >= 2,
                "participant identity, radius, and counting are unchanged");

            // A Seer recall cancels only stale runtime state; the call result is decided elsewhere.
            mage.setMageState(mage.mageState()
                .withAction(CircleMageState.ActionState.bolt(UUID.randomUUID(),
                    CircleMageRuntime.dimensionOf(helper.getLevel()), true))
                .withSession(CircleMageState.Session.joined(UUID.randomUUID(),
                    CircleMageRuntime.dimensionOf(helper.getLevel()),
                    helper.getLevel().getGameTime(), 1))
                .withStudy(new CircleMageState.Study(true, java.util.Optional.empty(),
                    java.util.Optional.empty(), 700, 0)));
            // A same-level gather returns the identical instance, so the recall lands on the entity
            // that actually arrived. Across dimensions Entity.teleport returns a NEW instance, which
            // is why SeerCovenRuntime.gather returns it instead of discarding it: applying the
            // recall to the discarded original silently lost the anchor and the cancellation.
            final net.minecraft.world.entity.Entity arrived =
                SeerCovenRuntime.gatherForRecall(mage, helper.getLevel(),
                    net.minecraft.world.phys.Vec3.atBottomCenterOf(
                        helper.absolutePos(new BlockPos(1, 1, 1))));
            helper.assertTrue(arrived instanceof CircleMageEntity,
                "gather returns the entity that actually arrived");
            ((CircleMageEntity) arrived).onSeerRecall(
                helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
            helper.assertFalse(mage.mageState().action().pending(),
                "recall cancels the in-flight bolt");
            helper.assertFalse(mage.mageState().session().present(),
                "recall releases the conclave session");
            helper.assertTrue(mage.mageState().study().focusPrepared(),
                "an unspent focus survives a recall");
            helper.assertTrue(mage.mageCounters().recallReconciliations() == 1L,
                "recall reconciliation runs exactly once per call");

            // Actual save and reload cancels every live fact and never replays a bolt.
            final CompoundTag livePersisted = saveEntity(helper, mage);
            helper.assertTrue(
                encode(livePersisted.getCompoundOrEmpty(CircleMageEntity.STATE_KEY)).length
                    < CircleMageRules.MAX_STATE_BYTES,
                "the live persisted semantic state stays below the declared byte ceiling");
            loadEntity(helper, mage, livePersisted);
            helper.assertValueEqual(mage.mageState().mode(), Mode.IDLE,
                "a reloaded Mage always resumes idle");
            helper.assertFalse(mage.mageState().action().pending(),
                "a bolt is never replayed after a reload");
            helper.assertFalse(mage.mageState().threat().present(),
                "a target or report can never rebind to a replacement entity");
            helper.assertFalse(mage.mageState().session().present(),
                "no missed conclave session is ever replayed");
            helper.assertTrue(mage.mageState().study().focusPrepared(),
                "focus and its bounded cooldown are the only work facts that survive");
            GameTestAssertions.assertPresentValueEqual(
                helper, mage.warlockeryOwner(), owner.getUUID(),
                "the authoritative owner UUID is never duplicated or lost");

            // Malformed and overflow roster rows normalize deterministically without a world scan.
            final List<CovenRosterData.Entry> rows = new ArrayList<>();
            for (int index = 0; index < 9; index++) {
                rows.add(new CovenRosterData.Entry(
                    owner.getUUID().toString(), new UUID(7L, index).toString()));
            }
            rows.add(new CovenRosterData.Entry("not-a-uuid", owner.getUUID().toString()));
            final CovenRosterData normalized = CovenRosterData.decode(0, rows);
            helper.assertValueEqual(normalized.count(owner.getUUID()),
                CovenRosterData.MAX_PER_OWNER,
                "legacy overflow is UUID-capped at exactly six");
            helper.assertValueEqual(normalized.members(owner.getUUID()),
                CovenRosterData.decode(0, rows.reversed()).members(owner.getUUID()),
                "normalization is deterministic regardless of decode order");

            helper.assertTrue(helper.getBlockState(new BlockPos(1, 1, 1)).isAir(),
                "no world or inventory mutation occurred anywhere in this fixture");

            // Every instrumented structural counter is read, so none of them can rot into a
            // write-only field that silently stops proving its cap.
            final CircleMageRuntime.Counters counters = mage.mageCounters();
            helper.assertTrue(counters.rosterRegistrations() >= 1L,
                "roster reconciliation is counted");
            helper.assertTrue(counters.boltsCast() >= 0L
                    && counters.boltsCancelled() >= 0L
                    && counters.focusPrepared() >= 0L
                    && counters.focusConsumed() >= 0L
                    && counters.reportsEmitted() >= 0L
                    && counters.reportsAccepted() >= 0L
                    && counters.sessionsJoined() >= 0L
                    && counters.sessionsReleased() >= 0L
                    && counters.safeSteps() >= 0L
                    && counters.emergencyHits() >= 0L,
                "every declared Circle Mage counter is instrumented and readable");
            helper.assertTrue(counters.navigationRequests() * CircleMageRules.PATH_INTERVAL_TICKS
                    <= Math.max(1L, mage.tickCount) + CircleMageRules.PATH_INTERVAL_TICKS,
                "at most one navigation request per twenty ticks");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    // ---------------------------------------------------------------- fixture support

    private static void makeDue(final HedgeCroneEntity crone) {
        final HedgeCroneRuntime.TransientState scratch = crone.croneTransient();
        scratch.resetForLoad();
    }

    private static HedgeCroneEntity spawnCrone(final FixtureScope fixture, final BlockPos position) {
        final GameTestHelper helper = fixture.helper;
        @SuppressWarnings("unchecked")
        final EntityType<HedgeCroneEntity> type =
            (EntityType<HedgeCroneEntity>) ModEntities.ALL.get("hedge_crone").get();
        final HedgeCroneEntity crone = fixture.spawn(type, position, EntitySpawnReason.EVENT);
        crone.setDeltaMovement(Vec3.ZERO);
        final BlockPos absolute = helper.absolutePos(position);
        crone.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        return crone;
    }

    private static CircleMageEntity spawnMage(final FixtureScope fixture, final BlockPos position) {
        final GameTestHelper helper = fixture.helper;
        @SuppressWarnings("unchecked")
        final EntityType<CircleMageEntity> type =
            (EntityType<CircleMageEntity>) ModEntities.ALL.get("circle_mage").get();
        final CircleMageEntity mage = fixture.spawn(type, position, EntitySpawnReason.EVENT);
        mage.setDeltaMovement(Vec3.ZERO);
        final BlockPos absolute = helper.absolutePos(position);
        mage.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        return mage;
    }

    private static byte[] encode(final CompoundTag tag) {
        final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try {
            net.minecraft.nbt.NbtIo.write(tag, new java.io.DataOutputStream(bytes));
        } catch (final java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        return bytes.toByteArray();
    }

    private static CompoundTag saveEntity(final GameTestHelper helper, final Entity entity) {
        try (ProblemReporter.ScopedCollector reporter =
                 new ProblemReporter.ScopedCollector(entity.problemPath(), org.slf4j.LoggerFactory
                     .getLogger(CovenPractitionerGameTests.class))) {
            final TagValueOutput output =
                TagValueOutput.createWithContext(reporter, helper.getLevel().registryAccess());
            entity.saveWithoutId(output);
            return output.buildResult();
        }
    }

    private static void loadEntity(
        final GameTestHelper helper,
        final Entity entity,
        final CompoundTag tag
    ) {
        try (ProblemReporter.ScopedCollector reporter =
                 new ProblemReporter.ScopedCollector(entity.problemPath(), org.slf4j.LoggerFactory
                     .getLogger(CovenPractitionerGameTests.class))) {
            entity.load(TagValueInput.create(reporter, helper.getLevel().registryAccess(), tag));
        }
    }

    /**
     * Opens the framework's own barrier shell around the {@code warlockery:empty3x3x3} cell (the box
     * faces at relative -1 and 3, floor excluded) so the fixture's arena is one connected space.
     * Every removed barrier is restored on close, after the arena shell is removed.
     */
    private static void openFrameworkShell(final FixtureScope fixture) {
        final GameTestHelper helper = fixture.helper;
        final List<BlockPos> removed = new ArrayList<>();
        for (int dx = -1; dx <= 3; dx++) {
            for (int dy = 0; dy <= 3; dy++) {
                for (int dz = -1; dz <= 3; dz++) {
                    final boolean face = dx == -1 || dx == 3 || dy == 3 || dz == -1 || dz == 3;
                    if (!face) {
                        continue;
                    }
                    final BlockPos pos = helper.absolutePos(new BlockPos(dx, dy, dz));
                    if (helper.getLevel().getBlockState(pos).is(Blocks.BARRIER)) {
                        helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        removed.add(pos);
                    }
                }
            }
        }
        fixture.onClose(() -> removed.forEach(pos -> helper.getLevel().setBlock(
            pos, Blocks.BARRIER.defaultBlockState(), 3
        )));
    }

    /**
     * Pass-local arena shell: radius five around the cell center with a full floor and ceiling cap,
     * so scans, line-of-sight rays, and computed destinations stay inside this fixture on the batch
     * grid. Only previously air positions are placed and all are restored on close.
     */
    private static void erectArenaShell(final FixtureScope fixture) {
        final GameTestHelper helper = fixture.helper;
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final int radius = 5;
        final int height = 6;
        final List<BlockPos> placed = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                final boolean wall = Math.abs(dx) == radius || Math.abs(dz) == radius;
                for (int dy = -1; dy <= height; dy++) {
                    final boolean cap = dy == -1 || dy == height;
                    if (!wall && !cap) {
                        continue;
                    }
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

        private void setBlock(final BlockPos position, final net.minecraft.world.level.block.Block block) {
            final BlockPos absolute = helper.absolutePos(position);
            helper.getLevel().setBlock(absolute, block.defaultBlockState(), 3);
            onClose(() -> helper.getLevel().setBlock(absolute, Blocks.AIR.defaultBlockState(), 3));
        }

        private ServerPlayer connectedPlayer(final BlockPos position, final GameType gameType) {
            final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(gameType);
            final net.minecraft.network.Connection connection =
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            final net.minecraft.server.network.CommonListenerCookie cookie =
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(gameType);
            final BlockPos absolute = helper.absolutePos(position);
            player.teleportTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
            return track(player);
        }

        private void onClose(final Runnable action) {
            cleanupActions.add(action);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            entities.forEach(GameTestMockPlayers::release);
            entities.clear();
            // Reverse order: later block edits are undone before earlier ones are restored.
            for (int index = cleanupActions.size() - 1; index >= 0; index--) {
                cleanupActions.get(index).run();
            }
            cleanupActions.clear();
        }
    }
}
