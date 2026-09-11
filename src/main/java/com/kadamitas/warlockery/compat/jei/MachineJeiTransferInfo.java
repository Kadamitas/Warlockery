package com.kadamitas.warlockery.compat.jei;

import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.crafting.MachineRecipeSlotPlan;
import com.kadamitas.warlockery.menu.MachineMenu;
import com.kadamitas.warlockery.registry.ModMenus;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

final class MachineJeiTransferInfo implements IRecipeTransferInfo<MachineMenu, MachineRecipeManager.Match> {
    private final String machine;
    MachineJeiTransferInfo(String machine) { this.machine = machine; }
    @Override public Class<MachineMenu> getContainerClass() { return MachineMenu.class; }
    @Override public Optional<MenuType<MachineMenu>> getMenuType() { return Optional.of(ModMenus.machine(machine).get()); }
    @Override public IRecipeType<MachineRecipeManager.Match> getRecipeType() { return WarlockeryJeiRecipeTypes.MACHINES.get(machine); }
    @Override public boolean canHandle(MachineMenu menu, MachineRecipeManager.Match recipe) {
        return menu.kind().equals(machine) && recipe.recipe().machine().equals(machine) && recipe.recipe().fluid().isEmpty();
    }
    @Override public List<Slot> getRecipeSlots(MachineMenu menu, MachineRecipeManager.Match recipe) {
        return MachineRecipeSlotPlan.inputSlots(MachineProfiles.forRecipeType(machine).orElseThrow(), recipe.recipe())
            .stream().map(menu::getSlot).toList();
    }
    @Override public List<Slot> getInventorySlots(MachineMenu menu, MachineRecipeManager.Match recipe) {
        return menu.slots.stream().filter(slot -> slot.container instanceof Inventory).toList();
    }
}
