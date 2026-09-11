package com.kadamitas.warlockery.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.kadamitas.warlockery.ritual.RitualManager;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class RitualScreenPayloadTest {
    @Test
    void deliberateOpensAndRefreshesRetainDistinctPermissionsAcrossTheWire() {
        for (final boolean mayOpen : new boolean[] {true, false}) {
            final var original = payload(List.of(option(List.of(requirement()))), mayOpen);
            final var decoded = roundTrip(original);
            assertEquals(original, decoded);
            assertEquals(mayOpen, decoded.mayOpen());
            assertEquals("#minecraft:coals", decoded.options().getFirst().requirements().getFirst().label());
        }
    }

    @Test
    void refreshPermissionSurvivesTheExistingOptionAndRequirementCaps() {
        final var oversizedRequirements = IntStream.range(0, 33).mapToObj(_ -> requirement()).toList();
        final var oversizedOptions = IntStream.range(0, 129).mapToObj(_ -> option(oversizedRequirements)).toList();
        final var decoded = roundTrip(payload(oversizedOptions, false));
        assertEquals(128, decoded.options().size());
        assertEquals(32, decoded.options().getFirst().requirements().size());
        assertEquals(false, decoded.mayOpen());
    }

    @Test
    void emptyRefreshStillCannotRequestOpeningAClosedScreen() {
        final var decoded = roundTrip(payload(List.of(), false));
        assertEquals(List.of(), decoded.options());
        assertEquals(false, decoded.mayOpen());
    }

    private static ModNetwork.OpenRitualScreenPayload payload(
        final List<RitualManager.RitualOption> options,
        final boolean mayOpen
    ) {
        return new ModNetwork.OpenRitualScreenPayload(new BlockPos(11, 72, -19), options, mayOpen);
    }

    private static ModNetwork.OpenRitualScreenPayload roundTrip(final ModNetwork.OpenRitualScreenPayload original) {
        final var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ModNetwork.OpenRitualScreenPayload.STREAM_CODEC.encode(buffer, original);
            final var decoded = ModNetwork.OpenRitualScreenPayload.STREAM_CODEC.decode(buffer);
            assertFalse(buffer.isReadable(), "the permission field and entire checklist must be consumed");
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static RitualManager.RitualOption option(final List<RitualManager.RequirementStatus> requirements) {
        return new RitualManager.RitualOption("warlockery:cook_food", "ritual.title", "ritual.description",
            100, 250, 80, requirements, false);
    }

    private static RitualManager.RequirementStatus requirement() {
        return new RitualManager.RequirementStatus("ingredient", "#minecraft:coals", 2, 1, false);
    }
}
