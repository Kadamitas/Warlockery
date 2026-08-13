package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.Set;
import net.minecraft.core.BlockPos;

public final class GoblinSettlementLifeRules {
    public static final int SETTLEMENT_RADIUS = 24;
    public static final int MIN_RESIDENTS = 2;
    public static final int POPULATION_CAP = 8;
    public static final int HUT_CAP = 3;
    public static final int TUNNEL_CAP = 1;
    public static final int WORLD_EDIT_CAP = 128;
    public static final int HUT_DIRT_COST = 18;
    public static final int HUT_LOG_COST = 3;
    public static final int HUT_EDIT_COST = 32;
    public static final int TUNNEL_EDIT_CAP = 10;
    public static final int ADULT_TICK_INTERVAL = 200;
    public static final int CHILD_TICK_INTERVAL = 40;
    public static final int GIFT_COOLDOWN_TICKS = 12_000;
    public static final int TUNNEL_ROLL_BOUND = 240;
    private static final Set<CreatureKind> SETTLERS = Set.of(CreatureKind.GOBLIN, CreatureKind.HOBGOBLIN);

    private GoblinSettlementLifeRules() {
    }

    public static long settlementKey(final BlockPos position, final CreatureKind kind) {
        final long regionX = Math.floorDiv(position.getX(), 128);
        final long regionZ = Math.floorDiv(position.getZ(), 128);
        final long region = (regionX & 0x7FFF_FFFFL) << 32 | regionZ & 0xFFFF_FFFFL;
        return region * 31L + kind.ordinal();
    }

    public static boolean participates(final CreatureKind kind, final boolean raider, final boolean boss) {
        return SETTLERS.contains(kind) && !raider && !boss;
    }

    public static boolean needsHousing(final int residents, final int reachableBeds) {
        return residents >= MIN_RESIDENTS
            && residents < POPULATION_CAP
            && reachableBeds <= residents;
    }

    public static boolean canReproduce(final int residents, final int reachableBeds) {
        return residents >= MIN_RESIDENTS
            && residents < POPULATION_CAP
            && reachableBeds > residents;
    }

    public static boolean canReserveHut(final int huts, final int worldEdits) {
        return huts < HUT_CAP && fitsEditBudget(worldEdits, HUT_EDIT_COST);
    }

    public static boolean canReserveTunnel(final int tunnels, final int worldEdits, final int edits) {
        return tunnels < TUNNEL_CAP
            && edits > 0
            && edits <= TUNNEL_EDIT_CAP
            && fitsEditBudget(worldEdits, edits);
    }

    public static boolean canGatherNaturalBlock(final int worldEdits) {
        return fitsEditBudget(worldEdits, 1);
    }

    public static boolean shouldAttemptTunnel(final int roll) {
        return Math.floorMod(roll, TUNNEL_ROLL_BOUND) == 0;
    }

    public static boolean giftReady(final long gameTime, final long nextGiftTime, final boolean holdingFlower) {
        return holdingFlower && gameTime >= nextGiftTime;
    }

    private static boolean fitsEditBudget(final int current, final int requested) {
        return current >= 0 && requested >= 0 && current <= WORLD_EDIT_CAP - requested;
    }
}

