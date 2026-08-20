package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.BansheeRules.Mode;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.util.GameTestAssertions;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Five bounded live F16 fixtures. Every fixture asserts through spawned, AI-enabled or directly
 * dispatched entities, cleans up all created entities and blocks in {@code finally} including
 * mid-sequence stages, and uses exact counter assertions instead of elapsed-time guesses.
 *
 * <p>Arena geometry: the framework seals the {@code warlockery:empty3x3x3} cell in a barrier shell,
 * so close-quarters fixtures keep every entity inside relative 0..2 at y=1. The two fixtures
 * that need real standoff distances (6..10 blocks) open the framework shell inside their own
 * pass-local radius-five arena, mirroring the accepted F15 Hex Bat precedent, and restore every
 * block on close in reverse order so the framework shell ends byte-identical.</p>
 */
public final class BansheeGameTests {
    private BansheeGameTests() {
    }

    public static void bansheeWarnsAtRiskPlayerWithoutCausingHarm(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            openFrameworkShell(fixture);
            erectArenaShell(fixture);
            // The banshee and its at-risk subject start eight blocks apart (already inside the
            // 6..10 warning standoff band centered on the shared arena anchor (1,1,1)) so the
            // very first observation holds in place: the WITHDRAW/APPROACH standoff search never
            // has to route toward a point beyond the arena's own five-block-radius wall.
            final BansheeEntity banshee = spawnBanshee(fixture, new BlockPos(-3, 1, 1));
            final ServerPlayer atRisk = fixture.connectedPlayer(new BlockPos(5, 1, 1), GameType.SURVIVAL);
            atRisk.setHealth(atRisk.getMaxHealth() * 0.3F);
            final ServerPlayer healthy = fixture.connectedPlayer(new BlockPos(-3, 1, 0), GameType.SURVIVAL);
            final ServerPlayer creative = fixture.connectedPlayer(new BlockPos(0, 1, -3), GameType.CREATIVE);
            creative.setHealth(creative.getMaxHealth() * 0.1F);
            final float atRiskHealth = atRisk.getHealth();
            makeDue(banshee);
            helper.runAfterDelay(120L, () -> {
                try {
                    GameTestAssertions.assertPresentValueEqual(helper, 
                        banshee.bansheeState().subject().id(), atRisk.getUUID(),
                        "the low-health survival player is the one selected warning subject");
                    helper.assertTrue(banshee.getTarget() == null,
                        "the warning subject is never written to Mob.target");
                    helper.assertTrue(
                        banshee.bansheeState().mode() == Mode.WARNING
                            || banshee.bansheeState().mode() == Mode.APPROACH,
                        "a live self-ticking Banshee holds a warning episode");
                    helper.assertTrue(banshee.bansheeState().subject().pulsesEmitted()
                            <= BansheeRules.MAX_WARNING_PULSES,
                        "the warning pulse count never exceeds three per episode");
                    helper.assertValueEqual(atRisk.getHealth(), atRiskHealth,
                        "a warning pulse applies zero damage");
                    helper.assertTrue(atRisk.getActiveEffects().isEmpty(),
                        "a warning pulse applies zero effects");
                    helper.assertTrue(healthy.getActiveEffects().isEmpty()
                            && healthy.getHealth() == healthy.getMaxHealth(),
                        "healthy players are never warned or harmed");
                    atRisk.setHealth(atRisk.getMaxHealth());
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(190L, () -> {
                try {
                    helper.assertTrue(banshee.bansheeState().subject().id().isEmpty(),
                        "sixty sustained recovered ticks release the subject");
                    helper.assertTrue(
                        banshee.bansheeState().cadence().reacquireTicks() > 0
                            || banshee.bansheeState().mode() == Mode.VIGIL,
                        "release enters the reacquisition cooldown and returns to vigil");
                    helper.assertTrue(banshee.bansheeCounters().releases() >= 1L,
                        "release work is counted");
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

    public static void bansheeLamentsOnlyAnObservedDeathAndReturnsToVigil(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final BansheeEntity observer = spawnBanshee(fixture, new BlockPos(0, 1, 0));
            observer.setNoAi(true);
            final ServerPlayer doomed = fixture.connectedPlayer(new BlockPos(2, 1, 2), GameType.SURVIVAL);
            observer.setBansheeState(observer.bansheeState()
                .withSubject(BansheeState.Subject.acquired(
                    doomed.getUUID(), BansheeRuntime.dimensionOf(helper.getLevel())
                ))
                .withMode(Mode.APPROACH));
            final BansheeEntity griefless = spawnBanshee(fixture, new BlockPos(2, 1, 0));
            griefless.setNoAi(true);
            griefless.setBansheeState(griefless.bansheeState()
                .withSubject(BansheeState.Subject.acquired(
                    UUID.randomUUID(), BansheeRuntime.dimensionOf(helper.getLevel())
                ))
                .withMode(Mode.APPROACH));

            final BlockPos expectedSite = doomed.blockPosition();
            // Zero health is exactly what a real death looks like on the observation tick; a
            // fully processed mock-player kill is immediately respawned or removed by the
            // GameTest server, so it can never be observed by any entity tick.
            doomed.setHealth(0.0F);
            helper.assertTrue(!doomed.isAlive()
                    && helper.getLevel().getPlayerByUUID(doomed.getUUID()) == doomed,
                "precondition: the dying subject stays resolvable in-level at zero health");
            makeDue(observer);
            BansheeRuntime.tick(observer, helper.getLevel());
            helper.assertTrue(observer.bansheeState().death().present(),
                "a subject that actually dies while loaded and resolved produces one death report");
            helper.assertValueEqual(observer.bansheeState().death().position().orElseThrow(),
                expectedSite, "the report records the directly observed death position");
            helper.assertValueEqual(observer.bansheeState().mode(), Mode.LAMENT,
                "the observed death begins a bounded lament");
            helper.assertTrue(observer.bansheeState().subject().id().isEmpty(),
                "the subject identity clears into the death report");

            for (int tick = 0; tick <= BansheeRules.MISSING_GRACE_TICKS; tick++) {
                makeDue(griefless);
                BansheeRuntime.tick(griefless, helper.getLevel());
            }
            helper.assertTrue(!griefless.bansheeState().death().present(),
                "a missing or unresolvable subject never produces a death report");
            helper.assertTrue(griefless.bansheeState().subject().id().isEmpty(),
                "the missing grace releases the unresolvable subject");

            for (int tick = 0; tick <= BansheeRules.LAMENT_TICKS + 1; tick++) {
                makeDue(observer);
                BansheeRuntime.tick(observer, helper.getLevel());
            }
            helper.assertTrue(!observer.bansheeState().death().present(),
                "lament expiry clears the death report");
            helper.assertTrue(observer.bansheeState().mode() == Mode.RECOVERY
                    || observer.bansheeState().mode() == Mode.VIGIL,
                "after the lament the Banshee returns toward its vigil");
            helper.assertTrue(observer.bansheeCounters().lamentPulsesEmitted()
                    <= BansheeRules.MAX_LAMENT_PULSES,
                "at most two lament pulses are emitted per report");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void bansheeRecoilsFromAttackWithoutASonicWeapon(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final BansheeEntity banshee = spawnBanshee(fixture, new BlockPos(1, 2, 1));
            banshee.setNoAi(true);
            final Zombie hostile = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 1), EntitySpawnReason.EVENT);
            hostile.setNoAi(true);
            banshee.invulnerableTime = 0;
            helper.assertTrue(banshee.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(hostile), 1.0F
            ), "the recoil fixture needs one real accepted hit");
            helper.assertTrue(hostile.hasEffect(MobEffects.WEAKNESS),
                "a legal hostile Enemy attacker receives exact Weakness I");
            helper.assertTrue(hostile.hasEffect(MobEffects.MINING_FATIGUE),
                "a legal hostile Enemy attacker receives exact Mining Fatigue I");
            helper.assertValueEqual(
                hostile.getEffect(MobEffects.WEAKNESS).getAmplifier(), 0, "amplifier zero");
            helper.assertTrue(
                hostile.getEffect(MobEffects.WEAKNESS).getDuration() <= BansheeRules.TABOO_EFFECT_TICKS,
                "the taboo effect lasts at most 120 effect ticks");
            helper.assertValueEqual(banshee.bansheeState().mode(), Mode.RECOIL,
                "accepted legal damage interrupts into recoil");
            helper.assertTrue(banshee.bansheeState().attacker().teleportAttempted(),
                "the persisted teleport-attempt bit is set before any teleport attempt");
            helper.assertValueEqual(banshee.bansheeCounters().tabooResponses(), 1L,
                "one taboo response per accepted window");
            helper.assertTrue(banshee.bansheeCounters().teleportAttempts() <= 1L,
                "at most one validated teleport attempt per response window");
            helper.assertTrue(banshee.getTarget() == null, "recoil never assigns a target");
            helper.assertTrue(hostile.getHealth() == hostile.getMaxHealth(),
                "the Banshee deals no damage back");

            banshee.invulnerableTime = 0;
            helper.assertTrue(banshee.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(hostile), 1.0F
            ), "a second accepted hit inside the cooldown still hurts normally");
            helper.assertValueEqual(banshee.bansheeCounters().tabooResponses(), 1L,
                "the 120-loaded-tick cooldown forbids a second taboo response");

            final BansheeEntity villagerVictim = spawnBanshee(fixture, new BlockPos(1, 2, 5));
            villagerVictim.setNoAi(true);
            final Villager villager = fixture.spawn(EntityTypes.VILLAGER, new BlockPos(3, 1, 5), EntitySpawnReason.EVENT);
            villager.setNoAi(true);
            villagerVictim.invulnerableTime = 0;
            villagerVictim.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(villager), 1.0F
            );
            helper.assertTrue(!villager.hasEffect(MobEffects.WEAKNESS)
                    && !villagerVictim.bansheeState().attacker().present(),
                "a villager attacker is excluded even when an external mechanic makes it deal damage");

            final ServerPlayer creative = fixture.connectedPlayer(new BlockPos(5, 1, 5), GameType.CREATIVE);
            villagerVictim.invulnerableTime = 0;
            villagerVictim.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().playerAttack(creative), 1.0F
            );
            helper.assertTrue(!villagerVictim.bansheeState().attacker().present(),
                "a creative attacker never creates attacker state");

            final BansheeEntity owned = spawnBanshee(fixture, new BlockPos(5, 2, 1));
            owned.setNoAi(true);
            final ServerPlayer ownerPlayer = fixture.connectedPlayer(new BlockPos(7, 1, 1), GameType.SURVIVAL);
            CreatureBehaviorState.bind(owned, ownerPlayer.getUUID());
            owned.invulnerableTime = 0;
            owned.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().playerAttack(ownerPlayer), 1.0F
            );
            helper.assertTrue(!owned.bansheeState().attacker().present()
                    && !ownerPlayer.hasEffect(MobEffects.WEAKNESS),
                "the Spectral Stone owner is excluded from the taboo");

            owned.invulnerableTime = 0;
            owned.hurtServer(helper.getLevel(), helper.getLevel().damageSources().cactus(), 1.0F);
            helper.assertTrue(!owned.bansheeState().attacker().present(),
                "environmental damage never creates attacker state");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void bansheeSaveReloadAndAcquisitionContractsArePreserved(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final BansheeEntity banshee = spawnBanshee(fixture, new BlockPos(1, 1, 1));
            banshee.setNoAi(true);
            helper.assertValueEqual(banshee.getClass().getName(), BansheeEntity.class.getName(),
                "the exact registered banshee id constructs the dedicated BansheeEntity class");
            helper.assertValueEqual(banshee.creatureKind(), CreatureKind.BANSHEE, "exact kind");
            helper.assertValueEqual(banshee.getType().getCategory(), MobCategory.MONSTER,
                "public MONSTER category stays exact");
            helper.assertTrue(!banshee.getType().isAllowedInPeaceful(),
                "explicit Peaceful removal stays exact");
            helper.assertValueEqual(banshee.getAttributeValue(Attributes.MAX_HEALTH), 14.0D, "health 14");
            helper.assertValueEqual(banshee.getAttributeValue(Attributes.ATTACK_DAMAGE), 4.0D, "attack 4");
            helper.assertValueEqual(banshee.getAttributeValue(Attributes.FOLLOW_RANGE), 16.0D, "follow 16");
            helper.assertTrue(Math.abs(banshee.getType().getDimensions().width() - 0.65F) < 1.0E-6F
                    && Math.abs(banshee.getType().getDimensions().height() - 1.8F) < 1.0E-6F,
                "exact 0.65 by 1.8 dimensions");
            helper.assertValueEqual(banshee.operationalTargetGoalCount(), 0,
                "the target selector is empty");
            for (final EquipmentSlot slot : EquipmentSlot.values()) {
                helper.assertTrue(banshee.getItemBySlot(slot).isEmpty(),
                    "equipment stays empty through construction: " + slot);
            }

            final UUID attackerId = UUID.randomUUID();
            final String dimension = BansheeRuntime.dimensionOf(helper.getLevel());
            for (int level = 0; level < 5; level++) {
                CreatureBehaviorState.empower(banshee, 1);
            }
            final ServerPlayer owner = fixture.connectedPlayer(new BlockPos(2, 1, 2), GameType.SURVIVAL);
            CreatureBehaviorState.bind(banshee, owner.getUUID());
            banshee.setBansheeState(banshee.bansheeState()
                .withSubject(BansheeState.Subject.acquired(owner.getUUID(), dimension))
                .withMode(Mode.WARNING)
                .withAttacker(new BansheeState.Attacker(
                    Optional.of(attackerId), Optional.of(dimension), 45, true
                )));

            final TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
            );
            banshee.saveWithoutId(output);
            final CompoundTag saved = output.buildResult().copy();
            final CompoundTag banshedState = saved.getCompoundOrEmpty(BansheeEntity.STATE_KEY);
            banshedState.putInt("Episode", Integer.MAX_VALUE);
            banshedState.putInt("WarnPulse", 0);
            saved.put(BansheeEntity.STATE_KEY, banshedState);
            final CompoundTag legacySword = new CompoundTag();
            legacySword.putString("id", "minecraft:iron_sword");
            legacySword.putInt("count", 1);
            final CompoundTag equipment = new CompoundTag();
            equipment.put("mainhand", legacySword);
            saved.put("equipment", equipment);
            saved.putInt("LifeTicks", 40);

            final Entity recreated = ModEntities.ALL.get("banshee").get()
                .create(helper.getLevel(), EntitySpawnReason.LOAD);
            helper.assertTrue(recreated instanceof BansheeEntity,
                "the registered type recreates the dedicated class on load");
            final BansheeEntity loaded = (BansheeEntity) recreated;
            fixture.track(loaded);
            loaded.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved
            ));
            helper.assertValueEqual(loaded.bansheeState().subject().id().orElseThrow(), owner.getUUID(),
                "the semantic warning subject survives reload pending revalidation");
            helper.assertValueEqual(loaded.bansheeState().mode(), Mode.APPROACH,
                "a reloaded warning requires a fresh twenty-tick hold");
            helper.assertValueEqual(loaded.bansheeState().subject().episodeRemainingTicks(),
                BansheeRules.EPISODE_TICKS,
                "extreme persisted durations clamp without elapsed-world-time expiry");
            helper.assertValueEqual(loaded.bansheeState().subject().pulseRemainingTicks(),
                BansheeRules.WARNING_PULSE_INTERVAL_TICKS,
                "a zero persisted pulse interval is restored so no pulse replays");
            helper.assertValueEqual(loaded.bansheeState().attacker().id().orElseThrow(), attackerId,
                "the one attacker identity survives reload");
            helper.assertTrue(loaded.bansheeState().attacker().teleportAttempted(),
                "the teleport-attempted bit survives reload so no second teleport can occur");
            for (final EquipmentSlot slot : EquipmentSlot.values()) {
                helper.assertTrue(loaded.getItemBySlot(slot).isEmpty(),
                    "hostile legacy equipment is normalized empty on load: " + slot);
            }
            helper.assertValueEqual(CreatureBehaviorState.empowerment(loaded), 5,
                "all five Graveyard Dust empowerment levels persist");
            helper.assertTrue(CreatureBehaviorState.isOwnedBy(loaded, owner.getUUID()),
                "the Warlockery Spectral Stone owner marker persists");
            helper.assertTrue(loaded.getNavigation().isDone(), "no path resumes from disk");
            helper.assertTrue(loaded.isNoGravity(), "flight gravity removal survives reload");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void bansheeFlightHazardFeedbackAndWorkAreBounded(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            openFrameworkShell(fixture);
            erectArenaShell(fixture);
            final BansheeEntity banshee = spawnBanshee(fixture, new BlockPos(1, 1, 1));
            banshee.setNoAi(true);
            helper.assertTrue(!banshee.noPhysics,
                "the dedicated Banshee never phases: collision stays enabled");
            helper.assertTrue(banshee.isNoGravity(), "flight disables gravity only");

            makeDue(banshee);
            BansheeRuntime.tick(banshee, helper.getLevel());
            final long baselineReads = banshee.bansheeCounters().blockReads();
            helper.assertTrue(baselineReads <= BansheeRules.MAX_HAZARD_READS,
                "one hazard observation charges at most twenty-seven block reads");

            final BlockPos fireRelative = new BlockPos(2, 1, 1);
            helper.setBlock(fireRelative, Blocks.FIRE.defaultBlockState());
            fixture.onClose(() -> helper.setBlock(fireRelative, Blocks.AIR.defaultBlockState()));
            makeDue(banshee);
            BansheeRuntime.tick(banshee, helper.getLevel());
            helper.assertTrue(banshee.bansheeCounters().hazardInterruptions() >= 1L,
                "an adjacent fire block is observed as an escapable hazard");
            helper.assertTrue(banshee.bansheeCounters().safeCandidateVisits()
                    <= BansheeRules.MAX_SAFE_CANDIDATES * banshee.bansheeCounters().safeSearches(),
                "every safe search stays within its twenty-four candidate budget");
            helper.assertTrue(banshee.getType() == ModEntities.ALL.get("banshee").get(),
                "hazard handling never converts or reidentifies the entity");
            helper.setBlock(fireRelative, Blocks.AIR.defaultBlockState());

            final ServerPlayer subject = fixture.connectedPlayer(new BlockPos(-3, 1, -3), GameType.SURVIVAL);
            subject.setHealth(subject.getMaxHealth() * 0.3F);
            // Sixteen chorus members, every one genuinely in the 6..10 warning band of the one
            // shared subject with clear line of sight inside the opened arena.
            final int[][] bandOffsets = {
                {6, 0}, {6, 1}, {6, 2}, {6, 3}, {6, 4}, {6, 5}, {6, 6},
                {5, 6}, {4, 6}, {3, 6}, {2, 6}, {1, 6}, {0, 6},
                {5, 4}, {4, 5}, {5, 5}
            };
            final List<BansheeEntity> chorus = new ArrayList<>();
            for (final int[] offset : bandOffsets) {
                final BansheeEntity member = spawnBanshee(fixture, new BlockPos(
                    -3 + offset[0], 1, -3 + offset[1]
                ));
                member.setNoAi(true);
                member.setBansheeState(member.bansheeState()
                    .withSubject(new BansheeState.Subject(
                        Optional.of(subject.getUUID()),
                        Optional.of(BansheeRuntime.dimensionOf(helper.getLevel())),
                        Optional.empty(), 0, 0, 0, BansheeRules.EPISODE_TICKS, 1, 0
                    ))
                    .withMode(Mode.WARNING));
                chorus.add(member);
            }
            // Priming pass: the first live tick runs reconcileOnLoad, which deliberately
            // clears any pre-armed warning hold, so each member takes one real tick before
            // the fixture arms its hold for the due pass.
            for (final BansheeEntity member : chorus) {
                makeDue(member);
                BansheeRuntime.tick(member, helper.getLevel());
            }
            for (final BansheeEntity member : chorus) {
                makeDue(member);
                member.bansheeTransient().holdTicks = BansheeRules.WARNING_HOLD_TICKS;
                member.bansheeTransient().sightCooldownTicks = 0;
                BansheeRuntime.tick(member, helper.getLevel());
            }
            long emitted = 0;
            long advanced = 0;
            for (final BansheeEntity member : chorus) {
                emitted += member.bansheeCounters().warningPulsesEmitted();
                if (member.bansheeState().subject().pulsesEmitted() >= 1
                    || member.bansheeState().subject().id().isEmpty()) {
                    advanced++;
                }
            }
            helper.assertTrue(emitted >= 1L,
                "at least one due Banshee in the chorus emits its warning pulse");
            helper.assertTrue(emitted <= 16L,
                "local suppression is best effort; no impossible global-uniqueness claim is made");
            helper.assertValueEqual(advanced, 16L,
                "every due Banshee advances its own schedule whether it emitted or was suppressed");
            for (final BansheeEntity member : chorus) {
                helper.assertTrue(member.bansheeCounters().candidateVisits()
                        <= BansheeRules.MAX_CANDIDATES_VISITED + BansheeRules.MAX_FEEDBACK_NEIGHBOURS,
                    "candidate and suppression visits stay within their declared caps");
                helper.assertTrue(member.bansheeCounters().lineOfSightChecks()
                        <= BansheeRules.MAX_LINE_OF_SIGHT_CHECKS + 2L,
                    "line-of-sight rays stay within the declared discovery and subject budget");
            }

            final BansheeEntity wayworn = spawnBanshee(fixture, new BlockPos(2, 1, 0));
            wayworn.setNoAi(true);
            wayworn.setBansheeState(wayworn.bansheeState()
                .withSubject(BansheeState.Subject.acquired(
                    subject.getUUID(), BansheeRuntime.dimensionOf(helper.getLevel())
                ))
                .withMode(Mode.APPROACH)
                .withCadence(new BansheeState.Cadence(0, 3, 0, 0, 0)));
            makeDue(wayworn);
            BansheeRuntime.tick(wayworn, helper.getLevel());
            helper.assertTrue(wayworn.bansheeState().subject().id().isEmpty(),
                "a third persisted route failure is observable and releases through the live tick");
            helper.assertValueEqual(wayworn.bansheeState().mode(), Mode.RECOVERY,
                "the route-failure release enters recovery");
            helper.assertValueEqual(wayworn.bansheeState().cadence().routeFailures(), 0,
                "the release resets the failure counter after it was observed");
            helper.assertTrue(wayworn.bansheeState().cadence().reacquireTicks() > 0,
                "the release starts the reacquisition cooldown");
            helper.assertTrue(wayworn.bansheeCounters().releases() >= 1L,
                "the route-failure release is counted");

            final TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
            );
            banshee.saveWithoutId(output);
            final byte[] encoded = encode(output.buildResult()
                .getCompoundOrEmpty(BansheeEntity.STATE_KEY));
            helper.assertTrue(encoded.length < BansheeRules.MAX_STATE_BYTES,
                "the live persisted semantic state stays below the declared byte ceiling");
            helper.succeed();
        } finally {
            fixture.close();
        }
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

    private static void makeDue(final BansheeEntity banshee) {
        final BansheeRuntime.TransientState scratch = banshee.bansheeTransient();
        scratch.pathCooldownTicks = 0;
        scratch.hazardCooldownTicks = 0;
        scratch.discoveryCooldownTicks = 0;
        scratch.sightCooldownTicks = 0;
        scratch.ambientCooldownTicks = BansheeRules.AMBIENT_INTERVAL_TICKS;
        // The fixture claims the idle-wander cadence so the exact hazard-read
        // assertion is not polluted by the idle safe-search's charged reads.
        scratch.idleCooldownTicks = BansheeRules.IDLE_DESTINATION_INTERVAL_TICKS;
    }

    private static BansheeEntity spawnBanshee(final FixtureScope fixture, final BlockPos position) {
        final GameTestHelper helper = fixture.helper;
        @SuppressWarnings("unchecked")
        final EntityType<BansheeEntity> type =
            (EntityType<BansheeEntity>) ModEntities.ALL.get("banshee").get();
        final BansheeEntity banshee = fixture.spawn(type, position, EntitySpawnReason.EVENT);
        banshee.setDeltaMovement(Vec3.ZERO);
        final BlockPos absolute = helper.absolutePos(position);
        banshee.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        return banshee;
    }

    /**
     * Opens the framework's own barrier shell around the {@code warlockery:empty3x3x3} cell (the box
     * faces at relative -1 and 3, floor excluded) so the fixture's arena is one connected
     * space. Every removed barrier is restored on close, after the arena shell is removed.
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
     * Pass-local arena shell mirroring the accepted F15 pattern: radius five around the cell
     * center with a full floor and ceiling cap, so scans, line-of-sight rays, and routes stay
     * inside this fixture on the 8-10 block batch grid. Only previously-air positions are
     * placed and all are restored on close.
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
            // Reverse order: later block edits (the arena shell) are undone before earlier
            // ones (the reopened framework shell) are restored, so overlaps end correct.
            for (int index = cleanupActions.size() - 1; index >= 0; index--) {
                cleanupActions.get(index).run();
            }
            cleanupActions.clear();
        }
    }
}
