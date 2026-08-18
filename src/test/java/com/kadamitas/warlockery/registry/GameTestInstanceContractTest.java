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
    private static final Path INFERNAL_HIERARCHY_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "infernal_hierarchy_isolated.json"
    );
    private static final Path IMP_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "imp_isolated.json"
    );
    private static final Path ELDRITCH_WATCHER_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "eldritch_watcher_isolated.json"
    );
    private static final Path CORPSE_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "corpse_isolated.json"
    );
    private static final Path HELLHOUND_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "hellhound_isolated.json"
    );
    private static final Path HEX_BAT_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "hex_bat_isolated.json"
    );
    private static final Path BANSHEE_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "banshee_isolated.json"
    );
    private static final Path GOBLIN_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "goblin_isolated.json"
    );

    private static final Path GOBLIN_PATRON_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "goblin_patron_isolated.json"
    );
    private static final Path DEATH_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "death_isolated.json"
    );
    private static final Path LOST_SOUL_SPIRIT_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "lost_soul_spirit_isolated.json"
    );
    private static final Path COVEN_PRACTITIONERS_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "coven_practitioners_isolated.json"
    );
    private static final Path POLTERGEIST_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "poltergeist_isolated.json"
    );
    private static final Path ECHO_SPECTRE_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "echo_spectre_isolated.json"
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
    private static final Set<String> ISOLATED_INFERNAL_HIERARCHY = Set.of(
        "infernal_ranks_normalize_without_identity_drift",
        "demon_conflicting_owners_preserve_direct_pact",
        "demon_truce_morale_retreat_and_return_are_bounded",
        "archfiend_anchor_squad_and_ember_front_are_bounded",
        "regent_court_orders_phase_and_reinforcements_cleanup",
        "infernal_leader_loss_and_unloaded_authority_cancel_execution",
        "infernal_save_reload_truncates_and_migrates_state",
        "infernal_collision_border_and_chunk_edge_fail_safely",
        "infernal_acquisition_paths_preserve_targets_and_contracts",
        "infernal_population_caps_and_scan_budgets_hold"
    );
    private static final Set<String> ISOLATED_IMP = Set.of(
        "imp_contract_binding_favor_and_spells_remain_exact",
        "imp_familiar_bind_recall_and_owner_conflict_remain_exact",
        "imp_follow_watch_and_scout_return_are_bounded",
        "imp_scout_interrupt_reload_and_report_once",
        "imp_curiosity_inspects_without_storage_mutation",
        "imp_perch_collision_border_and_chunk_edge_fail_safely",
        "imp_ranged_lane_windup_and_retreat_are_bounded",
        "imp_projectile_allies_griefing_and_protected_blocks_are_safe",
        "imp_bound_environmental_immunity_does_not_transfer_damage",
        "imp_infernal_orders_authority_conflicts_and_leader_loss_are_safe",
        "imp_state_migration_corruption_and_expiry_are_bounded",
        "imp_population_cadence_and_operation_budgets_hold"
    );
    private static final Set<String> ISOLATED_ELDRITCH_WATCHER = Set.of(
        "eldritch_watcher_vigil_observes_and_escalates_on_reciprocal_gaze",
        "eldritch_watcher_revelation_is_bound_visible_and_attributed",
        "eldritch_watcher_binding_warning_lure_and_return_remain_local",
        "eldritch_watcher_save_reload_focus_hazard_and_work_are_bounded"
    );
    private static final Set<String> ISOLATED_CORPSE = Set.of(
        "corpse_raise_dead_identity_owner_and_acquisition_are_preserved",
        "corpse_scavenges_feeds_and_enters_dormancy_safely",
        "corpse_clutch_reacts_without_horde_or_conversion",
        "corpse_dual_owner_grave_command_and_loyalty_are_deterministic",
        "corpse_relationships_and_zombie_lifecycle_are_replaced",
        "corpse_save_reload_hazards_and_work_are_bounded"
    );
    private static final Set<String> ISOLATED_HELLHOUND = Set.of(
        "hellhound_acquisition_and_zombie_variants_are_contained",
        "hellhound_natural_group_pack_identity_excludes_outsiders",
        "hellhound_warning_commit_leash_and_return_are_bounded",
        "hellhound_scent_evidence_expires_without_omniscience",
        "hellhound_pack_roles_calls_and_member_loss_are_bounded",
        "hellhound_blocked_sectors_and_route_failures_back_off",
        "hellhound_bite_fire_recovery_and_ally_safety_are_exact",
        "hellhound_retreat_regroup_and_isolation_hysteresis_hold",
        "hellhound_fire_water_contact_and_conversion_contracts_hold",
        "hellhound_heat_rest_never_edits_world",
        "hellhound_animus_authority_follow_and_guard_are_safe",
        "hellhound_cure_is_transactional_and_preserves_exact_rules"
    );
    private static final Set<String> ISOLATED_HEX_BAT = Set.of(
        "hex_bat_roosts_by_day_and_sorties_at_night",
        "hex_bat_swoop_marks_and_releases_target_safely",
        "murderous_flock_protects_caster_and_calls_locally",
        "hex_bat_save_reload_hazard_and_work_are_bounded"
    );
    private static final Set<String> ISOLATED_BANSHEE = Set.of(
        "banshee_warns_at_risk_player_without_causing_harm",
        "banshee_laments_only_an_observed_death_and_returns_to_vigil",
        "banshee_recoils_from_attack_without_a_sonic_weapon",
        "banshee_save_reload_and_acquisition_contracts_are_preserved",
        "banshee_flight_hazard_feedback_and_work_are_bounded"
    );
    private static final Path HOBGOBLIN_ENVIRONMENT = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment",
        "hobgoblin_isolated.json"
    );

    private static final Set<String> ISOLATED_HOBGOBLIN_JOURNEY = Set.of(
        "hobgoblin_journey_identity_village_exclusion_and_migration",
        "hobgoblin_journey_trade_contract_and_relations",
        "hobgoblin_journey_caravan_family_and_camp_lifecycle",
        "hobgoblin_journey_work_hazard_defense_and_cleanup",
        "hobgoblin_journey_event_adapter_and_population_bounds"
    );

    private static final Set<String> ISOLATED_GOBLIN_ENCLAVE = Set.of(
        "goblin_enclave_identity_schedule_and_migration",
        "goblin_enclave_family_children_and_relations",
        "goblin_enclave_work_transactions_and_caps",
        "goblin_enclave_combat_assault_and_cleanup",
        "goblin_enclave_hazard_navigation_and_population_bounds"
    );

    private static final Set<String> ISOLATED_GOBLIN_PATRON = Set.of(
        "goblin_patrons_identity_offerings_and_migration",
        "stonebroker_parley_appraisal_and_combat_doctrine",
        "forgewarden_commission_ward_and_combat_doctrine",
        "goblin_patrons_accord_navigation_and_cleanup",
        "goblin_patrons_structural_caps_and_foreign_boundaries"
    );
    private static final Set<String> ISOLATED_DEATH = Set.of(
        "death_appointment_telegraphs_and_reaps_once",
        "death_complete_disguise_releases_appointment",
        "death_blocked_route_releases_after_three_failures",
        "death_reap_respects_vanilla_protection_and_attribution",
        "death_reload_does_not_replay_reap",
        "death_hazard_and_other_families_remain_isolated"
    );
    private static final Set<String> ISOLATED_LOST_SOUL_SPIRIT = Set.of(
        "lost_soul_petitions_then_settles_at_memorial",
        "lost_soul_binding_cancels_petition_without_combat",
        "spirit_wary_binding_transition_is_finite",
        "spirit_defends_once_with_attribution_then_recovers",
        "spectral_reload_hazard_and_family_isolation",
        "spectral_owner_race_and_route_failure_cleanup"
    );
    private static final Set<String> ISOLATED_COVEN_PRACTITIONERS = Set.of(
        "hedge_crone_warns_intruders_and_casts_contextual_hex",
        "hedge_crone_prepares_one_ward_and_releases_safely",
        "hedge_crone_save_reload_hazard_and_lifecycle_are_bounded",
        "circle_mage_recruits_follows_and_regenerates_owner",
        "circle_mages_study_and_defend_as_a_bounded_conclave",
        "circle_mage_save_reload_seer_and_work_are_bounded"
    );
    private static final Set<String> ISOLATED_POLTERGEIST = Set.of(
        "poltergeist_warns_lifts_throws_once_then_recovers",
        "poltergeist_missing_or_picked_prop_finishes_safely",
        "poltergeist_throw_preserves_item_stack_and_pickup",
        "poltergeist_dense_candidates_stay_capped_and_stable",
        "poltergeist_hazard_and_three_route_failures_cancel",
        "poltergeist_reload_does_not_replay_and_families_stay_isolated"
    );
    private static final Set<String> ISOLATED_ECHO_SPECTRE = Set.of(
        "echo_shade_records_and_replays_one_vector",
        "echo_shade_never_copies_player_state",
        "echo_shade_route_hazard_and_reload_cancel",
        "spectre_warns_one_witness_dreads_once_then_fades",
        "spectre_dread_does_not_refresh_or_spread",
        "echo_spectre_dense_candidates_stay_capped_and_stable",
        "echo_spectre_reload_does_not_replay",
        "echo_spectre_families_stay_isolated"
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

    @Test
    void onlyTheExactInfernalHierarchyFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(INFERNAL_HIERARCHY_ENVIRONMENT),
            "the isolated F07 Infernal Hierarchy environment resource must exist");
        final JsonObject environment = JsonParser.parseString(read(INFERNAL_HIERARCHY_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F07 Infernal Hierarchy environment must not mutate shared world state");
        assertEquals(10, ISOLATED_INFERNAL_HIERARCHY.size());
        final Set<String> registered = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registered.containsAll(ISOLATED_INFERNAL_HIERARCHY),
            "all ten exact F07 Infernal Hierarchy GameTests must be registered");
    }

    @Test
    void onlyTheExactImpFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(IMP_ENVIRONMENT),
            "the isolated F08 Imp environment resource must exist");
        final JsonObject environment = JsonParser.parseString(read(IMP_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F08 Imp environment must not mutate shared world state");
        assertEquals(12, ISOLATED_IMP.size());
        final Set<String> registered = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registered.containsAll(ISOLATED_IMP),
            "all twelve exact F08 Imp GameTests must be registered");
    }

    @Test
    void onlyTheExactEldritchWatcherFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(ELDRITCH_WATCHER_ENVIRONMENT),
            "the isolated F14 Eldritch Watcher environment resource must exist");
        final JsonObject environment = JsonParser.parseString(read(ELDRITCH_WATCHER_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F14 Eldritch Watcher environment must not mutate shared world state");
        assertEquals(4, ISOLATED_ELDRITCH_WATCHER.size());
        final Set<String> registered = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registered.containsAll(ISOLATED_ELDRITCH_WATCHER),
            "all four exact F14 Eldritch Watcher GameTests must be registered");
    }

    @Test
    void onlyTheExactCorpseFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(CORPSE_ENVIRONMENT),
            "the isolated F17 Corpse environment resource must exist");
        final JsonObject environment = JsonParser.parseString(read(CORPSE_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F17 Corpse environment must not mutate shared world state");
        assertEquals(6, ISOLATED_CORPSE.size());
        final Set<String> registered = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registered.containsAll(ISOLATED_CORPSE),
            "all six exact F17 Corpse GameTests must be registered");
    }

    @Test
    void onlyTheExactHellhoundFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(HELLHOUND_ENVIRONMENT),
            "the isolated F09 Hellhound environment resource must exist");
        final JsonObject environment = JsonParser.parseString(read(HELLHOUND_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F09 Hellhound environment must not mutate shared world state");
        assertEquals(12, ISOLATED_HELLHOUND.size());
        final Set<String> registered = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registered.containsAll(ISOLATED_HELLHOUND),
            "all twelve exact F09 Hellhound GameTests must be registered");
    }

    @Test
    void onlyTheExactHexBatFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(HEX_BAT_ENVIRONMENT),
            "the isolated F15 Hex Bat environment resource must exist");
        final JsonObject environment = JsonParser.parseString(read(HEX_BAT_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F15 Hex Bat environment must not mutate shared world state");
        assertEquals(4, ISOLATED_HEX_BAT.size());
        final Set<String> registered = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registered.containsAll(ISOLATED_HEX_BAT),
            "all four exact F15 Hex Bat GameTests must be registered");
    }

    @Test
    void onlyTheExactBansheeFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(BANSHEE_ENVIRONMENT),
            "the isolated F16 Banshee environment resource must exist");
        final JsonObject environment = JsonParser.parseString(read(BANSHEE_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F16 Banshee environment must not mutate shared world state");
        assertEquals(5, ISOLATED_BANSHEE.size());
        final Set<String> registered = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registered.containsAll(ISOLATED_BANSHEE),
            "all five exact F16 Banshee GameTests must be registered");
    }

    @Test
    void onlyTheExactHobgoblinJourneyFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(HOBGOBLIN_ENVIRONMENT),
            "the isolated F11 Hobgoblin environment resource must exist");
        final JsonObject environment =
            JsonParser.parseString(read(HOBGOBLIN_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F11 Hobgoblin environment must not mutate shared world state");
        assertEquals(5, ISOLATED_HOBGOBLIN_JOURNEY.size());
        final Set<String> registeredJourney = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registeredJourney.containsAll(ISOLATED_HOBGOBLIN_JOURNEY),
            "all five exact F11 Hobgoblin GameTests must be registered");
    }

    @Test
    void onlyTheExactGoblinPatronFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(GOBLIN_PATRON_ENVIRONMENT),
            "the isolated F12 Goblin Patron environment resource must exist");
        final JsonObject environment =
            JsonParser.parseString(read(GOBLIN_PATRON_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F12 Goblin Patron environment must not mutate shared world state");
        assertEquals(5, ISOLATED_GOBLIN_PATRON.size());
        final Set<String> registeredPatrons = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registeredPatrons.containsAll(ISOLATED_GOBLIN_PATRON),
            "all five exact F12 Goblin Patron GameTests must be registered");
    }

    @Test
    void onlyTheExactGoblinEnclaveFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(GOBLIN_ENVIRONMENT),
            "the isolated F10 Goblin environment resource must exist");
        final JsonObject environment = JsonParser.parseString(read(GOBLIN_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F10 Goblin environment must not mutate shared world state");
        assertEquals(5, ISOLATED_GOBLIN_ENCLAVE.size());
        final Set<String> registered = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registered.containsAll(ISOLATED_GOBLIN_ENCLAVE),
            "all five exact F10 Goblin GameTests must be registered");
    }

    @Test
    void onlyTheExactDeathFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(DEATH_ENVIRONMENT),
            "the isolated F18 Death environment resource must exist");
        final JsonObject environment = JsonParser.parseString(read(DEATH_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F18 Death environment must not mutate shared world state");
        assertEquals(6, ISOLATED_DEATH.size());
        final Set<String> registered = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registered.containsAll(ISOLATED_DEATH),
            "all six exact F18 Death GameTests must be registered");
    }

    @Test
    void onlyTheExactLostSoulAndSpiritFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(LOST_SOUL_SPIRIT_ENVIRONMENT),
            "the isolated F19 Lost Soul and Spirit environment resource must exist");
        final JsonObject environment =
            JsonParser.parseString(read(LOST_SOUL_SPIRIT_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F19 environment must not mutate shared world state");
        assertEquals(6, ISOLATED_LOST_SOUL_SPIRIT.size());
        final Set<String> registered = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registered.containsAll(ISOLATED_LOST_SOUL_SPIRIT),
            "all six exact F19 Lost Soul and Spirit GameTests must be registered");
    }

    @Test
    void onlyTheExactCovenPractitionerFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(COVEN_PRACTITIONERS_ENVIRONMENT),
            "the isolated F13 Coven Practitioner environment resource must exist");
        final JsonObject environment =
            JsonParser.parseString(read(COVEN_PRACTITIONERS_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F13 environment must not mutate shared world state");
        assertEquals(6, ISOLATED_COVEN_PRACTITIONERS.size());
        final Set<String> registered = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registered.containsAll(ISOLATED_COVEN_PRACTITIONERS),
            "all six exact F13 Coven Practitioner GameTests must be registered");
    }

    @Test
    void onlyTheExactPoltergeistFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(POLTERGEIST_ENVIRONMENT),
            "the isolated F20 Poltergeist environment resource must exist");
        final JsonObject environment =
            JsonParser.parseString(read(POLTERGEIST_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F20 environment must not mutate shared world state");
        assertEquals(6, ISOLATED_POLTERGEIST.size());
        final Set<String> registered = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registered.containsAll(ISOLATED_POLTERGEIST),
            "all six exact F20 Poltergeist GameTests must be registered");
    }

    @Test
    void onlyTheExactEchoShadeAndSpectreFixturesUseTheRegisteredNoOpEnvironment() {
        assertTrue(Files.exists(ECHO_SPECTRE_ENVIRONMENT),
            "the isolated F21 Echo Shade and Spectre environment resource must exist");
        final JsonObject environment =
            JsonParser.parseString(read(ECHO_SPECTRE_ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F21 environment must not mutate shared world state");
        assertEquals(8, ISOLATED_ECHO_SPECTRE.size());
        final Set<String> registered = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(registered.containsAll(ISOLATED_ECHO_SPECTRE),
            "all eight exact F21 Echo Shade and Spectre GameTests must be registered");
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
                            : ISOLATED_INFERNAL_HIERARCHY.contains(registration.id())
                                ? "warlockery:infernal_hierarchy_isolated"
                                : ISOLATED_IMP.contains(registration.id())
                                    ? "warlockery:imp_isolated"
                                    : ISOLATED_ELDRITCH_WATCHER.contains(registration.id())
                                        ? "warlockery:eldritch_watcher_isolated"
                                        : ISOLATED_CORPSE.contains(registration.id())
                                            ? "warlockery:corpse_isolated"
                                            : ISOLATED_HELLHOUND.contains(registration.id())
                                                ? "warlockery:hellhound_isolated"
                                                : ISOLATED_HEX_BAT.contains(registration.id())
                                                    ? "warlockery:hex_bat_isolated"
                                                    : ISOLATED_BANSHEE.contains(registration.id())
                                                        ? "warlockery:banshee_isolated"
                                                        : ISOLATED_DEATH.contains(registration.id())
                                                            ? "warlockery:death_isolated"
                                                            : ISOLATED_LOST_SOUL_SPIRIT.contains(registration.id())
                                                                ? "warlockery:lost_soul_spirit_isolated"
                                                                : ISOLATED_GOBLIN_ENCLAVE.contains(registration.id())
                                                                    ? "warlockery:goblin_isolated"
                                                                    : ISOLATED_COVEN_PRACTITIONERS.contains(registration.id())
                                                                        ? "warlockery:coven_practitioners_isolated"
                                                                        : ISOLATED_POLTERGEIST.contains(registration.id())
                                                                            ? "warlockery:poltergeist_isolated"
                                                                            : ISOLATED_ECHO_SPECTRE.contains(registration.id())
                                                                                ? "warlockery:echo_spectre_isolated"
                                                                                : ISOLATED_HOBGOBLIN_JOURNEY.contains(registration.id())
                                                                                    ? "warlockery:hobgoblin_isolated"
                                                                                    : ISOLATED_GOBLIN_PATRON.contains(registration.id())
                                                                                        ? "warlockery:goblin_patron_isolated"
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
