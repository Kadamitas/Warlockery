package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.ritual.hex.HexKind;
import com.kadamitas.warlockery.ritual.hex.HexState;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class RitualOutcomeGameTests {
    /**
     * A rite that needs no altar, so a complete site is one circle centre and one small chalk ring.
     */
    private static final String PORTABLE_BINDING_RITE = "bind_waystone_portable";
    private static final BlockPos SITE_CENTER = new BlockPos(9, 2, 9);
    /**
     * Clear of the circle and its three block ring, and inside the region a GameTest arena keeps ticking.
     *
     * <p>An arena is planted at a world position that changes every run, so an offset large enough to reach a
     * second chunk lands inside the ticking region or outside it depending on where in its chunk the arena
     * happened to fall. This altar had been placed twenty blocks out: its chunk was always loaded, so the
     * block entity was always found, but on the runs where it was not ticking the altar was never inspected,
     * came up invalid and held nothing. Every relative coordinate this altar occupies stays under sixteen, so
     * it can never be more than one chunk from the arena whatever the alignment.</p>
     */
    private static final BlockPos ALTAR_ORIGIN = new BlockPos(13, 2, 13);
    /**
     * Long enough for the altar to come up valid and gather several rounds of recharge, so a refund and a
     * settlement can be told apart by more than one unit of power.
     */
    private static final int ALTAR_WARMUP_TICKS = 165;

    private RitualOutcomeGameTests() {
    }

    /**
     * Lays the circle centre and the small ring that {@code bind_waystone_portable} declares, so every
     * requirement surviving the cast is satisfied and a started session runs to term.
     */
    private static BlockPos preparePortableBindingSite(final GameTestHelper helper) {
        placeSupported(helper, SITE_CENTER, ModBlocks.ALL.get("circle").get());
        ChalkCircleLayout.Size.SMALL.offsets().forEach(offset ->
            placeSupported(helper, SITE_CENTER.offset(offset), ModBlocks.ALL.get("circleglyphritual").get())
        );
        return helper.absolutePos(SITE_CENTER);
    }

    private static void placeSupported(
        final GameTestHelper helper,
        final BlockPos position,
        final net.minecraft.world.level.block.Block block
    ) {
        helper.setBlock(position.below(), Blocks.STONE);
        helper.setBlock(position, block);
    }

    public static void aHexOnlyReachesVictimsInsideTheDeclaredRadius(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final Zombie inside = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 2));
        inside.setNoAi(true);

        RitualManager.INSTANCE.complete(
            helper.getLevel(),
            center,
            null,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "hex_misfortune"),
            0
        );

        helper.assertTrue(
            HexState.isActive(inside, HexKind.MISFORTUNE),
            "a hex must reach an unbound victim standing inside the circle radius"
        );
        helper.succeed();
    }

    public static void aBoundTargetInAnotherDimensionIsNotReachedByAHex(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final Zombie nearby = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 2));
        nearby.setNoAi(true);

        final ItemStack vial = new ItemStack(ModItems.ALL.get("sympathetic_vial").get());
        new SympatheticBinding(java.util.UUID.randomUUID(), "AbsentTarget", "player").write(vial);
        helper.getLevel().addFreshEntity(new ItemEntity(
            helper.getLevel(),
            center.getX() + 0.5,
            center.getY() + 1.0,
            center.getZ() + 0.5,
            vial
        ));

        RitualManager.INSTANCE.complete(
            helper.getLevel(),
            center,
            null,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "hex_misfortune"),
            0
        );

        helper.assertTrue(
            HexState.isActive(nearby, HexKind.MISFORTUNE),
            "an unresolvable binding must fall back to the radius sweep rather than hexing nothing"
        );
        helper.succeed();
    }

    public static void aRitualWithNothingToActOnReportsNoEffect(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final List<ItemEntity> before = helper.getLevel().getEntitiesOfClass(
            ItemEntity.class,
            new AABB(center).inflate(RitualManager.OFFERING_RADIUS)
        );
        helper.assertTrue(before.isEmpty(), "the test site must start with no offerings");

        RitualManager.INSTANCE.complete(
            helper.getLevel(),
            center,
            null,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "bind_waystone"),
            0
        );

        helper.assertTrue(
            helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                new AABB(center).inflate(RitualManager.OFFERING_RADIUS)
            ).isEmpty(),
            "a binding ritual with no waystone present must not fabricate an item"
        );
        helper.succeed();
    }

    public static void aFailingTerminalEffectDoesNotStrandTheSession(final GameTestHelper helper) {
        final BlockPos center = preparePortableBindingSite(helper);
        final RitualSessionData sessions = RitualSessionData.get(helper.getLevel());
        helper.assertTrue(
            sessions.start(
                center,
                Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, PORTABLE_BINDING_RITE),
                java.util.UUID.randomUUID(),
                1,
                0
            ),
            "the prepared circle must accept a ritual session"
        );

        final int[] terminalRuns = new int[1];
        sessions.tick(helper.getLevel(), (level, site, caster, ritual, variant) -> {
            terminalRuns[0]++;
            throw new IllegalStateException("terminal effect refused to finish");
        });

        helper.assertValueEqual(terminalRuns[0], 1, "the finished cast must reach its terminal effect once");
        helper.assertFalse(
            sessions.isActive(center),
            "a terminal effect that fails must still leave the finished session removed"
        );

        sessions.tick(helper.getLevel(), (level, site, caster, ritual, variant) -> terminalRuns[0]++);
        helper.assertValueEqual(terminalRuns[0], 1, "a failed terminal effect must not be replayed on later ticks");
        helper.succeed();
    }

    public static void aPresenceOnlyMobIsNeverEatenByAConsumingRequirement(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final List<Mob> present = List.of(
            spawnStill(helper, EntityTypes.HUSK, new BlockPos(1, 1, 0)),
            spawnStill(helper, EntityTypes.HUSK, new BlockPos(0, 1, 1)),
            spawnStill(helper, EntityTypes.HUSK, new BlockPos(2, 1, 1))
        );
        final Mob offering = spawnStill(helper, EntityTypes.ZOMBIE, new BlockPos(1, 1, 2));
        final List<RitualDefinition.EntityRequirement> requirements = List.of(
            new RitualDefinition.EntityRequirement("minecraft:husk", 3, false),
            new RitualDefinition.EntityRequirement("#minecraft:zombies", 1, true)
        );

        helper.assertTrue(
            RitualManager.inspectEntityRequirements(helper.getLevel(), center, requirements).stream()
                .allMatch(RitualManager.RequirementStatus::met),
            "the site must satisfy both entity requirements before anything is consumed"
        );

        RitualManager.consumeEntityRequirements(helper.getLevel(), center, requirements);

        present.forEach(husk -> helper.assertTrue(
            husk.isAlive(),
            "a mob held only as a presence requirement must survive the rite that declared it"
        ));
        helper.assertFalse(offering.isAlive(), "the consuming requirement must still take its own offering");
        helper.succeed();
    }

    private static Mob spawnStill(
        final GameTestHelper helper,
        final net.minecraft.world.entity.EntityType<? extends Mob> type,
        final BlockPos position
    ) {
        final Mob mob = helper.spawn(type, position);
        mob.setNoAi(true);
        return mob;
    }

    public static void escrowedPowerIsReturnedExactlyOnceAndSettledOnlyOnce(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(ALTAR_ORIGIN, ALTAR_ORIGIN.offset(2, 0, 1))
            .forEach(position -> helper.setBlock(position, ModBlocks.ALTAR.get()));
        final BlockPos altarPos = helper.absolutePos(ALTAR_ORIGIN);
        final BlockPos circle = preparePortableBindingSite(helper);
        final BlockPos bareGround = helper.absolutePos(new BlockPos(1, 1, 1));

        helper.runAfterDelay(ALTAR_WARMUP_TICKS, () -> {
            final ServerLevel level = helper.getLevel();
            final AltarBlockEntity altar = level.getBlockEntity(altarPos) instanceof AltarBlockEntity found
                ? found
                : null;
            helper.assertTrue(altar != null && altar.isMultiblockValid(), "the six block altar must come up valid");
            final int held = altar.getPower();
            helper.assertTrue(held >= 2, "the altar must have gathered power to lend, held " + held);

            final RitualSessionData sessions = RitualSessionData.get(level);
            final Identifier rite = Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, PORTABLE_BINDING_RITE);
            final int[] terminalRuns = new int[1];

            helper.assertTrue(altar.escrowPower(held), "the altar must be able to promise what it holds");
            helper.assertValueEqual(altar.getPower(), held, "altar power after a promise is made");
            helper.assertValueEqual(altar.availablePower(), 0, "spendable power while a promise stands");
            helper.assertTrue(
                sessions.start(bareGround, rite, java.util.UUID.randomUUID(), 200, 0, altarPos, held),
                "the session must record what the altar promised it"
            );

            // Bare ground is no circle, so the first sweep cancels this cast and later sweeps find nothing to
            // cancel. Five sweeps because a refund that credited power back instead of releasing a promise
            // would keep paying out on every one of them.
            IntStream.range(0, 5).forEach(_ -> sessions.tick(level, (_, _, _, _, _) -> terminalRuns[0]++));
            helper.assertFalse(sessions.isActive(bareGround), "the cancelled cast must be gone");
            helper.assertValueEqual(terminalRuns[0], 0, "terminal effects run for a cancelled cast");
            helper.assertValueEqual(altar.getEscrowedPower(), 0, "power still promised after the refund");
            helper.assertValueEqual(altar.getPower(), held, "altar power after the refund");
            helper.assertValueEqual(altar.availablePower(), held, "spendable power after the refund");

            final int owed = held / 2;
            helper.assertTrue(altar.escrowPower(owed), "the altar must be able to promise a second cast");
            helper.assertTrue(
                sessions.start(circle, rite, java.util.UUID.randomUUID(), 1, 0, altarPos, owed),
                "the prepared circle must accept a session"
            );
            IntStream.range(0, 5).forEach(_ -> sessions.tick(level, (_, _, _, _, _) -> terminalRuns[0]++));
            helper.assertValueEqual(terminalRuns[0], 1, "a finished cast must reach its terminal effect once");
            helper.assertValueEqual(altar.getEscrowedPower(), 0, "power still promised after settlement");
            helper.assertValueEqual(altar.getPower(), held - owed, "altar power after settlement");
            helper.succeed();
        });
    }

    public static void aLapsedCastNamesTheRequirementThatEndedIt(final GameTestHelper helper) {
        final BlockPos center = preparePortableBindingSite(helper);
        final Identifier rite = Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, PORTABLE_BINDING_RITE);
        helper.assertTrue(
            RitualManager.INSTANCE.castingObstacles(helper.getLevel(), center, rite, 0, null).isEmpty(),
            "a complete site must report nothing standing in the way"
        );

        helper.setBlock(SITE_CENTER.offset(ChalkCircleLayout.Size.SMALL.offsets().getFirst()), Blocks.AIR);

        final List<RitualManager.RequirementStatus> obstacles =
            RitualManager.INSTANCE.castingObstacles(helper.getLevel(), center, rite, 0, null);
        helper.assertValueEqual(obstacles.size(), 1, "requirements reported as lapsed");
        helper.assertValueEqual(obstacles.getFirst().label(), "circleglyphritual", "the lapsed requirement");
        final String namedRequirement = RitualRequirementText.label(obstacles.getFirst()).getString();
        helper.assertTrue(
            RitualRequirementText.summary(obstacles).orElseThrow().getString().contains(namedRequirement),
            "the notice the caster receives must name the ring that lapsed"
        );
        helper.succeed();
    }

    public static void anAbsentCasterBlocksTheRitesThatActThroughOne(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        for (final String rite : List.of("call_familiar", "marriage", "divorce", "hex_wolf", "corrupt_doll")) {
            final RitualDefinition definition = RitualManager.INSTANCE
                .byId(Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, rite))
                .orElseThrow()
                .definition();
            final RitualManager.RequirementStatus status = RitualManager
                .actionEnvironmentRequirement(definition, helper.getLevel(), center, null)
                .orElseThrow(() -> new AssertionError(rite + " drops its condition when no caster is present"));
            helper.assertFalse(status.met(), rite + " must report an unmet condition when its caster is absent");
            helper.assertTrue(
                status.blocksActivation(),
                rite + " must classify the absent caster as blocking, so a cast cannot survive it"
            );
        }
        helper.succeed();
    }

    public static void aCancelledCastReachesACasterOutsideItsLevel(final GameTestHelper helper) {
        final ServerPlayer caster = connectedSurvivalPlayer(helper);
        final ServerLevel elsewhere = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(
            elsewhere != null && elsewhere != helper.getLevel(),
            "this test needs a second level to stand in for the one the caster walked into"
        );
        helper.assertTrue(
            elsewhere.getPlayerByUUID(caster.getUUID()) == null,
            "the control for this test requires the caster to be absent from the level running the sweep"
        );

        helper.assertTrue(
            RitualSessionData.onlineCaster(elsewhere, caster.getStringUUID())
                .filter(found -> found.getUUID().equals(caster.getUUID()))
                .isPresent(),
            "a caster who left the level is still online and must still be told their rite collapsed"
        );
        helper.assertTrue(
            RitualSessionData.onlineCaster(elsewhere, java.util.UUID.randomUUID().toString()).isEmpty(),
            "a caster who is genuinely gone must resolve to nobody"
        );
        helper.succeed();
    }

    private static ServerPlayer connectedSurvivalPlayer(final GameTestHelper helper) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.SURVIVAL);
        final BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        player.teleportTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        return GameTestMockPlayers.autoDisconnect(helper, player);
    }

    public static void everyLoadedRitualPassesTargetValidation(final GameTestHelper helper) {
        RitualManager.INSTANCE.all().forEach(entry -> {
            final List<String> problems = RitualManager.problems(entry.definition());
            helper.assertTrue(
                problems.isEmpty(),
                entry.id() + " survived load with problems " + problems
            );
        });
        helper.assertFalse(RitualManager.INSTANCE.all().isEmpty(), "the ritual catalog must be loaded");
        helper.succeed();
    }
}
