package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.brew.CauldronChalkCircleGameTests;
import com.kadamitas.warlockery.brew.SolidifyingBrewGameTests;
import com.kadamitas.warlockery.dream.SpiritWorldGameTests;
import com.kadamitas.warlockery.item.CircleTalismanGameTests;
import com.kadamitas.warlockery.item.BroomFlightGameTests;
import com.kadamitas.warlockery.item.BroomMotionGameTests;
import com.kadamitas.warlockery.item.SpiritLocatorGameTests;
import com.kadamitas.warlockery.item.VeilWaystoneGameTests;
import com.kadamitas.warlockery.ritual.BiomeRitualGameTests;
import com.kadamitas.warlockery.ritual.WarlockeryGameTests;
import com.kadamitas.warlockery.ritual.NamiRitualGameTests;
import com.kadamitas.warlockery.ritual.SeerCovenGameTests;
import com.kadamitas.warlockery.transformation.SupernaturalProgressionGameTests;
import com.kadamitas.warlockery.world.SettlementFortificationGameTests;
import com.kadamitas.warlockery.world.VillageAssaultGameTests;
import com.kadamitas.warlockery.world.VillageGuardGameTests;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModGameTests {
    public static final DeferredRegister<Consumer<GameTestHelper>> REGISTRY =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, Warlockery.MOD_ID);

    static {
        REGISTRY.register("ritual_catalog_loads", () -> WarlockeryGameTests::ritualCatalogLoads);
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
        REGISTRY.register("goblin_raid_wave_is_grouped_and_coordinated",
            () -> VillageGuardGameTests::goblinRaidWaveIsGroupedAndCoordinated);
        REGISTRY.register("hobgoblins_flee_human_villagers_and_keep_custom_professions",
            () -> VillageGuardGameTests::hobgoblinsFleeHumanVillagersAndKeepCustomProfessions);
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
    }

    private ModGameTests() {
    }
}
