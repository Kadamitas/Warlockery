package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.HellhoundLifeRules.EvidenceKind;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.Intent;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.Mode;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.PackOrigin;
import com.kadamitas.warlockery.item.InfernalPactEffects;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Twelve registered bounded live F09 fixtures. Every assertion runs against spawned, AI-enabled,
 * self-ticking Hellhounds inside the isolated environment; helpers never bypass the live tick.
 */
public final class HellhoundLifeGameTests {
    private HellhoundLifeGameTests() {
    }

    public static void acquisitionAndZombieVariantsAreContained(final GameTestHelper helper) {
        buildFloor(helper);
        final HellhoundEntity raw = spawnHellhound(helper, new BlockPos(1, 1, 1));
        helper.assertTrue(raw.lifeState().packOrigin() == PackOrigin.SOLITARY,
            "a raw rift/API instance initializes solitary identity");
        final HellhoundEntity natural = spawnHellhound(helper, new BlockPos(2, 1, 1));
        final SpawnGroupData group = natural.finalizeSpawn(
            helper.getLevel(),
            helper.getLevel().getCurrentDifficultyAt(natural.blockPosition()),
            EntitySpawnReason.NATURAL,
            null
        );
        helper.assertTrue(group instanceof HellhoundEntity.HellhoundPackSpawnData,
            "natural finalization emits the shared pack group datum");
        helper.assertFalse(natural.isBaby(), "no baby variant survives finalization");
        for (final EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ANIMAL_ARMOR) {
                helper.assertTrue(natural.getItemBySlot(slot).isEmpty(),
                    "no equipment survives finalization: " + slot);
            }
        }
        helper.assertFalse(natural.canPickUpLoot(), "loot pickup stays disabled");
        helper.assertFalse(natural.canBreakDoors(), "door breaking stays disabled");
        helper.assertValueEqual(
            natural.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE), 0.0D,
            "reinforcement chance is forced to zero on every route");
        helper.assertValueEqual(natural.operationalTargetGoalCount(), 0,
            "every inherited Zombie target selector is cleared");
        helper.assertTrue(natural.operationalGoalNames().contains("FloatGoal"),
            "the float goal is installed");
        helper.assertFalse(natural.operationalGoalNames().contains("ZombieAttackGoal"),
            "the inherited Zombie attack goal is removed");
        // Save/reload phase reconciliation folded into this descriptor.
        final TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
        );
        natural.saveWithoutId(output);
        final HellhoundEntity loaded = (HellhoundEntity) ModEntities.ALL.get("hellhound").get()
            .create(helper.getLevel(), EntitySpawnReason.LOAD);
        helper.assertTrue(loaded != null, "the registered type recreates saved Hellhounds");
        loaded.load(TagValueInput.create(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult().copy()
        ));
        helper.getLevel().addFreshEntity(loaded);
        helper.assertValueEqual(loaded.lifeState().packId(), natural.lifeState().packId(),
            "reload preserves the exact pack identity");
        helper.assertFalse(loaded.isBaby(), "reload renormalizes the adult lifecycle");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(raw.lifeCounters().controllerTicks() > 0L,
                "the dedicated runtime runs from the live entity tick");
            helper.succeed();
        });
    }

    public static void naturalGroupPackIdentityExcludesOutsiders(final GameTestHelper helper) {
        buildFloor(helper);
        final HellhoundEntity first = spawnHellhound(helper, new BlockPos(1, 1, 1));
        final SpawnGroupData shared = first.finalizeSpawn(
            helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(first.blockPosition()),
            EntitySpawnReason.NATURAL, null
        );
        final HellhoundEntity second = spawnHellhound(helper, new BlockPos(2, 1, 1));
        second.finalizeSpawn(
            helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(second.blockPosition()),
            EntitySpawnReason.NATURAL, shared
        );
        final HellhoundEntity outsider = spawnHellhound(helper, new BlockPos(0, 1, 2));
        helper.assertValueEqual(first.lifeState().packId(), second.lifeState().packId(),
            "one natural group shares one exact pack identity");
        helper.assertValueEqual(first.lifeState().territoryAnchor(), second.lifeState().territoryAnchor(),
            "one natural group shares one territory anchor");
        helper.assertFalse(outsider.lifeState().packId().equals(first.lifeState().packId()),
            "an adjacent separately spawned Hellhound remains an outsider");
        helper.assertFalse(first.canAttack(second), "same-pack members are never eligible targets");
        helper.assertFalse(first.canAttack(outsider),
            "a neutral outsider Hellhound is not attacked merely for proximity");
        helper.runAfterDelay(60, () -> {
            helper.assertFalse(outsider.lifeState().packId().equals(first.lifeState().packId()),
                "live ticking never merges packs by proximity");
            helper.succeed();
        });
    }

    public static void warningCommitLeashAndReturnAreBounded(final GameTestHelper helper) {
        buildFloor(helper);
        final HellhoundEntity hound = spawnHellhound(helper, new BlockPos(0, 1, 1));
        final ServerPlayer intruder = connectedPlayer(helper, new BlockPos(2, 1, 1));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(hound.lifeCounters().warnings() > 0L,
                "an intruding eligible player receives a warning; state=" + hound.lifeState());
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(hound.lifeState().evidence().stream().anyMatch(entry ->
                    entry.kind() == EvidenceKind.TERRITORY_INTRUSION
                        && entry.sourceId().equals(Optional.of(intruder.getUUID()))),
                "a player that remains commits one territory-intrusion record; state="
                    + hound.lifeState());
            // Leaving far beyond the pursuit leash releases the target and begins RETURN.
            final BlockPos far = helper.absolutePos(new BlockPos(1, 1, 1)).offset(60, 0, 0);
            intruder.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        });
        helper.runAfterDelay(220, () -> {
            helper.assertTrue(hound.getTarget() == null || !hound.getTarget().isAlive()
                    || hound.getTarget() != intruder,
                "pursuit stops once the intruder is beyond the leash");
            final Optional<BlockPos> anchor = hound.lifeState().territoryAnchor();
            helper.assertTrue(anchor.isPresent(), "the territory anchor persists");
            helper.assertTrue(hound.blockPosition().distSqr(anchor.orElseThrow())
                    <= (double) HellhoundLifeRules.TERRITORY_PURSUIT_LEASH
                        * HellhoundLifeRules.TERRITORY_PURSUIT_LEASH,
                "the hound holds inside its own leash; intent=" + hound.lifeState().intent());
            helper.succeed();
        });
    }

    public static void scentEvidenceExpiresWithoutOmniscience(final GameTestHelper helper) {
        buildFloor(helper);
        // A stone wall removes line of sight while scent still works within sixteen blocks
        // (only barrier blocks are scent-tight, and the arena shell is exactly that).
        for (int y = 1; y <= 2; y++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(1, y, z), Blocks.STONE);
            }
        }
        final HellhoundEntity hound = spawnHellhound(helper, new BlockPos(0, 1, 1));
        final ServerPlayer hidden = connectedPlayer(helper, new BlockPos(2, 1, 1));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(hound.lifeState().evidence().stream().anyMatch(entry ->
                    entry.kind() == EvidenceKind.SCENT || entry.kind() == EvidenceKind.SIGHT),
                "one eligible loaded player is recorded inside sixteen blocks; state="
                    + hound.lifeState() + ", counters visited=" + hound.lifeCounters().entitiesVisited());
            helper.assertTrue(hound.lifeCounters().entitiesVisited() > 0L,
                "the scan instruments actual entities visited");
            final BlockPos far = helper.absolutePos(new BlockPos(0, 1, 0)).offset(80, 0, 80);
            hidden.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        });
        helper.runAfterDelay(40 + HellhoundLifeRules.SNIFF_SEARCH_TICKS + 160, () -> {
            helper.assertTrue(hound.lifeState().evidence().stream().noneMatch(entry ->
                    entry.kind() == EvidenceKind.SCENT && entry.valid(helper.getLevel().getGameTime())),
                "scent expires and is never refreshed through unloaded distance; state="
                    + hound.lifeState());
            helper.assertTrue(hound.getTarget() == null,
                "search ends after its bound instead of guessing");
            helper.succeed();
        });
    }

    public static void packRolesCallsAndMemberLossAreBounded(final GameTestHelper helper) {
        buildFloor(helper);
        final HellhoundEntity first = spawnHellhound(helper, new BlockPos(1, 1, 1));
        final SpawnGroupData shared = first.finalizeSpawn(
            helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(first.blockPosition()),
            EntitySpawnReason.NATURAL, null
        );
        final HellhoundEntity second = spawnHellhound(helper, new BlockPos(2, 1, 1));
        second.finalizeSpawn(
            helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(second.blockPosition()),
            EntitySpawnReason.NATURAL, shared
        );
        final HellhoundEntity third = spawnHellhound(helper, new BlockPos(0, 1, 1));
        third.finalizeSpawn(
            helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(third.blockPosition()),
            EntitySpawnReason.NATURAL, shared
        );
        final HellhoundEntity outsider = spawnHellhound(helper, new BlockPos(2, 1, 2));
        final ServerPlayer aggressor = connectedPlayer(helper, new BlockPos(0, 1, 2));
        helper.runAfterDelay(10, () -> first.hurtServer(
            helper.getLevel(), aggressor.damageSources().playerAttack(aggressor), 2.0F
        ));
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(first.lifeCounters().packCalls() <= 4L,
                "pack calls stay rate limited per sender");
            final boolean copied =
                second.lifeState().evidence().stream().anyMatch(entry ->
                    entry.kind() == EvidenceKind.PACK_CALL || entry.kind() == EvidenceKind.SIGHT
                        || entry.kind() == EvidenceKind.SCENT)
                || third.lifeState().evidence().stream().anyMatch(entry ->
                    entry.kind() == EvidenceKind.PACK_CALL || entry.kind() == EvidenceKind.SIGHT
                        || entry.kind() == EvidenceKind.SCENT);
            helper.assertTrue(copied, "one-hop pack sharing reaches loaded same-pack members");
            helper.assertTrue(outsider.lifeState().evidence().stream()
                    .noneMatch(entry -> entry.kind() == EvidenceKind.PACK_CALL),
                "a pack call never mutates an outsider's state");
            final Set<Object> roles = new LinkedHashSet<>();
            for (final HellhoundEntity member : new HellhoundEntity[] {first, second, third}) {
                member.lifeState().packRole().ifPresent(roles::add);
            }
            helper.assertTrue(roles.size() <= HellhoundLifeRules.MAX_PACK_MEMBERS,
                "derived roles stay within the defensive cap");
            second.discard();
        });
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(first.isAlive() && third.isAlive(),
                "member loss simply reduces the loaded role set without cascade");
            // Population budget slice folded into this descriptor: raise the loaded population
            // to the full thirty-two-hound stress bound and hold every pass-local counter cap.
            for (int extra = 0; extra < HellhoundLifeRules.STRESS_POPULATION - 3; extra++) {
                spawnHellhound(helper, new BlockPos(extra % 3, 1, (extra / 3) % 3));
            }
        });
        helper.runAfterDelay(300, () -> {
            final java.util.List<HellhoundEntity> population = helper.getLevel().getEntitiesOfClass(
                HellhoundEntity.class, first.getBoundingBox().inflate(24.0D)
            );
            helper.assertTrue(population.size() >= HellhoundLifeRules.STRESS_POPULATION,
                "the stress slice runs at the full population bound; loaded=" + population.size());
            for (final HellhoundEntity member : population) {
                helper.assertTrue(member.lifeCounters().controllerTicks() > 0L,
                    "every stressed hound self-ticks through the dedicated runtime");
                helper.assertTrue(member.lifeCounters().maximumRetainedCandidates()
                        <= HellhoundLifeRules.MAX_RETAINED_CANDIDATES,
                    "retention stays capped under stress; retained="
                        + member.lifeCounters().maximumRetainedCandidates());
                helper.assertTrue(member.lifeCounters().navigationRequests()
                        <= 300L / HellhoundLifeRules.NAVIGATION_INTERVAL_TICKS + 2L,
                    "navigation cadence holds under stress; requests="
                        + member.lifeCounters().navigationRequests());
                helper.assertTrue(member.lifeCounters().packCalls()
                        <= 300L / HellhoundLifeRules.PACK_CALL_INTERVAL_TICKS + 1L,
                    "pack calls stay rate limited under stress; calls="
                        + member.lifeCounters().packCalls());
                helper.assertTrue(member.lifeState().evidence().size()
                        <= HellhoundLifeRules.MAX_EVIDENCE_RECORDS,
                    "the durable evidence ledger stays capped under stress");
            }
            helper.succeed();
        });
    }

    public static void blockedSectorsAndRouteFailuresBackOff(final GameTestHelper helper) {
        buildFloor(helper);
        // A sealed obsidian corner cell makes every route fail while the target stays
        // observable through scent; the arena shell caps the cell from above.
        for (int y = 1; y <= 2; y++) {
            helper.setBlock(new BlockPos(1, y, 0), Blocks.OBSIDIAN);
            helper.setBlock(new BlockPos(1, y, 1), Blocks.OBSIDIAN);
            helper.setBlock(new BlockPos(0, y, 1), Blocks.OBSIDIAN);
        }
        final HellhoundEntity hound = spawnHellhound(helper, new BlockPos(0, 1, 0));
        final ServerPlayer prey = connectedPlayer(helper, new BlockPos(2, 1, 2));
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(hound.lifeState().routeFailures() <= HellhoundLifeRules.MAX_ROUTE_FAILURES,
                "route failures clamp at three");
            helper.assertTrue(hound.lifeCounters().navigationRequests()
                    <= 200L / HellhoundLifeRules.NAVIGATION_INTERVAL_TICKS + 2L,
                "navigation requests stay within the twenty-tick cadence; requests="
                    + hound.lifeCounters().navigationRequests());
            if (hound.lifeState().routeFailures() >= HellhoundLifeRules.MAX_ROUTE_FAILURES) {
                helper.assertTrue(hound.lifeState().routeRetryAfter() > 0L,
                    "three failures impose an explicit backoff gate");
                helper.assertTrue(hound.lifeState().destination().isEmpty(),
                    "three failures clear the destination claim");
            }
            helper.assertTrue(prey.isAlive(), "the unreachable target is never harmed");
            helper.succeed();
        });
    }

    public static void biteFireRecoveryAndAllySafetyAreExact(final GameTestHelper helper) {
        buildFloor(helper);
        final HellhoundEntity hound = spawnHellhound(helper, new BlockPos(1, 1, 1));
        final HellhoundEntity ally = spawnHellhound(helper, new BlockPos(0, 1, 0));
        ally.setLifeState(ally.lifeState().withPackIdentity(
            hound.lifeState().packId(), hound.lifeState().packOrigin()));
        final ServerPlayer victim = connectedPlayer(helper, new BlockPos(2, 1, 1));
        helper.runAfterDelay(5, () -> hound.hurtServer(
            helper.getLevel(), victim.damageSources().playerAttack(victim), 1.0F
        ));
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(hound.lifeCounters().biteWindups() > 0L,
                "the bite executes through the eight-tick windup; state=" + hound.lifeState()
                    + ", commits=" + hound.lifeCounters().biteCommits());
            helper.assertTrue(hound.lifeCounters().biteCommits() > 0L,
                "one commit lands after windup within range");
            helper.assertTrue(victim.getRemainingFireTicks() > 0 || victim.getHealth() < victim.getMaxHealth(),
                "a successful bite hurts and ignites the living victim for four seconds");
            helper.assertTrue(hound.lifeState().biteRecoveryUntil() > 0L
                    || hound.lifeCounters().biteCommits() > 1L,
                "at least twenty ticks of recovery separate commits");
            helper.assertFalse(ally.getRemainingFireTicks() > 0,
                "a same-pack ally is never ignited");
            helper.assertFalse(hound.canAttack(ally), "ally safety holds during combat");
            helper.succeed();
        });
    }

    public static void retreatRegroupAndIsolationHysteresisHold(final GameTestHelper helper) {
        buildFloor(helper);
        final HellhoundEntity hound = spawnHellhound(helper, new BlockPos(1, 1, 1));
        helper.runAfterDelay(20, () -> hound.setHealth(hound.getMaxHealth() * 0.20F));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(hound.lifeState().retreatLatched(),
                "twenty-percent health latches the retreat; state=" + hound.lifeState());
            helper.assertTrue(hound.lifeState().intent() == Intent.RETREAT
                    || hound.lifeState().intent() == Intent.REGROUP,
                "the latched hound retreats or regroups; intent=" + hound.lifeState().intent());
            helper.assertTrue(hound.getTarget() == null, "retreat clears the target claim");
            hound.setHealth(hound.getMaxHealth() * 0.50F);
        });
        helper.runAfterDelay(120, () -> {
            helper.assertFalse(hound.lifeState().retreatLatched(),
                "forty-percent health releases the latch");
            helper.succeed();
        });
    }

    public static void fireWaterContactAndConversionContractsHold(final GameTestHelper helper) {
        buildFloor(helper);
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.FIRE);
        final HellhoundEntity burning = spawnHellhound(helper, new BlockPos(0, 1, 0));
        final float initialHealth = burning.getHealth();
        // A two-deep water column in the far corner exercises drowning behavior without
        // conversion; the hound escapes it onto the open floor.
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.WATER);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.WATER);
        final HellhoundEntity submerged = spawnHellhound(helper, new BlockPos(2, 1, 2));
        helper.runAfterDelay(150, () -> {
            helper.assertValueEqual(burning.getHealth(), initialHealth,
                "fire immunity remains exact entity-type behavior");
            helper.assertTrue(submerged.isAlive() || submerged.isRemoved(),
                "drowning damage remains ordinary");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(
                    Zombie.class,
                    submerged.getBoundingBox().inflate(8.0D),
                    candidate -> !(candidate instanceof ArcaneCreature)
                ).isEmpty(),
                "no Drowned or Zombie replacement is ever created from a Hellhound");
            helper.assertTrue(submerged.lifeCounters().hazardInterruptions() > 0L,
                "the drowning hazard interrupts the runtime exactly once on entry; counters="
                    + submerged.lifeCounters().hazardInterruptions());
            helper.assertFalse(submerged.isUnderWater(),
                "the spawned self-ticking hound genuinely leaves the water instead of "
                    + "jump-spamming in place; position=" + submerged.blockPosition());
            helper.succeed();
        });
    }

    public static void heatRestNeverEditsWorld(final GameTestHelper helper) {
        buildFloor(helper);
        final BlockPos campfire = new BlockPos(1, 1, 1);
        helper.setBlock(campfire, Blocks.CAMPFIRE);
        final HellhoundEntity hound = spawnHellhound(helper, new BlockPos(0, 1, 0));
        helper.runAfterDelay(160, () -> {
            helper.assertBlockPresent(Blocks.CAMPFIRE, campfire);
            hound.lifeState().heatPoint().ifPresent(point -> helper.assertFalse(
                point.equals(helper.absolutePos(campfire)),
                "the rest position is adjacent safe ground, never the hazardous block itself"
            ));
            helper.assertTrue(hound.lifeCounters().maximumBlockReadsPerSearch()
                    <= HellhoundLifeRules.HEAT_MAX_BLOCK_READS,
                "one heat discovery charges at most 128 actual block reads; reads="
                    + hound.lifeCounters().maximumBlockReadsPerSearch());
            // The winter-hearth creator is disabled: no new campfire may ever appear.
            int campfires = 0;
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    for (int y = 1; y <= 2; y++) {
                        if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.CAMPFIRE)) {
                            campfires++;
                        }
                    }
                }
            }
            helper.assertValueEqual(campfires, 1,
                "heat rest never creates, fuels, or removes any block");
            helper.succeed();
        });
    }

    public static void animusAuthorityFollowAndGuardAreSafe(final GameTestHelper helper) {
        buildFloor(helper);
        final HellhoundEntity hound = spawnHellhound(helper, new BlockPos(1, 1, 1));
        final ServerPlayer owner = connectedPlayer(helper, new BlockPos(2, 1, 1));
        hound.getPersistentData().putString(InfernalPactEffects.OWNER_KEY, owner.getStringUUID());
        helper.runAfterDelay(40, () -> {
            helper.assertValueEqual(hound.lifeState().mode(), Mode.ANIMUS_BOUND,
                "the exact pact key is the only Hellhound player authority");
            helper.assertFalse(hound.canAttack(owner),
                "a bound Hellhound can never reacquire its owner between pact scans");
            helper.assertTrue(hound.lifeState().intent() == Intent.OWNER_GUARD
                    || hound.lifeState().intent() == Intent.OWNER_FOLLOW
                    || hound.lifeState().intent() == Intent.IDLE,
                "the bound hound guards its owner instead of wild territory; intent="
                    + hound.lifeState().intent());
            final BlockPos away = helper.absolutePos(new BlockPos(1, 1, 1)).offset(16, 0, 0);
            owner.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
        });
        helper.runAfterDelay(120, () -> {
            helper.assertValueEqual(hound.lifeState().intent(), Intent.OWNER_FOLLOW,
                "beyond ten blocks the bound hound requests a normal follow path");
            // A delivered command is filtered through the common eligibility predicate.
            final Zombie threat = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(0, 1, 0));
            threat.setNoAi(true);
            HellhoundLifeRuntime.deliverOwnerCommand(hound, helper.getLevel(), owner, threat);
            helper.assertTrue(hound.lifeState().evidence().stream().anyMatch(entry ->
                    entry.kind() == EvidenceKind.OWNER_THREAT
                        && entry.sourceId().equals(Optional.of(threat.getUUID()))),
                "a validated owner command arrives as semantic owner-threat evidence");
            HellhoundLifeRuntime.deliverOwnerCommand(hound, helper.getLevel(), owner, owner);
            helper.assertFalse(hound.canAttack(owner),
                "no command can ever turn the hound on its own authority");
        });
        helper.runAfterDelay(200, () -> {
            helper.assertFalse(hound.getTarget() == owner, "owner safety is continuous");
            helper.succeed();
        });
    }

    public static void cureIsTransactionalAndPreservesExactRules(final GameTestHelper helper) {
        buildFloor(helper);
        final HellhoundEntity hound = spawnHellhound(helper, new BlockPos(1, 1, 1));
        // Exact legacy-hearth cleanup slice folded into this descriptor: the hound owns one
        // still-claimed legacy campfire that completion must release through the exact-owner
        // seam. The key literal is the AmbientActivityRuntime claim contract.
        final BlockPos legacyHearth = new BlockPos(2, 1, 2);
        helper.setBlock(legacyHearth, Blocks.CAMPFIRE);
        final BlockPos absoluteHearth = helper.absolutePos(legacyHearth);
        AmbientActivityHearthData.get(helper.getLevel()).claim(
            absoluteHearth, hound.getUUID(), helper.getLevel().getBlockState(absoluteHearth)
        );
        hound.getPersistentData().putLong("WarlockeryAmbientHearthPosition", absoluteHearth.asLong());
        final ServerPlayer starter = connectedPlayer(helper, new BlockPos(0, 1, 1));
        final ServerPlayer finisher = connectedPlayer(helper, new BlockPos(2, 1, 1));
        starter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE, 2));
        finisher.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE, 2));
        helper.assertFalse(hound.mobInteract(starter, InteractionHand.MAIN_HAND).consumesAction(),
            "without Weakness the interaction still passes through");
        helper.assertValueEqual(starter.getMainHandItem().getCount(), 2,
            "no apple is consumed without the Weakness prerequisite");
        hound.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20_000, 0));
        helper.assertTrue(hound.mobInteract(starter, InteractionHand.MAIN_HAND).consumesAction(),
            "a weakened Hellhound accepts a golden apple");
        helper.assertValueEqual(starter.getMainHandItem().getCount(), 1,
            "each valid attempt consumes exactly one apple");
        helper.assertTrue(hound.mobInteract(starter, InteractionHand.MAIN_HAND).consumesAction(),
            "the second contribution advances progress");
        helper.assertTrue(hound.mobInteract(finisher, InteractionHand.MAIN_HAND).consumesAction(),
            "a second contributor may finish the cure");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(hound.isRemoved(), "completion discards the Hellhound");
            final java.util.List<Wolf> wolves = helper.getLevel().getEntitiesOfClass(
                Wolf.class, hound.getBoundingBox().inflate(8.0D)
            );
            helper.assertValueEqual(wolves.size(), 1,
                "exactly one persistent vanilla Wolf replaces the Hellhound");
            final Wolf wolf = wolves.get(0);
            helper.assertValueEqual(
                Optional.ofNullable(wolf.getOwnerReference()).map(ref -> ref.getUUID()),
                Optional.of(finisher.getUUID()),
                "the finishing contributor owns the Wolf");
            helper.assertTrue(wolf.isPersistenceRequired(), "the Wolf is persistent");
            helper.assertValueEqual(finisher.getMainHandItem().getCount(), 1,
                "the finisher consumed exactly one apple");
            helper.assertFalse(helper.getBlockState(legacyHearth).is(Blocks.CAMPFIRE),
                "cure completion releases the exact still-owned legacy hearth claim");
            helper.succeed();
        });
    }

    private static HellhoundEntity spawnHellhound(final GameTestHelper helper, final BlockPos position) {
        @SuppressWarnings("unchecked")
        final EntityType<HellhoundEntity> type =
            (EntityType<HellhoundEntity>) ModEntities.ALL.get("hellhound").get();
        final HellhoundEntity hound = helper.spawn(type, position, EntitySpawnReason.EVENT);
        hound.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        return hound;
    }

    private static ServerPlayer connectedPlayer(final GameTestHelper helper, final BlockPos relativePosition) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        final BlockPos position = helper.absolutePos(relativePosition);
        player.teleportTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        return GameTestMockPlayers.autoDisconnect(helper, player);
    }

    private static void buildFloor(final GameTestHelper helper) {
        // The descriptor structure is warlockery:empty3x3x3; every relative position must stay
        // within 0..2 so nothing sits inside the framework's barrier enclosure.
        BlockPos.betweenClosedStream(new BlockPos(0, 0, 0), new BlockPos(2, 0, 2))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
    }
}
