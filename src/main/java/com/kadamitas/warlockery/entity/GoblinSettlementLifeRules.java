package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import net.minecraft.core.BlockPos;

/**
 * Persistence-side remainder of the retired 1.4 settlement life system. The behavioural half
 * (participation gating, housing, reproduction, gifting, tunnel rolls, and tick intervals) went
 * with {@code GoblinSettlementLifeRuntime} and the shared {@code HobgoblinEntity} body, which no
 * registry entry constructs any more. What survives is exactly the structure-reservation contract
 * {@link com.kadamitas.warlockery.world.GoblinSettlementLifeData} needs to read, clamp, and
 * migrate {@code goblin_settlement_life} records that still exist in 1.4-era save directories:
 * the caps that bound a persisted record and the key format those records are stored under.
 */
public final class GoblinSettlementLifeRules {
    public static final int HUT_CAP = 3;
    public static final int TUNNEL_CAP = 1;
    public static final int WORLD_EDIT_CAP = 128;
    public static final int HUT_EDIT_COST = 32;
    public static final int TUNNEL_EDIT_CAP = 10;

    private GoblinSettlementLifeRules() {
    }

    public static long settlementKey(final BlockPos position, final CreatureKind kind) {
        final long regionX = Math.floorDiv(position.getX(), 128);
        final long regionZ = Math.floorDiv(position.getZ(), 128);
        final long region = (regionX & 0x7FFF_FFFFL) << 32 | regionZ & 0xFFFF_FFFFL;
        return region * 31L + kind.ordinal();
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

    private static boolean fitsEditBudget(final int current, final int requested) {
        return current >= 0 && requested >= 0 && current <= WORLD_EDIT_CAP - requested;
    }
}
