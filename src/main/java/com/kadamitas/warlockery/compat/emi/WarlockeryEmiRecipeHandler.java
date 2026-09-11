package com.kadamitas.warlockery.compat.emi;

import com.kadamitas.warlockery.menu.MachineMenu;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class WarlockeryEmiRecipeHandler implements StandardRecipeHandler<MachineMenu> {
    private final String machine;

    public WarlockeryEmiRecipeHandler(String machine) { this.machine = machine; }

    @Override public boolean supportsRecipe(EmiRecipe recipe) {
        return recipe instanceof WarlockeryEmiRecipe entry && entry.machine() != null
            && EmiMachineTransferPlan.supports(machine, entry.machine().recipe());
    }

    @Override public List<Slot> getInputSources(MachineMenu menu) {
        if (!menu.kind().equals(machine)) return List.of();
        var inputs = getCraftingSlots(menu);
        return menu.slots.stream().filter(slot -> slot.container instanceof Inventory || inputs.contains(slot)).toList();
    }

    @Override public List<Slot> getCraftingSlots(MachineMenu menu) {
        if (!menu.kind().equals(machine)) return List.of();
        return EmiMachineTransferPlan.clearSlots(machine).stream().map(menu::getSlot).toList();
    }

    @Override public List<Slot> getCraftingSlots(EmiRecipe recipe, MachineMenu menu) {
        if (!menu.kind().equals(machine) || !supportsRecipe(recipe)) return List.of();
        return EmiMachineTransferPlan.slots(((WarlockeryEmiRecipe) recipe).machine().recipe()).stream().map(menu::getSlot).toList();
    }

    @Override public boolean canCraft(EmiRecipe recipe, EmiCraftContext<MachineMenu> context) {
        return context.getScreenHandler().kind().equals(machine) && supportsRecipe(recipe)
            && StandardRecipeHandler.super.canCraft(recipe, context);
    }

    @Override public boolean craft(EmiRecipe recipe, EmiCraftContext<MachineMenu> context) {
        return canCraft(recipe, context) && StandardRecipeHandler.super.craft(recipe, context);
    }
}
