package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.entity.GoblinHostilityRules;
import com.kadamitas.warlockery.entity.HobgoblinEntity;
import com.kadamitas.warlockery.world.VillageAssaultRules.AssaultKind;
import com.kadamitas.warlockery.world.VillageAssaultRules.SettlementKind;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GoblinRaidRuntime {
    private GoblinRaidRuntime() {
    }

    public static void tick(final ServerLevel level) {
        VillageAssaultRuntime.tick(level);
    }

    /*
     * The 1.4 `coordinate(HobgoblinEntity, ServerLevel)` assault-coordination pass was removed here
     * rather than repaired.
     *
     * It was orphaned by the F10 split, not by F11. Its only production caller was
     * `HobgoblinEntity.customServerAiStep` behind `kind == CreatureKind.GOBLIN`, and after F10
     * `ModEntities.GOBLIN` constructs `GoblinEntity`, so no `HobgoblinEntity` can ever report
     * `CreatureKind.GOBLIN` again: the guard became unsatisfiable and the method unreachable. Its
     * body also scanned `HobgoblinEntity.class` for `isVillageRaider()` members, which requires the
     * same unsatisfiable kind, so the scan matched zero entities even if the method had been called.
     * Every unit test still passed because they exercise the rules, never this receiver.
     *
     * The behavior itself is not lost: F10 owns exact-Goblin assault movement and shared targeting
     * in `GoblinEnclaveRuntime.executeAssault` plus `onAssaultJoined` / `onAssaultLeft`, which the
     * dedicated body wires from `joinVillageAssault` / `leaveVillageAssault`. This is a MOVE, not a
     * deletion of a live contract.
     */

    static int spawnWave(
        final ServerLevel level,
        final BlockPos center,
        final int wave,
        final int radius
    ) {
        return VillageAssaultRuntime.spawnWave(
            level,
            center,
            wave,
            AssaultKind.GOBLIN,
            SettlementKind.HUMAN,
            radius
        );
    }
}
