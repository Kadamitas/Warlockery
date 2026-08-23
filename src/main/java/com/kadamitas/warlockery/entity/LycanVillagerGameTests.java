package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.util.GameTestWorldClock;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;

public final class LycanVillagerGameTests {
    private LycanVillagerGameTests() { }

    public static void brainRoutineResumesAfterWatch(final GameTestHelper h) { withLycan(h, lycan -> {
        h.assertValueEqual(lycan.sentinelState().intent(), LycanVillagerRules.Intent.ROUTINE, "return must release the Brain");
    }, lycan -> lycan.setSentinelState(lycan.sentinelState().withIntent(
        LycanVillagerRules.Intent.RETURN, h.getLevel().getGameTime()))); }
    public static void signatureOffersSurviveProfessionAndReload(final GameTestHelper h) { withLycan(h, lycan -> {
        lycan.setVillagerData(lycan.getVillagerData().withProfession(h.getLevel().registryAccess(), VillagerProfession.FARMER));
        final MerchantOffer ordinary = new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD), 4, 1, 0.05F);
        lycan.getOffers().add(0, ordinary);
        h.assertTrue(lycan.getOffers().contains(ordinary), "profession reconciliation preserves the ordinary offer object and its state");
        final TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        lycan.saveWithoutId(output);
        final LycanVillagerEntity loaded = (LycanVillagerEntity) ModEntities.ALL.get("lycan_villager").get()
            .create(h.getLevel(), EntitySpawnReason.LOAD);
        h.assertTrue(loaded != null, "registered Lycan must reload");
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult().copy()));
        for (final var signature : LycanVillagerEntity.signatureOffers()) {
            h.assertValueEqual(loaded.getOffers().stream().filter(offer -> LycanVillagerEntity.sameOffer(offer, signature)).count(),
                1L, "profession and entity reload retain exactly one complete signature tuple");
        }
        h.assertTrue(loaded.getOffers().stream().anyMatch(offer -> LycanVillagerEntity.sameOffer(offer, ordinary)),
            "entity reload preserves the ordinary profession offer tuple");
        loaded.discard();
    }); }
    public static void signatureOffersReconcileWithoutDuplicates(final GameTestHelper h) { withLycan(h, lycan -> {
        for (final var signature : LycanVillagerEntity.signatureOffers()) {
            final long matches = lycan.getOffers().stream().filter(offer -> LycanVillagerEntity.sameOffer(offer, signature)).count();
            h.assertValueEqual(matches, 1L, "live offer access removes every exact duplicate");
        }
    }, lycan -> lycan.getOffers().addAll(LycanVillagerEntity.signatureOffers())); }
    public static void tradeSuccessAwardsFamiliarityOnce(final GameTestHelper h) {
        final LycanVillagerEntity lycan = spawnLycan(h);
        final ServerPlayer player = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        SupernaturalState.setForm(player, SupernaturalForm.WEREWOLF);
        lycan.setTradingPlayer(player);
        final MerchantOffer offer = lycan.getOffers().getFirst();
        lycan.notifyTrade(offer);
        lycan.notifyTrade(offer);
        h.runAfterDelay(2L, () -> {
            try { h.assertValueEqual(lycan.sentinelState().points(player.getUUID()),
                LycanVillagerRules.TRADE_FAMILIARITY_POINTS, "two live notifications inside cooldown award familiarity once"); }
            finally { lycan.setTradingPlayer(null); lycan.discard(); player.discard(); }
            h.succeed();
        });
    }
    public static void familiarityCapsAndEvictsDeterministically(final GameTestHelper h) {
        final LycanVillagerEntity lycan = spawnLycan(h);
        // This fixture drives the bounded observation pass explicitly below. Freeze the inherited
        // villager Brain so an ordinary Brain tick cannot erase HOME and clear the residence ledger
        // between the two controlled observation windows.
        lycan.setNoAi(true);
        final java.util.List<Villager> residents = new java.util.ArrayList<>();
        final GlobalPos home = GlobalPos.of(h.getLevel().dimension(), lycan.blockPosition());
        lycan.getBrain().setMemory(MemoryModuleType.HOME, home);
        for (int index = 0; index < 5; index++) {
            final Villager resident = h.spawn(EntityTypes.VILLAGER, new BlockPos(1 + index % 2, 1, 1 + index / 2 % 2));
            resident.setNoAi(true); residents.add(resident);
            resident.getBrain().setMemory(MemoryModuleType.HOME, home);
        }
        final long now = h.getLevel().getGameTime();
        lycan.setSentinelState(lycan.sentinelState().withCadence(now, now, now + 500L));
        scheduleResidenceWindow(h, lycan, residents, home, 1L);
        scheduleResidenceWindow(h, lycan, residents, home, 101L);
        h.runAfterDelay(125L, () -> {
            try {
                h.assertValueEqual(lycan.sentinelState().familiarity().size(), LycanVillagerRules.FAMILIARITY_CAP,
                    "five live shared-home residents earn exactly four bounded rows through runtime observation");
                h.assertTrue(lycan.sentinelState().familiarity().stream().allMatch(row -> row.points() >= 1),
                    "each retained resident earned familiarity only after cumulative loaded residence");
            } finally { lycan.discard(); residents.forEach(Villager::discard); }
            h.succeed();
        });
    }
    public static void fullMoonWatchIsBounded(final GameTestHelper h) { withLycan(h, lycan -> {
        h.assertValueEqual(lycan.sentinelState().intent(), LycanVillagerRules.Intent.ROUTINE,
            "an elapsed live moon watch releases authority back to the Brain");
        h.assertTrue(lycan.getNavigation().isDone(), "expired watch owns no path");
    }, lycan -> {
        final long now = h.getLevel().getGameTime();
        lycan.setSentinelState(lycan.sentinelState().withIntent(LycanVillagerRules.Intent.MOON_WATCH,
            now - LycanVillagerRules.WATCH_TICKS - LycanVillagerRules.DECISION_CADENCE_TICKS));
    }); }
    public static void bondedResidentAttackWarnsThenDefends(final GameTestHelper h) {
        // Vanilla Villager schedules are world-time driven. Keep this protection fixture in the
        // working day so inherited sleep/REST activity cannot cancel the sentinel before it sees
        // the attributed harm, then restore the shared clock for the next isolated batch.
        GameTestWorldClock.restoreAfterTest(h);
        h.setTime(6_000L);
        final LycanVillagerEntity lycan = spawnLycan(h);
        final Villager resident = h.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 1));
        // A hostile body can make the inherited villager Brain enter HIDE before the sentinel
        // observes the attributed hit. Use a neutral living attacker so this fixture isolates
        // the bonded-resident protection contract instead of racing vanilla hostile sensing.
        final var attacker = h.spawn(EntityTypes.COW, new BlockPos(2, 1, 2));
        resident.setNoAi(true);
        attacker.setNoAi(true);
        final GlobalPos home = GlobalPos.of(h.getLevel().dimension(), lycan.blockPosition());
        lycan.getBrain().setMemory(MemoryModuleType.HOME, home);
        resident.getBrain().setMemory(MemoryModuleType.HOME, home);
        lycan.setSentinelState(lycan.sentinelState().observe(resident.getUUID(),
            LycanVillagerRules.RelationshipSource.RESIDENT, LycanVillagerRules.HOUSEHOLD_THRESHOLD,
            h.getLevel().getGameTime()).withCadence(h.getLevel().getGameTime(), h.getLevel().getGameTime(),
                h.getLevel().getGameTime() + 500L));
        final boolean[] defenseObserved = {false};
        h.onEachTick(() -> {
            // HOME normally points at a real village POI. This empty fixture supplies the
            // household relation directly, so keep that synthetic memory live while the
            // inherited villager Brain validates and may erase POI-less memories.
            lycan.getBrain().setMemory(MemoryModuleType.HOME, home);
            resident.getBrain().setMemory(MemoryModuleType.HOME, home);
            if (defenseObserved[0]) {
                return;
            }
            final LycanVillagerRules.Intent intent = lycan.sentinelState().intent();
            if ((intent != LycanVillagerRules.Intent.INTERCEPT
                && intent != LycanVillagerRules.Intent.DEFEND)
                || (lycan.getTarget() != attacker && attacker.getHealth() >= attacker.getMaxHealth())) {
                return;
            }
            defenseObserved[0] = true;
        });
        h.runAfterDelay(125L, () -> {
            resident.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(attacker), 1.0F);
            resident.setLastHurtByMob(attacker);
            final long observed = h.getLevel().getGameTime();
            lycan.setSentinelState(lycan.sentinelState().withCadence(observed, observed, observed + 500L));
        });
        h.runAfterDelay(195L, () -> {
            try {
                h.assertTrue(defenseObserved[0],
                    "bonded household familiarity and live harm advance beyond warning by the bounded deadline"
                        + " [intent=" + lycan.sentinelState().intent()
                        + ", points=" + lycan.sentinelState().points(resident.getUUID())
                        + ", targetMatches=" + (lycan.getTarget() == attacker)
                        + ", recentMatches=" + lycan.sentinelState().recentAggressor()
                            .map(attacker.getUUID()::equals).orElse(false)
                        + ", residentMatches=" + lycan.sentinelState().protectedResident()
                            .map(resident.getUUID()::equals).orElse(false)
                        + ", lastHurtMatches=" + (resident.getLastHurtByMob() == attacker)
                        + ", lastHurtTimestamp=" + resident.getLastHurtByMobTimestamp()
                        + ", residentTick=" + resident.tickCount
                        + ", lycanTick=" + lycan.tickCount + "]");
                h.assertTrue(!attacker.isAlive()
                    || lycan.sentinelState().intent() == LycanVillagerRules.Intent.INTERCEPT
                    || lycan.sentinelState().intent() == LycanVillagerRules.Intent.DEFEND,
                    "engagement persists past the evidence-freshness window while the live threat endures");
            } finally {
                lycan.discard();
                resident.discard();
                attacker.discard();
            }
            h.succeed();
        });
    }
    public static void unbondedAttackDoesNotTriggerProtection(final GameTestHelper h) {
        final LycanVillagerEntity lycan = spawnLycan(h);
        final Villager resident = h.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 1));
        final Zombie attacker = h.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        h.runAfterDelay(80L, () -> resident.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(attacker), 1.0F));
        h.runAfterDelay(125L, () -> {
            try { h.assertTrue(lycan.getTarget() != attacker, "a live unbonded attack must not acquire its attacker"); }
            finally { lycan.discard(); resident.discard(); attacker.discard(); }
            h.succeed();
        });
    }
    public static void directAttackerUsesAttributeMeleeDamage(final GameTestHelper h) {
        final LycanVillagerEntity lycan = spawnLycan(h);
        final Zombie attacker = h.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
        final float before = attacker.getHealth();
        attacker.setNoAi(true);
        lycan.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(attacker), 1.0F);
        h.runAfterDelay(35L, () -> {
            try {
                h.assertTrue(attacker.getHealth() < before, "warning/intercept and the live melee goal must damage its target");
                h.assertValueEqual(lycan.getAttributeValue(Attributes.ATTACK_DAMAGE), 6.0D,
                    "the live attack is backed by the six-point attack attribute");
            }
            finally { lycan.discard(); attacker.discard(); }
            h.succeed();
        });
    }
    public static void lowHealthWithdrawsAndReleasesTarget(final GameTestHelper h) {
        final LycanVillagerEntity lycan = spawnLycan(h);
        final Zombie attacker = h.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        attacker.setNoAi(true);
        lycan.setHealth(7.0F);
        lycan.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(attacker), 1.0F);
        h.runAfterDelay(3L, () -> {
            try {
                h.assertTrue(lycan.getTarget() == null, "live low-health harm releases the attacker target");
                h.assertTrue(lycan.sentinelState().intent() == LycanVillagerRules.Intent.WITHDRAW
                    || lycan.sentinelState().intent() == LycanVillagerRules.Intent.ROUTINE,
                    "live low-health harm withdraws or safely returns to Brain routine");
            } finally { lycan.discard(); attacker.discard(); }
            h.succeed();
        });
    }
    public static void blockedRouteBacksOffAfterThreeFailures(final GameTestHelper h) {
        final LycanVillagerEntity lycan = spawnLycan(h);
        final BlockPos target = h.absolutePos(new BlockPos(2, 1, 2)).above(80);
        final BlockPos origin = lycan.blockPosition();
        final java.util.List<BlockPos> cage = java.util.List.of(origin.north(), origin.south(), origin.east(), origin.west());
        cage.forEach(pos -> h.getLevel().setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState()));
        lycan.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(h.getLevel().dimension(), target));
        lycan.setSentinelState(lycan.sentinelState().withAnchor(h.getLevel().dimension().identifier().toString(),
            target.asLong()).withIntent(LycanVillagerRules.Intent.WITHDRAW, h.getLevel().getGameTime()));
        for (long delay = 1; delay < 70; delay++) {
            h.runAfterDelay(delay, () -> {
                lycan.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(h.getLevel().dimension(), target));
                if (lycan.sentinelState().intent() == LycanVillagerRules.Intent.ROUTINE) {
                    lycan.setSentinelState(lycan.sentinelState().withAnchor(h.getLevel().dimension().identifier().toString(),
                        target.asLong()).withIntent(LycanVillagerRules.Intent.WITHDRAW, h.getLevel().getGameTime()));
                }
                LycanVillagerRuntime.tick(lycan, h.getLevel());
            });
        }
        h.runAfterDelay(75L, () -> {
            try {
                h.assertValueEqual(lycan.sentinelState().routeFailures(), LycanVillagerRules.MAX_ROUTE_FAILURES,
                    "three real failed path creations exhaust the bounded failure counter");
                h.assertTrue(lycan.sentinelState().retryAfter() > h.getLevel().getGameTime(),
                    "three real route failures impose live backoff");
            } finally { lycan.discard(); cage.forEach(pos -> h.getLevel().removeBlock(pos, false)); }
            h.succeed();
        });
    }
    public static void destroyedPoiCancelsOverride(final GameTestHelper h) { withLycan(h, lycan -> {
        h.assertValueEqual(lycan.sentinelState().intent(), LycanVillagerRules.Intent.ROUTINE,
            "erasing the live Brain POI cancels the sentinel override");
        h.assertTrue(lycan.getNavigation().isDone(), "POI loss releases its owned path");
    }, lycan -> {
        lycan.getBrain().setMemory(MemoryModuleType.HOME,
            GlobalPos.of(h.getLevel().dimension(), lycan.blockPosition()));
        lycan.setSentinelState(lycan.sentinelState().withAnchor(h.getLevel().dimension().identifier().toString(),
            lycan.blockPosition().asLong()).withIntent(LycanVillagerRules.Intent.BOUNDARY_WATCH,
            h.getLevel().getGameTime()));
        lycan.getBrain().eraseMemory(MemoryModuleType.HOME);
    }); }
    public static void reloadDiscardsTransientCombatClaims(final GameTestHelper h) { withLycan(h, lycan -> {
        final TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        lycan.saveWithoutId(output);
        final LycanVillagerEntity loaded = (LycanVillagerEntity) ModEntities.ALL.get("lycan_villager").get()
            .create(h.getLevel(), EntitySpawnReason.LOAD);
        h.assertTrue(loaded != null, "reload fixture creates the registered entity");
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult().copy()));
        h.getLevel().addFreshEntity(loaded);
        h.assertTrue(loaded.sentinelState().recentAggressor().isEmpty()
            && loaded.sentinelState().protectedResident().isEmpty() && loaded.getTarget() == null,
            "entity save/load and insertion discard transient combat authority");
        loaded.discard();
    }, lycan -> lycan.setSentinelState(lycan.sentinelState().withCombat(UUID.randomUUID(), UUID.randomUUID(),
        LycanVillagerRules.Intent.DEFEND, h.getLevel().getGameTime() + 20L, h.getLevel().getGameTime() + 40L))); }
    public static void hazardWinsEndOfTickMovement(final GameTestHelper h) {
        final LycanVillagerEntity lycan = spawnLycan(h);
        lycan.igniteForSeconds(3.0F); lycan.setSentinelState(lycan.sentinelState().withIntent(
            LycanVillagerRules.Intent.DEFEND, h.getLevel().getGameTime()));
        for (int east = 0; east <= 10; east++) {
            for (int side = -2; side <= 2; side++) {
                h.getLevel().setBlockAndUpdate(lycan.blockPosition().offset(east, -1, side), Blocks.STONE.defaultBlockState());
            }
        }
        h.runAfterDelay(1L, () -> {
            try {
                lycan.tickCount = 10;
                LycanVillagerRuntime.tick(lycan, h.getLevel());
                h.assertTrue(lycan.getTarget() == null && lycan.sentinelState().intent() == LycanVillagerRules.Intent.ROUTINE,
                    "an actual burning entity releases sentinel combat");
                h.assertTrue(HazardEscapeRuntime.currentHazard(lycan, h.getLevel()).isPresent()
                    && HazardEscapeRuntime.tick(lycan, h.getLevel(), lycan.creatureKind()),
                    "the same tick transfers the detected hazard to the shared escape controller");
            } finally { lycan.discard(); }
            h.succeed();
        });
    }
    public static void replacementPathsDoNotTransferSentinelState(final GameTestHelper h) { withLycan(h, lycan -> {
        final var child = lycan.getBreedOffspring(h.getLevel(), lycan);
        h.assertTrue(child != null, "the inherited breeding replacement path must produce a concrete child");
        h.assertTrue(!(child instanceof LycanVillagerEntity replacement)
            || replacement.sentinelState().familiarity().isEmpty(),
            "the inherited breeding replacement path never transfers sentinel state");
        if (child != null) child.discard();
    }, lycan -> lycan.setSentinelState(lycan.sentinelState().observe(UUID.randomUUID(),
        LycanVillagerRules.RelationshipSource.RESIDENT, 8, h.getLevel().getGameTime()))); }

    private static void withLycan(final GameTestHelper helper, final java.util.function.Consumer<LycanVillagerEntity> test) {
        withLycan(helper, test, lycan -> { });
    }

    private static LycanVillagerEntity spawnLycan(final GameTestHelper helper) {
        return (LycanVillagerEntity) helper.spawn(ModEntities.ALL.get("lycan_villager").get(),
            new BlockPos(1, 1, 1), EntitySpawnReason.EVENT);
    }

    private static void scheduleResidenceWindow(final GameTestHelper h, final LycanVillagerEntity lycan,
                                                 final java.util.List<Villager> residents, final GlobalPos home,
                                                 final long start) {
        for (long offset = 0; offset < 8; offset++) {
            h.runAfterDelay(start + offset, () -> {
                lycan.getBrain().setMemory(MemoryModuleType.HOME, home);
                residents.forEach(resident -> resident.getBrain().setMemory(MemoryModuleType.HOME, home));
                final long now = h.getLevel().getGameTime();
                lycan.setSentinelState(lycan.sentinelState().withCadence(now, now, now + 500L));
                LycanVillagerRuntime.tick(lycan, h.getLevel());
            });
        }
    }

    private static void withLycan(final GameTestHelper helper, final java.util.function.Consumer<LycanVillagerEntity> test,
                                  final java.util.function.Consumer<LycanVillagerEntity> setup) {
        final LycanVillagerEntity lycan = (LycanVillagerEntity) helper.spawn(
            ModEntities.ALL.get("lycan_villager").get(), new BlockPos(1, 1, 1), EntitySpawnReason.EVENT);
        setup.accept(lycan);
        helper.runAfterDelay(2L, () -> {
            try { test.accept(lycan); helper.succeed(); } finally { lycan.discard(); }
        });
    }
}
