package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.magic.MagicPathState;
import com.kadamitas.warlockery.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Six isolated bounded F17 live fixtures. Each fixture owns setup and cleanup and
 * restores created entities, items, and world state through {@link Fixture#close()}
 * even on failure; delayed stages are idempotent and re-entrant.
 */
public final class CorpseGameTests {
    private CorpseGameTests() {
    }

    public static void corpseRaiseDeadIdentityOwnerAndAcquisitionArePreserved(final GameTestHelper helper) {
        buildFloor(helper);
        final Fixture fixture = new Fixture(helper);
        try {
            final CorpseEntity body = fixture.spawnBody(new BlockPos(1, 1, 1));
            helper.assertValueEqual(
                BuiltInRegistries.ENTITY_TYPE.getKey(body.getType()).toString(),
                "warlockery:corpse",
                "the dedicated shell keeps the exact registry identity"
            );
            helper.assertTrue(!Zombie.class.isAssignableFrom(CorpseEntity.class)
                    && !ArcaneMob.class.isAssignableFrom(CorpseEntity.class)
                    && Monster.class.isAssignableFrom(CorpseEntity.class),
                "the Body is a dedicated Monster, not a Zombie or ArcaneMob");
            helper.assertValueEqual(body.creatureKind(), ArcaneCreature.CreatureKind.CORPSE,
                "creatureKind stays CORPSE");
            helper.assertValueEqual(body.operationalTargetGoalCount(), 0,
                "the target selector is empty");
            helper.assertTrue(body.operationalGoalNames().stream()
                .allMatch(name -> name.contains("Look")), "goal selector is LOOK-only");
            for (final EquipmentSlot slot : EquipmentSlot.values()) {
                helper.assertTrue(body.getItemBySlot(slot).isEmpty(),
                    "every equipment slot stays empty: " + slot);
            }
            final ServerPlayer owner = fixture.connectedPlayer(new BlockPos(0, 1, 0));
            CreatureBehaviorState.bind(body, owner.getUUID());
            helper.runAfterDelay(5, () -> fixture.step(() -> {
                helper.assertTrue(CreatureBehaviorState.isOwnedBy(body, owner.getUUID()),
                    "the existing Raise owner key is the loyalty source");
                helper.assertTrue(!CorpseRuntime.legalTarget(body, owner),
                    "the Raise owner is an absolute target exclusion");
                helper.assertTrue(body.isAlive() && !body.corpseState().dormant(),
                    "a fresh Body is active with full cohesion");
                helper.assertValueEqual(body.corpseState().cohesion(), 1_200,
                    "new cohesion starts at the maximum");
                final BlockPos corner = helper.absolutePos(new BlockPos(2, 1, 2));
                body.snapTo(Vec3.atBottomCenterOf(corner));
                body.setDeltaMovement(Vec3.ZERO);
                helper.assertTrue(body.distanceTo(owner) <= CorpseRules.FOLLOW_STOP_DISTANCE,
                    "the sealed 3x3 cell keeps the loaded living Raise owner inside the 4-block stop envelope");
                helper.runAfterDelay(100, () -> fixture.step(() -> {
                    final double distance = body.distanceTo(owner);
                    helper.assertTrue(distance <= CorpseRules.FOLLOW_STOP_DISTANCE + 1.0D,
                        "inside the stop envelope the self-ticking Body holds beside its Raise owner");
                    helper.assertTrue(body.getNavigation().isDone(),
                        "inside the stop distance the follow stage keeps navigation halted"
                            + " through the cancellation sequence");
                    fixture.close();
                    helper.succeed();
                }));
            }));
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void corpseScavengesFeedsAndEntersDormancySafely(final GameTestHelper helper) {
        buildFloor(helper);
        final Fixture fixture = new Fixture(helper);
        try {
            final CorpseEntity body = fixture.spawnBody(new BlockPos(2, 1, 2));
            body.setCorpseState(new CorpseState(600, 0, 0));
            final ItemEntity flesh = fixture.dropItem(new BlockPos(1, 1, 2), Items.ROTTEN_FLESH, 2);
            helper.runAfterDelay(160, () -> fixture.step(() -> {
                helper.assertTrue(flesh.isAlive() && flesh.getItem().getCount() == 1,
                    "exactly one Rotten Flesh is consumed and the remainder stays");
                helper.assertTrue(body.corpseState().cohesion() > 850
                        && body.corpseState().cohesion() <= 900,
                    "one meal restores exactly 300 cohesion minus ordinary loaded decay");
                helper.assertValueEqual(body.corpseState().groundMealCooldown() > 0, true,
                    "a successful ground meal starts the 4,800-tick cooldown");
                helper.assertValueEqual(body.corpseCounters().itemsConsumed, 1,
                    "exactly one autonomous mutation succeeded");
                body.setCorpseState(body.corpseState().withCohesion(0));
                helper.runAfterDelay(5, () -> fixture.step(() -> {
                    helper.assertTrue(body.isDormant(), "zero cohesion derives dormancy");
                    helper.assertTrue(body.getTarget() == null && body.getNavigation().isDone(),
                        "a dormant Body clears target and navigation");
                    final ServerPlayer ownerPlayer = fixture.connectedPlayer(new BlockPos(1, 1, 1));
                    CreatureBehaviorState.bind(body, ownerPlayer.getUUID());
                    ownerPlayer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(Items.ROTTEN_FLESH, 1));
                    final var result = CorpseRuntime.manualFeed(body, ownerPlayer,
                        net.minecraft.world.InteractionHand.MAIN_HAND);
                    helper.assertValueEqual(result.consumesAction(), true,
                        "the Raise owner may hand-feed the dormant Body");
                    helper.assertTrue(body.corpseState().cohesion() >= 300,
                        "a manual feed restores 300 cohesion and wakes the Body");
                    helper.assertTrue(ownerPlayer.getMainHandItem().isEmpty(),
                        "a survival owner consumes exactly one item");
                    fixture.close();
                    helper.succeed();
                }));
            }));
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void corpseClutchReactsWithoutHordeOrConversion(final GameTestHelper helper) {
        buildFloor(helper);
        final Fixture fixture = new Fixture(helper);
        try {
            final CorpseEntity body = fixture.spawnBody(new BlockPos(1, 1, 1));
            final Zombie attacker = fixture.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
            attacker.setNoAi(true);
            final long baseline = countEntities(helper.getLevel(), Zombie.class);
            helper.runAfterDelay(5, () -> fixture.step(() -> {
                body.hurtServer(helper.getLevel(),
                    helper.getLevel().damageSources().mobAttack(attacker), 2.0F);
                helper.runAfterDelay(60, () -> fixture.step(() -> {
                    helper.assertTrue(body.corpseCounters().directAttackerWrites >= 1,
                        "an effective attack from a causing LivingEntity is attributed");
                    helper.assertTrue(body.getTarget() == attacker || body.corpseCounters().attackAttempts >= 1,
                        "the direct attacker becomes the one combat subject");
                    helper.assertValueEqual(countEntities(helper.getLevel(), Zombie.class), baseline,
                        "no reinforcement, horde, or conversion spawns another mob");
                    helper.assertValueEqual(body.corpseCounters().reinforcements, 0,
                        "the reinforcement counter stays zero");
                    helper.assertValueEqual(body.corpseCounters().villagerConversions, 0,
                        "no villager conversion executes");
                    final ServerPlayer creative = fixture.connectedPlayer(new BlockPos(0, 1, 2), GameType.CREATIVE);
                    helper.assertTrue(!CorpseRuntime.legalTarget(body, creative),
                        "a creative player is an absolute exclusion");
                    fixture.close();
                    helper.succeed();
                }));
            }));
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void corpseDualOwnerGraveCommandAndLoyaltyAreDeterministic(final GameTestHelper helper) {
        buildFloor(helper);
        final Fixture fixture = new Fixture(helper);
        try {
            final CorpseEntity body = fixture.spawnBody(new BlockPos(1, 1, 1));
            final ServerPlayer raiseOwner = fixture.connectedPlayer(new BlockPos(0, 1, 0));
            final ServerPlayer graveOwner = fixture.connectedPlayer(new BlockPos(2, 1, 0));
            CreatureBehaviorState.bind(body, raiseOwner.getUUID());
            MagicPathState.grantPermanent(graveOwner, MagicPath.GRAVE);
            body.getPersistentData().putString("WarlockeryGraveOwner", graveOwner.getStringUUID());
            body.getPersistentData().putLong("WarlockeryGraveExpiration",
                helper.getLevel().getGameTime() + CorpseRules.GRAVE_DURATION_TICKS);
            CorpseRuntime.notifyGraveBind(body, helper.getLevel());
            helper.runAfterDelay(25, () -> fixture.step(() -> {
                helper.assertTrue(!CorpseRuntime.legalTarget(body, raiseOwner)
                        && !CorpseRuntime.legalTarget(body, graveOwner),
                    "both valid owner identities are absolute exclusions");
                final BlockPos destination = helper.absolutePos(new BlockPos(2, 1, 2));
                CorpseRuntime.deliverGraveDirective(body, helper.getLevel(), destination);
                helper.assertTrue(body.transientFacts().graveDestination().isPresent(),
                    "one typed Grave directive is stored instead of a direct navigation write");
                helper.assertValueEqual(body.transientFacts().graveDestination().orElseThrow(),
                    new Vec3(destination.getX() + 0.5D, destination.getY() + 1.0D, destination.getZ() + 0.5D),
                    "the directive keeps the exact centered command destination");
                helper.runAfterDelay(60, () -> fixture.step(() -> {
                    helper.assertTrue(body.corpseCounters().graveDirectivesReceived >= 1,
                        "the sole controller consumed the directive");
                    body.getPersistentData().putLong("WarlockeryGraveExpiration", 0L);
                    helper.runAfterDelay(25, () -> fixture.step(() -> {
                        helper.assertTrue(body.getPersistentData()
                                .getStringOr("WarlockeryGraveOwner", "").isEmpty(),
                            "an expired Grave key is cleared once and loyalty is restored");
                        helper.assertTrue(CreatureBehaviorState.isOwnedBy(body, raiseOwner.getUUID()),
                            "the Raise owner relationship survives Grave expiry");
                        fixture.close();
                        helper.succeed();
                    }));
                }));
            }));
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void corpseRelationshipsAndZombieLifecycleAreReplaced(final GameTestHelper helper) {
        buildFloor(helper);
        final Fixture fixture = new Fixture(helper);
        try {
            final CorpseEntity body = fixture.spawnBody(new BlockPos(1, 1, 1));
            body.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            body.normalizeLifecycle();
            helper.assertTrue(body.getMainHandItem().isEmpty(),
                "hostile legacy equipment is normalized empty without drops");
            for (final String modifierId : CorpseEntity.legacyModifierIds()) {
                helper.assertTrue(modifierId.startsWith("minecraft:"),
                    "exact legacy modifier IDs are declared: " + modifierId);
            }
            final Villager villager = fixture.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new BlockPos(0, 1, 2));
            villager.setNoAi(true);
            helper.runAfterDelay(60, () -> fixture.step(() -> {
                helper.assertTrue(body.getTarget() == null,
                    "the Body never proactively hunts a villager");
                helper.assertValueEqual(body.corpseCounters().villagerConversions, 0,
                    "killedEntity has no conversion path");
                helper.assertValueEqual(body.corpseCounters().drownedConversions, 0,
                    "there is no Drowned conversion state");
                helper.assertValueEqual(body.corpseCounters().doorBreaks, 0,
                    "no door breaking executes");
                body.setCorpseState(body.corpseState().withCohesion(0));
                helper.runAfterDelay(5, () -> fixture.step(() -> {
                    helper.assertTrue(body.isDormant(), "the same entity becomes dormant");
                    helper.assertTrue(body.isAlive() && !body.isInvulnerable(),
                        "a dormant Body stays the same damageable entity");
                    fixture.close();
                    helper.succeed();
                }));
            }));
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void corpseSaveReloadHazardsAndWorkAreBounded(final GameTestHelper helper) {
        buildFloor(helper);
        final Fixture fixture = new Fixture(helper);
        try {
            final CorpseEntity body = fixture.spawnBody(new BlockPos(2, 1, 2));
            body.setCorpseState(new CorpseState(451, 7, 1_234));
            final CorpseState reloaded = CorpseState.read(body.corpseState().write());
            helper.assertValueEqual(reloaded, body.corpseState(),
                "the versioned state round-trips exactly through save data");
            final var malformed = new net.minecraft.nbt.CompoundTag();
            malformed.putInt("Version", 9);
            helper.assertValueEqual(CorpseState.read(malformed), CorpseState.fresh(),
                "an unknown future version discards only Corpse semantics to safe defaults");
            helper.setBlock(new BlockPos(2, 1, 1), Blocks.FIRE);
            helper.runAfterDelay(45, () -> fixture.step(() -> {
                helper.assertTrue(body.corpseCounters().hazardObservationReads
                        <= (body.tickCount / 20 + 2) * CorpseRules.HAZARD_OBSERVATION_READS,
                    "hazard observation stays within 18 charged reads per due 20-tick phase");
                helper.assertValueEqual(body.corpseCounters().chunkLoadRequests, 0,
                    "no forced chunk work is ever requested");
                helper.assertValueEqual(body.corpseCounters().blockEdits, 0,
                    "the Body never edits a block");
                helper.assertValueEqual(body.corpseCounters().genericBehaviorDispatches, 0,
                    "no generic behavior runtime dispatches for the Body");
                helper.assertTrue(body.corpseCounters().pathRequests
                        <= body.tickCount / CorpseRules.PATH_INTERVAL_TICKS + 1,
                    "path cadence stays at most one request per 20 loaded ticks");
                fixture.close();
                helper.succeed();
            }));
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static long countEntities(final ServerLevel level, final Class<? extends Entity> type) {
        final long[] count = {0L};
        level.getEntities().get(
            net.minecraft.world.level.entity.EntityTypeTest.forClass(type),
            entity -> {
                count[0]++;
                return net.minecraft.util.AbortableIterationConsumer.Continuation.CONTINUE;
            }
        );
        return count[0];
    }

    private static void buildFloor(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(0, 0, 0), new BlockPos(2, 0, 2))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final List<Entity> entities = new ArrayList<>();
        private boolean closed;

        private Fixture(final GameTestHelper helper) {
            this.helper = helper;
        }

        private CorpseEntity spawnBody(final BlockPos position) {
            @SuppressWarnings("unchecked")
            final EntityType<? extends Monster> type =
                (EntityType<? extends Monster>) ModEntities.ALL.get("corpse").get();
            final CorpseEntity body = new CorpseEntity(type, helper.getLevel());
            final BlockPos absolute = helper.absolutePos(position);
            body.snapTo(Vec3.atBottomCenterOf(absolute));
            body.setDeltaMovement(Vec3.ZERO);
            body.setPersistenceRequired();
            helper.getLevel().addFreshEntity(body);
            return track(body);
        }

        private <T extends Entity> T spawn(final EntityType<T> type, final BlockPos position) {
            final T entity = helper.spawn(type, position, EntitySpawnReason.EVENT);
            entity.setDeltaMovement(Vec3.ZERO);
            return track(entity);
        }

        private ItemEntity dropItem(final BlockPos position, final net.minecraft.world.item.Item item, final int count) {
            final BlockPos absolute = helper.absolutePos(position);
            final ItemEntity dropped = new ItemEntity(
                helper.getLevel(),
                absolute.getX() + 0.5D,
                absolute.getY() + 0.1D,
                absolute.getZ() + 0.5D,
                new ItemStack(item, count)
            );
            dropped.setDeltaMovement(Vec3.ZERO);
            dropped.setNeverPickUp();
            helper.getLevel().addFreshEntity(dropped);
            return track(dropped);
        }

        private ServerPlayer connectedPlayer(final BlockPos position) {
            return connectedPlayer(position, GameType.SURVIVAL);
        }

        private ServerPlayer connectedPlayer(final BlockPos position, final GameType mode) {
            final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(mode);
            final net.minecraft.network.Connection connection =
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            final net.minecraft.server.network.CommonListenerCookie cookie =
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(mode);
            final BlockPos absolute = helper.absolutePos(position);
            player.teleportTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
            return track(player);
        }

        private void step(final Runnable stage) {
            if (closed) {
                return;
            }
            try {
                stage.run();
            } catch (final Throwable failure) {
                close();
                throw failure;
            }
        }

        private <T extends Entity> T track(final T entity) {
            entities.add(entity);
            return entity;
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            entities.forEach(entity -> {
                if (entity instanceof ServerPlayer player) {
                    player.getInventory().clearContent();
                    player.removeAllEffects();
                }
                entity.discard();
            });
            entities.clear();
        }
    }

}
