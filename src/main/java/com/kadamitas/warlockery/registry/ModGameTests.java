package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.brew.CauldronChalkCircleGameTests;
import com.kadamitas.warlockery.brew.SolidifyingBrewGameTests;
import com.kadamitas.warlockery.dream.SpiritWorldGameTests;
import com.kadamitas.warlockery.entity.TacticalCombatGameTests;
import com.kadamitas.warlockery.entity.VampireCourtGameTests;
import com.kadamitas.warlockery.entity.LycanVillagerGameTests;
import com.kadamitas.warlockery.entity.LycanPackGameTests;
import com.kadamitas.warlockery.entity.WerewolfHunterGameTests;
import com.kadamitas.warlockery.entity.BansheeGameTests;
import com.kadamitas.warlockery.entity.CovenPractitionerGameTests;
import com.kadamitas.warlockery.entity.LostSoulSpiritGameTests;
import com.kadamitas.warlockery.entity.PoltergeistGameTests;
import com.kadamitas.warlockery.entity.EchoShadeSpectreGameTests;
import com.kadamitas.warlockery.entity.EldritchWatcherGameTests;
import com.kadamitas.warlockery.entity.CorpseGameTests;
import com.kadamitas.warlockery.entity.DeathGameTests;
import com.kadamitas.warlockery.entity.InfernalHierarchyGameTests;
import com.kadamitas.warlockery.entity.HellhoundLifeGameTests;
import com.kadamitas.warlockery.entity.ImpGameTests;
import com.kadamitas.warlockery.entity.HazardEscapeGameTests;
import com.kadamitas.warlockery.entity.HexBatGameTests;
import com.kadamitas.warlockery.entity.AmbientActivityGameTests;
import com.kadamitas.warlockery.entity.SpouseAmbientGameTests;
import com.kadamitas.warlockery.entity.NamiLifeGameTests;
import com.kadamitas.warlockery.entity.NaamahCourtGameTests;
import com.kadamitas.warlockery.item.CircleTalismanGameTests;
import com.kadamitas.warlockery.item.BroomFlightGameTests;
import com.kadamitas.warlockery.item.BroomMotionGameTests;
import com.kadamitas.warlockery.item.SpiritLocatorGameTests;
import com.kadamitas.warlockery.item.VeilWaystoneGameTests;
import com.kadamitas.warlockery.ritual.HexMetalRitualGameTests;
import com.kadamitas.warlockery.ritual.RitualOutcomeGameTests;
import com.kadamitas.warlockery.ritual.WarlockeryGameTests;
import com.kadamitas.warlockery.ritual.BiomeRitualGameTests;
import com.kadamitas.warlockery.ritual.NamiRitualGameTests;
import com.kadamitas.warlockery.ritual.SeerCovenGameTests;
import com.kadamitas.warlockery.transformation.SupernaturalProgressionGameTests;
import com.kadamitas.warlockery.world.SettlementFortificationGameTests;
import com.kadamitas.warlockery.world.VillageAssaultGameTests;
import com.kadamitas.warlockery.world.VillageGuardGameTests;
import com.kadamitas.warlockery.world.GoblinSettlementLifeGameTests;
import com.kadamitas.warlockery.world.GoblinEnclaveGameTests;
import com.kadamitas.warlockery.entity.GoblinPatronGameTests;
import com.kadamitas.warlockery.world.HobgoblinJourneyGameTests;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.registries.DeferredRegister;

public final class ModGameTests {
    public static final DeferredRegister<Consumer<GameTestHelper>> REGISTRY =
        DeferredRegister.create(Registries.TEST_FUNCTION, Warlockery.MOD_ID);

    static {
        REGISTRY.register("ritual_catalog_loads", () -> WarlockeryGameTests::ritualCatalogLoads);
        REGISTRY.register("ritual_heat_metal_target_reaches_persistent_hex",
            () -> HexMetalRitualGameTests::heatMetalRitualTargetReachesThePersistentHex);
        REGISTRY.register("ritual_heat_metal_burns_metal_wearer",
            () -> HexMetalRitualGameTests::heatMetalBurnsAWearerOfTaggedMetal);
        REGISTRY.register("ritual_heat_metal_spares_metalless_victim",
            () -> HexMetalRitualGameTests::heatMetalSparesAVictimCarryingNoMetal);
        REGISTRY.register("ritual_heat_metal_cure_clears_hex",
            () -> HexMetalRitualGameTests::heatMetalCureRitualClearsTheHex);
        REGISTRY.register("ritual_every_hex_target_resolves",
            () -> HexMetalRitualGameTests::everyDatapackHexTargetResolvesInALiveRegistry);
        REGISTRY.register("ritual_heat_metal_pair_is_loaded",
            () -> HexMetalRitualGameTests::heatMetalRitualIsLoadedAndPairedWithItsCure);
        REGISTRY.register("ritual_hex_reaches_victims_in_radius",
            () -> RitualOutcomeGameTests::aHexOnlyReachesVictimsInsideTheDeclaredRadius);
        REGISTRY.register("ritual_unresolvable_binding_falls_back_to_radius",
            () -> RitualOutcomeGameTests::aBoundTargetInAnotherDimensionIsNotReachedByAHex);
        REGISTRY.register("ritual_noop_binding_fabricates_nothing",
            () -> RitualOutcomeGameTests::aRitualWithNothingToActOnReportsNoEffect);
        REGISTRY.register("ritual_every_loaded_ritual_passes_validation",
            () -> RitualOutcomeGameTests::everyLoadedRitualPassesTargetValidation);
        REGISTRY.register("biome_book_capture_persists_and_names",
            () -> BiomeRitualGameTests::biomeBookCapturePersistsAndNames);
        REGISTRY.register("climate_shift_uses_bound_book_target",
            () -> BiomeRitualGameTests::climateShiftUsesBoundBookTarget);
        REGISTRY.register("climate_shift_empowerment_and_stars_are_optional_and_capped",
            () -> BiomeRitualGameTests::climateShiftEmpowermentAndStarsAreOptionalAndCapped);
        REGISTRY.register("blood_audience_transforms_unmarried_nami",
            () -> NamiRitualGameTests::bloodAudienceTransformsUnmarriedNami);
        REGISTRY.register("blood_audience_protects_married_nami",
            () -> NamiRitualGameTests::bloodAudienceProtectsMarriedNami);
        REGISTRY.register("broom_mount_stores_damages_and_returns_exact_stack",
            () -> BroomFlightGameTests::mountStoresDamagesAndReturnsTheExactBroom);
        REGISTRY.register("broom_logout_returns_stack_before_player_save",
            () -> BroomFlightGameTests::logoutReturnsBroomBeforePlayerSave);
        REGISTRY.register("broom_legacy_creative_flight_state_is_removed",
            () -> BroomFlightGameTests::legacyCreativeFlightStateIsRemovedOnLogin);
        REGISTRY.register("broom_death_uses_vanilla_drop_keep_and_vanishing_rules",
            () -> BroomFlightGameTests::deathUsesVanillaDropKeepAndVanishingRules);
        REGISTRY.register("broom_mount_moves_forward_and_keeps_its_rider",
            () -> BroomMotionGameTests::mountedBroomMovesForwardAndKeepsItsRider);
        REGISTRY.register("ritual_sessions_reject_duplicate_centers",
            () -> WarlockeryGameTests::ritualSessionsRejectDuplicateCenters);
        REGISTRY.register("drinkable_custom_brew_applies_its_formula",
            () -> WarlockeryGameTests::drinkableCustomBrewAppliesItsFormula);
        REGISTRY.register("cauldron_reads_exact_independent_chalk_rings",
            () -> CauldronChalkCircleGameTests::cauldronReadsExactIndependentChalkRings);
        REGISTRY.register("sanctity_ward_repels_hostiles_immediately",
            () -> WarlockeryGameTests::sanctityWardRepelsHostilesImmediately);
        REGISTRY.register("summon_imp_creates_warlockery_creature",
            () -> WarlockeryGameTests::summonImpCreatesWarlockeryCreature);
        REGISTRY.register("murderous_flock_spawns_targeted_hex_bats",
            () -> WarlockeryGameTests::murderousFlockSpawnsTargetedHexBats);
        REGISTRY.register("winged_creatures_use_custom_entity_classes",
            () -> WarlockeryGameTests::wingedCreaturesUseCustomEntityClasses);
        REGISTRY.register("lycan_villager_trades_only_with_werewolves",
            () -> WarlockeryGameTests::lycanVillagerTradesOnlyWithWerewolves);
        REGISTRY.register("fertility_grows_and_cures",
            () -> WarlockeryGameTests::fertilityGrowsAndCures);
        REGISTRY.register("natures_power_repairs_ground",
            () -> WarlockeryGameTests::naturesPowerRepairsGround);
        REGISTRY.register("broken_earth_creates_fissure",
            () -> WarlockeryGameTests::brokenEarthCreatesFissure);
        REGISTRY.register("earths_wrath_moves_volcanic_fluid",
            () -> WarlockeryGameTests::earthsWrathMovesVolcanicFluid);
        REGISTRY.register("skys_wrath_calls_targeted_lightning",
            () -> WarlockeryGameTests::skysWrathCallsTargetedLightning);
        REGISTRY.register("hell_on_earth_uses_tagged_demons",
            () -> WarlockeryGameTests::hellOnEarthUsesTaggedDemons);
        REGISTRY.register("forestation_places_tagged_saplings",
            () -> WarlockeryGameTests::forestationPlacesTaggedSaplings);
        REGISTRY.register("all_registered_creatures_instantiate",
            () -> WarlockeryGameTests::allRegisteredCreaturesInstantiate);
        REGISTRY.register("goblins_raid_villagers_while_hobgoblins_remain_friendly",
            () -> WarlockeryGameTests::goblinsRaidVillagersWhileHobgoblinsRemainFriendly);
        REGISTRY.register("hobgoblin_trading_bypasses_village_guard_commissioning",
            () -> VillageGuardGameTests::hobgoblinTradingBypassesVillageGuardCommissioning);
        REGISTRY.register("goblin_trading_retains_its_customer",
            () -> VillageGuardGameTests::goblinTradingRetainsItsCustomer);
        REGISTRY.register("goblin_families_produce_matching_babies",
            () -> VillageGuardGameTests::goblinFamiliesProduceMatchingBabies);
        REGISTRY.register("goblin_hut_consumes_materials_and_respects_persistent_caps",
            () -> GoblinSettlementLifeGameTests::goblinHutConsumesMaterialsAndRespectsPersistentCaps);
        REGISTRY.register("goblin_children_gather_dance_and_gift_flowers",
            () -> GoblinSettlementLifeGameTests::goblinChildrenGatherDanceAndGiftFlowers);
        REGISTRY.register("goblin_tunnel_is_single_bounded_and_protects_containers",
            () -> GoblinSettlementLifeGameTests::goblinTunnelIsSingleBoundedAndProtectsContainers);
        REGISTRY.register("goblin_enclave_identity_schedule_and_migration",
            () -> GoblinEnclaveGameTests::goblinEnclaveIdentityScheduleAndMigration);
        REGISTRY.register("goblin_enclave_family_children_and_relations",
            () -> GoblinEnclaveGameTests::goblinEnclaveFamilyChildrenAndRelations);
        REGISTRY.register("goblin_enclave_work_transactions_and_caps",
            () -> GoblinEnclaveGameTests::goblinEnclaveWorkTransactionsAndCaps);
        REGISTRY.register("goblin_enclave_combat_assault_and_cleanup",
            () -> GoblinEnclaveGameTests::goblinEnclaveCombatAssaultAndCleanup);
        REGISTRY.register("goblin_enclave_hazard_navigation_and_population_bounds",
            () -> GoblinEnclaveGameTests::goblinEnclaveHazardNavigationAndPopulationBounds);
        REGISTRY.register("hobgoblin_journey_identity_village_exclusion_and_migration",
            () -> HobgoblinJourneyGameTests::hobgoblinJourneyIdentityVillageExclusionAndMigration);
        REGISTRY.register("hobgoblin_journey_trade_contract_and_relations",
            () -> HobgoblinJourneyGameTests::hobgoblinJourneyTradeContractAndRelations);
        REGISTRY.register("hobgoblin_journey_caravan_family_and_camp_lifecycle",
            () -> HobgoblinJourneyGameTests::hobgoblinJourneyCaravanFamilyAndCampLifecycle);
        REGISTRY.register("hobgoblin_journey_work_hazard_defense_and_cleanup",
            () -> HobgoblinJourneyGameTests::hobgoblinJourneyWorkHazardDefenseAndCleanup);
        REGISTRY.register("hobgoblin_journey_event_adapter_and_population_bounds",
            () -> HobgoblinJourneyGameTests::hobgoblinJourneyEventAdapterAndPopulationBounds);
        REGISTRY.register("goblin_patrons_identity_offerings_and_migration",
            () -> GoblinPatronGameTests::goblinPatronsIdentityOfferingsAndMigration);
        REGISTRY.register("stonebroker_parley_appraisal_and_combat_doctrine",
            () -> GoblinPatronGameTests::stonebrokerParleyAppraisalAndCombatDoctrine);
        REGISTRY.register("forgewarden_commission_ward_and_combat_doctrine",
            () -> GoblinPatronGameTests::forgewardenCommissionWardAndCombatDoctrine);
        REGISTRY.register("goblin_patrons_accord_navigation_and_cleanup",
            () -> GoblinPatronGameTests::goblinPatronsAccordNavigationAndCleanup);
        REGISTRY.register("goblin_patrons_structural_caps_and_foreign_boundaries",
            () -> GoblinPatronGameTests::goblinPatronsStructuralCapsAndForeignBoundaries);
        REGISTRY.register("goblin_raid_wave_is_grouped_and_coordinated",
            () -> VillageGuardGameTests::goblinRaidWaveIsGroupedAndCoordinated);
        REGISTRY.register("hobgoblins_flee_human_villagers_and_keep_custom_professions",
            () -> VillageGuardGameTests::hobgoblinsFleeHumanVillagersAndKeepCustomProfessions);
        REGISTRY.register("ranged_creature_routes_behind_cover_when_player_draws_bow",
            () -> TacticalCombatGameTests::rangedCreatureRoutesBehindCoverWhenPlayerDrawsBow);
        REGISTRY.register("melee_creature_disengages_from_unreachable_attack_slit",
            () -> TacticalCombatGameTests::meleeCreatureDisengagesFromUnreachableAttackSlit);
        REGISTRY.register("vulnerable_mob_routes_away_from_contact_hazards",
            () -> HazardEscapeGameTests::vulnerableMobRoutesAwayFromContactHazards);
        REGISTRY.register("drowning_mob_routes_from_water_to_dry_ground",
            () -> HazardEscapeGameTests::drowningMobRoutesFromWaterToDryGround);
        REGISTRY.register("demon_builds_one_temporary_snow_hearth",
            () -> AmbientActivityGameTests::demonBuildsOneTemporarySnowHearth);
        REGISTRY.register("ent_plants_one_loose_sapling_without_duplicating_it",
            () -> AmbientActivityGameTests::entPlantsOneLooseSaplingWithoutDuplicatingIt);
        REGISTRY.register("human_village_builds_a_closed_stone_defense",
            () -> SettlementFortificationGameTests::humanVillageBuildsAClosedStoneDefense);
        REGISTRY.register("hobgoblin_village_builds_a_closed_wood_defense",
            () -> SettlementFortificationGameTests::hobgoblinVillageBuildsAClosedWoodDefense);
        REGISTRY.register("varied_terrain_keeps_level_patrol_deck_and_supported_walls",
            () -> SettlementFortificationGameTests::variedTerrainKeepsLevelPatrolDeckAndSupportedWalls);
        REGISTRY.register("repeated_fortification_does_not_stack_or_duplicate_guards",
            () -> SettlementFortificationGameTests::repeatedFortificationDoesNotStackOrDuplicateGuards);
        REGISTRY.register("protected_village_blocks_survive_fortification",
            () -> SettlementFortificationGameTests::protectedVillageBlocksSurviveFortification);
        REGISTRY.register("infected_villager_transforms_and_restores_with_identity",
            () -> VillageAssaultGameTests::infectedVillagerTransformsAndRestoresWithIdentity);
        REGISTRY.register("both_settlements_receive_tagged_silver_guards",
            () -> VillageAssaultGameTests::bothSettlementsReceiveTaggedSilverGuards);
        REGISTRY.register("guards_retaliate_against_players_with_silver_bolts",
            () -> VillageAssaultGameTests::guardsRetaliateAgainstPlayersWithSilverBolts);
        REGISTRY.register("low_health_raiders_escape_as_bat_and_wolf",
            () -> VillageAssaultGameTests::lowHealthRaidersEscapeAsBatAndWolf);
        REGISTRY.register("approach_forms_cross_closed_fortification_and_reveal_inside",
            () -> VillageAssaultGameTests::approachFormsCrossClosedFortificationAndRevealInside);
        REGISTRY.register("blood_drained_trade_lock_uses_forge_interaction_event",
            () -> VillageAssaultGameTests::bloodDrainedTradeLockUsesForgeInteractionEvent);
        REGISTRY.register("only_raid_contributors_receive_settlement_rewards",
            () -> VillageAssaultGameTests::onlyRaidContributorsReceiveSettlementRewards);
        REGISTRY.register("hobgoblin_supernatural_variants_exist_only_as_raid_markers",
            () -> VillageAssaultGameTests::hobgoblinSupernaturalVariantsExistOnlyAsRaidMarkers);
        REGISTRY.register("compact_waves_preserve_counts_powers_and_settlement_variants",
            () -> VillageAssaultGameTests::compactWavesPreserveCountsPowersAndSettlementVariants);
        REGISTRY.register("objective_targeting_skips_completed_or_unavailable_residents",
            () -> VillageAssaultGameTests::objectiveTargetingSkipsCompletedOrUnavailableResidents);
        REGISTRY.register("assault_objectives_rewards_and_cleanup_remain_isolated",
            () -> VillageAssaultGameTests::assaultObjectivesRewardsAndCleanupRemainIsolated);
        REGISTRY.register("werewolf_hunter_carries_silver_ammunition",
            () -> WarlockeryGameTests::werewolfHunterCarriesSilverAmmunition);
        REGISTRY.register("wolf_altar_final_trial_completes_once",
            () -> WarlockeryGameTests::wolfAltarFinalTrialCompletesOnce);
        REGISTRY.register("death_guard_uses_totem_recovery_without_vanilla_trigger",
            () -> WarlockeryGameTests::deathGuardUsesTotemRecoveryWithoutVanillaTrigger);
        REGISTRY.register("hunger_guard_restores_hunger_and_saturation",
            () -> WarlockeryGameTests::hungerGuardRestoresHungerAndSaturation);
        REGISTRY.register("mending_doll_trades_its_durability",
            () -> WarlockeryGameTests::mendingDollTradesItsDurability);
        REGISTRY.register("shelved_mending_dolls_repair_once_per_second",
            () -> WarlockeryGameTests::shelvedMendingDollsRepairOncePerSecond);
        REGISTRY.register("self_applied_doll_remains_active_on_shelf",
            () -> WarlockeryGameTests::selfAppliedDollRemainsActiveOnShelf);
        REGISTRY.register("altar_attachments_install_render_and_shift_remove",
            () -> WarlockeryGameTests::altarAttachmentsInstallRenderAndShiftRemove);
        REGISTRY.register("chalk_places_connected_glyphs_and_spends_durability",
            () -> WarlockeryGameTests::chalkPlacesConnectedGlyphsAndSpendsDurability);
        REGISTRY.register("unsupported_chalk_vanishes_without_dropping_glyph_items",
            () -> WarlockeryGameTests::unsupportedChalkVanishesWithoutDroppingGlyphItems);
        REGISTRY.register("circle_talisman_captures_and_restores_full_large_ring",
            () -> CircleTalismanGameTests::circleTalismanCapturesAndRestoresFullLargeRing);
        REGISTRY.register("circle_talisman_missing_support_fails_atomically",
            () -> CircleTalismanGameTests::circleTalismanMissingSupportFailsAtomically);
        REGISTRY.register("circle_talisman_occupied_target_fails_atomically",
            () -> CircleTalismanGameTests::circleTalismanOccupiedTargetFailsAtomically);
        REGISTRY.register("scattered_chalk_marks_do_not_form_a_ritual_ring",
            () -> WarlockeryGameTests::scatteredChalkMarksDoNotFormARitualRing);
        REGISTRY.register("malformed_chalk_ring_is_rejected_even_with_the_right_counts",
            () -> WarlockeryGameTests::malformedChalkRingIsRejectedEvenWithTheRightCounts);
        REGISTRY.register("complete_large_chalk_ring_is_recognized",
            () -> WarlockeryGameTests::completeLargeChalkRingIsRecognized);
        REGISTRY.register("glyph_transformation_changes_only_the_selected_ring",
            () -> WarlockeryGameTests::glyphTransformationChangesOnlyTheSelectedRing);
        REGISTRY.register("hex_guard_blocks_hostile_hex",
            () -> WarlockeryGameTests::hexGuardBlocksHostileHex);
        REGISTRY.register("hex_behavior_applies_and_removes_its_effect",
            () -> WarlockeryGameTests::hexBehaviorAppliesAndRemovesItsEffect);
        REGISTRY.register("elemental_guard_dolls_use_vanilla_recovery",
            () -> WarlockeryGameTests::elementalGuardDollsUseVanillaRecovery);
        REGISTRY.register("machine_profile_processes_a_real_inventory",
            () -> WarlockeryGameTests::machineProfileProcessesARealInventory);
        REGISTRY.register("common_material_and_wood_tags_are_populated",
            () -> WarlockeryGameTests::commonMaterialAndWoodTagsArePopulated);
        REGISTRY.register("pipe_automation_uses_sided_item_handlers",
            () -> WarlockeryGameTests::pipeAutomationUsesSidedItemHandlers);
        REGISTRY.register("fluid_pipes_connect_to_liquid_machines",
            () -> WarlockeryGameTests::fluidPipesConnectToLiquidMachines);
        REGISTRY.register("seer_stone_calls_only_the_owners_recruited_circle_mages",
            () -> SeerCovenGameTests::seerStoneCallsOnlyTheOwnersRecruitedCircleMages);
        REGISTRY.register("seer_stone_leaves_ordinary_divination_available",
            () -> SeerCovenGameTests::seerStoneLeavesOrdinaryDivinationAvailable);
        REGISTRY.register("failed_veil_binding_reports_only_once_per_drop",
            () -> VeilWaystoneGameTests::failedVeilBindingReportsOnlyOncePerDrop);
        REGISTRY.register("spirit_locator_requires_an_exact_ritual_chalk_ring",
            () -> SpiritLocatorGameTests::spiritLocatorRequiresAnExactRitualChalkRing);
        REGISTRY.register("veil_ring_binds_waystones_to_its_center",
            () -> VeilWaystoneGameTests::veilRingBindsWaystonesToItsCenter);
        REGISTRY.register("veil_ring_transposes_living_and_dropped_travellers",
            () -> VeilWaystoneGameTests::veilRingTransposesLivingAndDroppedTravellers);
        REGISTRY.register("solidifying_stone_converts_every_hollow_tears_state",
            () -> SolidifyingBrewGameTests::stoneConvertsEveryHollowTearsState);
        REGISTRY.register("solidifying_dirt_converts_every_hollow_tears_state",
            () -> SolidifyingBrewGameTests::dirtConvertsEveryHollowTearsState);
        REGISTRY.register("solidifying_sand_converts_every_hollow_tears_state",
            () -> SolidifyingBrewGameTests::sandConvertsEveryHollowTearsState);
        REGISTRY.register("solidifying_sandstone_converts_every_hollow_tears_state",
            () -> SolidifyingBrewGameTests::sandstoneConvertsEveryHollowTearsState);
        REGISTRY.register("solidifying_erosion_clears_terrain_below_every_hollow_tears_state",
            () -> SolidifyingBrewGameTests::erosionClearsTerrainBelowEveryHollowTearsState);
        REGISTRY.register("torn_page_use_reveals_only_the_next_immortal_lesson",
            () -> SupernaturalProgressionGameTests::tornPageUseRevealsOnlyTheNextImmortalLesson);
        REGISTRY.register("vampire_path_initiates_diagnoses_and_advances",
            () -> SupernaturalProgressionGameTests::vampirePathInitiatesDiagnosesAndAdvances);
        REGISTRY.register("vampire_creation_rejects_foreign_goblet_then_completes_path",
            () -> SupernaturalProgressionGameTests::vampireCreationRejectsForeignGobletThenCompletesPath);
        REGISTRY.register("werewolf_altar_diagnoses_and_advances",
            () -> SupernaturalProgressionGameTests::werewolfAltarDiagnosesAndAdvances);
        REGISTRY.register("transformed_werewolves_dig_dirt_and_sand_faster",
            () -> SupernaturalProgressionGameTests::transformedWerewolvesDigDirtAndSandFaster);
        REGISTRY.register("spirit_world_entry_creates_state_body_and_diagnostic",
            () -> SpiritWorldGameTests::entryCreatesStateBodyAndDiagnostic);
        REGISTRY.register("spirit_world_carry_in_and_exports_restore_without_duplication",
            () -> SpiritWorldGameTests::carryInAndExportsRestoreWithoutDuplication);
        REGISTRY.register("sleeping_apple_forces_only_a_standard_nightmare",
            () -> SpiritWorldGameTests::sleepingAppleForcesOnlyAStandardNightmare);
        REGISTRY.register("icy_needle_wakes_and_is_spent",
            () -> SpiritWorldGameTests::icyNeedleWakesAndIsSpent);
        REGISTRY.register("fatal_dream_damage_wakes_before_death",
            () -> SpiritWorldGameTests::fatalDreamDamageWakesBeforeDeath);
        REGISTRY.register("destroyed_sleeping_body_forces_wake",
            () -> SpiritWorldGameTests::destroyedBodyForcesWake);
        REGISTRY.register("spirit_world_inhibits_every_circle_ritual",
            () -> SpiritWorldGameTests::spiritWorldInhibitsEveryCircleRitual);
        REGISTRY.register("demonic_nightmare_flag_persists_in_session",
            () -> SpiritWorldGameTests::demonicNightmareFlagPersistsInSession);
        REGISTRY.register("spouse_cooks_one_meat_and_delivers_one_meal",
            () -> SpouseAmbientGameTests::spouseCooksOneMeatAndDeliversOneMeal);
        REGISTRY.register("spouse_rejects_occupied_furnace_without_taking_meat",
            () -> SpouseAmbientGameTests::spouseRejectsOccupiedFurnaceWithoutTakingMeat);
        REGISTRY.register("spouse_kiss_persists_cooldown",
            () -> SpouseAmbientGameTests::spouseKissPersistsCooldown);
        REGISTRY.register("nami_daily_routine_returns_home",
            () -> NamiLifeGameTests::dailyRoutineReturnsHome);
        REGISTRY.register("nami_greeting_builds_bounded_trust",
            () -> NamiLifeGameTests::greetingBuildsBoundedTrust);
        REGISTRY.register("nami_ward_protects_spouse_and_releases_stale_threat",
            () -> NamiLifeGameTests::wardProtectsSpouseAndReleasesStaleThreat);
        REGISTRY.register("naamah_court_phases_latch_and_recover",
            () -> NaamahCourtGameTests::courtPhasesLatchAndRecover);
        REGISTRY.register("naamah_trial_defeat_concludes_audience",
            () -> NaamahCourtGameTests::trialDefeatConcludesAudience);
        REGISTRY.register("naamah_sunlight_water_and_singular_lifecycle",
            () -> NaamahCourtGameTests::sunlightWaterAndSingularLifecycle);
        REGISTRY.register("naamah_court_releases_invalid_targets",
            () -> NaamahCourtGameTests::courtReleasesInvalidTargets);
        REGISTRY.register("vampire_court_day_shelter_and_night_hunt",
            () -> VampireCourtGameTests::dayShelterAndNightHunt);
        REGISTRY.register("vampire_court_feeding_and_reports_remain_distinct",
            () -> VampireCourtGameTests::feedingAndReportsRemainDistinct);
        REGISTRY.register("blood_thrall_binds_intercepts_and_wavers",
            () -> VampireCourtGameTests::bloodThrallBindsInterceptsAndWavers);
        REGISTRY.register("vampire_court_assault_composition_preserves_contracts",
            () -> VampireCourtGameTests::assaultCompositionPreservesContracts);
        REGISTRY.register("vampire_court_identity_targets_and_failures_are_bounded",
            () -> VampireCourtGameTests::identityTargetsAndFailuresAreBounded);
        REGISTRY.register("vampire_court_population_caps_hold",
            () -> VampireCourtGameTests::populationCapsHold);
        REGISTRY.register("lycan_brain_routine_resumes_after_watch", () -> LycanVillagerGameTests::brainRoutineResumesAfterWatch);
        REGISTRY.register("lycan_signature_offers_survive_profession_and_reload", () -> LycanVillagerGameTests::signatureOffersSurviveProfessionAndReload);
        REGISTRY.register("lycan_signature_offers_reconcile_without_duplicates", () -> LycanVillagerGameTests::signatureOffersReconcileWithoutDuplicates);
        REGISTRY.register("lycan_trade_success_awards_familiarity_once", () -> LycanVillagerGameTests::tradeSuccessAwardsFamiliarityOnce);
        REGISTRY.register("lycan_familiarity_caps_and_evicts_deterministically", () -> LycanVillagerGameTests::familiarityCapsAndEvictsDeterministically);
        REGISTRY.register("lycan_full_moon_watch_is_bounded", () -> LycanVillagerGameTests::fullMoonWatchIsBounded);
        REGISTRY.register("lycan_bonded_resident_attack_warns_then_defends", () -> LycanVillagerGameTests::bondedResidentAttackWarnsThenDefends);
        REGISTRY.register("lycan_unbonded_attack_does_not_trigger_protection", () -> LycanVillagerGameTests::unbondedAttackDoesNotTriggerProtection);
        REGISTRY.register("lycan_direct_attacker_uses_attribute_melee_damage", () -> LycanVillagerGameTests::directAttackerUsesAttributeMeleeDamage);
        REGISTRY.register("lycan_low_health_withdraws_and_releases_target", () -> LycanVillagerGameTests::lowHealthWithdrawsAndReleasesTarget);
        REGISTRY.register("lycan_blocked_route_backs_off_after_three_failures", () -> LycanVillagerGameTests::blockedRouteBacksOffAfterThreeFailures);
        REGISTRY.register("lycan_destroyed_poi_cancels_override", () -> LycanVillagerGameTests::destroyedPoiCancelsOverride);
        REGISTRY.register("lycan_reload_discards_transient_combat_claims", () -> LycanVillagerGameTests::reloadDiscardsTransientCombatClaims);
        REGISTRY.register("lycan_hazard_wins_end_of_tick_movement", () -> LycanVillagerGameTests::hazardWinsEndOfTickMovement);
        REGISTRY.register("lycan_replacement_paths_do_not_transfer_sentinel_state", () -> LycanVillagerGameTests::replacementPathsDoNotTransferSentinelState);
        REGISTRY.register("lycan_variants_keep_identity_and_drop_zombie_lifecycle",
            () -> LycanPackGameTests::lycanVariantsKeepIdentityAndDropZombieLifecycle);
        REGISTRY.register("werewolf_hunt_assigns_roles_and_replaces_coordinator",
            () -> LycanPackGameTests::werewolfHuntAssignsRolesAndReplacesCoordinator);
        REGISTRY.register("feral_lycan_tracks_prey_warns_bonds_and_avoids_settlement",
            () -> LycanPackGameTests::feralLycanTracksPreyWarnsBondsAndAvoidsSettlement);
        REGISTRY.register("lycan_schedules_hazards_and_silver_counters_remain_distinct",
            () -> LycanPackGameTests::lycanSchedulesHazardsAndSilverCountersRemainDistinct);
        REGISTRY.register("lycan_family_targets_respect_kin_players_and_other_families",
            () -> LycanPackGameTests::lycanFamilyTargetsRespectKinPlayersAndOtherFamilies);
        REGISTRY.register("werewolf_trap_hunt_assault_and_infection_contracts_remain_exact",
            () -> LycanPackGameTests::werewolfTrapHuntAssaultAndInfectionContractsRemainExact);
        REGISTRY.register("lycan_actions_cancel_across_failure_save_and_reload",
            () -> LycanPackGameTests::lycanActionsCancelAcrossFailureSaveAndReload);
        REGISTRY.register("lycan_population_work_stays_within_declared_caps",
            () -> LycanPackGameTests::lycanPopulationWorkStaysWithinDeclaredCaps);
        REGISTRY.register("hunter_identity_loadout_and_raid_containment",
            () -> WerewolfHunterGameTests::hunterIdentityLoadoutAndRaidContainment);
        REGISTRY.register("hunter_warrant_matrix_and_evidence_expiry",
            () -> WerewolfHunterGameTests::hunterWarrantMatrixAndEvidenceExpiry);
        REGISTRY.register("hunter_warns_tracks_and_returns_to_anchor",
            () -> WerewolfHunterGameTests::hunterWarnsTracksAndReturnsToAnchor);
        REGISTRY.register("hunter_crossbow_consumes_finite_silver_ammunition",
            () -> WerewolfHunterGameTests::hunterCrossbowConsumesFiniteSilverAmmunition);
        REGISTRY.register("hunter_protected_crossfire_cancels_shot",
            () -> WerewolfHunterGameTests::hunterProtectedCrossfireCancelsShot);
        REGISTRY.register("hunter_retreat_search_and_hazard_preemption_are_bounded",
            () -> WerewolfHunterGameTests::hunterRetreatSearchAndHazardPreemptionAreBounded);
        REGISTRY.register("hunter_resupply_caps_without_duplication",
            () -> WerewolfHunterGameTests::hunterResupplyCapsWithoutDuplication);
        REGISTRY.register("silver_hunt_transaction_deduplicates_and_rolls_back",
            () -> WerewolfHunterGameTests::silverHuntTransactionDeduplicatesAndRollsBack);
        REGISTRY.register("hunter_reload_reconciles_semantic_state_only",
            () -> WerewolfHunterGameTests::hunterReloadReconcilesSemanticStateOnly);
        REGISTRY.register("hunter_route_failures_back_off_and_release",
            () -> WerewolfHunterGameTests::hunterRouteFailuresBackOffAndRelease);
        REGISTRY.register("infernal_ranks_normalize_without_identity_drift",
            () -> InfernalHierarchyGameTests::infernalRanksNormalizeWithoutIdentityDrift);
        REGISTRY.register("demon_conflicting_owners_preserve_direct_pact",
            () -> InfernalHierarchyGameTests::demonConflictingOwnersPreserveDirectPact);
        REGISTRY.register("demon_truce_morale_retreat_and_return_are_bounded",
            () -> InfernalHierarchyGameTests::demonTruceMoraleRetreatAndReturnAreBounded);
        REGISTRY.register("archfiend_anchor_squad_and_ember_front_are_bounded",
            () -> InfernalHierarchyGameTests::archfiendAnchorSquadAndEmberFrontAreBounded);
        REGISTRY.register("regent_court_orders_phase_and_reinforcements_cleanup",
            () -> InfernalHierarchyGameTests::regentCourtOrdersPhaseAndReinforcementsCleanup);
        REGISTRY.register("infernal_leader_loss_and_unloaded_authority_cancel_execution",
            () -> InfernalHierarchyGameTests::infernalLeaderLossAndUnloadedAuthorityCancelExecution);
        REGISTRY.register("infernal_save_reload_truncates_and_migrates_state",
            () -> InfernalHierarchyGameTests::infernalSaveReloadTruncatesAndMigratesState);
        REGISTRY.register("infernal_collision_border_and_chunk_edge_fail_safely",
            () -> InfernalHierarchyGameTests::infernalCollisionBorderAndChunkEdgeFailSafely);
        REGISTRY.register("infernal_acquisition_paths_preserve_targets_and_contracts",
            () -> InfernalHierarchyGameTests::infernalAcquisitionPathsPreserveTargetsAndContracts);
        REGISTRY.register("infernal_population_caps_and_scan_budgets_hold",
            () -> InfernalHierarchyGameTests::infernalPopulationCapsAndScanBudgetsHold);
        REGISTRY.register("imp_contract_binding_favor_and_spells_remain_exact",
            () -> ImpGameTests::impContractBindingFavorAndSpellsRemainExact);
        REGISTRY.register("imp_familiar_bind_recall_and_owner_conflict_remain_exact",
            () -> ImpGameTests::impFamiliarBindRecallAndOwnerConflictRemainExact);
        REGISTRY.register("imp_follow_watch_and_scout_return_are_bounded",
            () -> ImpGameTests::impFollowWatchAndScoutReturnAreBounded);
        REGISTRY.register("imp_scout_interrupt_reload_and_report_once",
            () -> ImpGameTests::impScoutInterruptReloadAndReportOnce);
        REGISTRY.register("imp_curiosity_inspects_without_storage_mutation",
            () -> ImpGameTests::impCuriosityInspectsWithoutStorageMutation);
        REGISTRY.register("imp_perch_collision_border_and_chunk_edge_fail_safely",
            () -> ImpGameTests::impPerchCollisionBorderAndChunkEdgeFailSafely);
        REGISTRY.register("imp_ranged_lane_windup_and_retreat_are_bounded",
            () -> ImpGameTests::impRangedLaneWindupAndRetreatAreBounded);
        REGISTRY.register("imp_projectile_allies_griefing_and_protected_blocks_are_safe",
            () -> ImpGameTests::impProjectileAlliesGriefingAndProtectedBlocksAreSafe);
        REGISTRY.register("imp_bound_environmental_immunity_does_not_transfer_damage",
            () -> ImpGameTests::impBoundEnvironmentalImmunityDoesNotTransferDamage);
        REGISTRY.register("imp_infernal_orders_authority_conflicts_and_leader_loss_are_safe",
            () -> ImpGameTests::impInfernalOrdersAuthorityConflictsAndLeaderLossAreSafe);
        REGISTRY.register("imp_state_migration_corruption_and_expiry_are_bounded",
            () -> ImpGameTests::impStateMigrationCorruptionAndExpiryAreBounded);
        REGISTRY.register("imp_population_cadence_and_operation_budgets_hold",
            () -> ImpGameTests::impPopulationCadenceAndOperationBudgetsHold);
        REGISTRY.register("eldritch_watcher_vigil_observes_and_escalates_on_reciprocal_gaze",
            () -> EldritchWatcherGameTests::vigilObservesAndEscalatesOnReciprocalGaze);
        REGISTRY.register("eldritch_watcher_revelation_is_bound_visible_and_attributed",
            () -> EldritchWatcherGameTests::revelationIsBoundVisibleAndAttributed);
        REGISTRY.register("eldritch_watcher_binding_warning_lure_and_return_remain_local",
            () -> EldritchWatcherGameTests::bindingWarningLureAndReturnRemainLocal);
        REGISTRY.register("eldritch_watcher_save_reload_focus_hazard_and_work_are_bounded",
            () -> EldritchWatcherGameTests::saveReloadFocusHazardAndWorkAreBounded);
        REGISTRY.register("corpse_raise_dead_identity_owner_and_acquisition_are_preserved",
            () -> CorpseGameTests::corpseRaiseDeadIdentityOwnerAndAcquisitionArePreserved);
        REGISTRY.register("corpse_scavenges_feeds_and_enters_dormancy_safely",
            () -> CorpseGameTests::corpseScavengesFeedsAndEntersDormancySafely);
        REGISTRY.register("corpse_clutch_reacts_without_horde_or_conversion",
            () -> CorpseGameTests::corpseClutchReactsWithoutHordeOrConversion);
        REGISTRY.register("corpse_dual_owner_grave_command_and_loyalty_are_deterministic",
            () -> CorpseGameTests::corpseDualOwnerGraveCommandAndLoyaltyAreDeterministic);
        REGISTRY.register("corpse_relationships_and_zombie_lifecycle_are_replaced",
            () -> CorpseGameTests::corpseRelationshipsAndZombieLifecycleAreReplaced);
        REGISTRY.register("corpse_save_reload_hazards_and_work_are_bounded",
            () -> CorpseGameTests::corpseSaveReloadHazardsAndWorkAreBounded);
        REGISTRY.register("hellhound_acquisition_and_zombie_variants_are_contained",
            () -> HellhoundLifeGameTests::acquisitionAndZombieVariantsAreContained);
        REGISTRY.register("hellhound_natural_group_pack_identity_excludes_outsiders",
            () -> HellhoundLifeGameTests::naturalGroupPackIdentityExcludesOutsiders);
        REGISTRY.register("hellhound_warning_commit_leash_and_return_are_bounded",
            () -> HellhoundLifeGameTests::warningCommitLeashAndReturnAreBounded);
        REGISTRY.register("hellhound_scent_evidence_expires_without_omniscience",
            () -> HellhoundLifeGameTests::scentEvidenceExpiresWithoutOmniscience);
        REGISTRY.register("hellhound_pack_roles_calls_and_member_loss_are_bounded",
            () -> HellhoundLifeGameTests::packRolesCallsAndMemberLossAreBounded);
        REGISTRY.register("hellhound_blocked_sectors_and_route_failures_back_off",
            () -> HellhoundLifeGameTests::blockedSectorsAndRouteFailuresBackOff);
        REGISTRY.register("hellhound_bite_fire_recovery_and_ally_safety_are_exact",
            () -> HellhoundLifeGameTests::biteFireRecoveryAndAllySafetyAreExact);
        REGISTRY.register("hellhound_retreat_regroup_and_isolation_hysteresis_hold",
            () -> HellhoundLifeGameTests::retreatRegroupAndIsolationHysteresisHold);
        REGISTRY.register("hellhound_fire_water_contact_and_conversion_contracts_hold",
            () -> HellhoundLifeGameTests::fireWaterContactAndConversionContractsHold);
        REGISTRY.register("hellhound_heat_rest_never_edits_world",
            () -> HellhoundLifeGameTests::heatRestNeverEditsWorld);
        REGISTRY.register("hellhound_animus_authority_follow_and_guard_are_safe",
            () -> HellhoundLifeGameTests::animusAuthorityFollowAndGuardAreSafe);
        REGISTRY.register("hellhound_cure_is_transactional_and_preserves_exact_rules",
            () -> HellhoundLifeGameTests::cureIsTransactionalAndPreservesExactRules);
        REGISTRY.register("hex_bat_roosts_by_day_and_sorties_at_night",
            () -> HexBatGameTests::hexBatRoostsByDayAndSortiesAtNight);
        REGISTRY.register("hex_bat_swoop_marks_and_releases_target_safely",
            () -> HexBatGameTests::hexBatSwoopMarksAndReleasesTargetSafely);
        REGISTRY.register("murderous_flock_protects_caster_and_calls_locally",
            () -> HexBatGameTests::murderousFlockProtectsCasterAndCallsLocally);
        REGISTRY.register("hex_bat_save_reload_hazard_and_work_are_bounded",
            () -> HexBatGameTests::hexBatSaveReloadHazardAndWorkAreBounded);
        REGISTRY.register("banshee_warns_at_risk_player_without_causing_harm",
            () -> BansheeGameTests::bansheeWarnsAtRiskPlayerWithoutCausingHarm);
        REGISTRY.register("banshee_laments_only_an_observed_death_and_returns_to_vigil",
            () -> BansheeGameTests::bansheeLamentsOnlyAnObservedDeathAndReturnsToVigil);
        REGISTRY.register("banshee_recoils_from_attack_without_a_sonic_weapon",
            () -> BansheeGameTests::bansheeRecoilsFromAttackWithoutASonicWeapon);
        REGISTRY.register("banshee_save_reload_and_acquisition_contracts_are_preserved",
            () -> BansheeGameTests::bansheeSaveReloadAndAcquisitionContractsArePreserved);
        REGISTRY.register("banshee_flight_hazard_feedback_and_work_are_bounded",
            () -> BansheeGameTests::bansheeFlightHazardFeedbackAndWorkAreBounded);
        REGISTRY.register("death_appointment_telegraphs_and_reaps_once",
            () -> DeathGameTests::deathAppointmentTelegraphsAndReapsOnce);
        REGISTRY.register("death_complete_disguise_releases_appointment",
            () -> DeathGameTests::deathCompleteDisguiseReleasesAppointment);
        REGISTRY.register("death_blocked_route_releases_after_three_failures",
            () -> DeathGameTests::deathBlockedRouteReleasesAfterThreeFailures);
        REGISTRY.register("death_reap_respects_vanilla_protection_and_attribution",
            () -> DeathGameTests::deathReapRespectsVanillaProtectionAndAttribution);
        REGISTRY.register("death_reload_does_not_replay_reap",
            () -> DeathGameTests::deathReloadDoesNotReplayReap);
        REGISTRY.register("death_hazard_and_other_families_remain_isolated",
            () -> DeathGameTests::deathHazardAndOtherFamiliesRemainIsolated);
        REGISTRY.register("lost_soul_petitions_then_settles_at_memorial",
            () -> LostSoulSpiritGameTests::lostSoulPetitionsThenSettlesAtMemorial);
        REGISTRY.register("lost_soul_binding_cancels_petition_without_combat",
            () -> LostSoulSpiritGameTests::lostSoulBindingCancelsPetitionWithoutCombat);
        REGISTRY.register("spirit_wary_binding_transition_is_finite",
            () -> LostSoulSpiritGameTests::spiritWaryBindingTransitionIsFinite);
        REGISTRY.register("spirit_defends_once_with_attribution_then_recovers",
            () -> LostSoulSpiritGameTests::spiritDefendsOnceWithAttributionThenRecovers);
        REGISTRY.register("spectral_reload_hazard_and_family_isolation",
            () -> LostSoulSpiritGameTests::spectralReloadHazardAndFamilyIsolation);
        REGISTRY.register("spectral_owner_race_and_route_failure_cleanup",
            () -> LostSoulSpiritGameTests::spectralOwnerRaceAndRouteFailureCleanup);
        REGISTRY.register("hedge_crone_warns_intruders_and_casts_contextual_hex",
            () -> CovenPractitionerGameTests::hedgeCroneWarnsIntrudersAndCastsContextualHex);
        REGISTRY.register("hedge_crone_prepares_one_ward_and_releases_safely",
            () -> CovenPractitionerGameTests::hedgeCronePreparesOneWardAndReleasesSafely);
        REGISTRY.register("hedge_crone_save_reload_hazard_and_lifecycle_are_bounded",
            () -> CovenPractitionerGameTests::hedgeCroneSaveReloadHazardAndLifecycleAreBounded);
        REGISTRY.register("circle_mage_recruits_follows_and_regenerates_owner",
            () -> CovenPractitionerGameTests::circleMageRecruitsFollowsAndRegeneratesOwner);
        REGISTRY.register("circle_mages_study_and_defend_as_a_bounded_conclave",
            () -> CovenPractitionerGameTests::circleMagesStudyAndDefendAsABoundedConclave);
        REGISTRY.register("circle_mage_save_reload_seer_and_work_are_bounded",
            () -> CovenPractitionerGameTests::circleMageSaveReloadSeerAndWorkAreBounded);
        REGISTRY.register("poltergeist_warns_lifts_throws_once_then_recovers",
            () -> PoltergeistGameTests::poltergeistWarnsLiftsThrowsOnceThenRecovers);
        REGISTRY.register("poltergeist_missing_or_picked_prop_finishes_safely",
            () -> PoltergeistGameTests::poltergeistMissingOrPickedPropFinishesSafely);
        REGISTRY.register("poltergeist_throw_preserves_item_stack_and_pickup",
            () -> PoltergeistGameTests::poltergeistThrowPreservesItemStackAndPickup);
        REGISTRY.register("poltergeist_dense_candidates_stay_capped_and_stable",
            () -> PoltergeistGameTests::poltergeistDenseCandidatesStayCappedAndStable);
        REGISTRY.register("poltergeist_hazard_and_three_route_failures_cancel",
            () -> PoltergeistGameTests::poltergeistHazardAndThreeRouteFailuresCancel);
        REGISTRY.register("poltergeist_reload_does_not_replay_and_families_stay_isolated",
            () -> PoltergeistGameTests::poltergeistReloadDoesNotReplayAndFamiliesStayIsolated);
        REGISTRY.register("echo_shade_records_and_replays_one_vector",
            () -> EchoShadeSpectreGameTests::echoShadeRecordsOneVectorAndAnswersIt);
        REGISTRY.register("echo_shade_never_copies_player_state",
            () -> EchoShadeSpectreGameTests::echoShadeNeverCopiesPlayerState);
        REGISTRY.register("echo_shade_route_hazard_and_reload_cancel",
            () -> EchoShadeSpectreGameTests::echoShadeRouteHazardAndReloadCancel);
        REGISTRY.register("spectre_warns_one_witness_dreads_once_then_fades",
            () -> EchoShadeSpectreGameTests::spectreWarnsOneWitnessDreadsOnceThenFades);
        REGISTRY.register("spectre_dread_does_not_refresh_or_spread",
            () -> EchoShadeSpectreGameTests::spectreDreadDoesNotRefreshOrSpread);
        REGISTRY.register("echo_spectre_dense_candidates_stay_capped_and_stable",
            () -> EchoShadeSpectreGameTests::echoSpectreDenseCandidatesStayCappedAndStable);
        REGISTRY.register("echo_spectre_reload_does_not_replay",
            () -> EchoShadeSpectreGameTests::echoSpectreReloadDoesNotReplay);
        REGISTRY.register("echo_spectre_families_stay_isolated",
            () -> EchoShadeSpectreGameTests::echoSpectreFamiliesStayIsolated);
    }

    private ModGameTests() {
    }
}
