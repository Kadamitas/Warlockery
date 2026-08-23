package com.kadamitas.warlockery.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.world.VillageAssaultData.AssaultState;
import com.kadamitas.warlockery.world.VillageAssaultRules.AssaultKind;
import com.kadamitas.warlockery.world.VillageAssaultRules.SettlementKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class VillageAssaultPreyProtectionTest {
    @Test
    void activeParticipantsVictimsAndCurrentObjectiveResidentsAreProtected() {
        final AssaultState state = new AssaultState(
            BlockPos.ZERO, AssaultKind.VAMPIRE, SettlementKind.HUMAN,
            1, 20L, 1_000L, false, List.of("participant"),
            1, 4, List.of("victim"), false, List.of("raider")
        );

        assertTrue(VillageAssaultRuntime.protectsPreyTarget(state, "participant", false, false));
        assertTrue(VillageAssaultRuntime.protectsPreyTarget(state, "victim", false, false));
        assertTrue(VillageAssaultRuntime.protectsPreyTarget(state, "raider", false, false));
        assertTrue(VillageAssaultRuntime.protectsPreyTarget(state, "fresh-villager", true, true));
        assertFalse(VillageAssaultRuntime.protectsPreyTarget(state, "distant-villager", true, false));
        assertFalse(VillageAssaultRuntime.protectsPreyTarget(state, "bystander", false, true));
    }

    @Test
    void naamahTrialProtectionUsesTheFabricEntityAttachment() throws Exception {
        final String source = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/world/VillageAssaultRuntime.java"
        ));
        assertTrue(source.contains(
            "WarlockeryEntityData.get(target).contains(SupernaturalProgressionRuntime.NAAMAH_TRIAL_OWNER)"
        ));
    }
}
