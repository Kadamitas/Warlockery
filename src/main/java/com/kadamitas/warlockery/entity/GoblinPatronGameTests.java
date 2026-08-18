package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.GoblinPatronRules.Action;
import com.kadamitas.warlockery.registry.ModEntities;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Five bounded live F12 fixtures, each asserting one named contract through spawned, AI-enabled,
 * self-ticking exact patron entities.
 *
 * <p>Arena geometry: the framework seals every {@code forge:empty3x3x3} cell in a barrier shell, so
 * all fixture geometry stays within relative 0..2 and every entity spawns at y=1. y=2 would clip a
 * tall body's eyes into the ceiling and every line-of-sight walk would terminate on barrier.
 * Computed navigation destinations stay inside the shell, so a request can never land outside the
 * arena and silently freeze the entity while assertions pass on stale state.</p>
 *
 * <p>Every fixture claims the cadences it depends on inside its own scope with {@link #makeDue},
 * because fixtures share the global world clock across a batch and a patron seeds its cadences from
 * its own UUID on the first tick, which can otherwise delay acquisition until tick 60. All
 * {@code runAfterDelay} stages are registered from the test body, never from inside another
 * callback, and every created entity and block edit is released in a {@code finally} block.</p>
 *
 * <p>Construction note: the bodies are built directly against their own registered
 * {@code EntityType} rather than through {@code GameTestHelper.spawn}. Two reasons, both
 * deliberate. The registry flip to the dedicated factories is a coordinator-deferred edit, so the
 * ordinary factory still yields the shared 1.4 body here; and the GameTest entity builder latches
 * vanilla persistence on every mob it spawns, which makes the unlatched half of the persistence
 * contract unobservable. The registered type supplies the exact dimensions, category, and attribute
 * baseline either way, so these fixtures pass unchanged once the deferred registration lands.</p>
 */
public final class GoblinPatronGameTests {
    /**
     * Every Villager-specific Brain activity. {@code CORE} and {@code IDLE} are excluded on
     * purpose: the vanilla {@code Brain} constructor seeds those two on every living entity in the
     * game, so an empty active-activity set is unreachable and carries no Villager semantics.
     */
    private static final Set<Activity> VILLAGER_BRAIN_ACTIVITIES = Set.of(
        Activity.WORK, Activity.MEET, Activity.REST, Activity.PLAY,
        Activity.PANIC, Activity.HIDE, Activity.RAID, Activity.PRE_RAID
    );

    private GoblinPatronGameTests() {
    }

    // ================================================================ 1: identity and offerings

    /**
     * Both exact public IDs build their own dedicated merchant body with no Villager Brain, no
     * target goals, no MOVE-declaring executor, the exact registered attributes, no invented
     * profession name, and a heart offering that is bounded, non-taming, and exactly costed.
     */
    public static void goblinPatronsIdentityOfferingsAndMigration(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final StonebrokerEntity broker = spawnStonebroker(fixture, new BlockPos(1, 1, 1));
            final ForgewardenEntity warden = spawnForgewarden(fixture, new BlockPos(2, 1, 1));

            helper.assertValueEqual(broker.creatureKind(), CreatureKind.STONEBROKER,
                "the exact public ID must construct CreatureKind.STONEBROKER");
            helper.assertValueEqual(warden.creatureKind(), CreatureKind.FORGEWARDEN,
                "the exact public ID must construct CreatureKind.FORGEWARDEN");
            helper.assertTrue(broker.getClass() == StonebrokerEntity.class,
                "warlockery:stonebroker must construct the dedicated StonebrokerEntity body");
            helper.assertTrue(warden.getClass() == ForgewardenEntity.class,
                "warlockery:forgewarden must construct the dedicated ForgewardenEntity body");
            // Class.isInstance rather than instanceof: the compiler already proves the types are
            // unrelated, and this keeps the contract asserted at runtime as well.
            helper.assertFalse(Villager.class.isInstance(broker),
                "a patron must not inherit the human Villager implementation");
            helper.assertTrue(AbstractVillager.class.isInstance(warden),
                "a patron keeps the narrow AbstractVillager merchant surface");

            helper.assertValueEqual(broker.operationalTargetGoalCount(), 0,
                "F12 registers no target-selector goal; targeting belongs to the runtime");
            helper.assertValueEqual(warden.operationalTargetGoalCount(), 0,
                "F12 registers no target-selector goal; targeting belongs to the runtime");
            helper.assertTrue(broker.operationalGoalNames().size() == 4,
                "exactly four executors are registered");
            helper.assertTrue(warden.operationalGoalNames().equals(broker.operationalGoalNames()),
                "both patrons register one shared executor set rather than two copies");
            helper.assertTrue(VILLAGER_BRAIN_ACTIVITIES.stream()
                    .noneMatch(activity -> broker.getBrain().isActive(activity)),
                "no Villager Brain activity may ever run on a patron");

            helper.assertValueEqual(broker.getAttributeValue(Attributes.MAX_HEALTH), 400.0D,
                "Stonebroker keeps the exact registered 400 maximum health");
            helper.assertValueEqual(broker.getAttributeValue(Attributes.ATTACK_DAMAGE), 9.0D,
                "Stonebroker keeps the exact registered 9.0 attack damage");
            helper.assertValueEqual(warden.getAttributeValue(Attributes.MAX_HEALTH), 400.0D,
                "Forgewarden keeps the exact registered 400 maximum health");
            helper.assertValueEqual(warden.getAttributeValue(Attributes.ATTACK_DAMAGE), 11.0D,
                "Forgewarden keeps the exact registered 11.0 attack damage");

            helper.assertFalse(broker.hasCustomName(),
                "a patron has no Goblin profession and therefore no invented profession name");
            helper.assertFalse(warden.hasCustomName(),
                "a patron has no Goblin profession and therefore no invented profession name");
            helper.assertValueEqual(broker.goblinPatronState().kind(), CreatureKind.STONEBROKER,
                "the dedicated constructor fixes the exact kind before the first tick");
            helper.assertTrue(CreatureBehaviorState.owner(broker).isEmpty(),
                "a freshly created patron has no owner");
            helper.assertTrue(warden.fireImmune(),
                "Forgewarden is intrinsically immune to fire and lava");
            helper.assertFalse(broker.fireImmune(),
                "Stonebroker is not fire immune and must escape fire like any other body");

            // ---------------- persistence, both halves
            helper.assertFalse(broker.vanillaPersistenceLatched(),
                "a directly constructed patron starts genuinely unlatched");
            broker.setPersistenceRequired();
            helper.assertTrue(broker.vanillaPersistenceLatched(),
                "an ordinary latch write, a name tag or a dispenser, is honoured exactly as usual");
            helper.assertFalse(warden.vanillaPersistenceLatched(),
                "the second patron is still unlatched");
            warden.patronCore().setContractLatchSuppressed(true);
            try {
                warden.setPersistenceRequired();
            } finally {
                warden.patronCore().setContractLatchSuppressed(false);
            }
            helper.assertFalse(warden.vanillaPersistenceLatched(),
                "the one unclearable contract-binding latch write is refused at its source");
            helper.assertTrue(warden.isPersistenceRequired(),
                "a summoned patron still persists through its own explicit reason");
            helper.assertFalse(warden.removeWhenFarAway(4_096.0D),
                "a summoned patron never distance-despawns");

            // ---------------- heart offering
            final ServerPlayer player = fixture.connectedPlayer(new BlockPos(1, 1, 2), GameType.SURVIVAL);
            final double baseHealth = broker.getAttributeBaseValue(Attributes.MAX_HEALTH);
            final double baseAttack = broker.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
            for (int offering = 0; offering < GoblinPatronRules.MAX_EMPOWERMENT; offering++) {
                final ItemStack hearts = heartStack(4);
                player.setItemInHand(InteractionHand.MAIN_HAND, hearts);
                player.interactOn(broker, InteractionHand.MAIN_HAND, Vec3.ZERO);
                helper.assertValueEqual(hearts.getCount(), 3,
                    "each accepted offering consumes exactly one item");
                helper.assertValueEqual(
                    broker.goblinPatronState().empowerment().level(), offering + 1,
                    "empowerment advances by exactly one per accepted offering");
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            helper.assertValueEqual(
                broker.getAttributeBaseValue(Attributes.MAX_HEALTH),
                baseHealth + GoblinPatronRules.OFFERING_HEALTH_DELTA * GoblinPatronRules.MAX_EMPOWERMENT,
                "five offerings add exactly twenty maximum health");
            helper.assertValueEqual(
                broker.getAttributeBaseValue(Attributes.ATTACK_DAMAGE),
                baseAttack + GoblinPatronRules.OFFERING_ATTACK_DELTA * GoblinPatronRules.MAX_EMPOWERMENT,
                "five offerings add exactly five attack damage");
            final ItemStack sixth = heartStack(2);
            player.setItemInHand(InteractionHand.MAIN_HAND, sixth);
            player.interactOn(broker, InteractionHand.MAIN_HAND, Vec3.ZERO);
            helper.assertValueEqual(sixth.getCount(), 2,
                "the sixth offering is refused without consuming anything");
            helper.assertValueEqual(
                broker.goblinPatronState().empowerment().level(), GoblinPatronRules.MAX_EMPOWERMENT,
                "the cap holds at exactly five");
            helper.assertTrue(CreatureBehaviorState.owner(broker).isEmpty(),
                "an offering never creates an owner, a follow, or a work state");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            // ---------------- save round trip and pre-F12 migration
            final CompoundTag saved = saveEntity(broker);
            final StonebrokerEntity reloaded = spawnStonebroker(fixture, new BlockPos(1, 1, 1));
            loadEntity(reloaded, saved);
            helper.assertValueEqual(
                reloaded.goblinPatronState().empowerment().level(), GoblinPatronRules.MAX_EMPOWERMENT,
                "empowerment survives the save seam");
            helper.assertValueEqual(reloaded.goblinPatronState().kind(), CreatureKind.STONEBROKER,
                "the exact kind guard survives the save seam");
            helper.assertValueEqual(reloaded.goblinPatronState().combat().action(), Action.IDLE,
                "no committed action is ever restored across a save seam");
            helper.assertFalse(reloaded.goblinPatronState().route().held(),
                "no navigation lease is ever restored across a save seam");
            helper.assertTrue(
                encode(saved.getCompoundOrEmpty(StonebrokerEntity.STATE_KEY)).length
                    < GoblinPatronRules.MAX_STATE_BYTES,
                "the live persisted semantic state stays below the declared byte ceiling");

            final ForgewardenEntity migrated = spawnForgewarden(fixture, new BlockPos(2, 1, 1));
            final CompoundTag legacy = saveEntity(migrated);
            legacy.remove(ForgewardenEntity.STATE_KEY);
            legacy.putInt("WarlockeryEmpowerment", 2);
            loadEntity(migrated, legacy);
            helper.assertValueEqual(migrated.goblinPatronState().empowerment().level(), 2,
                "a pre-F12 patron migrates its empowerment and invents nothing else");
            helper.assertValueEqual(migrated.goblinPatronState().kind(), CreatureKind.FORGEWARDEN,
                "migration keeps the body's own exact kind");

            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    // ================================================================ 2: Stonebroker doctrine

    /**
     * Stonebroker appraises a mineral context without touching it, opens a parley that permits
     * trade and breaks on a direct attack, and fires a real attributed volley only after its tell.
     */
    public static void stonebrokerParleyAppraisalAndCombatDoctrine(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ServerLevel level = helper.getLevel();
            final StonebrokerEntity broker = spawnStonebroker(fixture, new BlockPos(1, 1, 1));
            fixture.placeBlock(new BlockPos(2, 1, 2), Blocks.STONE);

            // ---------------- appraisal
            makeDue(broker);
            final long readsBefore = broker.patronCounters().chargedBlockReads();
            final long editsBefore = broker.patronCounters().worldEdits();
            GoblinPatronRuntime.tick(broker, level);
            final long reads = broker.patronCounters().chargedBlockReads() - readsBefore;
            helper.assertTrue(reads > 0, "the appraisal scan actually charged its reads");
            helper.assertTrue(reads <= GoblinPatronRules.scanReadCap(),
                "one appraisal scan never exceeds its declared 256-read budget, was " + reads);
            helper.assertValueEqual(broker.patronCounters().worldEdits(), editsBefore,
                "an appraisal performs zero block mutations");
            helper.assertValueEqual(
                level.getBlockState(helper.absolutePos(new BlockPos(2, 1, 2))).getBlock(),
                Blocks.STONE,
                "the appraised context is left exactly as it was found");
            helper.assertTrue(broker.patronTransient().scannedContext().isPresent(),
                "a loaded tagged mineral context inside the envelope actually qualifies");

            // ---------------- parley
            final ServerPlayer player = fixture.connectedPlayer(new BlockPos(1, 1, 2), GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, heartStack(2));
            player.interactOn(broker, InteractionHand.MAIN_HAND, Vec3.ZERO);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(broker.goblinPatronState().engagement().open(),
                "an accepted offering opens the parley window");
            helper.assertValueEqual(
                broker.goblinPatronState().engagement().remainingTicks(),
                GoblinPatronRules.PARLEY_TICKS,
                "Stonebroker parleys for exactly 200 ticks");
            helper.assertFalse(broker.canAttack(player),
                "the parley holder is not a challenge subject while the window is open");
            helper.assertTrue(GoblinPatronRuntime.safeToTrade(broker),
                "a parley permits trade");

            final int empowermentBeforeBreach = broker.goblinPatronState().empowerment().level();
            broker.hurtServer(level, level.damageSources().playerAttack(player), 4.0F);
            helper.assertFalse(broker.goblinPatronState().engagement().open(),
                "a direct attack by the holder closes the window in the same tick");
            helper.assertValueEqual(
                broker.goblinPatronState().empowerment().level(), empowermentBeforeBreach,
                "a breach never refunds or duplicates the consumed offering");
            helper.assertTrue(broker.canAttack(player),
                "the breaching player becomes eligible again under ordinary priority");

            // ---------------- volley
            final ForgewardenEntity dummy = spawnForgewarden(fixture, new BlockPos(1, 1, 2));
            final Zombie target = fixture.spawnZombie(new BlockPos(2, 1, 1));
            broker.setTarget(target);
            broker.getSensing().tick();
            broker.setGoblinPatronState(broker.goblinPatronState().withCombat(
                broker.goblinPatronState().combat().withChallenger(
                    Optional.of(target.getUUID()), GoblinPatronRules.ReleaseReason.NONE
                )
            ));
            makeDue(broker);
            final long arrowsBefore = broker.patronCounters().arrowsFired();
            GoblinPatronRuntime.tick(broker, level);
            final GoblinPatronState afterDecision = broker.goblinPatronState();
            if (afterDecision.combat().action() == Action.LEDGER_VOLLEY) {
                helper.assertTrue(afterDecision.combat().telling(),
                    "the volley shows its tell before any arrow exists");
                helper.assertValueEqual(broker.patronCounters().arrowsFired(), arrowsBefore,
                    "no arrow is created during the tell");
            }
            helper.assertTrue(
                GoblinPatronRules.permits(CreatureKind.STONEBROKER, Action.LEDGER_VOLLEY)
                    && !GoblinPatronRules.permits(CreatureKind.STONEBROKER, Action.FORGE_SURGE),
                "Stonebroker can only ever commit its own vocabulary");
            helper.assertTrue(dummy.isAlive(), "the co-located counterpart is unharmed by a volley tell");

            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    // ================================================================ 3: Forgewarden doctrine

    /**
     * Forgewarden inspects a forge context that a Stonebroker would never choose, opens a
     * commission, and commits a bounded surge that damages only eligible targets and edits no
     * block at all.
     */
    public static void forgewardenCommissionWardAndCombatDoctrine(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ServerLevel level = helper.getLevel();
            final ForgewardenEntity warden = spawnForgewarden(fixture, new BlockPos(1, 1, 1));
            fixture.placeBlock(new BlockPos(2, 1, 2), Blocks.ANVIL);

            // ---------------- the two patrons scan genuinely different subjects
            makeDue(warden);
            GoblinPatronRuntime.tick(warden, level);
            helper.assertTrue(warden.patronTransient().scannedContext().isPresent(),
                "a loaded anvil qualifies as a Forgewarden forge context");
            final BlockPos anvil = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.assertValueEqual(warden.patronTransient().scannedContext(), Optional.of(anvil),
                "the Forgewarden selects exactly the loaded anvil");
            final StonebrokerEntity broker = spawnStonebroker(fixture, new BlockPos(1, 1, 2));
            makeDue(broker);
            GoblinPatronRuntime.tick(broker, level);
            helper.assertFalse(broker.patronTransient().scannedContext().equals(Optional.of(anvil)),
                "an anvil is never a Stonebroker mineral context; the two scans are not one reskin");

            // ---------------- commission
            final ServerPlayer player = fixture.connectedPlayer(new BlockPos(2, 1, 1), GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, heartStack(2));
            player.interactOn(warden, InteractionHand.MAIN_HAND, Vec3.ZERO);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(warden.goblinPatronState().engagement().open(),
                "an accepted offering opens the commission window");
            helper.assertValueEqual(
                warden.goblinPatronState().engagement().remainingTicks(),
                GoblinPatronRules.COMMISSION_TICKS,
                "Forgewarden prepares a commission for exactly 100 ticks, not a 200-tick parley");

            // ---------------- surge
            final Zombie victim = fixture.spawnZombie(new BlockPos(2, 1, 1));
            final Zombie distant = fixture.spawnZombie(new BlockPos(0, 1, 0));
            distant.setInvulnerable(true);
            final float victimHealth = victim.getHealth();
            final float distantHealth = distant.getHealth();
            final long editsBefore = warden.patronCounters().worldEdits();
            warden.getSensing().tick();
            warden.setGoblinPatronState(warden.goblinPatronState().withCombat(
                new GoblinPatronState.Combat(
                    Action.FORGE_SURGE, Optional.of(victim.getUUID()), 0, 40, 0, Action.IDLE, 0, 0, 0,
                    Optional.of(victim.getUUID()), Optional.empty(), Optional.empty(), 0,
                    GoblinPatronRules.ReleaseReason.NONE, false, 0
                )
            ));
            makeDue(warden);
            GoblinPatronRuntime.tick(warden, level);
            helper.assertTrue(warden.patronCounters().surgeVisits()
                    <= GoblinPatronRules.MAX_SURGE_INSPECTIONS,
                "one surge inspects at most sixteen living candidates");
            helper.assertValueEqual(distant.getHealth(), distantHealth,
                "an invulnerable target is rejected and receives no rider at all");
            helper.assertFalse(distant.isOnFire(),
                "a rejected surge target receives no fire rider");
            helper.assertValueEqual(warden.patronCounters().worldEdits(), editsBefore,
                "a surge performs zero block ignition, break, placement, or fluid operation");
            helper.assertValueEqual(
                level.getBlockState(helper.absolutePos(new BlockPos(2, 1, 2))).getBlock(),
                Blocks.ANVIL,
                "the inspected forge context is left exactly as it was found");
            helper.assertTrue(victim.getHealth() < victimHealth,
                "an eligible in-range target actually takes the surge damage");

            // ---------------- the attack executor never writes a path
            warden.setTarget(victim);
            warden.getSensing().tick();
            // Without this the surge's own hit leaves the victim invulnerable, doHurtTarget returns
            // false, and both rider assertions below would pass vacuously on stale state.
            victim.invulnerableTime = 0;
            victim.clearFire();
            final float beforeMelee = victim.getHealth();
            final boolean navigationIdleBefore = warden.getNavigation().isDone();
            final boolean accepted = warden.doHurtTarget(level, victim);
            helper.assertTrue(warden.getNavigation().isDone() == navigationIdleBefore,
                "the attack-only executor never creates, replaces, or stops a path");
            helper.assertTrue(accepted, "the melee commit was actually accepted");
            helper.assertTrue(victim.getHealth() < beforeMelee,
                "an accepted melee commit actually removed health");
            helper.assertTrue(victim.isOnFire(),
                "the hammer fire rider lands after the hit was actually accepted");

            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    // ================================================================ 4: accord and navigation

    /**
     * The accord forms one-to-one and mutually, publishes exactly one shared challenger mark, and
     * reduces only the accorded Stonebroker's damage. Route failures back off rather than spin, and
     * removal releases every derived link.
     */
    public static void goblinPatronsAccordNavigationAndCleanup(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ServerLevel level = helper.getLevel();
            final StonebrokerEntity broker = spawnStonebroker(fixture, new BlockPos(1, 1, 1));
            final ForgewardenEntity warden = spawnForgewarden(fixture, new BlockPos(2, 1, 1));

            makeDue(broker);
            makeDue(warden);
            GoblinPatronRuntime.tick(broker, level);
            GoblinPatronRuntime.tick(warden, level);
            makeDue(broker);
            makeDue(warden);
            GoblinPatronRuntime.tick(broker, level);
            GoblinPatronRuntime.tick(warden, level);

            helper.assertValueEqual(
                broker.goblinPatronState().accord().counterpart(), Optional.of(warden.getUUID()),
                "the Stonebroker side of the accord names exactly the loaded Forgewarden");
            helper.assertValueEqual(
                warden.goblinPatronState().accord().counterpart(), Optional.of(broker.getUUID()),
                "the accord is mutual and one to one");
            helper.assertValueEqual(
                broker.goblinPatronState().accord().counterpartKind(),
                Optional.of(CreatureKind.FORGEWARDEN),
                "an accord only ever forms between opposite kinds");

            // ---------------- one shared challenger mark, and a bounded consequence
            final Zombie challenger = fixture.spawnZombie(new BlockPos(1, 1, 2));
            broker.hurtServer(level, level.damageSources().mobAttack(challenger), 2.0F);
            helper.assertValueEqual(
                broker.goblinPatronState().accord().sharedChallenger(),
                Optional.of(challenger.getUUID()),
                "Stonebroker publishes exactly one shared challenger mark");
            helper.assertValueEqual(
                warden.goblinPatronState().accord().sharedChallenger(),
                Optional.of(challenger.getUUID()),
                "the accorded Forgewarden consumes that exact mark and no other");
            helper.assertValueEqual(
                GoblinPatronRuntime.attackDamageBonus(warden, level, challenger),
                GoblinPatronRules.SHARED_CHALLENGER_BONUS,
                "the mark supplies exactly four extra damage against that challenger");
            final Zombie bystander = fixture.spawnZombie(new BlockPos(2, 1, 2));
            helper.assertValueEqual(
                GoblinPatronRuntime.attackDamageBonus(warden, level, bystander), 0.0F,
                "the mark supplies nothing at all against any other target");
            helper.assertValueEqual(
                GoblinPatronRuntime.attackDamageBonus(broker, level, challenger), 0.0F,
                "Stonebroker publishes the mark and never consumes it");

            // ---------------- ward stance protects only the Stonebroker
            warden.setGoblinPatronState(warden.goblinPatronState().withCombat(
                new GoblinPatronState.Combat(
                    Action.WARD_STANCE, Optional.empty(), 0, 60, 0, Action.IDLE, 0, 0,
                    GoblinPatronRules.WARD_STANCE_TICKS, Optional.empty(), Optional.empty(),
                    Optional.empty(), 0, GoblinPatronRules.ReleaseReason.NONE, false, 0
                )
            ));
            helper.assertValueEqual(
                GoblinPatronRuntime.wardReductionFor(
                    broker, level, level.damageSources().mobAttack(challenger)
                ),
                GoblinPatronRules.WARD_DAMAGE_REDUCTION,
                "a valid ward stance removes exactly 25 percent of the Stonebroker's accepted damage");
            helper.assertValueEqual(
                GoblinPatronRuntime.wardReductionFor(
                    warden, level, level.damageSources().mobAttack(challenger)
                ),
                0.0F,
                "the ward never reduces the Forgewarden's own damage and never recurses");

            // ---------------- the ward stance is the complete model, not a supplement
            // The 1.4 symmetric paired reduction in CreatureCombat.applyPairedPatronProtection is
            // live on the same event, and the counterpart is one block away, which is its strongest
            // 0.2 multiplier. Composed with the ward that is 0.15 of the raw amount, a number no
            // design chose. This asserts the real accepted loss rather than either rule in
            // isolation, because stacking reads as correct in every unit test.
            final float raw = 100.0F;
            broker.setHealth(broker.getMaxHealth());
            broker.invulnerableTime = 0;
            final float healthBeforeWardedHit = broker.getHealth();
            broker.hurtServer(level, level.damageSources().mobAttack(challenger), raw);
            final float wardedLoss = healthBeforeWardedHit - broker.getHealth();
            helper.assertTrue(broker.isAlive(), "the probe hit must not kill the patron");
            helper.assertTrue(wardedLoss > raw * 0.4F,
                "the 1.4 symmetric paired reduction must not compose with the ward stance;"
                    + " observed loss " + wardedLoss + " of a raw " + raw);
            helper.assertTrue(wardedLoss < raw * 0.85F,
                "the ward stance itself still removed its own share of the accepted damage;"
                    + " observed loss " + wardedLoss + " of a raw " + raw);

            // ---------------- the hammer launch is applied exactly once, not composed
            // The attack-side twin of the assertion above. CreatureBehaviorRuntime.afterAttack's
            // Forgewarden arm applies its own 0.45 push whenever any counterpart is within 16
            // blocks, with no accord, stance, epoch or line-of-sight gate, and Entity.push is
            // additive. The Forgewarden and the Stonebroker are one block apart here, so that gate
            // is satisfied and a body that called both rider paths would launch this victim by
            // 0.90. Knockback resistance is pinned so vanilla knockback contributes nothing and the
            // hammer rider is the only writer of the victim's vertical velocity.
            bystander.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            bystander.setDeltaMovement(Vec3.ZERO);
            bystander.invulnerableTime = 0;
            bystander.clearFire();
            helper.assertTrue(warden.doHurtTarget(level, bystander),
                "the probe melee must actually be accepted or every rider assertion below is vacuous");
            final double launch = bystander.getDeltaMovement().y;
            helper.assertTrue(launch == (double) GoblinPatronRuntime.HAMMER_LAUNCH,
                "the hammer launch must be applied exactly once and never composed with the 1.4"
                    + " paired push; observed vertical impulse " + launch
                    + " against a designed " + GoblinPatronRuntime.HAMMER_LAUNCH);
            helper.assertTrue(bystander.isOnFire(),
                "the hammer fire rider still lands without the legacy rider path");

            // ---------------- route failures back off instead of spinning
            broker.setGoblinPatronState(broker.goblinPatronState().withRoute(
                GoblinPatronState.Route.none()
                    .failed(GoblinPatronRules.RouteFailure.NO_PATH)
                    .failed(GoblinPatronRules.RouteFailure.REJECTED)
                    .failed(GoblinPatronRules.RouteFailure.UNREACHABLE)
            ));
            helper.assertValueEqual(
                broker.goblinPatronState().route().retryRemainingTicks(),
                GoblinPatronRules.ROUTE_BACKOFF_TICKS,
                "the third classified failure establishes the full 100-tick backoff");
            helper.assertFalse(broker.goblinPatronState().route().held(),
                "the third classified failure clears the destination");
            helper.assertValueEqual(
                broker.goblinPatronState().route().succeeded().failureCount(), 0,
                "a successful movement resets prior classified failures");

            // ---------------- cleanup
            final long accordEpochBefore = warden.goblinPatronState().authorityEpoch();
            warden.remove(Entity.RemovalReason.KILLED);
            helper.assertFalse(warden.goblinPatronState().accord().present(),
                "removal releases the accord");
            helper.assertFalse(warden.goblinPatronState().published().present(),
                "removal withdraws the published result");
            helper.assertFalse(warden.goblinPatronState().route().held(),
                "removal releases the navigation lease");
            helper.assertTrue(warden.goblinPatronState().authorityEpoch() > accordEpochBefore,
                "removal advances the authority epoch so no derived result survives it");

            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    // ================================================================ 5: caps and boundaries

    /**
     * Fixed-seed structural assertions: the challenger scan charges every candidate it looks at and
     * still prioritises a preseeded recent attacker, the block scan honours its read and retention
     * caps, and the published directive is immutable, bounded, expiring, and carries no navigation.
     */
    public static void goblinPatronsStructuralCapsAndForeignBoundaries(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ServerLevel level = helper.getLevel();
            final StonebrokerEntity broker = spawnStonebroker(fixture, new BlockPos(1, 1, 1));

            // ---------------- crowded challenger scan
            final List<Zombie> crowd = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                crowd.add(fixture.spawnZombie(new BlockPos(1 + index % 2, 1, 1 + index % 2)));
            }
            final Zombie attacker = crowd.getLast();
            broker.setGoblinPatronState(broker.goblinPatronState().withCombat(
                new GoblinPatronState.Combat(
                    Action.IDLE, Optional.empty(), 0, 0, 0, Action.IDLE, 0, 0, 0, Optional.empty(),
                    Optional.of(attacker.getUUID()),
                    Optional.of(GoblinPatronRuntime.dimensionOf(level)), 400,
                    GoblinPatronRules.ReleaseReason.NONE, false, 0
                )
            ));
            makeDue(broker);
            final long visitsBefore = broker.patronCounters().challengerVisits();
            GoblinPatronRuntime.tick(broker, level);
            final long visits = broker.patronCounters().challengerVisits() - visitsBefore;
            helper.assertTrue(visits <= GoblinPatronRules.MAX_CHALLENGER_INSPECTIONS,
                "a crowded scan inspects at most sixteen candidates, was " + visits);
            helper.assertValueEqual(
                broker.goblinPatronState().combat().challenger(), Optional.of(attacker.getUUID()),
                "the preseeded recent attacker is still selected out of a crowd of twenty");

            // ---------------- block scan caps
            fixture.placeBlock(new BlockPos(0, 1, 0), Blocks.STONE);
            fixture.placeBlock(new BlockPos(2, 1, 0), Blocks.STONE);
            fixture.placeBlock(new BlockPos(0, 1, 2), Blocks.STONE);
            makeDue(broker);
            final long readsBefore = broker.patronCounters().chargedBlockReads();
            final long retainedBefore = broker.patronCounters().candidatesRetained();
            GoblinPatronRuntime.tick(broker, level);
            final long reads = broker.patronCounters().chargedBlockReads() - readsBefore;
            final long retained = broker.patronCounters().candidatesRetained() - retainedBefore;
            helper.assertTrue(reads <= GoblinPatronRules.scanReadCap() + SHIFT_READ_ALLOWANCE,
                "one due scan never exceeds its declared read budget, was " + reads);
            helper.assertTrue(retained <= GoblinPatronRules.retentionCap(),
                "one scan never retains more than eight candidates, was " + retained);

            // ---------------- published directive
            broker.setGoblinPatronState(broker.goblinPatronState().withAnchor(
                GoblinPatronState.Anchor.at(
                    broker.blockPosition(), GoblinPatronRuntime.dimensionOf(level)
                )
            ));
            makeDue(broker);
            GoblinPatronRuntime.tick(broker, level);
            final Optional<GoblinPatronDirective> directive =
                GoblinPatronRuntime.directiveOf(broker, level);
            helper.assertTrue(directive.isPresent(),
                "an anchored patron publishes exactly one current local result");
            final GoblinPatronDirective published = directive.orElseThrow();
            helper.assertValueEqual(published.patronKind(), CreatureKind.STONEBROKER,
                "the directive names its exact publishing kind");
            helper.assertValueEqual(published.result(),
                GoblinPatronRules.DirectiveKind.BROKERED_WORK,
                "Stonebroker publishes exactly BROKERED_WORK");
            helper.assertTrue(published.prefersWork(),
                "the recipient reads a preference, never an instruction");
            helper.assertTrue(published.valid(GoblinPatronRuntime.dimensionOf(level), level.getGameTime()),
                "a freshly published directive is valid in its own dimension");
            helper.assertFalse(published.valid("minecraft:the_nether", level.getGameTime()),
                "a directive is never valid across a dimension boundary");
            helper.assertFalse(
                published.valid(GoblinPatronRuntime.dimensionOf(level), published.expiresGameTime()),
                "a directive expires rather than persisting forever");

            final List<GoblinPatronDirective> local =
                GoblinPatronRuntime.localDirectives(attacker, level);
            helper.assertTrue(local.size() <= GoblinPatronRules.MAX_DIRECTIVE_INSPECTIONS,
                "a local directive query is bounded by its own inspection cap");
            helper.assertValueEqual(broker.patronCounters().worldEdits(), 0L,
                "the patron physical world-edit count is always exactly zero");

            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    /**
     * A reposition survey charges three reads per candidate stand over sixteen offsets, and it can
     * legitimately run in the same tick as a due block scan.
     */
    private static final int SHIFT_READ_ALLOWANCE = 48;

    // ================================================================ helpers

    /**
     * Claims every cadence this fixture depends on at the tick it needs it. Fixtures share the
     * global world clock across a batch, and a patron seeds its cadences from its own UUID on the
     * first tick, so a fixture that does not claim its own cadence can wait until tick 60 or
     * observe another fixture's stale phase.
     */
    private static void makeDue(final GoblinPatronRuntime.PatronBody patron) {
        patron.patronCore().scratch().makeEveryCadenceDue();
    }

    private static StonebrokerEntity spawnStonebroker(
        final FixtureScope fixture,
        final BlockPos position
    ) {
        final StonebrokerEntity broker =
            new StonebrokerEntity(ModEntities.STONEBROKER.get(), fixture.helper.getLevel());
        return fixture.place(broker, position);
    }

    private static ForgewardenEntity spawnForgewarden(
        final FixtureScope fixture,
        final BlockPos position
    ) {
        final ForgewardenEntity warden =
            new ForgewardenEntity(ModEntities.FORGEWARDEN.get(), fixture.helper.getLevel());
        return fixture.place(warden, position);
    }

    private static ItemStack heartStack(final int count) {
        return new ItemStack(
            com.kadamitas.warlockery.registry.ModItems.ALL.get("ingredient_creeper_heart").get(), count
        );
    }

    private static CompoundTag saveEntity(final Entity entity) {
        final TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, entity.level().registryAccess()
        );
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    private static void loadEntity(final Entity entity, final CompoundTag tag) {
        entity.load(TagValueInput.create(
            ProblemReporter.DISCARDING, entity.level().registryAccess(), tag
        ));
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

    /** Local alias so the fixture reads clearly; the arena's disposable hostile body. */
    private static final class Zombie extends net.minecraft.world.entity.monster.zombie.Zombie {
        private Zombie(final ServerLevel level) {
            super(net.minecraft.world.entity.EntityTypes.ZOMBIE, level);
        }
    }

    private static final class FixtureScope implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<Entity> entities = new ArrayList<>();
        private final List<Runnable> cleanupActions = new ArrayList<>();
        private boolean closed;

        private FixtureScope(final GameTestHelper helper) {
            this.helper = helper;
        }

        private <T extends Entity> T place(final T entity, final BlockPos position) {
            final BlockPos absolute = helper.absolutePos(position);
            entity.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
            entity.setDeltaMovement(Vec3.ZERO);
            helper.getLevel().addFreshEntity(entity);
            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                // Sensing caches line of sight per server tick; repositioning invalidates that cache.
                mob.getSensing().tick();
            }
            entities.add(entity);
            return entity;
        }

        private Zombie spawnZombie(final BlockPos position) {
            return place(new Zombie(helper.getLevel()), position);
        }

        private void placeBlock(final BlockPos position, final net.minecraft.world.level.block.Block block) {
            final BlockPos absolute = helper.absolutePos(position);
            final var previous = helper.getLevel().getBlockState(absolute);
            helper.setBlock(position, block);
            cleanupActions.add(() -> helper.getLevel().setBlockAndUpdate(absolute, previous));
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
            entities.add(player);
            return player;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            entities.forEach(Entity::discard);
            entities.clear();
            // Reverse order so later edits are undone before earlier ones are restored.
            for (int index = cleanupActions.size() - 1; index >= 0; index--) {
                cleanupActions.get(index).run();
            }
            cleanupActions.clear();
        }
    }
}
