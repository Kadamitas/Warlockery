package com.kadamitas.warlockery.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class SupernaturalSnapshotTest {
    @Test
    void normalizesUntrustedNetworkValues() {
        final ModNetwork.SupernaturalSnapshot snapshot = new ModNetwork.SupernaturalSnapshot(
            null,
            14,
            900,
            500,
            null,
            null,
            null,
            null
        );

        assertEquals("", snapshot.identity());
        assertEquals(10, snapshot.level());
        assertEquals(500, snapshot.resource());
        assertEquals(500, snapshot.maxResource());
        assertEquals("", snapshot.selectedPower());
        assertFalse(snapshot.active());
    }

    @Test
    void clampsNegativeResourceCapacity() {
        final ModNetwork.SupernaturalSnapshot snapshot = new ModNetwork.SupernaturalSnapshot(
            "transformation.warlockery.vampire",
            -4,
            -20,
            -1,
            "power.warlockery.transfix",
            "shape.warlockery.human",
            "quest.warlockery.vampire.first_night",
            "0 / 1"
        );

        assertEquals(0, snapshot.level());
        assertEquals(0, snapshot.resource());
        assertEquals(0, snapshot.maxResource());
        assertEquals(0.0F, snapshot.resourceFraction());
        assertTrue(snapshot.active());
    }

    @Test
    void calculatesResourceFraction() {
        final ModNetwork.SupernaturalSnapshot snapshot = new ModNetwork.SupernaturalSnapshot(
            "transformation.warlockery.werewolf",
            7,
            35,
            100,
            "power.warlockery.stun_howl",
            "shape.warlockery.wolfman",
            "quest.warlockery.werewolf.call_the_pack",
            "2 / 6"
        );

        assertEquals(0.35F, snapshot.resourceFraction(), 0.0001F);
        assertTrue(snapshot.active());
    }

    @Test
    void hidesExplicitNoneIdentities() {
        assertFalse(snapshot("none").active());
        assertFalse(snapshot("transformation.warlockery.none").active());
        assertTrue(snapshot("transformation.warlockery.vampire").active());
    }

    @Test
    void normalizesSanguineAndPreyTargetState() {
        final ModNetwork.SupernaturalSnapshot snapshot = new ModNetwork.SupernaturalSnapshot(
            "vampire", 10, 3500, 3500, "", "", "", "", -1, 0, "", 0, 0, true, -9
        );
        assertTrue(snapshot.sanguine());
        assertEquals(-1, snapshot.preyTargetEntityId());
    }

    @Test
    void roundTripsSanguineAndPreyTargetState() {
        final ModNetwork.SupernaturalSnapshot snapshot = new ModNetwork.SupernaturalSnapshot(
            "transformation.warlockery.vampire", 10, 3_500, 3_500,
            "power.warlockery.transfix", "shape.warlockery.human", "quest", "done",
            17, 240, "feeding", 60, 80, true, 42
        );
        final RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(), RegistryAccess.EMPTY
        );

        ModNetwork.SupernaturalSnapshotPayload.STREAM_CODEC.encode(
            buffer, new ModNetwork.SupernaturalSnapshotPayload(snapshot)
        );
        final ModNetwork.SupernaturalSnapshot decoded =
            ModNetwork.SupernaturalSnapshotPayload.STREAM_CODEC.decode(buffer).snapshot();

        assertEquals(snapshot, decoded);
        assertTrue(decoded.sanguine());
        assertEquals(42, decoded.preyTargetEntityId());
    }

    @Test
    void protocolV7AppendsSanguineBeforeTheNormalizedPreyTarget() throws Exception {
        final String source = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/network/ModNetwork.java"
        ));
        assertTrue(source.contains("PROTOCOL_PATH = \"network/v7/\""));
        final int sanguine = source.indexOf("output.writeBoolean(snapshot.sanguine())");
        final int preyTarget = source.indexOf("output.writeVarInt(snapshot.preyTargetEntityId())");
        assertTrue(sanguine >= 0);
        assertTrue(preyTarget > sanguine);
    }

    private static ModNetwork.SupernaturalSnapshot snapshot(final String identity) {
        return new ModNetwork.SupernaturalSnapshot(identity, 0, 0, 0, "", "", "", "");
    }
}
