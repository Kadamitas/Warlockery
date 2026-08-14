package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Guards the data-driven GameTest dispatch layer from registration/fixture drift. */
final class GameTestInstanceContractTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path REGISTRY = MAIN_JAVA.resolve(
        "com/kadamitas/warlockery/registry/ModGameTests.java"
    );
    private static final Path INSTANCES = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_instance"
    );
    private static final Path VAMPIRE_COURT_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "vampire_court_isolated.json"
    );
    private static final Path LYCAN_VILLAGER_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "lycan_villager_isolated.json"
    );
    private static final Path LYCAN_PACK_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "lycan_pack_isolated.json"
    );
    private static final Path WEREWOLF_HUNTER_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "werewolf_hunter_isolated.json"
    );
    private static final Pattern REGISTRATION = Pattern.compile(
        "REGISTRY\\.register\\(\\\"([^\\\"]+)\\\",\\s*\\(\\)\\s*->\\s*([A-Za-z0-9_]+)::([A-Za-z0-9_]+)\\);"
    );
    private static final Pattern IMPORT = Pattern.compile(
        "import\\s+(com\\.kadamitas\\.warlockery\\.[\\w.]+);"
    );
    private static final Set<String> TACTICAL_HAZARD_AMBIENT_SETTLEMENT_AND_SPOUSE = Set.of(
        "ranged_creature_routes_behind_cover_when_player_draws_bow",
        "melee_creature_disengages_from_unreachable_attack_slit",
        "vulnerable_mob_routes_away_from_contact_hazards",
        "drowning_mob_routes_from_water_to_dry_ground",
        "demon_builds_one_temporary_snow_hearth",
        "ent_plants_one_loose_sapling_without_duplicating_it",
        "goblin_hut_consumes_materials_and_respects_persistent_caps",
        "goblin_children_gather_dance_and_gift_flowers",
        "goblin_tunnel_is_single_bounded_and_protects_containers",
        "spouse_cooks_one_meat_and_delivers_one_meal",
        "spouse_rejects_occupied_furnace_without_taking_meat",
        "spouse_kiss_persists_cooldown"
    );
    private static final Set<String> ISOLATED_VAMPIRE_COURT = Set.of(
        "vampire_court_day_shelter_and_night_hunt",
        "vampire_court_feeding_and_reports_remain_distinct",
        "blood_thrall_binds_intercepts_and_wavers",
        "vampire_court_assault_composition_preserves_contracts",
        "vampire_court_identity_targets_and_failures_are_bounded",
        "vampire_court_population_caps_hold"
    );
    private static final Set<String> ISOLATED_LYCAN_VILLAGER = Set.of(
        "lycan_brain_routine_resumes_after_watch",
        "lycan_signature_offers_survive_profession_and_reload",
        "lycan_signature_offers_reconcile_without_duplicates",
        "lycan_trade_success_awards_familiarity_once",
        "lycan_familiarity_caps_and_evicts_deterministically",
        "lycan_full_moon_watch_is_bounded",
        "lycan_bonded_resident_attack_warns_then_defends",
        "lycan_unbonded_attack_does_not_trigger_protection",
        "lycan_direct_attacker_uses_attribute_melee_damage",
        "lycan_low_health_withdraws_and_releases_target",
        "lycan_blocked_route_backs_off_after_three_failures",
        "lycan_destroyed_poi_cancels_override",
        "lycan_reload_discards_transient_combat_claims",
        "lycan_hazard_wins_end_of_tick_movement",
        "lycan_replacement_paths_do_not_transfer_sentinel_state"
    );
    private static final Set<String> ISOLATED_LYCAN_PACK = Set.of(
        "lycan_variants_keep_identity_and_drop_zombie_lifecycle",
        "werewolf_hunt_assigns_roles_and_replaces_coordinator",
        "feral_lycan_tracks_prey_warns_bonds_and_avoids_settlement",
        "lycan_schedules_hazards_and_silver_counters_remain_distinct",
        "lycan_family_targets_respect_kin_players_and_other_families",
        "werewolf_trap_hunt_assault_and_infection_contracts_remain_exact",
        "lycan_actions_cancel_across_failure_save_and_reload",
        "lycan_population_work_stays_within_declared_caps"
    );
    private static final Set<String> ISOLATED_WEREWOLF_HUNTER = Set.of(
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
    void everyGameTestRegistrationHasOneMatchingEmptyTemplateFixture() {
        final List<Registration> registrations = registrations();
        final Set<String> registeredIds = registrations.stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        final Set<String> fixtureIds = fixtureIds();

        assertEquals(registrations.size(), registeredIds.size(), "GameTest registration IDs must be unique");
        assertEquals(registeredIds, fixtureIds,
            "every GameTest registration must have exactly one test-instance fixture");
        assertTrue(registeredIds.containsAll(TACTICAL_HAZARD_AMBIENT_SETTLEMENT_AND_SPOUSE),
            "1.4 tactical, hazard, ambient, goblin-settlement, and spouse GameTests must remain registered");

        registrations.forEach(this::assertFixtureAndMethod);
    }

    @Test
    void onlyTheExactVampireCourtFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(VAMPIRE_COURT_ENVIRONMENT),
            "the isolated Vampire Court environment resource must exist");
        final JsonObject environment = JsonParser.parseString(read(VAMPIRE_COURT_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated Vampire Court environment must not mutate shared world state");
    }

    @Test
    void onlyTheExactLycanVillagerFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(LYCAN_VILLAGER_ENVIRONMENT));
        final JsonObject environment = JsonParser.parseString(read(LYCAN_VILLAGER_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty());
        assertEquals(15, ISOLATED_LYCAN_VILLAGER.size());
    }

    @Test
    void onlyTheExactLycanPackFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(LYCAN_PACK_ENVIRONMENT),
            "the isolated F04 Lycan Pack environment resource must exist");
        final JsonObject environment = JsonParser.parseString(read(LYCAN_PACK_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F04 Lycan Pack environment must not mutate shared world state");
        assertEquals(8, ISOLATED_LYCAN_PACK.size());
        final Set<String> registered = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registered.containsAll(ISOLATED_LYCAN_PACK),
            "all eight exact F04 Lycan Pack GameTests must be registered");
    }

    @Test
    void onlyTheExactWerewolfHunterFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(WEREWOLF_HUNTER_ENVIRONMENT),
            "the isolated F06 Werewolf Hunter environment resource must exist");
        final JsonObject environment = JsonParser.parseString(read(WEREWOLF_HUNTER_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F06 Werewolf Hunter environment must not mutate shared world state");
        assertEquals(10, ISOLATED_WEREWOLF_HUNTER.size());
        final Set<String> registered = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registered.containsAll(ISOLATED_WEREWOLF_HUNTER),
            "all ten exact F06 Werewolf Hunter GameTests must be registered");
    }

    private void assertFixtureAndMethod(final Registration registration) {
        final JsonObject fixture = readFixture(registration.id());
        assertEquals("minecraft:function", fixture.get("type").getAsString(), registration.id());
        assertEquals("warlockery:" + registration.id(), fixture.get("function").getAsString(), registration.id());
        assertEquals(
            ISOLATED_VAMPIRE_COURT.contains(registration.id())
                ? "warlockery:vampire_court_isolated"
                : ISOLATED_LYCAN_VILLAGER.contains(registration.id())
                    ? "warlockery:lycan_villager_isolated"
                    : ISOLATED_LYCAN_PACK.contains(registration.id())
                        ? "warlockery:lycan_pack_isolated"
                        : ISOLATED_WEREWOLF_HUNTER.contains(registration.id())
                            ? "warlockery:werewolf_hunter_isolated"
                            : "minecraft:default",
            fixture.get("environment").getAsString(),
            registration.id()
        );
        assertEquals("forge:empty3x3x3", fixture.get("structure").getAsString(), registration.id());
        assertTrue(fixture.get("max_ticks").getAsInt() > 0, registration.id());

        final Path source = sourceFor(registration.owner());
        assertTrue(Files.exists(source), () -> registration.id() + " owner source missing: " + source);
        final String sourceText = read(source);
        assertTrue(sourceText.contains("public static void " + registration.method() + "("),
            () -> registration.id() + " method missing from " + source);
    }

    private static List<Registration> registrations() {
        final String source = read(REGISTRY);
        final Matcher matcher = REGISTRATION.matcher(source);
        final List<Registration> registrations = new ArrayList<>();
        while (matcher.find()) {
            registrations.add(new Registration(matcher.group(1), matcher.group(2), matcher.group(3)));
        }
        assertTrue(!registrations.isEmpty(), "ModGameTests must contain registrations");
        return List.copyOf(registrations);
    }

    private static Path sourceFor(final String simpleName) {
        final Matcher matcher = IMPORT.matcher(read(REGISTRY));
        final Map<String, Path> sources = new LinkedHashMap<>();
        while (matcher.find()) {
            final String qualifiedName = matcher.group(1);
            final int separator = qualifiedName.lastIndexOf('.');
            sources.put(qualifiedName.substring(separator + 1), MAIN_JAVA.resolve(
                qualifiedName.replace('.', '/') + ".java"
            ));
        }
        final Path source = sources.get(simpleName);
        if (source == null) {
            throw new AssertionError("Missing ModGameTests import for " + simpleName);
        }
        return source;
    }

    private static Set<String> fixtureIds() {
        try (Stream<Path> files = Files.list(INSTANCES)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to list GameTest fixtures", exception);
        }
    }

    private static JsonObject readFixture(final String id) {
        return JsonParser.parseString(read(INSTANCES.resolve(id + ".json"))).getAsJsonObject();
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private record Registration(String id, String owner, String method) {
    }
}
