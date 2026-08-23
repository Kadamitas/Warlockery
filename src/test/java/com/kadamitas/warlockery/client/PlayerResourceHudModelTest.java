package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.network.ModNetwork;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PlayerResourceHudModelTest {
    @Test
    void onlyWarlockeryFocusItemsActivateTheManaPrompt() {
        assertTrue(PlayerResourceHudModel.isFocusItem("warlockery", "arcane_focus"));
        assertTrue(PlayerResourceHudModel.isFocusItem("warlockery", "mysticbranch"));
        assertFalse(PlayerResourceHudModel.isFocusItem("other_magic", "arcane_focus"));
        assertFalse(PlayerResourceHudModel.isFocusItem("minecraft", "mysticbranch"));
    }

    @Test
    void vampireBloodLeavesTheTopLeftLaneWhileManaRemains() {
        final ModNetwork.SupernaturalSnapshot snapshot = snapshot(
            "path.warlockery.vampire",
            1_250,
            2_000,
            "power.warlockery.teleport",
            4,
            41,
            "magic_path.warlockery.otherwhere",
            73,
            160
        );

        final List<PlayerResourceHudModel.Meter> meters = PlayerResourceHudModel.meters(snapshot, true);

        assertEquals(1, meters.size());
        assertEquals(PlayerResourceHudModel.Kind.MANA, meters.getFirst().kind());
        assertEquals(73, meters.getFirst().resource());
        assertEquals(160, meters.getFirst().maximum());
    }

    @Test
    void werewolfReserveIsPresentedAsFerocityWithoutBloodCharges() {
        final ModNetwork.SupernaturalSnapshot snapshot = snapshot(
            "path.warlockery.werewolf",
            96,
            240,
            "power.warlockery.stun_howl",
            5,
            20,
            "",
            0,
            0
        );

        final PlayerResourceHudModel.Meter meter = PlayerResourceHudModel.meters(snapshot, false).getFirst();

        assertEquals(PlayerResourceHudModel.Kind.FEROCITY, meter.kind());
        assertEquals(-1, meter.charges());
        assertEquals(1, meter.cooldownSeconds());
    }

    @Test
    void heldUnattunedFocusExplainsWhyNoManaBarExists() {
        final ModNetwork.SupernaturalSnapshot snapshot = snapshot("", 0, 0, "", -1, 0, "", 0, 0);

        assertEquals(
            List.of(PlayerResourceHudModel.Kind.UNATTUNED),
            PlayerResourceHudModel.meters(snapshot, true).stream().map(PlayerResourceHudModel.Meter::kind).toList()
        );
        assertEquals(List.of(), PlayerResourceHudModel.meters(snapshot, false));
    }

    @Test
    void malformedNetworkValuesCannotOverflowMeterGeometry() {
        final ModNetwork.SupernaturalSnapshot snapshot = snapshot(
            "path.warlockery.vampire",
            900,
            400,
            null,
            -9,
            -20,
            "magic_path.warlockery.sky",
            300,
            120
        );

        final List<PlayerResourceHudModel.Meter> meters = PlayerResourceHudModel.meters(snapshot, true);

        assertEquals(1, meters.size());
        assertEquals(120, meters.getFirst().resource());
        assertEquals(96, meters.getFirst().filledWidth(96));
        assertEquals(0, meters.getFirst().filledWidth(-5));
    }

    private static ModNetwork.SupernaturalSnapshot snapshot(
        final String identity,
        final int resource,
        final int maximum,
        final String power,
        final int charges,
        final int cooldown,
        final String magicPath,
        final int magicResource,
        final int magicMaximum
    ) {
        return new ModNetwork.SupernaturalSnapshot(
            identity,
            7,
            resource,
            maximum,
            power,
            "",
            "",
            "",
            charges,
            cooldown,
            magicPath,
            magicResource,
            magicMaximum
        );
    }
}
