package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.LycanPackRules.ActionKind;
import com.kadamitas.warlockery.entity.LycanPackRules.HuntPhase;
import com.kadamitas.warlockery.entity.LycanPackRules.HuntRole;
import com.kadamitas.warlockery.entity.LycanPackRules.Relation;
import com.kadamitas.warlockery.entity.LycanPackRules.Variant;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import com.kadamitas.warlockery.world.CreatureWorldIntegration;
import com.kadamitas.warlockery.world.VillageAssaultData;
import com.kadamitas.warlockery.world.VillageAssaultRules.AssaultKind;
import com.kadamitas.warlockery.world.VillageAssaultRules.SettlementKind;
import com.kadamitas.warlockery.world.VillageAssaultRuntime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public final class LycanPackGameTests {
    private static final long NIGHT = 15_000L;
    private static final long DAY = 6_000L;

    private LycanPackGameTests() {
    }

    public static void lycanVariantsKeepIdentityAndDropZombieLifecycle(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final WerewolfEntity werewolf = spawnLycan(fixture, "werewolf", new BlockPos(1, 1, 1));
            final FeralLycanEntity feral = (FeralLycanEntity) spawnLycan(fixture, "feral_lycan", new BlockPos(1, 1, 2));
            helper.assertValueEqual(werewolf.getClass().getName(), WerewolfEntity.class.getName(),
                "the exact registered werewolf must construct the public WerewolfEntity class");
            helper.assertValueEqual(feral.getClass().getName(), FeralLycanEntity.class.getName(),
                "the exact registered feral_lycan must construct the dedicated FeralLycanEntity class");
            helper.assertValueEqual(werewolf.variant(), Variant.WEREWOLF, "explicit werewolf variant");
            helper.assertValueEqual(feral.variant(), Variant.FERAL_LYCAN, "explicit feral variant");
            helper.assertValueEqual(werewolf.getBbWidth(), 0.85F, "upright werewolf keeps its 0.85 width");
            helper.assertValueEqual(werewolf.getBbHeight(), 2.15F, "upright werewolf keeps its 2.15 height");
            helper.assertValueEqual(feral.getBbWidth(), 0.95F, "feral keeps its reduced 0.95 width");
            helper.assertValueEqual(feral.getBbHeight(), 1.25F, "feral keeps its reduced 1.25 height");
            for (final WerewolfEntity lycan : List.of(werewolf, feral)) {
                helper.assertValueEqual(lycan.creatureKind(), CreatureKind.WEREWOLF,
                    "both variants keep the public family kind");
                helper.assertValueEqual(lycan.getAttributeValue(Attributes.MAX_HEALTH), 42.0D, "health 42");
                helper.assertValueEqual(lycan.getAttributeValue(Attributes.ATTACK_DAMAGE), 9.0D, "attack 9");
                helper.assertValueEqual(lycan.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.32D, "speed 0.32");
                helper.assertValueEqual(lycan.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE), 0.0D,
                    "lycans must never call Zombie reinforcements");
                helper.assertFalse(lycan.isBaby(), "lycans are permanently adult");
                helper.assertFalse(lycan.canPickUpLoot(), "lycans never pick up equipment");
                helper.assertFalse(lycan.convertsInWater(), "water never converts a lycan");
                helper.assertFalse(lycan.isUnderWaterConverting(), "no Drowned conversion timer may run");
                helper.assertTrue(lycan.getPassengers().isEmpty(), "no chicken jockeys");
                for (final EquipmentSlot slot : EquipmentSlot.values()) {
                    helper.assertValueEqual(lycan.getItemBySlot(slot), ItemStack.EMPTY,
                        "spawn finalization strips every random Zombie equipment slot");
                }
            }
            final var sheep = fixture.spawn(EntityTypes.SHEEP, new BlockPos(1, 1, 1), EntitySpawnReason.EVENT);
            sheep.setNoAi(true);
            werewolf.setRemainingFireTicks(200);
            final int fireBefore = sheep.getRemainingFireTicks();
            werewolf.doHurtTarget(helper.getLevel(), sheep);
            helper.assertValueEqual(sheep.getRemainingFireTicks(), fireBefore,
                "a burning bare-handed lycan melee hit must not inherit Zombie target ignition");
            werewolf.clearFire();
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void werewolfHuntAssignsRolesAndReplacesCoordinator(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final long now = helper.getLevel().getGameTime();
            final List<WerewolfEntity> members = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                final WerewolfEntity member = spawnLycan(fixture, "werewolf", new BlockPos(index % 3, 1, index / 3));
                member.setNoAi(true);
                members.add(member);
            }
            final var cow = fixture.spawn(EntityTypes.COW, new BlockPos(2, 1, 2), EntitySpawnReason.EVENT);
            cow.setNoAi(true);
            helper.assertTrue(LycanPackRuntime.formHunt(helper.getLevel(), members, cow, now),
                "seven eligible loaded recruits must form one hunt");
            final List<WerewolfEntity> enrolled = members.stream()
                .filter(member -> member.packState().hunt().episodeId().isPresent())
                .toList();
            helper.assertValueEqual(enrolled.size(), LycanPackRules.MAX_HUNT_MEMBERS,
                "hunt membership must cap at exactly six of the seven candidates");
            final Set<UUID> episodes = new HashSet<>();
            final Set<HuntRole> roles = new HashSet<>();
            final Set<UUID> coordinators = new HashSet<>();
            for (final WerewolfEntity member : enrolled) {
                episodes.add(member.packState().hunt().episodeId().orElseThrow());
                roles.add(member.packState().hunt().role().orElseThrow());
                coordinators.add(member.packState().hunt().coordinatorId().orElseThrow());
                helper.assertValueEqual(member.packState().hunt().phase().orElseThrow(), HuntPhase.RALLY,
                    "a fresh hunt begins in RALLY");
            }
            helper.assertValueEqual(episodes.size(), 1, "one shared episode identity");
            helper.assertValueEqual(roles.size(), LycanPackRules.MAX_HUNT_MEMBERS,
                "all six members receive six distinct roles");
            helper.assertValueEqual(coordinators.size(), 1, "one shared coordinator");
            final UUID coordinatorId = coordinators.iterator().next();
            final WerewolfEntity coordinator = (WerewolfEntity) helper.getLevel().getEntity(coordinatorId);
            helper.assertTrue(coordinator != null, "the elected coordinator must be a loaded member");
            helper.assertValueEqual(coordinator.getAttributeValue(Attributes.ATTACK_DAMAGE), 9.0D,
                "the coordinator job grants no attribute or damage bonus");

            final WerewolfEntity phased = enrolled.stream()
                .filter(member -> !member.getUUID().equals(coordinatorId)).findFirst().orElseThrow();
            final LycanPackState.Hunt expired = phased.packState().hunt();
            phased.setPackState(makePlanDue(phased.packState()).withHunt(new LycanPackState.Hunt(
                expired.episodeId(), expired.coordinatorId(), expired.memberIds(), expired.role(),
                expired.phase(), expired.targetId(), expired.targetPosition(),
                expired.episodeExpiresAt(), now - 1L, expired.targetChanges(), expired.returnIntent()
            )));
            phased.setPackState(LycanPackRuntime.planHunt(
                phased, helper.getLevel(), phased.packState(), NIGHT, true, now
            ));
            helper.assertValueEqual(phased.packState().hunt().phase().orElseThrow(), HuntPhase.TRAIL,
                "an elapsed RALLY deadline must advance exactly one phase to TRAIL");

            coordinator.discard();
            final WerewolfEntity survivor = enrolled.stream()
                .filter(member -> !member.getUUID().equals(coordinatorId) && member != phased)
                .findFirst().orElseThrow();
            survivor.setPackState(makePlanDue(survivor.packState()));
            survivor.setPackState(LycanPackRuntime.planHunt(
                survivor, helper.getLevel(), survivor.packState(), NIGHT, true, now
            ));
            final Optional<UUID> replacement = survivor.packState().hunt().coordinatorId();
            helper.assertTrue(replacement.isPresent(), "coordinator loss elects a replacement at the plan cadence");
            helper.assertFalse(replacement.orElseThrow().equals(coordinatorId),
                "the replacement must not be the removed coordinator");
            helper.assertTrue(helper.getLevel().getEntity(replacement.orElseThrow()) instanceof WerewolfEntity,
                "the replacement coordinator must be a loaded living member");

            cow.discard();
            final WerewolfEntity aborter = enrolled.stream()
                .filter(member -> member != survivor && member != phased && member.isAlive())
                .findFirst().orElseThrow();
            aborter.setPackState(makePlanDue(aborter.packState()));
            aborter.setPackState(LycanPackRuntime.planHunt(
                aborter, helper.getLevel(), aborter.packState(), NIGHT, true, now
            ));
            helper.assertTrue(aborter.packState().hunt().episodeId().isEmpty(),
                "an invalid target must abort the hunt episode");
            helper.assertTrue(aborter.getTarget() == null, "an aborted hunt releases the live target");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void feralLycanTracksPreyWarnsBondsAndAvoidsSettlement(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final long now = helper.getLevel().getGameTime();
            final WerewolfEntity feral = spawnLycan(fixture, "feral_lycan", new BlockPos(1, 1, 1));
            final WerewolfEntity bondedFeral = spawnLycan(fixture, "feral_lycan", new BlockPos(2, 1, 1));
            feral.setNoAi(true);
            bondedFeral.setNoAi(true);
            final Villager villager = fixture.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 2), EntitySpawnReason.EVENT);
            villager.setNoAi(true);
            helper.assertFalse(feral.canAttack(villager),
                "settlement residents are never Feral prey");

            LycanPackState state = feral.packState();
            for (int observation = 0; observation < LycanPackRules.FAMILIARITY_BOND_POINTS; observation++) {
                state = LycanPackRuntime.recordFamiliarityObservation(
                    feral, state, bondedFeral.getUUID(),
                    now + (long) observation * LycanPackRules.FAMILIARITY_OBSERVATION_INTERVAL_TICKS
                );
            }
            feral.setPackState(state);
            helper.assertTrue(feral.packState().cohort().bondedIds().contains(bondedFeral.getUUID()),
                "six spaced proximity observations create one bond");
            helper.assertTrue(feral.packState().cohort().bondedIds().size()
                    <= LycanPackRules.MAX_COHORT_MEMBERS - 1,
                "a cohort holds at most two bonded members besides self");
            helper.assertFalse(LycanPackRules.cohortHasLeader(), "a Feral cohort has no leader");

            final var attacker = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(0, 1, 1), EntitySpawnReason.EVENT);
            attacker.setNoAi(true);
            final long warningBefore = bondedFeral.packState().cohort().warningExpiresAt();
            helper.assertTrue(feral.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 1.0F
            ), "the warning fixture needs one real accepted hit");
            helper.assertTrue(bondedFeral.packState().cohort().warningExpiresAt() > warningBefore,
                "a hurt Feral warns its loaded bonded member one hop away");
            helper.assertTrue(feral.packCounters().alerts() >= 1L, "warning work must be counted");
            helper.assertTrue(feral.packState().needs().fear() >= LycanPackRules.ORDINARY_DAMAGE_FEAR,
                "ordinary direct damage adds Feral fear");
            attacker.discard();
            feral.rememberTransientAttacker(null, 0L);

            final var cow = fixture.spawn(EntityTypes.COW, new BlockPos(1, 1, 2), EntitySpawnReason.EVENT);
            cow.setNoAi(true);
            helper.runAfterDelay(10L, () -> {
                feral.setPackState(makeDue(feral.packState())
                    .withNeeds(LycanPackRules.HUNT_HUNGER, 0, helper.getLevel().getGameTime()));
                LycanPackRuntime.tickForObservation(feral, helper.getLevel(), NIGHT, false);
                helper.assertTrue(feral.getTarget() == cow,
                    "a hungry Feral must stalk the nearest eligible adult prey");
                feral.setTarget(null);
                cow.discard();
            });

            final BlockPos itemPos = helper.absolutePos(new BlockPos(1, 1, 1));
            final ItemEntity carrion = new ItemEntity(helper.getLevel(),
                itemPos.getX() + 0.5D, itemPos.getY(), itemPos.getZ() + 0.5D,
                new ItemStack(Items.ROTTEN_FLESH, 2));
            carrion.setPickUpDelay(0);
            helper.getLevel().addFreshEntity(carrion);
            fixture.track(carrion);
            helper.runAfterDelay((long) LycanPackRules.MIN_CARRION_AGE_TICKS + 10L, () -> {
                try {
                final long later = helper.getLevel().getGameTime();
                feral.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                feral.snapTo(carrion.getX(), carrion.getY(), carrion.getZ(), 0.0F, 0.0F);
                feral.setPackState(makeDue(feral.packState())
                    .withNeeds(LycanPackRules.HUNT_HUNGER, 0, later));
                LycanPackRuntime.tickForObservation(feral, helper.getLevel(), NIGHT, false);
                helper.assertValueEqual(feral.packState().action().kind(), ActionKind.CONSUME_CARRION,
                    "aged unowned carrion within six blocks begins one bounded forage action");
                feral.setPackState(makeDue(feral.packState()));
                LycanPackRuntime.tickForObservation(feral, helper.getLevel(), NIGHT, false);
                helper.assertValueEqual(carrion.getItem().getCount(), 1,
                    "exactly one item is consumed at eating distance");
                helper.assertValueEqual(feral.packCounters().carrionConsumed(), 1L, "one counted consumption");
                helper.assertValueEqual(feral.packState().needs().hunger(),
                    LycanPackRules.HUNT_HUNGER - LycanPackRules.CARRION_REDUCTION,
                    "one carrion item reduces hunger by exactly 250");
                helper.assertTrue(feral.packState().needs().forageCooldownUntil() > later,
                    "consumption starts the four-hundred-tick forage cooldown");
                helper.assertTrue(feral.transientCarrionId() == null,
                    "the transient carrion reservation is cleared after consumption");
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

    public static void lycanSchedulesHazardsAndSilverCountersRemainDistinct(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final WerewolfEntity werewolf = spawnLycan(fixture, "werewolf", new BlockPos(1, 1, 1));
            werewolf.setNoAi(true);
            final var cow = fixture.spawn(EntityTypes.COW, new BlockPos(2, 1, 1), EntitySpawnReason.EVENT);
            cow.setNoAi(true);
            helper.runAfterDelay(10L, () -> {
                try {
            final long now = helper.getLevel().getGameTime();
            werewolf.setPackState(makeDue(werewolf.packState()).withNeeds(LycanPackRules.HUNT_HUNGER, 0, now));
            LycanPackRuntime.tickForObservation(werewolf, helper.getLevel(), DAY, false);
            helper.assertTrue(werewolf.getTarget() == null,
                "ordinary daytime must not begin a Werewolf prey pursuit");
            werewolf.setPackState(makeDue(werewolf.packState()).withNeeds(LycanPackRules.HUNT_HUNGER, 0, now));
            LycanPackRuntime.tickForObservation(werewolf, helper.getLevel(), NIGHT, false);
            helper.assertTrue(werewolf.getTarget() == cow,
                "night hours admit the ordinary hungry pursuit");
            werewolf.setTarget(null);

            final WerewolfEntity feral = spawnLycan(fixture, "feral_lycan", new BlockPos(0, 1, 1));
            feral.setNoAi(true);
            feral.setPackState(makeDue(feral.packState()).withNeeds(LycanPackRules.HUNT_HUNGER, 0, now));
            LycanPackRuntime.tickForObservation(feral, helper.getLevel(), DAY, true);
            helper.assertTrue(feral.getTarget() == cow,
                "the Feral doctrine has no moon or night gate for hunger stalking");
            feral.setTarget(null);

            werewolf.setPackState(makeDue(werewolf.packState())
                .beginAction(ActionKind.HARRY, now, now, now + LycanPackRules.HARRY_TICKS));
            werewolf.setRemainingFireTicks(100);
            LycanPackRuntime.tickForObservation(werewolf, helper.getLevel(), NIGHT, false);
            helper.assertTrue(werewolf.packCounters().hazardInterruptions() >= 1L,
                "burning must interrupt autonomy before any decision work");
            helper.assertValueEqual(werewolf.packState().action().kind(), ActionKind.NONE,
                "a hazard cancels the current semantic action");
            werewolf.clearFire();

            final var mundane = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(0, 1, 2), EntitySpawnReason.EVENT);
            mundane.setNoAi(true);
            final Pillager guard = fixture.spawn(EntityTypes.PILLAGER, new BlockPos(2, 1, 2), EntitySpawnReason.EVENT);
            guard.setNoAi(true);
            VillageAssaultRuntime.markSettlementGuard(guard, SettlementKind.HUMAN);
            werewolf.setPackState(werewolf.packState().withNeeds(werewolf.packState().needs().hunger(), 0, now));
            werewolf.invulnerableTime = 0;
            helper.assertTrue(werewolf.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(mundane), 1.0F
            ), "the ordinary fear fixture needs a real hit");
            helper.assertValueEqual(werewolf.packState().needs().fear(), LycanPackRules.ORDINARY_DAMAGE_FEAR,
                "ordinary damage adds exactly 120 fear");
            werewolf.invulnerableTime = 0;
            helper.assertTrue(werewolf.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(guard), 1.0F
            ), "the silver-guard fear fixture needs a real hit");
            helper.assertValueEqual(werewolf.packState().needs().fear(),
                LycanPackRules.ORDINARY_DAMAGE_FEAR + LycanPackRules.SILVER_OR_GUARD_FEAR,
                "settlement-guard damage adds the distinct 300 fear");

            final ServerPlayer alchemist = fixture.connectedPlayer(new BlockPos(0, 1, 0));
            final var armor = werewolf.getAttribute(Attributes.ARMOR);
            if (armor != null) armor.setBaseValue(0.0D);
            werewolf.setHealth(werewolf.getMaxHealth());
            werewolf.invulnerableTime = 0;
            helper.assertTrue(werewolf.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().magic(), 4.0F
            ), "the generic magic fixture needs a real hit");
            final float genericDelta = werewolf.getMaxHealth() - werewolf.getHealth();
            helper.assertTrue(Math.abs(genericDelta - 0.6F) < 0.01F,
                "ordinary magic keeps the generic supernatural reduction to fifteen percent; was " + genericDelta);
            werewolf.setHealth(werewolf.getMaxHealth());
            werewolf.invulnerableTime = 0;
            helper.assertTrue(werewolf.hurtServer(
                helper.getLevel(),
                LycanDamageTypes.harmWerewolvesSource(helper.getLevel(), alchemist, alchemist), 4.0F
            ), "the typed brew fixture needs a real hit");
            final float typedDelta = werewolf.getMaxHealth() - werewolf.getHealth();
            helper.assertTrue(Math.abs(typedDelta - 4.0F) < 0.01F,
                "the typed anti-werewolf source bypasses only the generic reduction and is never doubled; was " + typedDelta);
            final ItemStack silverSword = new ItemStack(BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath("warlockery", "silversword")
            ));
            alchemist.setItemInHand(InteractionHand.MAIN_HAND, silverSword);
            werewolf.setHealth(werewolf.getMaxHealth());
            werewolf.invulnerableTime = 0;
            helper.assertTrue(werewolf.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().playerAttack(alchemist), 4.0F
            ), "the silver fixture needs a real hit");
            final float silverDelta = werewolf.getMaxHealth() - werewolf.getHealth();
            helper.assertTrue(Math.abs(silverDelta - 8.0F) < 0.01F,
                "silver keeps its own distinct bypass-and-double counter; was " + silverDelta);
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

    public static void lycanFamilyTargetsRespectKinPlayersAndOtherFamilies(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final long now = helper.getLevel().getGameTime();
            final WerewolfEntity werewolf = spawnLycan(fixture, "werewolf", new BlockPos(1, 1, 1));
            werewolf.setNoAi(true);
            final Villager villager = fixture.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 1), EntitySpawnReason.EVENT);
            final var wolf = fixture.spawn(EntityTypes.WOLF, new BlockPos(2, 1, 2), EntitySpawnReason.EVENT);
            final var cow = fixture.spawn(EntityTypes.COW, new BlockPos(0, 1, 2), EntitySpawnReason.EVENT);
            final var calf = fixture.spawn(EntityTypes.COW, new BlockPos(0, 1, 0), EntitySpawnReason.EVENT);
            calf.setBaby(true);
            final var namedSheep = fixture.spawn(EntityTypes.SHEEP, new BlockPos(2, 1, 0), EntitySpawnReason.EVENT);
            namedSheep.setCustomName(net.minecraft.network.chat.Component.literal("0451"));
            final WerewolfEntity otherFeral = spawnLycan(fixture, "feral_lycan", new BlockPos(0, 1, 1));
            final VampireCourtEntity vampire = (VampireCourtEntity) fixture.spawn(
                ModEntities.ALL.get("vampire").get(), new BlockPos(1, 1, 0), EntitySpawnReason.EVENT
            );
            helper.assertFalse(werewolf.canAttack(villager), "ordinary villagers are non-prey");
            helper.assertFalse(werewolf.canAttack(wolf), "wolves are never prey");
            helper.assertFalse(werewolf.canAttack(calf), "baby animals are never prey");
            helper.assertFalse(werewolf.canAttack(namedSheep), "custom-named animals are never prey");
            helper.assertFalse(werewolf.canAttack(otherFeral), "the other F04 variant is non-prey kin");
            helper.assertFalse(werewolf.canAttack(vampire), "other supernatural families are non-prey");
            helper.assertTrue(werewolf.canAttack(cow), "an ordinary loaded adult cow is eligible prey");

            final ServerPlayer bystander = fixture.connectedPlayer(new BlockPos(3, 1, 1));
            helper.assertFalse(werewolf.canAttack(bystander), "ordinary players are non-prey");
            SupernaturalState.setForm(bystander, SupernaturalForm.WEREWOLF);
            helper.assertFalse(werewolf.canAttack(bystander), "transformed werewolf players are dynamic kin");
            SupernaturalState.setForm(bystander, SupernaturalForm.NONE);

            final ServerPlayer aggressor = fixture.connectedPlayer(new BlockPos(3, 1, 2));
            werewolf.invulnerableTime = 0;
            helper.assertTrue(werewolf.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().playerAttack(aggressor), 1.0F
            ), "the melee attribution fixture needs a real hit");
            helper.assertValueEqual(werewolf.packState().relationships().size(), 1,
                "accepted player melee writes one ledger entry");
            helper.assertValueEqual(werewolf.packState().relationships().get(0).relation(), Relation.THREAT,
                "the first accepted hit is a THREAT");
            helper.assertTrue(werewolf.canAttack(aggressor),
                "a live attributed THREAT admits the player as an execution target");

            final AbstractArrow arrow = (AbstractArrow) fixture.spawn(
                EntityTypes.ARROW, new BlockPos(3, 1, 0), EntitySpawnReason.EVENT
            );
            arrow.setOwner(aggressor);
            werewolf.invulnerableTime = 0;
            helper.assertTrue(werewolf.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, null), 1.0F
            ), "the projectile attribution fixture needs a real hit");
            helper.assertValueEqual(werewolf.packState().relationships().get(0).relation(), Relation.GRIEVANCE,
                "a second accepted attributed form promotes the same player to GRIEVANCE");
            werewolf.invulnerableTime = 0;
            helper.assertTrue(werewolf.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().indirectMagic(aggressor, aggressor), 1.0F
            ), "the indirect magic attribution fixture needs a real hit");
            werewolf.invulnerableTime = 0;
            helper.assertTrue(werewolf.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().explosion(null, aggressor), 1.0F
            ), "the explosion attribution fixture needs a real hit");
            helper.assertValueEqual(werewolf.packState().relationships().size(), 1,
                "all four attribution forms resolve to one player entry");
            helper.assertValueEqual(werewolf.packCounters().relationshipWrites(), 4L,
                "melee, projectile, indirect magic, and explosion each write the ledger once");

            werewolf.invulnerableTime = 0;
            helper.assertTrue(werewolf.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().inFire(), 1.0F
            ), "the environmental negative fixture needs a real hit");
            helper.assertValueEqual(werewolf.packState().relationships().size(), 1,
                "environmental damage writes no relationship");
            final var mob = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3), EntitySpawnReason.EVENT);
            mob.setNoAi(true);
            werewolf.invulnerableTime = 0;
            helper.assertTrue(werewolf.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(mob), 1.0F
            ), "the non-player negative fixture needs a real hit");
            helper.assertValueEqual(werewolf.packState().relationships().size(), 1,
                "a non-player attacker stays a transient fact only");
            helper.assertTrue(werewolf.transientAttackerId(now + 1L) != null,
                "the non-player attacker is remembered transiently for self-defense");

            for (int extra = 0; extra < 4; extra++) {
                final ServerPlayer another = fixture.connectedPlayer(new BlockPos(4, 1, extra));
                werewolf.invulnerableTime = 0;
                werewolf.hurtServer(
                    helper.getLevel(), helper.getLevel().damageSources().playerAttack(another), 1.0F
                );
            }
            helper.assertValueEqual(werewolf.packState().relationships().size(),
                LycanPackRules.MAX_RELATIONSHIP_ENTRIES,
                "the relationship ledger holds at most four bounded entries");

            aggressor.setGameMode(GameType.CREATIVE);
            helper.assertFalse(werewolf.canAttack(aggressor),
                "creative players are never execution targets even with a live grievance");
            aggressor.setGameMode(GameType.SURVIVAL);
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void werewolfTrapHuntAssaultAndInfectionContractsRemainExact(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final long now = helper.getLevel().getGameTime();
            final WerewolfEntity werewolf = spawnLycan(fixture, "werewolf", new BlockPos(1, 1, 1));
            werewolf.setNoAi(true);
            werewolf.getPersistentData().putLong("WarlockeryWolfTrap", werewolf.blockPosition().asLong());
            werewolf.setPackState(makeDue(werewolf.packState()));
            LycanPackRuntime.tickForObservation(werewolf, helper.getLevel(), NIGHT, false);
            helper.assertValueEqual(werewolf.packCounters().decisions(), 0L,
                "a Wolf Trap marker suppresses every autonomous decision");
            werewolf.getPersistentData().remove("WarlockeryWolfTrap");

            final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
            final VillageAssaultData data = VillageAssaultData.get(helper.getLevel());
            helper.assertTrue(data.begin(center, AssaultKind.WEREWOLF, SettlementKind.HUMAN, now),
                "the isolated fixture must begin one Werewolf assault");
            fixture.onClose(() -> data.active().filter(state -> state.center().equals(center)).ifPresent(
                ignored -> data.finish(helper.getLevel().getGameTime(), 0L, 1.0D)
            ));
            VillageAssaultRuntime.markRaider(
                werewolf, center, 1, AssaultKind.WEREWOLF, SettlementKind.HUMAN, false, false
            );
            werewolf.setPackState(makeDue(werewolf.packState()));
            LycanPackRuntime.tickForObservation(werewolf, helper.getLevel(), NIGHT, false);
            helper.assertValueEqual(werewolf.packCounters().decisions(), 0L,
                "an assault raider defers autonomy to the event owner");

            final Villager victim = fixture.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 2), EntitySpawnReason.EVENT);
            victim.setNoAi(true);
            helper.assertTrue(LycanPackRuntime.isAssaultDesignatedVictim(werewolf, victim),
                "the exact marked Werewolf recognizes the active assault designation");
            LycanPackRuntime.coordinateAssaultPressure(helper.getLevel(), werewolf, victim, center);
            helper.assertTrue(werewolf.getTarget() == victim,
                "the Lycan delegate sets the admitted resident target in the same pass");
            final long navigationAfterFirst = werewolf.packCounters().navigationRequests();
            helper.assertTrue(navigationAfterFirst >= 1L,
                "the delegate issues the one bounded pressure route");
            LycanPackRuntime.coordinateAssaultPressure(helper.getLevel(), werewolf, victim, center);
            helper.assertValueEqual(werewolf.packCounters().navigationRequests(), navigationAfterFirst,
                "repeat pressure passes inside twenty ticks issue no extra navigation");
            LycanPackRuntime.coordinateAssaultPressure(helper.getLevel(), werewolf, null, center);
            helper.assertTrue(werewolf.getTarget() == null,
                "losing the designated resident clears the delegated target in the same pass");

            final WerewolfEntity feral = spawnLycan(fixture, "feral_lycan", new BlockPos(0, 1, 0));
            feral.setNoAi(true);
            VillageAssaultRuntime.markRaider(
                feral, center, 1, AssaultKind.WEREWOLF, SettlementKind.HUMAN, false, false
            );
            helper.assertFalse(LycanPackRuntime.exactWerewolf(feral),
                "the Feral variant is excluded from exact-Werewolf membership by explicit variant");
            helper.assertFalse(LycanPackRuntime.isAssaultDesignatedVictim(feral, victim),
                "a marked Feral never receives the Werewolf assault designation");

            werewolf.setPackState(werewolf.packState().withNeeds(LycanPackRules.HUNT_HUNGER, 0, now));
            final var assaultCow = fixture.spawn(EntityTypes.COW, new BlockPos(0, 1, 2), EntitySpawnReason.EVENT);
            LycanPackRuntime.afterKill(werewolf, helper.getLevel(), assaultCow);
            helper.assertValueEqual(werewolf.packState().needs().hunger(), LycanPackRules.HUNT_HUNGER,
                "an assault raider kill never feeds ordinary hunger");
            helper.assertTrue(werewolf.killedEntity(helper.getLevel(), victim,
                    helper.getLevel().damageSources().mobAttack(werewolf)),
                "a Werewolf villager kill must skip the inherited Zombie Villager conversion");
            werewolf.getPersistentData().remove(VillageAssaultRuntime.RAIDER_MARKER);
            LycanPackRuntime.afterKill(werewolf, helper.getLevel(), assaultCow);
            helper.assertValueEqual(werewolf.packState().needs().hunger(),
                LycanPackRules.HUNT_HUNGER - LycanPackRules.WEREWOLF_KILL_REDUCTION,
                "an ordinary qualifying kill reduces hunger by exactly 450");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void lycanActionsCancelAcrossFailureSaveAndReload(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final long now = helper.getLevel().getGameTime();
            final WerewolfEntity feral = spawnLycan(fixture, "feral_lycan", new BlockPos(1, 1, 1));
            feral.setNoAi(true);

            LycanPackState failing = feral.packState()
                .recordRouteResult(false, now)
                .recordRouteResult(false, now + 20L)
                .recordRouteResult(false, now + 40L);
            helper.assertValueEqual(failing.cadence().routeFailures(), LycanPackRules.MAX_ROUTE_FAILURES,
                "route failures saturate at three");
            helper.assertValueEqual(failing.cadence().retryAfter(),
                now + 40L + LycanPackRules.ROUTE_BACKOFF_TICKS,
                "the third failed route imposes the hundred-tick backoff");
            helper.assertTrue(LycanPackRules.forcedRetreat(0, 1.0F, false, failing.cadence().routeFailures()),
                "exhausted routes force retreat");

            feral.setPackState(makeDue(feral.packState())
                .beginAction(ActionKind.POUNCE, now - 20L, now - 16L, now + LycanPackRules.POUNCE_COOLDOWN_TICKS));
            final net.minecraft.world.phys.Vec3 motionBefore = feral.getDeltaMovement();
            LycanPackRuntime.tickForObservation(feral, helper.getLevel(), NIGHT, false);
            helper.assertTrue(feral.getTarget() == null, "an invalid pounce never acquires a target");
            helper.assertValueEqual(feral.packState().action().kind(), ActionKind.NONE,
                "a pounce whose preconditions fail at execution cancels the semantic action");
            helper.assertTrue(feral.packCounters().cancellations() >= 1L, "the cancellation is counted");
            helper.assertValueEqual(feral.getDeltaMovement().x, motionBefore.x,
                "a cancelled pounce applies no launch impulse");
            helper.assertValueEqual(feral.getDeltaMovement().z, motionBefore.z,
                "a cancelled pounce applies no sideways launch impulse");

            final long past = now - 100_000L;
            final LycanPackState stale = feral.packState().withNeeds(300, 500, past);
            final LycanPackState reconciled = stale.reconcile(now);
            helper.assertValueEqual(reconciled.needs().hunger(),
                300 + (int) (LycanPackRules.MAX_ELAPSED_RECONCILE_TICKS / LycanPackRules.HUNGER_RISE_INTERVAL_TICKS),
                "elapsed hunger reconciliation is one bounded pass capped at 24000 ticks");
            helper.assertValueEqual(reconciled.needs().fear(), 0,
                "elapsed fear decays without replaying any event");

            final ItemEntity reserved = new ItemEntity(helper.getLevel(),
                feral.getX(), feral.getY(), feral.getZ(), new ItemStack(Items.ROTTEN_FLESH));
            helper.getLevel().addFreshEntity(reserved);
            fixture.track(reserved);
            feral.setTransientCarrionId(reserved.getUUID());
            feral.rememberTransientAttacker(UUID.randomUUID(), now + 200L);
            feral.setPackState(feral.packState()
                .withRefuge(feral.blockPosition(), now + 1_000L, now + 1_000L)
                .beginAction(ActionKind.POUNCE, now + 10L, now + 14L, now + 90L));
            final TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
            );
            feral.saveWithoutId(output);
            final WerewolfEntity loaded = (WerewolfEntity) ModEntities.ALL.get("feral_lycan").get()
                .create(helper.getLevel(), EntitySpawnReason.LOAD);
            helper.assertTrue(loaded != null, "the registered Feral type must recreate saved state");
            fixture.track(loaded);
            loaded.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult().copy()
            ));
            helper.assertValueEqual(loaded.variant(), Variant.FERAL_LYCAN,
                "reload preserves the exact variant identity");
            helper.assertTrue(loaded.transientCarrionId() == null,
                "the transient carrion reservation is never persisted or resumed");
            helper.assertTrue(loaded.transientAttackerId(now) == null,
                "the transient attacker fact is never persisted");
            helper.assertValueEqual(loaded.packState().needs().hunger(), feral.packState().needs().hunger(),
                "semantic hunger survives the save round trip");
            helper.assertValueEqual(loaded.packCounters().carrionConsumed(), 0L,
                "reload replays no feeding, damage, or movement");
            helper.assertFalse(loaded.isBaby(), "load renormalizes the permanent adult lifecycle");

            final LycanPackState crossed = feral.packState().afterDimensionChange(now);
            helper.assertTrue(crossed.refuge().position().isEmpty(),
                "dimension change clears the refuge immediately");
            helper.assertTrue(crossed.hunt().episodeId().isEmpty(),
                "dimension change clears episode membership immediately");
            helper.assertValueEqual(crossed.action().kind(), ActionKind.NONE,
                "dimension change clears the current action immediately");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void lycanPopulationWorkStaysWithinDeclaredCaps(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final long now = helper.getLevel().getGameTime();
            final ServerPlayer anchor = fixture.connectedPlayer(new BlockPos(1, 1, 1));
            final List<WerewolfEntity> lycans = new ArrayList<>();
            for (int index = 0; index < 64; index++) {
                final WerewolfEntity lycan = spawnLycan(
                    fixture, index % 2 == 0 ? "werewolf" : "feral_lycan",
                    new BlockPos(index % 3, 1, index / 24)
                );
                lycan.setNoAi(true);
                lycans.add(lycan);
            }
            for (int index = 0; index < 8; index++) {
                fixture.spawn(EntityTypes.COW, new BlockPos(index % 3, 1, 2), EntitySpawnReason.EVENT)
                    .setNoAi(true);
            }
            final List<Pillager> pillagers = new ArrayList<>();
            for (int index = 0; index < 64; index++) {
                final Pillager pillager = fixture.spawn(
                    EntityTypes.PILLAGER, new BlockPos(index % 3, 1, 2), EntitySpawnReason.EVENT
                );
                pillager.setNoAi(true);
                pillagers.add(pillager);
            }
            helper.runAfterDelay(5L, () -> {
                try {
            final long settled = helper.getLevel().getGameTime();
            long navigationTotal = 0L;
            for (final WerewolfEntity lycan : lycans) {
                lycan.setPackState(makeDue(lycan.packState())
                    .withNeeds(LycanPackRules.HUNT_HUNGER, 0, settled));
                LycanPackRuntime.tickForObservation(lycan, helper.getLevel(), NIGHT, false);
                helper.assertValueEqual(lycan.packCounters().candidateAppraisals(),
                    (long) LycanPackRules.MAX_SCAN_RESULTS,
                    "each dense perception scan aborts at exactly thirty-two raw visits; decisions="
                        + lycan.packCounters().decisions() + ", hazard=" + lycan.packCounters().hazardInterruptions()
                        + ", target=" + lycan.getTarget() + ", action=" + lycan.packState().action().kind()
                        + ", hunt=" + lycan.packState().hunt().episodeId() + ", fear=" + lycan.packState().needs().fear()
                        + ", hunger=" + lycan.packState().needs().hunger()
                        + ", perceptionScans=" + lycan.packCounters().perceptionScans()
                        + ", nextPerception=" + lycan.packState().cadence().nextPerceptionAt()
                        + ", alive=" + lycan.isAlive() + ", fire=" + lycan.isOnFire()
                        + ", variant=" + lycan.variant());
                helper.assertTrue(lycan.packCounters().lineOfSightChecks() <= LycanPackRules.MAX_LINE_OF_SIGHT_CHECKS,
                    "each perception scan spends at most eight line-of-sight checks");
                helper.assertTrue(lycan.packCounters().recruitmentInspections()
                        <= LycanPackRules.MAX_RECRUITMENT_CANDIDATES,
                    "each plan pass inspects at most sixteen recruitment candidates");
                helper.assertTrue(lycan.packCounters().navigationRequests() <= 1L,
                    "one semantic tick issues at most one navigation request");
                navigationTotal += lycan.packCounters().navigationRequests();
                lycan.setTarget(null);
            }
            helper.assertTrue(navigationTotal <= 64L,
                "population navigation stays at one request per due member");

            final CreatureWorldIntegration.ArmingReport report =
                CreatureWorldIntegration.armNearbyPillagers(helper.getLevel(), anchor);
            helper.assertValueEqual(report.rawLycanVisits(), CreatureWorldIntegration.MAX_RAW_ARMING_VISITS,
                "the arming pass aborts lycan traversal at thirty-two raw visits");
            helper.assertValueEqual(report.retainedLycans(), CreatureWorldIntegration.MAX_RETAINED_ARMING,
                "the arming pass retains sixteen lycans by anchor distance then UUID");
            helper.assertValueEqual(report.rawPillagerVisits(), CreatureWorldIntegration.MAX_RAW_ARMING_VISITS,
                "the arming pass aborts Pillager traversal at thirty-two raw visits");
            helper.assertValueEqual(report.retainedPillagers(), CreatureWorldIntegration.MAX_RETAINED_ARMING,
                "the arming pass retains sixteen Pillagers by anchor distance then UUID");
            helper.assertValueEqual(report.armedPillagers(), CreatureWorldIntegration.MAX_RETAINED_ARMING,
                "at most sixteen Pillagers are armed in one pass");
            long armed = 0L;
            for (final Pillager pillager : pillagers) {
                if (pillager.getTarget() == null) continue;
                armed++;
                helper.assertTrue(pillager.getTarget() instanceof WerewolfEntity,
                    "every armed Pillager targets a retained lycan");
                helper.assertTrue(pillager.getMainHandItem().is(Items.CROSSBOW),
                    "every armed Pillager receives the existing crossbow");
                helper.assertValueEqual(pillager.getOffhandItem().getCount(), 64,
                    "every armed Pillager receives exactly sixty-four silver bolts");
            }
            helper.assertTrue(armed <= 64L, "the bounded sweep only observes fixture Pillagers");
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

    private static long farFuture(final LycanPackState state) {
        return LycanPackRules.saturatingAdd(
            state.needs().lastNeedUpdateAt(), LycanPackRules.MAX_ELAPSED_RECONCILE_TICKS - 4_000L
        );
    }

    private static LycanPackState makeDue(final LycanPackState state) {
        final long far = farFuture(state);
        return state.withCadence(new LycanPackState.Cadence(
            0L, 0L, far, far, -1_000_000L, far, 0, 0L
        ));
    }

    private static LycanPackState makePlanDue(final LycanPackState state) {
        final long far = farFuture(state);
        return state.withCadence(new LycanPackState.Cadence(
            0L, 0L, 0L, far, -1_000_000L, far, 0, 0L
        ));
    }

    private static WerewolfEntity spawnLycan(
        final FixtureScope fixture,
        final String id,
        final BlockPos position
    ) {
        final WerewolfEntity lycan = (WerewolfEntity) fixture.spawn(
            ModEntities.ALL.get(id).get(), position, EntitySpawnReason.EVENT
        );
        fixture.helper.assertValueEqual(lycan.creatureKind(), CreatureKind.WEREWOLF,
            "the registered lycan kind must remain exact");
        return lycan;
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

        private ServerPlayer connectedPlayer(final BlockPos position) {
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
