package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.CombatRole;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.Intent;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.PersistenceReason;
import com.kadamitas.warlockery.entity.GoblinEnclaveRuntime;
import com.kadamitas.warlockery.entity.GoblinEnclaveState;
import com.kadamitas.warlockery.entity.GoblinEntity;
import com.kadamitas.warlockery.entity.GoblinProfession;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Five bounded live F10 fixtures, each asserting one named contract through spawned, AI-enabled,
 * self-ticking exact {@code warlockery:goblin} entities.
 *
 * <p>Arena geometry: the framework seals every {@code warlockery:empty3x3x3} cell in a barrier shell, so
 * all fixture geometry stays within relative 0..2 and every entity spawns at y=1. y=2 would clip a
 * tall body's eyes into the ceiling and every line-of-sight walk would terminate on barrier.
 * Computed navigation destinations are kept inside the shell so a goal can never land outside the
 * arena and silently freeze the entity while assertions pass on stale state.</p>
 *
 * <p>Every fixture claims the cadences it depends on inside its own scope with {@link #makeDue},
 * because fixtures share the global world clock across a batch. All {@code runAfterDelay} stages are
 * registered from the test body, never from inside another callback, and every created entity and
 * block edit is cleaned up in a {@code finally} block including mid-sequence lambdas.</p>
 */
public final class GoblinEnclaveGameTests {
    /**
     * Every Villager-specific Brain activity. {@code CORE} and {@code IDLE} are excluded on
     * purpose: the vanilla {@code Brain} constructor seeds those two on every living entity in the
     * game, so they carry no Villager semantics.
     */
    private static final Set<Activity> VILLAGER_BRAIN_ACTIVITIES = Set.of(
        Activity.WORK, Activity.MEET, Activity.REST, Activity.PLAY,
        Activity.PANIC, Activity.HIDE, Activity.RAID, Activity.PRE_RAID
    );

    private GoblinEnclaveGameTests() {
    }

    // ---------------------------------------------------------------- 1: identity

    /**
     * The exact public ID builds the dedicated merchant body with no Villager Brain, no target
     * goals, no MOVE-declaring executor, the exact registered attributes, an unanchored despawn
     * contract, and valid 1.4 state migration.
     */
    public static void goblinEnclaveIdentityScheduleAndMigration(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final GoblinEntity goblin = spawnGoblin(fixture, new BlockPos(1, 1, 1));
            helper.assertValueEqual(goblin.creatureKind(), CreatureKind.GOBLIN,
                "the exact public ID must construct CreatureKind.GOBLIN");
            helper.assertTrue(goblin.getClass() == GoblinEntity.class,
                "warlockery:goblin must construct the dedicated GoblinEntity body");
            // Class.isInstance rather than instanceof: the compiler already proves the two types
            // are unrelated, and this keeps the contract asserted at runtime as well.
            helper.assertFalse(Villager.class.isInstance(goblin),
                "the dedicated body must not inherit the human Villager implementation");
            helper.assertTrue(net.minecraft.world.entity.npc.villager.AbstractVillager.class
                    .isInstance(goblin),
                "the dedicated body keeps the narrow AbstractVillager merchant surface");
            helper.assertValueEqual(goblin.operationalTargetGoalCount(), 0,
                "F10 registers no target-selector goal; targeting belongs to the runtime");
            helper.assertTrue(goblin.operationalGoalNames().size() == 4,
                "exactly four executors are registered");
            // Every LivingEntity's Brain constructor seeds the inert vanilla default {CORE, IDLE},
            // so emptiness is unreachable for any mob. The contract F10 actually owns is that not
            // one Villager activity is ever installed on, or active for, a Goblin.
            helper.assertTrue(VILLAGER_BRAIN_ACTIVITIES.stream()
                    .noneMatch(activity -> goblin.getBrain().isActive(activity)),
                "no Villager Brain activity may ever run on a Goblin");
            helper.assertTrue(VILLAGER_BRAIN_ACTIVITIES.stream()
                    .noneMatch(activity -> goblin.getBrain().getActiveActivities().contains(activity)),
                "no Villager Brain activity may ever be installed on a Goblin");

            // The profession roll is uniform over four values and the field already starts on the
            // PROSPECTOR fallback, so a fifth of a spawn batch lands on the value it already held.
            // Every one of them must still be named: the displayed name is a public invariant.
            for (int index = 0; index < 12; index++) {
                final GoblinEntity rolled = spawnGoblin(fixture, new BlockPos(1, 1, 1));
                helper.assertTrue(rolled.hasCustomName(),
                    "every spawned Goblin carries its profession display name");
                helper.assertTrue(rolled.isCustomNameVisible(),
                    "the profession display name stays visible");
            }
            for (final GoblinProfession profession : GoblinProfession.values()) {
                final GoblinEntity named = spawnGoblin(fixture, new BlockPos(1, 1, 1));
                named.setGoblinProfession(profession);
                // Re-applying the same profession must not drop the name.
                named.setGoblinProfession(profession);
                helper.assertTrue(named.hasCustomName(),
                    "re-applying an unchanged profession keeps the display name");
            }

            // The registry-owned attribute baseline is exact once the random spawn bonus is gone.
            helper.assertValueEqual(goblin.getAttributeValue(Attributes.FOLLOW_RANGE), 24.0D,
                "the follow range stays the exact registered 24.0");
            helper.assertValueEqual(goblin.getAttributeValue(Attributes.ATTACK_DAMAGE), 3.0D,
                "the attack damage stays the exact registered 3.0");

            helper.assertTrue(goblin.removeWhenFarAway(4_096.0D),
                "an unanchored uncontracted wild Goblin uses ordinary hostile despawn");
            helper.assertTrue(goblin.persistenceReason().isEmpty(),
                "a wild Goblin records no persistence reason");
            goblin.setGoblinEnclaveState(goblin.goblinEnclaveState().withAnchor(
                GoblinEnclaveState.Anchor.at(
                    GoblinEnclaveRules.enclaveKey(
                        goblin.blockPosition().getX(), goblin.blockPosition().getZ(),
                        CreatureKind.GOBLIN
                    ),
                    goblin.blockPosition(),
                    GoblinEnclaveRuntime.dimensionOf(helper.getLevel())
                )
            ));
            helper.assertFalse(goblin.removeWhenFarAway(4_096.0D),
                "a valid anchored resident is retained");
            helper.assertValueEqual(goblin.persistenceReason().orElseThrow(),
                PersistenceReason.ANCHORED_RESIDENT,
                "the retained Goblin records exactly the anchored-resident reason");
            helper.assertTrue(goblin.requiresCustomPersistence(),
                "the derived persistence flag follows the reason rather than latching");

            // A live 1.4 Goblin compound must migrate rather than reset.
            final GoblinEntity migrated = spawnGoblin(fixture, new BlockPos(1, 1, 0));
            migrated.setGoblinEnclaveState(GoblinEnclaveState.migrateLegacy(
                "smith", 75, 0L, helper.getLevel().getGameTime(), Optional.empty()
            ));
            helper.assertValueEqual(migrated.goblinProfession(), GoblinProfession.SMITH,
                "the 1.4 custom profession is authoritative through the live body");
            helper.assertValueEqual(migrated.merchantLevel(), 3,
                "old Villager XP becomes a bounded merchant level");
            helper.assertFalse(migrated.goblinEnclaveState().anchor().present(),
                "a migrated Goblin without enclave state starts solitary");

            makeDue(goblin);
            helper.runAfterDelay(60L, () -> {
                try {
                    helper.assertTrue(goblin.goblinCounters().decisions() >= 1L,
                        "a live self-ticking Goblin reaches its own decision cadence");
                    helper.assertTrue(goblin.goblinCounters().decisions() <= 4L,
                        "the decision cadence stays no faster than every twenty ticks");
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

    // ---------------------------------------------------------------- 2: family

    /**
     * Same-kind conception needs food, a bed, and enclave headroom, produces exactly one exact
     * Goblin child with a deterministic parent profession and no combat target, and bounded player
     * relation facts move only on their explicit declared inputs.
     */
    public static void goblinEnclaveFamilyChildrenAndRelations(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final long key = GoblinEnclaveRules.enclaveKey(
                helper.absolutePos(new BlockPos(1, 1, 1)).getX(),
                helper.absolutePos(new BlockPos(1, 1, 1)).getZ(),
                CreatureKind.GOBLIN
            );
            final GoblinEnclaveData data = GoblinEnclaveData.get(helper.getLevel());
            data.clearForGameTest(key);
            fixture.onClose(() -> data.clearForGameTest(key));

            final GoblinEntity first = spawnGoblin(fixture, new BlockPos(0, 1, 1));
            final GoblinEntity second = spawnGoblin(fixture, new BlockPos(2, 1, 1));
            first.setGoblinProfession(GoblinProfession.MINER);
            second.setGoblinProfession(GoblinProfession.SHAMAN);

            final GoblinEntity child = (GoblinEntity) first.getBreedOffspring(helper.getLevel(), second);
            helper.assertTrue(child != null, "two exact Goblins must produce an offspring");
            fixture.track(child);
            helper.assertValueEqual(child.creatureKind(), CreatureKind.GOBLIN,
                "the child is exactly warlockery:goblin");
            helper.assertValueEqual(child.goblinProfession(), GoblinEnclaveRules.childProfession(
                first.getUUID(), GoblinProfession.MINER, second.getUUID(), GoblinProfession.SHAMAN
            ), "the child profession is the deterministic lower-UUID parent's");
            helper.assertTrue(child.getTarget() == null, "a newborn holds no combat target");
            helper.assertTrue(child.goblinEnclaveState().action().claimId().isEmpty(),
                "a newborn holds no work claim");

            // No other family or a human Villager can produce a Goblin child.
            final Villager human = fixture.spawn(
                EntityTypes.VILLAGER, new BlockPos(1, 1, 2), EntitySpawnReason.EVENT
            );
            helper.assertTrue(first.getBreedOffspring(helper.getLevel(), human) == null,
                "a human Villager can never father an exact Goblin child");

            // The enclave population cap is the only birth ceiling and it is exact.
            for (int index = 0; index < GoblinEnclaveRules.MAX_MEMBERS; index++) {
                data.joinEnclave(key, new UUID(0L, index));
            }
            helper.assertValueEqual(data.population(key), GoblinEnclaveRules.MAX_MEMBERS,
                "eight resident UUIDs fill the enclave");
            helper.assertFalse(data.joinEnclave(key, UUID.randomUUID()),
                "a ninth resident is refused rather than expanding storage");

            final ServerPlayer patron = fixture.connectedPlayer(new BlockPos(1, 1, 0), GameType.SURVIVAL);
            data.recordRelation(key, patron.getUUID(),
                GoblinEnclaveRules.RelationEvent.CONTRACT_ACCEPTED);
            helper.assertValueEqual(data.relationScore(key, patron.getUUID()), 10,
                "an accepted contract is an explicit relation input");
            data.recordRelation(key, patron.getUUID(),
                GoblinEnclaveRules.RelationEvent.DIRECT_ATTACK);
            helper.assertValueEqual(data.relationScore(key, patron.getUUID()), -10,
                "a direct attack is an explicit relation input");
            helper.assertTrue(data.record(key).relations().size()
                    <= GoblinEnclaveRules.MAX_RELATIONS,
                "the enclave never retains more than eight player relation facts");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    // ---------------------------------------------------------------- 3: work

    /**
     * Structure reservation simulates before mutating, obeys the three-hut, one-tunnel, and
     * 128-edit caps, and refuses to expand storage when a cap is reached. Counters prove the
     * declared scan budgets are not exceeded by a live tick.
     */
    public static void goblinEnclaveWorkTransactionsAndCaps(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
            final long key = GoblinEnclaveRules.enclaveKey(
                anchor.getX(), anchor.getZ(), CreatureKind.GOBLIN
            );
            final GoblinEnclaveData data = GoblinEnclaveData.get(helper.getLevel());
            data.clearForGameTest(key);
            fixture.onClose(() -> data.clearForGameTest(key));

            final GoblinEntity builder = spawnGoblin(fixture, new BlockPos(1, 1, 1));
            builder.setGoblinProfession(GoblinProfession.SMITH);

            helper.assertTrue(data.reserveHut(key, anchor), "the first hut reservation succeeds");
            helper.assertFalse(data.reserveHut(key, anchor.offset(4, 0, 0)),
                "a second hut inside the spacing radius is refused");
            helper.assertTrue(data.reserveHut(key, anchor.offset(24, 0, 0)),
                "a spaced second hut succeeds");
            helper.assertTrue(data.reserveHut(key, anchor.offset(48, 0, 0)),
                "a spaced third hut succeeds");
            helper.assertFalse(data.reserveHut(key, anchor.offset(72, 0, 0)),
                "a fourth hut is refused by the exact three-hut cap");
            helper.assertValueEqual(data.record(key).ownedEdits(),
                3 * GoblinEnclaveRules.HUT_MAX_EDITS,
                "three committed huts own exactly ninety-six edits");
            helper.assertTrue(data.reserveTunnel(key, anchor.below(), 10),
                "one bounded tunnel fits inside the remaining edit budget");
            helper.assertFalse(data.reserveTunnel(key, anchor.below().offset(96, 0, 0), 10),
                "a second tunnel is refused by the exact one-tunnel cap");
            helper.assertFalse(data.recordEdits(key, 64),
                "an edit request past the 128 cap is refused rather than truncated silently");

            // A claim is a lease: one per Goblin, one per worksite, at most eight per enclave.
            final Optional<UUID> claim = data.claim(
                key, Intent.BUILD_HUT, builder.getUUID(), Optional.of(anchor)
            );
            helper.assertTrue(claim.isPresent(), "the builder receives one lease");
            helper.assertTrue(data.claim(key, Intent.MINE, builder.getUUID(), Optional.empty())
                    .isEmpty(),
                "one Goblin can never hold two leases");
            data.releaseClaimsOf(key, builder.getUUID());
            helper.assertFalse(data.holdsClaim(key, claim.orElseThrow()),
                "releasing by claimant clears the lease");

            makeDue(builder);
            helper.runAfterDelay(100L, () -> {
                try {
                    // The surveyed site is carried forward instead of being rescanned by the
                    // executor, so a selected BUILD_HUT can never be cancelled by the very cooldown
                    // its own survey just consumed.
                    helper.assertFalse(
                        builder.goblinEnclaveState().action().intent() == Intent.BUILD_HUT
                            && builder.goblinTransient().plan().hutSite().isEmpty(),
                        "a committed hut intent always has a carried site to act on");
                    helper.assertTrue(builder.goblinCounters().chargedBlockReads()
                            <= GoblinEnclaveRules.MAX_SITE_BLOCK_READS * 4L,
                        "live charged block reads stay inside the declared survey budget");
                    helper.assertTrue(builder.goblinCounters().navigationRequests()
                            <= 100L / GoblinEnclaveRules.NAVIGATION_INTERVAL_TICKS + 1L,
                        "navigation is requested no faster than every twenty ticks");
                    helper.assertValueEqual(builder.goblinCounters().transactionsRolledBack(),
                        builder.goblinCounters().transactionsRolledBack(),
                        "rollback accounting is observable");
                    helper.assertTrue(builder.goblinCounters().editsApplied()
                            >= builder.goblinCounters().editsRestored(),
                        "no rollback restores more edits than were applied");
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

    // ---------------------------------------------------------------- 4: combat

    /**
     * Adult Goblins target exact human Villagers while players, patrons, children, and other
     * goblin-society members stay safe; an assault marker suspends enclave work and its end
     * releases target, role, claims, and persistence together.
     */
    public static void goblinEnclaveCombatAssaultAndCleanup(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final GoblinEntity goblin = spawnGoblin(fixture, new BlockPos(1, 1, 1));
            final Villager human = fixture.spawn(
                EntityTypes.VILLAGER, new BlockPos(2, 1, 1), EntitySpawnReason.EVENT
            );
            final GoblinEntity kin = spawnGoblin(fixture, new BlockPos(0, 1, 1));
            final GoblinEntity infant = spawnGoblin(fixture, new BlockPos(0, 1, 2));
            infant.setBaby(true);
            final ServerPlayer player = fixture.connectedPlayer(
                new BlockPos(2, 1, 2), GameType.SURVIVAL
            );

            helper.assertTrue(goblin.canAttack(human),
                "an adult Goblin may target an exact human Villager");
            helper.assertFalse(goblin.canAttack(kin),
                "a fellow Goblin is never an eligible target");
            helper.assertFalse(goblin.canAttack(infant),
                "a Goblin child is never an eligible target");
            helper.assertFalse(goblin.canAttack(player),
                "a player is never proactively targeted");
            helper.assertFalse(infant.canAttack(human),
                "a Goblin child never fights");

            goblin.setGoblinEnclaveState(goblin.goblinEnclaveState()
                .withPatron(GoblinEnclaveState.Patron.bound(player.getUUID())));
            helper.assertFalse(goblin.canAttack(player),
                "a bound patron stays protected");

            // An assault marker suspends enclave work and cleanup restores ordinary despawn.
            goblin.joinVillageAssault(helper.absolutePos(new BlockPos(1, 1, 1)), 1, true);
            helper.assertTrue(goblin.isAssaultMember(), "the marked Goblin joins the wave");
            helper.assertTrue(goblin.isAssaultLeader(), "the leader marker is recorded");
            helper.assertValueEqual(goblin.assaultWave(), 1, "the exact wave index is recorded");
            helper.assertTrue(goblin.requiresCustomPersistence(),
                "an active assault member is retained");
            helper.assertTrue(goblin.goblinEnclaveState().action().claimId().isEmpty(),
                "joining an assault releases every enclave work claim");
            helper.assertValueEqual(goblin.goblinEnclaveState().combat().role(), CombatRole.NONE,
                "an assault member derives its role inside the wave, not from a nearby enclave");

            goblin.leaveVillageAssault();
            helper.assertFalse(goblin.isAssaultMember(), "assault markers clear on leave");
            helper.assertTrue(goblin.getTarget() == null,
                "assault cleanup releases the combat target");
            // The patron reason still holds, so persistence is still derived rather than latched.
            helper.assertTrue(goblin.requiresCustomPersistence(),
                "a contracted survivor keeps persistence for its own explicit reason");
            goblin.setGoblinEnclaveState(goblin.goblinEnclaveState()
                .withPatron(GoblinEnclaveState.Patron.none()));
            helper.assertFalse(goblin.requiresCustomPersistence(),
                "a timed-out survivor with no remaining reason is not permanently persistent");
            helper.assertTrue(goblin.removeWhenFarAway(4_096.0D),
                "ordinary hostile despawn is restored after cleanup");

            // The explicit reasons are visible to Mob.checkDespawn, which short-circuits on
            // isPersistenceRequired() before consulting any derived predicate. The vanilla latch is
            // deliberately still honoured after the I6 re-review: GameTestEntityBuilder latches
            // every test-spawned mob exactly as a name tag or a dispenser would, and F10 refuses
            // only the single unclearable write made by the shared contract binding. So the
            // composite predicate stays true here while the half F10 owns is released.
            helper.assertTrue(goblin.isPersistenceRequired(),
                "a GameTest-spawned mob keeps the vanilla latch F10 no longer discards");
            goblin.setGoblinEnclaveState(goblin.goblinEnclaveState()
                .withPatron(GoblinEnclaveState.Patron.bound(player.getUUID())));
            helper.assertTrue(goblin.isPersistenceRequired(),
                "a live contracted patron is a real persistence reason");
            helper.assertTrue(goblin.requiresCustomPersistence(),
                "the bound patron is the F10-owned half of that persistence");
            goblin.setGoblinEnclaveState(goblin.goblinEnclaveState()
                .withPatron(GoblinEnclaveState.Patron.none()));
            helper.assertFalse(goblin.requiresCustomPersistence(),
                "releasing the patron releases the only persistence reason F10 owns");
            helper.assertTrue(goblin.removeWhenFarAway(4_096.0D),
                "releasing the patron restores ordinary hostile despawn");

            // Every ordinary vanilla latch site is still honoured. A name-tagged Goblin persists
            // exactly like any other named mob; only the shared contract binding's unclearable
            // latch write is suppressed, and that is suppressed at its own call site.
            final GoblinEntity tagged = spawnUnlatchedGoblin(fixture, new BlockPos(1, 1, 0));
            helper.assertFalse(tagged.isPersistenceRequired(),
                "precondition: an unnamed wild Goblin is despawnable");
            // A real player-supplied name, taken from the connected profile rather than inlined as
            // English copy: a name tag carries exactly this kind of literal component.
            final String playerName = player.getGameProfile().name();
            tagged.setCustomName(net.minecraft.network.chat.Component.literal(playerName));
            tagged.setPersistenceRequired();
            helper.assertTrue(tagged.isPersistenceRequired(),
                "a name-tagged Goblin persists like every other named mob");
            helper.assertFalse(tagged.removeWhenFarAway(4_096.0D)
                    != GoblinEnclaveRules.mayDespawn(false, false, false),
                "removeWhenFarAway still reports the F10 reasons; the latch is what pins it");

            // The player-assigned name survives a profession change and a reload seam.
            tagged.setGoblinProfession(GoblinProfession.SMITH);
            helper.assertValueEqual(tagged.getCustomName().getString(), playerName,
                "a profession change never overwrites a player-assigned name");
            tagged.setGoblinEnclaveState(tagged.goblinEnclaveState()
                .withProfession(GoblinProfession.SHAMAN));
            helper.assertValueEqual(tagged.getCustomName().getString(), playerName,
                "the reload seam never overwrites a player-assigned name");

            // C4: departure on removal frees the seat and the leases it held.
            final long enclaveKey = GoblinEnclaveRules.enclaveKey(
                kin.blockPosition().getX(), kin.blockPosition().getZ(), CreatureKind.GOBLIN
            );
            final GoblinEnclaveData enclaves = GoblinEnclaveData.get(helper.getLevel());
            enclaves.clearForGameTest(enclaveKey);
            fixture.onClose(() -> enclaves.clearForGameTest(enclaveKey));
            kin.setGoblinEnclaveState(kin.goblinEnclaveState().withAnchor(
                GoblinEnclaveState.Anchor.at(enclaveKey, kin.blockPosition(),
                    GoblinEnclaveRuntime.dimensionOf(helper.getLevel()))
            ));
            enclaves.joinEnclave(enclaveKey, kin.getUUID());
            enclaves.claim(enclaveKey, Intent.MINE, kin.getUUID(), Optional.empty());
            helper.assertValueEqual(enclaves.population(enclaveKey), 1,
                "the resident occupies exactly one seat");
            kin.remove(Entity.RemovalReason.KILLED);
            helper.assertValueEqual(enclaves.population(enclaveKey), 0,
                "a dead resident frees its seat instead of inflating the population forever");
            helper.assertTrue(enclaves.record(enclaveKey).claims().isEmpty(),
                "a dead resident takes every lease it held with it");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    // ---------------------------------------------------------------- 5: bounds

    /**
     * A live Goblin under simultaneous hazard, target, work, and schedule pressure keeps exactly one
     * movement authority, stays inside every declared bound, and persists a semantic state below the
     * declared byte ceiling.
     */
    public static void goblinEnclaveHazardNavigationAndPopulationBounds(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final List<GoblinEntity> crowd = new ArrayList<>();
            // Enclave identities intentionally use absolute 128-block regions. GameTest origins
            // are randomized, so choose the nearest relative offset that keeps this 3x3 crowd on
            // one side of each region boundary instead of making the assertion origin-dependent.
            final BlockPos absoluteOrigin = helper.absolutePos(BlockPos.ZERO);
            final int baseX = regionSafeCrowdOffset(absoluteOrigin.getX());
            final int baseZ = regionSafeCrowdOffset(absoluteOrigin.getZ());
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    crowd.add(spawnGoblin(fixture, new BlockPos(baseX + x, 1, baseZ + z)));
                }
            }
            helper.assertValueEqual(crowd.size(), 9, "nine live Goblins share one arena");
            crowd.forEach(GoblinEnclaveGameTests::makeDue);

            final GoblinEntity sample = crowd.getFirst();
            helper.assertFalse(sample.goblinTransient().hazardActive(),
                "a dry unlit arena reports no hazard");

            helper.runAfterDelay(120L, () -> {
                try {
                    crowd.forEach(goblin -> {
                        helper.assertTrue(goblin.goblinCounters().navigationRequests()
                                <= 120L / GoblinEnclaveRules.NAVIGATION_INTERVAL_TICKS + 2L,
                            "no Goblin exceeds one navigation request per twenty ticks");
                        helper.assertTrue(goblin.goblinCounters().entityVisits()
                                <= GoblinEnclaveRules.MAX_ENTITY_VISITS
                                    * (120L / GoblinEnclaveRules.PERCEPTION_INTERVAL_TICKS + 2L),
                            "entity perception stays inside its declared visit budget");
                        helper.assertTrue(goblin.goblinCounters().feedbackPulses()
                                <= 120L / GoblinEnclaveRules.FEEDBACK_INTERVAL_TICKS + 1L,
                            "feedback stays inside its declared rate limit");
                        helper.assertTrue(goblin.goblinEnclaveState().cadence().routeFailures()
                                <= GoblinEnclaveRules.MAX_ROUTE_FAILURES,
                            "route failures saturate at three instead of growing");
                    });
                    final long enclaves = crowd.stream()
                        .map(GoblinEnclaveRuntime::enclaveKey)
                        .filter(Optional::isPresent)
                        .map(Optional::orElseThrow)
                        .distinct()
                        .count();
                    helper.assertTrue(enclaves <= 1L,
                        "one co-located crowd reconciles into at most one enclave record");

                    final TagValueOutput output = TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
                    );
                    sample.saveWithoutId(output);
                    helper.assertTrue(
                        encode(output.buildResult().getCompoundOrEmpty(GoblinEntity.STATE_KEY)).length
                            < GoblinEnclaveRules.MAX_STATE_BYTES,
                        "the live persisted semantic state stays below the declared byte ceiling");
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

    // ---------------------------------------------------------------- helpers

    /**
     * Claims every cadence this fixture depends on at the tick it needs it. Fixtures share the
     * global world clock across a batch, so a fixture that does not claim its own cadence can
     * observe another fixture's stale phase.
     */
    private static void makeDue(final GoblinEntity goblin) {
        final GoblinEnclaveRuntime.TransientState scratch = goblin.goblinTransient();
        scratch.resetForLoad();
    }

    private static int regionSafeCrowdOffset(final int absoluteOrigin) {
        final int withinRegion = Math.floorMod(absoluteOrigin, GoblinEnclaveRules.REGION_SIZE);
        return withinRegion <= GoblinEnclaveRules.REGION_SIZE - 3
            ? 0
            : GoblinEnclaveRules.REGION_SIZE - withinRegion;
    }

    private static GoblinEntity spawnGoblin(final FixtureScope fixture, final BlockPos position) {
        final GameTestHelper helper = fixture.helper;
        @SuppressWarnings("unchecked")
        final EntityType<GoblinEntity> type =
            (EntityType<GoblinEntity>) ModEntities.ALL.get("goblin").get();
        final GoblinEntity goblin = fixture.spawn(type, position, EntitySpawnReason.EVENT);
        goblin.setDeltaMovement(Vec3.ZERO);
        final BlockPos absolute = helper.absolutePos(position);
        goblin.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        // Sensing caches line of sight per server tick; repositioning invalidates that cache.
        goblin.getSensing().tick();
        return goblin;
    }

    /**
     * Spawns a Goblin without the GameTest entity builder, which latches vanilla persistence on
     * every mob it spawns so that a fixture cannot despawn its own subject mid-test. The name-tag
     * contract has to start from a genuinely unlatched body to prove the latch transition at all,
     * so this one is constructed directly and tracked for the same cleanup.
     */
    private static GoblinEntity spawnUnlatchedGoblin(
        final FixtureScope fixture,
        final BlockPos position
    ) {
        final GameTestHelper helper = fixture.helper;
        @SuppressWarnings("unchecked")
        final EntityType<GoblinEntity> type =
            (EntityType<GoblinEntity>) ModEntities.ALL.get("goblin").get();
        final GoblinEntity goblin = type.create(helper.getLevel(), EntitySpawnReason.EVENT);
        if (goblin == null) {
            throw new IllegalStateException("warlockery:goblin must construct a body");
        }
        final BlockPos absolute = helper.absolutePos(position);
        goblin.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
        goblin.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(goblin);
        // Sensing caches line of sight per server tick; repositioning invalidates that cache.
        goblin.getSensing().tick();
        return fixture.track(goblin);
    }

    private static byte[] encode(final CompoundTag tag) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            NbtIo.write(tag, new DataOutputStream(bytes));
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return bytes.toByteArray();
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
            // makeMockServerPlayer pins the game mode at construction; a later setGameMode is inert,
            // so the requested mode is supplied to the constructor.
            final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(gameType);
            final net.minecraft.network.Connection connection =
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            final net.minecraft.server.network.CommonListenerCookie cookie =
                net.minecraft.server.network.CommonListenerCookie.createInitial(
                    player.getGameProfile(), false
                );
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
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
            // Reverse order so later edits are undone before earlier ones are restored.
            for (int index = cleanupActions.size() - 1; index >= 0; index--) {
                cleanupActions.get(index).run();
            }
            cleanupActions.clear();
        }
    }
}
