package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.AmbientActivityProfile.ActivityType;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile.Feature;
import com.kadamitas.warlockery.entity.SpectralSteedRules.Gait;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The seven live cases for F27.
 *
 * <p>Each drives {@link SpectralSteedRuntime#tick} directly so the assertions read a state the
 * fixture put the steed in rather than one the scheduler happened to produce, except where the point
 * of the case is that the live server tick reaches this family at all, which two of them prove with
 * a single delayed callback.</p>
 */
public final class SpectralSteedGameTests {

    private SpectralSteedGameTests() {
    }

    /**
     * Only the bound owner may sit on a steed or steer it, exactly one rider fits, mounting opens a
     * ride episode, and dismounting looks for somewhere clear before putting a rider down.
     */
    public static void steedOwnerOnlyControlAndSafeDismount(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            floor(helper);
            final SpectralSteedEntity steed = fixture.steed(CreatureKind.PALE_STEED, new BlockPos(1, 1, 1));
            final ServerPlayer owner = fixture.player(new BlockPos(0, 1, 0));
            final ServerPlayer stranger = fixture.player(new BlockPos(2, 1, 2));

            helper.assertFalse(owner.startRiding(steed),
                "an unbound steed carries nobody at all, owner or not");
            helper.assertValueEqual(steed.steedState().episode(), 0,
                "a refused mount opens no ride episode");

            helper.assertTrue(CreatureBehaviorState.bind(steed, owner.getUUID()),
                "the existing Warlockery owner key is what binds a steed");
            helper.assertFalse(stranger.startRiding(steed),
                "a steed bound elsewhere refuses every other player");
            helper.assertTrue(owner.startRiding(steed), "the bound owner mounts");
            helper.assertTrue(steed.getControllingPassenger() == owner,
                "the bound owner is the controlling passenger");
            helper.assertTrue(SpectralSteedRuntime.carryingOwner(steed),
                "the runtime agrees the legal owner is aboard");
            helper.assertValueEqual(steed.steedState().episode(), 1,
                "mounting opens exactly one ride episode");
            helper.assertFalse(stranger.startRiding(steed),
                "a steed already carrying its owner takes no second rider");
            helper.assertValueEqual(steed.getPassengers().size(), 1,
                "exactly one passenger is ever aboard");

            helper.setBlock(new BlockPos(0, 1, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(1, 1, 0), Blocks.STONE);
            helper.setBlock(new BlockPos(1, 1, 2), Blocks.STONE);
            final BlockPos clear = helper.absolutePos(new BlockPos(2, 1, 1));
            final Vec3 dismount = steed.getDismountLocationForPassenger(owner);
            helper.assertTrue(dismount.distanceToSqr(clear.getX() + 0.5, clear.getY(), clear.getZ() + 0.5) < 0.01,
                "the only clear supported neighbour is where a rider is put down, not the wall");

            owner.stopRiding();
            helper.assertTrue(steed.getPassengers().isEmpty(), "the rider leaves cleanly");
            helper.assertValueEqual(steed.steedState().gait(), Gait.HALT,
                "ending a ride leaves the steed halted rather than mid band");
            helper.assertValueEqual(steed.steedState().bondThisEpisode(), 0,
                "the per-ride accumulator is cleared when the ride ends");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    /**
     * Riding the owner matures the bond one point per tick and no faster, the band climbs one step
     * per hold window and never past the Pale Steed's ceiling, sprinting costs fatigue, and a tired
     * unridden steed finds a hay landmark, settles beside it and completes the rest.
     *
     * <p>The delayed arm is the live wiring proof: no fixture call, just the server's own AI step
     * reaching this family through {@code ArcaneMob.customServerAiStep}.</p>
     */
    public static void paleSteedBondGaitFatigueAndRest(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final boolean[] scheduled = {false};
        try {
            floor(helper);
            clearApron(helper);
            final ServerLevel level = helper.getLevel();
            final SpectralSteedEntity steed = fixture.steed(CreatureKind.PALE_STEED, new BlockPos(1, 1, 1));
            final ServerPlayer owner = fixture.player(new BlockPos(1, 1, 1));
            CreatureBehaviorState.bind(steed, owner.getUUID());
            helper.assertTrue(owner.startRiding(steed), "the bond fixture needs a real mount");

            owner.zza = 1.0F;
            owner.xxa = 0.0F;
            Gait previous = steed.steedState().gait();
            int changes = 0;
            int lastChangeTick = Integer.MIN_VALUE;
            for (int tick = 0; tick < 60; tick++) {
                SpectralSteedRuntime.tick(steed, level);
                final Gait now = steed.steedState().gait();
                helper.assertTrue(now.ordinal() <= Gait.CANTER.ordinal(),
                    "an unmatured Pale Steed never reaches the sprint band, it reached " + now);
                if (now != previous) {
                    helper.assertValueEqual(now.ordinal(), previous.ordinal() + 1,
                        "a band change moves exactly one step");
                    if (changes > 0) {
                        final int gap = tick - lastChangeTick;
                        helper.assertTrue(gap >= SpectralSteedRules.GAIT_HOLD_TICKS,
                            "consecutive band changes are at least a hold window apart, saw " + gap);
                    }
                    changes++;
                    lastChangeTick = tick;
                    previous = now;
                }
            }
            helper.assertValueEqual(steed.steedState().gait(), Gait.CANTER,
                "sixty ticks of full forward input reach and stop at the Pale Steed ceiling");
            helper.assertValueEqual(steed.steedState().bond(), 60,
                "carrying the legal owner earns exactly one point of bond per tick");
            helper.assertValueEqual(steed.steedState().counters().bondGains(), 60L,
                "every earned point has one recorded gain");
            helper.assertTrue(steed.steedState().fatigue() > 0,
                "cantering costs fatigue");
            helper.assertValueEqual(steed.steedState().counters().gaitChanges(), (long) changes,
                "every applied band change is counted");

            owner.stopRiding();
            final BlockPos landmark = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.HAY_BLOCK);
            steed.setSteedState(steed.steedState().withFatigue(SpectralSteedRules.MAX_FATIGUE));
            final BlockPos searchOrigin = steed.blockPosition();
            helper.assertTrue(Math.abs(landmark.getX() - searchOrigin.getX())
                    <= SpectralSteedRules.REST_HORIZONTAL_RADIUS
                    && Math.abs(landmark.getZ() - searchOrigin.getZ())
                    <= SpectralSteedRules.REST_HORIZONTAL_RADIUS
                    && Math.abs(landmark.getY() - searchOrigin.getY())
                    <= SpectralSteedRules.REST_VERTICAL_RADIUS,
                "the fixture landmark is inside the declared search envelope");
            SpectralSteedRuntime.tick(steed, level);
            helper.assertTrue(steed.steedState().counters().restSearches() >= 1,
                "a tired unridden steed actually runs a rest search");
            helper.assertTrue(steed.steedState().counters().restBlockReads() > 0,
                "the search charges the reads it performed");
            helper.assertTrue(steed.steedState().counters().restBlockReads()
                    <= SpectralSteedRules.MAX_REST_BLOCK_READS * steed.steedState().counters().restSearches(),
                "no search ever charges more than forty eight reads");
            helper.assertTrue(steed.steedState().rest().isPresent(),
                "the hay landmark yields one rest site");
            final BlockPos site = steed.steedState().rest().orElseThrow();
            final int siteToLandmarkX = Math.abs(site.getX() - landmark.getX());
            final int siteToLandmarkZ = Math.abs(site.getZ() - landmark.getZ());
            helper.assertTrue(site.getY() == landmark.getY()
                    && siteToLandmarkX + siteToLandmarkZ == 2
                    && (siteToLandmarkX == 0 || siteToLandmarkZ == 0),
                "the chosen stance belongs to this fixture's landmark");

            steed.snapTo(site.getX() + 0.5, site.getY(), site.getZ() + 0.5, 0.0F, 0.0F);
            SpectralSteedRuntime.tick(steed, level);
            helper.assertTrue(steed.steedState().resting(), "arriving at the site settles the steed");
            final int bondBeforeRest = steed.steedState().bond();
            for (int tick = 0; tick <= SpectralSteedRules.REST_SETTLE_TICKS; tick++) {
                SpectralSteedRuntime.tick(steed, level);
            }
            helper.assertValueEqual(steed.steedState().counters().restsCompleted(), 1L,
                "the rest ran to its end exactly once");
            helper.assertValueEqual(steed.steedState().bond(), bondBeforeRest + 1,
                "a completed rest is worth exactly one point of bond");
            helper.assertTrue(steed.steedState().restCooldown() >= SpectralSteedRules.REST_COOLDOWN_TICKS - 5,
                "the ending branch armed the rest cooldown");
            helper.assertFalse(steed.steedState().resting(), "the finished rest is over");

            final SpectralSteedEntity live = fixture.steed(CreatureKind.PALE_STEED, new BlockPos(0, 1, 0));
            live.setSteedState(live.steedState().withFatigue(SpectralSteedRules.MAX_FATIGUE));
            helper.runAfterDelay(4, () -> {
                try {
                    helper.assertTrue(live.steedState().counters().restSearches() >= 1,
                        "the live server AI step reaches this family with no fixture call at all");
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

    /**
     * A startled Pale Steed halts, refuses steering while it lasts, keeps its rider, applies nothing
     * to anybody, and its balk ends in exactly one place.
     */
    public static void paleSteedBalksWithoutFearOrEjection(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            floor(helper);
            final ServerLevel level = helper.getLevel();
            final SpectralSteedEntity steed = fixture.steed(CreatureKind.PALE_STEED, new BlockPos(1, 1, 1));
            final ServerPlayer owner = fixture.player(new BlockPos(1, 1, 1));
            final Zombie bystander = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
            bystander.setNoAi(true);
            CreatureBehaviorState.bind(steed, owner.getUUID());
            helper.assertTrue(owner.startRiding(steed), "the balk fixture needs a real mount");
            owner.zza = 1.0F;

            SpectralSteedRuntime.tick(steed, level);
            helper.assertTrue(steed.getRiddenInput(owner, Vec3.ZERO).lengthSqr() > 0.0,
                "an unstartled steed answers its rider");

            steed.igniteForSeconds(3.0F);
            // The hazard look runs on its own cadence, so the startle lands on the first due tick
            // rather than the next one. Ticking a whole period is what makes the case independent of
            // where in that period the fixture happened to start.
            for (int tick = 0; tick < SpectralSteedRules.HAZARD_SCAN_INTERVAL_TICKS
                && !steed.steedState().balking(); tick++) {
                SpectralSteedRuntime.tick(steed, level);
            }
            helper.assertValueEqual(steed.steedState().counters().balks(), 1L,
                "a hazard startles the steed exactly once");
            helper.assertTrue(steed.steedState().balking(), "the startle is running");
            helper.assertValueEqual(steed.steedState().gait(), Gait.HALT,
                "a startle drops the band straight to a halt rather than one step at a time");
            helper.assertTrue(steed.getFirstPassenger() == owner,
                "a Pale Steed never throws its rider for being frightened");
            helper.assertTrue(steed.getRiddenInput(owner, Vec3.ZERO).lengthSqr() == 0.0,
                "a balking steed accepts no steering at all");
            helper.assertValueEqual(steed.steedState().counters().warningsIssued(), 0L,
                "a Pale Steed answers fear with a balk and never with a warning");
            helper.assertFalse(bystander.hasEffect(MobEffects.SLOWNESS),
                "nothing at all is applied to a bystander");
            helper.assertValueEqual(bystander.getHealth(), bystander.getMaxHealth(),
                "a balk deals no damage to anybody");

            steed.clearFire();
            steed.setRemainingFireTicks(0);
            final int balkTicks = SpectralSteedRules.balkTicks(CreatureKind.PALE_STEED, steed.steedState().bond());
            for (int tick = 0; tick <= balkTicks; tick++) {
                SpectralSteedRuntime.tick(steed, level);
            }
            helper.assertFalse(steed.steedState().balking(),
                "the balk is ended by the one branch that owns ending it");
            helper.assertValueEqual(steed.steedState().counters().balks(), 1L,
                "an ended balk is not silently re-armed");
            helper.assertTrue(steed.getRiddenInput(owner, Vec3.ZERO).lengthSqr() > 0.0,
                "steering returns when the startle passes");
            helper.assertTrue(steed.getFirstPassenger() == owner,
                "the rider was aboard for the whole startle");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    /**
     * A Nightmare reaches a band a Pale Steed cannot, and its one bonded warning reaches only what
     * the tag admits, never its owner, never an ally of that owner, never anything out of range, and
     * never twice inside a cooldown.
     */
    public static void nightmareAcceleratesAndWarnsOnlyLegalHostiles(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            floor(helper);
            final ServerLevel level = helper.getLevel();
            final SpectralSteedEntity nightmare = fixture.steed(CreatureKind.NIGHTMARE, new BlockPos(1, 1, 1));
            final SpectralSteedEntity pale = fixture.steed(CreatureKind.PALE_STEED, new BlockPos(0, 1, 0));
            final ServerPlayer nightRider = fixture.player(new BlockPos(1, 1, 1));
            final ServerPlayer paleRider = fixture.player(new BlockPos(0, 1, 0));
            CreatureBehaviorState.bind(nightmare, nightRider.getUUID());
            CreatureBehaviorState.bind(pale, paleRider.getUUID());
            helper.assertTrue(nightRider.startRiding(nightmare), "the fixture needs a ridden Nightmare");
            helper.assertTrue(paleRider.startRiding(pale), "the fixture needs a ridden Pale Steed");
            nightRider.zza = 1.0F;
            paleRider.zza = 1.0F;

            for (int tick = 0; tick < 60; tick++) {
                SpectralSteedRuntime.tick(nightmare, level);
                SpectralSteedRuntime.tick(pale, level);
            }
            helper.assertValueEqual(nightmare.steedState().gait(), Gait.SPRINT,
                "a Nightmare reaches its top band on the same input");
            helper.assertValueEqual(pale.steedState().gait(), Gait.CANTER,
                "an unmatured Pale Steed does not");
            helper.assertTrue(nightmare.steedState().fatigue() > pale.steedState().fatigue(),
                "the Nightmare pays more for the band it took");

            final Zombie threat = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
            final Zombie second = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
            threat.setNoAi(true);
            second.setNoAi(true);
            final SpectralSteedEntity ally = fixture.steed(CreatureKind.PALE_STEED, new BlockPos(0, 1, 2));
            CreatureBehaviorState.bind(ally, nightRider.getUUID());
            final Zombie distant = fixture.loose(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1), 12);
            distant.setNoAi(true);

            nightmare.setSteedState(nightmare.steedState()
                .withBond(SpectralSteedRules.NIGHTMARE_WARNING_BOND + 100));
            nightmare.setLastHurtByMob(threat);
            final float threatHealth = threat.getHealth();
            SpectralSteedRuntime.tick(nightmare, level);

            helper.assertValueEqual(nightmare.steedState().counters().warningsIssued(), 1L,
                "one threat produces exactly one warning");
            helper.assertValueEqual(nightmare.steedState().counters().warningTelegraphs(), 1L,
                "one warning emits exactly one bounded telegraph");
            helper.assertTrue(nightmare.steedState().counters().warningVisits() >= 1,
                "the warning charges every entity it looked at");
            helper.assertTrue(nightmare.steedState().counters().warningVisits()
                    <= SpectralSteedRules.MAX_FEAR_VISITS,
                "a warning never visits more than eight entities");
            helper.assertTrue(threat.hasEffect(MobEffects.SLOWNESS),
                "a tagged hostile in range receives the warning");
            helper.assertValueEqual(threat.getHealth(), threatHealth,
                "the warning is not damage");
            helper.assertFalse(nightRider.hasEffect(MobEffects.SLOWNESS),
                "the owner is never warned");
            helper.assertFalse(ally.hasEffect(MobEffects.SLOWNESS),
                "an ally of the same owner is never warned");
            helper.assertFalse(distant.hasEffect(MobEffects.SLOWNESS),
                "nothing outside the declared radius is reached");
            helper.assertValueEqual(nightmare.steedState().fearCooldown(),
                SpectralSteedRules.FEAR_COOLDOWN_TICKS,
                "issuing a warning arms its cooldown");

            second.removeAllEffects();
            threat.removeAllEffects();
            SpectralSteedRuntime.tick(nightmare, level);
            helper.assertValueEqual(nightmare.steedState().counters().warningsIssued(), 1L,
                "the cooldown blocks a second warning");
            helper.assertValueEqual(nightmare.steedState().counters().warningTelegraphs(), 1L,
                "the cooldown also blocks telegraph spam");
            helper.assertFalse(threat.hasEffect(MobEffects.SLOWNESS),
                "no warning means no effect");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    /**
     * Binding a Nightmare changes who may ride it and nothing else. The dream systems' referent, its
     * tags, its hostility and its acquisition path are all exactly what they were.
     */
    public static void unboundNightmareRemainsDreamHostile(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            floor(helper);
            final ServerLevel level = helper.getLevel();
            final SpectralSteedEntity nightmare = fixture.steed(CreatureKind.NIGHTMARE, new BlockPos(1, 1, 1));
            final ServerPlayer player = fixture.player(new BlockPos(0, 1, 0));

            helper.assertTrue(nightmare.typeHolder().is(WarlockeryTags.EntityTypes.NIGHTMARES),
                "the dream systems' entity type tag still holds after the body change");
            helper.assertFalse(nightmare.typeHolder().is(WarlockeryTags.EntityTypes.SPECTRAL),
                "a steed is not and never was a member of the spectral gameplay tag");
            helper.assertFalse(nightmare.getType().toString().isEmpty(),
                "the registered type still resolves");
            helper.assertTrue(ModEntities.ALL.get("nightmare").get()
                    .create(level, EntitySpawnReason.EVENT) instanceof SpectralSteedEntity,
                "the Dream Weaver's own spawn path builds the dedicated body");

            helper.assertFalse(player.startRiding(nightmare),
                "an unbound Nightmare is a hostile, not a mount");
            nightmare.setTarget(player);
            helper.assertTrue(nightmare.canAttack(player),
                "an unbound Nightmare may still hunt a player");
            SpectralSteedRuntime.tick(nightmare, level);
            helper.assertTrue(nightmare.getTarget() == player,
                "an unridden Nightmare keeps the target the dream systems gave it");
            nightmare.setLastHurtByMob(player);
            SpectralSteedRuntime.tick(nightmare, level);
            helper.assertValueEqual(nightmare.steedState().counters().warningsIssued(), 0L,
                "an unbound Nightmare issues no warning: the warning belongs to the bond");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLAZE_POWDER, 2));
            nightmare.mobInteract(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(CreatureBehaviorState.isOwnedBy(nightmare, player.getUUID()),
                "the existing offering still binds through the shared interaction path");
            helper.assertTrue(player.startRiding(nightmare),
                "and binding is still what makes it rideable");
            helper.assertTrue(nightmare.canAttack(player) == false,
                "a bound Nightmare will not attack its own owner");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    /**
     * A rest site is a landmark and nothing more: losing it releases the site, no block is ever
     * changed, nothing is dropped or eaten, and a search that qualifies nothing still arms its
     * cadence, counts the failure and eventually backs off.
     */
    @SuppressWarnings("unchecked")
    public static void steedRestReleasesLostSupportWithoutHayMutation(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            floor(helper);
            clearApron(helper);
            final ServerLevel level = helper.getLevel();
            final SpectralSteedEntity steed = fixture.steed(CreatureKind.PALE_STEED, new BlockPos(1, 1, 1));
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.HAY_BLOCK);
            steed.setSteedState(steed.steedState().withFatigue(SpectralSteedRules.MAX_FATIGUE));

            SpectralSteedRuntime.tick(steed, level);
            helper.assertTrue(steed.steedState().rest().isPresent(), "the landmark yields a site");
            helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.HAY_BLOCK),
                "choosing a site changes no block");
            helper.assertValueEqual(steed.steedState().counters().restValidationReads(), 0L,
                "choosing a site charges no validity read: there was nothing held to re-check");

            final BlockPos heldSite = steed.steedState().rest().orElseThrow();
            steed.teleportTo(heldSite.getX() + 3.5, heldSite.getY(), heldSite.getZ() + 0.5);
            for (int tick = 0; tick < 20; tick++) {
                SpectralSteedRuntime.tick(steed, level);
            }
            helper.assertValueEqual(steed.steedState().counters().restNavigationStarts(), 1L,
                "a held rest site starts navigation at most once in the first twenty ticks");

            final SpectralSteedEntity releaseSteed =
                fixture.steed(CreatureKind.PALE_STEED, new BlockPos(1, 1, 1));
            releaseSteed.setSteedState(releaseSteed.steedState()
                .withFatigue(SpectralSteedRules.MAX_FATIGUE)
                .withRest(Optional.of(heldSite),
                    Optional.of(level.dimension().identifier().toString())));
            final long validationReadsBeforeRelease =
                releaseSteed.steedState().counters().restValidationReads();
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
            SpectralSteedRuntime.tick(releaseSteed, level);
            helper.assertFalse(releaseSteed.steedState().rest().isPresent(),
                "losing the landmark releases the site instead of walking to nothing");
            // The whole point of item one. Re-checking a held site is fourteen admitted queries in
            // the worst case: border, four loaded corners, three block states, two collision looks,
            // then all four landmark looks with no hit. Charged, they are visible here.
            final long releaseValidationReads =
                releaseSteed.steedState().counters().restValidationReads() - validationReadsBeforeRelease;
            helper.assertValueEqual(releaseValidationReads, 14L,
                "the re-check that released the site charged every read it performed");
            helper.assertValueEqual(releaseValidationReads,
                (long) SpectralSteedRules.MAX_REST_VALIDATION_READS,
                "and that is exactly the declared worst case, so the cap genuinely binds");

            final long searchesBefore = steed.steedState().counters().restSearches();
            int guard = 0;
            while (steed.steedState().restRequest().consecutiveFailures() < 3 && guard < 200) {
                SpectralSteedRuntime.tick(steed, level);
                guard++;
            }
            helper.assertValueEqual(steed.steedState().restRequest().consecutiveFailures(), 3,
                "three searches that qualified nothing are three recorded failures");
            helper.assertTrue(steed.steedState().counters().restSearches() > searchesBefore,
                "a search that finds nothing is still a search that ran");
            helper.assertValueEqual(steed.steedState().restRequest().backoffRemaining(), 100,
                "the third failure opens the declared backoff window");
            final long searchesAtBackoff = steed.steedState().counters().restSearches();
            for (int tick = 0; tick < 60; tick++) {
                SpectralSteedRuntime.tick(steed, level);
            }
            helper.assertValueEqual(steed.steedState().counters().restSearches(), searchesAtBackoff,
                "no search at all runs while the backoff window is open");

            helper.setBlock(new BlockPos(2, 1, 2), Blocks.HAY_BLOCK);
            steed.setSteedState(steed.steedState()
                .withBond(321)
                .withFatigue(654)
                .withGait(Gait.CANTER)
                .startingBalk(SpectralSteedRules.PALE_BALK_TICKS));
            final int backoffBefore = steed.steedState().restRequest().backoffRemaining();
            final TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, level.registryAccess()
            );
            steed.saveWithoutId(output);
            final var saved = output.buildResult().copy();
            // The copy is deliberately never added to the level. Loading rewrites an entity's UUID to
            // the saved one, so an entity added first and loaded second sits in the level index under
            // a key it no longer answers to, and one added after loading is silently rejected as a
            // duplicate. Either way every downstream assertion would read an entity nobody ticks.
            final SpectralSteedEntity reloaded = new SpectralSteedEntity(
                (EntityType<? extends Zombie>) ModEntities.ALL.get("pale_steed").get(),
                level, CreatureKind.PALE_STEED
            );
            reloaded.load(TagValueInput.create(
                ProblemReporter.DISCARDING, level.registryAccess(), saved
            ));
            helper.assertValueEqual(reloaded.steedState().bond(), 321,
                "bond is durable mount maturity and survives");
            helper.assertValueEqual(reloaded.steedState().fatigue(), 654,
                "fatigue survives");
            helper.assertValueEqual(reloaded.steedState().gait(), Gait.HALT,
                "no steed comes back mid canter");
            helper.assertFalse(reloaded.steedState().balking(),
                "no steed comes back mid startle");
            helper.assertValueEqual(reloaded.steedState().bondThisEpisode(), 0,
                "no bond is credited for time that merely elapsed");
            helper.assertValueEqual(reloaded.steedState().counters().bondGains(), 0L,
                "reload replays no earning at all");
            helper.assertValueEqual(reloaded.steedState().restRequest().backoffRemaining(),
                backoffBefore,
                "an open backoff window is preserved across the boundary rather than reset");
            helper.assertTrue(level.getEntity(steed.getUUID()) == steed,
                "the level still holds exactly the original steed and no duplicate of it");
            reloaded.discard();

            // ------------------------------------------- exhaustion is cost, not absence
            // A dense landmark field. Every position the window evaluates is hay, and every stance
            // beside one is blocked by more hay, so the search spends its entire allowance and
            // qualifies nothing. Charging that as a route failure is how a steed standing in a hay
            // meadow gets pushed into backoff by what the look cost rather than by what is there.
            for (int x = -1; x <= 3; x++) {
                for (int z = -1; z <= 3; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.HAY_BLOCK);
                }
            }
            final SpectralSteedEntity dense =
                fixture.steed(CreatureKind.PALE_STEED, new BlockPos(1, 1, 1));
            dense.setSteedState(dense.steedState().withFatigue(SpectralSteedRules.MAX_FATIGUE));
            SpectralSteedRuntime.tick(dense, level);
            helper.assertValueEqual(dense.steedState().counters().restSearches(), 1L,
                "the dense field is searched exactly once");
            helper.assertValueEqual(dense.steedState().counters().restBlockReads(),
                (long) SpectralSteedRules.MAX_REST_BLOCK_READS,
                "and that search spent its whole allowance, which is this case's premise");
            helper.assertFalse(dense.steedState().rest().isPresent(),
                "a field of blocked landmarks qualifies no site at all");
            helper.assertValueEqual(dense.steedState().restRequest().consecutiveFailures(), 0,
                "a search stopped by its own cost is not a route failure");
            helper.assertValueEqual(dense.steedState().restRequest().backoffRemaining(), 0,
                "so it opens no backoff window either");
            helper.assertFalse(dense.steedState().restRequest().mayRequest(),
                "but it does arm the request cadence, so the sweep is not repeated every tick");
            helper.assertValueEqual(dense.steedState().restCursor(), 8,
                "and the window still rotates, so the next look is somewhere else");

            // The other end of the validity charge. This site's own stance block is hay, so the
            // re-check fails after admission, four loaded-corner checks and the three stance-state
            // reads. A flat rate, or a charge taken only when the site survives, cannot produce
            // both this and the fourteen above.
            final SpectralSteedEntity blocked =
                fixture.steed(CreatureKind.PALE_STEED, new BlockPos(0, 1, 0));
            blocked.setSteedState(blocked.steedState()
                .withFatigue(SpectralSteedRules.MAX_FATIGUE)
                .withRest(Optional.of(helper.absolutePos(new BlockPos(1, 1, 1))),
                    Optional.of(level.dimension().identifier().toString())));
            SpectralSteedRuntime.tick(blocked, level);
            helper.assertValueEqual(blocked.steedState().counters().restValidationReads(), 8L,
                "a blocked site charges admission plus its full stance-state triple");
            helper.assertFalse(blocked.steedState().rest().isPresent(),
                "and the site it could not re-prove is released");

            final BlockPos obstructedSite = helper.absolutePos(new BlockPos(1, 1, 0));
            helper.setBlock(new BlockPos(1, 2, 0), Blocks.STONE);
            final SpectralSteedEntity obstructed =
                fixture.steed(CreatureKind.PALE_STEED, new BlockPos(0, 1, 0));
            obstructed.setSteedState(obstructed.steedState()
                .withFatigue(SpectralSteedRules.MAX_FATIGUE)
                .withRest(Optional.of(obstructedSite),
                    Optional.of(level.dimension().identifier().toString())));
            SpectralSteedRuntime.tick(obstructed, level);
            helper.assertFalse(obstructed.steedState().rest().isPresent(),
                "head obstruction releases a retained site immediately");

            helper.setBlock(new BlockPos(1, 2, 0), Blocks.AIR);
            helper.setBlock(new BlockPos(1, 0, 0), Blocks.WATER);
            final SpectralSteedEntity unsafeSupport =
                fixture.steed(CreatureKind.PALE_STEED, new BlockPos(0, 1, 0));
            unsafeSupport.setSteedState(unsafeSupport.steedState()
                .withFatigue(SpectralSteedRules.MAX_FATIGUE)
                .withRest(Optional.of(obstructedSite),
                    Optional.of(level.dimension().identifier().toString())));
            SpectralSteedRuntime.tick(unsafeSupport, level);
            helper.assertFalse(unsafeSupport.steedState().rest().isPresent(),
                "fluid and non-sturdy support release a retained site immediately");

            helper.assertTrue(helper.getBlockState(new BlockPos(1, 0, 1)).is(Blocks.STONE),
                "the floor the steed stood on is untouched");
            final AABB arena = steed.getBoundingBox().inflate(8.0);
            final List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, arena);
            helper.assertTrue(drops.isEmpty(),
                "a steed neither eats, breaks nor drops the hay it rested beside");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    /**
     * The caps and the neighbouring families. One rider, the frozen owner aura still delivered by the
     * shared runtime on the live tick, and no overlap at all with the Owl.
     */
    public static void steedTwoPlayerCapsAurasAndOwlIsolation(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final boolean[] scheduled = {false};
        try {
            floor(helper);
            final ServerLevel level = helper.getLevel();
            final SpectralSteedEntity steed = fixture.steed(CreatureKind.PALE_STEED, new BlockPos(1, 1, 1));
            final ServerPlayer owner = fixture.player(new BlockPos(1, 1, 1));
            final ServerPlayer other = fixture.player(new BlockPos(2, 1, 2));
            CreatureBehaviorState.bind(steed, owner.getUUID());
            helper.assertTrue(owner.startRiding(steed), "the owner mounts");
            helper.assertFalse(other.startRiding(steed), "a second player never gets on");
            helper.assertValueEqual(steed.getPassengers().size(), 1, "the passenger cap is one");
            helper.assertFalse(SpectralSteedRuntime.controllingOwner(steed)
                    .filter(other::equals).isPresent(),
                "the second player never becomes the controller");

            final CreatureBehaviorProfile paleProfile =
                CreatureBehaviorProfile.find(CreatureKind.PALE_STEED).orElseThrow();
            final CreatureBehaviorProfile nightProfile =
                CreatureBehaviorProfile.find(CreatureKind.NIGHTMARE).orElseThrow();
            helper.assertTrue(paleProfile.has(Feature.RIDEABLE_BOND) && paleProfile.has(Feature.OWNER_AURA),
                "the Pale Steed's frozen profile is unchanged");
            helper.assertTrue(nightProfile.has(Feature.RIDEABLE_BOND)
                    && nightProfile.has(Feature.OWNER_AURA)
                    && nightProfile.has(Feature.FIRE_MELEE),
                "the Nightmare's frozen profile is unchanged");

            final ArcaneMob owl = fixture.arcane(CreatureKind.OWL, "owl", new BlockPos(0, 1, 2));
            CreatureBehaviorState.bind(owl, owner.getUUID());
            helper.assertFalse(owl instanceof SpectralSteedEntity, "an Owl is not a steed body");
            helper.assertFalse(other.startRiding(owl), "an Owl is not rideable, bond or no bond");
            helper.assertValueEqual(
                Set.copyOf(AmbientActivityProfile.forType(ActivityType.HAY_REST).kinds()),
                Set.of(CreatureKind.PALE_STEED, CreatureKind.NIGHTMARE),
                "hay rest belongs to exactly the two steeds");
            helper.assertFalse(AmbientActivityProfile.forKind(CreatureKind.OWL).stream()
                    .anyMatch(profile -> profile.type() == ActivityType.HAY_REST),
                "an Owl has no hay behaviour to inherit");

            helper.runAfterDelay(25, () -> {
                try {
                    helper.assertTrue(steed.hasEffect(MobEffects.SPEED),
                        "the shared owner aura still reaches a bound Pale Steed on the live tick");
                    helper.assertTrue(level.getEntity(steed.getUUID()) == steed,
                        "the steed under test is the one the level actually holds");
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

    private static void floor(final GameTestHelper helper) {
        for (int x = -6; x <= 8; x++) {
            for (int z = -6; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /**
     * Clears the one-block apron around the arena at standing height.
     *
     * <p>The dense landmark field below writes hay one block outside the structure so that a
     * landmark on the arena edge still has a blocked stance on every side. GameTest cleanup
     * restores the structure and nothing around it, so without this the second run of the same
     * fixture would start with hay already sitting beside the arena and the earlier release case
     * would find a landmark it was never given. Clearing it here is what makes run one and run
     * seventeen the same run.</p>
     */
    private static void clearApron(final GameTestHelper helper) {
        for (int x = -6; x <= 8; x++) {
            for (int z = -6; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
            }
        }
    }

    private static final class FixtureScope implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<Entity> entities = new ArrayList<>();
        private boolean closed;

        private FixtureScope(final GameTestHelper helper) {
            this.helper = helper;
        }

        @SuppressWarnings("unchecked")
        private SpectralSteedEntity steed(final CreatureKind kind, final BlockPos position) {
            final String id = kind == CreatureKind.NIGHTMARE ? "nightmare" : "pale_steed";
            final EntityType<? extends net.minecraft.world.entity.monster.zombie.Zombie> type =
                (EntityType<? extends net.minecraft.world.entity.monster.zombie.Zombie>)
                    ModEntities.ALL.get(id).get();
            final SpectralSteedEntity steed = new SpectralSteedEntity(type, helper.getLevel(), kind);
            place(steed, position);
            return track(steed);
        }

        @SuppressWarnings("unchecked")
        private ArcaneMob arcane(final CreatureKind kind, final String id, final BlockPos position) {
            final EntityType<? extends net.minecraft.world.entity.monster.zombie.Zombie> type =
                (EntityType<? extends net.minecraft.world.entity.monster.zombie.Zombie>)
                    ModEntities.ALL.get(id).get();
            final ArcaneMob mob = new ArcaneMob(type, helper.getLevel(), kind);
            place(mob, position);
            return track(mob);
        }

        private void place(final Entity entity, final BlockPos position) {
            final BlockPos absolute = helper.absolutePos(position);
            entity.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
            entity.setDeltaMovement(Vec3.ZERO);
            helper.getLevel().addFreshEntity(entity);
        }

        private <T extends Entity> T spawn(final EntityType<T> type, final BlockPos position) {
            return track(helper.spawn(type, position, EntitySpawnReason.EVENT));
        }

        /** An entity placed outside the arena, for proving that a declared radius actually binds. */
        private <T extends Entity> T loose(
            final EntityType<T> type,
            final BlockPos anchor,
            final int offsetX
        ) {
            final T entity = type.create(helper.getLevel(), EntitySpawnReason.EVENT);
            if (entity == null) {
                throw new IllegalStateException("could not build a control entity of " + type);
            }
            final BlockPos absolute = helper.absolutePos(anchor).offset(offsetX, 0, 0);
            entity.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
            entity.setDeltaMovement(Vec3.ZERO);
            helper.getLevel().addFreshEntity(entity);
            return track(entity);
        }

        private ServerPlayer player(final BlockPos position) {
            final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
            final net.minecraft.network.Connection connection =
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            final net.minecraft.server.network.CommonListenerCookie cookie =
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            final BlockPos absolute = helper.absolutePos(position);
            player.teleportTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
            return track(player);
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
            for (final Entity entity : entities) {
                if (entity.isPassenger()) {
                    entity.stopRiding();
                }
                if (entity instanceof ServerPlayer player) {
                    player.getInventory().clearContent();
                    player.removeAllEffects();
                }
                entity.discard();
            }
            entities.clear();
        }
    }

}
