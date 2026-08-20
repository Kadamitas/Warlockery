package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WerewolfHunterResourceTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final List<String> FIXTURE_IDS = List.of(
        "hunter_identity_loadout_and_raid_containment",
        "hunter_warrant_matrix_and_evidence_expiry",
        "hunter_warns_tracks_and_returns_to_anchor",
        "hunter_crossbow_consumes_finite_silver_ammunition",
        "hunter_protected_crossfire_cancels_shot",
        "hunter_retreat_search_and_hazard_preemption_are_bounded",
        "hunter_resupply_caps_without_duplication",
        "silver_hunt_transaction_deduplicates_and_rolls_back",
        "hunter_reload_reconciles_semantic_state_only",
        "hunter_route_failures_back_off_and_release"
    );

    @Test
    void hunterIsRemovedFromVanillaIllagerAndRaiderMembership() {
        for (final String tag : List.of("illager", "raiders")) {
            final JsonObject parsed = read(RESOURCES.resolve(
                Path.of("data", "minecraft", "tags", "entity_type", tag + ".json")
            ));
            assertTrue(parsed.has("values"), tag + " stays a valid tag data file");
            assertEquals(0, parsed.getAsJsonArray("values").size(),
                "the hunter value is removed and no other value is introduced in " + tag);
            assertTrue(parsed.has("replace"), tag + " keeps its non-replacing shape");
            assertTrue(!parsed.get("replace").getAsBoolean(),
                tag + " must not replace vanilla membership");
        }
    }

    private static JsonObject read(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (final IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
