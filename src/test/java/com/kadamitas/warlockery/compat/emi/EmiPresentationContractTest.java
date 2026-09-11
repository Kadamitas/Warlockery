package com.kadamitas.warlockery.compat.emi;

import static org.junit.jupiter.api.Assertions.*;
import com.kadamitas.warlockery.crafting.MachineRecipeDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class EmiPresentationContractTest {
    @BeforeAll static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }
    @Test void compactPagesReachEveryLineWithoutDuplicationOrLoss() {
        var pages = new EmiTextPages(137, 7);
        var reached = new ArrayList<Integer>();
        for (int page = 0; page < pages.pageCount(); page++) {
            for (int line = pages.start(); line < pages.end(); line++) reached.add(line);
            pages.move(1);
        }
        assertEquals(java.util.stream.IntStream.range(0, 137).boxed().toList(), reached);
        assertEquals(0, pages.start());
        pages.move(-1);
        assertEquals(133, pages.start());
        assertEquals(137, pages.end());
    }

    @Test void emptyAndTinyLayoutsCannotDivideByZero() {
        assertEquals(1, new EmiTextPages(0, 0).pageCount());
        var pages = new EmiTextPages(3, 0);
        assertEquals(3, pages.pageCount());
        pages.move(1);
        assertEquals(1, pages.start());
    }

    @Test void jarRoutingIsIndependentOfIngredientOrderAndNeverTargetsFuel() {
        var oven = recipe("alchemical_oven", List.of(input("warlockery:ingredient_clay_jar"), input("minecraft:birch_sapling")), false);
        assertEquals(List.of(3, 0), EmiMachineTransferPlan.slots(oven));
        assertFalse(EmiMachineTransferPlan.slots(oven).contains(4));
        var distillery = recipe("distillery", List.of(input("minecraft:bone_meal"), input("warlockery:ingredient_clay_jar")), false);
        assertEquals(List.of(0, 2), EmiMachineTransferPlan.slots(distillery));
    }

    @Test void spinningPrimaryAndEveryDrySlotKeepDeclaredCountsAndOrder() {
        var spinning = recipe("spinningwheel", List.of(new MachineRecipeDefinition.Input("minecraft:string", 3), input("minecraft:stick")), false);
        assertEquals(List.of(0, 1), EmiMachineTransferPlan.slots(spinning));
        assertEquals(3, spinning.inputs().getFirst().count());
        assertTrue(EmiMachineTransferPlan.supports("spinningwheel", spinning));
        assertFalse(EmiMachineTransferPlan.supports("distillery", spinning));
    }

    @Test void fluidRecipesNeverAdvertiseAutomaticDryTransfer() {
        var kettle = recipe("kettle", List.of(input("minecraft:bone_meal")), true);
        assertFalse(EmiMachineTransferPlan.supports("kettle", kettle));
        assertTrue(EmiMachineTransferPlan.slots(kettle).isEmpty());
    }

    @Test void displayedFluidAmountsUseNativeLoaderUnits() {
        assertEquals(81000L, EmiFluidUnits.amount(1000));
        assertEquals(20250L, EmiFluidUnits.amount(250));
        assertEquals(81L, EmiFluidUnits.amount(1));
    }

    @Test void allPackagedMachinesAreCoveredAndOnlyInputSlotsCanBeCleared() {
        assertEquals(java.util.Set.of("alchemical_oven", "distillery", "kettle", "cauldron", "silvervat", "spinningwheel", "brazier"),
            java.util.Set.copyOf(EmiMachineTransferPlan.MACHINES));
        for (var match : com.kadamitas.warlockery.compat.jei.PackagedJeiCatalog.machines()) {
            var definition = match.recipe();
            var profile = com.kadamitas.warlockery.crafting.MachineProfiles.forRecipeType(definition.machine()).orElseThrow();
            assertTrue(EmiMachineTransferPlan.MACHINES.contains(definition.machine()));
            var clear = EmiMachineTransferPlan.clearSlots(definition.machine());
            assertFalse(clear.contains(profile.fuelSlot()), match.id().toString());
            assertTrue(clear.stream().allMatch(slot -> slot < profile.outputStart()), match.id().toString());
            var targets = EmiMachineTransferPlan.slots(definition);
            if (definition.fluid().isPresent()) assertTrue(targets.isEmpty(), match.id().toString());
            else {
                assertEquals(definition.inputs().size(), targets.size(), match.id().toString());
                assertTrue(clear.containsAll(targets), match.id().toString());
                assertEquals(targets.size(), targets.stream().distinct().count(), match.id().toString());
            }
        }
    }

    private static MachineRecipeDefinition.Input input(String item) { return new MachineRecipeDefinition.Input(item, 1); }
    private static MachineRecipeDefinition recipe(String kind, List<MachineRecipeDefinition.Input> inputs, boolean fluid) {
        return new MachineRecipeDefinition(kind, inputs, List.of(new MachineRecipeDefinition.Output("minecraft:clay_ball", 1)),
            200, kind.equals("alchemical_oven"), fluid ? Optional.of(new MachineRecipeDefinition.FluidInput("minecraft:water", 1000)) : Optional.empty(), 0);
    }
}
