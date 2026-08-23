package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.DeathRules.Phase;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.util.GameTestAssertions;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Six bounded live F18 fixtures. Every fixture asserts through spawned entities and the real
 * {@link DeathRuntime} decision path, cleans up all created entities and blocks in
 * {@code finally} including mid-sequence stages, and uses exact counter and phase assertions
 * instead of elapsed-time guesses.
 *
 * <p>Arena geometry: the framework seals the {@code warlockery:empty3x3x3} cell in a barrier shell, so
 * every entity starts inside relative 0..2 at y=1 and every computed destination is a live entity
 * already inside that cell. The one fixture that needs a genuinely unreachable route opens the
 * framework shell inside its own pass-local arena and restores every block on close in reverse
 * order so the framework shell ends byte-identical.</p>
 */
public final class DeathGameTests {
    private DeathGameTests() {
    }

    public static void deathAppointmentTelegraphsAndReapsOnce(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final DeathEntity death = spawnDeath(fixture, new BlockPos(0, 1, 0));
            death.setNoAi(true);
            final ServerPlayer subject = fixture.connectedPlayer(new BlockPos(2, 1, 0), GameType.SURVIVAL);
            makeDue(helper, death);
            DeathRuntime.tick(death, helper.getLevel());
            GameTestAssertions.assertPresentValueEqual(
                helper, death.deathState().appointment().subject(),
                subject.getUUID(), "the one loaded survival player is the single appointed subject");
            helper.assertTrue(death.getTarget() == null,
                "the appointed subject is never written to Mob.target");
            helper.assertTrue(death.deathCounters().appointments() == 1L,
                "exactly one appointment is made");

            DeathRuntime.tick(death, helper.getLevel());
            helper.assertValueEqual(death.deathState().phase(), Phase.TELEGRAPH,
                "reaching the subject begins the clear finite telegraph");
            helper.assertTrue(death.deathCounters().reapAttempts() == 0L,
                "no attempt happens before the telegraph elapses");

            for (int tick = 0; tick <= DeathRules.TELEGRAPH_TICKS + 2; tick++) {
                makeDue(helper, death);
                DeathRuntime.tick(death, helper.getLevel());
            }
            helper.assertTrue(death.deathCounters().reapAttempts() == 1L,
                "the telegraph resolves into exactly one call to the primary melee path");
            helper.assertTrue(death.deathState().appointment().reaped(),
                "the completed attempt is recorded");
            helper.assertValueEqual(death.deathState().phase(), Phase.RECOVER,
                "the attempt is followed by the bounded recovery");

            for (int tick = 0; tick <= DeathRules.RECOVER_TICKS + 2; tick++) {
                makeDue(helper, death);
                DeathRuntime.tick(death, helper.getLevel());
            }
            helper.assertTrue(death.deathCounters().reapAttempts() == 1L,
                "recovery never produces a second or catch-up strike");
            helper.assertTrue(death.deathState().appointment().subject().isEmpty(),
                "the recovery releases the appointment");
            helper.assertTrue(death.deathCounters().releases() >= 1L, "release work is counted");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void deathCompleteDisguiseReleasesAppointment(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final DeathEntity death = spawnDeath(fixture, new BlockPos(0, 1, 0));
            death.setNoAi(true);
            final ServerPlayer subject = fixture.connectedPlayer(new BlockPos(2, 1, 0), GameType.SURVIVAL);
            makeDue(helper, death);
            DeathRuntime.tick(death, helper.getLevel());
            GameTestAssertions.assertPresentValueEqual(
                helper, death.deathState().appointment().subject(),
                subject.getUUID(), "precondition: an undisguised survival player is appointed");
            helper.assertTrue(death.canAttack(subject),
                "precondition: an undisguised subject is an ordinary legal target");

            wearCompleteDisguise(subject);
            helper.assertTrue(DeathImpersonationRules.isComplete(subject),
                "precondition: the exact preserved four-piece disguise is complete");
            helper.assertTrue(!death.canAttack(subject),
                "complete-disguise pacification is preserved exactly");
            makeDue(helper, death);
            DeathRuntime.tick(death, helper.getLevel());
            helper.assertTrue(death.deathState().appointment().subject().isEmpty(),
                "a fully disguised subject releases the appointment");
            helper.assertTrue(death.deathCounters().reapAttempts() == 0L,
                "no attempt is ever made against a disguised player");
            helper.assertTrue(death.deathState().cadence().reappointCooldownTicks() > 0,
                "the release starts the reappointment backoff");

            final long appointmentsBefore = death.deathCounters().appointments();
            for (int tick = 0; tick <= DeathRules.REAPPOINT_COOLDOWN_TICKS + 2; tick++) {
                makeDue(helper, death);
                DeathRuntime.tick(death, helper.getLevel());
            }
            helper.assertTrue(death.deathCounters().appointments() == appointmentsBefore,
                "a nearby completely disguised player suppresses every later acquisition scan");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void deathBlockedRouteReleasesAfterThreeFailures(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            openFrameworkShell(fixture);
            erectArenaShell(fixture);
            final DeathEntity death = spawnDeath(fixture, new BlockPos(-3, 1, 1));
            death.setNoAi(true);
            final ServerPlayer unreachable = fixture.connectedPlayer(new BlockPos(1, 1, 1), GameType.SURVIVAL);
            makeDue(helper, death);
            DeathRuntime.tick(death, helper.getLevel());
            GameTestAssertions.assertPresentValueEqual(
                helper, death.deathState().appointment().subject(),
                unreachable.getUUID(), "precondition: the subject is appointed");
            // Sealing has to follow acquisition: the same barriers that make every route fail also
            // break the line of sight the bounded scan needs, so sealing first would appoint nobody
            // and the fixture would prove nothing about route failure.
            sealSubject(fixture, new BlockPos(1, 1, 1));

            for (int attempt = 0; attempt < DeathRules.MAX_ROUTE_FAILURES + 1; attempt++) {
                makeDue(helper, death);
                DeathRuntime.tick(death, helper.getLevel());
            }
            helper.assertTrue(death.deathCounters().navigationRequests() <= DeathRules.MAX_ROUTE_FAILURES,
                "route attempts stop at the third failure instead of retrying every tick");
            helper.assertTrue(death.deathState().appointment().subject().isEmpty(),
                "the third route failure releases the appointment");
            helper.assertTrue(death.deathState().cadence().routeFailures() == 0,
                "the release resets the failure counter after it was observed");
            helper.assertTrue(death.getNavigation().isDone(),
                "the release stops navigation instead of leaving a stale path");
            helper.assertTrue(death.deathCounters().reapAttempts() == 0L,
                "an unreachable subject is never reached by a fallback or a teleport");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void deathReapRespectsVanillaProtectionAndAttribution(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final DeathEntity death = spawnDeath(fixture, new BlockPos(0, 1, 0));
            death.setNoAi(true);
            final ServerPlayer creative = fixture.connectedPlayer(new BlockPos(0, 1, 2), GameType.CREATIVE);
            final ServerPlayer subject = fixture.connectedPlayer(new BlockPos(2, 1, 0), GameType.SURVIVAL);
            final float creativeHealth = creative.getHealth();
            final float subjectHealth = subject.getHealth();
            makeDue(helper, death);
            DeathRuntime.tick(death, helper.getLevel());
            GameTestAssertions.assertPresentValueEqual(
                helper, death.deathState().appointment().subject(),
                subject.getUUID(), "a creative player is never eligible for an appointment");
            for (int tick = 0; tick <= DeathRules.TELEGRAPH_TICKS + 3; tick++) {
                makeDue(helper, death);
                DeathRuntime.tick(death, helper.getLevel());
            }
            helper.assertTrue(death.deathCounters().reapAttempts() == 1L,
                "precondition: exactly one attempt was made");
            helper.assertValueEqual(creative.getHealth(), creativeHealth,
                "the creative bystander is untouched");
            helper.assertTrue(subject.getHealth() < subjectHealth,
                "the attempt lands through the ordinary vanilla damage path");
            // The preserved shape is a floor, not a cap: the base attack attribute is topped up to
            // fifteen percent of the victim's maximum health only when that fraction is larger, so
            // an ordinary twenty-health player takes the base attribute and never the two summed.
            final float meleeCeiling = Math.max(
                (float) death.getAttributeValue(Attributes.ATTACK_DAMAGE),
                DeathCombatRules.meleeDamage(subject.getMaxHealth())
            );
            helper.assertTrue(subject.getHealth() >= subjectHealth - meleeCeiling,
                "the attempt never exceeds the preserved melee shape, and never adds the "
                    + "fifteen percent top-up on top of the full attack attribute");
            helper.assertTrue(subject.getLastDamageSource() != null
                    && subject.getLastDamageSource().getEntity() == death,
                "attribution names the Death entity, never an anonymous or hidden source");
            helper.assertTrue(subject.hasEffect(net.minecraft.world.effect.MobEffects.WITHER),
                "the preserved 120-tick amplifier-one Wither rider is applied by the successful hit");
            helper.assertTrue(subject.isAlive(),
                "Death alters no player death mechanic, drop, or respawn");

            final float before = death.getHealth();
            death.hurtServer(helper.getLevel(),
                helper.getLevel().damageSources().playerAttack(subject), 200.0F);
            helper.assertTrue(before - death.getHealth() <= DeathCombatRules.MAX_INCOMING_DAMAGE,
                "the preserved fifteen-damage incoming cap still holds");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void deathReloadDoesNotReplayReap(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final DeathEntity death = spawnDeath(fixture, new BlockPos(0, 1, 0));
            death.setNoAi(true);
            final ServerPlayer subject = fixture.connectedPlayer(new BlockPos(2, 1, 0), GameType.SURVIVAL);
            makeDue(helper, death);
            DeathRuntime.tick(death, helper.getLevel());
            for (int tick = 0; tick <= DeathRules.TELEGRAPH_TICKS + 3; tick++) {
                makeDue(helper, death);
                DeathRuntime.tick(death, helper.getLevel());
            }
            helper.assertTrue(death.deathCounters().reapAttempts() == 1L,
                "precondition: exactly one attempt was made before the reload");

            final CompoundTag saved = saveEntity(helper, death);
            final float healthAfterAttempt = subject.getHealth();
            final DeathEntity reloaded = spawnDeath(fixture, new BlockPos(0, 1, 0));
            reloaded.setNoAi(true);
            loadEntity(helper, reloaded, saved);
            helper.assertTrue(reloaded.deathState().appointment().reaped(),
                "the completed attempt survives the reload");
            helper.assertTrue(reloaded.deathState().phase() != Phase.REAP,
                "a reload never re-enters the reaping phase");
            for (int tick = 0; tick <= DeathRules.TELEGRAPH_TICKS + DeathRules.RECOVER_TICKS + 3; tick++) {
                makeDue(helper, reloaded);
                DeathRuntime.tick(reloaded, helper.getLevel());
            }
            helper.assertTrue(reloaded.deathCounters().reapAttempts() == 0L,
                "the reloaded Death never replays the completed attempt");
            helper.assertValueEqual(subject.getHealth(), healthAfterAttempt,
                "no second hit reaches the subject across the reload");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void deathHazardAndOtherFamiliesRemainIsolated(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final DeathEntity death = spawnDeath(fixture, new BlockPos(0, 1, 0));
            death.setNoAi(true);
            final Entity zombie = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2),
                EntitySpawnReason.EVENT);
            @SuppressWarnings("unchecked")
            final EntityType<CorpseEntity> corpseType =
                (EntityType<CorpseEntity>) ModEntities.ALL.get("corpse").get();
            final CorpseEntity corpse = fixture.spawn(corpseType, new BlockPos(2, 1, 0),
                EntitySpawnReason.EVENT);
            corpse.setNoAi(true);
            final CorpseState corpseStateBefore = corpse.corpseState();

            makeDue(helper, death);
            DeathRuntime.tick(death, helper.getLevel());
            helper.assertTrue(death.deathState().appointment().subject().isEmpty(),
                "only players are ever appointed; no mob of any family becomes a subject");

            final BlockPos hazard = helper.absolutePos(new BlockPos(0, 1, 0));
            // The empty cell has no interior floor, so fire placed straight into it cannot survive
            // its own placement update. One sturdy support inside the cell, restored on close.
            fixture.placeBlock(hazard.below(), Blocks.STONE);
            fixture.placeBlock(hazard, Blocks.FIRE);
            makeDue(helper, death);
            DeathRuntime.tick(death, helper.getLevel());
            helper.assertTrue(death.deathCounters().hazardInterruptions() >= 1L,
                "an escapable hazard preempts every Death activity");
            helper.assertTrue(death.deathCounters().reapAttempts() == 0L,
                "hazard escape never produces an attack");
            helper.assertValueEqual(corpse.corpseState(), corpseStateBefore,
                "no neighbouring family state is read, written, alerted, or converted");
            helper.assertTrue(zombie.isAlive() && corpse.isAlive(),
                "Death raises, eats, commands, and converts nothing");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    // ---------------------------------------------------------------- fixture helpers

    /**
     * Clears exactly the cadences that gate the next decision. The per-level quota is reset too:
     * it rolls over on the level clock, which a directly dispatched decision loop never advances,
     * so without this a fixture would spend one tick's quota across its whole run.
     */
    private static void makeDue(final GameTestHelper helper, final DeathEntity death) {
        // The one-shot load reconciliation re-seeds the discovery stagger from inside the first
        // tick, so it has to be settled before these cadences are cleared rather than after.
        DeathRuntime.reconcileForFixture(death, helper.getLevel());
        final DeathRuntime.TransientState scratch = death.deathTransient();
        scratch.pathCooldownTicks = 0;
        scratch.discoveryCooldownTicks = 0;
        // The fixture claims the vigil-healing cadence so exact health assertions are not
        // polluted by the preserved one health per twenty loaded ticks.
        scratch.healCooldownTicks = DeathRules.VIGIL_HEAL_INTERVAL_TICKS;
        DeathRuntime.resetLevelBudget(helper.getLevel());
    }

    private static DeathEntity spawnDeath(final FixtureScope fixture, final BlockPos position) {
        final GameTestHelper helper = fixture.helper;
        @SuppressWarnings("unchecked")
        final EntityType<DeathEntity> type =
            (EntityType<DeathEntity>) ModEntities.ALL.get("death").get();
        final DeathEntity death = fixture.spawn(type, position, EntitySpawnReason.EVENT);
        death.setDeltaMovement(Vec3.ZERO);
        final BlockPos absolute = helper.absolutePos(position);
        death.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        death.normalizeIdentity();
        return death;
    }

    private static void wearCompleteDisguise(final ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.ALL.get("deathscowl").get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.ALL.get("deathsrobe").get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.ALL.get("deathsfeet").get()));
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.ALL.get("deathshand").get()));
    }

    private static CompoundTag saveEntity(final GameTestHelper helper, final DeathEntity death) {
        final TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
        );
        death.saveWithoutId(output);
        return output.buildResult().copy();
    }

    private static void loadEntity(
        final GameTestHelper helper,
        final DeathEntity death,
        final CompoundTag saved
    ) {
        death.load(TagValueInput.create(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved
        ));
    }

    /**
     * Opens the framework's own barrier shell around the {@code warlockery:empty3x3x3} cell (the box
     * faces at relative -1 and 3, floor excluded) so the fixture's arena is one connected space.
     * Every removed barrier is restored on close, after the arena shell is removed.
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
     * Pass-local arena shell: radius five around the cell center with a full floor and ceiling
     * cap, so every route stays inside this fixture on the batch grid. Only previously-air
     * positions are placed and all are restored on close.
     */
    private static void erectArenaShell(final FixtureScope fixture) {
        final GameTestHelper helper = fixture.helper;
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final int radius = 5;
        final int height = 6;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                final boolean wall = Math.abs(dx) == radius || Math.abs(dz) == radius;
                for (int dy = -1; dy <= height; dy++) {
                    final boolean cap = dy == -1 || dy == height;
                    if (!wall && !cap) {
                        continue;
                    }
                    fixture.placeBlock(
                        new BlockPos(center.getX() + dx, center.getY() + dy, center.getZ() + dz),
                        Blocks.BARRIER
                    );
                }
            }
        }
    }

    /** Seals the subject inside a barrier box so no route to it can ever be found. */
    private static void sealSubject(final FixtureScope fixture, final BlockPos relative) {
        final BlockPos center = fixture.helper.absolutePos(relative);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0 && dy >= 0 && dy <= 1) {
                        continue;
                    }
                    fixture.placeBlock(
                        new BlockPos(center.getX() + dx, center.getY() + dy, center.getZ() + dz),
                        Blocks.BARRIER
                    );
                }
            }
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
            // ServerPlayer.isInvulnerableTo ends in !connection.hasClientLoaded(), so a player
            // stays invulnerable to everything until its client reports that it finished loading.
            // A mock player on an embedded channel never sends that packet on its own, so without
            // this every hit is silently refused and a damage assertion would prove nothing. This
            // delivers the genuine signal through the real handler rather than faking the flag.
            player.connection.handleAcceptPlayerLoad(
                new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            final BlockPos absolute = helper.absolutePos(position);
            player.teleportTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
            return track(player);
        }

        /** Places one block only where the level was air and restores that air on close. */
        private void placeBlock(final BlockPos absolute, final net.minecraft.world.level.block.Block block) {
            if (!helper.getLevel().getBlockState(absolute).isAir()) {
                return;
            }
            helper.getLevel().setBlock(absolute, block.defaultBlockState(), 3);
            onClose(() -> helper.getLevel().setBlock(absolute, Blocks.AIR.defaultBlockState(), 3));
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
            // Reverse order: later block edits are undone before earlier ones are restored, so
            // overlapping edits end exactly where the framework left them.
            for (int index = cleanupActions.size() - 1; index >= 0; index--) {
                cleanupActions.get(index).run();
            }
            cleanupActions.clear();
        }
    }
}
