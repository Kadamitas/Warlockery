package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.GoblinPatronRules.Action;
import com.kadamitas.warlockery.entity.GoblinPatronRules.OfferingEvent;
import com.kadamitas.warlockery.entity.GoblinPatronRules.OfferingFact;
import com.kadamitas.warlockery.entity.GoblinPatronRules.ReleaseReason;
import com.kadamitas.warlockery.entity.GoblinPatronRules.RouteFailure;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Defaults, coupling, round trip, corruption, unknown schema, and 1.4 migration for F12 patron
 * state, plus the explicit proof that no canonical constructor here ends a timed phase.
 */
final class GoblinPatronStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final UUID PLAYER = new UUID(3L, 5L);
    private static final UUID CHALLENGER = new UUID(7L, 11L);
    private static final UUID COUNTERPART = new UUID(13L, 17L);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---------------------------------------------------------------- defaults

    @Test
    void anEmptyStateIsAnIdleUnanchoredPatronOfItsOwnKind() {
        final GoblinPatronState state = GoblinPatronState.empty(CreatureKind.FORGEWARDEN);
        assertEquals(GoblinPatronState.SCHEMA_VERSION, state.schemaVersion());
        assertEquals(CreatureKind.FORGEWARDEN, state.kind());
        assertEquals(Action.IDLE, state.combat().action());
        assertEquals(0, state.empowerment().level());
        assertTrue(state.empowerment().facts().isEmpty());
        assertFalse(state.anchor().present());
        assertFalse(state.engagement().open());
        assertFalse(state.accord().present());
        assertFalse(state.published().present());
        assertFalse(state.route().held());
        assertEquals(1, state.merchant().level());
    }

    @Test
    void aForeignKindIsRefusedRatherThanStored() {
        assertEquals(CreatureKind.STONEBROKER, GoblinPatronState.empty(CreatureKind.GOBLIN).kind());
        assertEquals(CreatureKind.STONEBROKER, GoblinPatronState.empty(null).kind());
    }

    // ---------------------------------------------------------------- reconciliation shape

    @Test
    void noConstructorEndsATimedPhaseThatATickBranchOwns() {
        // Timer shape, the defect: an elapsed action must still be reported as its own action so
        // the runtime branch can end it, arm the recovery, and record the completion.
        final GoblinPatronState.Combat elapsed = new GoblinPatronState.Combat(
            Action.FORGE_SURGE, Optional.of(CHALLENGER), 0, 0, 0, Action.IDLE, 0, 0, 0,
            Optional.of(CHALLENGER), Optional.empty(), Optional.empty(), 0, ReleaseReason.NONE,
            false, 0
        );
        assertEquals(Action.FORGE_SURGE, elapsed.action(),
            "the constructor must not clear an action whose commit timer reached zero");
        assertTrue(elapsed.actionElapsed(), "expiry is reported, never applied");
        assertTrue(elapsed.committed());

        final GoblinPatronState.Anchor expiredAnchor =
            new GoblinPatronState.Anchor(Optional.of(BlockPos.ZERO), Optional.of(OVERWORLD), 0);
        assertTrue(expiredAnchor.present(), "an elapsed anchor is still present until a branch releases it");
        assertTrue(expiredAnchor.expired());

        final GoblinPatronState.Engagement elapsedWindow =
            new GoblinPatronState.Engagement(Optional.of(PLAYER), 0, false);
        assertTrue(elapsedWindow.player().isPresent());
        assertTrue(elapsedWindow.windowElapsed());
        assertFalse(elapsedWindow.open());

        final GoblinPatronState.Accord expiredAccord = new GoblinPatronState.Accord(
            Optional.of(COUNTERPART), Optional.of(CreatureKind.FORGEWARDEN), Optional.of(OVERWORLD),
            1L, 2L, 0, Optional.of(CHALLENGER)
        );
        assertTrue(expiredAccord.counterpart().isPresent());
        assertTrue(expiredAccord.expired());
        assertFalse(expiredAccord.present());
    }

    @Test
    void identityCouplingIsEnforcedBecauseDependentFieldsCannotDisagree() {
        // Identity shape, legitimate: a dependent field is meaningless without the thing it
        // depends on, and the type is what should say so.
        final GoblinPatronState.Anchor halfAnchor =
            new GoblinPatronState.Anchor(Optional.of(BlockPos.ZERO), Optional.empty(), 900);
        assertFalse(halfAnchor.present());
        assertEquals(0, halfAnchor.remainingTicks(),
            "an anchor with no dimension keeps no remaining ticks");

        final GoblinPatronState.Accord halfAccord = new GoblinPatronState.Accord(
            Optional.of(COUNTERPART), Optional.empty(), Optional.of(OVERWORLD), 4L, 5L, 300,
            Optional.of(CHALLENGER)
        );
        assertTrue(halfAccord.counterpart().isEmpty());
        assertTrue(halfAccord.sharedChallenger().isEmpty(),
            "an accord with no counterpart kind carries no shared mark");
        assertEquals(0, halfAccord.remainingTicks());

        final GoblinPatronState.Route halfRoute = new GoblinPatronState.Route(
            Optional.of(BlockPos.ZERO), Optional.empty(), 3L, RouteFailure.NO_PATH, 1, 20, false
        );
        assertTrue(halfRoute.destination().isEmpty());
        assertFalse(halfRoute.held());

        final GoblinPatronState.Engagement holderless =
            new GoblinPatronState.Engagement(Optional.empty(), 200, true);
        assertEquals(0, holderless.remainingTicks());
        assertFalse(holderless.breached());
    }

    @Test
    void tickingAdvancesEveryRemainingCounterByExactlyOneLoadedTick() {
        final GoblinPatronState.Combat combat = new GoblinPatronState.Combat(
            Action.LEDGER_VOLLEY, Optional.of(CHALLENGER), 16, 60, 5, Action.IDLE, 80, 40, 0,
            Optional.of(CHALLENGER), Optional.empty(), Optional.empty(), 0, ReleaseReason.NONE,
            false, 3
        ).tick();
        assertEquals(15, combat.tellRemainingTicks());
        assertEquals(59, combat.commitRemainingTicks());
        assertEquals(4, combat.recoveryRemainingTicks());
        assertEquals(79, combat.signatureGapTicks());
        assertEquals(39, combat.secondaryGapTicks());
        assertEquals(3, combat.arrowsRemaining(), "ticking never spends an arrow");
        assertEquals(0, GoblinPatronState.Anchor.none().tick().remainingTicks(),
            "a zero counter never goes negative");
    }

    // ---------------------------------------------------------------- round trip

    @Test
    void everyDurableFieldSurvivesOneRoundTrip() {
        final GoblinPatronState original = GoblinPatronState.empty(CreatureKind.STONEBROKER)
            .withMerchant(new GoblinPatronState.Merchant(4, 180, 1, 900, 6L))
            .withEmpowerment(new GoblinPatronState.Empowerment(3, List.of(
                new OfferingFact(PLAYER, 4, OfferingEvent.TRADED, 5_000)
            )))
            .withAnchor(GoblinPatronState.Anchor.at(new BlockPos(12, 64, -30), OVERWORLD))
            .withEngagement(GoblinPatronState.Engagement.opened(PLAYER, 180))
            .withAccord(GoblinPatronState.Accord.formed(
                COUNTERPART, CreatureKind.FORGEWARDEN, OVERWORLD, 2L, 3L
            ).withSharedChallenger(Optional.of(CHALLENGER)));
        final GoblinPatronState restored = GoblinPatronState.read(
            original.write(), CreatureKind.STONEBROKER, OVERWORLD
        );

        assertEquals(CreatureKind.STONEBROKER, restored.kind());
        assertEquals(4, restored.merchant().level());
        assertEquals(180, restored.merchant().xp());
        assertEquals(1, restored.merchant().restocksToday());
        assertEquals(6L, restored.merchant().restockEpoch());
        assertEquals(3, restored.empowerment().level());
        assertEquals(1, restored.empowerment().facts().size());
        assertEquals(PLAYER, restored.empowerment().facts().getFirst().player());
        assertEquals(4, restored.empowerment().facts().getFirst().standing());
        assertEquals(OfferingEvent.TRADED, restored.empowerment().facts().getFirst().event());
        assertEquals(Optional.of(new BlockPos(12, 64, -30)), restored.anchor().position());
        assertTrue(restored.engagement().open());
        assertEquals(Optional.of(PLAYER), restored.engagement().player());
        assertEquals(Optional.of(COUNTERPART), restored.accord().counterpart());
        assertEquals(Optional.of(CHALLENGER), restored.accord().sharedChallenger());
    }

    @Test
    void aCommittedActionAndItsRouteLeaseAreAlwaysDroppedOnLoad() {
        final GoblinPatronState original = GoblinPatronState.empty(CreatureKind.FORGEWARDEN)
            .withCombat(new GoblinPatronState.Combat(
                Action.FORGE_SURGE, Optional.of(CHALLENGER), 24, 90, 0, Action.HAMMER_COMMIT, 100, 20,
                40, Optional.of(CHALLENGER), Optional.of(CHALLENGER), Optional.of(OVERWORLD), 400,
                ReleaseReason.NONE, true, 3
            ))
            .withRoute(new GoblinPatronState.Route(
                Optional.of(new BlockPos(5, 64, 5)), Optional.of(OVERWORLD), 9L,
                RouteFailure.REJECTED, 2, 20, false
            ));
        final GoblinPatronState restored = GoblinPatronState.read(
            original.write(), CreatureKind.FORGEWARDEN, OVERWORLD
        );
        assertEquals(Action.IDLE, restored.combat().action());
        assertTrue(restored.combat().actionTarget().isEmpty());
        assertEquals(0, restored.combat().tellRemainingTicks());
        assertEquals(0, restored.combat().arrowsRemaining());
        assertEquals(0, restored.combat().stanceRemainingTicks());
        assertFalse(restored.route().held());
        assertEquals(Action.HAMMER_COMMIT, restored.combat().lastCompleted(),
            "the completed-action memory is a durable fact and survives");
        assertEquals(Optional.of(CHALLENGER), restored.combat().challenger());
        assertTrue(restored.combat().withdrawing());
        assertTrue(restored.actionEpoch() > original.actionEpoch(),
            "loading advances the action epoch so no in-flight commit can land");
    }

    @Test
    void crossDimensionAnchorsAccordsAndAttackersDoNotSurviveALoadInAnotherDimension() {
        final GoblinPatronState original = GoblinPatronState.empty(CreatureKind.STONEBROKER)
            .withAnchor(GoblinPatronState.Anchor.at(BlockPos.ZERO, OVERWORLD))
            .withAccord(GoblinPatronState.Accord.formed(
                COUNTERPART, CreatureKind.FORGEWARDEN, OVERWORLD, 1L, 2L
            ))
            .withCombat(new GoblinPatronState.Combat(
                Action.IDLE, Optional.empty(), 0, 0, 0, Action.IDLE, 0, 0, 0, Optional.empty(),
                Optional.of(CHALLENGER), Optional.of(OVERWORLD), 400, ReleaseReason.NONE, false, 0
            ));
        final GoblinPatronState restored = GoblinPatronState.read(
            original.write(), CreatureKind.STONEBROKER, NETHER
        );
        assertFalse(restored.anchor().present());
        assertTrue(restored.accord().counterpart().isEmpty());
        assertTrue(restored.combat().recentAttacker().isEmpty());
        assertEquals(1, restored.merchant().level(), "merchant progression is dimension free");
    }

    // ---------------------------------------------------------------- corruption

    @Test
    void aMissingWrongKindOrUnknownSchemaResetsToASafeIdlePatron() {
        assertEquals(
            GoblinPatronState.empty(CreatureKind.STONEBROKER),
            GoblinPatronState.read(null, CreatureKind.STONEBROKER, OVERWORLD)
        );
        final CompoundTag future = GoblinPatronState.empty(CreatureKind.STONEBROKER).write();
        future.putInt("Version", GoblinPatronState.SCHEMA_VERSION + 7);
        assertEquals(
            GoblinPatronState.empty(CreatureKind.STONEBROKER),
            GoblinPatronState.read(future, CreatureKind.STONEBROKER, OVERWORLD)
        );
        final CompoundTag wrongKind = GoblinPatronState.empty(CreatureKind.STONEBROKER)
            .withEmpowerment(new GoblinPatronState.Empowerment(4, List.of())).write();
        final GoblinPatronState mismatched =
            GoblinPatronState.read(wrongKind, CreatureKind.FORGEWARDEN, OVERWORLD);
        assertEquals(CreatureKind.FORGEWARDEN, mismatched.kind());
        assertEquals(0, mismatched.empowerment().level(),
            "a Stonebroker payload never empowers a Forgewarden");
    }

    @Test
    void malformedFieldsAreClampedAndRepairedTogetherRatherThanOneAtATime() {
        final CompoundTag tag = GoblinPatronState.empty(CreatureKind.FORGEWARDEN).write();
        tag.putInt("Empowerment", 99);
        tag.putInt("Level", 99);
        tag.putInt("Recovery", Integer.MAX_VALUE);
        tag.putInt("SignatureGap", -4_000);
        tag.putInt("AnchorTicks", Integer.MAX_VALUE);
        tag.putString("Challenger", "not-a-uuid");
        tag.putString("CounterpartKind", "not-a-kind");
        tag.putString("Counterpart", COUNTERPART.toString());
        tag.putString("LastAction", "not-an-action");
        final GoblinPatronState restored =
            GoblinPatronState.read(tag, CreatureKind.FORGEWARDEN, OVERWORLD);
        assertEquals(GoblinPatronRules.MAX_EMPOWERMENT, restored.empowerment().level());
        assertEquals(GoblinPatronRules.MAX_MERCHANT_LEVEL, restored.merchant().level());
        assertEquals((int) GoblinPatronRules.FAR_FUTURE_TICKS, restored.combat().recoveryRemainingTicks());
        assertEquals(0, restored.combat().signatureGapTicks());
        assertEquals(0, restored.anchor().remainingTicks(), "an anchorless payload keeps no deadline");
        assertTrue(restored.combat().challenger().isEmpty());
        assertTrue(restored.accord().counterpart().isEmpty(),
            "an unparseable counterpart kind invalidates the whole accord, not just one field");
        assertEquals(Action.IDLE, restored.combat().lastCompleted());
    }

    @Test
    void factsAreCappedAndExpiredOnesAreDroppedOnLoad() {
        final GoblinPatronState.Empowerment overfull = new GoblinPatronState.Empowerment(2, List.of(
            new OfferingFact(new UUID(1L, 1L), 1, OfferingEvent.OFFERED, 100),
            new OfferingFact(new UUID(1L, 2L), 1, OfferingEvent.OFFERED, 0),
            new OfferingFact(new UUID(1L, 3L), 1, OfferingEvent.OFFERED, 100),
            new OfferingFact(new UUID(1L, 4L), 1, OfferingEvent.OFFERED, 100),
            new OfferingFact(new UUID(1L, 5L), 1, OfferingEvent.OFFERED, 100),
            new OfferingFact(new UUID(1L, 6L), 1, OfferingEvent.OFFERED, 100),
            new OfferingFact(new UUID(1L, 7L), 1, OfferingEvent.OFFERED, 100),
            new OfferingFact(new UUID(1L, 8L), 1, OfferingEvent.OFFERED, 100),
            new OfferingFact(new UUID(1L, 9L), 1, OfferingEvent.OFFERED, 100),
            new OfferingFact(new UUID(1L, 10L), 1, OfferingEvent.OFFERED, 100)
        ));
        assertEquals(GoblinPatronRules.MAX_OFFERING_FACTS, overfull.facts().size());
        final GoblinPatronState restored = GoblinPatronState.read(
            GoblinPatronState.empty(CreatureKind.STONEBROKER).withEmpowerment(overfull).write(),
            CreatureKind.STONEBROKER,
            OVERWORLD
        );
        assertTrue(restored.empowerment().facts().size() <= GoblinPatronRules.MAX_OFFERING_FACTS);
        assertTrue(restored.empowerment().facts().stream().noneMatch(OfferingFact::expired),
            "an expired fact is never restored");
    }

    // ---------------------------------------------------------------- route and epochs

    @Test
    void routeFailuresAccumulateIntoTheLongBackoffAndSuccessClearsThem() {
        GoblinPatronState.Route route = new GoblinPatronState.Route(
            Optional.of(new BlockPos(1, 64, 1)), Optional.of(OVERWORLD), 4L,
            RouteFailure.NONE, 0, 0, false
        );
        route = route.failed(RouteFailure.NO_PATH);
        assertEquals(1, route.failureCount());
        assertEquals(GoblinPatronRules.ROUTE_RETRY_TICKS, route.retryRemainingTicks());
        assertTrue(route.held(), "one failure does not clear the destination");
        route = route.failed(RouteFailure.REJECTED).failed(RouteFailure.UNREACHABLE);
        assertEquals(GoblinPatronRules.MAX_ROUTE_FAILURES, route.failureCount());
        assertEquals(GoblinPatronRules.ROUTE_BACKOFF_TICKS, route.retryRemainingTicks());
        assertFalse(route.held(), "the third classified failure clears the destination");
        final GoblinPatronState.Route recovered = route.succeeded();
        assertEquals(0, recovered.failureCount());
        assertEquals(RouteFailure.NONE, recovered.lastFailure());
        assertEquals(0, recovered.retryRemainingTicks());
    }

    @Test
    void releasingLocalStateKeepsProgressionAndDropsEveryDerivedLink() {
        final GoblinPatronState populated = GoblinPatronState.empty(CreatureKind.FORGEWARDEN)
            .withMerchant(new GoblinPatronState.Merchant(5, 400, 2, 100, 3L))
            .withEmpowerment(new GoblinPatronState.Empowerment(4, List.of(
                new OfferingFact(PLAYER, 3, OfferingEvent.OFFERED, 900)
            )))
            .withAnchor(GoblinPatronState.Anchor.at(BlockPos.ZERO, OVERWORLD))
            .withAccord(GoblinPatronState.Accord.formed(
                COUNTERPART, CreatureKind.STONEBROKER, OVERWORLD, 1L, 2L
            ))
            .withPublished(new GoblinPatronState.Published(
                1L, Optional.of(GoblinPatronRules.DirectiveKind.FORGE_WARD), Optional.of(BlockPos.ZERO),
                Optional.of(OVERWORLD), Optional.of(CHALLENGER), 600
            ));
        final GoblinPatronState released = populated.releasedLocalState();
        assertFalse(released.anchor().present());
        assertFalse(released.accord().present());
        assertFalse(released.published().present());
        assertFalse(released.route().held());
        assertEquals(Action.IDLE, released.combat().action());
        assertEquals(5, released.merchant().level(), "merchant progression survives");
        assertEquals(4, released.empowerment().level(), "empowerment survives");
        assertEquals(1, released.empowerment().facts().size(), "bounded facts survive");
        assertTrue(released.authorityEpoch() > populated.authorityEpoch(),
            "a new authority epoch invalidates every derived result at once");
    }

    @Test
    void aPublishedResultIsBoundedExpiringAndCarriesNoNavigation() {
        final GoblinPatronState.Published published = new GoblinPatronState.Published(
            9L, Optional.of(GoblinPatronRules.DirectiveKind.BROKERED_WORK),
            Optional.of(new BlockPos(2, 64, 2)), Optional.of(OVERWORLD), Optional.of(CHALLENGER),
            Integer.MAX_VALUE
        );
        assertEquals(GoblinPatronRules.DIRECTIVE_EXPIRY_TICKS, published.remainingTicks());
        assertTrue(published.present());
        final GoblinPatronState.Published anchorless = new GoblinPatronState.Published(
            9L, Optional.of(GoblinPatronRules.DirectiveKind.BROKERED_WORK), Optional.empty(),
            Optional.of(OVERWORLD), Optional.of(CHALLENGER), 600
        );
        assertTrue(anchorless.resultKind().isEmpty(), "a result with no anchor is no result");
        assertEquals(0, anchorless.resultEpoch());
    }

    // ---------------------------------------------------------------- migration and size

    @Test
    void aPreF12PatronMigratesItsEmpowermentAndMerchantExperienceAndInventsNothing() {
        final GoblinPatronState migrated =
            GoblinPatronState.migrateLegacy(CreatureKind.FORGEWARDEN, 3, 150);
        assertEquals(CreatureKind.FORGEWARDEN, migrated.kind());
        assertEquals(3, migrated.empowerment().level());
        assertEquals(150, migrated.merchant().xp());
        assertEquals(GoblinPatronRules.levelForXp(150), migrated.merchant().level());
        assertTrue(migrated.empowerment().facts().isEmpty());
        assertFalse(migrated.anchor().present());
        assertFalse(migrated.accord().present());
        assertEquals(Action.IDLE, migrated.combat().action());
        assertEquals(GoblinPatronRules.MAX_EMPOWERMENT,
            GoblinPatronState.migrateLegacy(CreatureKind.STONEBROKER, 99, 0).empowerment().level());
    }

    @Test
    void aFullyPopulatedStateEncodesBelowTheDeclaredByteCeiling() {
        GoblinPatronState state = GoblinPatronState.empty(CreatureKind.STONEBROKER)
            .withMerchant(new GoblinPatronState.Merchant(5, 999, 2, 2_400, 12L))
            .withAnchor(GoblinPatronState.Anchor.at(new BlockPos(-1_000, 200, 1_000), OVERWORLD))
            .withEngagement(GoblinPatronState.Engagement.opened(PLAYER, 200))
            .withAccord(GoblinPatronState.Accord.formed(
                COUNTERPART, CreatureKind.FORGEWARDEN, OVERWORLD, 400L, 401L
            ).withSharedChallenger(Optional.of(CHALLENGER)))
            .withPublished(new GoblinPatronState.Published(
                77L, Optional.of(GoblinPatronRules.DirectiveKind.BROKERED_WORK),
                Optional.of(new BlockPos(-1_000, 200, 1_000)), Optional.of(OVERWORLD),
                Optional.of(CHALLENGER), 1_200
            ))
            .withRoute(new GoblinPatronState.Route(
                Optional.of(new BlockPos(999, 250, -999)), Optional.of(OVERWORLD), 88L,
                RouteFailure.STUCK, 3, 100, true
            ));
        List<OfferingFact> facts = List.of();
        for (int index = 0; index < GoblinPatronRules.MAX_OFFERING_FACTS; index++) {
            facts = GoblinPatronRules.recordFact(facts, new UUID(index, ~index), OfferingEvent.OFFERED);
        }
        state = state.withEmpowerment(new GoblinPatronState.Empowerment(5, facts));
        final int encoded = encode(state.write()).length;
        assertTrue(encoded < GoblinPatronRules.MAX_STATE_BYTES,
            "a populated patron state must stay below its declared ceiling, was " + encoded);
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
}
