package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.item.UtilityDecision;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;

public final class UtilityDeviceRules {
    private UtilityDeviceRules() {
    }

    public static UtilityDecision bloodCrucible(final boolean vampire, final boolean blood) {
        if (!vampire) {
            return UtilityDecision.failure("vampire_required");
        }
        return blood ? UtilityDecision.success("fed") : UtilityDecision.failure("missing_blood");
    }

    public static UtilityDecision coffin(final boolean vampire, final boolean daytime) {
        if (!vampire) {
            return UtilityDecision.failure("vampire_required");
        }
        return daytime ? UtilityDecision.success("nightfall") : UtilityDecision.failure("already_night");
    }

    public static UtilityDecision leechChest(final boolean vial, final boolean victim) {
        if (!vial) {
            return UtilityDecision.failure("missing_vial");
        }
        return victim ? UtilityDecision.success("sampled") : UtilityDecision.failure("missing_victim");
    }

    public static UtilityDecision garlicWard(final boolean vampire) {
        return vampire
            ? UtilityDecision.failure("vampire_burned")
            : UtilityDecision.success("warding");
    }

    public static UtilityDecision mirror(final boolean mirrorTool) {
        return mirrorTool ? UtilityDecision.success("bound") : UtilityDecision.failure("missing_mirror");
    }

    public static UtilityDecision spiritPortal(final boolean destination) {
        return destination ? UtilityDecision.success("travelled") : UtilityDecision.failure("missing_destination");
    }

    public static UtilityDecision trentEffigy(final boolean offering) {
        return offering ? UtilityDecision.success("awakened") : UtilityDecision.failure("missing_sapling");
    }

    public static UtilityDecision wolfAltar(final boolean head, final boolean offering, final boolean moonlit) {
        if (!head) {
            return UtilityDecision.failure("missing_wolf_head");
        }
        if (!offering) {
            return UtilityDecision.failure("missing_offering");
        }
        return moonlit ? UtilityDecision.success("trial_complete") : UtilityDecision.failure("moon_required");
    }

    public static UtilityDecision wolfAltar(
        final boolean head,
        final boolean offering,
        final boolean moonlit,
        final int currentLevel
    ) {
        return currentLevel >= SupernaturalProgression.MAX_LEVEL
            ? UtilityDecision.success("path_complete")
            : wolfAltar(head, offering, moonlit);
    }

    public static WolfAltarProgression advanceWolf(final int currentLevel) {
        final int boundedLevel = Math.clamp(currentLevel, 0, SupernaturalProgression.MAX_LEVEL);
        final int nextLevel = Math.min(SupernaturalProgression.MAX_LEVEL, boundedLevel + 1);
        return new WolfAltarProgression(
            nextLevel,
            nextLevel > boundedLevel,
            boundedLevel == SupernaturalProgression.MAX_LEVEL - 1
        );
    }

    public static boolean shadedGlassActive(final boolean redstoneSignal) {
        return redstoneSignal;
    }

    public static boolean harmsDisease(final boolean living, final boolean immune) {
        return living && !immune;
    }

    public static boolean trapsInPit(final boolean living, final boolean sneaking) {
        return living && !sneaking;
    }

    public record WolfAltarProgression(int level, boolean advanced, boolean hornEarned) {
    }
}
