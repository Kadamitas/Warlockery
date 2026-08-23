package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.data.WarlockeryEntityData;

import com.kadamitas.warlockery.entity.ArcaneMob;
import com.kadamitas.warlockery.entity.CreatureCombat;
import com.kadamitas.warlockery.entity.WerewolfEntity;
import com.kadamitas.warlockery.fabric.event.LivingDamageContext;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import com.kadamitas.warlockery.world.VillageAssaultData.AssaultState;
import com.kadamitas.warlockery.world.VillageAssaultRules.AssaultKind;
import com.kadamitas.warlockery.world.VillageAssaultRules.SettlementKind;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class VillageAssaultGameTests {
    private VillageAssaultGameTests() {
    }

    public static void infectedVillagerTransformsAndRestoresWithIdentity(final GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        final Villager original = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
        final Component originalName = Component.translatable("entity.minecraft.villager");
        original.setCustomName(originalName);
        original.setCustomNameVisible(true);
        original.setVillagerData(original.getVillagerData()
            .withProfession(helper.getLevel().registryAccess(), VillagerProfession.CARTOGRAPHER)
            .withLevel(4));
        final var originalVillagerData = original.getVillagerData();
        original.setVillagerXp(37);
        final UUID originalId = original.getUUID();
        final long bloodLockExpiry = helper.getLevel().getGameTime() + 90_000L;
        WarlockeryEntityData.get(original).putLong(VillageAssaultRuntime.BLOOD_DRAINED_UNTIL, bloodLockExpiry);

        helper.assertTrue(WerewolfVillagerInfectionRuntime.markInfected(original),
            "a vanilla villager must accept its first werewolf infection");
        helper.assertTrue(!WerewolfVillagerInfectionRuntime.markInfected(original),
            "repeated bites must not duplicate infection state");
        helper.assertTrue(VillageAssaultRules.shouldTransformInfected(true, true),
            "the conversion fixture must represent a full-moon night");
        final WerewolfEntity transformed = WerewolfVillagerInfectionRuntime
            .convertInfectedVillager(helper.getLevel(), original)
            .orElseThrow();
        helper.assertTrue(original.isRemoved(), "the villager body must be replaced while transformed");
        helper.assertTrue(WerewolfVillagerInfectionRuntime.isTransformedVillager(transformed),
            "the werewolf must retain its reversible villager marker");
        helper.assertTrue(transformed.transformedVillagerData()
            .filter(data -> data.type().equals(originalVillagerData.type())
                && data.profession().is(VillagerProfession.CARTOGRAPHER)
                && data.level() == 4)
            .isPresent(),
            "the transformed werewolf must synchronize the cartographer's visible clothing data");
        helper.assertValueEqual(transformed.getCustomName(), originalName,
            "transformed villager name");

        helper.assertTrue(VillageAssaultRules.shouldRestoreVillager(true),
            "the restoration fixture must represent daylight");
        final Villager restored = WerewolfVillagerInfectionRuntime.restoreVillager(helper.getLevel(), transformed)
            .orElseThrow();
        helper.assertTrue(transformed.isRemoved(), "the temporary werewolf body must be removed at daylight");
        helper.assertTrue(WerewolfVillagerInfectionRuntime.isInfected(restored),
            "restored villagers must remain moon-marked for later full moons");
        helper.assertValueEqual(restored.getCustomName(), originalName,
            "restored villager name");
        helper.assertValueEqual(restored.getVillagerData().level(), 4, "restored villager profession level");
        helper.assertTrue(restored.getVillagerData().profession().is(VillagerProfession.CARTOGRAPHER),
            "restored villager profession must survive the transformation");
        helper.assertValueEqual(restored.getUUID(), originalId, "restored villager UUID");
        helper.assertValueEqual(restored.getVillagerXp(), 37, "restored villager experience");
        helper.assertValueEqual(VillageAssaultRuntime.bloodDrainedUntil(restored), bloodLockExpiry,
            "restored blood-drained trade lock expiry");

        final WerewolfEntity transformedAgain = WerewolfVillagerInfectionRuntime
            .convertInfectedVillager(helper.getLevel(), restored)
            .orElseThrow();
        helper.assertTrue(restored.isRemoved() && WerewolfVillagerInfectionRuntime.isTransformedVillager(
            transformedAgain
        ), "the same moon-marked villager must transform again on a later full moon");
        final Villager restoredAgain = WerewolfVillagerInfectionRuntime
            .restoreVillager(helper.getLevel(), transformedAgain)
            .orElseThrow();
        helper.assertTrue(transformedAgain.isRemoved() && WerewolfVillagerInfectionRuntime.isInfected(restoredAgain),
            "the second daylight restoration must remain eligible for future moon cycles");
        helper.assertValueEqual(restoredAgain.getUUID(), originalId, "second-cycle villager UUID");
        helper.assertValueEqual(restoredAgain.getCustomName(), originalName,
            "second-cycle villager name");
        helper.assertTrue(restoredAgain.getVillagerData().profession().is(VillagerProfession.CARTOGRAPHER)
            && restoredAgain.getVillagerData().level() == 4,
            "second-cycle villager profession and level must persist");
        helper.assertValueEqual(restoredAgain.getVillagerXp(), 37, "second-cycle villager experience");
        helper.assertValueEqual(VillageAssaultRuntime.bloodDrainedUntil(restoredAgain), bloodLockExpiry,
            "second-cycle blood-drained trade lock expiry");
        helper.succeed();
    }

    public static void bothSettlementsReceiveTaggedSilverGuards(final GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        final IronGolem humanGuard = helper.spawn(
            EntityTypes.IRON_GOLEM, new BlockPos(0, 1, 0), EntitySpawnReason.STRUCTURE
        );
        final IronGolem hobgoblinGuard = helper.spawn(
            EntityTypes.IRON_GOLEM, new BlockPos(2, 1, 2), EntitySpawnReason.STRUCTURE
        );
        humanGuard.addTag(SettlementFortificationRuntime.GUARD_TAG);
        humanGuard.addTag(SettlementFortificationRuntime.HUMAN_GUARD_TAG);
        hobgoblinGuard.addTag(SettlementFortificationRuntime.GUARD_TAG);
        hobgoblinGuard.addTag(SettlementFortificationRuntime.HOBGOBLIN_GUARD_TAG);
        helper.assertTrue(VillageGuardRuntime.isHumanSettlementGuard(humanGuard),
            "human defenders must retain their settlement guard role");
        helper.assertTrue(VillageGuardRuntime.isHobgoblinSettlementGuard(hobgoblinGuard),
            "hobgoblin defenders must retain their settlement guard role");
        helper.assertTrue(VillageGuardRuntime.isSettlementGuard(humanGuard)
            && VillageGuardRuntime.isSettlementGuard(hobgoblinGuard),
            "both defender variants must share the settlement guard role");
        helper.assertTrue(VillageGuardRules.isSilverClassifiedAttack(true),
            "all settlement guard attacks must be classified as silver");
        helper.succeed();
    }

    public static void guardsRetaliateAgainstPlayersWithSilverBolts(final GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 0, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 0, 1), Blocks.STONE);
        final ServerPlayer attacker = connectedSurvivalPlayer(helper);
        final Villager resident = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
        final WerewolfEntity werewolf = helper.spawn(
            ModEntities.WEREWOLF.get(), new BlockPos(2, 1, 1), EntitySpawnReason.EVENT
        );
        final IronGolem guard = helper.spawn(
            EntityTypes.IRON_GOLEM, new BlockPos(0, 1, 1), EntitySpawnReason.STRUCTURE
        );
        guard.addTag(SettlementFortificationRuntime.GUARD_TAG);
        guard.addTag(SettlementFortificationRuntime.HUMAN_GUARD_TAG);

        helper.assertTrue(resident.hurtServer(
            helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 1.0F
        ), "a survival player attack must damage the protected resident");
        helper.assertTrue(guard.getTarget() == attacker,
            "the settlement guard must immediately defend a resident from a survival player");

        helper.onEachTick(() -> {
            if (helper.getLevel().getGameTime() % 5L != 0L) {
                return;
            }
            VillageGuardRuntime.tick(helper.getLevel());
            final var bolts = helper.getLevel().getEntitiesOfClass(
                AbstractArrow.class,
                new AABB(guard.blockPosition()).inflate(8.0D),
                arrow -> arrow.getOwner() == guard
            );
            helper.assertTrue(!bolts.isEmpty(),
                "a settlement guard must answer an attacker with a ranged silver bolt");
            helper.assertTrue(bolts.stream().allMatch(arrow -> arrow.getPickupItemStackOrigin().is(
                ModItems.ALL.get("ingredient_bolt_silver").get()
            )), "every settlement guard projectile must carry silver ammunition");
            final LivingDamageContext silverHit = new LivingDamageContext(
                werewolf,
                helper.getLevel().damageSources().arrow(bolts.get(0), guard),
                3.0F
            );
            helper.assertTrue(CreatureCombat.isSilverDamage(silverHit),
                "an actual guard-fired projectile must be recognized as silver damage");
            CreatureCombat.handleDamage(silverHit);
            helper.assertValueEqual(silverHit.getAmount(), 6.0F,
                "guard silver bolt damage against a werewolf");
            helper.succeed();
        });
    }

    public static void lowHealthRaidersEscapeAsBatAndWolf(final GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final ArcaneMob vampire = spawnArcane(helper, "vampire", new BlockPos(0, 1, 0));
        final WerewolfEntity werewolf = helper.spawn(
            ModEntities.WEREWOLF.get(), new BlockPos(2, 1, 2), EntitySpawnReason.EVENT
        );
        VillageAssaultRuntime.markRaider(
            vampire, center, 3, AssaultKind.VAMPIRE, SettlementKind.HUMAN, false, true
        );
        VillageAssaultRuntime.markRaider(
            werewolf, center, 3, AssaultKind.WEREWOLF, SettlementKind.HUMAN, false, true
        );
        vampire.setHealth(vampire.getMaxHealth() * VillageAssaultRules.ESCAPE_HEALTH_FRACTION);
        werewolf.setHealth(werewolf.getMaxHealth() * VillageAssaultRules.ESCAPE_HEALTH_FRACTION);

        final Mob bat = VillageAssaultRuntime.transformForEscape(helper.getLevel(), vampire, false).orElseThrow();
        final Mob wolf = VillageAssaultRuntime.transformForEscape(helper.getLevel(), werewolf, false).orElseThrow();
        helper.assertTrue(bat instanceof Bat, "a low-health vampire must escape in bat form");
        helper.assertTrue(wolf instanceof Wolf, "a low-health werewolf must escape in wolf form");
        helper.assertTrue(vampire.isRemoved() && werewolf.isRemoved(),
            "escape transformations must replace their original raider bodies");
        helper.assertTrue(VillageAssaultRuntime.isAssaultRaider(bat)
            && VillageAssaultRuntime.isAssaultRaider(wolf),
            "escape forms must retain their raid identity until retreat cleanup");
        helper.assertTrue(VillageAssaultRuntime.transformForEscape(helper.getLevel(), bat, true).isEmpty()
            && VillageAssaultRuntime.transformForEscape(helper.getLevel(), wolf, true).isEmpty(),
            "an escape form must never transform repeatedly");
        helper.succeed();
    }

    public static void approachFormsCrossClosedFortificationAndRevealInside(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final BlockPos center = helper.absolutePos(new BlockPos(1, 0, 1));
        final var plan = SettlementFortificationRules.compactPlan(
            SettlementFortificationRules.SettlementKind.HOBGOBLIN
        );
        SettlementFortificationRuntime.fortify(level, center, plan);
        final var layout = SettlementFortificationRuntime.registeredLayout(level, center).orElseThrow();
        helper.assertTrue(SettlementFortificationRuntime.gatePositions(level, center).stream().allMatch(position -> {
            final var state = level.getBlockState(position);
            return state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN);
        }), "approach fixture must start with every fortification gate closed");

        // Barrier shell so the live bat and wolf approach forms cannot wander out of the
        // ticking area and batch neighbors cannot wander into the reveal region. The shell
        // sits at horizontal radius 3, outside the radius-1 fort and the center +/-2
        // terrain snapshot, so gate and terrain assertions are untouched.
        final int shellRadius = 3;
        final int shellTop = layout.deckY() + 5;
        for (int dx = -shellRadius; dx <= shellRadius; dx++) {
            for (int dz = -shellRadius; dz <= shellRadius; dz++) {
                final boolean edge = Math.abs(dx) == shellRadius || Math.abs(dz) == shellRadius;
                for (int y = center.getY(); y <= shellTop; y++) {
                    if (edge || y == shellTop) {
                        level.setBlock(
                            new BlockPos(center.getX() + dx, y, center.getZ() + dz),
                            Blocks.BARRIER.defaultBlockState(),
                            3
                        );
                    }
                }
            }
        }

        final Map<BlockPos, BlockState> terrainBefore = snapshotBlocks(
            helper,
            center.offset(-2, -1, -2),
            new BlockPos(center.getX() + 2, layout.deckY() + 4, center.getZ() + 2)
        );
        final Mob bat = VillageAssaultRuntime.spawnApproachMember(
            level,
            center.offset(-2, 0, 0),
            center,
            3,
            AssaultKind.VAMPIRE,
            SettlementKind.HOBGOBLIN,
            true
        );
        final Mob wolf = VillageAssaultRuntime.spawnApproachMember(
            level,
            center.offset(0, 0, -2),
            center,
            3,
            AssaultKind.WEREWOLF,
            SettlementKind.HOBGOBLIN,
            true
        );
        helper.assertTrue(bat instanceof Bat && wolf instanceof Wolf,
            "vampire and werewolf assaults must begin as bat and wolf approach forms");
        // The fixture directly integrates both approach vectors so unrelated vanilla wandering
        // and navigation cadence cannot compete with the steering contract under test.
        bat.setNoAi(true);
        wolf.setNoAi(true);
        // Start the bat at cruising height so it does not spend dozens of ticks climbing
        // while vanilla Bat AI wanders it into walls or the revealed werewolf's reach.
        // The horizontal wall crossing under test is unchanged.
        bat.snapTo(bat.getX(), layout.deckY() + 3.0, bat.getZ(), bat.getYRot(), bat.getXRot());
        wolf.snapTo(wolf.getX(), layout.deckY() + 1.0, wolf.getZ(), wolf.getYRot(), wolf.getXRot());
        final long gameTime = level.getGameTime();
        final AssaultState state = new AssaultState(
            center,
            AssaultKind.WEREWOLF,
            SettlementKind.HOBGOBLIN,
            3,
            gameTime,
            gameTime + 1_000L,
            true,
            List.of()
        ).addRaiders(Set.of(bat.getStringUUID(), wolf.getStringUUID()));

        helper.onEachTick(() -> {
            if (!bat.isRemoved()) {
                VillageAssaultRuntime.tickApproachForm(level, state, bat);
                if (!bat.isRemoved()) {
                    bat.move(MoverType.SELF, bat.getDeltaMovement());
                }
            }
            if (!wolf.isRemoved()) {
                VillageAssaultRuntime.tickApproachForm(level, state, wolf);
                if (!wolf.isRemoved()) {
                    wolf.move(MoverType.SELF, wolf.getDeltaMovement());
                }
            }
            if (!bat.isRemoved() || !wolf.isRemoved()) {
                return;
            }
            final List<Mob> revealed = level.getEntitiesOfClass(
                Mob.class,
                new AABB(center).inflate(3.0D, 12.0D, 3.0D),
                entity -> VillageAssaultRuntime.isAssaultRaider(entity)
                    && !(entity instanceof Bat)
                    && !(entity instanceof Wolf)
            );
            if (revealed.size() != 2) {
                return;
            }
            helper.assertTrue(revealed.stream().allMatch(entity -> horizontalDistanceSqr(
                BlockPos.of(WarlockeryEntityData.get(entity).getLongOr(
                    VillageAssaultRuntime.APPROACH_REVEAL_POSITION,
                    BlockPos.ZERO.asLong()
                )),
                center
            ) <= 1.0D),
                "both disguised raiders must cross the wall and reveal inside its compact perimeter");
            helper.assertTrue(revealed.stream().map(entity -> WarlockeryEntityData.get(entity)
                .getString(VillageAssaultRuntime.ASSAULT_KIND).orElse("")).collect(java.util.stream.Collectors.toSet())
                .equals(Set.of(AssaultKind.VAMPIRE.serializedName(), AssaultKind.WEREWOLF.serializedName())),
                "approach forms must reveal as their original vampire and werewolf raid kinds");
            helper.assertTrue(SettlementFortificationRuntime.gatePositions(level, center).stream().allMatch(position -> {
                final var gateState = level.getBlockState(position);
                return gateState.getBlock() instanceof FenceGateBlock
                    && !gateState.getValue(FenceGateBlock.OPEN);
            }), "the final wolf passage must close every fortification gate");
            helper.assertValueEqual(
                snapshotBlocks(
                    helper,
                    center.offset(-2, -1, -2),
                    new BlockPos(center.getX() + 2, layout.deckY() + 4, center.getZ() + 2)
                ),
                terrainBefore,
                "approach forms changed fortification terrain"
            );
            helper.succeed();
        });
        helper.runAfterDelay(295L, () -> helper.assertTrue(bat.isRemoved() && wolf.isRemoved(),
            "approach forms must reveal by the bounded deadline"));
    }

    public static void bloodDrainedTradeLockUsesFabricInteractionCallback(final GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
        final long gameTime = helper.getLevel().getGameTime();
        WarlockeryEntityData.get(villager).putLong(VillageAssaultRuntime.BLOOD_DRAINED_UNTIL, gameTime + 40L);
        helper.assertTrue(VillageAssaultRuntime.handleVillagerInteraction(player, villager),
            "a blood-drained villager interaction must be consumed by the Fabric callback");
        helper.assertTrue(villager.getTradingPlayer() == null && !(player.containerMenu instanceof MerchantMenu),
            "a locked villager must not gain a customer or open a merchant menu");

        WarlockeryEntityData.get(villager).putLong(VillageAssaultRuntime.BLOOD_DRAINED_UNTIL, gameTime);
        helper.assertTrue(!VillageAssaultRuntime.handleVillagerInteraction(player, villager),
            "a trade lock must stop consuming interactions on its exact expiry tick");
        helper.assertValueEqual(VillageAssaultRuntime.bloodDrainedUntil(villager), 0L,
            "exact-expiry interaction must clear the persisted trade lock");
        helper.succeed();
    }

    public static void onlyRaidContributorsReceiveSettlementRewards(final GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        final var level = helper.getLevel();
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final ServerPlayer contributor = connectedSurvivalPlayer(helper);
        final ServerPlayer idleWitness = connectedSurvivalPlayer(helper);
        final VillageAssaultData data = VillageAssaultData.get(level);
        final long gameTime = level.getGameTime();
        data.active().ifPresent(state -> data.finish(gameTime, 0L, 1.0D));
        helper.assertTrue(data.begin(center, AssaultKind.GOBLIN, SettlementKind.HUMAN, gameTime),
            "reward contribution fixture must start an assault");
        final Mob raider = helper.spawn(ModEntities.GOBLIN.get(), new BlockPos(1, 1, 1), EntitySpawnReason.EVENT);
        VillageAssaultRuntime.markRaider(
            raider,
            center,
            3,
            AssaultKind.GOBLIN,
            SettlementKind.HUMAN,
            false,
            true
        );
        data.update(data.active().orElseThrow().waveSpawned(3).addRaiders(Set.of(raider.getStringUUID())));

        final LivingDamageContext contribution = new LivingDamageContext(
            raider,
            level.damageSources().playerAttack(contributor),
            2.0F
        );
        VillageAssaultRuntime.handleDamage(contribution);
        final AssaultState defended = data.active().orElseThrow();
        helper.assertValueEqual(Set.copyOf(defended.participants()), Set.of(contributor.getStringUUID()),
            "only players who damage an assault raider may become reward participants");
        helper.assertTrue(!defended.participants().contains(idleWitness.getStringUUID()),
            "a nearby idle player must not receive contribution credit");

        VillageAssaultRuntime.completeDefense(level, data, defended, gameTime);
        helper.assertTrue(contributor.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)
            && contributor.hasEffect(MobEffects.ABSORPTION)
            && contributor.hasEffect(MobEffects.HASTE),
            "the contributing defender must receive the completed settlement reward");
        helper.assertTrue(!idleWitness.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)
            && !idleWitness.hasEffect(MobEffects.ABSORPTION)
            && !idleWitness.hasEffect(MobEffects.HASTE),
            "an idle nearby player must receive no settlement reward effects");
        helper.succeed();
    }

    public static void hobgoblinSupernaturalVariantsExistOnlyAsRaidMarkers(final GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        final ArcaneMob ordinary = spawnArcane(helper, "vampire", new BlockPos(0, 1, 0));
        final ArcaneMob raidVariant = spawnArcane(helper, "vampire", new BlockPos(2, 1, 2));
        VillageAssaultRuntime.markRaider(
            raidVariant,
            helper.absolutePos(new BlockPos(1, 1, 1)),
            2,
            AssaultKind.VAMPIRE,
            SettlementKind.HOBGOBLIN,
            true,
            true
        );
        helper.assertTrue(!VillageAssaultRuntime.isAssaultRaider(ordinary)
            && !VillageAssaultRuntime.isHobgoblinVariant(ordinary),
            "ordinary supernatural mobs must not masquerade as hobgoblin raid variants");
        helper.assertTrue(VillageAssaultRuntime.isAssaultRaider(raidVariant)
            && VillageAssaultRuntime.isHobgoblinVariant(raidVariant),
            "hobgoblin supernatural variants must be derived from explicit raid state");
        helper.succeed();
    }

    public static void compactWavesPreserveCountsPowersAndSettlementVariants(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(0, 0, 0), new BlockPos(2, 0, 2))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
        final var level = helper.getLevel();
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final AABB testBounds = new AABB(center).inflate(2.0D);

        for (final AssaultKind kind : AssaultKind.values()) {
            for (int wave = 1; wave <= VillageAssaultRules.WAVE_COUNT; wave++) {
                final int currentWave = wave;
                final SettlementKind settlement = kind != AssaultKind.GOBLIN && wave == 2
                    ? SettlementKind.HOBGOBLIN
                    : SettlementKind.HUMAN;
                final Set<UUID> existing = level.getEntitiesOfClass(Mob.class, testBounds).stream()
                    .map(Entity::getUUID)
                    .collect(java.util.stream.Collectors.toCollection(HashSet::new));
                final int expected = VillageAssaultRules.waveSize(kind, wave);
                final int spawned = VillageAssaultRuntime.spawnCompactWave(
                    level, center, wave, kind, settlement
                );
                final List<Mob> members = level.getEntitiesOfClass(
                    Mob.class,
                    testBounds,
                    entity -> !existing.contains(entity.getUUID()) && VillageAssaultRuntime.isAssaultRaider(entity)
                );
                helper.assertValueEqual(spawned, expected,
                    kind.serializedName() + " wave " + wave + " spawned count");
                helper.assertValueEqual(members.size(), expected,
                    kind.serializedName() + " wave " + wave + " tracked count");
                helper.assertTrue(members.stream().allMatch(entity -> WarlockeryEntityData.get(entity)
                    .getString(VillageAssaultRuntime.ASSAULT_KIND)
                    .filter(kind.serializedName()::equals)
                    .isPresent()), "every compact wave member must retain its assault kind");
                helper.assertTrue(members.stream().allMatch(entity -> WarlockeryEntityData.get(entity).getIntOr(
                    VillageAssaultRuntime.ASSAULT_WAVE, 0
                ) == currentWave), "every compact wave member must retain its wave number");
                helper.assertValueEqual(members.stream().filter(entity -> WarlockeryEntityData.get(entity).getBooleanOr(
                    VillageAssaultRuntime.ASSAULT_LEADER, false
                )).count(), 1L, "compact wave leader count");
                final boolean expectedHobgoblinVariant = settlement == SettlementKind.HOBGOBLIN
                    && kind != AssaultKind.GOBLIN;
                helper.assertTrue(members.stream().allMatch(entity ->
                    VillageAssaultRuntime.isHobgoblinVariant(entity) == expectedHobgoblinVariant
                ), "only supernatural waves attacking hobgoblin settlements may use hobgoblin variants");
                final int expectedPower = kind == AssaultKind.GOBLIN ? 0 : switch (wave) {
                    case 1 -> 3;
                    case 2 -> 6;
                    case 3 -> 10;
                    default -> throw new IllegalStateException();
                };
                helper.assertValueEqual(
                    VillageAssaultRules.npcPowers(kind, wave).progressionLevel(),
                    expectedPower,
                    kind.serializedName() + " wave " + wave + " NPC power level"
                );
                members.forEach(Entity::discard);
            }
        }

        final VillageAssaultData data = VillageAssaultData.get(level);
        final long gameTime = level.getGameTime();
        data.active().ifPresent(state -> data.finish(gameTime, 0L, 1.0D));
        helper.assertTrue(data.begin(center, AssaultKind.VAMPIRE, SettlementKind.HOBGOBLIN, gameTime),
            "hobgoblin vampire objective fixture must start");
        data.update(data.active().orElseThrow().waveSpawned(3));
        final ArcaneMob vampire = spawnArcane(helper, "vampire", new BlockPos(0, 1, 1));
        VillageAssaultRuntime.markRaider(
            vampire, center, 3, AssaultKind.VAMPIRE, SettlementKind.HOBGOBLIN, true, true
        );
        final var fedResident = helper.spawn(
            ModEntities.HOBGOBLIN.get(), new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL
        );
        helper.assertTrue(VillageAssaultRuntime.feedOnVillager(level, vampire, fedResident, 2.0F).newlyCounted(),
            "feeding on a hobgoblin resident must advance a hobgoblin-settlement vampire objective");
        helper.assertValueEqual(data.active().orElseThrow().objectiveProgress(), 1,
            "hobgoblin vampire objective progress");
        data.finish(gameTime, 0L, 1.0D);
        vampire.discard();

        helper.assertTrue(data.begin(center, AssaultKind.WEREWOLF, SettlementKind.HOBGOBLIN, gameTime),
            "hobgoblin werewolf objective fixture must start");
        data.update(data.active().orElseThrow().waveSpawned(3));
        final WerewolfEntity werewolf = helper.spawn(
            ModEntities.WEREWOLF.get(), new BlockPos(2, 1, 1), EntitySpawnReason.EVENT
        );
        VillageAssaultRuntime.markRaider(
            werewolf, center, 3, AssaultKind.WEREWOLF, SettlementKind.HOBGOBLIN, true, true
        );
        final var huntedResident = helper.spawn(
            ModEntities.HOBGOBLIN.get(), new BlockPos(1, 1, 2), EntitySpawnReason.NATURAL
        );
        helper.assertTrue(VillageAssaultRuntime.recordWerewolfObjective(level, werewolf, huntedResident),
            "hunting a hobgoblin resident must advance a hobgoblin-settlement werewolf objective");
        helper.assertValueEqual(data.active().orElseThrow().objectiveProgress(), 1,
            "hobgoblin werewolf objective progress");
        data.finish(gameTime, 0L, 1.0D);
        helper.succeed();
    }

    public static void objectiveTargetingSkipsCompletedOrUnavailableResidents(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(0, 0, 0), new BlockPos(2, 0, 2))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
        final var level = helper.getLevel();
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final long gameTime = level.getGameTime();

        final ArcaneMob vampire = spawnArcane(helper, "vampire", new BlockPos(0, 1, 1));
        final Villager drained = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
        WarlockeryEntityData.get(drained).putLong(
            VillageAssaultRuntime.BLOOD_DRAINED_UNTIL,
            gameTime + VillageAssaultRuntime.BLOOD_DRAINED_TICKS
        );
        final Villager fedBefore = helper.spawn(EntityTypes.VILLAGER, new BlockPos(0, 1, 1));
        final Villager freshBlood = helper.spawn(EntityTypes.VILLAGER, new BlockPos(0, 1, 1));
        final AssaultState vampireState = new AssaultState(
            center,
            AssaultKind.VAMPIRE,
            SettlementKind.HUMAN,
            3,
            gameTime,
            gameTime + 1_000L,
            false,
            List.of(),
            1,
            VillageAssaultRules.objectiveQuota(AssaultKind.VAMPIRE),
            List.of(fedBefore.getStringUUID()),
            false
        );
        helper.assertValueEqual(
            VillageAssaultRuntime.selectObjectiveResident(level, vampire, vampireState).orElseThrow().getUUID(),
            freshBlood.getUUID(),
            "vampire fresh-blood target priority"
        );
        drained.discard();
        fedBefore.discard();
        freshBlood.discard();
        vampire.discard();

        final WerewolfEntity werewolf = helper.spawn(
            ModEntities.WEREWOLF.get(), new BlockPos(2, 1, 1), EntitySpawnReason.EVENT
        );
        final Villager infected = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
        helper.assertTrue(WerewolfVillagerInfectionRuntime.markInfected(infected),
            "werewolf target-priority fixture must mark its nearest resident infected");
        final Villager huntedBefore = helper.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 1));
        final Villager freshPrey = helper.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 1));
        final AssaultState werewolfState = new AssaultState(
            center,
            AssaultKind.WEREWOLF,
            SettlementKind.HUMAN,
            3,
            gameTime,
            gameTime + 1_000L,
            false,
            List.of(),
            1,
            VillageAssaultRules.objectiveQuota(AssaultKind.WEREWOLF),
            List.of(huntedBefore.getStringUUID()),
            false
        );
        helper.assertValueEqual(
            VillageAssaultRuntime.selectObjectiveResident(level, werewolf, werewolfState).orElseThrow().getUUID(),
            freshPrey.getUUID(),
            "werewolf fresh-villager target priority"
        );
        helper.succeed();
    }

    public static void assaultObjectivesRewardsAndCleanupRemainIsolated(final GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        final var level = helper.getLevel();
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final long gameTime = level.getGameTime();
        final ServerPlayer defender = connectedSurvivalPlayer(helper);
        final VillageAssaultData data = VillageAssaultData.get(level);
        data.active().ifPresent(state -> data.finish(gameTime, 0L, 1.0D));

        helper.assertTrue(data.begin(center, AssaultKind.VAMPIRE, SettlementKind.HUMAN, gameTime),
            "vampire objective fixture must start cleanly");
        data.update(data.active().orElseThrow().waveSpawned(3)
            .addParticipants(Set.of(defender.getStringUUID())));
        final ArcaneMob vampire = spawnArcane(helper, "vampire", new BlockPos(1, 1, 1));
        VillageAssaultRuntime.markRaider(
            vampire, center, 3, AssaultKind.VAMPIRE, SettlementKind.HUMAN, false, true
        );
        vampire.setHealth(vampire.getMaxHealth() - 5.0F);
        final float healthBeforeEmptyFeed = vampire.getHealth();
        final Villager emptyFeedVictim = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
        emptyFeedVictim.setHealth(1.0F);
        final var emptyFeed = VillageAssaultRuntime.feedOnVillager(
            level, vampire, emptyFeedVictim, 100.0F
        );
        helper.assertTrue(emptyFeed.damage() == 0.0F && !emptyFeed.newlyCounted(),
            "a vampire feed that cannot drain health must not count as a victim");
        helper.assertValueEqual(vampire.getHealth(), healthBeforeEmptyFeed,
            "vampire health after an empty feed");
        helper.assertValueEqual(VillageAssaultRuntime.bloodDrainedUntil(emptyFeedVictim), 0L,
            "empty feed trade lock");
        helper.assertValueEqual(data.active().orElseThrow().objectiveProgress(), 0,
            "vampire objective progress after an empty feed");
        Villager firstFed = null;
        for (int index = 0; index < VillageAssaultRules.objectiveQuota(AssaultKind.VAMPIRE); index++) {
            final Villager victim = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
            victim.setHealth(index == 0 ? 2.0F : victim.getMaxHealth());
            final var fed = VillageAssaultRuntime.feedOnVillager(level, vampire, victim, 100.0F);
            helper.assertTrue(fed.newlyCounted(), "each fresh vampire victim must count exactly once");
            if (index == 0) {
                firstFed = victim;
                victim.setHealth(victim.getHealth() - fed.damage());
                helper.assertTrue(victim.isAlive() && victim.getHealth() == 1.0F,
                    "vampire feeding must leave its villager alive with at least one health");
                helper.assertValueEqual(
                    fed.tradeLockExpiresAt(), gameTime + VillageAssaultRuntime.BLOOD_DRAINED_TICKS,
                    "blood-drained trade lock expiration"
                );
                final var duplicate = VillageAssaultRuntime.feedOnVillager(level, vampire, victim, 1.0F);
                helper.assertTrue(!duplicate.newlyCounted(), "the same fed villager must never count twice");
            }
        }
        final AssaultState vampireRetreat = data.active().orElseThrow();
        helper.assertTrue(vampireRetreat.raidersRetreating(),
            "vampires must retreat immediately after meeting their unique-victim quota");
        VillageAssaultRuntime.applyDefenseReward(level, vampireRetreat);
        helper.assertTrue(!defender.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)
            && !defender.hasEffect(MobEffects.ABSORPTION)
            && !defender.hasEffect(MobEffects.REGENERATION),
            "a vampire objective retreat must not grant defender rewards");
        final Villager fedVictim = java.util.Objects.requireNonNull(firstFed);
        final long lockExpiry = VillageAssaultRuntime.bloodDrainedUntil(fedVictim);
        helper.assertTrue(VillageAssaultRuntime.isBloodDrained(fedVictim, lockExpiry - 1L),
            "trading must stay locked through the tick before expiration");
        helper.assertTrue(!VillageAssaultRuntime.isBloodDrained(fedVictim, lockExpiry),
            "trading must resume at the exact expiration tick");
        final Villager reloadedVictim = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
        WarlockeryEntityData.get(reloadedVictim).merge(WarlockeryEntityData.get(fedVictim).copy());
        helper.assertValueEqual(VillageAssaultRuntime.bloodDrainedUntil(reloadedVictim), lockExpiry,
            "persisted blood-drained trade lock after reload");
        helper.assertTrue(VillageAssaultRuntime.clearExpiredTradeLock(reloadedVictim, lockExpiry),
            "expired persisted trade locks must clean themselves once");
        helper.assertTrue(!VillageAssaultRuntime.clearExpiredTradeLock(reloadedVictim, lockExpiry),
            "trade-lock cleanup must not spam repeated state changes");
        data.finish(gameTime, 1L, 1.0D);

        helper.assertTrue(data.begin(center, AssaultKind.WEREWOLF, SettlementKind.HUMAN, gameTime),
            "werewolf objective fixture must start after vampire cleanup");
        data.update(data.active().orElseThrow().waveSpawned(3)
            .addParticipants(Set.of(defender.getStringUUID())));
        final WerewolfEntity werewolf = helper.spawn(
            ModEntities.WEREWOLF.get(), new BlockPos(1, 1, 1), EntitySpawnReason.EVENT
        );
        VillageAssaultRuntime.markRaider(
            werewolf, center, 3, AssaultKind.WEREWOLF, SettlementKind.HUMAN, false, true
        );
        final LivingDamageContext playerAttack = new LivingDamageContext(
            defender, level.damageSources().mobAttack(werewolf), 4.0F
        );
        VillageAssaultRuntime.handleDamage(playerAttack);
        helper.assertTrue(SupernaturalState.getForm(defender) == SupernaturalForm.NONE,
            "an NPC werewolf attack must never infect or transform a player");
        helper.assertValueEqual(data.active().orElseThrow().objectiveProgress(), 0,
            "attacking a player must not advance a werewolf raid objective");
        final Villager infected = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
        helper.assertTrue(VillageAssaultRuntime.infectVillagerFromRaider(level, werewolf, infected),
            "a werewolf infection must count as one raid objective victim");
        helper.assertTrue(!VillageAssaultRuntime.infectVillagerFromRaider(level, werewolf, infected),
            "duplicate infection must not count twice");
        final Villager killed = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
        helper.assertTrue(VillageAssaultRuntime.recordWerewolfObjective(level, werewolf, killed),
            "a werewolf villager kill must count toward the same objective");
        helper.assertTrue(!VillageAssaultRuntime.recordWerewolfObjective(level, werewolf, killed),
            "the same killed villager must not count twice");
        final Villager finalVictim = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
        helper.assertTrue(VillageAssaultRuntime.recordWerewolfObjective(level, werewolf, finalVictim),
            "a third unique werewolf victim must complete the objective");
        final AssaultState werewolfRetreat = data.active().orElseThrow();
        helper.assertTrue(werewolfRetreat.objectiveProgress() == 3 && werewolfRetreat.raidersRetreating(),
            "werewolf quota completion must begin retreat without counting a player");
        helper.assertTrue(!werewolfRetreat.objectiveVictims().contains(defender.getStringUUID()),
            "players must never be infected or counted toward the werewolf objective");
        VillageAssaultRuntime.applyDefenseReward(level, werewolfRetreat);
        helper.assertTrue(!defender.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)
            && !defender.hasEffect(MobEffects.ABSORPTION)
            && !defender.hasEffect(MobEffects.SPEED),
            "a werewolf objective retreat must not grant defender rewards");
        data.finish(gameTime, 2L, 1.0D);

        helper.assertTrue(data.begin(center, AssaultKind.GOBLIN, SettlementKind.HUMAN, gameTime),
            "defense victory fixture must start after supernatural cleanup");
        AssaultState defended = data.active().orElseThrow().waveSpawned(3)
            .addParticipants(Set.of(defender.getStringUUID()));
        data.update(defended);
        final Mob goblin = helper.spawn(ModEntities.GOBLIN.get(), new BlockPos(1, 1, 1), EntitySpawnReason.EVENT);
        VillageAssaultRuntime.markRaider(
            goblin, center, 3, AssaultKind.GOBLIN, SettlementKind.HUMAN, false, true
        );
        VillageAssaultRuntime.completeDefense(level, data, defended, gameTime);
        helper.assertTrue(data.active().isEmpty(), "defense victory must clear its active saved state");
        helper.assertTrue(data.nextAttempt() > gameTime, "defense victory must schedule an anti-spam cooldown");
        helper.assertTrue(!VillageAssaultRuntime.isAssaultRaider(goblin),
            "defense cleanup must remove stale raid markers from surviving entities");
        helper.assertTrue(defender.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)
            && defender.hasEffect(MobEffects.ABSORPTION)
            && defender.hasEffect(MobEffects.HASTE),
            "a true village defense must grant favor, protection, and its themed boon");
        helper.succeed();
    }

    private static ArcaneMob spawnArcane(
        final GameTestHelper helper,
        final String id,
        final BlockPos relativePosition
    ) {
        final Entity created = ModEntities.ALL.get(id).get().create(helper.getLevel(), EntitySpawnReason.EVENT);
        if (!(created instanceof ArcaneMob mob)) {
            throw new IllegalStateException(id + " did not create an ArcaneMob");
        }
        final BlockPos position = helper.absolutePos(relativePosition);
        mob.snapTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static ServerPlayer connectedSurvivalPlayer(final GameTestHelper helper) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        final BlockPos position = helper.absolutePos(new BlockPos(1, 1, 1));
        player.teleportTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        return GameTestMockPlayers.autoDisconnect(helper, player);
    }

    private static Map<BlockPos, BlockState> snapshotBlocks(
        final GameTestHelper helper,
        final BlockPos first,
        final BlockPos second
    ) {
        final Map<BlockPos, BlockState> snapshot = new LinkedHashMap<>();
        BlockPos.betweenClosedStream(first, second).forEach(position ->
            snapshot.put(position.immutable(), helper.getLevel().getBlockState(position))
        );
        return Map.copyOf(snapshot);
    }

    private static double horizontalDistanceSqr(final Entity entity, final BlockPos position) {
        final double x = entity.getX() - (position.getX() + 0.5D);
        final double z = entity.getZ() - (position.getZ() + 0.5D);
        return x * x + z * z;
    }

    private static double horizontalDistanceSqr(final BlockPos first, final BlockPos second) {
        final double x = first.getX() - second.getX();
        final double z = first.getZ() - second.getZ();
        return x * x + z * z;
    }
}
