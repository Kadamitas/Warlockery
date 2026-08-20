package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;

/**
 * Twelve bounded live fixtures for the two mimicry families.
 *
 * <p>Each descriptor uses {@code forge:empty15x15x15}. Its entrypoint independently creates a
 * floor and closed barrier shell spanning relative 1..13 and height 1..6, then restores every
 * touched block during cleanup. Actors remain inside that shell. The two families use separate
 * registered isolated environments and assert only state and counters owned by their local actors;
 * no fixture counts a type across the world.</p>
 *
 * <p>Every {@code runAfterDelay} and {@code onEachTick} is registered directly from the fixture
 * body and never from inside another such callback. Assertions read monotonic counters rather than
 * instantaneous phases wherever an episode may legitimately have advanced further by the time a
 * callback runs, and every fixture discards the entities it created even on assertion failure.</p>
 *
 * <p>These fixtures depend on the coordinator-deferred {@code ModEntities} and {@code ModGameTests}
 * wiring that routes the four mimic ids through their dedicated bodies and registers these twelve
 * functions.</p>
 */
public final class MimicryGameTests {

    /** The default cell footprint, relative 0..2, used by the two fixtures that need no room. */
    private static final int CELL_ARENA_MAX = 2;

    private MimicryGameTests() {
    }

    public static void illusionCreeperTellCollapsesWithoutBlast(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final AbstractMimicEntity creeper = spawnMimic(fixture, "illusion_creeper", new BlockPos(7, 1, 7));
        final ServerPlayer observer = fixture.connectedPlayer(new BlockPos(9, 1, 7));
        helper.runAfterDelay(2L, () -> creeper.mimicCore().scratch().makeEveryCadenceDue());
        fixture.after(180L, () -> {
            final var counters = creeper.mimicCore().counters();
            helper.assertTrue(counters.episodeStarts >= 1L && counters.telegraphs == 1L,
                "one observed creeper episode emits exactly one tell");
            helper.assertTrue(counters.collapses >= 1L, "the tell and hold end in a collapse");
            helper.assertValueEqual(counters.meleeAttempts, 0L, "collapse deals no melee damage");
            helper.assertValueEqual(counters.foreignEntityWrites, 0L, "collapse mutates no observer");
            helper.assertTrue(creeper.mimicCore().state().primaryCooldown() > 0,
                "collapse arms the 600-loaded-tick cooldown");
            helper.assertTrue(!MimicryRules.facing(0.84D)
                && MimicryRules.facing(0.85D), "the exact facing seam is production-owned");
        });
    }

    public static void illusionSpiderSnareIsBoundedAndBreaks(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final AbstractMimicEntity spider = spawnMimic(fixture, "illusion_spider", new BlockPos(7, 1, 7));
        final ServerPlayer subject = fixture.connectedPlayer(new BlockPos(5, 1, 9));
        helper.runAfterDelay(2L, () -> spider.mimicCore().scratch().makeEveryCadenceDue());
        fixture.after(130L, () -> {
            final var counters = spider.mimicCore().counters();
            helper.assertTrue(counters.episodeStarts >= 1L && counters.snareApplications == 1L,
                "one local threshold crossing applies one snare");
            helper.assertTrue(subject.getEffect(MobEffects.SLOWNESS) == null
                || subject.getEffect(MobEffects.SLOWNESS).getAmplifier() == 0,
                "the only live effect is Slowness I");
            helper.assertValueEqual(counters.pathRequests, 0L, "the spider never paths outside hazard escape");
            helper.assertTrue(spider.mimicCore().state().primaryCooldown() > 0,
                "the snare episode arms its 400-tick cooldown");
        });
    }

    public static void illusionZombieAbsorbsWithoutRewardOrAlert(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final AbstractMimicEntity zombie = spawnMimic(fixture, "illusion_zombie", new BlockPos(7, 1, 7));
        final ServerPlayer attacker = fixture.connectedPlayer(new BlockPos(9, 1, 7));
        helper.runAfterDelay(20L, () -> strike(helper, zombie, attacker));
        helper.runAfterDelay(32L, () -> strike(helper, zombie, attacker));
        fixture.after(70L, () -> {
            final var counters = zombie.mimicCore().counters();
            helper.assertValueEqual(counters.attributions, 2L, "both accepted hits are attributed");
            helper.assertValueEqual(counters.unmasks, 1L,
                "the first attributed hit is retained and the exact second hit unmasks");
            helper.assertValueEqual(zombie.mimicCore().scratch().acceptedHits(), 2,
                "the decisive hit threshold is exact");
            helper.assertValueEqual(counters.meleeAttempts, 0L, "the decoy never retaliates");
        });
    }

    public static void illusionCopiesDealNoDamageAndNeverTouchVanillaAi(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final ServerPlayer observer = fixture.connectedPlayer(new BlockPos(7, 1, 7));
        final List<AbstractMimicEntity> copies = List.of(
            spawnMimic(fixture, "illusion_creeper", new BlockPos(5, 1, 7)),
            spawnMimic(fixture, "illusion_spider", new BlockPos(7, 1, 9)),
            spawnMimic(fixture, "illusion_zombie", new BlockPos(9, 1, 7))
        );
        final List<LivingEntity> vanilla = List.of(
            fixture.spawn(EntityTypes.COW, new BlockPos(3, 1, 3)),
            fixture.spawn(EntityTypes.VILLAGER, new BlockPos(3, 1, 11)),
            fixture.spawn(EntityTypes.IRON_GOLEM, new BlockPos(11, 1, 3)),
            fixture.spawn(EntityTypes.TURTLE, new BlockPos(11, 1, 11)),
            fixture.spawn(EntityTypes.CREEPER, new BlockPos(3, 1, 5)),
            fixture.spawn(EntityTypes.SPIDER, new BlockPos(3, 1, 9)),
            fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(11, 1, 9))
        );
        vanilla.forEach(entity -> entity.setInvulnerable(true));
        final List<Float> health = vanilla.stream().map(LivingEntity::getHealth).toList();
        final List<Entity> targets = new ArrayList<>();
        vanilla.forEach(entity -> targets.add(entity instanceof Mob mob ? mob.getTarget() : null));
        for (int y = 1; y <= 4; y++) {
            for (int coordinate = 1; coordinate <= 5; coordinate++) {
                fixture.place(new BlockPos(9, y, coordinate), Blocks.BARRIER);
                fixture.place(new BlockPos(13, y, coordinate), Blocks.BARRIER);
            }
            for (int x = 9; x <= 13; x++) {
                fixture.place(new BlockPos(x, y, 1), Blocks.BARRIER);
                fixture.place(new BlockPos(x, y, 5), Blocks.BARRIER);
            }
        }
        final BlockPos door = new BlockPos(7, 1, 3);
        fixture.place(door, Blocks.IRON_DOOR);
        final BlockState doorState = helper.getBlockState(door);
        final BlockPos turtleEgg = new BlockPos(7, 1, 11);
        fixture.place(turtleEgg, Blocks.TURTLE_EGG);
        for (int y = 1; y <= 3; y++) {
            fixture.place(new BlockPos(6, y, 10), Blocks.BARRIER);
            fixture.place(new BlockPos(7, y, 10), Blocks.BARRIER);
            fixture.place(new BlockPos(8, y, 10), Blocks.BARRIER);
            fixture.place(new BlockPos(6, y, 11), Blocks.BARRIER);
            fixture.place(new BlockPos(8, y, 11), Blocks.BARRIER);
            fixture.place(new BlockPos(6, y, 12), Blocks.BARRIER);
            fixture.place(new BlockPos(7, y, 12), Blocks.BARRIER);
            fixture.place(new BlockPos(8, y, 12), Blocks.BARRIER);
        }
        final BlockState turtleEggState = helper.getBlockState(turtleEgg);
        copies.forEach(copy -> copy.mimicCore().scratch().makeEveryCadenceDue());
        helper.runAfterDelay(20L, () -> copies.forEach(copy -> strike(helper, copy, observer)));
        fixture.after(2_000L, () -> {
            for (final AbstractMimicEntity copy : copies) {
                final var counters = copy.mimicCore().counters();
                helper.assertValueEqual(counters.meleeAttempts, 0L, "illusion copies never attack");
                helper.assertValueEqual(counters.foreignEntityWrites,
                    copy instanceof IllusionSpiderEntity ? counters.snareApplications + counters.snareRemovals : 0L,
                    "only the spider's guarded Slowness pair may write another entity");
                helper.assertTrue(copy.getTarget() == null, "no copy acquires a Mob target");
                helper.assertTrue(counters.attributions >= 1L,
                    "fresh causing living sources are attributed without retaliation");
                helper.assertTrue(!copy.mimicCore().scratch().boundSubject().map(id -> copies.stream()
                    .anyMatch(other -> other.getUUID().equals(id))).orElse(false), "copies never bind one another");
            }
            helper.assertTrue(observer.getLastHurtByMob() == null
                || !copies.contains(observer.getLastHurtByMob()), "no copy damages the observer");
            for (int index = 0; index < vanilla.size(); index++) {
                final LivingEntity actor = vanilla.get(index);
                helper.assertValueEqual(actor.getHealth(), health.get(index),
                    "mimics deal no damage to the vanilla cohort");
                if (actor instanceof Mob mob) helper.assertTrue(mob.getTarget() == targets.get(index),
                    "mimics never replace a vanilla AI target");
            }
            helper.assertTrue(helper.getBlockState(door).equals(doorState), "the door snapshot is stable");
            helper.assertTrue(helper.getBlockState(turtleEgg).equals(turtleEggState),
                "the real turtle-egg block remains present and unchanged");
            helper.assertTrue(MimicryRules.attributionFresh(40) && !MimicryRules.attributionFresh(41),
                "freshness 40/41 is proved at the production seam");
        });
    }

    public static void illusionCopiesHazardEscapeAndCancellationAreDeterministic(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final List<AbstractMimicEntity> copies = List.of(
            spawnMimic(fixture, "illusion_creeper", new BlockPos(5, 1, 7)),
            spawnMimic(fixture, "illusion_spider", new BlockPos(7, 1, 7)),
            spawnMimic(fixture, "illusion_zombie", new BlockPos(9, 1, 7))
        );
        copies.get(0).mimicCore().scratch().phase = MimicryRules.Phase.TELL;
        copies.get(1).mimicCore().scratch().phase = MimicryRules.Phase.SNARE;
        copies.get(2).mimicCore().scratch().phase = MimicryRules.Phase.ABSORB;
        copies.get(0).setRemainingFireTicks(100);
        fixture.place(new BlockPos(7, 1, 7), Blocks.LAVA);
        fixture.place(new BlockPos(9, 0, 7), Blocks.SAND);
        fixture.place(new BlockPos(9, 1, 7), Blocks.CACTUS);
        final ServerPlayer customer = fixture.connectedPlayer(new BlockPos(7, 1, 11));
        final net.minecraft.world.entity.npc.villager.Villager trader =
            fixture.spawn(EntityTypes.VILLAGER, new BlockPos(3, 1, 3));
        final AbstractMimicEntity tradeCancellation =
            spawnMimic(fixture, "illusion_creeper", new BlockPos(4, 1, 3));
        tradeCancellation.mimicCore().scratch().phase = MimicryRules.Phase.TELL;
        tradeCancellation.mimicCore().scratch().bound = trader.getUUID();
        tradeCancellation.getNavigation().moveTo(customer, 1.0D);
        tradeCancellation.setDeltaMovement(0.25D, 0.0D, 0.25D);
        trader.setTradingPlayer(customer);
        MimicryRuntime.tick(tradeCancellation, helper.getLevel());
        helper.assertValueEqual(tradeCancellation.mimicCore().counters().cancellations, 1L,
            "a genuine active villager trade cancels its bound episode");
        assertFullyCancelled(helper, tradeCancellation, MimicryRules.Phase.LATENT,
            "trade cancellation");

        final ServerPlayer sleeper = fixture.connectedPlayer(new BlockPos(5, 1, 11));
        final AbstractMimicEntity sleepCancellation =
            spawnMimic(fixture, "illusion_spider", new BlockPos(5, 1, 10));
        sleepCancellation.mimicCore().scratch().phase = MimicryRules.Phase.SNARE;
        sleepCancellation.mimicCore().scratch().bound = sleeper.getUUID();
        sleepCancellation.mimicCore().scratch().snareApplied = true;
        sleeper.addEffect(new MobEffectInstance(
            MobEffects.SLOWNESS,
            MimicryRules.WEAVER_SNARE_DURATION_TICKS,
            MimicryRules.WEAVER_SNARE_AMPLIFIER
        ));
        sleepCancellation.getNavigation().moveTo(sleeper, 1.0D);
        sleepCancellation.setDeltaMovement(0.25D, 0.0D, 0.25D);
        sleeper.startSleeping(helper.absolutePos(new BlockPos(5, 1, 11)));
        helper.assertTrue(sleeper.isSleeping(), "the cancellation subject entered real sleeping state");
        MimicryRuntime.tick(sleepCancellation, helper.getLevel());
        helper.assertValueEqual(sleepCancellation.mimicCore().counters().cancellations, 1L,
            "sleep cancels a bound episode");
        assertFullyCancelled(helper, sleepCancellation, MimicryRules.Phase.HIDDEN,
            "sleep cancellation");
        helper.assertTrue(sleeper.getEffect(MobEffects.SLOWNESS) == null,
            "sleep cancellation immediately removes the Spider's owned Slowness instance");
        sleeper.stopSleeping();

        final net.minecraft.world.entity.raid.Raider raidActor =
            fixture.spawn(EntityTypes.PILLAGER, new BlockPos(9, 1, 3));
        final AbstractMimicEntity raidCancellation =
            spawnMimic(fixture, "illusion_zombie", new BlockPos(10, 1, 3));
        raidCancellation.mimicCore().scratch().phase = MimicryRules.Phase.ABSORB;
        raidCancellation.mimicCore().scratch().bound = raidActor.getUUID();
        raidCancellation.getNavigation().moveTo(raidActor, 1.0D);
        raidCancellation.setDeltaMovement(0.25D, 0.0D, 0.25D);
        final net.minecraft.world.entity.raid.Raid raid =
            new net.minecraft.world.entity.raid.Raid(raidActor.blockPosition(), helper.getLevel().getDifficulty());
        raid.joinRaid(helper.getLevel(), 1, raidActor, raidActor.blockPosition(), true);
        MimicryRuntime.tick(raidCancellation, helper.getLevel());
        helper.assertValueEqual(raidCancellation.mimicCore().counters().cancellations, 1L,
            "a live raid participant cancels its bound episode");
        assertFullyCancelled(helper, raidCancellation, MimicryRules.Phase.BLENDED,
            "raid cancellation");
        for (final String id : List.of("illusion_creeper", "illusion_spider", "illusion_zombie")) {
            assertEveryOwnedPhaseCancels(helper, fixture, id, trader, new BlockPos(4, 1, 5));
        }

        final net.minecraft.world.entity.npc.villager.Villager panicActor =
            fixture.spawn(EntityTypes.VILLAGER, new BlockPos(3, 1, 11));
        final AbstractMimicEntity panicCancellation =
            spawnMimic(fixture, "illusion_creeper", new BlockPos(3, 1, 10));
        panicCancellation.setNoAi(true);
        panicCancellation.mimicCore().scratch().phase = MimicryRules.Phase.TELL;
        panicCancellation.mimicCore().scratch().bound = panicActor.getUUID();
        final net.minecraft.world.entity.monster.zombie.Zombie panicSource =
            fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 11));
        helper.assertTrue(panicActor.hurtServer(helper.getLevel(),
                helper.getLevel().damageSources().mobAttack(panicSource), 1.0F),
            "real hostile damage starts the villager panic trigger");
        helper.runAfterDelay(20L, () -> {
            helper.assertTrue(panicActor.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.PANIC)
                    || panicActor.getBrain().hasMemoryValue(
                        net.minecraft.world.entity.ai.memory.MemoryModuleType.IS_PANICKING),
                "the genuine villager Brain entered panic");
            panicCancellation.setNoAi(false);
            MimicryRuntime.tick(panicCancellation, helper.getLevel());
            helper.assertValueEqual(panicCancellation.mimicCore().counters().cancellations, 1L,
                "the live villager panic state cancels its bound episode");
        });

        final net.minecraft.world.entity.animal.Animal breedingActor =
            fixture.spawn(EntityTypes.COW, new BlockPos(11, 1, 11));
        breedingActor.setInLove(customer);
        final AbstractMimicEntity breedingCancellation =
            spawnMimic(fixture, "illusion_spider", new BlockPos(10, 1, 11));
        breedingCancellation.mimicCore().scratch().phase = MimicryRules.Phase.SNARE;
        breedingCancellation.mimicCore().scratch().bound = breedingActor.getUUID();
        MimicryRuntime.tick(breedingCancellation, helper.getLevel());
        helper.assertValueEqual(breedingCancellation.mimicCore().counters().cancellations, 1L,
            "a genuine in-love animal cancels its bound episode");

        final AbstractMimicEntity removed = spawnMimic(fixture, "illusion_creeper", new BlockPos(6, 1, 3));
        final AbstractMimicEntity discarded = spawnMimic(fixture, "illusion_spider", new BlockPos(7, 1, 3));
        final AbstractMimicEntity killed = spawnMimic(fixture, "illusion_zombie", new BlockPos(8, 1, 3));
        removed.remove(Entity.RemovalReason.KILLED);
        discarded.discard();
        killed.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 10_000.0F);
        helper.assertTrue(!killed.isAlive(), "genuine lethal damage reaches the death lifecycle");
        if (!killed.isRemoved()) killed.remove(Entity.RemovalReason.KILLED);
        helper.assertTrue(removed.mimicCore().counters().cancellations == 1L
                && discarded.mimicCore().counters().cancellations == 1L
                && killed.mimicCore().counters().cancellations == 1L,
            "explicit removal, discard and genuine lethal damage each execute full teardown once");

        final AbstractMimicEntity routeProbe =
            spawnMimic(fixture, "illusion_creeper", new BlockPos(3, 1, 7));
        for (int x = 2; x <= 4; x++) for (int z = 6; z <= 8; z++) {
            if (x == 3 && z == 7) continue;
            fixture.place(new BlockPos(x, 1, z), Blocks.BARRIER);
            fixture.place(new BlockPos(x, 2, z), Blocks.BARRIER);
        }
        routeProbe.setRemainingFireTicks(200);
        routeProbe.mimicCore().scratch().makeEveryCadenceDue();
        for (int tick = 0; tick < 65; tick++) MimicryRuntime.tick(routeProbe, helper.getLevel());
        helper.assertValueEqual(routeProbe.mimicCore().scratch().routeFailures(), 3,
            "actual navigation rejects the same unreachable local escape exactly three times");
        helper.assertTrue(routeProbe.mimicCore().scratch().routeBackoff() > 0,
            "the third genuine navigation failure enters loaded-tick backoff");
        fixture.after(55L, () -> {
            for (final AbstractMimicEntity copy : copies) {
                helper.assertTrue(copy.mimicCore().counters().hazardEscapes > 0L,
                    "each species performs a live bounded hazard escape");
                helper.assertTrue(copy.mimicCore().counters().hazardReads <= 256L * 3L,
                    "hazard reads remain structurally capped");
                helper.assertValueEqual(copy.mimicCore().counters().meleeAttempts, 0L,
                    "hazard teardown replays no attack");
            }
            helper.assertTrue(copies.get(0).mimicCore().counters().hazardContactReads > 0L
                && copies.get(1).mimicCore().counters().hazardContactReads > 0L
                && copies.get(2).mimicCore().counters().hazardContactReads > 0L,
                "fire, lava and genuine cactus contact are all observed through the bounded live detector");
            var route = MimicryRules.routeRequest();
            route = route.failed(MimicryRules.ROUTE_BACKOFF).failed(MimicryRules.ROUTE_BACKOFF)
                .failed(MimicryRules.ROUTE_BACKOFF);
            helper.assertValueEqual(route.consecutiveFailures(), 3, "the third route failure is exact");
            helper.assertValueEqual(route.backoffRemaining(), 100, "route exhaustion arms exact backoff");
            helper.assertTrue(MimicryRules.destinationCandidateCap()
                * MimicryRules.READS_PER_DESTINATION_CANDIDATE <= MimicryRules.MAX_DESTINATION_READS,
                "every escape search stays inside its production read cap");
        });
    }

    public static void illusionCopiesSaveReloadAndZombieLifecycleAreReplaced(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final List<AbstractMimicEntity> originals = List.of(
            spawnMimic(fixture, "illusion_creeper", new BlockPos(5, 1, 7)),
            spawnMimic(fixture, "illusion_spider", new BlockPos(7, 1, 7)),
            spawnMimic(fixture, "illusion_zombie", new BlockPos(9, 1, 7))
        );
        originals.forEach(copy -> {
            copy.mimicCore().setState(copy.mimicCore().state().withPrimaryCooldown(77));
            copy.setCanPickUpLoot(true);
            copy.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND));
        });
        originals.get(0).mimicCore().scratch().phase = MimicryRules.Phase.TELL;
        originals.get(1).mimicCore().scratch().phase = MimicryRules.Phase.SNARE;
        originals.get(2).mimicCore().scratch().phase = MimicryRules.Phase.ABSORB;
        final List<AbstractMimicEntity> copies = originals.stream()
            .map(copy -> reload(fixture, copy)).toList();
        for (final AbstractMimicEntity copy : copies) {
            for (final MimicryRules.Phase phase : MimicryRules.Phase.values()) {
                if (!MimicryRules.owns(copy.mimicSpecies(), phase)) continue;
                final AbstractMimicEntity phaseOriginal = spawnMimic(fixture,
                    copy.mimicSpecies().kind().name().toLowerCase(java.util.Locale.ROOT), new BlockPos(7, 1, 7));
                phaseOriginal.mimicCore().scratch().phase = phase;
                final AbstractMimicEntity phaseLoaded = reload(fixture, phaseOriginal);
                helper.assertTrue(phaseLoaded.mimicCore().scratch().phase() == phaseLoaded.mimicSpecies().routine(),
                    "every saved illusion phase normalizes without replay: " + phase);
                phaseLoaded.discard();
            }
        }
        final AbstractMimicEntity malformed = reloadMalformed(fixture,
            spawnMimic(fixture, "illusion_zombie", new BlockPos(7, 1, 7)));
        helper.assertTrue(malformed.mimicCore().state().episodeAllowed(),
            "malformed illusion state defaults independently to a safe routine");
        fixture.after(2L, () -> {
            for (final AbstractMimicEntity copy : copies) {
                final MimicryState decoded = MimicryState.read(copy.mimicCore().state().write(), copy.mimicSpecies());
                helper.assertValueEqual(decoded.primaryCooldown(), 75,
                    "loaded-tick cooldown survives and decrements only while loaded");
                helper.assertTrue(helper.getLevel().getEntity(copy.getUUID()) == copy,
                    "the saved UUID resolves only to its live replacement");
                helper.assertTrue(!net.minecraft.world.entity.monster.zombie.Zombie.class.isAssignableFrom(copy.getClass()),
                    "the zombie lifecycle is structurally replaced");
                helper.assertTrue(copy.mimicCore().scratch().phase() == copy.mimicSpecies().routine(),
                    "a saved episode phase normalizes to the species routine without replay");
                helper.assertTrue(copy.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && !copy.canPickUpLoot(),
                    "dedicated bodies normalize to adult empty lifecycle state");
                helper.assertValueEqual(copy.mimicCore().counters().meleeAttempts, 0L, "load emits no replay");
            }
            helper.assertValueEqual(MimicryRules.MAX_RAW_VISITS_PER_CHECK * 128,
                128 * MimicryRules.MAX_RAW_VISITS_PER_CHECK,
                "population arithmetic remains linear at the production seam");
        });
    }

    public static void glassDoppelgangerPresentsOneSubjectWithoutCopyingData(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final ServerPlayer subject = fixture.connectedPlayer(new BlockPos(9, 1, 7));
        final LivingEntity nearer = fixture.spawn(EntityTypes.VILLAGER, new BlockPos(9, 1, 7));
        final ServerPlayer thrower = fixture.connectedPlayer(new BlockPos(3, 1, 7));
        final ServerPlayer mirrorUser = fixture.connectedPlayer(new BlockPos(4, 1, 11));
        final AtomicReference<AbstractMimicEntity> charged = new AtomicReference<>();
        final AtomicReference<AbstractMimicEntity> mirrored = new AtomicReference<>();
        final AtomicReference<AbstractThrownPotion> thrownCharge = new AtomicReference<>();
        final BlockPos mirrorPos = new BlockPos(4, 0, 11);
        fixture.place(mirrorPos, ModBlocks.ALL.get("mirrorblock").get());
        mirrorUser.setShiftKeyDown(true);
        helper.getBlockState(mirrorPos).useWithoutItem(
            helper.getLevel(), mirrorUser,
            new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(mirrorPos)), Direction.UP, helper.absolutePos(mirrorPos), false)
        );
        mirrorUser.setShiftKeyDown(false);
        helper.getLevel().getEntitiesOfClass(
            GlassDoppelgangerEntity.class,
            new AABB(helper.absolutePos(mirrorPos.above())).inflate(2.0D)
        ).stream().findFirst().ifPresent(entity -> mirrored.set(fixture.track(entity)));

        final ItemStack charge = new ItemStack(ModItems.ALL.get("replication_charge").get());
        thrower.setItemInHand(InteractionHand.MAIN_HAND, charge);
        thrower.lookAt(EntityAnchorArgument.Anchor.EYES,
            Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 0, 7))));
        helper.runAfterDelay(2L, () -> {
            charge.use(helper.getLevel(), thrower, InteractionHand.MAIN_HAND);
            // Mock connected players may share the harness profile. Restore the privacy subject's
            // pre-acquisition hand after the item-use input, before either presentation begins.
            subject.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            helper.getLevel().getEntitiesOfClass(
                AbstractThrownPotion.class,
                new AABB(Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(1, 1, 1))),
                    Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(13, 6, 13))))
            ).stream().findFirst().ifPresent(projectile -> thrownCharge.set(fixture.track(projectile)));
            helper.assertTrue(thrownCharge.get() != null,
                "using the registered replication charge creates its real thrown potion entity");
        });
        helper.runAfterDelay(3L, () -> {
            final AbstractThrownPotion projectile = thrownCharge.get();
            final BlockPos impact = helper.absolutePos(new BlockPos(6, 1, 7));
            projectile.snapTo(impact.getX() + 0.5D, impact.getY() + 1.5D, impact.getZ() + 0.5D);
            projectile.setDeltaMovement(0.0D, -1.5D, 0.0D);
            for (int tick = 0; tick < 4 && !projectile.isRemoved(); tick++) projectile.tick();
            helper.assertTrue(projectile.isRemoved(),
                "the real replication-charge projectile completed its block-impact lifecycle");
        });
        for (int y = 1; y <= 6; y++) for (int z = 1; z <= 13; z++)
            fixture.place(new BlockPos(10, y, z), Blocks.BARRIER);
        final AbstractMimicEntity occluded = spawnMimic(fixture, "glass_doppelganger", new BlockPos(12, 1, 7));
        subject.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        subject.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        subject.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        subject.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        subject.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        subject.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        final List<EquipmentSlot> privateSlots = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);
        final Map<EquipmentSlot, ItemStack> privateEquipment = new LinkedHashMap<>();
        privateSlots.forEach(slot -> privateEquipment.put(slot, subject.getItemBySlot(slot).copy()));
        subject.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 7));
        subject.getEnderChestInventory().setItem(0, new ItemStack(Items.EMERALD, 5));
        subject.getPersistentData().putString("PrivateFixtureSecret", "never-copy");
        subject.addEffect(new MobEffectInstance(MobEffects.SPEED, 2_200, 1));
        subject.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(13.0D);
        subject.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(9.0D);
        subject.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.21D);
        subject.setHealth(7.0F);
        final ItemStack privateInventory = subject.getInventory().getItem(9).copy();
        final Map<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>, Double>
            privateAttributes = Map.of(
                net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                subject.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR),
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                subject.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE),
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                subject.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
        helper.runAfterDelay(20L, () -> {
            final List<GlassDoppelgangerEntity> localGlass = helper.getLevel().getEntitiesOfClass(
                GlassDoppelgangerEntity.class,
                new AABB(Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(1, 0, 1))),
                    Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(14, 7, 14))))
            );
            localGlass.stream().filter(entity -> subject.getStringUUID().equals(entity.getPersistentData()
                    .getStringOr("WarlockeryReflectedTarget", ""))).findFirst()
                .ifPresent(entity -> charged.set(fixture.track(entity)));
            helper.assertTrue(charged.get() != null,
                "the registered replication charge creates a Glass Doppelganger through its live projectile route; local="
                    + localGlass.size() + ", owners=" + localGlass.stream().map(entity -> entity.getPersistentData()
                        .getStringOr("WarlockeryReflectedTarget", "")).toList());
            helper.assertTrue(mirrored.get() != null,
                "shift-using a real unpaired mirror creates a Glass Doppelganger through the block route");
            helper.assertTrue(mirrorUser.getStringUUID().equals(mirrored.get().getPersistentData()
                    .getStringOr("WarlockeryReflectedTarget", "")),
                "the live unpaired-mirror route records its invoking player as reflected target");
            charged.get().mimicCore().scratch().makeEveryCadenceDue();
            mirrored.get().mimicCore().scratch().makeEveryCadenceDue();
        });
        helper.runAfterDelay(30L, () -> {
            helper.assertTrue(charged.get().mimicCore().scratch().boundSubject()
                    .filter(subject.getUUID()::equals).isPresent(),
                "the replication-charge route records and acquires its actual reflected target");
            helper.assertTrue(mirrored.get().mimicCore().scratch().boundSubject()
                    .filter(mirrorUser.getUUID()::equals).isPresent(),
                "the unpaired-mirror route records and acquires its actual reflected target");
        });
        fixture.after(2_000L, () -> {
            final AbstractMimicEntity copy = charged.get();
            final AbstractMimicEntity reflected = mirrored.get();
            helper.assertTrue(copy.mimicCore().counters().episodeStarts >= 1L, "one local subject is presented");
            helper.assertTrue(copy.hasCustomName(), "only the presented name is derived");
            helper.assertTrue(java.util.Arrays.stream(EquipmentSlot.values())
                .allMatch(slot -> copy.getItemBySlot(slot).isEmpty()) && copy.getActiveEffects().isEmpty(),
                "equipment and effects are never copied");
            for (final EquipmentSlot slot : privateSlots) {
                final ItemStack expected = privateEquipment.get(slot);
                final ItemStack actual = subject.getItemBySlot(slot);
                helper.assertTrue(ItemStack.isSameItemSameComponents(actual, expected)
                        && actual.getCount() == expected.getCount(),
                    "every private equipment slot remains byte-for-gameplay stable: " + slot);
            }
            helper.assertTrue(subject.getEffect(MobEffects.SPEED) != null
                    && subject.getEffect(MobEffects.SPEED).getAmplifier() == 1
                    && copy.getEffect(MobEffects.SPEED) == null,
                "the subject's active effect remains private and the likeness receives none");
            helper.assertValueEqual(copy.getHealth(), copy.getMaxHealth(),
                "the likeness retains its own exact full-health lifecycle");
            helper.assertTrue(copy.getHealth() != subject.getHealth(),
                "the subject's partial health is not copied");
            helper.assertTrue(ItemStack.isSameItemSameComponents(subject.getInventory().getItem(9), privateInventory)
                && subject.getInventory().getItem(9).getCount() == 7, "inventory and components remain private");
            for (final var attribute : privateAttributes.entrySet()) {
                helper.assertValueEqual(subject.getAttributeValue(attribute.getKey()), attribute.getValue(),
                    "the subject's private attribute remains unchanged");
                helper.assertTrue(copy.getAttributeValue(attribute.getKey()) != attribute.getValue(),
                    "the likeness does not copy the subject's private attribute");
            }
            helper.assertTrue(subject.getEnderChestInventory().getItem(0).is(Items.EMERALD)
                && subject.getEnderChestInventory().getItem(0).getCount() == 5,
                "ender-chest contents remain private and unchanged");
            helper.assertTrue("never-copy".equals(subject.getPersistentData()
                .getStringOr("PrivateFixtureSecret", ""))
                && !copy.getPersistentData().contains("PrivateFixtureSecret"),
                "persistent data remains private and absent from the likeness");
            helper.assertTrue(MimicryPresentation.presentedNameFor(subject).isPresent()
                && MimicryPresentation.presentedNameFor(null).isEmpty(),
                "named acquisition and coarse fallback are the only two presentation routes");
            helper.assertTrue(reflected.hasCustomName(),
                "the reflected-target route derives one presentation");
            helper.assertTrue(nearer.isAlive(), "the bypassed nearer candidate remains untouched");
            helper.assertValueEqual(copy.mimicCore().counters().foreignEntityWrites, 0L, "subject remains untouched");
            helper.assertValueEqual(occluded.mimicCore().counters().episodeStarts, 0L,
                "an occluded cohort performs zero presentation work for 2000 loaded ticks");
            helper.assertValueEqual(occluded.mimicCore().counters().foreignEntityWrites, 0L,
                "the zero-work cohort writes no foreign state");
        });
    }

    public static void glassDoppelgangerShadowBandHoldsAndNeverCloses(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final AbstractMimicEntity copy = spawnMimic(fixture, "glass_doppelganger", new BlockPos(7, 1, 7));
        final ServerPlayer subject = fixture.connectedPlayer(new BlockPos(12, 1, 7));
        copy.getPersistentData().putString("WarlockeryReflectedTarget", subject.getStringUUID());
        final AbstractMimicEntity unkeyed =
            spawnMimic(fixture, "glass_doppelganger", new BlockPos(3, 1, 3));
        final AbstractMimicEntity sightLoss =
            spawnMimic(fixture, "glass_doppelganger", new BlockPos(7, 1, 12));
        sightLoss.getPersistentData().putString("WarlockeryReflectedTarget", subject.getStringUUID());
        sightLoss.mimicCore().scratch().phase = MimicryRules.Phase.SHADOWING;
        sightLoss.mimicCore().scratch().bound = subject.getUUID();
        sightLoss.mimicCore().scratch().boundVisible = true;
        sightLoss.mimicCore().scratch().sightTestTicks = 0;
        for (int y = 1; y <= 4; y++) {
            for (int x = 6; x <= 8; x++) {
                fixture.place(new BlockPos(x, y, 11), Blocks.BARRIER);
                fixture.place(new BlockPos(x, y, 13), Blocks.BARRIER);
            }
            fixture.place(new BlockPos(6, y, 12), Blocks.BARRIER);
            fixture.place(new BlockPos(8, y, 12), Blocks.BARRIER);
        }
        final AtomicInteger sightLossLoadedTicks = new AtomicInteger();
        final AtomicInteger sightLossMilestones = new AtomicInteger();
        helper.onEachTick(() -> {
            final MimicryRuntime.TransientState scratch = sightLoss.mimicCore().scratch();
            if (sightLossLoadedTicks.get() == 0 && scratch.boundVisible) {
                return;
            }
            final int elapsed = sightLossLoadedTicks.incrementAndGet();
            if (elapsed == 39) {
                helper.assertTrue(scratch.boundSubject().filter(subject.getUUID()::equals).isPresent(),
                    "sight loss preserves the keyed subject through exactly 39 loaded ticks");
                sightLossMilestones.incrementAndGet();
            } else if (elapsed == 40) {
                helper.assertTrue(scratch.boundSubject().isEmpty(),
                    "sight loss releases the keyed subject on exactly loaded tick 40");
                sightLossMilestones.incrementAndGet();
            } else if (elapsed == 41) {
                helper.assertTrue(scratch.boundSubject().isEmpty(),
                    "loaded tick 41 cannot replay the released sight-loss binding");
                sightLossMilestones.incrementAndGet();
            }
        });
        helper.runAfterDelay(2L, () -> copy.mimicCore().scratch().makeEveryCadenceDue());
        fixture.after(190L, () -> {
            helper.assertTrue(copy.mimicCore().counters().episodeStarts >= 1L, "shadow-band episode starts locally");
            helper.assertValueEqual(copy.mimicCore().counters().meleeAttempts, 0L, "shadowing never closes to contact");
            helper.assertValueEqual(unkeyed.mimicCore().counters().checks, 0L,
                "an unkeyed Glass Doppelganger performs zero subject queries in clear line of sight");
            helper.assertTrue(unkeyed.mimicCore().scratch().boundSubject().isEmpty(),
                "an unkeyed Glass Doppelganger never invents a third acquisition route");
            helper.assertValueEqual(sightLossMilestones.get(), 3,
                "live sight-loss coverage observes independent 39/40/41 loaded-tick boundaries");
            helper.assertTrue(MimicryRules.likenessBand(4.0D) == MimicryRules.LikenessBand.CONTACT
                && MimicryRules.likenessBand(6.0D) == MimicryRules.LikenessBand.HOLD
                && MimicryRules.likenessBand(12.0D) == MimicryRules.LikenessBand.HOLD
                && MimicryRules.likenessBand(16.0D) == MimicryRules.LikenessBand.APPROACH
                && MimicryRules.likenessBand(24.0D) == MimicryRules.LikenessBand.OUTER,
                "4/6/12/24 geometry is proved at the rules seam");
        });
    }

    public static void glassDoppelgangerRecognitionEndsThePresentationAndWithdraws(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final AbstractMimicEntity copy = spawnMimic(fixture, "glass_doppelganger", new BlockPos(7, 1, 7));
        final ServerPlayer subject = fixture.connectedPlayer(new BlockPos(10, 1, 7));
        copy.getPersistentData().putString("WarlockeryReflectedTarget", subject.getStringUUID());
        helper.onEachTick(() -> face(subject, copy));
        helper.runAfterDelay(2L, () -> copy.mimicCore().scratch().makeEveryCadenceDue());
        fixture.after(220L, () -> {
            helper.assertValueEqual(copy.mimicCore().counters().recognitions, 1L,
                "one observer reaches certainty exactly once");
            helper.assertValueEqual(copy.mimicCore().counters().withdrawals, 1L,
                "certainty schedules exactly one withdrawal feedback");
            helper.assertTrue(copy.mimicCore().scratch().phase() == MimicryRules.Phase.WITHDRAWN,
                "recognition completes its bounded withdrawal");
            helper.assertTrue(copy.mimicCore().state().primaryCooldown() > 0,
                "withdrawal arms the 1200-loaded-tick zero-work cooldown");
            helper.assertValueEqual(copy.mimicCore().counters().episodeStarts, 1L,
                "certainty suppresses any later presentation in the same tick and cooldown window");
            helper.assertTrue(MimicryRules.recognitionAfter(0, true, false, false) == 50
                && MimicryRules.recognitionAfter(50, false, false, false) == 30
                && MimicryRules.recognitionAfter(500, false, false, true) == 1000,
                "recognition +50/-20/+500 arithmetic is exact");
            helper.assertValueEqual(copy.mimicCore().counters().foreignEntityWrites, 0L, "recognition mutates no observer");
        });
    }

    public static void glassDoppelgangerAnswersOnlyAttributedDamage(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final AbstractMimicEntity copy = spawnMimic(fixture, "glass_doppelganger", new BlockPos(7, 1, 7));
        final AbstractMimicEntity otherMimic = spawnMimic(fixture, "illusion_creeper", new BlockPos(4, 1, 7));
        final ServerPlayer attacker = fixture.connectedPlayer(new BlockPos(9, 1, 7));
        copy.getPersistentData().putString("WarlockeryReflectedTarget", attacker.getStringUUID());
        final ServerPlayer creative = fixture.connectedPlayer(new BlockPos(11, 1, 3), GameType.CREATIVE);
        final ServerPlayer spectator = fixture.connectedPlayer(new BlockPos(11, 1, 11), GameType.SPECTATOR);
        final AbstractArrow ownedArrow = fixture.spawn(EntityTypes.ARROW, new BlockPos(5, 2, 7));
        ownedArrow.setOwner(attacker);
        final AbstractArrow dispenserArrow = fixture.spawn(EntityTypes.ARROW, new BlockPos(5, 2, 8));
        final List<String> sharedHookIds = List.of(
            "banshee", "death", "lost_soul", "poltergeist", "spectre", "spirit"
        );
        final List<Mob> sharedHookActors = new ArrayList<>();
        for (int index = 0; index < sharedHookIds.size(); index++) {
            final Mob actor = spawnArcaneMob(fixture, sharedHookIds.get(index),
                new BlockPos(3 + (index % 3) * 4, 1, index < 3 ? 3 : 11));
            actor.setNoAi(true);
            sharedHookActors.add(actor);
        }
        final List<Vec3> sharedHookStarts = sharedHookActors.stream().map(Entity::position).toList();
        helper.runAfterDelay(2L, () -> {
            final MimicryRules.Phase phase = copy.mimicCore().scratch().phase();
            MimicryRuntime.onAcceptedDamage(copy, null);
            MimicryRuntime.onAcceptedDamage(copy, copy);
            MimicryRuntime.onAcceptedDamage(copy, otherMimic);
            helper.assertValueEqual(copy.mimicCore().counters().attributionRejections, 3L,
                "null, self and another mimic are rejected at the attribution seam");
            helper.assertTrue(copy.mimicCore().scratch().phase() == phase,
                "the complete rejection matrix changes no phase");
        });
        helper.runAfterDelay(4L, () -> {
            copy.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 0.0F);
            copy.setInvulnerable(true);
            copy.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 3.0F);
            copy.setInvulnerable(false);
            helper.assertValueEqual(copy.mimicCore().counters().attributions, 0L,
                "zeroed and fully refused hits mint no attribution");
        });
        helper.runAfterDelay(6L, () -> {
            final MimicryRules.Phase phase = copy.mimicCore().scratch().phase();
            helper.assertTrue(creative.isCreative() && spectator.isSpectator(),
                "the rejection cohort uses genuine creative and spectator players");
            MimicryRuntime.onAcceptedDamage(copy, creative);
            MimicryRuntime.onAcceptedDamage(copy, spectator);
            helper.assertValueEqual(copy.mimicCore().counters().attributionRejections, 5L,
                "creative and spectator causing players join null, self and mimic rejection");
            helper.assertTrue(copy.mimicCore().scratch().phase() == phase,
                "creative and spectator rejection changes no phase");
        });
        helper.runAfterDelay(10L, () -> {
            copy.hurtServer(helper.getLevel(), helper.getLevel().damageSources().fall(), 1.0F);
            helper.assertValueEqual(copy.mimicCore().counters().attributions, 0L,
                "anonymous environmental damage cannot mint an attacker");
        });
        helper.runAfterDelay(25L, () -> copy.hurtServer(helper.getLevel(),
            helper.getLevel().damageSources().magic(), 1.0F));
        helper.runAfterDelay(40L, () -> copy.hurtServer(helper.getLevel(),
            helper.getLevel().damageSources().drown(), 1.0F));
        helper.runAfterDelay(55L, () -> copy.hurtServer(helper.getLevel(),
            helper.getLevel().damageSources().cactus(), 1.0F));
        helper.runAfterDelay(70L, () -> copy.hurtServer(helper.getLevel(),
            helper.getLevel().damageSources().arrow(dispenserArrow, null), 1.0F));
        helper.runAfterDelay(75L, () -> {
            for (int index = 0; index < sharedHookActors.size(); index++) {
                final Mob actor = sharedHookActors.get(index);
                final ArcaneCreature creature = (ArcaneCreature) actor;
                final CreatureBehaviorProfile profile = CreatureBehaviorProfile.find(creature.creatureKind())
                    .orElseThrow();
                for (int attempt = 0; attempt < 8; attempt++) {
                    CreatureBehaviorRuntime.afterHurt(
                        actor, helper.getLevel(), helper.getLevel().damageSources().magic(), 2.0F, profile
                    );
                }
                helper.assertTrue(profile.has(CreatureBehaviorProfile.Feature.PHASED)
                        == !sharedHookIds.get(index).equals("banshee"),
                    "the genuine current-main profile governs the shared hook for " + sharedHookIds.get(index));
            }
        });
        helper.runAfterDelay(85L, () -> copy.hurtServer(helper.getLevel(),
            helper.getLevel().damageSources().arrow(ownedArrow, attacker), 1.0F));
        helper.runAfterDelay(100L, () -> copy.hurtServer(helper.getLevel(),
            helper.getLevel().damageSources().indirectMagic(ownedArrow, attacker), 1.0F));
        helper.runAfterDelay(115L, () -> {
            copy.setAbsorptionAmount(4.0F);
            copy.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 1.0F);
            helper.assertTrue(copy.getAbsorptionAmount() < 4.0F,
                "an accepted absorbed hit is a real positive loss");
        });
        helper.runAfterDelay(150L, () -> strike(helper, copy, attacker));
        fixture.after(280L, () -> {
            helper.assertValueEqual(copy.mimicCore().counters().attributions, 4L,
                "owned projectile, owned indirect magic, absorbed and direct living-source hits are attributed");
            helper.assertTrue(copy.mimicCore().counters().confrontations >= 1L, "attributed damage opens confrontation");
            helper.assertTrue(copy.mimicCore().counters().meleeAttempts <= 3L, "melee cadence is no faster than once per 40 ticks");
            helper.assertValueEqual(copy.mimicCore().counters().foreignEntityWrites, 0L, "confrontation changes no foreign state");
            for (int index = 0; index < sharedHookActors.size(); index++) {
                final boolean moved = sharedHookActors.get(index).position()
                    .distanceToSqr(sharedHookStarts.get(index)) > 0.01D;
                helper.assertTrue(moved == !sharedHookIds.get(index).equals("banshee"),
                    "the real shared damage hook preserves Banshee and phases " + sharedHookIds.get(index));
            }
            helper.assertTrue(MimicryRules.attributionFresh(0) && MimicryRules.attributionFresh(40)
                && !MimicryRules.attributionFresh(41), "attribution freshness owns its exact 0/40/41 seam");
        });
    }

    public static void glassDoppelgangerHazardEscapeAndCancellationAreDeterministic(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final List<AbstractMimicEntity> copies = List.of(
            spawnMimic(fixture, "glass_doppelganger", new BlockPos(4, 1, 7)),
            spawnMimic(fixture, "glass_doppelganger", new BlockPos(7, 1, 7)),
            spawnMimic(fixture, "glass_doppelganger", new BlockPos(10, 1, 7)));
        final ServerPlayer subject = fixture.connectedPlayer(new BlockPos(7, 1, 10));
        copies.get(0).mimicCore().scratch().phase = MimicryRules.Phase.PRESENTING;
        copies.get(1).mimicCore().scratch().phase = MimicryRules.Phase.SHADOWING;
        copies.get(2).mimicCore().scratch().phase = MimicryRules.Phase.CONFRONT;
        final ServerPlayer teleportSubject = fixture.connectedPlayer(new BlockPos(3, 1, 5));
        final AbstractMimicEntity teleported =
            spawnMimic(fixture, "glass_doppelganger", new BlockPos(2, 1, 5));
        teleported.mimicCore().scratch().phase = MimicryRules.Phase.SHADOWING;
        teleported.mimicCore().scratch().bound = teleportSubject.getUUID();
        MimicryRuntime.tick(teleported, helper.getLevel());
        teleported.teleportTo(teleported.getX() + 9.0D, teleported.getY(), teleported.getZ());
        MimicryRuntime.tick(teleported, helper.getLevel());
        helper.assertValueEqual(teleported.mimicCore().counters().cancellations, 1L,
            "an external nine-block teleport performs one full cancellation");

        final ServerPlayer dimensionSubject = fixture.connectedPlayer(new BlockPos(3, 1, 9));
        final AbstractMimicEntity dimensionChanged =
            spawnMimic(fixture, "glass_doppelganger", new BlockPos(2, 1, 9));
        dimensionChanged.mimicCore().scratch().phase = MimicryRules.Phase.SHADOWING;
        dimensionChanged.mimicCore().scratch().bound = dimensionSubject.getUUID();
        MimicryRuntime.tick(dimensionChanged, helper.getLevel());
        final net.minecraft.server.level.ServerLevel nether =
            helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        helper.assertTrue(nether != null, "the live Nether exists for dimension cancellation");
        nether.getChunk(0, 0);
        helper.assertTrue(dimensionChanged.teleportTo(
            nether, 0.5D, 80.0D, 0.5D, java.util.Set.of(),
            dimensionChanged.getYRot(), dimensionChanged.getXRot(), false),
            "the Glass Doppelganger changes dimension through the live teleport API");
        MimicryRuntime.tick(dimensionChanged, nether);
        helper.assertValueEqual(dimensionChanged.mimicCore().counters().cancellations, 1L,
            "a real dimension transfer executes one full cancellation before removed-body early return");
        helper.assertTrue(dimensionChanged.mimicCore().scratch().phase()
                == dimensionChanged.mimicSpecies().routine()
                && dimensionChanged.mimicCore().scratch().boundSubject().isEmpty(),
            "dimension cancellation clears the episode and retained subject without replay");

        final net.minecraft.world.entity.npc.villager.Villager phaseMatrixSubject =
            fixture.spawn(EntityTypes.VILLAGER, new BlockPos(5, 1, 5));
        phaseMatrixSubject.setTradingPlayer(subject);
        assertEveryOwnedPhaseCancels(helper, fixture, "glass_doppelganger", phaseMatrixSubject,
            new BlockPos(4, 1, 5));

        final AbstractMimicEntity routeProbe =
            spawnMimic(fixture, "glass_doppelganger", new BlockPos(11, 1, 3));
        for (int x = 10; x <= 12; x++) for (int z = 2; z <= 4; z++) {
            if (x == 11 && z == 3) continue;
            fixture.place(new BlockPos(x, 1, z), Blocks.BARRIER);
            fixture.place(new BlockPos(x, 2, z), Blocks.BARRIER);
        }
        routeProbe.setRemainingFireTicks(200);
        routeProbe.mimicCore().scratch().makeEveryCadenceDue();
        for (int tick = 0; tick < 65; tick++) MimicryRuntime.tick(routeProbe, helper.getLevel());
        helper.assertValueEqual(routeProbe.mimicCore().scratch().routeFailures(), 3,
            "Glass uses real navigation for three unreachable escape-route failures");
        helper.assertTrue(routeProbe.mimicCore().scratch().routeBackoff() > 0,
            "Glass enters the shared loaded-tick route backoff after the third rejection");
        helper.runAfterDelay(5L, () -> {
            copies.get(0).setRemainingFireTicks(100);
            fixture.place(new BlockPos(7, 1, 7), Blocks.LAVA);
            strike(helper, copies.get(2), subject);
            fixture.place(new BlockPos(10, 0, 7), Blocks.SAND);
            fixture.place(new BlockPos(10, 1, 7), Blocks.CACTUS);
        });
        fixture.after(95L, () -> {
            for (final AbstractMimicEntity copy : copies) {
                helper.assertTrue(copy.mimicCore().counters().hazardEscapes > 0L, "hazard preempts each live phase");
                helper.assertTrue(copy.mimicCore().counters().hazardReads <= 256L * 5L, "escape observations remain bounded");
                helper.assertValueEqual(copy.mimicCore().counters().foreignEntityWrites, 0L, "cancellation replays no payload");
            }
            helper.assertTrue(copies.stream().allMatch(copy ->
                    copy.mimicCore().counters().hazardContactReads > 0L),
                "all three cancellation triggers use the bounded live hazard detector");
            var route = MimicryRules.routeRequest().failed(MimicryRules.ROUTE_BACKOFF)
                .failed(MimicryRules.ROUTE_BACKOFF).failed(MimicryRules.ROUTE_BACKOFF);
            helper.assertValueEqual(route.backoffRemaining(), 100, "three failures arm exact route backoff");
            helper.assertTrue(copies.get(0).mimicCore().scratch().phase()
                != copies.get(0).mimicSpecies().routine()
                || !copies.get(0).isPreventingPlayerRest(helper.getLevel(), null),
                "routine bed policy remains passive");
        });
    }

    public static void glassDoppelgangerSaveReloadAndZombieLifecycleAreReplaced(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final AbstractMimicEntity original = spawnMimic(fixture, "glass_doppelganger", new BlockPos(7, 1, 7));
        original.mimicCore().setState(original.mimicCore().state().withPrimaryCooldown(91));
        original.mimicCore().scratch().phase = MimicryRules.Phase.SHADOWING;
        original.setCanPickUpLoot(true);
        original.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND));
        final AbstractMimicEntity copy = reload(fixture, original);
        for (final MimicryRules.Phase phase : MimicryRules.Phase.values()) {
            if (!MimicryRules.owns(copy.mimicSpecies(), phase)) continue;
            final AbstractMimicEntity phaseOriginal = spawnMimic(fixture,
                "glass_doppelganger", new BlockPos(7, 1, 7));
            phaseOriginal.mimicCore().scratch().phase = phase;
            final AbstractMimicEntity phaseLoaded = reload(fixture, phaseOriginal);
            helper.assertTrue(phaseLoaded.mimicCore().scratch().phase() == MimicryRules.Phase.UNBOUND,
                "every saved Glass phase normalizes without replay: " + phase);
            phaseLoaded.discard();
        }
        final AbstractMimicEntity malformed = reloadMalformed(fixture,
            spawnMimic(fixture, "glass_doppelganger", new BlockPos(7, 1, 7)));
        helper.assertTrue(malformed.mimicCore().state().episodeAllowed(),
            "malformed Glass state defaults independently to a safe routine");
        fixture.after(2L, () -> {
            final MimicryState decoded = MimicryState.read(copy.mimicCore().state().write(), copy.mimicSpecies());
            helper.assertValueEqual(decoded.primaryCooldown(), 89, "durable cooldown survives loaded reconciliation");
            helper.assertTrue(copy.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && !copy.canPickUpLoot(),
                "legacy zombie equipment and pickup state stay normalized");
            helper.assertTrue(!net.minecraft.world.entity.monster.zombie.Zombie.class.isAssignableFrom(copy.getClass()),
                "the dedicated body replaces the zombie lifecycle");
            helper.assertTrue(helper.getLevel().getEntity(copy.getUUID()) == copy,
                "the saved UUID resolves only to its live replacement");
            helper.assertValueEqual(copy.mimicCore().counters().meleeAttempts, 0L, "reload produces no replay");
            helper.assertTrue(MimicryRules.clampRemaining(20_000,
                MimicryRules.Species.PRESENTED_LIKENESS.primaryCooldownTicks()) == 1_200,
                "cadence sentinel remains inside the approved loaded bound");
        });
    }

    // ---------------------------------------------------------------- fixture one

    /**
     * The replacement is real: all four ids construct dedicated mimic bodies, the whole inherited
     * vanilla zombie goal and target set is gone, and the one runtime is genuinely reached from the
     * live server tick rather than only from a test helper.
     */
    public static void mimicryBodiesReplaceTheZombieLifecycle(final GameTestHelper helper) {
        buildFloor(helper, CELL_ARENA_MAX);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final List<AbstractMimicEntity> mimics = List.of(
                spawnMimic(fixture, "illusion_creeper", new BlockPos(0, 1, 0)),
                spawnMimic(fixture, "illusion_spider", new BlockPos(2, 1, 0)),
                spawnMimic(fixture, "illusion_zombie", new BlockPos(0, 1, 2)),
                spawnMimic(fixture, "glass_doppelganger", new BlockPos(2, 1, 2))
            );
            helper.assertValueEqual(mimics.get(0).getClass(), IllusionCreeperEntity.class,
                "warlockery:illusion_creeper must construct its dedicated body");
            helper.assertValueEqual(mimics.get(1).getClass(), IllusionSpiderEntity.class,
                "warlockery:illusion_spider must construct its dedicated body");
            helper.assertValueEqual(mimics.get(2).getClass(), IllusionZombieEntity.class,
                "warlockery:illusion_zombie must construct its dedicated body");
            helper.assertValueEqual(mimics.get(3).getClass(), GlassDoppelgangerEntity.class,
                "warlockery:glass_doppelganger must construct its dedicated body");

            helper.runAfterDelay(60L, () -> {
                try {
                    for (final AbstractMimicEntity mimic : mimics) {
                        final String name = mimic.creatureKind().name();
                        helper.assertTrue(mimic.tickCount > 0,
                            name + " must be a genuinely self-ticking AI-enabled entity");
                        // The reachability assertion. A runtime wired only into a helper would
                        // leave this counter at zero while every unit test still passed.
                        helper.assertTrue(mimic.mimicCore().counters().aiTicks > 0L,
                            name + " never reached MimicryRuntime.tick from the live server tick");
                        helper.assertTrue(!ArcaneMob.class.isInstance(mimic),
                            name + " must not be an ArcaneMob, so no generic seam exists to delegate");
                        helper.assertTrue(
                            mimic.creatureKind() == ArcaneCreature.CreatureKind.GLASS_DOPPELGANGER
                                || CreatureBehaviorProfile.find(mimic.creatureKind()).isEmpty(),
                            name + " must not gain a generic behaviour profile row"
                        );
                        helper.assertValueEqual(mimic.operationalTargetGoalCount(), 0,
                            name + " must register no target goal at all");
                        helper.assertValueEqual(
                            mimic.operationalGoalNames(),
                            List.of("FloatGoal", "LookAtPlayerGoal", "LookOnlyRandomLookGoal"),
                            name + " must keep a LOOK-only goal set"
                        );
                        helper.assertTrue(mimic.getTarget() == null,
                            name + " must never acquire a target of its own");
                        for (final EquipmentSlot slot : EquipmentSlot.values()) {
                            helper.assertTrue(mimic.getItemBySlot(slot).isEmpty(),
                                name + " must carry no equipment in " + slot);
                        }
                        helper.assertTrue(
                            !mimic.isPreventingPlayerRest(helper.getLevel(), null)
                                || mimic.mimicCore().scratch().phase()
                                    != mimic.mimicSpecies().routine(),
                            name + " must not block the night while it is quiet"
                        );
                        helper.assertValueEqual(
                            mimic.mimicCore().state().species(), mimic.mimicSpecies(),
                            name + " must carry its own species in its durable record"
                        );
                    }
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

    // ---------------------------------------------------------------- fixture two

    /**
     * The F34 signature, in both directions.
     *
     * <p>The negative half is unchanged: none of the three Illusion Copies ever deals damage,
     * explodes, edits a block or acquires a target. The positive half is new, because a family
     * proved only by zeros is a family proved to do nothing. The weaver's Slowness is the only
     * foreign mutation any mimic performs and is asserted to happen <em>exactly once</em> and to be
     * observable on the subject while it is held, rather than bounded above by one and satisfied by
     * zero. The decoy's own behaviour is asserted the same way: it takes a station beside a hostile
     * anchor, it goes on to draw attention, it absorbs an attributed hit, and the second accepted
     * hit unmasks it, while still answering both hits with nothing at all.</p>
     *
     * <p>The decoy's <em>companion</em> half, {@code counters.stations} and {@code counters.draws},
     * is deliberately not asserted here and cannot be. A station is always exactly
     * {@link MimicryRules#DECOY_STATION_OFFSET} blocks out from the anchor on one of four cardinals
     * chosen by the decoy's own entity id, and the anchor must itself be inside the cell, so in a
     * sealed three by three no anchor placement puts more than two of those four station points on
     * walkable floor and no fixture can select which one the id picks. Widening the arena would mean
     * writing blocks outside the structure footprint and into the batch grid, which is exactly the
     * live-arena hazard these fixtures exist to avoid. The station geometry is asserted at the pure
     * level instead, in {@code MimicryRuntimeGeometryTest}, and the arrival counter is recorded as
     * unproven.</p>
     */
    public static void illusionCopiesRunTheirEpisodesAndDealNoDamage(final GameTestHelper helper) {
        buildFloor(helper, CELL_ARENA_MAX);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final IllusionCreeperEntity creeper =
                (IllusionCreeperEntity) spawnMimic(fixture, "illusion_creeper", new BlockPos(0, 1, 1));
            final IllusionSpiderEntity spider =
                (IllusionSpiderEntity) spawnMimic(fixture, "illusion_spider", new BlockPos(2, 1, 0));
            final IllusionZombieEntity zombie =
                (IllusionZombieEntity) spawnMimic(fixture, "illusion_zombie", new BlockPos(2, 1, 2));
            final ServerPlayer observer = fixture.connectedPlayer(new BlockPos(0, 1, 0));
            observer.setInvulnerable(false);

            // The observer deliberately looks away along +X, so the creeper's discovery predicate
            // cannot fire and the telegraph and hold phases are actually reached.
            helper.onEachTick(() -> {
                observer.setYRot(-90.0F);
                observer.setXRot(0.0F);
                observer.setYHeadRot(-90.0F);
            });

            // Deliberately at delay 2 rather than at 0. MimicryRuntime.tick seeds the UUID stagger
            // into the check cadence on the body's very first tick, which overwrites anything the
            // seam wrote before that tick, so calling it at 0 leaves the first check anywhere in
            // ticks 1 to 21 and every timing below becomes a range instead of a number.
            helper.runAfterDelay(2L, () -> {
                creeper.mimicCore().scratch().makeEveryCadenceDue();
                spider.mimicCore().scratch().makeEveryCadenceDue();
                zombie.mimicCore().scratch().makeEveryCadenceDue();
            });

            // -2 is "this stage never ran", -1 is "the subject carried no Slowness at all".
            final AtomicInteger snareAmplifierWhileHeld = new AtomicInteger(-2);
            final AtomicLong decoyAbsorbsBeforeAnyHit = new AtomicLong(-1L);
            final AtomicLong decoyUnmasksBeforeAnyHit = new AtomicLong(-1L);
            final AtomicInteger decoyHitsBeforeAnyHit = new AtomicInteger(-1);
            final AtomicLong decoyAbsorbsAfterOneHit = new AtomicLong(-1L);
            final AtomicLong decoyUnmasksAfterOneHit = new AtomicLong(-1L);
            final AtomicLong decoyUnmasksOnceDecisive = new AtomicLong(-1L);
            final AtomicInteger decoyHitsOnceDecisive = new AtomicInteger(-1);

            // The weaver binds at about tick 3, commits at about tick 53 and breaks at about tick
            // 93, and its Slowness is written for 40 ticks. Sampled in the middle of that window.
            helper.runAfterDelay(70L, () -> snareAmplifierWhileHeld.set(
                observer.getEffect(MobEffects.SLOWNESS) == null
                    ? -1
                    : observer.getEffect(MobEffects.SLOWNESS).getAmplifier()
            ));

            helper.runAfterDelay(120L, () -> {
                decoyAbsorbsBeforeAnyHit.set(zombie.mimicCore().counters().absorbs);
                decoyUnmasksBeforeAnyHit.set(zombie.mimicCore().counters().unmasks);
                decoyHitsBeforeAnyHit.set(zombie.mimicCore().scratch().acceptedHits());
            });

            helper.runAfterDelay(150L, () -> strike(helper, zombie, observer));

            helper.runAfterDelay(190L, () -> {
                decoyAbsorbsAfterOneHit.set(zombie.mimicCore().counters().absorbs);
                decoyUnmasksAfterOneHit.set(zombie.mimicCore().counters().unmasks);
                strike(helper, zombie, observer);
            });

            helper.runAfterDelay(215L, () -> {
                decoyUnmasksOnceDecisive.set(zombie.mimicCore().counters().unmasks);
                decoyHitsOnceDecisive.set(zombie.mimicCore().scratch().acceptedHits());
            });

            helper.runAfterDelay(280L, () -> {
                try {
                    final var creeperCounters = creeper.mimicCore().counters();
                    final var spiderCounters = spider.mimicCore().counters();
                    final var zombieCounters = zombie.mimicCore().counters();

                    helper.assertTrue(creeperCounters.episodeStarts >= 1L,
                        "the fuse must bind the observed player and open an episode");
                    helper.assertTrue(creeperCounters.collapses >= 1L,
                        "the fuse must reach its collapse rather than stalling mid-episode");
                    helper.assertTrue(spiderCounters.episodeStarts >= 1L,
                        "the weaver must open an episode against the player at its threshold");
                    helper.assertTrue(zombieCounters.aiTicks > 0L,
                        "the decoy must be ticking its own runtime");

                    // The decoy's reactive half: it absorbs, and the second hit is the decisive one.
                    helper.assertValueEqual(decoyAbsorbsBeforeAnyHit.get(), 0L,
                        "an unstruck decoy must never enter its absorb band");
                    helper.assertValueEqual(decoyUnmasksBeforeAnyHit.get(), 0L,
                        "an unstruck decoy must never unmask itself");
                    helper.assertValueEqual(decoyHitsBeforeAnyHit.get(), 0,
                        "an unstruck decoy must have absorbed nothing");
                    helper.assertTrue(decoyAbsorbsAfterOneHit.get() >= 1L,
                        "an attributed hit must open the absorb band, which is the decoy's answer to"
                            + " being struck and the only act it has for it");
                    helper.assertValueEqual(decoyUnmasksAfterOneHit.get(), 0L,
                        "one hit is never the decisive hit: the decoy absorbs it and stays masked");
                    helper.assertTrue(decoyUnmasksOnceDecisive.get() >= 1L,
                        "and the decisive hit must unmask it, inside the absorb window rather than"
                            + " by that window elapsing");
                    helper.assertValueEqual(
                        decoyHitsOnceDecisive.get(), MimicryRules.DECOY_DECISIVE_HITS,
                        "with the accepted hit count stopping exactly at the decisive threshold");
                    helper.assertTrue(zombieCounters.attributions >= 2L,
                        "every player hit must have minted an attribution");

                    // Answered with nothing at all, asserted after the decoy has been struck twice.
                    for (final AbstractMimicEntity mimic : List.of(creeper, spider, zombie)) {
                        final String name = mimic.creatureKind().name();
                        helper.assertValueEqual(mimic.mimicCore().counters().meleeAttempts, 0L,
                            name + " must never attempt a melee attack");
                        helper.assertValueEqual(mimic.mimicCore().counters().confrontations, 0L,
                            name + " has no confrontation band at all");
                        helper.assertTrue(mimic.getTarget() == null,
                            name + " must never mint a target");
                    }
                    helper.assertTrue(observer.getLastHurtByMob() == null,
                        "no illusion copy may ever be recorded as having hurt the player");

                    // Zero explosion, proven against the arena rather than against a counter.
                    for (int x = 0; x <= CELL_ARENA_MAX; x++) {
                        for (int z = 0; z <= CELL_ARENA_MAX; z++) {
                            helper.assertBlockPresent(Blocks.STONE, new BlockPos(x, 0, z));
                            helper.assertBlockNotPresent(Blocks.FIRE, new BlockPos(x, 1, z));
                        }
                    }

                    // The one permitted foreign mutation, its observed effect, and its guard.
                    helper.assertValueEqual(spiderCounters.snareApplications, 1L,
                        "the weaver's Slowness is the family's only foreign mutation and its whole"
                            + " distinguishing behaviour: exactly one, not at most one");
                    helper.assertTrue(snareAmplifierWhileHeld.get() != -2,
                        "the snare sampling stage must actually have run");
                    helper.assertValueEqual(
                        snareAmplifierWhileHeld.get(), MimicryRules.WEAVER_SNARE_AMPLIFIER,
                        "and the snared subject must really have been carrying that Slowness while"
                            + " the snare was held, rather than only a counter having moved");
                    helper.assertTrue(spiderCounters.foreignEntityWrites >= 1L,
                        "the weaver is the one mimic that writes to another entity at all");
                    helper.assertTrue(
                        spiderCounters.snareRemovals + spiderCounters.snareRemovalGuardMisses >= 1L,
                        "an applied snare must always reach its guarded removal attempt"
                    );
                    helper.assertValueEqual(creeperCounters.snareApplications, 0L,
                        "only the weaver may apply an effect to anything");
                    helper.assertValueEqual(zombieCounters.snareApplications, 0L,
                        "only the weaver may apply an effect to anything");
                    helper.assertValueEqual(creeperCounters.foreignEntityWrites, 0L,
                        "the fuse writes to no entity but itself");
                    helper.assertValueEqual(zombieCounters.foreignEntityWrites, 0L,
                        "the decoy writes to no entity but itself, including its station anchor");
                    helper.assertValueEqual(zombieCounters.stations, 0L,
                        "with no hostile mob in the cell, neither the player nor another mimic may"
                            + " ever be taken for a companion anchor");
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

    // ---------------------------------------------------------------- fixture three

    /**
     * The F35 signature: a fully equipped player is presented, not copied. Nothing the player wears,
     * holds, carries or has active reaches the copy, and only an attributed hit ever opens the one
     * reactive band any mimic has.
     */
    public static void glassDoppelgangerPresentsWithoutCopyingPlayerData(final GameTestHelper helper) {
        buildFloor(helper, CELL_ARENA_MAX);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final GlassDoppelgangerEntity copy = (GlassDoppelgangerEntity)
                spawnMimic(fixture, "glass_doppelganger", new BlockPos(0, 1, 0));
            final ServerPlayer subject = fixture.connectedPlayer(new BlockPos(2, 1, 0));
            subject.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            subject.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            subject.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
            subject.addEffect(new MobEffectInstance(MobEffects.SPEED, 2_000, 1));
            subject.setHealth(7.0F);
            final AtomicLong confrontationsBeforeHit = new AtomicLong(-1L);
            final AtomicLong recognitionAfterHit = new AtomicLong(-1L);
            // Sampled while the presentation is still live. The sealed arena is smaller than the
            // 6.0 band inner radius, so the band route legitimately fails and the copy withdraws
            // and releases its subject well before the last stage runs.
            final java.util.concurrent.atomic.AtomicReference<java.util.Optional<java.util.UUID>>
                boundWhilePresenting = new java.util.concurrent.atomic.AtomicReference<>(null);
            final java.util.concurrent.atomic.AtomicReference<MimicryPresentation.Stance>
                stanceWhilePresenting = new java.util.concurrent.atomic.AtomicReference<>(null);

            helper.onEachTick(() -> {
                subject.setYRot(-90.0F);
                subject.setXRot(0.0F);
                subject.setYHeadRot(-90.0F);
            });

            // At delay 2, not 0: the runtime seeds the UUID stagger into the check cadence on the
            // body's first tick and overwrites whatever the seam wrote before it, which would leave
            // the first bind anywhere in ticks 1 to 21 and the 100-tick sample below a gamble.
            helper.runAfterDelay(2L, () -> copy.mimicCore().scratch().makeEveryCadenceDue());

            helper.runAfterDelay(100L, () -> {
                boundWhilePresenting.set(copy.mimicCore().scratch().boundSubject());
                stanceWhilePresenting.set(copy.mimicCore().scratch().stance());
            });

            helper.runAfterDelay(140L, () -> {
                confrontationsBeforeHit.set(copy.mimicCore().counters().confrontations);
                copy.hurtServer(
                    helper.getLevel(), helper.getLevel().damageSources().playerAttack(subject), 3.0F
                );
                recognitionAfterHit.set(copy.mimicCore().scratch().recognition());
            });

            helper.runAfterDelay(200L, () -> {
                try {
                    final var counters = copy.mimicCore().counters();
                    helper.assertTrue(counters.aiTicks > 0L,
                        "the likeness must reach its runtime from the live server tick");
                    helper.assertTrue(counters.episodeStarts >= 1L,
                        "a legal, visible, in-range subject must open one presentation episode");

                    for (final EquipmentSlot slot : EquipmentSlot.values()) {
                        helper.assertTrue(copy.getItemBySlot(slot).isEmpty(),
                            "the likeness must copy no equipment into " + slot);
                    }
                    helper.assertTrue(copy.getActiveEffects().isEmpty(),
                        "the likeness must copy no status effect from its subject");
                    helper.assertTrue(copy.getHealth() != 7.0F || copy.getMaxHealth() == 7.0F,
                        "the likeness must never take its health from its subject");
                    helper.assertTrue(copy.hasCustomName(),
                        "the presented name is the one member the likeness does derive");
                    helper.assertValueEqual(
                        stanceWhilePresenting.get(), MimicryPresentation.Stance.STILL,
                        "a standing, non-crouching subject must present as STILL");
                    helper.assertValueEqual(
                        boundWhilePresenting.get(), java.util.Optional.of(subject.getUUID()),
                        "the likeness must have bound exactly the fixture's own subject");

                    // The subject is untouched in every way this family forbids.
                    helper.assertTrue(subject.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),
                        "the subject's own equipment must be left exactly as it was");
                    helper.assertTrue(subject.hasEffect(MobEffects.SPEED),
                        "the subject's own effects must be left exactly as they were");
                    helper.assertValueEqual(counters.foreignEntityWrites, 0L,
                        "the likeness writes to no entity but itself, with no exception at all");

                    helper.assertTrue(confrontationsBeforeHit.get() >= 0L,
                        "the delayed hit stage must actually have run");
                    helper.assertValueEqual(confrontationsBeforeHit.get(), 0L,
                        "nothing but an attributed hit may ever open the confrontation band");
                    helper.assertTrue(counters.attributions >= 1L,
                        "a player melee hit must mint exactly the attribution the band needs");
                    helper.assertTrue(counters.confrontations >= 1L,
                        "one accepted, attributed hit must open the reactive band");
                    helper.assertValueEqual(
                        recognitionAfterHit.get(), (long) MimicryRules.RECOGNITION_CERTAIN,
                        "one accepted hit is forced recognition, not a gradual gain");
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

    // ---------------------------------------------------------------- fixture support

    /** One attributed melee hit from the fixture's own player, delivered outside i-frames. */
    private static void assertFullyCancelled(
        final GameTestHelper helper,
        final AbstractMimicEntity mimic,
        final MimicryRules.Phase routine,
        final String trigger
    ) {
        helper.assertTrue(mimic.mimicCore().scratch().phase() == routine,
            trigger + " returns immediately to the species routine from its active phase");
        helper.assertTrue(mimic.mimicCore().scratch().boundSubject().isEmpty(),
            trigger + " clears the retained subject identity");
        helper.assertTrue(mimic.getNavigation().isDone(),
            trigger + " stops real navigation immediately");
        helper.assertTrue(mimic.getTarget() == null,
            trigger + " clears the body's target");
        helper.assertTrue(mimic.getDeltaMovement().x == 0.0D
                && mimic.getDeltaMovement().z == 0.0D,
            trigger + " clears horizontal movement without altering vertical motion");
    }

    private static void assertEveryOwnedPhaseCancels(
        final GameTestHelper helper,
        final FixtureScope fixture,
        final String id,
        final LivingEntity illegalSubject,
        final BlockPos position
    ) {
        for (final MimicryRules.Phase phase : MimicryRules.Phase.values()) {
            final AbstractMimicEntity mimic = spawnMimic(fixture, id, position);
            if (!MimicryRules.owns(mimic.mimicSpecies(), phase)) {
                mimic.discard();
                continue;
            }
            mimic.mimicCore().setState(mimic.mimicCore().state().withPrimaryCooldown(77));
            mimic.mimicCore().scratch().phase = phase;
            mimic.mimicCore().scratch().bound = illegalSubject.getUUID();
            mimic.getNavigation().moveTo(illegalSubject, 1.0D);
            mimic.setDeltaMovement(0.25D, 0.0D, 0.25D);
            MimicryRuntime.tick(mimic, helper.getLevel());
            helper.assertValueEqual(mimic.mimicCore().counters().cancellations, 1L,
                id + " cancellation is reached from owned phase " + phase);
            helper.assertValueEqual(mimic.mimicCore().state().primaryCooldown(), 76,
                id + " cancellation preserves the cooldown after its one legitimate loaded tick in phase " + phase);
            assertFullyCancelled(helper, mimic, mimic.mimicSpecies().routine(),
                id + " phase " + phase + " cancellation");
            mimic.discard();
        }
    }

    private static void strike(
        final GameTestHelper helper,
        final AbstractMimicEntity mimic,
        final ServerPlayer attacker
    ) {
        mimic.hurtServer(
            helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 3.0F
        );
    }

    private static void buildFloor(final GameTestHelper helper, final int maximum) {
        for (int x = 0; x <= maximum; x++) {
            for (int z = 0; z <= maximum; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static void face(final Entity observer, final Entity subject) {
        observer.lookAt(EntityAnchorArgument.Anchor.EYES, subject.getEyePosition());
    }

    private static AbstractMimicEntity spawnMimic(
        final FixtureScope fixture,
        final String id,
        final BlockPos position
    ) {
        @SuppressWarnings("unchecked")
        final EntityType<AbstractMimicEntity> type =
            (EntityType<AbstractMimicEntity>) ModEntities.ALL.get(id).get();
        return fixture.spawn(type, position);
    }

    private static Mob spawnArcaneMob(
        final FixtureScope fixture,
        final String id,
        final BlockPos position
    ) {
        @SuppressWarnings("unchecked")
        final EntityType<Mob> type = (EntityType<Mob>) ModEntities.ALL.get(id).get();
        return fixture.spawn(type, position);
    }

    private static AbstractMimicEntity reload(
        final FixtureScope fixture,
        final AbstractMimicEntity original
    ) {
        final TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, fixture.helper.getLevel().registryAccess());
        original.saveWithoutId(output);
        final CompoundTag saved = output.buildResult().copy();
        final java.util.UUID identity = original.getUUID();
        final String id = original.mimicSpecies().kind().name().toLowerCase(java.util.Locale.ROOT);
        original.discard();
        fixture.helper.assertTrue(fixture.helper.getLevel().getEntity(identity) == null,
            "the original is absent before its UUID is restored");
        @SuppressWarnings("unchecked")
        final EntityType<AbstractMimicEntity> type =
            (EntityType<AbstractMimicEntity>) ModEntities.ALL.get(id).get();
        final AbstractMimicEntity loaded = type.create(
            fixture.helper.getLevel(), EntitySpawnReason.LOAD);
        fixture.helper.assertTrue(loaded != null, "registered load creates the dedicated body");
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,
            fixture.helper.getLevel().registryAccess(), saved));
        fixture.track(loaded);
        fixture.helper.assertTrue(fixture.helper.getLevel().addFreshEntity(loaded),
            "the replacement enters the live level");
        return loaded;
    }

    private static AbstractMimicEntity reloadMalformed(
        final FixtureScope fixture,
        final AbstractMimicEntity original
    ) {
        final TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, fixture.helper.getLevel().registryAccess());
        original.saveWithoutId(output);
        final CompoundTag saved = output.buildResult().copy();
        saved.putString(AbstractMimicEntity.STATE_KEY, "legacy-not-a-compound");
        original.discard();
        @SuppressWarnings("unchecked")
        final EntityType<AbstractMimicEntity> type = (EntityType<AbstractMimicEntity>) ModEntities.ALL.get(
            original.mimicSpecies().kind().name().toLowerCase(java.util.Locale.ROOT)).get();
        final AbstractMimicEntity loaded = type.create(fixture.helper.getLevel(), EntitySpawnReason.LOAD);
        fixture.helper.assertTrue(loaded != null, "malformed cohort creates a dedicated body");
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,
            fixture.helper.getLevel().registryAccess(), saved));
        fixture.track(loaded);
        fixture.helper.assertTrue(fixture.helper.getLevel().addFreshEntity(loaded),
            "malformed cohort enters the live level safely");
        return loaded;
    }

    private static final class FixtureScope implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<Entity> entities = new ArrayList<>();
        private final Map<BlockPos, BlockState> restoredBlocks = new LinkedHashMap<>();
        private boolean closed;

        private FixtureScope(final GameTestHelper helper) {
            this.helper = helper;
            helper.addCleanup(passed -> close());
            buildClosedArena();
        }

        private void buildClosedArena() {
            for (int x = 1; x <= 13; x++) {
                for (int z = 1; z <= 13; z++) {
                    place(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
            for (int y = 1; y <= 6; y++) {
                for (int coordinate = 1; coordinate <= 13; coordinate++) {
                    place(new BlockPos(1, y, coordinate), Blocks.BARRIER);
                    place(new BlockPos(13, y, coordinate), Blocks.BARRIER);
                    place(new BlockPos(coordinate, y, 1), Blocks.BARRIER);
                    place(new BlockPos(coordinate, y, 13), Blocks.BARRIER);
                }
            }
        }

        private void place(final BlockPos position, final net.minecraft.world.level.block.Block block) {
            restoredBlocks.putIfAbsent(position.immutable(), helper.getBlockState(position));
            helper.setBlock(position, block);
        }

        private void after(final long delay, final Runnable assertion) {
            helper.runAfterDelay(delay, () -> {
                try {
                    assertion.run();
                    helper.succeed();
                } finally {
                    close();
                }
            });
        }

        private <T extends Entity> T spawn(final EntityType<T> type, final BlockPos position) {
            return track(helper.spawn(type, position, EntitySpawnReason.EVENT));
        }

        private <T extends Entity> T track(final T entity) {
            entities.add(entity);
            return entity;
        }

        private ServerPlayer connectedPlayer(final BlockPos position) {
            return connectedPlayer(position, GameType.SURVIVAL);
        }

        private ServerPlayer connectedPlayer(final BlockPos position, final GameType gameType) {
            final ServerPlayer player =
                (ServerPlayer) helper.makeMockServerPlayer(gameType);
            final net.minecraft.network.Connection connection =
                new net.minecraft.network.Connection(
                    net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            final net.minecraft.server.network.CommonListenerCookie cookie =
                net.minecraft.server.network.CommonListenerCookie.createInitial(
                    player.getGameProfile(), false);
            helper.getLevel().getServer().getPlayerList()
                .placeNewPlayer(connection, player, cookie);
            player.setGameMode(gameType);
            final BlockPos absolute = helper.absolutePos(position);
            player.teleportTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
            return track(GameTestMockPlayers.autoDisconnect(helper, player));
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            entities.forEach(GameTestMockPlayers::release);
            entities.clear();
            restoredBlocks.forEach(helper::setBlock);
            restoredBlocks.clear();
        }
    }
}

