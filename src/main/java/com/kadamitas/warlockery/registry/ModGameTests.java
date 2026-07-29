package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.ritual.WarlockeryGameTests;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModGameTests {
    public static final DeferredRegister<Consumer<GameTestHelper>> REGISTRY =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, Warlockery.MOD_ID);

    static {
        REGISTRY.register("ritual_catalog_loads", () -> WarlockeryGameTests::ritualCatalogLoads);
        REGISTRY.register("ritual_sessions_reject_duplicate_centers",
            () -> WarlockeryGameTests::ritualSessionsRejectDuplicateCenters);
        REGISTRY.register("drinkable_custom_brew_applies_its_formula",
            () -> WarlockeryGameTests::drinkableCustomBrewAppliesItsFormula);
        REGISTRY.register("sanctity_ward_repels_hostiles_immediately",
            () -> WarlockeryGameTests::sanctityWardRepelsHostilesImmediately);
        REGISTRY.register("summon_imp_creates_warlockery_creature",
            () -> WarlockeryGameTests::summonImpCreatesWarlockeryCreature);
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
        REGISTRY.register("werewolf_hunter_carries_silver_ammunition",
            () -> WarlockeryGameTests::werewolfHunterCarriesSilverAmmunition);
        REGISTRY.register("wolf_altar_final_trial_awards_horn_once",
            () -> WarlockeryGameTests::wolfAltarFinalTrialAwardsHornOnce);
        REGISTRY.register("death_guard_uses_totem_recovery_without_vanilla_trigger",
            () -> WarlockeryGameTests::deathGuardUsesTotemRecoveryWithoutVanillaTrigger);
        REGISTRY.register("hunger_guard_restores_hunger_and_saturation",
            () -> WarlockeryGameTests::hungerGuardRestoresHungerAndSaturation);
        REGISTRY.register("mending_doll_trades_its_durability",
            () -> WarlockeryGameTests::mendingDollTradesItsDurability);
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
    }

    private ModGameTests() {
    }
}
