package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
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
    private static final Path ENVIRONMENTS = Path.of(
        "src", "main", "resources", "data", "warlockery", "test_environment"
    );
    private static final Path ISOLATION_TEMPLATE = Path.of(
        "src", "main", "resources", "data", "warlockery", "structure", "empty32x32x32.nbt"
    );
    private static final String GENERIC_STRUCTURE = "warlockery:empty32x32x32";
    private static final String RETIRED_GENERIC_STRUCTURE = "forge:empty3x3x3";
    /**
     * These fixtures require the vanilla three-block staging cell: each passed alone on that
     * geometry and failed alone in the 32-cube experiment. Keep this allowlist explicit so a
     * future fixture cannot silently opt out of spatial isolation.
     */
    private static final Set<String> GEOMETRY_SENSITIVE_THREE_CUBE_FIXTURES = Set.of(
        "approach_forms_cross_closed_fortification_and_reveal_inside",
        "circle_talisman_captures_and_restores_full_large_ring",
        "earths_wrath_moves_volcanic_fluid",
        "ent_hazard_escape_and_cancellation_are_deterministic",
        "hellhound_animus_authority_follow_and_guard_are_safe",
        "hobgoblin_village_builds_a_closed_wood_defense",
        "hobgoblins_flee_human_villagers_and_keep_custom_professions",
        "human_village_builds_a_closed_stone_defense",
        "repeated_fortification_does_not_stack_or_duplicate_guards"
    );
    private static final Set<String> FORCE_TICKED_FIFTEEN_CUBE_FIXTURES = Set.of(
        "imp_ranged_lane_windup_and_retreat_are_bounded"
    );
    private static final Set<String> RELEASE_1_5_1_FIXTURES = Set.of(
        "vampire_blood_replaces_hunger_and_regenerates",
        "vampire_sunlight_ignores_fire_resistance",
        "werewolf_prey_drive_hunts_valid_prey",
        "werewolf_prey_drive_releases_invalid_target"
    );
    private static final Pattern REGISTRATION = Pattern.compile(
        "REGISTRY\\.register\\(\\\"([^\\\"]+)\\\",\\s*\\(\\)\\s*->\\s*([A-Za-z0-9_]+)::([A-Za-z0-9_]+)\\);"
    );
    private static final Set<String> LARGE_MACHINE_FIXTURES = Set.of(
        "machine_profile_processes_a_real_inventory",
        "pipe_automation_uses_sided_item_handlers"
    );
    private static final Set<String> ISOLATED_SPECTRAL_FAMILIAR = Set.of(
        "spectral_familiar_surveys_sample_and_returns",
        "spectral_familiar_owner_defense_interrupts_then_returns",
        "spectral_familiar_scan_and_route_caps_hold",
        "spectral_familiar_reload_does_not_replay_signal",
        "spectral_familiar_two_player_ownership_isolated",
        "spectral_familiar_neighbors_and_world_stay_untouched"
    );
    private static final Set<String> ISOLATED_ENT = Set.of(
        "ent_felling_rouses_warns_then_strikes_within_its_stand",
        "ent_ignores_presence_and_settles_to_its_anchor",
        "ent_stand_alarm_and_log_break_spawn_stay_bounded",
        "ent_grove_tending_is_bounded_and_respects_mobgriefing",
        "ent_hazard_escape_and_cancellation_are_deterministic",
        "ent_save_reload_variants_and_golem_lifecycle_are_replaced"
    );
    private static final Set<String> ISOLATED_THORNED_PURSUER = Set.of(
        "thorned_pursuer_bays_before_it_commits_to_a_course",
        "thorned_pursuer_courses_by_trail_and_never_teleports",
        "thorned_pursuer_snares_once_and_presses_on_cadence",
        "thorned_pursuer_escort_is_owned_capped_and_released",
        "thorned_pursuer_breaks_recovers_and_cancels_deterministically",
        "thorned_pursuer_save_reload_and_zombie_lifecycle_are_replaced"
    );
    private static final Set<String> ISOLATED_LIVING_ROOTS = Set.of(
        "mandrake_extraction_wail_and_resettle_are_bounded",
        "mandrake_disturbance_requires_fresh_attribution_and_sight",
        "dreamroot_threshold_dream_requires_rooted_ground",
        "dreamroot_bulb_population_and_mutation_stay_capped",
        "living_roots_hazard_escape_and_cancellation_are_deterministic",
        "living_roots_save_reload_and_zombie_lifecycle_are_replaced"
    );
    private static final Set<String> ISOLATED_BRAMBLE_COLOSSUS = Set.of(
        "bramble_colossus_post_sweep_displays_then_threshes",
        "bramble_colossus_allowlist_and_maker_are_never_struck",
        "bramble_colossus_circuit_and_stance_stay_inside_the_post",
        "bramble_colossus_nerve_falters_and_recovers_deterministically",
        "bramble_colossus_hazard_escape_and_cancellation_are_deterministic",
        "bramble_colossus_save_reload_and_zombie_lifecycle_are_replaced"
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
    /**
     * Fixtures that move the level clock. {@code setTime} writes state shared by every fixture in
     * a batch, so two of them running side by side flip the sky out from under each other. Each
     * gets a batch of its own, which means its environment is named after the fixture rather than
     * after its family.
     */
    private static final Set<String> CLOCK_ISOLATED = Set.of(
        "hex_bat_roosts_by_day_and_sorties_at_night",
        "hex_bat_swoop_marks_and_releases_target_safely",
        "naamah_court_phases_latch_and_recover",
        "naamah_sunlight_water_and_singular_lifecycle",
        "naamah_court_releases_invalid_targets",
        "naamah_court_mending_stops_when_the_gaze_breaks",
        "naamah_court_bind_holds_one_and_surge_catches_the_ground"
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
    private static final Set<String> ISOLATED_ILLUSION_COPIES = Set.of(
        "illusion_creeper_tell_collapses_without_blast",
        "illusion_spider_snare_is_bounded_and_breaks",
        "illusion_zombie_absorbs_without_reward_or_alert",
        "illusion_copies_deal_no_damage_and_never_touch_vanilla_ai",
        "illusion_copies_hazard_escape_and_cancellation_are_deterministic",
        "illusion_copies_save_reload_and_zombie_lifecycle_are_replaced"
    );
    private static final Set<String> ISOLATED_GLASS_DOPPELGANGER = Set.of(
        "glass_doppelganger_presents_one_subject_without_copying_data",
        "glass_doppelganger_shadow_band_holds_and_never_closes",
        "glass_doppelganger_recognition_ends_the_presentation_and_withdraws",
        "glass_doppelganger_answers_only_attributed_damage",
        "glass_doppelganger_hazard_escape_and_cancellation_are_deterministic",
        "glass_doppelganger_save_reload_and_zombie_lifecycle_are_replaced"
    );
    private static final Set<String> ISOLATED_SPECTRAL_STEEDS = Set.of(
        "steed_owner_only_control_and_safe_dismount",
        "pale_steed_bond_gait_fatigue_and_rest",
        "pale_steed_balks_without_fear_or_ejection",
        "nightmare_accelerates_and_warns_only_legal_hostiles",
        "unbound_nightmare_remains_dream_hostile",
        "steed_rest_releases_lost_support_without_hay_mutation",
        "steed_two_player_caps_auras_and_owl_isolation"
    );
    private static final Set<String> ISOLATED_ANIMAL_FAMILIAR = Set.of(
        "animal_familiars_are_three_distinct_bodies",
        "familiar_binding_honours_the_vanilla_latch_and_refuses_the_contract_one",
        "familiar_cat_claims_a_household_and_patrols_it",
        "owl_perch_and_toad_shelter_stay_species_specific",
        "familiar_owner_defence_is_one_lease_and_reload_never_replays",
        "familiar_home_claim_reaches_past_the_innermost_ring",
        "unbound_familiars_persist_and_no_latch_is_disturbed",
        "a_summoned_familiar_acts_on_what_it_is_given",
        "no_familiar_ever_gains_a_door_breaking_goal",
        "the_three_species_reach_ins_are_three_different_questions",
        "familiar_binding_converts_vanilla_cat_and_frog_transactionally",
        "owl_natural_spawn_contract_is_forest_only_and_sparse"
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
    /**
     * The ritual participant scan reaches eight blocks, which is wider than a test arena, so this fixture is
     * given its own environment to keep it out of a batch with neighbours whose players and Mages it would
     * otherwise count.
     */
    private static final Set<String> ISOLATED_COVEN_ATTRIBUTION = Set.of(
        "ritual_two_covens_are_counted_separately"
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
    private static final Set<String> ISOLATED_IRONBOUND_SENTINEL = Set.of(
        "ironbound_sentinel_charge_wakes_stands_down_and_resumes",
        "ironbound_sentinel_ward_bars_and_repels_only_within_sight",
        "ironbound_sentinel_permitted_parties_are_never_bound_or_repelled",
        "ironbound_sentinel_strain_seizes_and_stands_down_without_rampage",
        "ironbound_sentinel_hazard_preempts_episode_and_keeps_its_station",
        "ironbound_sentinel_save_reload_and_zombie_lifecycle_are_replaced"
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
    private static final Set<String> ISOLATED_UMBRAL_SIGIL = Set.of(
        "umbral_sigil_traces_three_vertices_and_strikes_once",
        "umbral_sigil_target_escape_breaks_unfinished_seal",
        "umbral_sigil_route_hazard_and_damage_cancel",
        "umbral_sigil_dense_candidates_stay_capped_and_stable",
        "umbral_sigil_reload_never_replays_close_or_strike",
        "umbral_sigil_families_wards_and_world_stay_isolated"
    );
    private static final Set<String> ISOLATED_STORM_SIMIAN = Set.of(
        "storm_simian_canopy_route_is_supported_and_bounded",
        "storm_simian_blocked_route_backs_off",
        "storm_simian_alarm_is_local_and_legal",
        "storm_simian_storm_observation_mutates_no_world_state",
        "storm_simian_curiosity_does_not_move_or_take_items",
        "storm_simian_charged_gust_consumes_once",
        "storm_simian_reload_clears_transient_claims",
        "storm_simian_preserves_owner_support",
        "storm_simian_excludes_owl_steed_familiar_and_imp_systems"
    );

    private static final Set<String> ISOLATED_PARASYTIC_LOUSE = Set.of(
        "parasytic_louse_marks_before_it_attaches_to_one_host",
        "parasytic_louse_feeds_on_a_capped_ladder_and_delivers_once",
        "parasytic_louse_term_expires_and_grooming_frees_the_host",
        "parasytic_louse_redirect_route_is_bounded_and_fires_once",
        "parasytic_louse_reload_replaces_the_zombie_lifecycle"
    );

    private static final List<FixtureFamily> ISOLATED_FAMILIES = List.of(
        new FixtureFamily("vampire_court_isolated", ISOLATED_VAMPIRE_COURT),
        new FixtureFamily("lycan_villager_isolated", ISOLATED_LYCAN_VILLAGER),
        new FixtureFamily("lycan_pack_isolated", ISOLATED_LYCAN_PACK),
        new FixtureFamily("werewolf_hunter_isolated", ISOLATED_WEREWOLF_HUNTER),
        new FixtureFamily("infernal_hierarchy_isolated", ISOLATED_INFERNAL_HIERARCHY),
        new FixtureFamily("imp_isolated", ISOLATED_IMP),
        new FixtureFamily("eldritch_watcher_isolated", ISOLATED_ELDRITCH_WATCHER),
        new FixtureFamily("corpse_isolated", ISOLATED_CORPSE),
        new FixtureFamily("hellhound_isolated", ISOLATED_HELLHOUND),
        new FixtureFamily("hex_bat_isolated", ISOLATED_HEX_BAT),
        new FixtureFamily("banshee_isolated", ISOLATED_BANSHEE),
        new FixtureFamily("goblin_isolated", ISOLATED_GOBLIN_ENCLAVE),
        new FixtureFamily("hobgoblin_isolated", ISOLATED_HOBGOBLIN_JOURNEY),
        new FixtureFamily("goblin_patron_isolated", ISOLATED_GOBLIN_PATRON),
        new FixtureFamily("illusion_copies_isolated", ISOLATED_ILLUSION_COPIES),
        new FixtureFamily("glass_doppelganger_isolated", ISOLATED_GLASS_DOPPELGANGER),
        new FixtureFamily("spectral_steeds_isolated", ISOLATED_SPECTRAL_STEEDS),
        new FixtureFamily("animal_familiar_isolated", ISOLATED_ANIMAL_FAMILIAR),
        new FixtureFamily("death_isolated", ISOLATED_DEATH),
        new FixtureFamily("lost_soul_spirit_isolated", ISOLATED_LOST_SOUL_SPIRIT),
        new FixtureFamily("coven_practitioners_isolated", ISOLATED_COVEN_PRACTITIONERS),
        new FixtureFamily("coven_attribution_isolated", ISOLATED_COVEN_ATTRIBUTION),
        new FixtureFamily("poltergeist_isolated", ISOLATED_POLTERGEIST),
        new FixtureFamily("echo_spectre_isolated", ISOLATED_ECHO_SPECTRE),
        new FixtureFamily("umbral_sigil_isolated", ISOLATED_UMBRAL_SIGIL),
        new FixtureFamily("ironbound_sentinel_isolated", ISOLATED_IRONBOUND_SENTINEL),
        new FixtureFamily("storm_simian_isolated", ISOLATED_STORM_SIMIAN),
        new FixtureFamily("parasytic_louse_isolated", ISOLATED_PARASYTIC_LOUSE),
        new FixtureFamily("spectral_familiar_isolated", ISOLATED_SPECTRAL_FAMILIAR),
        new FixtureFamily("ent_isolated", ISOLATED_ENT),
        new FixtureFamily("thorned_pursuer_isolated", ISOLATED_THORNED_PURSUER),
        new FixtureFamily("living_roots_isolated", ISOLATED_LIVING_ROOTS),
        new FixtureFamily("bramble_colossus_isolated", ISOLATED_BRAMBLE_COLOSSUS)
    );
    private static final Map<String, String> ISOLATED_ENVIRONMENT_BY_FIXTURE = isolatedEnvironments();

    @Test
    void isolatedFixtureFamiliesUseRegisteredNoOpEnvironments() {
        final Set<String> registered = registrations().stream()
            .map(Registration::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        final Set<String> assigned = new LinkedHashSet<>();
        for (final FixtureFamily family : ISOLATED_FAMILIES) {
            final Path path = ENVIRONMENTS.resolve(family.environment() + ".json");
            assertTrue(Files.exists(path), () -> "missing isolated environment " + path);
            final JsonObject environment = JsonParser.parseString(read(path)).getAsJsonObject();
            assertEquals("minecraft:all_of", environment.get("type").getAsString(), path.toString());
            assertTrue(environment.getAsJsonArray("definitions").isEmpty(), path.toString());
            assertTrue(registered.containsAll(family.fixtures()),
                () -> "missing registrations for " + family.environment());
            assertTrue(family.fixtures().stream().allMatch(assigned::add),
                () -> "fixture assigned to multiple isolated environments: " + family.environment());
        }
    }

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
    void genericFixturesUseTheOwnedThirtyTwoCubeTemplate() {
        assertTrue(Files.exists(ISOLATION_TEMPLATE), "missing owned isolation template");
        final CompoundTag template = readCompressed(ISOLATION_TEMPLATE);
        final var size = template.getListOrEmpty("size");
        assertEquals(32, size.getIntOr(0, 0), "isolation template width");
        assertEquals(32, size.getIntOr(1, 0), "isolation template height");
        assertEquals(32, size.getIntOr(2, 0), "isolation template depth");
        assertTrue(template.getListOrEmpty("blocks").isEmpty(), "isolation template must be empty");
        assertTrue(template.getListOrEmpty("entities").isEmpty(), "isolation template must be entity-free");

        final Set<String> bespokeFixtures = bespokeFixtures();
        final Set<String> fixtureIds = fixtureIds();
        assertEquals(26, bespokeFixtures.size(), "bespoke fixtures must retain their dedicated templates");
        assertTrue(fixtureIds.containsAll(RELEASE_1_5_1_FIXTURES), "missing 1.5.1 fixtures");

        final Set<String> ownedGenericFixtures = new LinkedHashSet<>(fixtureIds);
        ownedGenericFixtures.removeAll(bespokeFixtures);
        ownedGenericFixtures.removeAll(RELEASE_1_5_1_FIXTURES);
        assertEquals(338, ownedGenericFixtures.size(), "owned generic fixture count");
        assertTrue(ownedGenericFixtures.containsAll(GEOMETRY_SENSITIVE_THREE_CUBE_FIXTURES),
            "approved three-cube fixture missing");
        assertEquals(9, GEOMETRY_SENSITIVE_THREE_CUBE_FIXTURES.size(), "three-cube allowlist size");
        assertEquals(1, FORCE_TICKED_FIFTEEN_CUBE_FIXTURES.size(), "force-ticked 15-cube allowlist size");

        final long ownedThirtyTwoCubeCount = ownedGenericFixtures.stream()
            .filter(fixtureId -> GENERIC_STRUCTURE.equals(readFixture(fixtureId).get("structure").getAsString()))
            .count();
        final long ownedThreeCubeCount = ownedGenericFixtures.stream()
            .filter(fixtureId -> RETIRED_GENERIC_STRUCTURE.equals(readFixture(fixtureId).get("structure").getAsString()))
            .count();
        final long ownedFifteenCubeCount = ownedGenericFixtures.stream()
            .filter(fixtureId -> "forge:empty15x15x15".equals(readFixture(fixtureId).get("structure").getAsString()))
            .count();
        assertEquals(328, ownedThirtyTwoCubeCount, "owned 32-cube generic fixture count");
        assertEquals(9, ownedThreeCubeCount, "owned three-cube generic fixture count");
        assertEquals(1, ownedFifteenCubeCount, "owned force-ticked 15-cube generic fixture count");

        for (final String fixtureId : fixtureIds) {
            final String structure = readFixture(fixtureId).get("structure").getAsString();
            if (bespokeFixtures.contains(fixtureId)) {
                assertEquals("forge:empty15x15x15", structure, fixtureId);
            } else if (GEOMETRY_SENSITIVE_THREE_CUBE_FIXTURES.contains(fixtureId)) {
                assertEquals(RETIRED_GENERIC_STRUCTURE, structure, fixtureId);
            } else if (FORCE_TICKED_FIFTEEN_CUBE_FIXTURES.contains(fixtureId)) {
                assertEquals("forge:empty15x15x15", structure, fixtureId);
            } else {
                assertEquals(GENERIC_STRUCTURE, structure, fixtureId);
            }
        }
    }

    @Test
    void heightmapSensitiveSpawnsUseScopedFixtureSettings() {
        final JsonObject goblinRaid = readFixture("goblin_raid_wave_is_grouped_and_coordinated");
        assertTrue(goblinRaid.get("sky_access").getAsBoolean(), "goblin raid needs the fixture roof removed");
        assertEquals(
            "warlockery:goblin_raid_isolated",
            environmentDelegate(goblinRaid),
            "goblin raid must own its temporary difficulty setting"
        );

        final JsonObject environment = JsonParser.parseString(
            read(environment("goblin_raid_isolated"))
        ).getAsJsonObject();
        assertEquals("minecraft:difficulty", environment.get("type").getAsString());
        assertEquals("normal", environment.get("difficulty").getAsString());

        final JsonObject hellOnEarth = readFixture("hell_on_earth_uses_tagged_demons");
        assertTrue(hellOnEarth.get("sky_access").getAsBoolean(),
            "Hell on Earth needs the fixture roof removed");
    }

    private void assertFixtureAndMethod(final Registration registration) {
        final JsonObject fixture = readFixture(registration.id());
        assertEquals("minecraft:function", fixture.get("type").getAsString(), registration.id());
        assertEquals("warlockery:" + registration.id(), fixture.get("function").getAsString(), registration.id());
        assertEquals(
            expectedEnvironment(registration.id()),
            environmentDelegate(fixture),
            registration.id()
        );
        assertEquals(
            expectedStructure(registration.id()),
            fixture.get("structure").getAsString(),
            registration.id()
        );
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

    private static Set<String> bespokeFixtures() {
        final Set<String> fixtures = new LinkedHashSet<>();
        fixtures.addAll(ISOLATED_THORNED_PURSUER);
        fixtures.addAll(ISOLATED_BRAMBLE_COLOSSUS);
        fixtures.addAll(ISOLATED_ILLUSION_COPIES);
        fixtures.addAll(ISOLATED_GLASS_DOPPELGANGER);
        fixtures.addAll(LARGE_MACHINE_FIXTURES);
        return Set.copyOf(fixtures);
    }

    private static String expectedEnvironment(final String fixture) {
        if (CLOCK_ISOLATED.contains(fixture)) {
            return "warlockery:" + fixture;
        }
        if ("goblin_raid_wave_is_grouped_and_coordinated".equals(fixture)) {
            return "warlockery:goblin_raid_isolated";
        }
        final String environment = ISOLATED_ENVIRONMENT_BY_FIXTURE.get(fixture);
        return environment == null ? "minecraft:default" : "warlockery:" + environment;
    }

    private static String expectedStructure(final String fixture) {
        if (bespokeFixtures().contains(fixture)) {
            return "forge:empty15x15x15";
        }
        if (FORCE_TICKED_FIFTEEN_CUBE_FIXTURES.contains(fixture)) {
            return "forge:empty15x15x15";
        }
        return GEOMETRY_SENSITIVE_THREE_CUBE_FIXTURES.contains(fixture)
            ? RETIRED_GENERIC_STRUCTURE
            : GENERIC_STRUCTURE;
    }

    private static String environmentDelegate(final JsonObject fixture) {
        final JsonObject isolated = fixture.getAsJsonObject("environment");
        assertEquals("warlockery:isolated", isolated.get("type").getAsString());
        return isolated.get("delegate").getAsString();
    }

    private static Path environment(final String id) {
        return ENVIRONMENTS.resolve(id + ".json");
    }

    private static Map<String, String> isolatedEnvironments() {
        final Map<String, String> environments = new LinkedHashMap<>();
        for (final FixtureFamily family : ISOLATED_FAMILIES) {
            for (final String fixture : family.fixtures()) {
                if (environments.put(fixture, family.environment()) != null) {
                    throw new IllegalStateException("Fixture assigned twice: " + fixture);
                }
            }
        }
        return Map.copyOf(environments);
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static CompoundTag readCompressed(final Path path) {
        try {
            return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private record Registration(String id, String owner, String method) {
    }

    private record FixtureFamily(String environment, Set<String> fixtures) {
    }
}
