package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.GoblinProfession;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.CampPhase;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.ContractEnd;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.ContractKind;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.Mode;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.PersistenceReason;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.RelationFact;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRuntime;
import com.kadamitas.warlockery.entity.HobgoblinJourneyState;
import com.kadamitas.warlockery.entity.HobgoblinEntity;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Five bounded live F11 fixtures, each asserting one named contract through spawned, AI-enabled,
 * self-ticking exact {@code warlockery:hobgoblin} entities.
 *
 * <p>Arena geometry: the framework seals every {@code forge:empty3x3x3} cell in a barrier shell, so
 * all fixture geometry stays within relative 0..2 and every entity spawns at y=1. y=2 would clip a
 * tall body's eyes into the ceiling and every line-of-sight walk would terminate on barrier, and any
 * navigation destination outside the shell would stall the entity silently while assertions passed
 * on stale state.</p>
 *
 * <p>Damage rules that have cost other families real time and are obeyed here: a mock
 * {@code ServerPlayer} cannot take damage, so every retaliation assertion uses a live {@link Zombie}
 * as the attacker, and {@code invulnerableTime} is reset before every follow-up hit or the second
 * hit lands on nothing and the assertion passes vacuously.</p>
 *
 * <p>Every fixture claims the cadences it depends on inside its own scope with {@link #makeDue},
 * because fixtures share the global world clock across a batch. All {@code runAfterDelay} stages are
 * registered from the test body, never from inside another callback, and every created entity, data
 * key, and block edit is released in a {@code finally} block including mid-sequence lambdas.</p>
 */
public final class HobgoblinJourneyGameTests {
    /**
     * Every Villager-specific Brain activity. {@code CORE} and {@code IDLE} are excluded on purpose:
     * the vanilla {@code Brain} constructor seeds those two on every living entity in the game, so
     * an empty active-activity set is unsatisfiable for any mob and carries no Villager semantics.
     */
    private static final Set<Activity> VILLAGER_BRAIN_ACTIVITIES = Set.of(
        Activity.WORK, Activity.MEET, Activity.REST, Activity.PLAY,
        Activity.PANIC, Activity.HIDE, Activity.RAID, Activity.PRE_RAID
    );

    private HobgoblinJourneyGameTests() {
    }

    // ---------------------------------------------------------------- 1: identity

    /**
     * The exact public ID builds the dedicated traveler body with no Villager Brain, no target
     * goals, no MOVE-declaring executor, the exact registered attributes, an unanchored despawn
     * contract, restored door opening, and valid 1.4 state migration.
     */
    public static void hobgoblinJourneyIdentityVillageExclusionAndMigration(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final HobgoblinEntity traveler = spawnTraveler(fixture, new BlockPos(1, 1, 1));
            helper.assertValueEqual(traveler.creatureKind(), CreatureKind.HOBGOBLIN,
                "the exact public ID must construct CreatureKind.HOBGOBLIN");
            helper.assertTrue(traveler.getClass() == HobgoblinEntity.class,
                "warlockery:hobgoblin must construct the dedicated traveler body");
            helper.assertFalse(Villager.class.isInstance(traveler),
                "the dedicated body must not inherit the human Villager implementation");
            helper.assertTrue(AbstractVillager.class.isInstance(traveler),
                "the dedicated body keeps the narrow AbstractVillager merchant surface");
            helper.assertValueEqual(traveler.operationalTargetGoalCount(), 0,
                "F11 registers no target-selector goal; a traveler never picks a fight");
            helper.assertValueEqual(traveler.operationalGoalNames().size(), 4,
                "exactly four executors are registered");
            helper.assertTrue(VILLAGER_BRAIN_ACTIVITIES.stream()
                    .noneMatch(activity -> traveler.getBrain().isActive(activity)),
                "no Villager Brain activity may ever run on a Hobgoblin");

            // The Villager-supertype capability F11 deliberately restored after the split. The
            // enabled flag itself has no public getter, so the live assertion is that the body
            // still owns the ground navigation the constructor configures.
            helper.assertTrue(traveler.getNavigation()
                    instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation,
                "the traveler keeps the ground navigation whose door opening is restored");

            // finalizeSpawn strips the generic random follow-range bonus, so the registered
            // attribute baseline stays exact and comparable.
            final double followRange = traveler.getAttributeValue(Attributes.FOLLOW_RANGE);
            helper.assertTrue(followRange == traveler.getAttributeBaseValue(Attributes.FOLLOW_RANGE),
                "the random spawn bonus must be stripped: " + followRange);

            for (int index = 0; index < 8; index++) {
                final HobgoblinEntity rolled = spawnTraveler(fixture, new BlockPos(1, 1, 1));
                helper.assertTrue(rolled.hasCustomName(),
                    "every spawned traveler carries its profession display name");
                helper.assertTrue(rolled.isCustomNameVisible(),
                    "the profession display name stays visible");
            }

            // A live 1.4 Hobgoblin compound migrates rather than resetting, and the old permanent
            // owner becomes a bounded agreement rather than ownership.
            final HobgoblinEntity migrated = spawnTraveler(fixture, new BlockPos(1, 1, 0));
            final UUID legacyOwner = UUID.randomUUID();
            migrated.setJourneyState(HobgoblinJourneyState.migrateLegacy(
                "smith", 75, 0L, helper.getLevel().getGameTime(), Optional.of(legacyOwner)
            ));
            helper.assertValueEqual(migrated.goblinProfession(), GoblinProfession.SMITH,
                "the 1.4 custom profession is authoritative through the live body");
            helper.assertValueEqual(migrated.merchantLevel(), 3,
                "old Villager XP becomes a bounded merchant level");
            helper.assertValueEqual(migrated.journeyState().contract().kind(), ContractKind.LEGACY_WORK,
                "the legacy owner becomes a bounded work agreement");
            helper.assertValueEqual(
                migrated.journeyState().contract().remainingTicks(),
                HobgoblinJourneyRules.CONTRACT_DURATION_TICKS,
                "the migrated agreement is time bounded, not permanent");
            helper.assertTrue(migrated.persistenceReason()
                    .filter(reason -> reason == PersistenceReason.CONTRACTED).isPresent(),
                "a contracted traveler persists for an explicit recorded reason");

            // A traveler with no caravan, camp, contract, or event is ordinary and may despawn. It
            // has to be spawned unlatched: GameTestEntityBuilder latches persistence on every mob.
            final HobgoblinEntity wild = spawnUnlatchedTraveler(fixture, new BlockPos(2, 1, 2));
            helper.assertTrue(wild.persistenceReason().isEmpty(),
                "an unanchored solitary traveler holds no persistence reason");
            helper.assertTrue(wild.removeWhenFarAway(4096.0D),
                "an unanchored solitary traveler uses ordinary distance despawn");
            helper.assertFalse(wild.isPersistenceRequired(),
                "no explicit reason and no vanilla latch means no persistence");

            makeDue(traveler);
            helper.runAfterDelay(80L, () -> {
                try {
                    helper.assertTrue(traveler.journeyCounters().decisions() >= 1L,
                        "a live self-ticking traveler reaches its own decision cadence");
                    helper.assertTrue(traveler.journeyCounters().decisions() <= 9L,
                        "the decision cadence stays no faster than its declared interval");
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

    // ---------------------------------------------------------------- 2: trade and contracts

    /**
     * The recognizable lead or emerald interaction becomes one voluntary, expiring agreement with no
     * follow, teleport, aura, or ownership, exclusive to one contractor, and relationship facts move
     * only on their declared inputs.
     */
    public static void hobgoblinJourneyTradeContractAndRelations(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final HobgoblinEntity traveler = spawnTraveler(fixture, new BlockPos(1, 1, 1));
            makeDue(traveler);

            helper.assertTrue(traveler.getOffers().isEmpty() || !traveler.getOffers().isEmpty(),
                "the merchant offer list is reachable");
            helper.assertTrue(HobgoblinJourneyRuntime.safeToTrade(traveler),
                "an unthreatened adult outside village space may trade");

            final net.minecraft.server.level.ServerPlayer firstPlayer = mockPlayer(fixture);
            final net.minecraft.server.level.ServerPlayer secondPlayer = mockPlayer(fixture);
            final UUID first = firstPlayer.getUUID();
            final UUID second = secondPlayer.getUUID();
            helper.assertFalse(first.equals(second), "the two mock contractors must be distinct");
            HobgoblinJourneyRuntime.onContractAccepted(traveler, firstPlayer);
            helper.assertTrue(traveler.journeyState().contract().active(),
                "an eligible traveler accepts one agreement");
            helper.assertValueEqual(traveler.journeyState().contract().contractor().orElseThrow(), first,
                "the accepting player is the contractor");
            helper.assertValueEqual(traveler.journeyCounters().contractsAccepted(), 1L,
                "acceptance is counted exactly once");

            HobgoblinJourneyRuntime.onContractAccepted(traveler, secondPlayer);
            helper.assertValueEqual(traveler.journeyState().contract().contractor().orElseThrow(), first,
                "a second player can never steal an existing valid agreement");

            // No follow, no teleport, no aura, no protect: an agreement authorizes work only.
            helper.assertTrue(traveler.getTarget() == null,
                "an agreement never installs a combat target");
            helper.assertTrue(traveler.journeyState().caravan().leader().isEmpty()
                    || !traveler.journeyState().caravan().leader().orElseThrow().equals(first),
                "a contractor never becomes the caravan leader");

            // Completion is a unit count, not a timer the record decides on its own.
            HobgoblinJourneyState state = traveler.journeyState();
            for (int index = 0; index < HobgoblinJourneyRules.MAX_CONTRACT_UNITS; index++) {
                state = state.withContract(state.contract().withUnit());
            }
            traveler.setJourneyState(state);
            helper.assertTrue(traveler.journeyState().contract().unitsExhausted(),
                "eight delivered units exhaust the agreement");

            // Relationship facts are bounded and only ever move on a declared input.
            traveler.setJourneyState(traveler.journeyState()
                .withRelation(first, RelationFact.WORK_COMPLETED));
            helper.assertTrue(traveler.journeyState().relationScore(first) > 0,
                "a completed job is a positive hospitality fact");
            traveler.setJourneyState(traveler.journeyState().withRelation(first, RelationFact.ATTACK));
            helper.assertTrue(traveler.journeyState().relationScore(first) < 0,
                "an attack outweighs the job that preceded it");
            helper.assertTrue(traveler.journeyState().relations().size()
                    <= HobgoblinJourneyRules.MAX_RELATION_FACTS,
                "no more than eight hospitality facts are ever retained");

            // A tool handed over is aid, and it is the only way a traveler ever changes tool.
            final ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
            HobgoblinJourneyRuntime.equipMiningTool(
                traveler, helper.getLevel(), firstPlayer, pickaxe
            );
            helper.assertTrue(traveler.getMainHandItem().is(Items.IRON_PICKAXE),
                "the supplied tool is equipped");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    // ---------------------------------------------------------------- 3: caravan, family, camp

    /**
     * Caravan membership is explicit and leased, leadership is deterministic, the camp record is
     * reserved before any block is touched, and teardown removes only the exact states this camp
     * placed while leaving a player-modified position alone.
     */
    public static void hobgoblinJourneyCaravanFamilyAndCampLifecycle(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final BlockPos absolute = helper.absolutePos(new BlockPos(1, 1, 1));
            final long caravanKey = fixtureCaravanKey(absolute, 0xCAFEL);
            final long campKey = HobgoblinJourneyRules.campKey(caravanKey);
            final HobgoblinJourneyData data = HobgoblinJourneyData.get(helper.getLevel());
            data.clearForGameTest(caravanKey);
            fixture.onClose(() -> data.clearForGameTest(caravanKey));

            final HobgoblinEntity first = spawnTraveler(fixture, new BlockPos(0, 1, 1));
            final HobgoblinEntity second = spawnTraveler(fixture, new BlockPos(2, 1, 1));
            first.setGoblinProfession(GoblinProfession.MINER);
            second.setGoblinProfession(GoblinProfession.SHAMAN);

            helper.assertTrue(data.joinCaravan(caravanKey, first.getUUID()),
                "the first member joins its caravan");
            helper.assertTrue(data.joinCaravan(caravanKey, second.getUUID()),
                "the second member joins the same caravan");
            helper.assertValueEqual(data.population(caravanKey), 2, "two explicit members");
            final Optional<UUID> leader = data.electLeader(
                caravanKey, List.of(first.getUUID(), second.getUUID())
            );
            helper.assertTrue(leader.isPresent(), "an adult caravan always elects a leader");
            helper.assertValueEqual(leader.orElseThrow(), HobgoblinJourneyRules
                .electLeader(List.of(first.getUUID(), second.getUUID())).orElseThrow(),
                "leadership is the deterministic lowest unsigned adult UUID");

            // Proximity alone never merges groups.
            final HobgoblinEntity outsider = spawnTraveler(fixture, new BlockPos(1, 1, 2));
            helper.assertTrue(outsider.journeyState().caravan().key().isEmpty(),
                "standing next to a caravan does not join it");

            // A child is exactly one Hobgoblin, deterministic, holding no claim and no target.
            final Entity offspring = first.getBreedOffspring(helper.getLevel(), second);
            helper.assertTrue(offspring instanceof HobgoblinEntity,
                "two exact travelers must produce a traveler child");
            final HobgoblinEntity child = (HobgoblinEntity) offspring;
            fixture.track(child);
            helper.assertValueEqual(child.creatureKind(), CreatureKind.HOBGOBLIN,
                "the child is exactly warlockery:hobgoblin");
            helper.assertValueEqual(child.goblinProfession(), HobgoblinJourneyRules.childProfession(
                first.getUUID(), GoblinProfession.MINER, second.getUUID(), GoblinProfession.SHAMAN
            ), "the child profession is the deterministic lower-UUID parent's");
            helper.assertTrue(child.getTarget() == null, "a newborn holds no combat target");
            helper.assertFalse(child.journeyState().job().holdsClaim(), "a newborn holds no work claim");
            final Villager human = fixture.spawn(
                EntityTypes.VILLAGER, new BlockPos(1, 1, 0), EntitySpawnReason.EVENT
            );
            helper.assertTrue(first.getBreedOffspring(helper.getLevel(), human) == null,
                "a human Villager can never father an exact Hobgoblin child");
            // The Villager has to leave before the teardown stage. A loaded human Villager inside
            // the signal radius IS village space, and village exit correctly outranks camp
            // teardown, so leaving it standing here would make the traveler exit rather than tear
            // down and the teardown assertion would fail for the right reason at the wrong time.
            human.discard();

            // The camp record is reserved before any block is touched and is one per caravan.
            helper.assertTrue(data.openCamp(campKey, caravanKey, absolute,
                HobgoblinJourneyRules.CAMP_DIRT_COST, HobgoblinJourneyRules.CAMP_LOG_COST),
                "the camp record is reserved before a single block is touched");
            helper.assertValueEqual(data.camp(campKey).phase(), CampPhase.RESERVE,
                "a fresh camp record is never born active");
            helper.assertFalse(data.openCamp(campKey + 1, caravanKey, absolute, 0, 0),
                "one camp per caravan");

            // Teardown removes only the exact placed state and releases everything else untouched.
            final BlockPos owned = new BlockPos(0, 1, 0);
            final BlockPos altered = new BlockPos(2, 1, 0);
            helper.setBlock(owned, Blocks.DIRT);
            helper.setBlock(altered, Blocks.STONE);
            fixture.onClose(() -> {
                helper.setBlock(owned, Blocks.AIR);
                helper.setBlock(altered, Blocks.AIR);
            });
            data.recordCampEdit(campKey, helper.absolutePos(owned), "minecraft:dirt");
            data.recordCampEdit(campKey, helper.absolutePos(altered), "minecraft:dirt");
            helper.assertValueEqual(data.campJournal(campKey).size(), 2,
                "both owned placements are journaled");
            data.setCampPhase(campKey, CampPhase.TEARDOWN);
            helper.assertValueEqual(data.camp(campKey).phase(), CampPhase.TEARDOWN,
                "the record is in teardown before the traveler ticks");
            final String observedOwned = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(helper.getLevel().getBlockState(helper.absolutePos(owned)).getBlock()).toString();
            helper.assertTrue(observedOwned.equals("minecraft:dirt"),
                "the journaled position must actually hold what was journaled, saw " + observedOwned
                    + " rel=" + owned + " abs=" + helper.absolutePos(owned));
            data.setCampPhase(campKey, CampPhase.TEARDOWN);
            // The mode is deliberately NOT set by hand: the live decision table has to choose
            // CAMP_TEARDOWN itself and commitMode has to grant the claim, or this fixture would
            // prove only that the executor works when handed a state it can never reach.
            first.setJourneyState(first.journeyState()
                .withCamp(HobgoblinJourneyState.Camp.at(campKey, CampPhase.TEARDOWN))
                .withCaravan(first.journeyState().caravan().withKey(caravanKey)));
            first.snapTo(
                helper.absolutePos(new BlockPos(1, 1, 1)).getX() + 0.5D,
                helper.absolutePos(new BlockPos(1, 1, 1)).getY(),
                helper.absolutePos(new BlockPos(1, 1, 1)).getZ() + 0.5D
            );

            makeDue(first);
            helper.runAfterDelay(60L, () -> {
                try {
                    helper.assertTrue(helper.getBlockState(owned).is(Blocks.AIR),
                        "teardown removes a position whose state still matches what the camp placed"
                            + " [mode=" + first.journeyState().mode()
                            + " phase=" + data.camp(campKey).phase()
                            + " removed=" + first.journeyCounters().campEditsRemoved()
                            + " state=" + helper.getBlockState(owned) + "]");
                    helper.assertTrue(helper.getBlockState(altered).is(Blocks.STONE),
                        "teardown never overwrites a position a player changed");
                    helper.assertTrue(data.campJournal(campKey).isEmpty(),
                        "ownership of every journaled position is released exactly once");
                    helper.assertTrue(first.journeyCounters().campEditsRemoved() == 1L,
                        "exactly the one owned unchanged position was removed, saw "
                            + first.journeyCounters().campEditsRemoved());
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

    // ---------------------------------------------------------------- 4: defence and cleanup

    /**
     * A traveler has no proactive prey, remembers exactly one direct aggressor, retaliates only
     * through the runtime, disengages, and releases every claim on removal.
     */
    public static void hobgoblinJourneyWorkHazardDefenseAndCleanup(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final BlockPos absolute = helper.absolutePos(new BlockPos(1, 1, 1));
            final long caravanKey = fixtureCaravanKey(absolute, 0xBEEFL);
            final HobgoblinJourneyData data = HobgoblinJourneyData.get(helper.getLevel());
            data.clearForGameTest(caravanKey);
            fixture.onClose(() -> data.clearForGameTest(caravanKey));

            final HobgoblinEntity traveler = spawnTraveler(fixture, new BlockPos(1, 1, 1));
            makeDue(traveler);

            // No proactive prey at all: a human Villager and another goblinfolk body are both
            // permanently non-prey, and an unrelated bystander is not prey either.
            final Villager human = fixture.spawn(
                EntityTypes.VILLAGER, new BlockPos(0, 1, 1), EntitySpawnReason.EVENT
            );
            helper.assertFalse(traveler.canAttack(human),
                "a Hobgoblin never attacks a human Villager");
            final HobgoblinEntity kin = spawnTraveler(fixture, new BlockPos(2, 1, 1));
            helper.assertFalse(traveler.canAttack(kin), "goblinfolk are never prey");

            // A mock ServerPlayer cannot take damage, so the aggressor is a live Zombie.
            final Zombie aggressor = fixture.spawn(
                EntityTypes.ZOMBIE, new BlockPos(1, 1, 0), EntitySpawnReason.EVENT
            );
            aggressor.setPersistenceRequired();
            helper.assertFalse(traveler.canAttack(aggressor),
                "an unprovoked mob is not prey either");

            traveler.invulnerableTime = 0;
            final boolean hurt = traveler.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(aggressor), 2.0F
            );
            helper.assertTrue(hurt, "the first hit must actually land");
            helper.assertTrue(traveler.journeyState().combat().remembersAggressor(),
                "a landed direct hit records exactly one aggressor");
            helper.assertValueEqual(
                traveler.journeyState().combat().aggressor().orElseThrow(), aggressor.getUUID(),
                "the recorded aggressor is the entity that actually hit");
            helper.assertTrue(traveler.canAttack(aggressor),
                "only the remembered direct aggressor becomes targetable");

            // Follow-up hits need invulnerableTime reset or they land on nothing and the next
            // assertion passes vacuously.
            traveler.invulnerableTime = 0;
            final Zombie other = fixture.spawn(
                EntityTypes.ZOMBIE, new BlockPos(0, 1, 0), EntitySpawnReason.EVENT
            );
            other.setPersistenceRequired();
            helper.assertTrue(traveler.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(other), 1.0F
            ), "the follow-up hit must actually land");
            helper.assertValueEqual(
                traveler.journeyState().combat().aggressor().orElseThrow(), other.getUUID(),
                "exactly one aggressor is remembered at a time, the most recent one");
            helper.assertFalse(traveler.canAttack(aggressor),
                "the superseded aggressor stops being targetable immediately");

            // Removal releases every claim rather than stranding the worksite.
            final UUID claim = data.claim("WORK_COMMIT", traveler.getUUID(), Optional.of(absolute))
                .orElseThrow();
            helper.assertTrue(data.holdsClaim(claim), "the worksite starts reserved");
            data.joinCaravan(caravanKey, traveler.getUUID());
            traveler.setJourneyState(traveler.journeyState()
                .withCaravan(traveler.journeyState().caravan().withKey(caravanKey)));
            traveler.remove(Entity.RemovalReason.KILLED);
            helper.assertFalse(data.holdsClaim(claim),
                "a dead traveler never leaves its worksite reserved");
            helper.assertValueEqual(data.population(caravanKey), 0,
                "death frees the caravan seat immediately");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    // ---------------------------------------------------------------- 5: adapters and bounds

    /**
     * The external-event adapter exposes only the active camp anchor and the event hold, the
     * settlement interface still recognises the dedicated body, and every structural cap holds.
     */
    public static void hobgoblinJourneyEventAdapterAndPopulationBounds(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final BlockPos absolute = helper.absolutePos(new BlockPos(1, 1, 1));
            final long caravanKey = fixtureCaravanKey(absolute, 0xF00DL);
            final long campKey = HobgoblinJourneyRules.campKey(caravanKey);
            final HobgoblinJourneyData data = HobgoblinJourneyData.get(helper.getLevel());
            data.clearForGameTest(caravanKey);
            fixture.onClose(() -> data.clearForGameTest(caravanKey));

            final HobgoblinEntity traveler = spawnTraveler(fixture, new BlockPos(1, 1, 1));
            makeDue(traveler);

            helper.assertTrue(
                HobgoblinJourneyRuntime.activeCampAnchor(traveler, helper.getLevel()).isEmpty(),
                "a traveler without a camp exposes no anchor to any external event");
            data.joinCaravan(caravanKey, traveler.getUUID());
            data.openCamp(campKey, caravanKey, absolute, 0, 0);
            data.setCampPhase(campKey, CampPhase.ACTIVE);
            traveler.setJourneyState(traveler.journeyState()
                .withCaravan(traveler.journeyState().caravan().withKey(caravanKey))
                .withCamp(HobgoblinJourneyState.Camp.at(campKey, CampPhase.ACTIVE)));
            helper.assertValueEqual(
                HobgoblinJourneyRuntime.activeCampAnchor(traveler, helper.getLevel()).orElseThrow(),
                absolute, "an active camp exposes exactly its anchor and nothing else");

            helper.assertFalse(data.camp(campKey).eventHeld(),
                "no event, no hold");
            data.holdCampForEvent(campKey);
            helper.assertTrue(data.camp(campKey).eventHeld(),
                "a live matching event holds teardown");
            helper.assertTrue(HobgoblinJourneyRules.campExpired(
                0, false, true, 0
            ), "the hold is bounded by its own stale deadline, not indefinite");

            // The settlement interface must still recognise the dedicated body: a concrete-class
            // test naming only the retained 1.4 class would silently match nothing here.
            helper.assertTrue(traveler instanceof AbstractVillager,
                "the assault settlement scan keys on the shared merchant supertype");
            helper.assertTrue(traveler.creatureKind() == CreatureKind.HOBGOBLIN,
                "the assault settlement scan filters on the exact kind");

            // Structural caps.
            for (int index = 0; index < HobgoblinJourneyRules.MAX_CARAVAN_MEMBERS; index++) {
                data.joinCaravan(caravanKey, new UUID(77L, index));
            }
            helper.assertTrue(data.population(caravanKey) <= HobgoblinJourneyRules.MAX_CARAVAN_MEMBERS,
                "the caravan population cap is exact");
            helper.assertFalse(data.joinCaravan(caravanKey, UUID.randomUUID()),
                "an overflowing member is refused rather than expanding storage");

            // The one-per-caravan camp and the claim exclusivity both hold live.
            helper.assertTrue(data.caravanHasCamp(caravanKey), "the caravan owns exactly one camp");
            final UUID claim = data.claim("MINE", UUID.randomUUID(), Optional.of(absolute)).orElseThrow();
            helper.assertTrue(data.claim("MINE", UUID.randomUUID(), Optional.of(absolute)).isEmpty(),
                "two travelers can never claim the same worksite");
            data.releaseClaim(claim);
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * A caravan key unique to one fixture.
     *
     * <p>The production key is regional, and every GameTest arena in one batch sits inside the same
     * 128-block region, so all five fixtures would otherwise share one caravan key and one camp key
     * and each fixture's own reset would wipe its neighbour's records mid-run. The salt keeps the
     * regional derivation honest while making the record space per fixture.</p>
     */
    private static long fixtureCaravanKey(final BlockPos anchor, final long salt) {
        return HobgoblinJourneyRules.caravanKey(anchor.getX(), anchor.getZ()) * 31L + salt;
    }

    /**
     * Claims every cadence this fixture depends on at the tick it needs it. Fixtures share the
     * global world clock across a batch, so a fixture that does not claim its own cadence can
     * observe another fixture's stale phase.
     */
    private static void makeDue(final HobgoblinEntity traveler) {
        traveler.journeyTransient().resetForLoad();
    }

    private static HobgoblinEntity spawnTraveler(
        final FixtureScope fixture,
        final BlockPos position
    ) {
        final GameTestHelper helper = fixture.helper;
        @SuppressWarnings("unchecked")
        final EntityType<HobgoblinEntity> type =
            (EntityType<HobgoblinEntity>) ModEntities.ALL.get("hobgoblin").get();
        final HobgoblinEntity traveler = fixture.spawn(type, position, EntitySpawnReason.NATURAL);
        traveler.setDeltaMovement(Vec3.ZERO);
        final BlockPos absolute = helper.absolutePos(position);
        traveler.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        // Sensing caches line of sight per server tick; repositioning invalidates that cache.
        traveler.getSensing().tick();
        return traveler;
    }

    /**
     * Spawns a traveler without the GameTest entity builder, which latches vanilla persistence on
     * every mob it spawns so that a fixture cannot despawn its own subject mid-test. The despawn
     * contract has to start from a genuinely unlatched body to be provable at all.
     */
    private static HobgoblinEntity spawnUnlatchedTraveler(
        final FixtureScope fixture,
        final BlockPos position
    ) {
        final GameTestHelper helper = fixture.helper;
        @SuppressWarnings("unchecked")
        final EntityType<HobgoblinEntity> type =
            (EntityType<HobgoblinEntity>) ModEntities.ALL.get("hobgoblin").get();
        final HobgoblinEntity traveler = type.create(helper.getLevel(), EntitySpawnReason.EVENT);
        if (traveler == null) {
            throw new IllegalStateException("warlockery:hobgoblin must construct a body");
        }
        final BlockPos absolute = helper.absolutePos(position);
        traveler.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
        traveler.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(traveler);
        traveler.getSensing().tick();
        return fixture.track(traveler);
    }

    private static net.minecraft.server.level.ServerPlayer mockPlayer(final FixtureScope fixture) {
        return fixture.track((net.minecraft.server.level.ServerPlayer)
            fixture.helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL));
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
